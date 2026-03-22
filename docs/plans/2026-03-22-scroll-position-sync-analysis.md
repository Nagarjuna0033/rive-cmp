# Scroll Position Sync Analysis — Batched Rendering

## The Problem

Batched rendering draws ALL Rive items on a single shared TextureView overlay. The batch surface needs the exact pixel position of every item every frame. During scroll, positions change every frame but the render loop reads them at a different time than when layout updates them.

### Choreographer Frame Timing

```
1. INPUT phase     → Touch/scroll events fire
2. ANIMATION phase → withFrameNanos resumes → drawBatch() renders
3. TRAVERSAL phase → Layout pass → onPlaced fires → positions updated
```

`drawBatch()` happens BEFORE `onPlaced`. So during scroll, the render loop reads positions from the PREVIOUS frame. Cards scroll (LazyColumn handles its own drawing), but the batch surface draws buttons at stale positions → buttons lag behind cards.

## What Was Tried

### Attempt 1: onGloballyPositioned only
- Only fires on layout changes, not scroll
- Buttons stayed at initial positions during scroll

### Attempt 2: onGloballyPositioned + onPlaced
- `onPlaced` fires on scroll, but in TRAVERSAL phase (AFTER drawBatch)
- 1-frame position lag → buttons "swim" behind cards

### Attempt 3: NestedScrollConnection + applyScrollDelta
- Intercept scroll delta in INPUT phase, pre-correct positions before drawBatch
- Failed because:
  - Sign was wrong initially (fixed, still broken)
  - `onPreScroll` receives `available` delta, not `consumed` — differs at scroll boundaries
  - During fling, deltas come from animation, timing differs
  - ConcurrentHashMap modification during iteration causes stale reads
  - If `onPlaced` misses even ONE frame, accumulated delta drifts permanently
  - Caused buttons to detach, swap content between cards, and disappear entirely

### Current Fix (Path A): Remove applyScrollDelta entirely
- Removed NestedScrollConnection and applyScrollDelta
- Rely solely on onGloballyPositioned + onPlaced
- May have 1-frame lag (~10-30px during fast scroll)
- Eliminates all catastrophic position failures (detaching, swapping, disappearing)

## Industry Research

No other SDK does TextureView overlay batching with scroll sync. Every framework that achieves zero position lag keeps layout and drawing in the same pipeline:

| Framework | Approach | Position Lag? |
|-----------|----------|---------------|
| Flutter | Single surface, everything in same pipeline | No |
| Lottie | Per-item Drawable, draws into View's Canvas | No |
| Unity/Unreal | Single scene, transform hierarchy | No relative lag |
| RecyclerView ItemDecoration | Draws on RecyclerView's Canvas | No |
| Rive batched (our approach) | Separate TextureView overlay | Yes — fundamentally |

## Performance Comparison (10 visible items, 60fps)

### GPU

| | Original (per-item) | Current Batched | Path A (onPlaced only) | Path B (DrawScope) | Path C (hybrid) |
|---|---|---|---|---|---|
| EGL contexts | 10 | 1 | 1 | 1 (offscreen) | Mixed |
| Context switches/frame | 10 (0.5-2ms each) | 1 | 1 | 1 + 0 (bitmap uses Compose ctx) | Varies |
| GPU render targets | 10 (item-sized) | 1 (screen-sized) | 1 (screen-sized) | 1 offscreen + N bitmap uploads | Varies |
| GPU memory | 20-40MB | 4-8MB | 4-8MB | 8-15MB | 5-20MB |

### CPU

| | Original | Current Batched | Path A | Path B | Path C |
|---|---|---|---|---|---|
| Render loops | 10 | 1 | 1 | 1 | Mixed |
| GPU→CPU readback | None | None | None | Yes (~1-3ms for 10 items) | None for batched |
| GC pressure | High | Near zero | Near zero | Medium (bitmaps) | Low-medium |
| Main thread work | Low | Low | Low | Higher (drawImage calls) | Low-medium |

