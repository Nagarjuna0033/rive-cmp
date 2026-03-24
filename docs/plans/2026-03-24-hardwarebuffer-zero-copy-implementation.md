# HardwareBuffer Zero-Copy Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate per-frame glReadPixels and RGBA→ARGB CPU conversion by rendering directly to AHardwareBuffer via EGLImageKHR, achieving zero-copy GPU→Compose bitmap path.

**Architecture:** New C++ JNI method `cppDrawToHardwareBuffer` renders the artboard to the existing FBO, then blits to an AHardwareBuffer-backed texture via EGLImageKHR. Kotlin side creates/manages double-buffered HardwareBuffers per item. Existing `drawToBuffer` stays untouched.

**Tech Stack:** C++17, OpenGL ES 3.0, EGL 1.4, AHardwareBuffer (API 26+), EGLImageKHR, Kotlin, Compose

**Design Doc:** `docs/plans/2026-03-24-hardwarebuffer-zero-copy-design.md`

---

### Task 1: Add C++ JNI Method — `cppDrawToHardwareBuffer`

**Context:** This is the core native method. It reuses the existing draw pipeline from `cppDrawToBuffer` (lines 2423-2601 in `bindings_command_queue.cpp`) but replaces `glReadPixels` with a GPU-to-GPU blit into an AHardwareBuffer-backed texture.

**Files:**
- Modify: `/tmp/rive-upstream-shallow/kotlin/src/main/cpp/src/bindings/bindings_command_queue.cpp` (after line 2601)
- Modify: `/tmp/rive-upstream-shallow/kotlin/src/main/cpp/CMakeLists.txt` (line 160, add nativewindow)

**Step 1: Add required includes to bindings_command_queue.cpp**

Add these includes at the top of the file (after existing includes):

```cpp
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <EGL/eglext.h>
#include <GLES2/gl2ext.h>
```

**Step 2: Add the JNI function after cppDrawToBuffer (after line 2601)**

The function follows the same pattern as `cppDrawToBuffer` but replaces glReadPixels with EGLImage blit:

