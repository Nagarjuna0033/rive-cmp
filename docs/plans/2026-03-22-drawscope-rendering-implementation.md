# DrawScope Rendering Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace TextureView overlay with per-item `Modifier.drawBehind` rendering to fix scroll position sync permanently.

**Architecture:** Each Rive item renders to an offscreen GPU PBuffer surface via `CommandQueue.drawToBuffer()`, converts pixels to a Bitmap, and draws via Compose's `Modifier.drawBehind`. Since `drawBehind` runs in Compose's DRAW phase (after LAYOUT), position is always correct — no TextureView position lag.

**Tech Stack:** Rive SDK `CommandQueue.drawToBuffer`, Android `Bitmap.setPixels`, Compose `Modifier.drawBehind`, offscreen PBuffer surfaces via `createImageSurface`

**Design doc:** `docs/plans/2026-03-22-drawscope-rendering-design.md`

---

## Background

### Why TextureView doesn't work for scroll

Android's Choreographer frame phases:
```
1. INPUT       → Touch/scroll events
2. ANIMATION   → withFrameNanos resumes → drawBatch() renders
3. TRAVERSAL   → Layout pass → onPlaced fires → positions updated
```

`drawBatch()` reads item positions BEFORE `onPlaced` updates them. Items rendered at stale positions → buttons lag behind cards during scroll.

### Why DrawScope fixes it

`Modifier.drawBehind` runs during Compose's DRAW phase, which happens AFTER layout. Each item draws its bitmap at whatever position Compose placed it — no cross-phase coordination needed.

### Key SDK methods we use

```kotlin
// Creates an offscreen PBuffer surface (no window needed)
CommandQueue.createImageSurface(width: Int, height: Int): RiveSurface

// Renders artboard to surface AND reads pixels back (synchronous, blocks caller)
CommandQueue.drawToBuffer(
    artboardHandle: ArtboardHandle,
    stateMachineHandle: StateMachineHandle,
    surface: RiveSurface,
    buffer: ByteArray,        // Must be width * height * 4 (RGBA)
    width: Int, height: Int,
    fit: Fit,
    clearColor: Int
)

// Releases GPU resources for an offscreen surface
CommandQueue.destroyRiveSurface(surface: RiveSurface)

// Advances state machine by delta time
CommandQueue.advanceStateMachine(handle: StateMachineHandle, deltaTime: Duration)
```

### RGBA → ARGB conversion

OpenGL returns RGBA bytes. Android Bitmap needs ARGB ints. The SDK's `RenderBuffer.copyInto()` shows the pattern (see `RenderBuffer.kt:106-118`):

```kotlin
val r = pixels[i].toInt() and 0xFF
val g = pixels[i + 1].toInt() and 0xFF
val b = pixels[i + 2].toInt() and 0xFF
val a = pixels[i + 3].toInt() and 0xFF
argb[pixel] = (a shl 24) or (r shl 16) or (g shl 8) or b
```

---

## Task 1: Rewrite RiveBatch.kt — Replace TextureView with DrawScope rendering

**Files:**
- Modify: `/tmp/rive-upstream-shallow/kotlin/src/main/kotlin/app/rive/RiveBatch.kt`

**What changes:**
- `RiveBatchCoordinator` → `RiveBatchRenderer` (renders per-item to offscreen surfaces, distributes bitmaps)
- `RiveBatchSurface` → No TextureView, just provides renderer via CompositionLocal + `Box` wrapper
- `RiveBatchItem` → Uses `Modifier.drawBehind { drawImage(bitmap) }` instead of empty layout
- `BatchItemDescriptor` → Removed (renderer manages per-item state internally)
- `LocalRiveBatchCoordinator` → `LocalRiveBatchRenderer`

