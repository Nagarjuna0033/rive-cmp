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

    override func setStringProperty(name: String, value: String) {
        // Try data binding VMI first, fall back to text runs
        if let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance {
            if let prop = vmi.stringProperty(fromPath: name) {
                prop.value = value
                return
            }
        }
        try? riveViewModel.setTextRunValue(name, textValue: value)
    }

    override func setEnumProperty(name: String, value: String) {
        guard let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
              let prop = vmi.enumProperty(fromPath: name) else { return }
        prop.value = value
    }

    override func setBooleanProperty(name: String, value: Bool) {
        guard let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
              let prop = vmi.booleanProperty(fromPath: name) else { return }
        prop.value = value
    }

    override func setNumberProperty(name: String, value: Float) {
        guard let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
              let prop = vmi.numberProperty(fromPath: name) else { return }
        prop.value = value
    }

    override func fireTrigger(name: String) {
        if let vmi = riveViewModel.riveModel?.stateMachine?.viewModelInstance,
           let prop = vmi.triggerProperty(fromPath: name) {
            prop.trigger()
            return
        }
        riveViewModel.triggerInput(name)
    }

    override func destroy() {
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