```cpp
JNIEXPORT void JNICALL
Java_app_rive_core_CommandQueueJNIBridge_cppDrawToHardwareBuffer(
    JNIEnv* env,
    jobject,
    jlong ref,
    jlong renderContextRef,
    jlong surfaceRef,
    jlong drawKey,
    jlong artboardHandleRef,
    jlong stateMachineHandleRef,
    jlong renderTargetRef,
    jint jWidth,
    jint jHeight,
    jbyte jFit,
    jbyte jAlignment,
    jfloat jScaleFactor,
    jint jClearColor,
    jobject jHardwareBuffer) // AHardwareBuffer passed from Kotlin
{
    auto width = static_cast<uint32_t>(jWidth);
    auto height = static_cast<uint32_t>(jHeight);
    auto fit = static_cast<rive::Fit>(jFit);
    auto alignment = static_cast<rive::Alignment>(jAlignment);
    auto scaleFactor = static_cast<float>(jScaleFactor);
    uint32_t clearColor = static_cast<uint32_t>(jClearColor);

    // Get AHardwareBuffer from Java object
    AHardwareBuffer* hardwareBuffer =
        AHardwareBuffer_fromHardwareBuffer(env, jHardwareBuffer);
    if (!hardwareBuffer)
    {
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(exClass, "Invalid HardwareBuffer");
        return;
    }

    auto* commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
    auto* renderContext =
        reinterpret_cast<RenderContextGL*>(renderContextRef);

    enum class DrawResult { Success, Error };
    auto promise = std::make_shared<std::promise<DrawResult>>();
    std::future<DrawResult> future = promise->get_future();
    std::string errorMsg;
    auto errorMsgPtr = std::make_shared<std::string>();

    auto drawWork = [=](rive::CommandServer* server) mutable {
        auto* artboardHandle =
            reinterpret_cast<rive::ArtboardHandle*>(artboardHandleRef);
        auto* stateMachineHandle =
            reinterpret_cast<rive::StateMachineHandle*>(
                stateMachineHandleRef);

        if (!artboardHandle || !artboardHandle->artboardInstance())
        {
            *errorMsgPtr = "Invalid artboard handle";
            promise->set_value(DrawResult::Error);
            return;
        }
        if (!stateMachineHandle ||
            !stateMachineHandle->stateMachineInstance())
        {
            *errorMsgPtr = "Invalid state machine handle";
            promise->set_value(DrawResult::Error);
            return;
        }

        auto* artboardInstance = artboardHandle->artboardInstance();
        auto* stateMachineInstance =
            stateMachineHandle->stateMachineInstance();

        // Bind the surface (same as drawToBuffer)
        auto* surface =
            reinterpret_cast<EGLSurface>(static_cast<uintptr_t>(surfaceRef));
        renderContext->beginFrame(surface);

        // Set up frame descriptor (same as drawToBuffer)
        rive::gpu::RenderContext::FrameDescriptor frameDescriptor;
        frameDescriptor.renderTargetWidth = width;
        frameDescriptor.renderTargetHeight = height;
        frameDescriptor.clearColor =
            rive::colorARGBToVec4(static_cast<rive::ColorInt>(clearColor));

        auto* renderTarget = reinterpret_cast<
            rive::gpu::FramebufferRenderTargetGL*>(renderTargetRef);

        server->renderContext()->beginFrame(frameDescriptor, renderTarget);

        // Create renderer and draw artboard (same as drawToBuffer)
        auto renderer = std::make_unique<rive::Renderer>(
            server->renderContext());
        renderer->align(rive::Fit(fit),
                        rive::Alignment(alignment),
                        rive::AABB(0, 0, width, height),
                        artboardInstance->bounds(),
                        scaleFactor);
        artboardInstance->draw(renderer.get());
        server->renderContext()->flush({.
            resourceCounters = nullptr});

        // --- HERE IS THE KEY DIFFERENCE ---
        // Instead of glReadPixels, blit to HardwareBuffer via EGLImage

        // Get EGL display
        EGLDisplay eglDisplay = eglGetCurrentDisplay();

        // Create EGLImage from AHardwareBuffer
        EGLClientBuffer clientBuffer =
            eglGetNativeClientBufferANDROID(hardwareBuffer);
        if (!clientBuffer)
        {
            *errorMsgPtr = "eglGetNativeClientBufferANDROID failed";
            renderContext->present(surface);
            promise->set_value(DrawResult::Error);
            return;
        }

        EGLint imageAttribs[] = {EGL_NONE};
        EGLImageKHR eglImage = eglCreateImageKHR(
            eglDisplay,
            EGL_NO_CONTEXT,
            EGL_NATIVE_BUFFER_ANDROID,
            clientBuffer,
            imageAttribs);
        if (eglImage == EGL_NO_IMAGE_KHR)
        {
            *errorMsgPtr = "eglCreateImageKHR failed";
            renderContext->present(surface);
            promise->set_value(DrawResult::Error);
            return;
        }

        // Create temp texture and bind EGLImage to it
        GLuint hwbTexture;
        glGenTextures(1, &hwbTexture);
        glBindTexture(GL_TEXTURE_2D, hwbTexture);
        glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, eglImage);

        // Create temp FBO for the HardwareBuffer texture
        GLuint hwbFBO;
        glGenFramebuffers(1, &hwbFBO);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, hwbFBO);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,
                               GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D,
                               hwbTexture,
                               0);

        // Blit from render FBO (READ) to HardwareBuffer FBO (DRAW)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBlitFramebuffer(0, 0, width, height,
                          0, 0, width, height,
                          GL_COLOR_BUFFER_BIT,
                          GL_NEAREST);

        // Ensure GPU completes the blit
        glFinish();

        // Cleanup temp resources
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, &hwbFBO);
        glDeleteTextures(1, &hwbTexture);
        eglDestroyImageKHR(eglDisplay, eglImage);

        renderContext->present(surface);
        promise->set_value(DrawResult::Success);
    };

    commandQueue->runOnce(std::move(drawWork));
    DrawResult result = future.get();

    if (result == DrawResult::Error)
    {
        jclass exClass =
            env->FindClass("app/rive/runtime/kotlin/core/"
                           "errors/RiveDrawToBufferException");
        if (exClass)
        {
            env->ThrowNew(exClass, errorMsgPtr->c_str());
        }
        else
        {
            exClass = env->FindClass("java/lang/RuntimeException");
            env->ThrowNew(exClass, errorMsgPtr->c_str());
        }
    }
}
```

