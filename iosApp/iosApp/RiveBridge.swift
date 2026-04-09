import Foundation
import UIKit
import SwiftUI
import MetalKit
import RiveRuntime
import ComposeApp

// Disambiguate KMP enums from RiveRuntime enums
typealias KmpRiveFit = ComposeApp.RiveFit
typealias KmpRiveAlignment = ComposeApp.RiveAlignment

// MARK: - SwiftRiveHandle

class SwiftRiveHandle: IOSRiveHandle {

    private let riveModel: RiveModel
    private let riveViewModel: RiveViewModel
    private var hostingController: UIHostingController<AnyView>?  // kept for potential fallback
    private var containerView: UIView?
    private var pendingOperations: [() -> Void] = []
    private var boundVMI: RiveDataBindingViewModel.Instance?
    private var triggerListenerIds: [UUID] = []
    private var isDestroyed = false

    init(riveModel: RiveModel, artboardName: String?, stateMachineName: String?) {
        print("[SwiftRiveHandle] ⚙️ init — artboardName: \(artboardName ?? "nil"), stateMachineName: \(stateMachineName ?? "nil")")
        self.riveModel = riveModel
        self.riveViewModel = RiveViewModel(
            riveModel,
            stateMachineName: stateMachineName,
            autoPlay: true,
            artboardName: artboardName
        )
        print("[SwiftRiveHandle] ✅ RiveViewModel created")
        super.init()

        riveModel.enableAutoBind { [weak self] instance in
            DispatchQueue.main.async {
                guard let self, !self.isDestroyed else {
                    print("[SwiftRiveHandle] ⚠️ AutoBind callback — handle is destroyed or nil, skipping")
                    return
                }
                self.boundVMI = instance
                print("[SwiftRiveHandle] 🔗 AutoBind VMI received — propertyCount: \(instance.propertyCount)")
                for prop in instance.properties {
                    print("[SwiftRiveHandle]   📌 \"\(prop.name)\" type: \(prop.type.rawValue)")
                }
                if !self.pendingOperations.isEmpty {
                    print("[SwiftRiveHandle] 🔄 Flushing \(self.pendingOperations.count) pending operations")
                    let ops = self.pendingOperations
                    self.pendingOperations.removeAll()
                    for (i, op) in ops.enumerated() {
                        print("[SwiftRiveHandle]   ▶️ Executing pending op \(i + 1)/\(ops.count)")
                        op()
                    }
                    print("[SwiftRiveHandle] ✅ All pending operations flushed")
                } else {
                    print("[SwiftRiveHandle] ℹ️ No pending operations to flush")
                }
            }
        }
        print("[SwiftRiveHandle] ✅ enableAutoBind registered")
    }

    private func mapFit(_ fit: KmpRiveFit) -> RiveRuntime.RiveFit {
        switch fit {
        case .fill: return .fill
        case .contain: return .contain
        case .cover: return .cover
        case .fitWidth: return .fitWidth
        case .fitHeight: return .fitHeight
        case .scaleDown: return .scaleDown
        case .none: return .noFit
        case .layout: return .layout
        default: return .fill
        }
    }

    private func mapAlignment(_ alignment: KmpRiveAlignment) -> RiveRuntime.RiveAlignment {
        switch alignment {
        case .topLeft: return .topLeft
        case .topCenter: return .topCenter
        case .topRight: return .topRight
        case .centerLeft: return .centerLeft
        case .center: return .center
        case .centerRight: return .centerRight
        case .bottomLeft: return .bottomLeft
        case .bottomCenter: return .bottomCenter
        case .bottomRight: return .bottomRight
        default: return .center
        }
    }