**What stays the same:**
- Public API: `RiveBatchSurface(riveWorker, modifier, content)` and `RiveBatchItem(file, modifier, viewModelInstance, fit, backgroundColor)`
- Touch handling (`pointerInput` block in `RiveBatchItem`)
- Lifecycle-aware render loop (`repeatOnLifecycle`)
- `rememberArtboard`, `rememberStateMachine` usage

**Step 1: Write the complete new RiveBatch.kt**

Replace the entire file with:

```kotlin
package app.rive

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.RiveSurface
import app.rive.core.StateMachineHandle
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val BATCH_TAG = "Rive/Batch"
private const val BATCH_DRAW_TAG = "Rive/Batch/Draw"

/**
 * Renders multiple Rive items using per-item offscreen surfaces and Compose DrawScope.
 *
 * Each item gets its own offscreen [RiveSurface] (PBuffer). The render loop draws each
 * artboard to its surface via [CommandQueue.drawToBuffer], converts pixels to a [Bitmap],
 * and distributes the bitmap to the item for display via [Modifier.drawBehind].
 *
 * This eliminates the TextureView position sync problem: [drawBehind] runs during
 * Compose's DRAW phase (after LAYOUT), so position is always correct.
 */
class RiveBatchRenderer(private val riveWorker: CommandQueue) {

    /**
     * Per-item rendering state. Manages the offscreen surface, pixel buffers, and bitmap
     * for a single Rive artboard.
     */
    private class ItemState(
        var artboardHandle: ArtboardHandle,
        var stateMachineHandle: StateMachineHandle,
        var width: Int,
        var height: Int,
        var fit: Fit,
        var backgroundColor: Int,
        val onBitmap: (ImageBitmap) -> Unit,
    ) {
        var surface: RiveSurface? = null
        var pixels: ByteArray? = null
        var argbScratch: IntArray? = null
        var bitmap: Bitmap? = null
    }

    private val items = ConcurrentHashMap<Any, ItemState>()

    /**
     * Register or update an item for rendering.
     *
     * If size changes, the offscreen surface and buffers are reallocated on the next frame.
     * If only fit/backgroundColor change, the existing surface is reused.
     */
    fun register(
        key: Any,
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        width: Int,
        height: Int,
        fit: Fit,
        backgroundColor: Int,
        onBitmap: (ImageBitmap) -> Unit,
    ) {
        val existing = items[key]
        if (existing != null) {
            // Size changed — invalidate cached resources (reallocated in renderFrame).
            if (existing.width != width || existing.height != height) {
                existing.surface?.let { riveWorker.destroyRiveSurface(it) }
                existing.surface = null
                existing.pixels = null
                existing.argbScratch = null
                existing.bitmap?.recycle()
                existing.bitmap = null
            }
            existing.artboardHandle = artboardHandle
            existing.stateMachineHandle = stateMachineHandle
            existing.width = width
            existing.height = height
            existing.fit = fit
            existing.backgroundColor = backgroundColor
            return
        }
        items[key] = ItemState(
            artboardHandle, stateMachineHandle,
            width, height, fit, backgroundColor, onBitmap,
        )
    }

    /** Unregister an item and release its GPU resources. */
    fun unregister(key: Any) {
        val item = items.remove(key) ?: return
        item.surface?.let { riveWorker.destroyRiveSurface(it) }
        item.bitmap?.recycle()
    }

    /**
     * Render all registered items for this frame.
     *
     * For each item:
     * 1. Ensure offscreen surface + buffers exist (lazy allocation)
     * 2. Advance state machine by [deltaTime]
     * 3. Render artboard to offscreen surface + read pixels back (synchronous)
     * 4. Convert RGBA → ARGB and set on bitmap
     * 5. Distribute bitmap to the composable via [onBitmap] callback
     */
    fun renderFrame(deltaTime: Duration) {
        for ((_, item) in items) {
            if (item.width <= 0 || item.height <= 0) continue

            // Lazy-allocate offscreen surface and buffers on first frame.
            if (item.surface == null) {
                item.surface = riveWorker.createImageSurface(item.width, item.height)
                val pixelCount = item.width * item.height
                item.pixels = ByteArray(pixelCount * 4)
                item.argbScratch = IntArray(pixelCount)
                item.bitmap = Bitmap.createBitmap(
                    item.width, item.height, Bitmap.Config.ARGB_8888
                )
            }

            // Advance state machine.
            riveWorker.advanceStateMachine(item.stateMachineHandle, deltaTime)

            // Render artboard to offscreen buffer (draw + glReadPixels in one call).
            try {
                riveWorker.drawToBuffer(
                    item.artboardHandle,
                    item.stateMachineHandle,
                    item.surface!!,
                    item.pixels!!,
                    item.width,
                    item.height,
                    item.fit,
                    item.backgroundColor,
                )
            } catch (e: Exception) {
                RiveLog.e(BATCH_DRAW_TAG) { "drawToBuffer failed: ${e.message}" }
                continue
            }

            // Convert RGBA (OpenGL byte order) → ARGB (Android Bitmap int order).
            val px = item.pixels!!
            val argb = item.argbScratch!!
            var byteIdx = 0
            var pixelIdx = 0
            val totalPixels = item.width * item.height
            while (pixelIdx < totalPixels) {
                val r = px[byteIdx].toInt() and 0xFF
                val g = px[byteIdx + 1].toInt() and 0xFF
                val b = px[byteIdx + 2].toInt() and 0xFF
                val a = px[byteIdx + 3].toInt() and 0xFF
                argb[pixelIdx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                byteIdx += 4
                pixelIdx++
            }
            item.bitmap!!.setPixels(argb, 0, item.width, 0, 0, item.width, item.height)

            // Distribute bitmap to the composable (triggers drawBehind invalidation).
            item.onBitmap(item.bitmap!!.asImageBitmap())
        }
    }

    /** Release all GPU resources. Called from DisposableEffect. */
    fun destroy() {
        for ((_, item) in items) {
            item.surface?.let { riveWorker.destroyRiveSurface(it) }
            item.bitmap?.recycle()
        }
        items.clear()
    }
}

/**
 * CompositionLocal that provides the [RiveBatchRenderer] to child [RiveBatchItem]s.
 */
val LocalRiveBatchRenderer = compositionLocalOf<RiveBatchRenderer?> { null }

// Keep old name accessible for any external references during migration.
@Deprecated("Use LocalRiveBatchRenderer", replaceWith = ReplaceWith("LocalRiveBatchRenderer"))
val LocalRiveBatchCoordinator = LocalRiveBatchRenderer

/**
 * A shared rendering coordinator that batches all child [RiveBatchItem] draws.
 *
 * Unlike the previous TextureView approach, this does NOT create any Android View.
 * It provides a [RiveBatchRenderer] to children and runs a render loop that distributes
 * bitmaps for each item to draw via [Modifier.drawBehind].
 *
 * @param riveWorker The Rive command queue / worker to use for rendering.
 * @param modifier Modifier applied to the surface layout.
 * @param content Composable content that should contain [RiveBatchItem]s.
 */
@Composable
fun RiveBatchSurface(
    riveWorker: CommandQueue,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") surfaceClearColor: Int = Color.Transparent.toArgb(),
    content: @Composable () -> Unit,
) {
    val renderer = remember { RiveBatchRenderer(riveWorker) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Clean up all offscreen surfaces and bitmaps on dispose.
    DisposableEffect(renderer) {
        onDispose { renderer.destroy() }
    }

    // Render loop: advances state machines and renders all items each frame.
    LaunchedEffect(lifecycleOwner, renderer) {
        RiveLog.d(BATCH_DRAW_TAG) { "Starting DrawScope render loop" }

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastFrameTime = Duration.ZERO
            while (isActive) {
                val deltaTime = withFrameNanos { frameTimeNs ->
                    val frameTime = frameTimeNs.nanoseconds
                    val dt = if (lastFrameTime == Duration.ZERO) {
                        Duration.ZERO
                    } else {
                        frameTime - lastFrameTime
                    }
                    lastFrameTime = frameTime
                    dt
                }

                renderer.renderFrame(deltaTime)
            }
        }
    }

    // Provide renderer to children. Box applies the modifier (e.g. navigationBarsPadding).
    CompositionLocalProvider(LocalRiveBatchRenderer provides renderer) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/**
 * A single Rive item rendered within a [RiveBatchSurface].
 *
 * Instead of creating its own rendering surface, this composable registers itself
 * with the parent [RiveBatchRenderer]. The renderer draws the artboard to an offscreen
 * surface and provides a bitmap, which this composable draws via [Modifier.drawBehind].
 *
 * Position sync is perfect because [drawBehind] runs during Compose's DRAW phase,
 * after layout has determined the correct position.
 *
 * @param file The [RiveFile] to instantiate the artboard from.
 * @param modifier Modifier for layout sizing and interaction.
 * @param viewModelInstance Optional [ViewModelInstance] to bind to the state machine.
 * @param fit How the artboard should be fitted within this item's bounds.
 * @param backgroundColor Per-item clear color before drawing.
 */
@Composable
fun RiveBatchItem(
    file: RiveFile,
    modifier: Modifier = Modifier,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
    backgroundColor: Int = Color.Transparent.toArgb(),
) {
    val renderer = LocalRiveBatchRenderer.current
        ?: error("RiveBatchItem must be placed inside a RiveBatchSurface")

    val riveWorker = file.riveWorker
    val artboardToUse = rememberArtboard(file)
    val artboardHandle = artboardToUse.artboardHandle
    val stateMachineToUse = rememberStateMachine(artboardToUse)
    val stateMachineHandle = stateMachineToUse.stateMachineHandle

    // Bitmap state — updated by the renderer each frame via onBitmap callback.
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Bind the view model instance to the state machine if provided.
    LaunchedEffect(stateMachineHandle, viewModelInstance) {
        viewModelInstance ?: return@LaunchedEffect
        riveWorker.bindViewModelInstance(stateMachineHandle, viewModelInstance.instanceHandle)
        // Keep collecting dirty flow to stay responsive to VMI changes.
        viewModelInstance.dirtyFlow.collect { /* no-op, just keeps collection alive */ }
    }

    // Unregister when leaving composition (e.g. scrolled off screen in LazyColumn).
    DisposableEffect(stateMachineHandle) {
        onDispose {
            renderer.unregister(stateMachineHandle)
        }
    }

    // Combined modifier:
    // 1. onGloballyPositioned — tracks size for renderer registration (NOT position)
    // 2. drawBehind — draws the bitmap at whatever position Compose placed this item
    // 3. pointerInput — forwards touch events to the state machine
    val combinedModifier = modifier
        .onGloballyPositioned { coords ->
            if (coords.isAttached && coords.size.width > 0 && coords.size.height > 0) {
                renderer.register(
                    key = stateMachineHandle,
                    artboardHandle = artboardHandle,
                    stateMachineHandle = stateMachineHandle,
                    width = coords.size.width,
                    height = coords.size.height,
                    fit = fit,
                    backgroundColor = backgroundColor,
                    onBitmap = { bitmap = it },
                )
            }
        }
        .drawBehind {
            bitmap?.let { drawImage(it) }
        }
        .pointerInput(stateMachineHandle, fit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val boundsWidth = size.width.toFloat()
                    val boundsHeight = size.height.toFloat()

                    for (change in event.changes) {
                        val px = change.position.x
                        val py = change.position.y
                        val id = change.id.value.toInt()

                        when (event.type) {
                            PointerEventType.Press -> {
                                riveWorker.pointerDown(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                            PointerEventType.Move -> {
                                riveWorker.pointerMove(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                            PointerEventType.Release -> {
                                riveWorker.pointerUp(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                                riveWorker.pointerExit(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                            PointerEventType.Exit -> {
                                riveWorker.pointerExit(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                        }
                        change.consume()
                    }
                }
            }
        }

    Layout(
        modifier = combinedModifier,
        content = {}
    ) { _, constraints ->
        layout(constraints.maxWidth, constraints.maxHeight) {}
    }
}
```

