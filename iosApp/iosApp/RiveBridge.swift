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
    private var triggerListenerIds: [UUID] = []

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
            // Must update BOTH the VMI property AND the text run:
            // - VMI prop.value keeps the data binding in sync (prevents
            //   the render loop from overwriting the text run with stale data)
            // - setTextRunValue pushes the change through the rendering pipeline
            //   for immediate visual update
            if let prop = vmi.stringProperty(fromPath: name) {
                prop.value = value
            }
            do {
                try self.riveViewModel.setTextRunValue(name, textValue: value)
            } catch {
                // Not a text run — VMI property was already set above
            }
            print("[SwiftRiveHandle] String set: \(name) = \(value)")
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

    override func addTriggerListener(name: String, callback: @escaping () -> Void) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.triggerProperty(fromPath: name) {
                let listenerId = prop.addListener {
                    callback()
                }
                self.triggerListenerIds.append(listenerId)
                print("[SwiftRiveHandle] Trigger listener added: \(name)")
            } else {
                print("[SwiftRiveHandle] Trigger property not found for listener: \(name)")
            }
        }
    }

    override func destroy() {
        pendingOperations.removeAll()
        triggerListenerIds.removeAll()
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

    /// Strip .riv extension if present — RiveModel(fileName:) expects name without extension
    private static func stripRivExtension(_ name: String) -> String {
        if name.hasSuffix(".riv") {
            return String(name.dropLast(4))
        }
        return name
    }

    func preloadFiles(configs: [RiveFileConfig]) -> Bool {
        for config in configs {
            if loadedConfigs[config.resourceName] != nil {
                continue
            }

            var assetMap: [String: RiveAssetConfig] = [:]
            for asset in config.assets {
                assetMap[asset.assetId] = asset
            }

            let fileName = Self.stripRivExtension(config.resourceName)

            // Validate the file can be loaded
            do {
                let _ = try RiveModel(
                    fileName: fileName,
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

        let fileName = Self.stripRivExtension(resourceName)

        do {
            let model = try RiveModel(
                fileName: fileName,
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
    //
    // Matches Android behavior: use ONLY the resourceName from config mapping.
    // No fallback to asset ID filenames — fail loudly if the resource is missing.

    private static func loadAsset(
        asset: RiveFileAsset,
        data: Data,
        factory: RiveFactory,
        assetMap: [String: RiveAssetConfig]
    ) -> Bool {

        let uniqueName = asset.uniqueName()
        let assetName = asset.name()

        // Find matching config by uniqueName (assetId)
        guard let config = assetMap[uniqueName] ?? assetMap[assetName] else {
            print("[SwiftRiveBridge] No config mapping for asset: \(uniqueName) (\(assetName))")
            return false
        }

        let rawName = config.resourceName

        // Support resourceName with or without extension (e.g. "coin.webp" or "coin")
        let resourceName: String
        let configExt: String?
        if let dotIndex = rawName.lastIndex(of: ".") {
            resourceName = String(rawName[rawName.startIndex..<dotIndex])
            configExt = String(rawName[rawName.index(after: dotIndex)...])
        } else {
            resourceName = rawName
            configExt = nil
        }

        if let fontAsset = asset as? RiveFontAsset {
            let extensions = [configExt, asset.fileExtension(), "ttf", "otf"].compactMap { $0 }
            for ext in extensions {
                if let url = Bundle.main.url(forResource: resourceName, withExtension: ext),
                   let fontData = try? Data(contentsOf: url) {
                    let decodedFont = factory.decodeFont(fontData)
                    fontAsset.font(decodedFont)
                    print("[SwiftRiveBridge] Font injected: \(uniqueName) from \(resourceName).\(ext)")
                    return true
                }
            }
            print("[SwiftRiveBridge] Font not found: \(uniqueName), resource: \(rawName)")
            return false
        }

        if let imageAsset = asset as? RiveImageAsset {
            let extensions = [configExt, asset.fileExtension(), "webp", "png", "jpg", "jpeg"].compactMap { $0 }
            for ext in extensions {
                if let url = Bundle.main.url(forResource: resourceName, withExtension: ext),
                   let imageData = try? Data(contentsOf: url) {
                    let decoded = factory.decodeImage(imageData)
                    imageAsset.renderImage(decoded)
                    print("[SwiftRiveBridge] Image injected: \(uniqueName) from \(resourceName).\(ext)")
                    return true
                }
            }
            print("[SwiftRiveBridge] Image not found: \(uniqueName), resource: \(rawName)")
            return false
        }

        return false
    }
}