**Step 3: Verify CMakeLists.txt has required libraries**

Check `/tmp/rive-upstream-shallow/kotlin/src/main/cpp/CMakeLists.txt`. The `android` library (`${android-lib}`) is already linked at line 169. This provides `AHardwareBuffer_fromHardwareBuffer` and other AHardwareBuffer APIs. No CMakeLists changes needed — EGL and GLESv3 are also already linked.

**Step 4: Build the native library to verify compilation**

```bash
cd /tmp/rive-upstream-shallow
./gradlew :kotlin:assembleRelease 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL (C++ compiles without errors)

**Step 5: Commit**

```bash
cd /tmp/rive-upstream-shallow
git add kotlin/src/main/cpp/src/bindings/bindings_command_queue.cpp
git commit -m "feat: add cppDrawToHardwareBuffer JNI method for zero-copy GPU rendering"
```

---

### Task 2: Add Kotlin Bridge and CommandQueue Method

**Context:** Wire the new C++ method into the Kotlin API layer. Follow the exact pattern of existing `drawToBuffer` at `CommandQueue.kt:2230-2254` and `CommandQueueBridge.kt:422-437, 879-894`.

**Files:**
- Modify: `/tmp/rive-upstream-shallow/kotlin/src/main/kotlin/app/rive/core/CommandQueueBridge.kt`
- Modify: `/tmp/rive-upstream-shallow/kotlin/src/main/kotlin/app/rive/core/CommandQueue.kt`

**Step 1: Add interface method to CommandQueueBridge**

In `CommandQueueBridge.kt`, after the `cppDrawToBuffer` interface declaration (after line 437), add:

```kotlin
fun cppDrawToHardwareBuffer(
    pointer: Long,
    renderContextPointer: Long,
    surfaceNativePointer: Long,
    drawKey: Long,
    artboardHandle: Long,
    stateMachineHandle: Long,
    renderTargetPointer: Long,
    width: Int,
    height: Int,
    fit: Byte,
    alignment: Byte,
    scaleFactor: Float,
    clearColor: Int,
    hardwareBuffer: android.hardware.HardwareBuffer
)
```

**Step 2: Add external fun in JNIBridge implementation**

In the same file, after the `cppDrawToBuffer` external declaration (after line 894), add:

```kotlin
external override fun cppDrawToHardwareBuffer(
    pointer: Long,
    renderContextPointer: Long,
    surfaceNativePointer: Long,
    drawKey: Long,
    artboardHandle: Long,
    stateMachineHandle: Long,
    renderTargetPointer: Long,
    width: Int,
    height: Int,
    fit: Byte,
    alignment: Byte,
    scaleFactor: Float,
    clearColor: Int,
    hardwareBuffer: android.hardware.HardwareBuffer
)
```

**Step 3: Add drawToHardwareBuffer method in CommandQueue**

In `CommandQueue.kt`, after the `drawToBuffer` method (after line 2254), add:

```kotlin
/**
 * Renders an artboard to a HardwareBuffer via zero-copy GPU blit.
 * No glReadPixels, no CPU pixel conversion — GPU texture IS the bitmap.
 * Requires API 26+.
 */
