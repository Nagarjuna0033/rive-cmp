import Foundation
import UIKit
import SwiftUI
import RiveRuntime
import ComposeApp

// MARK: - SwiftRiveHandle

class SwiftRiveHandle: IOSRiveHandle {

    private let riveModel: RiveModel
    private let riveViewModel: RiveViewModel
    private var hostingController: UIHostingController<AnyView>?
    private var pendingOperations: [() -> Void] = []
    private var isFlushScheduled = false

    init(riveModel: RiveModel) {
        self.riveModel = riveModel
        self.riveViewModel = RiveViewModel(riveModel, autoPlay: true)
        super.init()
    }

    override func getUIView() -> UIView {
        if let existing = hostingController {
            return existing.view
        }
        let swiftUIView = riveViewModel.view()
        let hosting = UIHostingController(rootView: AnyView(swiftUIView))
        hosting.view.backgroundColor = .clear
        hostingController = hosting
        return hosting.view
    }

    // MARK: - VMI polling

    /// Check if the ViewModelInstance is available (state machine must be running)
    private var vmi: Any? {
        riveViewModel.riveModel?.stateMachine?.viewModelInstance
    }

    /// Execute immediately if VMI is ready, otherwise buffer and poll
    private func executeWithVMI(_ operation: @escaping () -> Void) {
        if vmi != nil {
            operation()
        } else {
            pendingOperations.append(operation)
            scheduleFlush()
        }
    }

    private func scheduleFlush() {
        guard !isFlushScheduled else { return }
        isFlushScheduled = true
        pollForVMI(attemptsLeft: 50) // 50 × 100ms = 5s max
    }

    private func pollForVMI(attemptsLeft: Int) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            guard let self, !self.pendingOperations.isEmpty else {
                self?.isFlushScheduled = false
                return
            }
            if self.vmi != nil {
                print("[SwiftRiveHandle] VMI ready, flushing \(self.pendingOperations.count) pending operations")
                for op in self.pendingOperations { op() }
                self.pendingOperations.removeAll()
                self.isFlushScheduled = false
            } else if attemptsLeft > 0 {
                self.pollForVMI(attemptsLeft: attemptsLeft - 1)
            } else {
                print("[SwiftRiveHandle] VMI never became available after 5s, dropping \(self.pendingOperations.count) operations")
                self.pendingOperations.removeAll()
                self.isFlushScheduled = false
            }
        }
    }

    // MARK: - Property setters

    override func setStringProperty(name: String, value: String) {
        executeWithVMI { [self] in
            if let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
               let prop = vmi.stringProperty(fromPath: name) {
                prop.value = value
                print("[SwiftRiveHandle] String set via VMI: \(name) = \(value)")
                return
            }
            // Fallback to text runs
            do {
                try riveViewModel.setTextRunValue(name, textValue: value)
                print("[SwiftRiveHandle] String set via text run: \(name) = \(value)")
            } catch {
                print("[SwiftRiveHandle] Failed to set string \(name): \(error)")
            }
        }
    }

    override func setEnumProperty(name: String, value: String) {
        executeWithVMI { [self] in
            guard let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
                  let prop = vmi.enumProperty(fromPath: name) else {
                print("[SwiftRiveHandle] Enum property not found: \(name)")
                return
            }
            prop.value = value
            print("[SwiftRiveHandle] Enum set: \(name) = \(value)")
        }
    }

    override func setBooleanProperty(name: String, value: Bool) {
        executeWithVMI { [self] in
            guard let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
                  let prop = vmi.booleanProperty(fromPath: name) else {
                print("[SwiftRiveHandle] Boolean property not found: \(name)")
                return
            }
            prop.value = value
            print("[SwiftRiveHandle] Boolean set: \(name) = \(value)")
        }
    }

    override func setNumberProperty(name: String, value: Float) {
        executeWithVMI { [self] in
            guard let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
                  let prop = vmi.numberProperty(fromPath: name) else {
                print("[SwiftRiveHandle] Number property not found: \(name)")
                return
            }
            prop.value = value
            print("[SwiftRiveHandle] Number set: \(name) = \(value)")
        }
    }

    override func fireTrigger(name: String) {
        // Triggers can be called anytime (user interaction), so check VMI first, then fallback
        if let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
           let prop = vmi.triggerProperty(fromPath: name) {
            prop.trigger()
            print("[SwiftRiveHandle] Trigger fired via VMI: \(name)")
            return
        }
        riveViewModel.triggerInput(name)
        print("[SwiftRiveHandle] Trigger fired via input: \(name)")
    }

    override func destroy() {
        pendingOperations.removeAll()
        hostingController = nil
    }
}

