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
    private var isDestroyed = false

    init(riveModel: RiveModel) {
        self.riveModel = riveModel
        self.riveViewModel = RiveViewModel(riveModel, autoPlay: true)
        super.init()

        // Use enableAutoBind to get a VMI that's connected to the rendering pipeline.
        // Callback may fire on a non-main thread, so dispatch to main for thread safety.
        riveModel.enableAutoBind { [weak self] instance in
            DispatchQueue.main.async {
                guard let self, !self.isDestroyed else { return }
                self.boundVMI = instance
                print("[SwiftRiveHandle] AutoBind VMI received, \(instance.propertyCount) properties:")
                for prop in instance.properties {
                    print("[SwiftRiveHandle]   - \"\(prop.name)\" (type: \(prop.type.rawValue))")
                }
                // Flush any buffered operations
                if !self.pendingOperations.isEmpty {
                    print("[SwiftRiveHandle] Flushing \(self.pendingOperations.count) pending operations")
                    let ops = self.pendingOperations
                    self.pendingOperations.removeAll()
                    for op in ops { op() }
                }
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
    // All operations are dispatched to main thread to ensure:
    // 1. Thread-safe access to pendingOperations and boundVMI
    // 2. UIKit operations (advance) always run on main thread

    private func executeWithVMI(_ operation: @escaping () -> Void) {
        let execute = { [weak self] in
            guard let self, !self.isDestroyed else { return }
            if self.boundVMI != nil {
                operation()
            } else {
                self.pendingOperations.append(operation)
            }
        }
        if Thread.isMainThread {
            execute()
        } else {
            DispatchQueue.main.async(execute: execute)
        }
    }

    // MARK: - Property setters

    override func setStringProperty(name: String, value: String) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.stringProperty(fromPath: name) {
                prop.value = value
                // Force a render frame so the visual picks up the change
                // (display link may have paused after animation completed)
                self.riveViewModel.riveView?.advance(delta: 0)
                print("[SwiftRiveHandle] String set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle] String property not found: \(name)")
            }
        }
    }

    override func setEnumProperty(name: String, value: String) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.enumProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
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
                self.riveViewModel.riveView?.advance(delta: 0)
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
                self.riveViewModel.riveView?.advance(delta: 0)
                print("[SwiftRiveHandle] Number set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle] Number property not found: \(name)")
            }
        }
    }

    override func fireTrigger(name: String) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.triggerProperty(fromPath: name) {
                prop.trigger()
                print("[SwiftRiveHandle] Trigger fired: \(name)")
            } else {
                // Fallback to input-based trigger
                self.riveViewModel.triggerInput(name)
                print("[SwiftRiveHandle] Trigger fired via input: \(name)")
            }
        }
    }

    override func addTriggerListener(name: String, callback: @escaping () -> Void) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.triggerProperty(fromPath: name) {
                let listenerId = prop.addListener { [weak self] in
                    guard self?.isDestroyed == false else { return }
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
        isDestroyed = true
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
