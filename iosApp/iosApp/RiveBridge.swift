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
    private var containerView: UIView?
    private var pendingOperations: [() -> Void] = []
    private var boundVMI: RiveDataBindingViewModel.Instance?
    private var triggerListenerIds: [UUID] = []
    private var isDestroyed = false

    init(riveModel: RiveModel, artboardName: String?, stateMachineName: String?) {
        self.riveModel = riveModel
        self.riveViewModel = RiveViewModel(
            riveModel,
            stateMachineName: stateMachineName,
            autoPlay: true,
            artboardName: artboardName
        )
        super.init()

        riveModel.enableAutoBind { [weak self] instance in
            DispatchQueue.main.async {
                guard let self, !self.isDestroyed else { return }
                self.boundVMI = instance
                if !self.pendingOperations.isEmpty {
                    let ops = self.pendingOperations
                    self.pendingOperations.removeAll()
                    for op in ops {
                        op()
                    }
                }
            }
        }
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
        riveViewModel.fit = mapFit(fit)
        riveViewModel.alignment = mapAlignment(alignment)

        if let existing = containerView {
            return existing
        }

        let riveView = riveViewModel.createRiveView()
        containerView = riveView
        return riveView
    }

    // MARK: - VMI access

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
                self.riveViewModel.riveView?.advance(delta: 0)
            }
        }
    }

    override func setEnumProperty(name: String, value: String) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.enumProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
            }
        }
    }

    override func setBooleanProperty(name: String, value: Bool) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.booleanProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
            }
        }
    }

    override func setNumberProperty(name: String, value: Float) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.numberProperty(fromPath: name) {
                prop.value = value
                self.riveViewModel.riveView?.advance(delta: 0)
            }
        }
    }

    override func fireTrigger(name: String) {
        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.triggerProperty(fromPath: name) {
                prop.trigger()
            } else {
                self.riveViewModel.triggerInput(name)
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
            }
        }
    }

    override func setImageProperty(name: String, pngBytes: KotlinByteArray) {
        let length = Int(pngBytes.size)
        var bytes = [UInt8](repeating: 0, count: length)
        for i in 0..<length {
            bytes[i] = UInt8(bitPattern: pngBytes.get(index: Int32(i)))
        }
        let data = Data(bytes)

        guard let uiImage = UIImage(data: data) else { return }
        guard let riveImage = RiveRenderImage(image: uiImage, format: .png) else { return }

        executeWithVMI { [weak self] in
            guard let self, let vmi = self.boundVMI else { return }
            if let prop = vmi.imageProperty(fromPath: name) {
                prop.setValue(riveImage)
                self.riveViewModel.riveView?.advance(delta: 0)
            }
        }
    }

    override func destroy() {
        isDestroyed = true
        pendingOperations.removeAll()
        triggerListenerIds.removeAll()
        riveModel.disableAutoBind()
        boundVMI = nil
        containerView = nil
    }
}

// MARK: - SwiftRiveBridge

class SwiftRiveBridge: NSObject, IOSRiveBridge {

    private var loadedConfigs: [String: [String: RiveAssetConfig]] = [:]

    private static let assetDir: URL = {
        let filesDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return filesDir.appendingPathComponent("app_assets/asset")
    }()

    func preloadFiles(configs: [RiveFileConfig]) -> Bool {
        for config in configs {
            if loadedConfigs[config.resourceName] != nil { continue }

            var assetMap: [String: RiveAssetConfig] = [:]
            for asset in config.assets {
                assetMap[asset.assetId] = asset
            }

            let rivFileURL = Self.assetDir.appendingPathComponent(config.resourceName)
            guard FileManager.default.fileExists(atPath: rivFileURL.path) else { return false }

            loadedConfigs[config.resourceName] = assetMap
        }
        return true
    }

    func createHandle(
        resourceName: String,
        artboardName: String?,
        stateMachineName: String?
    ) -> IOSRiveHandle? {
        guard let assetMap = loadedConfigs[resourceName] else { return nil }

        let rivFileURL = Self.assetDir.appendingPathComponent(resourceName)
        guard let rivData = try? Data(contentsOf: rivFileURL) else { return nil }

        do {
            let riveFile = try RiveFile(
                data: rivData,
                loadCdn: false,
                customAssetLoader: { [assetMap] asset, data, factory in
                    Self.loadAsset(
                        asset: asset,
                        data: data,
                        factory: factory,
                        assetMap: assetMap
                    )
                }
            )
            let model = RiveModel(riveFile: riveFile)
            return SwiftRiveHandle(
                riveModel: model,
                artboardName: artboardName,
                stateMachineName: stateMachineName
            )
        } catch {
            return nil
        }
    }

