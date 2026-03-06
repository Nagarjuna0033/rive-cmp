import SwiftUI
import RiveRuntime

@main
struct iOSApp: App {
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

                    // Inject font
                    if asset is RiveFontAsset {

                        guard let url = Bundle.main.url(
                            forResource: asset.uniqueName(),
                            withExtension: asset.fileExtension()
                        ) else {
                            print("Font not found: \(asset.uniqueName())")
                            return false
                        }

                        guard let fontData = try? Data(contentsOf: url) else {
                            print("Failed to read font")
                            return false
                        }

                        (asset as! RiveFontAsset).font(
                            factory.decodeFont(fontData)
                        )

                        return true
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