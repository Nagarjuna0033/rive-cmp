import SwiftUI
import RiveRuntime
import ComposeApp

@main
struct iOSApp: App {

    init() {
        IOSRivePlatform.shared.bridge = SwiftRiveBridge()
    }

    var body: some Scene {
        WindowGroup {
            RiveBatchListView()
        }
    }
}

struct RiveBatchListView: View {

    @State private var riveModel: RiveModel? = nil

    var body: some View {

        VStack {

            Text("Batched Testing")
                .padding()

            if let riveModel = riveModel {

                ScrollView {
                    LazyVStack(spacing: 15) {

                        ForEach(0..<25, id: \.self) { index in
                            BatchedRivePanel(
                                riveModel: riveModel,
                                index: index
                            )
                                .frame(height: 50)
                        }

                    }
                    .padding(.horizontal)
                }
            }
        }
        .onAppear {
            loadRiveFile()
        }
    }

    func loadRiveFile() {

        do {
            riveModel = try RiveModel(
                fileName: "testing",
                loadCdn: false,
                customLoader: { asset, data, factory in

                    print("[RiveLoader] Asset callback: type=\(type(of: asset)), name=\(asset.name()), uniqueName=\(asset.uniqueName()), ext=\(asset.fileExtension())")

                    // Inject font
                    if let fontAsset = asset as? RiveFontAsset {

                        // Try uniqueName first (e.g. "Outfit-4229794"), then fall back to name
                        let candidates = [asset.uniqueName(), asset.name()]
                        var fontData: Data?

                        for candidate in candidates {
                            if let url = Bundle.main.url(
                                forResource: candidate,
                                withExtension: asset.fileExtension()
                            ) {
                                fontData = try? Data(contentsOf: url)
                                if fontData != nil {
                                    print("[RiveLoader] Found font at: \(url.lastPathComponent)")
                                    break
                                }
                            }
                        }

                        guard let bytes = fontData else {
                            print("[RiveLoader] Font not found in bundle. Tried: \(candidates) with ext: \(asset.fileExtension())")
                            return false
                        }

                        let decodedFont = factory.decodeFont(bytes)
                        fontAsset.font(decodedFont)
                        print("[RiveLoader] Font injected successfully: \(asset.uniqueName())")
                        return true
                    }

                    // Inject image
                    if let imageAsset = asset as? RiveImageAsset {

                        let candidates = [asset.uniqueName(), asset.name()]

                        for candidate in candidates {
                            if let url = Bundle.main.url(
                                forResource: candidate,
                                withExtension: asset.fileExtension()
                            ),
                               let imageData = try? Data(contentsOf: url) {
                                let decodedImage = factory.decodeImage(imageData)
                                imageAsset.renderImage(decodedImage)
                                print("[RiveLoader] Image injected: \(asset.uniqueName())")
                                return true
                            }
                        }

                        print("[RiveLoader] Image not found: \(candidates) with ext: \(asset.fileExtension())")
                        return false
                    }

                    return false
                }
            )

        } catch {
            print("Failed to load Rive model:", error)
        }
    }
}

struct BatchedRivePanel: View {

    let riveModel: RiveModel
    let index: Int

    @State private var riveViewModel: RiveViewModel?

    var body: some View {

        Group {
            if let viewModel = riveViewModel {
                viewModel.view()
                    .onTapGesture {
                        viewModel.triggerInput("Press")
                    }
            } else {
                Color.clear
            }
        }
        .onAppear {
            if riveViewModel == nil {
                setupRive()
            }
        }
    }

    func setupRive() {

        let vm = RiveViewModel(
            riveModel,
            animationName: "Press",
            autoPlay: false
        )

        do {
            try vm.setTextRunValue(
                "Run 1",
                textValue: "Testing"
            )
        } catch {
            print("Failed to set text run:", error)
        }

        riveViewModel = vm
    }
}