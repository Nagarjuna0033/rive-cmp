# Batched Rendering Design

## Problem

Current `PoolableRiveView` creates one TextureView per Rive element. With ~25-35 buttons/cards:
- First load: ~200ms per TextureView (EGL surface creation)
- Tab switch / popup return: delay while TextureViews recreate
- No pointer event forwarding — buttons are not interactable via native Rive hit testing
- `AndroidView` + `TextureView` is the legacy "view compose" approach

## Solution: Global Batch Surface

Replace individual TextureViews with a **single global `RiveBatchSurface`** inside `RiveProvider`. All `RiveComponent` instances render as `RiveBatchItem`s on one shared surface.

### Architecture

```
RiveProvider (existing — wraps entire app)
└── RiveBatchSurface (NEW — 1 TextureView for everything)
    ├── MainScreen
    │   ├── Home tab → RiveBatchItems auto-register positions
    │   └── Contests tab → RiveBatchItems auto-register positions
    └── Notifications popup → RiveBatchItems auto-register positions
```

**Single `drawBatch()` call per frame** renders all visible items in one GPU pass.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Surface count | 1 global (inside RiveProvider) | Zero developer friction, 0ms everywhere |
| Developer API | Unchanged | RiveComponent works exactly as before |
| Pointer input | Per-item forwarding via RiveBatchItem | Enables native Rive interaction (hover, press, hit areas) |
| Opt-out | `batched = false` parameter | Heavy/full-screen animations get own TextureView |
| `dirtyFlow` | Skip for now (render every frame) | `dirtyFlow` is internal; revisit later |

### Subscriber Limit Analysis

MAX_CONCURRENT_SUBSCRIBERS = 32 per CommandQueue.

| Context | Items composed |
|---|---|
| Visible tab (LazyColumn viewport) | ~8-10 |
| Hidden retained tab (RiveRetainer) | ~8-10 |
| Popup | ~3-5 |
| **Total** | **~20-25 (under 32)** |

### What Changes

| Component | Before | After |
|---|---|---|
| `RiveProvider` (android) | Provides fileManager + runtime | Also wraps content in `RiveBatchSurface` |
| `RiveComponent` (android) | Uses `PoolableRiveView` | Uses `RiveBatchItem` (default) or `PoolableRiveView` (opt-out) |
| `RiveExpects.kt` (common) | No `batched` param | Adds `batched: Boolean = true` |
| `PoolableRiveView` | Main renderer | Kept as fallback for `batched = false` |
| `RiveRetainer` | Retains TextureViews | Still useful — retains artboard/SM handles |
| Pointer input | None (broken) | Full forwarding via SDK's pointer APIs |

### Developer Experience

```kotlin
// DEFAULT — batched, instant, interactive (NO CHANGE for developer)
RiveComponent(
    resourceName = "contest_button",
    instanceKey = "home-1",
    viewModelName = "Button",
    config = ...,
    modifier = Modifier.clickable { ... }
)

// OPT-OUT — standalone TextureView for heavy animations
RiveComponent(
    resourceName = "hero_animation",
    instanceKey = "hero",
    batched = false,  // gets own TextureView, accepts ~200ms load
    ...
)
```

### Pointer Event Flow (Fixed)

```
User touches button
  → RiveBatchItem's PointerInputModifier intercepts
  → Maps event type:
      Press → riveWorker.pointerDown(smHandle, fit, width, height, id, x, y)
      Release → riveWorker.pointerUp() + pointerExit()
      Move → riveWorker.pointerMove()
  → Native C++ processes hit test against artboard
  → State machine transitions fire
  → Animation plays on next draw frame
```

### Performance Targets

| Scenario | Before (PoolableRiveView) | After (Batched) |
|---|---|---|
| First load (cold) | ~200ms × N items | ~200ms × 1 surface |
| Scroll recycle | ~10ms | 0ms (item re-registers position) |
| Tab switch | 0ms (RiveRetainer) | 0ms (same surface) |
| Popup open/close | ~200ms | 0ms (surface already exists) |
| Button interaction | Broken | Working (native pointer forwarding) |

### Known Limitations

1. **Z-ordering** — All items draw on one flat surface. Fine for full-screen popups; could be an issue for half-sheet overlays covering other Rive items.
2. **No idle optimization** — Draws every frame. `dirtyFlow` is internal. Revisit after shipping.
3. **Subscriber limit** — 32 max. Current usage ~20-25. Monitor if more screens/items are added.
4. **Single point of failure** — Native crash kills all items. Acceptable since a crash would likely kill the app anyway.

---

## Files to Modify

| File | Change |
|---|---|
| `core/rive/src/commonMain/.../RiveExpects.kt` | Add `batched: Boolean = true` to `RiveComponent` |
| `core/rive/src/androidMain/.../RiveExpects.android.kt` | Wrap content in `RiveBatchSurface`; branch on `batched` flag |
| `core/rive/src/nativeMain/.../RiveExpects.native.kt` | Add `batched` param (ignored — iOS uses UIKitView) |
| `core/rive/src/androidMain/.../PoolableRiveView.kt` | Keep as fallback for `batched = false` |
| `core/rive/src/androidMain/.../RiveBatchWrapper.kt` | NEW — wrapper around SDK's `RiveBatchItem` with VMI/config wiring |
