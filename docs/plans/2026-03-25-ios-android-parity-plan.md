# iOS–Android Parity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Wire `artboardName`/`stateMachineName` through to iOS RiveRuntime SDK and fix config flash by applying config synchronously — matching Android behavior.

**Architecture:** Three iOS-only changes: (1) add params to Kotlin bridge interface + Swift implementation, (2) pass them from `RiveComponent` to `createHandle()`, (3) replace async `LaunchedEffect` config with sync `remember` + `applyConfig()`. Zero changes to common or Android code.

**Tech Stack:** Kotlin Multiplatform (nativeMain), Swift (iosApp), RiveRuntime iOS SDK

---

### Task 1: Update IOSRiveBridge interface to accept artboardName/stateMachineName

**Files:**
- Modify: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveBridge.kt:19-24`

**Context:** `IOSRiveBridge` is the Kotlin interface that Swift implements. `createHandle()` currently only takes `resourceName`. We need to add `artboardName` and `stateMachineName` with null defaults so existing callers aren't broken.

**Step 1: Modify the interface**

Change `createHandle` in the `IOSRiveBridge` interface:

```kotlin
interface IOSRiveBridge {
    fun preloadFiles(configs: List<RiveFileConfig>): Boolean
    fun createHandle(
        resourceName: String,
        artboardName: String? = null,
        stateMachineName: String? = null,
    ): IOSRiveHandle?
    fun isFileLoaded(resourceName: String): Boolean
    fun clearAll()
}
```

**Step 2: Verify compilation**

This is a Kotlin interface with default params. Existing Swift conformances will break (Task 2 fixes that). No test to write — this is a type-level change verified by the compiler.

**Step 3: Commit**

```bash
git add core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveBridge.kt
git commit -m "feat(ios): add artboardName/stateMachineName params to IOSRiveBridge.createHandle"
```

---

### Task 2: Update SwiftRiveHandle and SwiftRiveBridge to use artboardName/stateMachineName

**Files:**
- Modify: `iosApp/iosApp/RiveBridge.swift:9-43` (SwiftRiveHandle init)
- Modify: `iosApp/iosApp/RiveBridge.swift:227-253` (SwiftRiveBridge.createHandle)

**Context:** `SwiftRiveHandle` currently creates `RiveViewModel(riveModel, autoPlay: true)` without passing artboard/stateMachine names. The RiveRuntime SDK's `RiveViewModel` already supports these params:
```swift
RiveViewModel(riveModel, stateMachineName: String?, autoPlay: Bool, artboardName: String?)
```

**Step 1: Update SwiftRiveHandle init**

Change the `SwiftRiveHandle` initializer to accept and use both params:

```swift
class SwiftRiveHandle: IOSRiveHandle {

    private let riveModel: RiveModel
    private let riveViewModel: RiveViewModel
    private var hostingController: UIHostingController<AnyView>?
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

        // enableAutoBind callback — unchanged from current code
        riveModel.enableAutoBind { [weak self] instance in
            DispatchQueue.main.async {
                guard let self, !self.isDestroyed else { return }
                self.boundVMI = instance
                print("[SwiftRiveHandle] AutoBind VMI received, \(instance.propertyCount) properties:")
                for prop in instance.properties {
                    print("[SwiftRiveHandle]   - \"\(prop.name)\" (type: \(prop.type.rawValue))")
                }
                if !self.pendingOperations.isEmpty {
                    print("[SwiftRiveHandle] Flushing \(self.pendingOperations.count) pending operations")
                    let ops = self.pendingOperations
                    self.pendingOperations.removeAll()
                    for op in ops { op() }
                }
            }
        }
    }
    // ... rest of class unchanged
}
```

**Step 2: Update SwiftRiveBridge.createHandle()**

Change `createHandle` to accept and forward both params:

```swift
func createHandle(
    resourceName: String,
    artboardName: String?,
    stateMachineName: String?
) -> IOSRiveHandle? {
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
        return SwiftRiveHandle(
            riveModel: model,
            artboardName: artboardName,
            stateMachineName: stateMachineName
        )
    } catch {
        print("[SwiftRiveBridge] Failed to create model for \(resourceName): \(error)")
        return nil
    }
}
```

**Step 3: Commit**

```bash
git add iosApp/iosApp/RiveBridge.swift
git commit -m "feat(ios): pass artboardName/stateMachineName to RiveViewModel in Swift bridge"
```

---

### Task 3: Wire params from RiveComponent and fix config flash

**Files:**
- Modify: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt:57-124`

**Context:** `RiveComponent` receives `artboardName`, `stateMachineName`, and `config` from common code but:
1. Doesn't pass artboardName/stateMachineName to `bridge.createHandle()`
2. Applies config async in `LaunchedEffect` (causes state A flash)
3. Manually loops properties instead of using `controller.applyConfig(config)`

We fix all three in one pass.

**Step 1: Update RiveComponent**

Replace the entire `RiveComponent` actual function body:

```kotlin
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun RiveComponent(
    resourceName: String,
    instanceKey: String,
    viewModelName: String,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?,
    alignment: RiveAlignment,
    autoPlay: Boolean,
    artboardName: String?,
    fit: RiveFit,
    stateMachineName: String?,
    batched: Boolean,
) {
    val bridge = IOSRivePlatform.bridge ?: return

    // Pass artboardName and stateMachineName to createHandle
    val handle = remember(resourceName, instanceKey) {
        bridge.createHandle(resourceName, artboardName, stateMachineName)
    } ?: return

    val controller = remember(handle) { IOSRiveController(handle) }

    // Notify controller ready once per handle
    LaunchedEffect(handle) {
        onControllerReady?.invoke(controller)
    }

    // Apply config SYNCHRONOUSLY during composition — no flash.
    // Uses common applyConfig() instead of manual loops.
    // Same pattern as Android: remember(handle, config) { ... }
    @Suppress("RememberReturnType")
    remember(handle, config) {
        controller.applyConfig(config)
    }

    // Trigger listeners — unchanged
    LaunchedEffect(handle) {
        config.triggers.forEach { trigger ->
            handle.addTriggerListener(trigger) {
                eventCallback?.onTriggerAnimation(trigger)
            }
        }
    }

    DisposableEffect(handle) {
        onDispose { handle.destroy() }
    }

    UIKitView(
        factory = { handle.getUIView() },
        modifier = modifier,
    )
}
```

**Key changes from current code:**
1. Line `bridge.createHandle(resourceName, artboardName, stateMachineName)` — was `bridge.createHandle(resourceName)`
2. `remember(handle, config) { controller.applyConfig(config) }` — was `LaunchedEffect(handle, config) { manual loops }`
3. Removed `import androidx.compose.foundation.layout.height/width` and `import androidx.compose.ui.unit.dp` if they become unused

**Step 2: Commit**

```bash
git add core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt
git commit -m "feat(ios): wire artboardName/stateMachineName and fix config flash with sync applyConfig"
```

---

### Task 4: Test on device

**Steps:**

1. Build the iOS app in Xcode
2. Test with a .riv file that has **multiple artboards** — pass `artboardName` and verify correct artboard renders
3. Test with `stateMachineName` — verify correct state machine is active
4. Test with config that sets **state B** — verify **no flash** of state A on load
5. Test with **default params** (null artboardName, null stateMachineName) — verify backward compatibility (same behavior as before)
6. Test trigger callbacks still work

**Expected results:**
- Correct artboard selected when `artboardName` is provided
- Correct state machine active when `stateMachineName` is provided
- State B loads directly without any visible flash of state A
- Null params behave identically to current behavior (default artboard, default state machine)
