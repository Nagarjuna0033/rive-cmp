# iOS KMP Rive Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement iOS actual declarations for RiveProvider/RiveComponent using a Swift bridge pattern, so shared KMP code renders Rive animations on iOS.

**Architecture:** Kotlin defines an open class (`IOSRiveHandle`) and interface (`IOSRiveBridge`) in nativeMain. The iOS app provides a Swift implementation that uses RiveModel/RiveViewModel for rendering and RiveDataBindingViewModelInstance for property binding. RiveComponent wraps the Swift-provided UIView in a Compose `UIKitView`.

**Tech Stack:** Kotlin/Native, Compose Multiplatform (UIKitView), Swift, RiveRuntime xcframework

---

### Task 1: Create IOSRiveBridge + IOSRiveHandle (Kotlin bridge API)

**Files:**
- Create: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveBridge.kt`

**Step 1: Write the bridge API**

```kotlin
package com.arjun.core.rive

import platform.UIKit.UIView

// Wraps a single Rive view instance — subclassed in Swift
open class IOSRiveHandle {
    open fun getUIView(): UIView = UIView()
    open fun setStringProperty(name: String, value: String) {}
    open fun setEnumProperty(name: String, value: String) {}
    open fun setBooleanProperty(name: String, value: Boolean) {}
    open fun setNumberProperty(name: String, value: Float) {}
    open fun fireTrigger(name: String) {}
    open fun destroy() {}
}

// Factory interface — implemented in Swift
interface IOSRiveBridge {
    fun preloadFiles(configs: List<RiveFileConfig>): Boolean
    fun createHandle(resourceName: String): IOSRiveHandle?
    fun isFileLoaded(resourceName: String): Boolean
    fun clearAll()
}

// iOS app registers its bridge here before Compose starts
object IOSRivePlatform {
    var bridge: IOSRiveBridge? = null
}
```

**Step 2: Verify it compiles**

Run: `./gradlew :core:rive:compileKotlinIosArm64`

---

### Task 2: Create IOSRiveController

**Files:**
- Create: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveController.kt`

**Step 1: Write the controller**

```kotlin
package com.arjun.core.rive

class IOSRiveController(
    private val handle: IOSRiveHandle
) : RiveController {

    override fun setString(propertyName: String, value: String) {
        runCatching { handle.setStringProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setString($propertyName) error: ${it.message}") }
    }

    override fun setEnum(propertyName: String, value: String) {
        runCatching { handle.setEnumProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setEnum($propertyName) error: ${it.message}") }
    }

    override fun setBoolean(propertyName: String, value: Boolean) {
        runCatching { handle.setBooleanProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setBoolean($propertyName) error: ${it.message}") }
    }

    override fun setNumber(propertyName: String, value: Float) {
        runCatching { handle.setNumberProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setNumber($propertyName) error: ${it.message}") }
    }

    override fun fireTrigger(triggerName: String) {
        runCatching { handle.fireTrigger(triggerName) }
            .onFailure { println("[IOSRiveController] fireTrigger($triggerName) error: ${it.message}") }
    }

    override fun destroy() {
        handle.destroy()
    }
}
```

---

### Task 3: Create IOSRiveFileManager

**Files:**
- Create: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveFileManager.kt`

**Step 1: Write the file manager**

```kotlin
package com.arjun.core.rive

