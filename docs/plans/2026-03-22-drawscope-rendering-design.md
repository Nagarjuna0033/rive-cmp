# DrawScope + HardwareBuffer Rendering Design (Path B)

## Problem

Batched rendering draws ALL Rive items on a single shared TextureView overlay. The TextureView is a separate surface from the Compose hierarchy, so its render loop (`withFrameNanos` in ANIMATION phase) reads item positions before `onPlaced` updates them (TRAVERSAL phase). This causes a 1-frame position lag during scroll — buttons visually lag behind cards.

See `2026-03-22-scroll-position-sync-analysis.md` for full root cause analysis.

## Solution

Replace the TextureView overlay with per-item `Modifier.drawBehind` rendering. Each item renders its artboard to an offscreen FBO, wraps it in a HardwareBuffer-backed Bitmap (zero-copy GPU path), and draws it via Compose's DrawScope. Since `drawBehind` runs during Compose's DRAW phase (after LAYOUT), position is always correct.

## Architecture

```
RiveBatchSurface
├── CompositionLocalProvider(LocalRiveBatchRenderer)
│   └── content()  ← RiveBatchItems live here
└── (NO TextureView)

RiveBatchRenderer (background thread)
├── Shared EGL context (1 for all items)
├── FBOPool (3 size buckets, 1 FBO each)
├── BitmapPool (HardwareBuffer-backed, per-item)
└── Render loop: withFrameNanos → advance SMs → render dirty items → distribute bitmaps

RiveBatchItem
├── Modifier.drawBehind { drawImage(bitmap) }  ← position always correct
├── mutableStateOf<ImageBitmap?>  ← updated by renderer
└── Registers with renderer (not coordinator)
```

## Key Design Decisions

### 1. HardwareBuffer Zero-Copy (API 26+)

Instead of `glReadPixels` (CPU readback, ~1-3ms per item), use:
1. Render artboard to offscreen FBO
2. Wrap FBO's texture in `HardwareBuffer`
3. Wrap `HardwareBuffer` in `Bitmap.wrapHardwareBuffer()`
4. Convert to `ImageBitmap` for DrawScope

This is zero-copy — the GPU texture IS the bitmap. No pixel transfer.

**Minimum API**: 26 (Android 8.0), covers 99%+ active devices. Our `minSdk` is already 24, but `HardwareBuffer` requires 26. We'll use a runtime check and fall back to `glReadPixels` on API 24-25.

### 2. FBO Pool with Size Buckets

3 buckets to handle varying item sizes without per-item FBO allocation:

| Bucket | Max Size | Typical Use |
|--------|----------|-------------|
| Small  | 500 x 200 | Buttons, icons |
| Medium | 1000 x 750 | Cards, panels |
| Large  | 1080 x 1200 | Hero animations, full-screen |

Each bucket holds 1 reusable FBO. When an item needs rendering:
1. Find smallest bucket that fits
2. Set `glViewport` to actual item size (FBO may be larger)
3. Render artboard
4. Read only the item-sized region into bitmap

FBOs are created lazily on first use, destroyed when renderer disposes.

### 3. Dirty Flow Integration

Only re-render items whose state machines have changed:

```
Render loop:
  withFrameNanos →
    for each item:
      if item.isDirty:
        advance state machine
        render to FBO → bitmap
        item.isDirty = false

    if no items dirty for N frames:
      suspend (wait for dirtyFlow signal)
```

Requires `dirtyFlow` to be made public in the SDK fork. This is already tracked as a pending task.

### 4. Bitmap Distribution

Each `RiveBatchItem` holds a `mutableStateOf<ImageBitmap?>`. The renderer updates this from the render thread. Compose observes the state change and triggers a redraw of just that item's `drawBehind` block.

Thread safety: `mutableStateOf` is thread-safe for single-writer (renderer) / single-reader (Compose) patterns when the value is an immutable object (ImageBitmap).

## Component Details

### RiveBatchRenderer

Replaces `RiveBatchCoordinator` as the central orchestrator.

**Responsibilities:**
- Owns the shared EGL context + FBO pool
- Runs the render loop (background coroutine via `withFrameNanos`)
- Advances state machines
- Renders dirty items to offscreen FBOs
- Distributes bitmaps to items

**Lifecycle:**
- Created in `RiveBatchSurface` via `remember { }`
- EGL context created on first render frame
- Destroyed via `DisposableEffect` (releases EGL, FBOs, bitmaps)

**Registration:**
- Items call `renderer.register(key, descriptor)` — similar to current coordinator
- `descriptor` includes artboard handle, state machine handle, size, fit, backgroundColor
- Items call `renderer.unregister(key)` on dispose

### RiveBatchItem Changes

**Before (current):**
```kotlin
Layout(modifier = combinedModifier, content = {}) { _, constraints ->
    layout(constraints.maxWidth, constraints.maxHeight) {}
}
```
Empty layout — all drawing happens on the TextureView overlay.