    override func getUIView(
        fit: KmpRiveFit = .contain,
        alignment: KmpRiveAlignment = .center
    ) -> UIView {
        print("[SwiftRiveHandle] 🖼️ getUIView called — fit: \(fit), alignment: \(alignment)")
        riveViewModel.fit = mapFit(fit)
        riveViewModel.alignment = mapAlignment(alignment)
        print("[SwiftRiveHandle]   ✅ fit and alignment applied to riveViewModel")

        if let existing = containerView {
            print("[SwiftRiveHandle]   ♻️ Reusing existing containerView: \(type(of: existing)), frame: \(existing.frame), isOpaque: \(existing.isOpaque), backgroundColor: \(String(describing: existing.backgroundColor))")
            return existing
        }

        print("[SwiftRiveHandle]   🔨 Creating new RiveView via riveViewModel.createRiveView()")
        let riveView = riveViewModel.createRiveView()
        print("[SwiftRiveHandle]   ✅ RiveView created: \(type(of: riveView)), frame: \(riveView.frame), isOpaque: \(riveView.isOpaque), backgroundColor: \(String(describing: riveView.backgroundColor))")
        containerView = riveView
        print("[SwiftRiveHandle]   ✅ containerView set, returning riveView")
        return riveView
    }

    // MARK: - VMI access

    private func executeWithVMI(_ operation: @escaping () -> Void) {
        let execute = { [weak self] in
            guard let self, !self.isDestroyed else {
                print("[SwiftRiveHandle] ⚠️ executeWithVMI — handle destroyed or nil, skipping")
                return
            }
            if self.boundVMI != nil {
                print("[SwiftRiveHandle]   ▶️ executeWithVMI — VMI ready, executing immediately")
                operation()
            } else {
                print("[SwiftRiveHandle]   ⏳ executeWithVMI — VMI not ready, queuing (queue size will be: \(self.pendingOperations.count + 1))")
                self.pendingOperations.append(operation)
            }
        }
        if Thread.isMainThread {
            execute()
        } else {
            print("[SwiftRiveHandle]   🧵 executeWithVMI — dispatching to main thread")
            DispatchQueue.main.async(execute: execute)
        }
    }

    // MARK: - Property setters