class IOSRiveFileManager(
    private val bridge: IOSRiveBridge
) : RiveFileManager {

    override suspend fun preloadFile(config: RiveFileConfig): RiveLoadState {
        return try {
            val success = bridge.preloadFiles(listOf(config))
            if (success) RiveLoadState.Success
            else RiveLoadState.Error("Failed to preload ${config.resourceName}")
        } catch (e: Exception) {
            RiveLoadState.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun preloadAll(configs: List<RiveFileConfig>): RiveLoadState {
        return try {
            val success = bridge.preloadFiles(configs)
            if (success) RiveLoadState.Success
            else RiveLoadState.Error("Failed to preload files")
        } catch (e: Exception) {
            RiveLoadState.Error(e.message ?: "Unknown error")
        }
    }

    override fun isFileLoaded(resourceName: String): Boolean =
        bridge.isFileLoaded(resourceName)

    override fun getLoadState(resourceName: String): RiveLoadState =
        if (bridge.isFileLoaded(resourceName)) RiveLoadState.Success
        else RiveLoadState.Idle

    override fun clearAll() = bridge.clearAll()
}
```

---

### Task 4: Implement RiveExpects.native.kt (actual Composables)

**Files:**
- Modify: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt`

**Step 1: Replace empty stubs with real implementation**

```kotlin
package com.arjun.core.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView

@Composable
actual fun RiveProvider(
    configs: List<RiveFileConfig>,
    loadingContent: @Composable (() -> Unit),
    errorContent: @Composable ((String) -> Unit),
    content: @Composable (() -> Unit)
) {
    val bridge = IOSRivePlatform.bridge
    if (bridge == null) {
        errorContent("IOSRivePlatform.bridge not configured")
        return
    }

    val fileManager = remember(bridge) { IOSRiveFileManager(bridge) }
    var loadState by remember { mutableStateOf<RiveLoadState>(RiveLoadState.Loading) }

    LaunchedEffect(configs) {
        loadState = fileManager.preloadAll(configs)
    }

    CompositionLocalProvider(
        LocalRiveFileManager provides fileManager
    ) {
        when (val state = loadState) {
            is RiveLoadState.Loading -> loadingContent()
            is RiveLoadState.Error -> errorContent(state.message)
            is RiveLoadState.Success -> content()
            is RiveLoadState.Idle -> loadingContent()
        }
    }
}

@Composable
actual fun RiveComponent(
    resourceName: String,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?
) {
    val bridge = IOSRivePlatform.bridge ?: return

    val handle = remember(resourceName) {
        bridge.createHandle(resourceName)
    } ?: return

    val controller = remember(handle) { IOSRiveController(handle) }

    LaunchedEffect(handle, config) {
        controller.applyConfig(config)
    }

    LaunchedEffect(controller) {
        onControllerReady?.invoke(controller)
    }

    DisposableEffect(handle) {
        onDispose { handle.destroy() }
    }

    UIKitView(
        factory = { handle.getUIView() },
        modifier = modifier
    )
}
```

**Step 2: Verify it compiles**

Run: `./gradlew :core:rive:compileKotlinIosSimulatorArm64`

**Step 3: Commit Kotlin side**

```bash
git add core/rive/src/nativeMain/
git commit -m "implement iOS KMP Rive bridge and actual Composables"
```

---

### Task 5: Create Swift bridge implementation

**Files:**
- Create: `iosApp/iosApp/RiveBridge.swift`

**Step 1: Write the Swift bridge**

```swift
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
        if let vmi = getViewModelInstance() {
            if let prop = vmi.stringProperty(fromPath: name) {
                prop.value = value
                return
            }
        }
        try? riveViewModel.setTextRunValue(name, textValue: value)
    }

    override func setEnumProperty(name: String, value: String) {
        guard let vmi = getViewModelInstance(),
              let prop = vmi.enumProperty(fromPath: name) else { return }
        prop.value = value
    }

    override func setBooleanProperty(name: String, value: Bool) {
        guard let vmi = getViewModelInstance(),
              let prop = vmi.booleanProperty(fromPath: name) else { return }
        prop.value = value
    }

    override func setNumberProperty(name: String, value: Float) {
        guard let vmi = getViewModelInstance(),
              let prop = vmi.numberProperty(fromPath: name) else { return }
        prop.value = value
    }

    override func fireTrigger(name: String) {
        if let vmi = getViewModelInstance(),
           let prop = vmi.triggerProperty(fromPath: name) {
            prop.trigger()
            return
        }
        riveViewModel.triggerInput(name)
    }

    override func destroy() {
        hostingController = nil
    }

    // MARK: - Private

    private func getViewModelInstance() -> RiveDataBindingViewModelInstance? {
        // Access VMI through the artboard's state machine
        // This depends on how your Rive file is set up
        return riveViewModel.riveModel?.stateMachine?.viewModelInstance
    }
}

// MARK: - SwiftRiveBridge

class SwiftRiveBridge: NSObject, IOSRiveBridge {

    private var loadedModels: [String: RiveModel] = [:]
    private var assetMappings: [String: [RiveAssetConfig]] = [:]

    func preloadFiles(configs: [RiveFileConfig]) -> Bool {
        for config in configs {
            if loadedModels[config.resourceName] != nil {
                continue // already loaded
            }

            // Store asset mappings for this file
            let assets = config.assets
            var assetMap: [String: RiveAssetConfig] = [:]
            for asset in assets {
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
        assetMappings.removeAll()
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
            // Try without extension for .ttf/.otf
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
```

**Note:** The `getViewModelInstance()` method in `SwiftRiveHandle` accesses the VMI via `riveViewModel.riveModel?.stateMachine?.viewModelInstance`. This path depends on the RiveRuntime Swift API. If `RiveViewModel` doesn't expose `riveModel` publicly, you may need to:
- Access the VMI through the underlying `RiveFile` Obj-C API instead
- Or store a separate `RiveFile` reference and get the VMI from there

Verify this by checking autocomplete in Xcode for `RiveViewModel` properties.

---

### Task 6: Register bridge in iOS app startup

**Files:**
- Modify: `iosApp/iosApp/iOSApp.swift`

**Step 1: Add bridge registration**

Add this import and init block to `iOSApp.swift`:

```swift
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
```

**Step 2: Commit Swift side**

```bash
git add iosApp/iosApp/RiveBridge.swift iosApp/iosApp/iOSApp.swift
git commit -m "add Swift bridge implementation for iOS Rive KMP"
```

---

### Task 7: Build and test on iOS simulator

**Step 1: Build the full project**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`

**Step 2: Fix any compilation errors**

Common issues:
- `UIKitView` import path may differ — check `androidx.compose.ui.interop.UIKitView`
- `RiveDataBindingViewModelInstance` Swift access — verify the VMI accessor path in Xcode
- Framework export — ensure `ComposeApp` framework exports `IOSRivePlatform`, `IOSRiveBridge`, `IOSRiveHandle`

**Step 3: Run on iOS simulator via Xcode**

Open `iosApp/iosApp.xcodeproj` in Xcode and run on simulator.

**Step 4: Verify rendering**

- RiveProvider should show loading then content
- RiveComponent should render the .riv file with fonts and images injected
- Property binding (strings, enums) should update the animation

**Step 5: Final commit**

```bash
git add -A
git commit -m "iOS KMP Rive integration complete with Swift bridge"
```
