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
    private var boundVMI: RiveDataBindingViewModel.Instance?

    init(riveModel: RiveModel) {
        self.riveModel = riveModel
        self.riveViewModel = RiveViewModel(riveModel, autoPlay: true)
        super.init()

        // Use enableAutoBind to get a VMI that's connected to the rendering pipeline
        riveModel.enableAutoBind { [weak self] instance in
            guard let self else { return }
            self.boundVMI = instance
            print("[SwiftRiveHandle] AutoBind VMI received, \(instance.propertyCount) properties:")
            for prop in instance.properties {
                print("[SwiftRiveHandle]   - \"\(prop.name)\" (type: \(prop.type.rawValue))")
            }
            // Flush any buffered operations
            if !self.pendingOperations.isEmpty {
                print("[SwiftRiveHandle] Flushing \(self.pendingOperations.count) pending operations")
                for op in self.pendingOperations { op() }
                self.pendingOperations.removeAll()
            }
        }
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

    // MARK: - VMI access

    private func executeWithVMI(_ operation: @escaping () -> Void) {
        if boundVMI != nil {
            operation()
        } else {
            pendingOperations.append(operation)
        }
    }

    // MARK: - Property setters

    override func setStringProperty(name: String, value: String) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.stringProperty(fromPath: name) {
                prop.value = value
                print("[SwiftRiveHandle] String set: \(name) = \(value)")
            } else {
                // Fallback to text run API
                do {
                    try self.riveViewModel.setTextRunValue(name, textValue: value)
                    print("[SwiftRiveHandle] String set via text run: \(name) = \(value)")
                } catch {
                    print("[SwiftRiveHandle] String property not found: \(name)")
                }
            }
        }
    }

    override func setEnumProperty(name: String, value: String) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.enumProperty(fromPath: name) {
                prop.value = value
                print("[SwiftRiveHandle] Enum set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle] Enum property not found: \(name)")
            }
        }
    }

    override func setBooleanProperty(name: String, value: Bool) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.booleanProperty(fromPath: name) {
                prop.value = value
                print("[SwiftRiveHandle] Boolean set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle] Boolean property not found: \(name)")
            }
        }
    }

    override func setNumberProperty(name: String, value: Float) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.numberProperty(fromPath: name) {
                prop.value = value
                print("[SwiftRiveHandle] Number set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle] Number property not found: \(name)")
            }
        }
    }

    override func fireTrigger(name: String) {
        if let vmi = boundVMI, let prop = vmi.triggerProperty(fromPath: name) {
            prop.trigger()
            print("[SwiftRiveHandle] Trigger fired: \(name)")
            return
        }
        // Buffer if VMI not ready yet
        if boundVMI == nil {
            pendingOperations.append { [weak self] in
                self?.fireTrigger(name: name)
            }
            return
        }
        riveViewModel.triggerInput(name)
        print("[SwiftRiveHandle] Trigger fired via input: \(name)")
    }

    override func destroy() {
        pendingOperations.removeAll()
        riveModel.disableAutoBind()
        boundVMI = nil
        hostingController = nil
    }
}

// MARK: - SwiftRiveBridge

class SwiftRiveBridge: NSObject, IOSRiveBridge {

    // Store asset configs instead of shared RiveModel instances.
    // Each createHandle() call creates a fresh RiveModel so each
    // handle gets its own independent enableAutoBind VMI.
    private var loadedConfigs: [String: [String: RiveAssetConfig]] = [:]

    func preloadFiles(configs: [RiveFileConfig]) -> Bool {
        for config in configs {
            if loadedConfigs[config.resourceName] != nil {
                continue
            }

            var assetMap: [String: RiveAssetConfig] = [:]
            for asset in config.assets {
                assetMap[asset.assetId] = asset
            }

            // Validate the file can be loaded
            do {
                let _ = try RiveModel(
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
                loadedConfigs[config.resourceName] = assetMap
                print("[SwiftRiveBridge] Validated: \(config.resourceName)")
            } catch {
                print("[SwiftRiveBridge] Failed to load \(config.resourceName): \(error)")
                return false
            }
        }
        return true
    }

    func createHandle(resourceName: String) -> IOSRiveHandle? {
        guard let assetMap = loadedConfigs[resourceName] else {
            print("[SwiftRiveBridge] No config for: \(resourceName)")
            return nil
        }
        do {
            let model = try RiveModel(
                fileName: resourceName,
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
            return SwiftRiveHandle(riveModel: model)
        } catch {
            print("[SwiftRiveBridge] Failed to create model for \(resourceName): \(error)")
            return nil
        }
    }

    func isFileLoaded(resourceName: String) -> Bool {
        return loadedConfigs[resourceName] != nil
    }

    func clearAll() {
        loadedConfigs.removeAll()
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
