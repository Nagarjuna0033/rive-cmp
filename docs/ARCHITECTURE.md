# Rive KMP Architecture

Cross-platform Rive animation integration for Android and iOS using Kotlin Multiplatform + Compose Multiplatform.

## Module Structure

```
core/rive/src/
├── commonMain/          Shared interfaces, models, configs, expect declarations
├── androidMain/         Android actual: Rive SDK composable, VMI caching, asset management
└── nativeMain/          iOS actual: Swift bridge pattern via IOSRiveHandle/IOSRiveBridge

iosApp/iosApp/
└── RiveBridge.swift     Swift implementation of the bridge (RiveRuntime SDK)

composeApp/src/commonMain/
└── App.kt, Greeting.kt  Shared UI consuming RiveComponent
```

## How It Works

### Common Layer (`commonMain`)

- **`RiveExpects.kt`** — `expect` declarations for `RiveProvider` and `RiveComponent`
- **`RiveInterface.kt`** — `RiveController`, `RiveEventCallback`, `RiveFileManager` interfaces
- **`RiveModels.kt`** — Data classes: `RiveFileConfig`, `RiveItemConfig`, `RiveAssetConfig`, `RiveLoadState`
- **`RiveConfigs.kt`** — Asset IDs, resource names, per-file configs, property name constants

### Android (`androidMain`)

1. `RiveProvider` initializes `RiveCore.init(context)` (aliased from `app.rive.runtime.kotlin.core.Rive`), creates `AndroidRiveFileManager` + `RiveRuntime`
2. `AndroidRiveFileManager` loads `.riv` bytes from `app_assets/assets/`, decodes fonts/images/audio via `RiveWorker`
3. `RiveRuntime` caches `ViewModelInstance` by `"$resourceName-$instanceKey"` using atomic `computeIfAbsent`
4. `RiveComponent` renders via Rive SDK's `Rive()` composable with `file` + `viewModelInstance`

### iOS (`nativeMain` + Swift)

1. Swift app registers `SwiftRiveBridge` into `IOSRivePlatform.bridge` at startup
2. `RiveProvider` calls `bridge.preloadFiles()` to validate `.riv` files and store asset configs
3. `RiveComponent` calls `bridge.createHandle()` — creates fresh `RiveModel` per handle
4. `SwiftRiveHandle` uses `enableAutoBind` to get VMI connected to rendering pipeline
5. Property changes use `prop.value = x` + `advance(delta: 0)` to force render frame
6. `SwiftRiveHandle.getUIView()` creates a `UIHostingController` wrapping the Rive SwiftUI view, returns its `UIView`. Compose renders it via `UIKitView(factory = { handle.getUIView() })`

### Thread Safety (iOS)

All VMI operations are dispatched to `DispatchQueue.main` to ensure:
- Thread-safe access to `pendingOperations` and `boundVMI`
- UIKit operations (`advance(delta:0)`) always run on the main thread
- `enableAutoBind` callback (which may fire on a render thread) is safely marshalled to main

An `isDestroyed` flag prevents post-destroy callbacks from re-populating state.

## Key Design Decisions

### Per-Instance Binding (iOS)
Each `createHandle()` creates a fresh `RiveModel` + `RiveViewModel`. This ensures each UI element gets its own independent VMI — no state cross-contamination between buttons.

### `advance(delta: 0)` Pattern (iOS)
After setting any VMI property, we call `riveView?.advance(delta: 0)`. This forces one render frame without advancing the animation timeline. Required because Rive's display link pauses when no animation is active, so `prop.value` changes wouldn't render otherwise.

### Operation Buffering (iOS)
`enableAutoBind` callback is async — VMI may not be ready when `LaunchedEffect` fires. Property setters buffer operations in `pendingOperations` and flush when VMI arrives. All buffering and flushing happens on `DispatchQueue.main`.

### VMI Caching (Android)
`RiveRuntime` caches `ViewModelInstance` by composite key using `ConcurrentHashMap.computeIfAbsent` (atomic). This avoids recreating VMIs on recomposition and enables instant re-binding when navigating back to a screen.

### Asset Management
Both platforms pre-register external assets (fonts, images) before loading `.riv` files. Android uses `RiveWorker` decode/register APIs. iOS uses `customLoader` closure with `RiveFactory`.

## Data Flow

```
RiveConfigs.allConfigs
    │
    ▼
RiveProvider (preloads all .riv files + assets)
    ├── Android: shows loadingContent() during Loading state
    └── iOS: shows loadingContent() during Loading state
    │
    ▼
RiveComponent (per-button)
    ├── Creates/retrieves VMI (cached)
    ├── Notifies onControllerReady (once per handle/VMI)
    ├── Applies RiveItemConfig (strings, enums, booleans, numbers)
    ├── Registers trigger listeners
    └── Renders native view
            ├── Android: Rive() composable
            └── iOS: UIKitView → UIHostingController.view → Rive SwiftUI view
```

## Property Types

| Type | Rive VMI API | Example |
|------|-------------|---------|
| String | `stringProperty(fromPath:)` | "Button Text" = "Play Now" |
| Enum | `enumProperty(fromPath:)` | "Right Cash" = "Show" |
| Boolean | `booleanProperty(fromPath:)` | "Is Loading" = true |
| Number | `numberProperty(fromPath:)` | "buttonWidth" = 150f |
| Trigger | `triggerProperty(fromPath:)` | "Press" (fire-and-forget) |

## Platform Differences

| Aspect | Android | iOS |
|--------|---------|-----|
| VMI source | `ViewModelInstance.fromFile(file, ViewModelInstanceSource.Default(ViewModelSource.Named(...)))` | `enableAutoBind` (default VMI) |
| Rendering | `Rive()` composable (SDK) | `UIKitView` → `UIHostingController.view` (Rive SwiftUI view) |
| Asset loading | `RiveWorker` decode/register | `RiveFactory` in `customLoader` closure |
| File location | `app_assets/assets/` directory | App bundle (`Bundle.main`) |
| Trigger listeners | `vmi.getTriggerFlow()` (Kotlin Flow) | `prop.addListener()` → returns UUID for lifecycle tracking |
| Thread model | Compose main thread | All VMI ops dispatched to `DispatchQueue.main` |