**Step 2: Verify compilation**

```bash
cd /tmp/rive-upstream-shallow && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kotlin:compileReleaseKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

If compilation fails, check:
- Missing imports (the file should be self-contained with the imports listed above)
- `Box` requires `androidx.compose.foundation.layout.Box` — verify the dependency exists in `kotlin/build.gradle.kts`
- `drawBehind` requires `androidx.compose.ui.draw` — should be available via existing Compose UI dependency

**Step 3: Commit**

```bash
cd /tmp/rive-upstream-shallow
git add kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat: replace TextureView with DrawScope rendering (Path B)

Renders each Rive item to an offscreen PBuffer surface via drawToBuffer,
converts pixels to Bitmap, and draws via Modifier.drawBehind. This fixes
scroll position sync permanently — drawBehind runs in DRAW phase after
layout, so position is always correct.

Removes: TextureView, AndroidView, RiveBatchCoordinator, BatchItemDescriptor,
position tracking (onPlaced, positionInRoot), surfaceClearColor.

Adds: RiveBatchRenderer with per-item offscreen surfaces, RGBA→ARGB conversion,
bitmap distribution via mutableStateOf + drawBehind."
```

---

## Task 2: Build AAR and deploy to both repos

**Files:**
- Build: `/tmp/rive-upstream-shallow/kotlin/build/outputs/aar/kotlin-release.aar`
- Copy to: `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS/core/rive/libs/rive-android-local.aar`
- Copy to: `/Users/peeyush.gulati/Desktop/Projects/Rive/rive-android/app/libs/rive-android-local.aar`
- Copy to: `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS/SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt` (reference copy)

**Step 1: Build the release AAR**

```bash
cd /tmp/rive-upstream-shallow && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kotlin:assembleRelease 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

