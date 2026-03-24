# HardwareBuffer Zero-Copy Rendering Design

## Problem

Our DrawScope batched rendering (Path B) eliminates TextureView position sync issues but introduces per-frame CPU overhead:

```
GPU FBO → glReadPixels (~1ms) → ByteArray(RGBA) → RGBA→ARGB loop (~0.5ms) → Bitmap.setPixels() (~0.3ms)
```

For 10 items, that's ~18ms/frame of CPU work just moving pixels. Profiler data shows:
- +20% native memory allocation vs Compose buttons (1.281GB vs 1.067GB)
- +28% native allocation count (299k vs 234k)
- +11% retained memory (38MB vs 34MB)

Most of this overhead is the per-frame pixel copy pipeline.

## Solution: EGLImage → AHardwareBuffer Zero-Copy

Replace the CPU pixel pipeline with a GPU-to-GPU path using Android's HardwareBuffer API (API 26+).

### New Data Flow

```
GPU FBO → eglCreateImageKHR (GPU→GPU, ~0μs)
    → AHardwareBuffer → Bitmap.wrapHardwareBuffer() (ref only, ~0μs)
    → Compose drawImage
```

**What's eliminated:**
- `glReadPixels` — no GPU→CPU pixel transfer
- `ByteArray(w*h*4)` — no RGBA byte buffer per item
- `IntArray(w*h)` — no ARGB scratch buffer per item
- RGBA→ARGB conversion loop — no CPU pixel processing
- `Bitmap.setPixels()` — no CPU→bitmap copy

**What stays the same:**
- FBO rendering (Rive C++ renderer draws to same FBO)
- State machine advancement
- Compose `drawBehind { drawImage(bitmap) }` display
- All public APIs unchanged

## Architecture

### Approach: EGLImage → AHardwareBuffer

Render to existing PBuffer FBO, then use `eglCreateImageKHR` to wrap the FBO's texture as an `EGLImageKHR`, bind it to an `AHardwareBuffer`, and wrap that as a `Bitmap`. GPU texture IS the bitmap — zero pixel transfer.

### Double-Buffering (Ping-Pong)

Each item holds 2 HardwareBuffers to avoid GPU/RenderThread race conditions:
- Frame N renders to buffer A, Compose draws buffer A
- Frame N+1 renders to buffer B, Compose draws buffer B
- No per-frame allocation, no race condition

### Components

| Component | Change | Location |
|-----------|--------|----------|
| C++ JNI method | New `cppDrawToHardwareBuffer()` | `kotlin/src/main/cpp/` |
| CommandQueueBridge | New `external fun` declaration | `CommandQueueBridge.kt` |
| CommandQueue | New `drawToHardwareBuffer()` method | `CommandQueue.kt` |
| RiveBatchRenderer | Replace render loop internals | `RiveBatch.kt` |
| ItemState | Remove ByteArray/IntArray, add double-buffer | `RiveBatch.kt` |

### C++ Native Method

```cpp
// New JNI method
void cppDrawToHardwareBuffer(
    commandQueue, renderContext, surface, drawKey,
    artboardHandle, stateMachineHandle, renderTarget,
    width, height, fit, alignment, scaleFactor, clearColor,
    AHardwareBuffer* targetBuffer  // pre-allocated by Kotlin side
) {
    // 1. Render artboard to existing FBO (same as drawToBuffer)
    // 2. Create EGLImageKHR from AHardwareBuffer
    // 3. Create temp texture, bind EGLImage to it
    // 4. glBlitFramebuffer: FBO → HardwareBuffer-backed texture (GPU-to-GPU)
    // 5. glFinish() to ensure GPU completion before return
}
```

### Kotlin API

```kotlin
// New method on CommandQueue (alongside existing drawToBuffer)
fun drawToHardwareBuffer(
    artboardHandle: ArtboardHandle,
    stateMachineHandle: StateMachineHandle,
    surface: RiveSurface,
    targetBuffer: HardwareBuffer,
    width: Int, height: Int,
    fit: Fit, clearColor: Int
)
```

### ItemState Changes

