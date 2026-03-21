# Rive Android SDK Enhancement: Batched Rendering

## Overview

An enhancement to the Rive Android SDK that introduces batched rendering — drawing all Rive animations on a **single shared TextureView** instead of one per animation. This reduces GPU context switches from N to 1, eliminates scroll jank, and removes navigation delays.

**Status:** Production-ready
**SDK Fork:** [Nagarjuna0033/rive-android](https://github.com/Nagarjuna0033/rive-android)
**Integration App:** [Nagarjuna0033/rive-cmp](https://github.com/Nagarjuna0033/rive-cmp)

---

## Problem

The default Rive Android SDK creates one `TextureView` + one EGL context per animation instance. In apps with many animations (lists, tabs, grids), this causes:

- **GPU memory bloat** — Each TextureView allocates its own GPU render target (~2-4MB each)
- **Context switch overhead** — N EGL context switches per frame (0.5-2ms each on mobile)
- **Scroll jank** — TextureView creation/destruction during LazyColumn scroll (5-15ms spikes)
- **Navigation delays** — New TextureViews need surface allocation on screen transitions (100-200ms)
- **GC pressure** — N render loops creating garbage per frame

On mid-range devices, 10 animations consume 30-50% of the frame budget just on context switches.

---

## Solution

### Architecture

```
Before (N surfaces):                  After (1 surface):
┌──────────────────┐                  ┌──────────────────┐
│ LazyColumn       │                  │ LazyColumn       │
│ ┌──────────────┐ │                  │ ┌──────────────┐ │
│ │ TextureView₁ │ │  ← EGL ctx 1    │ │ Empty Layout │ │  ← coordinates only
│ └──────────────┘ │                  │ └──────────────┘ │
│ ┌──────────────┐ │                  │ ┌──────────────┐ │
│ │ TextureView₂ │ │  ← EGL ctx 2    │ │ Empty Layout │ │  ← coordinates only
│ └──────────────┘ │                  │ └──────────────┘ │
│ ┌──────────────┐ │                  │                  │
│ │ TextureView₃ │ │  ← EGL ctx 3    │ ┌──────────────┐ │
│ └──────────────┘ │                  │ │ TextureView  │ │  ← 1 EGL ctx
└──────────────────┘                  │ │ (shared)     │ │    draws ALL items
N surfaces, N contexts                │ └──────────────┘ │
N draw calls per frame                └──────────────────┘
                                      1 surface, 1 context
                                      1 drawBatch() per frame
```

### New SDK Components

| Component | File | Responsibility |
|-----------|------|----------------|
| `RiveBatchSurface` | `RiveBatch.kt` | Creates a single shared TextureView. Runs a per-frame render loop that advances all state machines and calls `drawBatch()`. |
| `RiveBatchItem` | `RiveBatch.kt` | A composable that registers its viewport coordinates with the coordinator. Forwards touch events to the Rive state machine via `pointerInput`. Has no rendering surface of its own. |
| `RiveBatchCoordinator` | `RiveBatch.kt` | Manages registered items in a `ConcurrentHashMap`. Fills pre-allocated arrays (power-of-2 sized) for the JNI `drawBatch` call. Zero allocation in steady state. |
| `drawBatch()` | `CommandQueue.kt` | New method that renders all items in a single GPU pass. Takes parallel arrays of artboard handles, state machine handles, viewport positions (x, y, width, height), fits, alignments, scale factors, and clear colors. |
| `cppDrawBatch` | `bindings_command_queue.cpp` | Native JNI implementation. Iterates items, sets viewport/scissor per item, draws each artboard at its position within the shared surface. |

---

## How It Works

### Render Loop

Every frame (synchronized with `withFrameNanos`):

```
1. Compute deltaTime from frame timestamp
2. coordinator.fillBatchArrays()
   → Snapshot all registered items into pre-allocated primitive arrays
   → Power-of-2 sizing avoids reallocation during scroll
3. For each item: advanceStateMachine(handle, deltaTime)
4. drawBatch(surface,
       artboardHandles[],      // native artboard pointers
       stateMachineHandles[],  // native state machine pointers
       viewportXs[], viewportYs[], viewportWidths[], viewportHeights[],
       fits[], alignments[], scaleFactors[],
       clearColors[],          // per-item background color
       surfaceClearColor       // full surface clear color
   )
   → Single native call renders all items in one GPU pass
```

The loop pauses automatically when the lifecycle drops below `RESUMED`. Note that the loop runs every frame (60fps) even when all animations are idle — there is no dirty-check optimization yet (see Future Enhancements).

### Position Tracking

Each `RiveBatchItem` tracks its position using two Compose callbacks:

- `onGloballyPositioned` — fires on layout changes (initial placement, size changes)
- `onPlaced` — fires on every placement including scroll offsets

```kotlin
val relativeX = (rootPos.x - coordinator.surfaceRootX).toInt()
val relativeY = (rootPos.y - coordinator.surfaceRootY).toInt()
coordinator.register(key, BatchItemDescriptor(x, y, width, height, fit, ...))
```

When an item leaves composition (scrolled out, screen navigated away), `DisposableEffect.onDispose` calls `coordinator.unregister()`.

### Touch Forwarding

`RiveBatchItem` uses `Modifier.pointerInput` with `awaitPointerEventScope` to forward all pointer events directly to the Rive state machine:

| Compose Event | Rive Call |
|---------------|-----------|
| `Press` | `pointerDown(stateMachineHandle, fit, boundsWidth, boundsHeight, pointerId, x, y)` |
| `Move` | `pointerMove(stateMachineHandle, fit, boundsWidth, boundsHeight, pointerId, x, y)` |
| `Release` | `pointerUp(...)` + `pointerExit(...)` |
| `Exit` | `pointerExit(...)` |

No explicit `fireTrigger()` needed. If the Rive file has interactive elements (Listeners, state machine inputs), they respond automatically.

### Z-Order Strategy

The TextureView renders **on top** as a transparent overlay (`isOpaque = false`):

```
Z-order (front to back):
  1. TextureView (transparent — only Rive pixels visible)
  2. Compose content (Scaffold, Cards, Text, backgrounds)
```

This ensures Rive items are visible above opaque backgrounds (Cards, Scaffold). The TextureView is placed SECOND in the Layout content lambda so it renders on top.

---

## Integration Guide

### Setup (App Root)

```kotlin
RiveProvider(
    configs = RiveConfigs.allConfigs,
    loadingContent = { CircularProgressIndicator() },
    errorContent = { Text("Error: $it") }
) {
    // A single RiveBatchSurface is created automatically.
    // All RiveComponent children render on the shared surface.
    AppContent()
}
```

### Using Animations (Anywhere in Composition)

```kotlin
RiveComponent(
    resourceName = "primary_button",
    instanceKey = "$tabTag-${item.id}",    // unique per instance
    modifier = Modifier.height(50.dp).width(150.dp),
    viewModelName = "Button",
    config = RiveItemConfigs.primaryButton(params),
    // batched = true is the default
)
```

### Opting Out for Specific Animations

```kotlin
RiveComponent(
    resourceName = "hero_animation",
    instanceKey = "hero",
    modifier = Modifier.fillMaxSize(),
    batched = false,  // gets its own TextureView
)
```

### Integration Rules

| Rule | Reason |
|------|--------|
| Set explicit size via `Modifier.height().width()` | `RiveBatchItem` has no intrinsic size |
| Use unique `instanceKey` per animation instance | Coordinator uses key for register/unregister |
| Use `when`-based navigation, not overlay `Box` | Hidden screens still render on shared surface |
| Apply `navigationBarsPadding()` to batch surface | TextureView can extend behind system bars |

### Navigation Pattern

```kotlin
// CORRECT — items unregister when screen leaves composition
when (currentRoute) {
    "home" -> HomeScreen()
    "notifications" -> NotificationsScreen()
}

// WRONG — home items bleed through on notifications
Box {
    HomeScreen()  // always in composition, items still rendered
    if (showNotifications) NotificationsScreen()
}
```

### When to Use Batched vs Per-Item

| Scenario | Batched | Per-Item | Why |
|----------|---------|----------|-----|
| List of animated buttons | Yes | | Scroll performance, many instances |
| Grid of animated cards | Yes | | Same benefits at scale |
| Tabs with animations | Yes | | Batch surface persists across tabs |
| Animated icons/indicators | Yes | | Lightweight, many instances |
| Full-screen hero animation | | Yes | Single instance — no batching benefit; adds heavy draw to shared render loop |
| Animation with Compose overlays | | Yes | Needs z-ordering with Compose content on top |
| Animation behind native views | | Yes | Batch surface is always on top |

---

## Performance Impact

### Benchmarks (Xiaomi M2101K7BI, mid-range)

| Metric | Per-Item (before) | Batched (after) | Improvement |
|--------|-------------------|-----------------|-------------|
| GPU memory (10 items) | ~20-40MB | ~4-8MB | **5x reduction** |
| CPU memory (SurfaceTexture buffers) | ~10-20MB | ~2MB | **5-10x reduction** |
| GPU context switches per frame | N | 1 | **Nx reduction** |
| Scroll jank (TextureView creation) | 5-15ms spikes | 0ms | **Eliminated** |
| Tab switch delay | 100-200ms | ~0ms | **Eliminated** |
| Navigation delay | 100-200ms | ~0ms | **Eliminated** |
| Frame budget used by Rive | 30-50% | 5-10% | **~5x reduction** |

### Memory Breakdown

**GPU memory:** Single TextureView at screen resolution (~4-8MB) vs N TextureViews at item resolution (~2-4MB each). At 10 items, savings are ~20-30MB.

**CPU memory:** Eliminated N `SurfaceTexture` native buffers (~1-2MB each). Coordinator arrays are negligible (~1KB for 30 items). `ConcurrentHashMap` overhead is ~200 bytes per item.

**GC pressure:**

| Scenario | Before | After |
|----------|--------|-------|
| Steady state | N render loops creating garbage | Zero allocation (pre-allocated arrays) |
| Scroll | TextureView create/destroy churn | Coordinate register/unregister only |
| Item count change | N/A | Power-of-2 array resize (infrequent) |

### Frame Budget Analysis

For 10 visible animations at 60fps:

```
Before:
  Context switches: 10 x 1ms   = 10ms
  Draw calls:       10 x 0.5ms = 5ms
  Total Rive cost:              = 15ms (out of 16.6ms budget)

After:
  Context switch:   1 x 1ms    = 1ms
  drawBatch:        1 x 1ms    = 1ms
  Total Rive cost:              = 2ms (out of 16.6ms budget)
```

---

## Risks & Limitations

### Known Limitations

1. **Single z-plane** — All batched items render on one transparent overlay. Compose content cannot be sandwiched between Rive items in z-order. Use `batched = false` for animations that need z-ordering with Compose.

2. **Explicit size required** — `RiveBatchItem` has no intrinsic size (`Layout(content = {})`). In unbounded containers without explicit dimensions, layout will fail.

3. **Navigation constraint** — Screens kept in composition (for state preservation) still have their items rendered on the shared surface. Use composition-based navigation (`when`) instead of overlay-based (`Box`).

4. **SharedFlow buffer capacity = 32** — The SDK's `CommandQueue` uses `MAX_CONCURRENT_SUBSCRIBERS = 32` as the `extraBufferCapacity` for its `SharedFlow` instances (property updates, triggers, settle events). This is a **buffer size**, not a hard limit on item count. Each `RiveBatchItem` with a `ViewModelInstance` subscribes to its own `dirtyFlow`. If property update events are emitted faster than subscribers consume them (unlikely in normal usage — events fire on user interaction, not per-frame), the oldest unconsumed events are silently dropped (`DROP_OLDEST`). In practice, this limit is not hit even with 100+ items because Rive property changes are infrequent relative to the consumption rate.

5. **arm64 only** — Current AAR built for arm64-v8a. Covers ~95% of active Android devices.

### Risk Assessment

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| Surface destroyed mid-draw | Medium | Low | `drawBatch` wrapped in try/catch; `LaunchedEffect` keyed on surface state |
| Stale positions after resume | Low | Medium | `onGloballyPositioned` + `onPlaced` re-fire on next layout pass |
| Array reallocation during fling | Low | Medium | Power-of-2 sizing reduces reallocations; only happens when item count crosses a power-of-2 boundary |
| Null state machine handle | Low | Low | Null-safe access with `?: continue` (no crash) |
| Preload failure | Medium | Low | Wrapped in try/catch; surfaces `RiveLoadState.Error` to UI |

### Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Buttons invisible | TextureView behind opaque content | Ensure TextureView is SECOND child in Layout (on top). Check `isOpaque = false`. |
| Items at wrong positions | Coordinate mismatch | Verify `onGloballyPositioned`/`onPlaced` firing. Check `surfaceRootX/Y`. |
| Items bleed through screens | Hidden screen still in composition | Use `when` navigation, not overlay `Box`. |
| Items behind system nav bar | TextureView extends behind insets | Apply `Modifier.navigationBarsPadding()` to `RiveBatchSurface`. |
| Scroll position lag | `onPlaced` not firing | Ensure both `onGloballyPositioned` AND `onPlaced` are chained. |
| Touch not working | Pointer events not forwarded | Verify `pointerInput` modifier is applied. Check Rive file has interactive elements. |

---

## SDK Changes Summary

### New Files

| File | Lines | Description |
|------|-------|-------------|
| `RiveBatch.kt` | ~430 | `RiveBatchSurface`, `RiveBatchItem`, `RiveBatchCoordinator`, `BatchItemDescriptor` |

### Modified Files

| File | Change |
|------|--------|
| `CommandQueue.kt` | Added `drawBatch()` — multi-item rendering in single GPU pass |
| `CommandQueueBridge.kt` | Added `cppDrawBatch` JNI bridge method |
| `bindings_command_queue.cpp` | Added native `drawBatch` implementation — iterates items, sets viewport/scissor, draws at position |

### Public API Surface

```kotlin
// New composables
@Composable fun RiveBatchSurface(
    riveWorker: CommandQueue,
    modifier: Modifier = Modifier,
    surfaceClearColor: Int = Color.Transparent.toArgb(),
    content: @Composable () -> Unit,
)

@Composable fun RiveBatchItem(
    file: RiveFile,
    modifier: Modifier = Modifier,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
    backgroundColor: Int = Color.Transparent.toArgb(),
)

// New data types
data class BatchItemDescriptor(
    artboardHandle: ArtboardHandle,
    stateMachineHandle: StateMachineHandle,
    x: Int, y: Int, width: Int, height: Int,
    fit: Fit,
    backgroundColor: Int,
)
class RiveBatchCoordinator  // internal coordination, public for CompositionLocal access

// New CompositionLocal
val LocalRiveBatchCoordinator: CompositionLocal<RiveBatchCoordinator?>

// New method on CommandQueue
fun CommandQueue.drawBatch(
    surface: RiveSurface,
    artboardHandles: LongArray,
    stateMachineHandles: LongArray,
    viewportXs: IntArray,
    viewportYs: IntArray,
    viewportWidths: IntArray,
    viewportHeights: IntArray,
    fits: ByteArray,
    alignments: ByteArray,
    scaleFactors: FloatArray,
    clearColors: IntArray,
    surfaceClearColor: Int,
)
```

---

## Future Enhancements

1. **Idle optimization** — Skip frames when no state machine has pending changes. Requires exposing `dirtyFlow` on `ViewModelInstance` (currently `internal` in SDK).

2. **Visibility culling** — Skip `drawBatch` for items whose viewport falls outside the visible surface bounds. Reduces GPU work when many items are registered but off-screen.

3. **Per-component VMI disposal** — Currently `ViewModelInstance` and `Controller` are only cleaned up when the `RiveProvider` unmounts. Individual component disposal would free native resources earlier.

4. **Multi-surface support** — Allow multiple `RiveBatchSurface` instances for different z-planes, enabling Compose content between groups of Rive items.

5. **Upstream contribution** — These changes could be proposed as a PR to the official [rive-app/rive-android](https://github.com/rive-app/rive-android) repository.
