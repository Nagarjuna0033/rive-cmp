# iOS–Android Parity: artboardName, stateMachineName, Config Flash Fix

## Goal

Bring iOS Rive integration to parity with Android on three fronts:
1. Wire `artboardName` and `stateMachineName` through to the native RiveRuntime SDK
2. Fix config flash (state A visible before state B) by applying config synchronously
3. Use common `applyConfig()` instead of manual property loops

## Architecture

All changes are in iOS-only files (`nativeMain/`, `iosApp/`). No changes to common interfaces or Android code.

```
RiveComponent(artboardName, stateMachineName, config)
  ↓
RiveExpects.native.kt
  ├── bridge.createHandle(resourceName, artboardName, stateMachineName)
  └── remember(handle, config) { controller.applyConfig(config) }  // sync, not async
  ↓
IOSRiveBridge.createHandle(resourceName, artboardName, stateMachineName)
  ↓
SwiftRiveBridge.createHandle(resourceName, artboardName, stateMachineName)
  ↓
SwiftRiveHandle(riveModel, artboardName, stateMachineName)
  ↓
RiveViewModel(riveModel, stateMachineName: stateMachineName, autoPlay: true, artboardName: artboardName)
```

## Changes by File

### 1. `IOSRiveBridge.kt` (nativeMain)

Add `artboardName` and `stateMachineName` to `createHandle()`:

```kotlin
// IOSRiveHandle — add params to open class
open class IOSRiveHandle {
    // existing methods unchanged
}

// IOSRiveBridge — update interface
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

### 2. `RiveBridge.swift` (iosApp)

**SwiftRiveHandle** — accept and use artboard/stateMachine names:

```swift
init(riveModel: RiveModel, artboardName: String?, stateMachineName: String?) {
    self.riveModel = riveModel
    self.riveViewModel = RiveViewModel(
        riveModel,
        stateMachineName: stateMachineName,
        autoPlay: true,
        artboardName: artboardName
    )
    // ... rest unchanged
}
```

**SwiftRiveBridge.createHandle()** — accept and forward:

```swift
func createHandle(
    resourceName: String,
    artboardName: String?,
    stateMachineName: String?
) -> IOSRiveHandle? {
    // ... model creation unchanged ...
    return SwiftRiveHandle(
        riveModel: model,
        artboardName: artboardName,
        stateMachineName: stateMachineName
    )
}
```

### 3. `RiveExpects.native.kt` (nativeMain)

Two changes:

**a) Pass artboardName/stateMachineName to createHandle:**
```kotlin
val handle = remember(resourceName, instanceKey) {
    bridge.createHandle(resourceName, artboardName, stateMachineName)
} ?: return
```

**b) Replace async LaunchedEffect with sync remember for config:**
```kotlin
// BEFORE (async — flashes state A):
LaunchedEffect(handle, config) {
    config.booleans.forEach { (k, v) -> controller.setBoolean(k, v) }
    config.strings.forEach { (k, v) -> controller.setString(k, v) }
    config.enums.forEach { (k, v) -> controller.setEnum(k, v) }
    config.numbers.forEach { (k, v) -> controller.setNumber(k, v) }
}

// AFTER (sync — state B from first frame):
@SuppressLint("RememberReturnType")
remember(handle, config) {
    controller.applyConfig(config)
}
```

## What Does NOT Change

- `RiveExpects.kt` (common) — expect signatures already have all params
- `RiveInterface.kt` (common) — `applyConfig()` already exists
- `RiveExpects.android.kt` — Android implementation untouched
- `IOSRiveController.kt` — already delegates to handle correctly
- `IOSRiveFileManager.kt` — no changes needed

## Risk Assessment

- **Zero risk to Android** — all changes in iOS-only compilation units
- **Low risk to iOS** — RiveViewModel already supports these params, we're just passing them through
- **Config sync** — same pattern proven on Android. iOS `executeWithVMI` buffers operations until VMI is ready, so sync config application will be buffered and flushed in order

## Testing

1. Load a .riv file with multiple artboards — verify `artboardName` selects the correct one
2. Load with `stateMachineName` — verify correct state machine is active
3. Load with config that sets state B — verify no flash of state A
4. Load with default params (null) — verify backward compatibility (same behavior as before)