**After:**
```kotlin
var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

// Renderer updates this bitmap reference each frame
DisposableEffect(renderer, stateMachineHandle) {
    renderer.register(stateMachineHandle, descriptor, onBitmap = { bitmap = it })
    onDispose { renderer.unregister(stateMachineHandle) }
}

Layout(
    modifier = modifier
        .onGloballyPositioned { /* track size only, not position for rendering */ }
        .drawBehind { bitmap?.let { drawImage(it) } }
        .pointerInput(stateMachineHandle, fit) { /* touch handling unchanged */ },
    content = {}
) { _, constraints ->
    layout(constraints.maxWidth, constraints.maxHeight) {}
}
```

**Key change:** Position tracking (`onPlaced`, `positionInRoot`) is no longer needed for rendering. Compose handles position via the modifier chain. We only need size for FBO allocation.

### RiveBatchSurface Changes

**Before:**
- Creates TextureView via `AndroidView`
- Uses custom `Layout` to overlay TextureView on top of content
- Tracks surface root position via `onGloballyPositioned`

**After:**
- No TextureView
- No `AndroidView`
- Provides `RiveBatchRenderer` via `CompositionLocalProvider`
- Simple `Box` or transparent wrapper
- No position tracking needed

### BitmapPool

Per-item bitmap allocation using HardwareBuffer:

```kotlin
class BitmapPool {
    private val pool = ConcurrentHashMap<Any, Bitmap>()

    fun getOrCreate(key: Any, width: Int, height: Int): Bitmap {
        val existing = pool[key]
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }
        existing?.recycle()
        val buffer = HardwareBuffer.create(width, height, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        val bitmap = Bitmap.wrapHardwareBuffer(buffer, null)!!
        buffer.close()
        pool[key] = bitmap
        return bitmap
    }

    fun recycle(key: Any) {
        pool.remove(key)?.recycle()
    }

    fun recycleAll() {
        pool.values.forEach { it.recycle() }
        pool.clear()
    }
}
```

### FBOPool

```kotlin
class FBOPool {
    data class Bucket(val maxWidth: Int, val maxHeight: Int, var fboId: Int = 0, var textureId: Int = 0)

    private val buckets = arrayOf(
        Bucket(500, 200),    // small: buttons, icons
        Bucket(1000, 750),   // medium: cards
        Bucket(1080, 1200),  // large: hero animations
    )

    fun acquire(width: Int, height: Int): Bucket {
        return buckets.first { it.maxWidth >= width && it.maxHeight >= height }
            .also { if (it.fboId == 0) createFBO(it) }
    }

    // No release needed — buckets are reused across items within the same frame
}
```

## Performance Comparison

| Metric | Original (per-item) | Current Batched (TextureView) | Path B (DrawScope) |
|--------|---------------------|-------------------------------|---------------------|
| EGL contexts | N | 1 | 1 |
| Context switches/frame | N | 1 | 1 (offscreen only) |
| GPU→CPU readback | None | None | None (HardwareBuffer) |
| GPU memory | 20-40MB | 4-8MB | 4-8MB |
| CPU memory | 10-20MB | ~2MB | ~2MB + bitmap refs |
| Position sync | Perfect | 1-frame lag | Perfect |
| Scroll correctness | Perfect | Broken during fast scroll | Perfect |
| Main thread work | Low | Low | Low (drawImage only) |
| GC pressure | High | Near zero | Near zero |
| 200ms tab switch | Yes | No | No |

## Public API

**Unchanged.** The `RiveComponent(batched = true/false)` API remains identical. Only internal implementation changes.

## Files to Change

### SDK (`Nagarjuna0033/rive-android`)

| File | Action |
|------|--------|
| `kotlin/src/main/kotlin/app/rive/RiveBatch.kt` | Major rewrite: remove TextureView, add DrawScope rendering |
| `kotlin/src/main/kotlin/app/rive/RiveBatchRenderer.kt` | New: background render loop + FBO pool |
| `kotlin/src/main/kotlin/app/rive/BitmapPool.kt` | New: HardwareBuffer bitmap management |
| `kotlin/src/main/kotlin/app/rive/FBOPool.kt` | New: size-bucketed FBO pool |

### App (`Nagarjuna0033/rive-cmp`)

| File | Action |
|------|--------|
| `core/rive/libs/rive-android-local.aar` | Rebuild with new SDK |
| `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt` | Reference copy update |

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| HardwareBuffer not available (API < 26) | Runtime check, fallback to `glReadPixels` |
| Bitmap allocation per item | Pool and reuse; only reallocate on size change |
| Thread safety (renderer → Compose) | `mutableStateOf` with immutable ImageBitmap values |
| FBO size mismatch | 3 size buckets cover common cases; oversized FBO wastes some GPU memory but works correctly |
| `dirtyFlow` not yet public | Make it public before implementing idle optimization; render loop works without it (just renders every frame) |

## Migration Path

1. Implement Path B alongside existing TextureView code (feature flag or separate composable)
2. Test on device — verify scroll correctness, measure performance
3. If good, replace TextureView code entirely
4. If issues, keep TextureView as fallback (Path C hybrid)