```kotlin
// BEFORE
private class ItemState(...) {
    var surface: RiveSurface? = null
    var pixels: ByteArray? = null       // w*h*4 bytes
    var argbScratch: IntArray? = null    // w*h ints
    var bitmap: Bitmap? = null           // w*h*4 bytes
}
// Total per item CPU: ~3x (w*h*4) bytes

// AFTER
private class ItemState(...) {
    var surface: RiveSurface? = null
    var hwBufferA: HardwareBuffer? = null  // GPU-side only
    var hwBufferB: HardwareBuffer? = null  // GPU-side only
    var useA: Boolean = true
}
// Total per item CPU: ~0 bytes (buffers live on GPU)
```

### renderFrame() Changes

```kotlin
// BEFORE (per item)
riveWorker.drawToBuffer(artboard, sm, surface, pixels, w, h, fit, bg)
// 20-line RGBA→ARGB conversion loop
bitmap.setPixels(argb, 0, w, 0, 0, w, h)
item.onBitmap(bitmap.asImageBitmap())

// AFTER (per item)
val hwBuffer = if (item.useA) item.hwBufferA!! else item.hwBufferB!!
riveWorker.drawToHardwareBuffer(artboard, sm, surface, hwBuffer, w, h, fit, bg)
val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, null)!!
item.onBitmap(bitmap.asImageBitmap())
item.useA = !item.useA  // flip for next frame
```

### Cleanup

```kotlin
fun unregister(key: Any) {
    val item = items.remove(key) ?: return
    item.surface?.let { riveWorker.destroyRiveSurface(it) }
    item.hwBufferA?.close()
    item.hwBufferB?.close()
    // Bitmaps from wrapHardwareBuffer — let GC handle (RenderThread race)
}
```

## Frame Safety Analysis

### GPU Synchronization
`drawToHardwareBuffer()` is synchronous on the EGL thread. `glFinish()` ensures the GPU completes rendering before the method returns. `Bitmap.wrapHardwareBuffer()` creates a reference — no race.

### Double-Buffer Prevents RenderThread Race
Compose's RenderThread may still draw frame N's bitmap while frame N+1 renders. Double-buffering ensures they use different HardwareBuffers — no conflict.

### Position Sync
Unchanged — `drawBehind` runs after layout phase. No position lag.

## Requirements

- **minSdk**: 26 (Android 8.0) — HardwareBuffer API requirement
- **EGL extension**: `EGL_ANDROID_image_native_buffer` (99%+ of devices)
- **OpenGL ES**: 3.0+ (already required by Rive SDK)

## Expected Impact

From profiler data (10 Rive buttons vs 10 Compose buttons):

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Native alloc size | 1.281 GB | ~1.07 GB | -17% |
| Native alloc count | 299k | ~240k | -20% |
| Retained memory | 38 MB | ~35 MB | -8% |
| Per-frame CPU | ~1.5-3ms (10 items) | ~0ms | -100% |
| Peak spike | 459 MB | ~390 MB | -15% |
| Kotlin allocations | 707k | ~600k | -15% |

## What Doesn't Change

- `RiveBatchSurface` composable
- `RiveBatchItem` composable
- `drawToBuffer()` method (stays for non-batched/snapshot use)
- Touch handling
- Lifecycle management
- Public API (`RiveComponent(batched = true/false)`)

## Files to Change

### SDK (`Nagarjuna0033/rive-android`)

| File | Action |
|------|--------|
| `kotlin/src/main/cpp/src/bindings/command_queue_bindings.cpp` | Add JNI `cppDrawToHardwareBuffer()` |
| `kotlin/src/main/cpp/CMakeLists.txt` | Link `android` lib for AHardwareBuffer |
| `kotlin/src/main/kotlin/app/rive/core/CommandQueueBridge.kt` | Add `external fun cppDrawToHardwareBuffer()` |
| `kotlin/src/main/kotlin/app/rive/core/CommandQueue.kt` | Add `drawToHardwareBuffer()` method |
| `kotlin/src/main/kotlin/app/rive/RiveBatch.kt` | Update RiveBatchRenderer to use HardwareBuffer |

### App (`Nagarjuna0033/rive-cmp`)

| File | Action |
|------|--------|
| `core/rive/libs/rive-android-local.aar` | Rebuild with new SDK |
| `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt` | Reference copy update |