    override func setStringProperty(name: String, value: String) {
        print("[SwiftRiveHandle] 📝 setStringProperty — name: \(name), value: \(value)")
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else {
                print("[SwiftRiveHandle]   ❌ setStringProperty — VMI unavailable for: \(name)")
                return
            }
            if let prop = vmi.stringProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
                print("[SwiftRiveHandle]   ✅ String set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle]   ❌ String property not found: \(name)")
            }
        }
    }

    override func setEnumProperty(name: String, value: String) {
        print("[SwiftRiveHandle] 📝 setEnumProperty — name: \(name), value: \(value)")
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else {
                print("[SwiftRiveHandle]   ❌ setEnumProperty — VMI unavailable for: \(name)")
                return
            }
            if let prop = vmi.enumProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
                print("[SwiftRiveHandle]   ✅ Enum set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle]   ❌ Enum property not found: \(name)")
            }
        }
    }

    override func setBooleanProperty(name: String, value: Bool) {
        print("[SwiftRiveHandle] 📝 setBooleanProperty — name: \(name), value: \(value)")
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else {
                print("[SwiftRiveHandle]   ❌ setBooleanProperty — VMI unavailable for: \(name)")
                return
            }
            if let prop = vmi.booleanProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
                print("[SwiftRiveHandle]   ✅ Boolean set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle]   ❌ Boolean property not found: \(name)")
            }
        }
    }

    override func setNumberProperty(name: String, value: Float) {
        print("[SwiftRiveHandle] 📝 setNumberProperty — name: \(name), value: \(value)")
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else {
                print("[SwiftRiveHandle]   ❌ setNumberProperty — VMI unavailable for: \(name)")
                return
            }
            if let prop = vmi.numberProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
                print("[SwiftRiveHandle]   ✅ Number set: \(name) = \(value)")
            } else {
                print("[SwiftRiveHandle]   ❌ Number property not found: \(name)")
            }
        }
    }

    override func fireTrigger(name: String) {
        print("[SwiftRiveHandle] 🔫 fireTrigger — name: \(name)")
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else {
                print("[SwiftRiveHandle]   ❌ fireTrigger — VMI unavailable for: \(name)")
                return
            }
            if let prop = vmi.triggerProperty(fromPath: name) {
                prop.trigger()
                print("[SwiftRiveHandle]   ✅ Trigger fired via VMI: \(name)")
            } else {
                self.riveViewModel.triggerInput(name)
                print("[SwiftRiveHandle]   ✅ Trigger fired via input fallback: \(name)")
            }
        }
    }

    override func addTriggerListener(name: String, callback: @escaping () -> Void) {
        print("[SwiftRiveHandle] 👂 addTriggerListener — name: \(name)")
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else {
                print("[SwiftRiveHandle]   ❌ addTriggerListener — VMI unavailable for: \(name)")
                return
            }
            if let prop = vmi.triggerProperty(fromPath: name) {
                let listenerId = prop.addListener { [weak self] in
                    guard self?.isDestroyed == false else {
                        print("[SwiftRiveHandle]   ⚠️ Trigger listener fired but handle is destroyed: \(name)")
                        return
                    }
                    print("[SwiftRiveHandle]   🔔 Trigger listener fired: \(name)")
                    callback()
                }
                self.triggerListenerIds.append(listenerId)
                print("[SwiftRiveHandle]   ✅ Trigger listener added: \(name), id: \(listenerId), total: \(self.triggerListenerIds.count)")
            } else {
                print("[SwiftRiveHandle]   ❌ Trigger property not found for listener: \(name)")
            }
        }
    }

    override func setImageProperty(name: String, pngBytes: KotlinByteArray) {
        print("[SwiftRiveHandle] 🖼️ setImageProperty — name: \(name), byteCount: \(pngBytes.size)")
        let length = Int(pngBytes.size)
        var bytes = [UInt8](repeating: 0, count: length)
        for i in 0..<length {
            bytes[i] = UInt8(bitPattern: pngBytes.get(index: Int32(i)))
        }
        let data = Data(bytes)
        print("[SwiftRiveHandle]   📦 KotlinByteArray → Data, size: \(data.count) bytes")

        guard let uiImage = UIImage(data: data) else {
            print("[SwiftRiveHandle]   ❌ Failed to create UIImage for: \(name)")
            return
        }
        print("[SwiftRiveHandle]   ✅ UIImage created — size: \(uiImage.size)")

        guard let riveImage = RiveRenderImage(image: uiImage, format: .png) else {
            print("[SwiftRiveHandle]   ❌ Failed to create RiveRenderImage for: \(name)")
            return
        }
        print("[SwiftRiveHandle]   ✅ RiveRenderImage created for: \(name)")

        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else {
                print("[SwiftRiveHandle]   ❌ setImageProperty — VMI unavailable for: \(name)")
                return
            }
            if let prop = vmi.imageProperty(fromPath: name) {
                prop.setValue(riveImage)
                self.riveViewModel.riveView?.advance(delta: 0)
                print("[SwiftRiveHandle]   ✅ Image set: \(name)")
            } else {
                print("[SwiftRiveHandle]   ❌ Image property not found: \(name)")
            }
        }
    }

    override func destroy() {
        print("[SwiftRiveHandle] 💥 destroy — isDestroyed was: \(isDestroyed), pendingOps: \(pendingOperations.count), triggerListeners: \(triggerListenerIds.count)")
        isDestroyed = true
        pendingOperations.removeAll()
        triggerListenerIds.removeAll()
        riveModel.disableAutoBind()
        boundVMI = nil
        hostingController = nil
        containerView = nil
        print("[SwiftRiveHandle] ✅ destroy complete")
    }
}

// MARK: - SwiftRiveBridge

class SwiftRiveBridge: NSObject, IOSRiveBridge {

    private var loadedConfigs: [String: [String: RiveAssetConfig]] = [:]

    private static let assetDir: URL = {
        let filesDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dir = filesDir.appendingPathComponent("app_assets/asset")
        print("[SwiftRiveBridge] 📁 assetDir resolved: \(dir.path)")
        return dir
    }()