    func isFileLoaded(resourceName: String) -> Bool {
        loadedConfigs[resourceName] != nil
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

        guard let config = assetMap[uniqueName] ?? assetMap[assetName] else {
            // No config mapping — decode embedded data directly if available
            if !data.isEmpty {
                if let fontAsset = asset as? RiveFontAsset {
                    let decodedFont = factory.decodeFont(data)
                    fontAsset.font(decodedFont)
                    return true
                }
                if let imageAsset = asset as? RiveImageAsset {
                    let decoded = factory.decodeImage(data)
                    imageAsset.renderImage(decoded)
                    return true
                }
            }
            return false
        }

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

        if let fontAsset = asset as? RiveFontAsset {
            let extensions = [configExt, asset.fileExtension(), "ttf", "otf"].compactMap { $0 }
            for ext in extensions {
                let fileURL = assetDir.appendingPathComponent("\(resourceName).\(ext)")
                if let fontData = try? Data(contentsOf: fileURL) {
                    let decodedFont = factory.decodeFont(fontData)
                    fontAsset.font(decodedFont)
                    return true
                }
            }
            return false
        }

        if let imageAsset = asset as? RiveImageAsset {
            let extensions = [configExt, asset.fileExtension(), "webp", "png", "jpg", "jpeg"].compactMap { $0 }
            for ext in extensions {
                let fileURL = assetDir.appendingPathComponent("\(resourceName).\(ext)")
                if let imageData = try? Data(contentsOf: fileURL) {
                    let decoded = factory.decodeImage(imageData)
                    imageAsset.renderImage(decoded)
                    return true
                }
            }
            return false
        }

        return false
    }
}

// MARK: - RiveBridgeFromBundle
// Loads .riv files and assets from the app Bundle instead of the Documents directory.

class SwiftRiveBridgeFromBundle: NSObject, IOSRiveBridge {

    private var loadedConfigs: [String: [String: RiveAssetConfig]] = [:]

    func preloadFiles(configs: [RiveFileConfig]) -> Bool {
        for config in configs {
            if loadedConfigs[config.resourceName] != nil { continue }

            var assetMap: [String: RiveAssetConfig] = [:]
            for asset in config.assets {
                assetMap[asset.assetId] = asset
            }

            let fileName = Self.stripRivExtension(config.resourceName)
            guard Bundle.main.url(forResource: fileName, withExtension: "riv") != nil else {
                return false
            }

            loadedConfigs[config.resourceName] = assetMap
        }
        return true
    }

    func createHandle(
        resourceName: String,
        artboardName: String?,
        stateMachineName: String?
    ) -> IOSRiveHandle? {
        guard let assetMap = loadedConfigs[resourceName] else { return nil }

        let fileName = Self.stripRivExtension(resourceName)

        guard let rivURL = Bundle.main.url(forResource: fileName, withExtension: "riv"),
              let rivData = try? Data(contentsOf: rivURL) else {
            return nil
        }

        do {
            let riveFile = try RiveFile(
                data: rivData,
                loadCdn: false,
                customAssetLoader: { [assetMap] asset, data, factory in
                    Self.loadAsset(
                        asset: asset,
                        data: data,
                        factory: factory,
                        assetMap: assetMap
                    )
                }
            )
            let model = RiveModel(riveFile: riveFile)
            return SwiftRiveHandle(
                riveModel: model,
                artboardName: artboardName,
                stateMachineName: stateMachineName
            )
        } catch {
            return nil
        }
    }

    func isFileLoaded(resourceName: String) -> Bool {
        loadedConfigs[resourceName] != nil
    }

    func clearAll() {
        loadedConfigs.removeAll()
    }

    // MARK: - Helpers

    private static func stripRivExtension(_ name: String) -> String {
        name.hasSuffix(".riv") ? String(name.dropLast(4)) : name
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

        guard let config = assetMap[uniqueName] ?? assetMap[assetName] else {
            // No config mapping — decode embedded data directly if available
            if !data.isEmpty {
                if let fontAsset = asset as? RiveFontAsset {
                    let decodedFont = factory.decodeFont(data)
                    fontAsset.font(decodedFont)
                    return true
                }
                if let imageAsset = asset as? RiveImageAsset {
                    let decoded = factory.decodeImage(data)
                    imageAsset.renderImage(decoded)
                    return true
                }
            }
            return false
        }

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

        if let fontAsset = asset as? RiveFontAsset {
            let extensions = [configExt, asset.fileExtension(), "ttf", "otf"].compactMap { $0 }
            for ext in extensions {
                if let url = Bundle.main.url(forResource: resourceName, withExtension: ext),
                   let fontData = try? Data(contentsOf: url) {
                    let decodedFont = factory.decodeFont(fontData)
                    fontAsset.font(decodedFont)
                    return true
                }
            }
            return false
        }

        if let imageAsset = asset as? RiveImageAsset {
            let extensions = [configExt, asset.fileExtension(), "webp", "png", "jpg", "jpeg"].compactMap { $0 }
            for ext in extensions {
                if let url = Bundle.main.url(forResource: resourceName, withExtension: ext),
                   let imageData = try? Data(contentsOf: url) {
                    let decoded = factory.decodeImage(imageData)
                    imageAsset.renderImage(decoded)
                    return true
                }
            }
            return false
        }

        return false
    }
}