The AAR is at: `/tmp/rive-upstream-shallow/kotlin/build/outputs/aar/kotlin-release.aar`

**Step 2: Copy AAR to CMP app**

```bash
cp /tmp/rive-upstream-shallow/kotlin/build/outputs/aar/kotlin-release.aar /Users/peeyush.gulati/Desktop/Projects/Rive-IOS/core/rive/libs/rive-android-local.aar
```

**Step 3: Copy AAR to SDK fork**

```bash
cp /tmp/rive-upstream-shallow/kotlin/build/outputs/aar/kotlin-release.aar /Users/peeyush.gulati/Desktop/Projects/Rive/rive-android/app/libs/rive-android-local.aar
```

**Step 4: Copy RiveBatch.kt reference to CMP app SDK folder**

```bash
cp /tmp/rive-upstream-shallow/kotlin/src/main/kotlin/app/rive/RiveBatch.kt /Users/peeyush.gulati/Desktop/Projects/Rive-IOS/SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
```

**Step 5: Commit SDK fork**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive/rive-android
git add app/libs/rive-android-local.aar
git commit -m "feat: update AAR with DrawScope rendering (Path B)

Replaces TextureView overlay with per-item Modifier.drawBehind rendering.
Fixes scroll position sync permanently."
```

**Step 6: Commit CMP app**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
git add core/rive/libs/rive-android-local.aar SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat: update AAR and SDK reference with DrawScope rendering (Path B)

Replaces TextureView overlay with per-item Modifier.drawBehind rendering.
Fixes scroll position sync permanently."
```