    func preloadFiles(configs: [RiveFileConfig]) -> Bool {
        print("[SwiftRiveBridge] 📦 preloadFiles — configCount: \(configs.count)")
        for config in configs {
            print("[SwiftRiveBridge]   🔍 Config: \(config.resourceName), assetCount: \(config.assets.count)")
            if loadedConfigs[config.resourceName] != nil {
                print("[SwiftRiveBridge]   ⏭️ Already loaded, skipping: \(config.resourceName)")
                continue
            }

            var assetMap: [String: RiveAssetConfig] = [:]
            for asset in config.assets {
                assetMap[asset.assetId] = asset
                print("[SwiftRiveBridge]     📌 Asset: assetId=\(asset.assetId), resourceName=\(asset.resourceName)")
            }

            let rivFileURL = Self.assetDir.appendingPathComponent(config.resourceName)
            print("[SwiftRiveBridge]   📂 Checking .riv at: \(rivFileURL.path)")
            guard let rivData = try? Data(contentsOf: rivFileURL) else {
                print("[SwiftRiveBridge]   ❌ .riv not found: \(rivFileURL.path)")
                return false
            }
            print("[SwiftRiveBridge]   ✅ .riv found, size: \(rivData.count) bytes")

            loadedConfigs[config.resourceName] = assetMap
            print("[SwiftRiveBridge]   ✅ Cached: \(config.resourceName)")
        }
        print("[SwiftRiveBridge] ✅ preloadFiles complete — totalLoaded: \(loadedConfigs.count)")
        return true
    }

    func createHandle(
        resourceName: String,
        artboardName: String?,
        stateMachineName: String?
    ) -> IOSRiveHandle? {
        print("\n================ RIVE CREATE HANDLE ================")
        print("[SwiftRiveBridge] 🔨 createHandle — resourceName: \(resourceName), artboardName: \(artboardName ?? "nil"), stateMachineName: \(stateMachineName ?? "nil")")

        guard let assetMap = loadedConfigs[resourceName] else {
            print("[SwiftRiveBridge] ❌ No config for: \(resourceName). Available: \(loadedConfigs.keys.joined(separator: ", "))")
            print("=====================================================\n")
            return nil
        }
        print("[SwiftRiveBridge]   ✅ Config found — assetMapCount: \(assetMap.count)")

        let rivFileURL = Self.assetDir.appendingPathComponent(resourceName)
        print("[SwiftRiveBridge]   📂 Loading .riv from: \(rivFileURL.path)")
        guard let rivData = try? Data(contentsOf: rivFileURL) else {
            print("[SwiftRiveBridge]   ❌ .riv not found: \(rivFileURL.path)")
            print("=====================================================\n")
            return nil
        }
        print("[SwiftRiveBridge]   ✅ .riv loaded, size: \(rivData.count) bytes")

        do {
            print("[SwiftRiveBridge]   🔨 Creating RiveFile (loadCdn: false, customAssetLoader: yes)")
            let riveFile = try RiveFile(
                data: rivData,
                loadCdn: false,
                customAssetLoader: { [assetMap] asset, data, factory in
                    print("[SwiftRiveBridge]   🎨 customAssetLoader — asset: \(asset.name()), uniqueName: \(asset.uniqueName())")
                    let result = Self.loadAsset(
                        asset: asset,
                        data: data,
                        factory: factory,
                        assetMap: assetMap
                    )
                    print("[SwiftRiveBridge]   🎨 customAssetLoader result: \(result) for: \(asset.name())")
                    return result
                }
            )
            print("[SwiftRiveBridge]   ✅ RiveFile created")
            let model = RiveModel(riveFile: riveFile)
            print("[SwiftRiveBridge]   ✅ RiveModel created")
            let handle = SwiftRiveHandle(
                riveModel: model,
                artboardName: artboardName,
                stateMachineName: stateMachineName
            )
            print("[SwiftRiveBridge]   ✅ SwiftRiveHandle created")
            print("=====================================================\n")
            return handle
        } catch {
            print("[SwiftRiveBridge]   ❌ Failed to create model for \(resourceName): \(error)")
            print("=====================================================\n")
            return nil
        }
    }

    func isFileLoaded(resourceName: String) -> Bool {
        let result = loadedConfigs[resourceName] != nil
        print("[SwiftRiveBridge] 🔎 isFileLoaded — resourceName: \(resourceName), result: \(result)")
        return result
    }