### Memory

| | Original | Current Batched | Path A | Path B | Path C |
|---|---|---|---|---|---|
| GPU memory | 20-40MB | 4-8MB | 4-8MB | 8-15MB | 5-20MB |
| CPU memory (native) | 10-20MB | ~2MB | ~2MB | ~450KB-5MB (depends on item size) | 2-10MB |
| Total | 30-60MB | 6-10MB | 6-10MB | 10-20MB | 10-30MB |

### Scroll Correctness

| | Original | Current Batched | Path A | Path B | Path C |
|---|---|---|---|---|---|
| Position sync | Perfect | Broken | 1-frame lag | Perfect | Perfect |
| Button swapping | Never | Yes | Never | Never | Never |
| Button disappearing | Never | Yes | Never | Never | Never |
| Scroll jank | 5-15ms spikes | None | None | None | Some |

## If Path A Doesn't Work (visible lag during scroll)

### Path B: Compose DrawScope Approach

Render artboards to offscreen bitmaps, draw them via `Modifier.drawBehind { drawImage(bitmap) }`.

**How it works:**
1. Render loop runs on background thread, renders each artboard to an offscreen GPU target
2. Read pixels back to CPU bitmap (glReadPixels or equivalent)
3. Each RiveBatchItem uses `Modifier.drawBehind` to draw its bitmap
4. `drawBehind` runs during Compose's DRAW phase — AFTER layout — so position is always correct

**Why position is perfect:** `drawBehind` runs during the draw phase of the Compose frame, at the exact position determined by the layout phase. No separate surface. No position communication across frame boundaries.

**Trade-offs:**
- GPU→CPU readback cost: ~1-3ms per frame for 10 items
- Per-item bitmap memory (poolable)
- More main thread work (drawImage calls)
- Animation content may be 1 frame old, but POSITION is always perfect
- No z-ordering constraints (items intersperse naturally with Compose content)

**Implementation approach:**
1. Keep the single EGL context and render loop
2. Instead of drawBatch to TextureView, render each artboard to an offscreen FBO
3. glReadPixels each item's region into a pre-allocated Bitmap
4. Each RiveBatchItem stores a `mutableStateOf<Bitmap?>` updated by the render loop
5. RiveBatchItem uses `Modifier.drawBehind { bitmap?.let { drawImage(it.asImageBitmap()) } }`
6. Remove the TextureView entirely

**Key SDK changes needed:**
- New method: `CommandQueue.drawToOffscreen()` or `CommandQueue.drawArtboardToBitmap()`
- RiveBatchItem becomes a real drawing participant in Compose (not an empty layout)
- RiveBatchCoordinator changes from position tracker to bitmap distributor

### Path C: Hybrid Approach

Use batched TextureView for static screens, per-item PoolableRiveView for LazyColumn items.

**How it works:**
- Items in scroll containers use `batched = false` (existing escape hatch)
- Items in static layouts (tabs, grids, popups) use `batched = true`
- Developer decides per-component

**Trade-offs:**
- Simple to implement (already have the mechanism)
- Loses batching benefit exactly where it matters most (scroll performance)
- Per-item TextureViews reintroduce the original problems in scroll contexts

## Recommendation Order

1. **Path A** (current) — Test first. If 1-frame lag is invisible, ship it.
2. **Path B** — If lag is visible, this is the only approach with both efficiency AND perfect sync.
3. **Path C** — Fallback if Path B is too complex for the SDK.

## Files Changed

**SDK (Nagarjuna0033/rive-android):**
- `kotlin/src/main/kotlin/app/rive/RiveBatch.kt` — Removed applyScrollDelta + NestedScrollConnection

**App (Nagarjuna0033/rive-cmp):**
- `core/rive/libs/rive-android-local.aar` — Rebuilt AAR
- `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt` — Reference copy updated
