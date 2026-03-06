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
            riveModel = try RiveModel(fileName: "testing")
        } catch {
            print("Failed loading Rive model:", error.localizedDescription)
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
            setupRive()
        }
    }

    func setupRive() {

        let vm = RiveViewModel(
            riveModel,
            animationName: "Press"
        )


        do {
            try vm.setTextRunValue("Button Text", path: "", textValue: "Testing")
        } catch {
            print("Failed to set text run:", error)
        }

        riveViewModel = vm
    }
}