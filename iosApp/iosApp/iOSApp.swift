import SwiftUI
import RiveRuntime
import ComposeApp

@main
struct iOSApp: App {

    init() {
        IOSRivePlatform.shared.bridge = SwiftRiveBridgeFromBundle()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