fun drawToHardwareBuffer(
    artboardHandle: ArtboardHandle,
    stateMachineHandle: StateMachineHandle,
    surface: RiveSurface,
    hardwareBuffer: android.hardware.HardwareBuffer,
    width: Int,
    height: Int,
    fit: Fit = Fit.Contain(),
    clearColor: Int = Color.TRANSPARENT
) = bridge.cppDrawToHardwareBuffer(
    cppPointer.pointer,
    renderContext.nativeObjectPointer,
    surface.surfaceNativePointer,
    surface.drawKey.handle,
    artboardHandle.handle,
    stateMachineHandle.handle,
    surface.renderTargetPointer.pointer,
    width,
    height,
    fit.nativeMapping,
    fit.alignment.nativeMapping,
    fit.scaleFactor,
    clearColor,
    hardwareBuffer
)
```

**Step 4: Build to verify Kotlin compilation**

```bash
cd /tmp/rive-upstream-shallow
./gradlew :kotlin:assembleRelease 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
cd /tmp/rive-upstream-shallow
git add kotlin/src/main/kotlin/app/rive/core/CommandQueueBridge.kt
git add kotlin/src/main/kotlin/app/rive/core/CommandQueue.kt
git commit -m "feat: add drawToHardwareBuffer Kotlin API for zero-copy rendering"
```

---

### Task 3: Update RiveBatchRenderer to Use HardwareBuffer

**Context:** Replace the pixel copy pipeline in `RiveBatchRenderer` (RiveBatch.kt:48-166) with HardwareBuffer zero-copy. Each item gets double-buffered HardwareBuffers instead of ByteArray/IntArray/Bitmap.

**Files:**
- Modify: `/tmp/rive-upstream-shallow/kotlin/src/main/kotlin/app/rive/RiveBatch.kt` (lines 48-166)

**Step 1: Add HardwareBuffer import**

At the top of `RiveBatch.kt`, add after the existing imports (after line 6):

```kotlin
import android.hardware.HardwareBuffer
```

**Step 2: Replace ItemState class (lines 50-63)**

Replace the entire `ItemState` class:

```kotlin
private class ItemState(
    var artboardHandle: ArtboardHandle,
    var stateMachineHandle: StateMachineHandle,
    var width: Int,
    var height: Int,
    var fit: Fit,
    var backgroundColor: Int,
    var onBitmap: (ImageBitmap) -> Unit,
) {
    var surface: RiveSurface? = null
    var hwBufferA: HardwareBuffer? = null
    var hwBufferB: HardwareBuffer? = null
    var useA: Boolean = true
}
```

**Step 3: Update register() to handle HardwareBuffer resize (lines 67-99)**

Replace the size-change handling inside register(). When dimensions change, close old HardwareBuffers:

```kotlin
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
        if (existing.width != width || existing.height != height) {
            existing.surface?.let { riveWorker.destroyRiveSurface(it) }
            existing.surface = null
            existing.hwBufferA?.close()
            existing.hwBufferA = null
            existing.hwBufferB?.close()
            existing.hwBufferB = null
        }
        existing.artboardHandle = artboardHandle
        existing.stateMachineHandle = stateMachineHandle
        existing.width = width
        existing.height = height
        existing.fit = fit
        existing.backgroundColor = backgroundColor
        existing.onBitmap = onBitmap
        return
    }
    items[key] = ItemState(
        artboardHandle, stateMachineHandle,
        width, height, fit, backgroundColor, onBitmap,
    )
}
```

**Step 4: Update unregister() to close HardwareBuffers (lines 101-106)**

```kotlin
fun unregister(key: Any) {
    val item = items.remove(key) ?: return
    item.surface?.let { riveWorker.destroyRiveSurface(it) }
    item.hwBufferA?.close()
    item.hwBufferB?.close()
    // Bitmaps from wrapHardwareBuffer — let GC handle (RenderThread race)
}
```

**Step 5: Replace renderFrame() with HardwareBuffer path (lines 108-158)**

```kotlin
fun renderFrame(deltaTime: Duration) {
    for ((_, item) in items) {
        if (item.width <= 0 || item.height <= 0) continue

        if (item.surface == null) {
            item.surface = riveWorker.createImageSurface(item.width, item.height)
        }
        if (item.hwBufferA == null) {
            item.hwBufferA = HardwareBuffer.create(
                item.width, item.height, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                    HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                    HardwareBuffer.USAGE_GPU_DATA_BUFFER
            )
        }
        if (item.hwBufferB == null) {
            item.hwBufferB = HardwareBuffer.create(
                item.width, item.height, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                    HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                    HardwareBuffer.USAGE_GPU_DATA_BUFFER
            )
        }

        riveWorker.advanceStateMachine(item.stateMachineHandle, deltaTime)

        val hwBuffer = if (item.useA) item.hwBufferA!! else item.hwBufferB!!

        try {
            riveWorker.drawToHardwareBuffer(
                item.artboardHandle,
                item.stateMachineHandle,
                item.surface!!,
                hwBuffer,
                item.width,
                item.height,
                item.fit,
                item.backgroundColor,
            )
        } catch (e: Exception) {
            RiveLog.e(BATCH_DRAW_TAG) { "drawToHardwareBuffer failed: ${e.message}" }
            continue
        }

        val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, null)
        if (bitmap != null) {
            item.onBitmap(bitmap.asImageBitmap())
        }
        item.useA = !item.useA
    }
}
```

**Step 6: Update destroy() to close HardwareBuffers (lines 160-165)**

```kotlin
fun destroy() {
    for ((_, item) in items) {
        item.surface?.let { riveWorker.destroyRiveSurface(it) }
        item.hwBufferA?.close()
        item.hwBufferB?.close()
    }
    items.clear()
}
```

**Step 7: Build to verify**

```bash
cd /tmp/rive-upstream-shallow
./gradlew :kotlin:assembleRelease 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
cd /tmp/rive-upstream-shallow
git add kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat: replace pixel copy pipeline with HardwareBuffer zero-copy in RiveBatchRenderer"
```

---

### Task 4: Build AAR and Update App References

**Context:** Build the updated SDK into an AAR and copy it to the CMP app, plus update the SDK reference copy.

**Files:**
- Build: `/tmp/rive-upstream-shallow/kotlin/build/outputs/aar/kotlin-release.aar`
- Copy to: `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS/core/rive/libs/rive-android-local.aar`
- Copy to: `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS/composeApp/libs/rive-android-local.aar`
- Update: `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS/SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt`

**Step 1: Build the AAR**

```bash
cd /tmp/rive-upstream-shallow
./gradlew :kotlin:assembleRelease
```

Expected: BUILD SUCCESSFUL, AAR at `kotlin/build/outputs/aar/kotlin-release.aar`

**Step 2: Copy AAR to app libs**

```bash
cp /tmp/rive-upstream-shallow/kotlin/build/outputs/aar/kotlin-release.aar \
   /Users/peeyush.gulati/Desktop/Projects/Rive-IOS/core/rive/libs/rive-android-local.aar

