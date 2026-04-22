# Dual-Layer RiveBatchSurface Design

## Problem

`RiveBatchSurface` places a single TextureView ON TOP of all Compose content. This means:
- Composables (dim overlays, popups, dialogs, tooltips) cannot render on top of Rive animations
- Touch events are intercepted by the TextureView before reaching Compose content
- Example: Home screen popup with dim scrim — Rive cosmic background bleeds through at full brightness

## Solution

Split `RiveBatchSurface` into three z-layers with two TextureViews:

```
Layer 3 (top):    Foreground TextureView — overlay effects (confetti, celebrations)
Layer 2 (middle): Compose content — UI, popups, dim overlays, buttons
Layer 1 (bottom): Background TextureView — decorative Rive (cosmic bg, store banner)
```

A `background: Boolean = false` parameter on `RiveBatchItem` / `RiveComponent` controls which layer an item renders on. The param is dynamic — it can change at runtime (e.g., when a popup opens).

## Architecture

### Current Layout (RiveBatch.kt)

```kotlin
Layout(content = {
    content()           // Compose content (behind)
    AndroidView(TV)     // ONE TextureView (on top)
})
```

### Proposed Layout

```kotlin
Layout(content = {
    AndroidView(bgTV)   // Background TextureView (behind)
    content()           // Compose content (middle)
    AndroidView(fgTV)   // Foreground TextureView (on top)
})
```

### Coordinator Changes

Two independent `RiveBatchCoordinator` instances:
- `bgCoordinator` — manages background items
- `fgCoordinator` — manages foreground items (existing behavior)

Each has its own:
- `ConcurrentHashMap` of items
- Pre-allocated batch arrays
- `itemCount` observable state
- `fillBatchArrays()` method

### Render Loop Changes

Two independent render loops:
- Background loop: advances SMs + `drawBatch()` on bgTV
- Foreground loop: advances SMs + `drawBatch()` on fgTV (existing)

Each loop auto-skips when its coordinator has 0 items (existing optimization).

### TextureView Auto-Hide

Each TextureView sets `visibility = INVISIBLE` when its coordinator has 0 items:
- Screen with only background items: fgTV invisible, zero GPU cost
- Screen with only foreground items: bgTV invisible, zero GPU cost
- Screen with both: both active

### CompositionLocal

Two CompositionLocals provided to children:
- `LocalRiveBatchCoordinator` (existing) — foreground coordinator
- `LocalRiveBgBatchCoordinator` (new) — background coordinator

`RiveBatchItem` reads `background` param to choose which coordinator to register with.

## API

### SDK (RiveBatch.kt)

```kotlin
@Composable
fun RiveBatchItem(
    file: RiveFile,
    modifier: Modifier = Modifier,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
    backgroundColor: Int = Color.Transparent.toArgb(),
    background: Boolean = false,  // NEW
)
```

### CMP (RiveExpects.kt)

```kotlin
@Composable
expect fun RiveComponent(
    resourceName: String,
    instanceKey: String,
    viewModelName: String,
    modifier: Modifier = Modifier,
    config: RiveItemConfig = RiveItemConfig(),
    eventCallback: RiveEventCallback? = null,
    onControllerReady: ((RiveController) -> Unit)? = null,
    alignment: RiveAlignment = RiveAlignment.CENTER,
    autoPlay: Boolean = true,
    artboardName: String? = null,
    fit: RiveFit = RiveFit.CONTAIN,
    stateMachineName: String? = null,
    batched: Boolean = true,
    background: Boolean = false,  // NEW
)
```

### App Usage (BeBetta)

```kotlin
// Static background
RiveComponent(resourceName = "cosmic_bg", background = true)

// Dynamic — goes behind when popup opens
val popupVisible by remember { mutableStateOf(false) }
RiveComponent(resourceName = "home_icon", background = popupVisible)

// Foreground overlay — default, no change needed
RiveComponent(resourceName = "confetti")
```

## Scope

### Files Changed

| File | Repo | Change |
|------|------|--------|
| `RiveBatch.kt` | rive-android (SDK fork) | Dual coordinators, dual TextureViews, dual render loops, `background` param on RiveBatchItem |
| `RiveExpects.kt` | rive-cmp (common) | Add `background` param to expect signature |
| `RiveExpects.android.kt` | rive-cmp (android) | Pass `background` to RiveBatchItem |
| `RiveExpects.native.kt` | rive-cmp (iOS) | Accept `background` param, ignore (no-op) |

### Files NOT Changed

- `PoolableRiveView.kt` — `batched=false` already has correct z-ordering
- `CommandQueue.kt` — no JNI changes needed
- `RenderContext.kt` — no EGL changes needed

## Impact Analysis

| Area | Impact |
|------|--------|
| Existing `batched=true` items | Zero — `background` defaults to `false`, identical to current behavior |
| Existing `batched=false` items | Zero — not affected by this change |
| GPU memory | +2-4MB VRAM when both layers active (one extra EGL surface at screen resolution) |
| Frame time | +0.5-1ms when both layers have items (one extra `drawBatch` call) |
| iOS | Zero — param accepted but ignored |
| AAR rebuild | Required — SDK fork change |

## Limitations

- Does NOT fix NavHost crossfade ghost (separate issue)
- `background` param ignored when `batched=false` (not needed — z-order already correct)
- Two EGL surfaces when both layers active (mitigated by auto-hide)