    func clearAll() {
        print("[SwiftRiveBridge] 🧹 clearAll — clearing \(loadedConfigs.count) configs")
        loadedConfigs.removeAll()
        print("[SwiftRiveBridge] ✅ clearAll complete")
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
        print("[SwiftRiveBridge] 🎨 loadAsset — uniqueName: \(uniqueName), assetName: \(assetName), type: \(type(of: asset))")

        guard let config = assetMap[uniqueName] ?? assetMap[assetName] else {
            // No config mapping — if the .riv has embedded data for this asset,
            // decode and inject it directly (the fallback chain has no other
            // loader when loadCdn is false, so returning false would discard
            // the embedded bytes entirely).
            if !data.isEmpty {
                if let fontAsset = asset as? RiveFontAsset {
                    let decodedFont = factory.decodeFont(data)
                    fontAsset.font(decodedFont)
                    print("[SwiftRiveBridge] Font decoded from embedded data: \(uniqueName)")
                    return true
                }
                if let imageAsset = asset as? RiveImageAsset {
                    let decoded = factory.decodeImage(data)
                    imageAsset.renderImage(decoded)
                    print("[SwiftRiveBridge] Image decoded from embedded data: \(uniqueName)")
                    return true
                }
            }
            print("[SwiftRiveBridge]   ❌ No config and no embedded data for asset: \(uniqueName) (\(assetName)). Available keys: \(assetMap.keys.joined(separator: ", "))")
            return false
        }
        print("[SwiftRiveBridge]   ✅ Config found — resourceName: \(config.resourceName)")

        let rawName = config.resourceName
        let resourceName: String
        let configExt: String?
        if let dotIndex = rawName.lastIndex(of: ".") {
            resourceName = String(rawName[rawName.startIndex..<dotIndex])
            configExt = String(rawName[rawName.index(after: dotIndex)...])
        } else {
            resourceName = rawName
            configExt = nil
        }
        print("[SwiftRiveBridge]   📝 Parsed — resourceName: \(resourceName), configExt: \(configExt ?? "nil")")

        if let fontAsset = asset as? RiveFontAsset {
            print("[SwiftRiveBridge]   🔤 Asset is a font")
            let extensions = [configExt, asset.fileExtension(), "ttf", "otf"].compactMap { $0 }
            print("[SwiftRiveBridge]   🔍 Trying extensions: \(extensions)")
            for ext in extensions {
                let fileURL = assetDir.appendingPathComponent("\(resourceName).\(ext)")
                print("[SwiftRiveBridge]     📂 Trying: \(fileURL.path)")
                if let fontData = try? Data(contentsOf: fileURL) {
                    print("[SwiftRiveBridge]     ✅ Font data found, size: \(fontData.count) bytes")
                    let decodedFont = factory.decodeFont(fontData)
                    fontAsset.font(decodedFont)
                    print("[SwiftRiveBridge]   ✅ Font injected: \(uniqueName) from \(resourceName).\(ext)")
                    return true
                } else {
                    print("[SwiftRiveBridge]     ⚠️ Not found: \(fileURL.path)")
                }
            }
            print("[SwiftRiveBridge]   ❌ Font not found: \(uniqueName), resource: \(rawName)")
            return false
        }

        if let imageAsset = asset as? RiveImageAsset {
            print("[SwiftRiveBridge]   🖼️ Asset is an image")
            let extensions = [configExt, asset.fileExtension(), "webp", "png", "jpg", "jpeg"].compactMap { $0 }
            print("[SwiftRiveBridge]   🔍 Trying extensions: \(extensions)")
            for ext in extensions {
                let fileURL = assetDir.appendingPathComponent("\(resourceName).\(ext)")
                print("[SwiftRiveBridge]     📂 Trying: \(fileURL.path)")
                if let imageData = try? Data(contentsOf: fileURL) {
                    print("[SwiftRiveBridge]     ✅ Image data found, size: \(imageData.count) bytes")
                    let decoded = factory.decodeImage(imageData)
                    imageAsset.renderImage(decoded)
                    print("[SwiftRiveBridge]   ✅ Image injected: \(uniqueName) from \(resourceName).\(ext)")
                    return true
                } else {
                    print("[SwiftRiveBridge]     ⚠️ Not found: \(fileURL.path)")
                }
            }
            print("[SwiftRiveBridge]   ❌ Image not found: \(uniqueName), resource: \(rawName)")
            return false
        }

        print("[SwiftRiveBridge]   ⚠️ Unhandled asset type: \(type(of: asset)) for \(uniqueName)")
        return false
    }
}