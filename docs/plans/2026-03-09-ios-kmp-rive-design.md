# iOS KMP Rive Implementation — Swift Bridge Design

## Problem
Rive has no Compose Multiplatform SDK. The project bridges both platforms under a single KMP module. Android is implemented; iOS has empty stubs. The iOS `RiveRuntime` SDK exposes Obj-C classes (RiveFile, RiveArtboard, RiveDataBindingViewModelInstance) but rendering classes (RiveModel, RiveViewModel, RiveView) are Swift-only and inaccessible via Kotlin/Native cinterop.

## Approach: Swift Bridge Pattern

### Architecture

```
Kotlin (nativeMain)                    Swift (iosApp)
IOSRiveHandle (open class)  <--subclass--  SwiftRiveHandle (RiveModel+VM)
IOSRiveBridge (interface)   <--conforms--  SwiftRiveBridge (preload+create)
IOSRivePlatform (singleton)
IOSRiveController (RiveController impl)
RiveProvider / RiveComponent (actual Composables)
```

### Kotlin Side (core/rive/src/nativeMain)

**IOSRiveHandle** — open class subclassed in Swift:
- `getUIView(): UIView` — returns the Rive rendering view
- `setStringProperty(name, value)` — set VMI string property
- `setEnumProperty(name, value)` — set VMI enum property
- `setBooleanProperty(name, value)` — set VMI boolean property
- `setNumberProperty(name, value)` — set VMI number property
- `fireTrigger(name)` — fire VMI trigger
- `destroy()` — cleanup

**IOSRiveBridge** — interface exported as Obj-C protocol:
- `preloadFiles(configs: List<RiveFileConfig>): Boolean`
- `createHandle(resourceName: String): IOSRiveHandle?`
- `isFileLoaded(resourceName: String): Boolean`
- `clearAll()`

**IOSRivePlatform** — singleton for bridge registration:
- `bridge: IOSRiveBridge?` — set by iOS app before Compose starts

**IOSRiveController** — implements RiveController, delegates to IOSRiveHandle

**IOSRiveFileManager** — implements RiveFileManager, delegates to IOSRiveBridge

### Swift Side (iosApp)

**SwiftRiveBridge** — implements IOSRiveBridge:
- Stores asset configs (assetId -> resourceName mapping)
- Loads files via RiveModel with customLoader that matches assets
- Caches loaded RiveModels by resourceName

**SwiftRiveHandle** — subclasses IOSRiveHandle:
- Holds RiveModel, RiveViewModel, RiveDataBindingViewModelInstance
- `getUIView()` returns UIHostingController(rootView: riveViewModel.view()).view
- Property setters use RiveDataBindingViewModelInstance Obj-C API

### Data Flow
1. iOS app: `IOSRivePlatform.shared.bridge = SwiftRiveBridge()`
2. RiveProvider: `bridge.preloadFiles(configs)` -> Swift loads .riv files with fonts/images
3. RiveComponent: `bridge.createHandle(name)` -> SwiftRiveHandle with UIView
4. Compose: `UIKitView(factory = { handle.getUIView() })`
5. Config updates: IOSRiveController -> IOSRiveHandle -> Swift VMI property setters

### Files to Create/Modify
- `core/rive/src/nativeMain/.../IOSRiveBridge.kt` (new)
- `core/rive/src/nativeMain/.../IOSRiveController.kt` (new)
- `core/rive/src/nativeMain/.../IOSRiveFileManager.kt` (new)
- `core/rive/src/nativeMain/.../RiveExpects.native.kt` (modify)
- `iosApp/iosApp/RiveBridge.swift` (new)
