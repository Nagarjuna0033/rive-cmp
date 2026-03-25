# iOS Rive SDK Integration — Changes, Differences vs Android, and Why

## Overview

The iOS side uses the official [RiveRuntime](https://github.com/rive-app/rive-ios) iOS SDK via a Kotlin–Swift bridge. Unlike Android (where we forked the SDK to add batched rendering and HardwareBuffer zero-copy), the iOS SDK is used unmodified. All customization lives in the bridge layer.

## Architecture: iOS vs Android

### iOS: Bridge Pattern

```
Kotlin (nativeMain)          Swift (iosApp)              Native SDK
─────────────────           ─────────────               ──────────
RiveComponent()
  ↓
IOSRiveBridge.createHandle(resourceName, artboardName, stateMachineName)
  ↓
                            SwiftRiveBridge.createHandle()
                              ↓
                            SwiftRiveHandle(riveModel, artboardName, stateMachineName)
                              ↓
                            RiveViewModel(model, stateMachineName:, autoPlay:, artboardName:)
                              ↓
                                                        RiveRuntime renders via RiveView
                              ↓
                            UIHostingController wraps SwiftUI → UIKit
  ↓
UIKitView(factory: { handle.getUIView() })
```

Each `RiveComponent` gets its own `RiveModel` + `RiveViewModel` + `UIHostingController`. The Kotlin side communicates via the `IOSRiveHandle` open class, which Swift subclasses.

### Android: Direct SDK Integration

```
Kotlin (androidMain)         SDK (rive-android fork)
────────────────────        ────────────────────────
RiveComponent()
  ↓
RiveRuntime.getInstance() → ViewModelInstance (VMI)
  ↓
  ├─ batched=true → RiveBatchItem
  │    ↓
  │    RiveBatchRenderer → HardwareBuffer → drawBehind { drawImage(bitmap) }
  │
  └─ batched=false → PoolableRiveView
       ↓
       TextureView with surface recycling
```

Android uses a forked SDK (`Nagarjuna0033/rive-android`) with:
- `RiveBatchSurface` / `RiveBatchRenderer` / `RiveBatchItem` — batched rendering pipeline
- `CommandQueue.drawToHardwareBuffer()` — HardwareBuffer zero-copy (C++ JNI)
- Single shared EGL context for all items

## What We Changed on iOS (No SDK Fork Needed)

All changes are in the bridge layer — no modifications to the RiveRuntime iOS SDK.

### 1. artboardName / stateMachineName Support

**What:** Pass `artboardName` and `stateMachineName` from Kotlin `RiveComponent` through the Swift bridge to `RiveViewModel`.

**Why:** Without this, every animation loads the default artboard and state machine. Multi-artboard `.riv` files (e.g., a file with both "Button" and "Icon" artboards) couldn't select which artboard to render.

**Files changed:**
- `IOSRiveBridge.kt` — added params to `createHandle()` interface
- `RiveBridge.swift` — `SwiftRiveHandle` init passes params to `RiveViewModel`; `SwiftRiveBridge.createHandle()` forwards them
- `RiveExpects.native.kt` — passes params from `RiveComponent` to `createHandle()`

### 2. Config Flash Fix (Synchronous Config Application)

**What:** Changed config application from async `LaunchedEffect` to synchronous `remember` block.

**Why:** With `LaunchedEffect`, the first frame renders with state A (default), then config applies and switches to state B. This causes a visible flash. By applying config synchronously during composition, the properties are set before the first render.

**Before (async — flashes):**
```kotlin
LaunchedEffect(handle, config) {
    config.booleans.forEach { (k, v) -> controller.setBoolean(k, v) }
    config.strings.forEach { (k, v) -> controller.setString(k, v) }
    // ...
}
```

**After (sync — no flash):**
```kotlin
remember(handle, config) {
    controller.applyConfig(config)
}
```

**Note:** On iOS, the `executeWithVMI` mechanism in `SwiftRiveHandle` buffers property-set operations until the VMI is ready (via `enableAutoBind` callback). So even though we call `applyConfig` synchronously during composition, the operations are queued and flushed in order when the VMI arrives. This means the first rendered frame after VMI binding already has the correct state.

### 3. Common `applyConfig()` Usage

**What:** Replaced manual property loops with `controller.applyConfig(config)` — the common interface method defined in `RiveInterface.kt`.

**Why:** Android already uses this pattern. Using the same method ensures consistency and reduces duplication.

## Key Differences: iOS vs Android

| Aspect | iOS | Android | Why Different |
|--------|-----|---------|---------------|
| **SDK** | Unmodified RiveRuntime | Forked rive-android | iOS doesn't need batching (no EGL context problem) |
| **Rendering** | Per-item RiveView via UIHostingController | Batched: single EGL context + HardwareBuffer zero-copy | Android TextureView has context thrashing with many items |
| **View bridge** | UIKitView (Compose → UIKit) | Pure Compose (drawBehind) or TextureView | Compose Multiplatform uses UIKitView for native iOS views |
| **VMI access** | `enableAutoBind` → async callback | Direct `ViewModelInstance` from `RiveFile` | Different SDK APIs; iOS SDK doesn't expose VMI synchronously |
| **Config buffering** | `executeWithVMI` queues ops until VMI ready | Synchronous — VMI available immediately from `RiveWorker` | iOS VMI arrives async via callback; Android has it at file load |
| **Memory** | Per-item RiveView (acceptable on iOS) | Shared GPU surface + HardwareBuffer pooling | iOS GPU memory management is more efficient than Android's |
| **Scroll performance** | Native UIKit scroll handling | Batched rendering eliminates jank | Android TextureView position sync is a known problem |

## Why iOS Doesn't Need Batched Rendering

Android's batched rendering solves problems specific to Android's graphics stack:

1. **EGL context thrashing** — Each Android TextureView creates its own EGL context. 10 items = 10 context switches/frame (5-20ms overhead). iOS doesn't have this problem — RiveView uses Core Animation, which shares the GPU efficiently.

2. **TextureView position sync** — Android TextureView renders on a separate surface whose position must be synced with the UI thread. During fast scroll, position lags by 1-2 frames. iOS RiveView is a standard UIView — Core Animation handles positioning correctly.

3. **GPU memory** — Android TextureView allocates a full render target per item (20-40MB for 10 items). iOS Core Animation composites layers efficiently with much lower overhead.

4. **View creation cost** — Android TextureView creation takes ~200ms. iOS UIView creation is near-instant.

## File Reference

### iOS-specific (nativeMain + iosApp)
- `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveBridge.kt` — Bridge interface + IOSRiveHandle base class
- `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt` — iOS actual implementations of RiveProvider/RiveComponent
- `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveController.kt` — Controller wrapping IOSRiveHandle
- `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/IOSRiveFileManager.kt` — File preloading
- `iosApp/iosApp/RiveBridge.swift` — Swift implementations (SwiftRiveHandle, SwiftRiveBridge)

### Android-specific (androidMain + SDK fork)
- `core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt` — Android actual implementations
- `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt` — Batched rendering (RiveBatchRenderer, RiveBatchSurface, RiveBatchItem)
- `SDK/kotlin/src/main/kotlin/app/rive/core/CommandQueue.kt` — HardwareBuffer zero-copy rendering

### Common (shared)
- `core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveExpects.kt` — Expect declarations
- `core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveInterface.kt` — RiveController, RiveEventCallback, RiveFileManager interfaces