**Step 7: Push both repos to main**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive/rive-android && git push origin main
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS && git push origin main
```

---

## Task 3: Device testing verification

**Context:** This is GPU rendering code that requires physical device verification. Automated tests are not feasible because they require native Rive libraries, OpenGL context, and visual comparison.

**Step 1: Build and install CMP app**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :composeApp:installDebug
```

**Step 2: Verify animations render correctly**

Open the app on device. Check:
- [ ] Rive animations are visible (not blank)
- [ ] Animations play smoothly (state machines advancing)
- [ ] Colors and content match expected designs
- [ ] No visual glitches or artifacts

If items are blank: check logcat for `Rive/Batch/Draw` errors. Common issues:
- `drawToBuffer failed` → surface creation failed, check GPU memory
- No log output at all → render loop not starting, check lifecycle

**Step 3: Verify scroll behavior (THE CRITICAL TEST)**

Navigate to a screen with RiveBatchItems inside a LazyColumn. Scroll fast. Check:
- [ ] Buttons/cards stay perfectly aligned with their content during scroll
- [ ] No position lag or "swimming" effect
- [ ] No buttons detaching from cards
- [ ] No content swapping between cards
- [ ] No items disappearing during scroll

This is the main fix. If position is still lagging, the issue is NOT in this code — check whether the app is actually using the new AAR.