// MARK: - SwiftRiveBridge

class SwiftRiveBridge: NSObject, IOSRiveBridge {

    private var loadedModels: [String: RiveModel] = [:]

    func preloadFiles(configs: [RiveFileConfig]) -> Bool {
        for config in configs {
            if loadedModels[config.resourceName] != nil {
                continue
            }

            // Build asset lookup: uniqueName/assetId -> resourceName
            var assetMap: [String: RiveAssetConfig] = [:]
            for asset in config.assets {
                assetMap[asset.assetId] = asset
            }

            do {
                let model = try RiveModel(
                    fileName: config.resourceName,
                    loadCdn: false,
                    customLoader: { [assetMap] asset, data, factory in
                        return Self.loadAsset(
                            asset: asset,
                            data: data,
                            factory: factory,
                            assetMap: assetMap
                        )
                    }
                )
                loadedModels[config.resourceName] = model
                print("[SwiftRiveBridge] Loaded: \(config.resourceName)")
            } catch {
                print("[SwiftRiveBridge] Failed to load \(config.resourceName): \(error)")
                return false
            }
        }
        return true
    }

    func createHandle(resourceName: String) -> IOSRiveHandle? {
        guard let model = loadedModels[resourceName] else {
            print("[SwiftRiveBridge] No preloaded model for: \(resourceName)")
            return nil
        }
        return SwiftRiveHandle(riveModel: model)
    }

    func isFileLoaded(resourceName: String) -> Bool {
        return loadedModels[resourceName] != nil
    }

    func clearAll() {
        loadedModels.removeAll()
    }

    // MARK: - Asset Loading

    private static func loadAsset(
        asset: RiveFileAsset,
        data: Data,
        factory: RiveFactory,
        assetMap: [String: RiveAssetConfig]
    ) -> Bool {

        let uniqueName = asset.uniqueName()
        let assetName = asset.name()

        // Find matching config by uniqueName (assetId)
        let config = assetMap[uniqueName] ?? assetMap[assetName]

        // Determine resource name: from config mapping, or try uniqueName/name
        let candidates: [String]
        if let config = config {
            candidates = [config.resourceName, uniqueName, assetName]
        } else {
            candidates = [uniqueName, assetName]
        }

        let ext = asset.fileExtension()

        if let fontAsset = asset as? RiveFontAsset {
            for candidate in candidates {
                if let url = Bundle.main.url(forResource: candidate, withExtension: ext),
                   let fontData = try? Data(contentsOf: url) {
                    let decodedFont = factory.decodeFont(fontData)
                    fontAsset.font(decodedFont)
                    print("[SwiftRiveBridge] Font injected: \(uniqueName) from \(candidate).\(ext)")
                    return true
                }
            }
            // Try common font extensions
            for candidate in candidates {
                for tryExt in ["ttf", "otf"] {
                    if let url = Bundle.main.url(forResource: candidate, withExtension: tryExt),
                       let fontData = try? Data(contentsOf: url) {
                        let decodedFont = factory.decodeFont(fontData)
                        fontAsset.font(decodedFont)
                        print("[SwiftRiveBridge] Font injected: \(uniqueName) from \(candidate).\(tryExt)")
                        return true
                    }
                }
            }
            print("[SwiftRiveBridge] Font not found: \(uniqueName), tried: \(candidates)")
            return false
        }

        if let imageAsset = asset as? RiveImageAsset {
            for candidate in candidates {
                if let url = Bundle.main.url(forResource: candidate, withExtension: ext),
                   let imageData = try? Data(contentsOf: url) {
                    let decoded = factory.decodeImage(imageData)
                    imageAsset.renderImage(decoded)
                    print("[SwiftRiveBridge] Image injected: \(uniqueName) from \(candidate).\(ext)")
                    return true
                }
            }
            // Try common image extensions
            for candidate in candidates {
                for tryExt in ["webp", "png", "jpg", "jpeg"] {
                    if let url = Bundle.main.url(forResource: candidate, withExtension: tryExt),
                       let imageData = try? Data(contentsOf: url) {
                        let decoded = factory.decodeImage(imageData)
                        imageAsset.renderImage(decoded)
                        print("[SwiftRiveBridge] Image injected: \(uniqueName) from \(candidate).\(tryExt)")
                        return true
                    }
                }
            }
            print("[SwiftRiveBridge] Image not found: \(uniqueName), tried: \(candidates)")
            return false
        }

        return false
    }
}
