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
    private var isViewReady = false
    private var pendingOperations: [() -> Void] = []

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

        // Flush pending operations once the view is in the hierarchy
        DispatchQueue.main.async { [weak self] in
            self?.markReady()
        }

        return hosting.view
    }

    private func markReady() {
        guard !isViewReady else { return }
        isViewReady = true
        print("[SwiftRiveHandle] View ready, flushing \(pendingOperations.count) pending operations")
        for op in pendingOperations {
            op()
        }
        pendingOperations.removeAll()
    }

    private func executeOrBuffer(_ operation: @escaping () -> Void) {
        if isViewReady {
            operation()
        } else {
            pendingOperations.append(operation)
        }
    }

    override func setStringProperty(name: String, value: String) {
        executeOrBuffer { [self] in
            if let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance {
                if let prop = vmi.stringProperty(fromPath: name) {
                    prop.value = value
                    print("[SwiftRiveHandle] String set via VMI: \(name) = \(value)")
                    return
                }
            }
            do {
                try riveViewModel.setTextRunValue(name, textValue: value)
                print("[SwiftRiveHandle] String set via text run: \(name) = \(value)")
            } catch {
                print("[SwiftRiveHandle] Failed to set string \(name): \(error)")
            }
        }
    }

    override func setEnumProperty(name: String, value: String) {
        executeOrBuffer { [self] in
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
        executeOrBuffer { [self] in
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
        executeOrBuffer { [self] in
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
        executeOrBuffer { [self] in
            if let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
               let prop = vmi.triggerProperty(fromPath: name) {
                prop.trigger()
                print("[SwiftRiveHandle] Trigger fired via VMI: \(name)")
                return
            }
            riveViewModel.triggerInput(name)
            print("[SwiftRiveHandle] Trigger fired via input: \(name)")
        }
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