**Step 4: Verify touch interaction**

Tap on Rive buttons inside the scrolling list. Check:
- [ ] Buttons respond to taps (state machine triggers)
- [ ] Touch coordinates are correct (button highlights where you tap)
- [ ] Scroll still works when dragging on non-button areas

**Step 5: Verify tab switching**

Switch between tabs that contain Rive content. Check:
- [ ] Animations appear immediately (no 200ms delay — this was the original per-item TextureView issue)
- [ ] Content is correct after switching back
- [ ] No memory leaks (check logcat for surface creation/destruction balance)

**Step 6: Performance check**

Use Android Profiler or `adb shell dumpsys gfxinfo` to check:
- [ ] Frame times stay under 16ms for most frames
- [ ] No janky spikes > 30ms during scroll
- [ ] GPU memory usage is reasonable (should be similar to current batched approach)

---

## Task 4: Future optimizations (document only, do not implement yet)

These optimizations should be considered if performance testing reveals issues:

### 4a. HardwareBuffer zero-copy (if readback is slow)

Replace `drawToBuffer` (which does `glReadPixels`) with:
1. Create `HardwareBuffer` with `USAGE_GPU_SAMPLED_IMAGE | USAGE_GPU_COLOR_OUTPUT`
2. Create EGLImage from HardwareBuffer
3. Bind as FBO color attachment
4. Render to it (zero-copy — GPU writes directly to buffer)
5. Wrap in `Bitmap.wrapHardwareBuffer()` (zero-copy — no pixel transfer)

Requires API 26+ (already our effective minimum). Would eliminate RGBA→ARGB conversion entirely.

### 4b. Dirty flow idle optimization (if CPU usage is high when idle)

Make `dirtyFlow` public in SDK fork. In render loop:
- Track which items are dirty (state machine changed)
- Only re-render dirty items
- If no items dirty for N frames, suspend and wait for dirtyFlow signal

Reduces idle CPU usage from ~5% to ~0%.

### 4c. FBO size bucketing (if too many surfaces)

Pool offscreen surfaces into 3 size buckets instead of per-item:
- Small: 500×200 (buttons, icons)
- Medium: 1000×750 (cards)
- Large: 1080×1200 (hero animations)

Reduces surface count from N to 3. Renders each item by setting glViewport to actual size within the bucket-sized FBO.

### 4d. Background thread rendering (if main thread is blocked too long)

Move `drawToBuffer` calls off the main thread:
1. Render loop runs on a dedicated coroutine dispatcher
2. `withFrameNanos` fires on main thread, posts work to render thread
3. Render thread does drawToBuffer for all items
4. Posts bitmaps back to main thread via `withContext(Dispatchers.Main)`

Reduces main thread blocking from ~N ms to ~0 ms per frame.