cp /tmp/rive-upstream-shallow/kotlin/build/outputs/aar/kotlin-release.aar \
   /Users/peeyush.gulati/Desktop/Projects/Rive-IOS/composeApp/libs/rive-android-local.aar
```

**Step 3: Update SDK reference copy**

Copy the updated `RiveBatch.kt` to the SDK reference folder:

```bash
cp /tmp/rive-upstream-shallow/kotlin/src/main/kotlin/app/rive/RiveBatch.kt \
   /Users/peeyush.gulati/Desktop/Projects/Rive-IOS/SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
```

**Step 4: Commit in CMP app repo**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
git add core/rive/libs/rive-android-local.aar
git add composeApp/libs/rive-android-local.aar
git add SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat: update AAR and SDK reference with HardwareBuffer zero-copy rendering"
```

---

### Task 5: Push SDK Changes and Verify on Device

**Context:** Push the SDK changes to the fork and verify the app runs correctly on device.

**Files:**
- Push: `/tmp/rive-upstream-shallow` → `Nagarjuna0033/rive-android` main branch

**Step 1: Push SDK fork**

```bash
cd /tmp/rive-upstream-shallow
git push origin main
```

**Step 2: Build and run CMP app**

Build and deploy to the Xiaomi device. Verify:
- Rive buttons render correctly (no black screens, no flickering)
- Scroll works without position lag
- Touch interactions work
- No crashes on screen load/unload
- Tab switching works

**Step 3: Profile with Android Profiler**

Compare against the baseline profiler data:
- Native allocation size should be ~17% lower (1.281GB → ~1.07GB)
- Native allocation count should be ~20% lower (299k → ~240k)
- No per-frame ByteArray/IntArray allocations visible in allocation tracker
- Retained memory should be ~8% lower (38MB → ~35MB)

**Step 4: Commit any fixes needed**

If device testing reveals issues, fix and commit incrementally.

---

### Task 6: Update Documentation

**Context:** Update the batched rendering architecture doc to reflect the HardwareBuffer optimization.

**Files:**
- Modify: `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS/docs/batched-rendering-architecture.md`

**Step 1: Update the architecture doc**

In the "Cons" section, remove or update items about pixel readback and RGBA→ARGB conversion. Add a "HardwareBuffer Zero-Copy" section explaining the optimization. Update the performance comparison table.

**Step 2: Commit**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
git add docs/batched-rendering-architecture.md
git commit -m "docs: update architecture doc with HardwareBuffer zero-copy details"
```
