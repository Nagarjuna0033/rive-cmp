# Dual-Layer RiveBatchSurface Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a `background: Boolean` param to `RiveBatchItem` / `RiveComponent` so Rive items can render behind Compose content (for dim overlays, popups) or on top (for confetti, celebrations).

**Architecture:** Split `RiveBatchSurface`'s Layout into 3 z-layers: background TextureView, Compose content, foreground TextureView. Two independent `RiveBatchCoordinator` instances route items to the correct layer. Each TextureView auto-hides when its coordinator has 0 items.

**Tech Stack:** Kotlin, Jetpack Compose, Android TextureView, Rive Android SDK (forked), Compose Multiplatform (KMP)

---

## Repos & File Paths

- **SDK fork:** `/Users/peeyush.gulati/Desktop/Projects/Rive/rive-android`
  - Source: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt`
- **CMP project:** `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS`
  - Common: `core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveExpects.kt`
  - Android: `core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt`
  - iOS: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt`

---

### Task 1: Add `LocalRiveBgBatchCoordinator` and second coordinator to `RiveBatchSurface`

**Files:**
- Modify: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt:196` (add CompositionLocal)
- Modify: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt:220` (add bgCoordinator)

**Step 1: Add the new CompositionLocal after the existing one (line 196)**

```kotlin
val LocalRiveBatchCoordinator = compositionLocalOf<RiveBatchCoordinator?> { null }
val LocalRiveBgBatchCoordinator = compositionLocalOf<RiveBatchCoordinator?> { null }
```

**Step 2: Create the background coordinator inside `RiveBatchSurface` (after line 220)**

```kotlin
val coordinator = remember { RiveBatchCoordinator() }       // existing (foreground)
val bgCoordinator = remember { RiveBatchCoordinator() }     // NEW (background)
```

**Step 3: Verify — file compiles**

Run: `cd /Users/peeyush.gulati/Desktop/Projects/Rive/rive-android && cat SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt | head -230`

**Step 4: Commit**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive/rive-android
git add SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat(batch): add background coordinator and CompositionLocal"
```

---

### Task 2: Add background TextureView + surface state to `RiveBatchSurface`

**Files:**
- Modify: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt:223` (add bgSurface state)
- Modify: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt:226-231` (add bgSurface cleanup)

**Step 1: Add background surface state (after the existing `surface` state)**

```kotlin
var surface by remember { mutableStateOf<RiveSurface?>(null) }       // existing (fg)
var bgSurface by remember { mutableStateOf<RiveSurface?>(null) }     // NEW (bg)
```

**Step 2: Add DisposableEffect for bgSurface cleanup (after existing DisposableEffect)**

```kotlin
// Cleanup: destroy the background RiveSurface when it changes or on dispose.
DisposableEffect(bgSurface) {
    val nonNullSurface = bgSurface ?: return@DisposableEffect onDispose { }
    onDispose {
        riveWorker.destroyRiveSurface(nonNullSurface)
    }
}
```

**Step 3: Commit**

```bash
git add SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat(batch): add background surface state and cleanup"
```

---

### Task 3: Add background render loop (Phase 1 + Phase 2)

**Files:**
- Modify: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt` (after existing Phase 2, ~line 334)

**Step 1: Add background Phase 1 render loop**

Duplicate the existing Phase 1 LaunchedEffect (lines 234-285) but targeting `bgCoordinator` and `bgSurface`:

```kotlin
// Background Phase 1 — render loop for background items.
LaunchedEffect(lifecycleOwner, bgSurface) {
    val currentSurface = bgSurface ?: return@LaunchedEffect
    RiveLog.d(BATCH_DRAW_TAG) { "Starting background batched render loop" }

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

            val count = bgCoordinator.fillBatchArrays()

            for (j in 0 until count) {
                val smRef = bgCoordinator.smHandleRefs[j] ?: continue
                riveWorker.advanceStateMachine(smRef, deltaTime)
            }

            try {
                riveWorker.drawBatch(
                    currentSurface,
                    count,
                    bgCoordinator.artboardHandles,
                    bgCoordinator.smHandles,
                    bgCoordinator.viewportXs,
                    bgCoordinator.viewportYs,
                    bgCoordinator.viewportWidths,
                    bgCoordinator.viewportHeights,
                    bgCoordinator.fits,
                    bgCoordinator.alignments,
                    bgCoordinator.scaleFactors,
                    bgCoordinator.clearColors,
                    surfaceClearColor,
                )
            } catch (e: Exception) {
                RiveLog.e(BATCH_DRAW_TAG) { "Background drawBatch failed: ${e.message}" }
            }
        }
    }
}
```

**Step 2: Add background Phase 2 stale frame flush**

```kotlin
// Background Phase 2 — flush stale frames after item removal.
LaunchedEffect(bgSurface, bgCoordinator) {
    val currentSurface = bgSurface ?: return@LaunchedEffect
    var previousCount = bgCoordinator.itemCount

    snapshotFlow { bgCoordinator.itemCount }
        .collect { newCount ->
            if (newCount < previousCount && newCount > 0) {
                RiveLog.d(BATCH_DRAW_TAG) {
                    "Background item removed ($previousCount→$newCount), flushing stale frame"
                }
                val count = bgCoordinator.fillBatchArrays()
                try {
                    riveWorker.drawBatch(
                        currentSurface,
                        count,
                        bgCoordinator.artboardHandles,
                        bgCoordinator.smHandles,
                        bgCoordinator.viewportXs,
                        bgCoordinator.viewportYs,
                        bgCoordinator.viewportWidths,
                        bgCoordinator.viewportHeights,
                        bgCoordinator.fits,
                        bgCoordinator.alignments,
                        bgCoordinator.scaleFactors,
                        bgCoordinator.clearColors,
                        surfaceClearColor,
                    )
                } catch (e: Exception) {
                    RiveLog.e(BATCH_DRAW_TAG) {
                        "Background correctness flush failed: ${e.message}"
                    }
                }
            }
            previousCount = newCount
        }
}
```

**Step 3: Commit**

```bash
git add SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat(batch): add background render loop (Phase 1 + Phase 2)"
```

---

### Task 4: Restructure Layout to 3 z-layers

**Files:**
- Modify: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt:336-405` (position tracking + Layout)

**Step 1: Update position tracking to set both coordinators' root positions**

Replace existing `onGloballyPositioned` block (lines 337-342):

```kotlin
val positionTrackingModifier = modifier
    .onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        coordinator.surfaceRootX = pos.x
        coordinator.surfaceRootY = pos.y
        bgCoordinator.surfaceRootX = pos.x
        bgCoordinator.surfaceRootY = pos.y
    }
```

**Step 2: Restructure the Layout content to 3 layers**

Replace the entire Layout block (lines 344-405) with:

```kotlin
Layout(
    modifier = positionTrackingModifier,
    content = {
        // Layer 1 (bottom): Background TextureView — decorative Rive items.
        val hasBgItems = bgCoordinator.itemCount > 0
        AndroidView(
            factory = { context: Context ->
                TextureView(context).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            newSurfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            RiveLog.d(BATCH_TAG) {
                                "Background batch surface available ($width x $height)"
                            }
                            bgSurface = riveWorker.createRiveSurface(newSurfaceTexture)
                        }

                        override fun onSurfaceTextureDestroyed(
                            destroyedSurfaceTexture: SurfaceTexture
                        ): Boolean {
                            RiveLog.d(BATCH_TAG) { "Background batch surface destroyed" }
                            bgSurface = null
                            return false
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            RiveLog.d(BATCH_TAG) {
                                "Background batch surface size changed ($width x $height)"
                            }
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            },
            update = { textureView ->
                textureView.visibility = if (hasBgItems) View.VISIBLE else View.INVISIBLE
            },
        )

        // Layer 2 (middle): Compose content — UI, popups, dim overlays.
        CompositionLocalProvider(
            LocalRiveBatchCoordinator provides coordinator,
            LocalRiveBgBatchCoordinator provides bgCoordinator,
        ) {
            content()
        }

        // Layer 3 (top): Foreground TextureView — overlay effects (confetti, etc).
        val hasFgItems = coordinator.itemCount > 0
        AndroidView(
            factory = { context: Context ->
                TextureView(context).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            newSurfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            RiveLog.d(BATCH_TAG) {
                                "Foreground batch surface available ($width x $height)"
                            }
                            surface = riveWorker.createRiveSurface(newSurfaceTexture)
                        }

                        override fun onSurfaceTextureDestroyed(
                            destroyedSurfaceTexture: SurfaceTexture
                        ): Boolean {
                            RiveLog.d(BATCH_TAG) { "Foreground batch surface destroyed" }
                            surface = null
                            return false
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            RiveLog.d(BATCH_TAG) {
                                "Foreground batch surface size changed ($width x $height)"
                            }
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            },
            update = { textureView ->
                textureView.visibility = if (hasFgItems) View.VISIBLE else View.INVISIBLE
            },
        )
    }
) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeables.forEach { it.placeRelative(0, 0) }
    }
}
```

**Step 3: Commit**

```bash
git add SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat(batch): restructure Layout to 3 z-layers (bg TV, content, fg TV)"
```

---

### Task 5: Add `background` param to `RiveBatchItem`

**Files:**
- Modify: `SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt:420-521` (RiveBatchItem)

**Step 1: Add `background` parameter to function signature**

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

**Step 2: Replace the coordinator selection (line 428-429)**

Replace:
```kotlin
val coordinator = LocalRiveBatchCoordinator.current
    ?: error("RiveBatchItem must be placed inside a RiveBatchSurface")
```

With:
```kotlin
val fgCoordinator = LocalRiveBatchCoordinator.current
val bgCoordinator = LocalRiveBgBatchCoordinator.current
val coordinator = if (background) {
    bgCoordinator ?: error("RiveBatchItem must be placed inside a RiveBatchSurface")
} else {
    fgCoordinator ?: error("RiveBatchItem must be placed inside a RiveBatchSurface")
}
```

**Step 3: Handle dynamic `background` changes — unregister from old coordinator on change**

Add a DisposableEffect keyed on `background` + `stateMachineHandle` to handle runtime switching. Replace the existing DisposableEffect (lines 446-450):

```kotlin
// Unregister from BOTH coordinators on dispose or when background/sm changes.
DisposableEffect(stateMachineHandle, background) {
    onDispose {
        fgCoordinator?.unregister(stateMachineHandle)
        bgCoordinator?.unregister(stateMachineHandle)
    }
}
```

This ensures that when `background` flips from `false` to `true`, the item is unregistered from the foreground coordinator (via dispose of old effect) and re-registered with the background coordinator (via `updatePosition` running in the new composition).

**Step 4: Commit**

```bash
git add SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt
git commit -m "feat(batch): add background param to RiveBatchItem with dynamic switching"
```

---

### Task 6: Add `background` param to CMP layer (common + Android + iOS)

**Files:**
- Modify: `core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveExpects.kt:29-45`
- Modify: `core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt:119-227`
- Modify: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt:59-117`

**Step 1: Add to common expect (RiveExpects.kt line 44, before closing paren)**

```kotlin
    batched: Boolean = true,
    background: Boolean = false,  // NEW
)
```

**Step 2: Add to Android actual (RiveExpects.android.kt line 132, before closing paren)**

```kotlin
    batched: Boolean,
    background: Boolean,  // NEW
```

**Step 3: Pass `background` to RiveBatchItem (RiveExpects.android.kt line 209-216)**

Replace:
```kotlin
    if (batched) {
        RiveBatchItem(
            file = riveFile,
            modifier = modifier,
            viewModelInstance = vmi,
            fit = riveFit,
        )
```

With:
```kotlin
    if (batched) {
        RiveBatchItem(
            file = riveFile,
            modifier = modifier,
            viewModelInstance = vmi,
            fit = riveFit,
            background = background,
        )
```

**Step 4: Add to iOS actual (RiveExpects.native.kt line 74, before closing paren)**

```kotlin
    batched: Boolean,
    background: Boolean,  // accepted but ignored — iOS UIKitView has no z-order issue
```

**Step 5: Commit**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
git add core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveExpects.kt
git add core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt
git add core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt
git commit -m "feat(cmp): add background param to RiveComponent (common + Android + iOS)"
```

---

### Task 7: Build AAR and copy to CMP project

**Step 1: Overlay SDK files to build directory**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive/rive-android
cp SDK/kotlin/src/main/kotlin/app/rive/RiveBatch.kt kotlin/src/main/kotlin/app/rive/
```

**Step 2: Build AAR**

```bash
rm -rf kotlin/.cxx
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :kotlin:assembleRelease
```

Expected: `BUILD SUCCESSFUL` with output at `kotlin/build/outputs/aar/kotlin-release.aar`

**Step 3: Copy AAR to all locations**

```bash
cp kotlin/build/outputs/aar/kotlin-release.aar /Users/peeyush.gulati/Desktop/Projects/Rive-IOS/core/rive/libs/rive-android-local.aar
cp kotlin/build/outputs/aar/kotlin-release.aar /Users/peeyush.gulati/Desktop/Projects/Rive-IOS/composeApp/libs/rive-android-local.aar
```

**Step 4: Commit AAR**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
git add core/rive/libs/rive-android-local.aar composeApp/libs/rive-android-local.aar
git commit -m "fix(android): rebuild AAR with dual-layer batch surface"
```

---

## Test Cases

### Manual Verification (on device)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 1 | **Background item behind dim overlay** | Set `background=true` on a Rive animation. Open popup with dim scrim overlay. | Rive animation is dimmed by the overlay, not showing through at full brightness. |
| 2 | **Foreground item on top (default, backward compat)** | Use `RiveComponent` without `background` param (defaults to `false`). | Rive renders on top of all composables — same as current behavior. |
| 3 | **Dynamic background switch** | Set `background = popupVisible`. Open/close popup. | Rive moves behind overlay when popup opens, back to foreground when popup closes. Transition is instant (one frame). |
| 4 | **Touch works on composables over background Rive** | `background=true` Rive + Button composable on top. Tap button. | Button receives tap. Rive does not intercept. |
| 5 | **Foreground Rive blocks touch (unchanged)** | Default `background=false` Rive + Button composable behind it. Tap on Rive area. | Rive area intercepts touch, button does NOT receive it. Same as today. |
| 6 | **Auto-hide: only background items** | Screen with only `background=true` items, zero foreground. | Foreground TextureView is `INVISIBLE`. Only background TextureView renders. GPU cost = 1 drawBatch. |
| 7 | **Auto-hide: only foreground items** | Screen with only default items, zero background. | Background TextureView is `INVISIBLE`. Only foreground TextureView renders. GPU cost = 1 drawBatch. |
| 8 | **Auto-hide: no items** | Screen with zero Rive items (e.g., settings). | Both TextureViews `INVISIBLE`. Zero GPU cost. |
| 9 | **Both layers active** | Screen with `background=true` AND default items. | Both TextureViews visible. Both render loops active. Both animations play correctly. |
| 10 | **Scroll sync** | Background Rive item in a LazyColumn. Scroll. | Item position tracks scroll correctly on the background TextureView. |
| 11 | **Tab switch (NavHost)** | Navigate between tabs with background Rive items. | No crash. Items register/unregister cleanly. Ghost frame behavior same as before (known limitation). |
| 12 | **`batched=false` unaffected** | `RiveComponent(batched=false, background=true)`. | `background` param is ignored. PoolableRiveView renders normally with its own TextureView inline. |
| 13 | **EGL stability on Adreno** | Run on an Adreno GPU device with both layers active. Navigate between screens. | No `EGL_BAD_ALLOC` crash. Both surfaces create/destroy cleanly. |
| 14 | **Language change / font reload** | Change language, trigger `preloadAll()` with new font configs. | Background and foreground items reload correctly. No stale state. |
| 15 | **iOS unaffected** | Run on iOS simulator/device. Use `background=true`. | Param accepted, no crash, no visual change (iOS ignores it). |

### Automated / Unit Testable

| # | Test | Type | What to assert |
|---|------|------|----------------|
| U1 | `RiveBatchCoordinator` register routes to correct instance | Unit | Register with bg coordinator → bg.itemCount=1, fg.itemCount=0 |
| U2 | `RiveBatchCoordinator` unregister cleans up | Unit | Unregister → itemCount=0, fillBatchArrays returns 0 |
| U3 | Dynamic switch unregisters from old coordinator | Unit | Register in fg, switch to bg → fg.itemCount=0, bg.itemCount=1 |
| U4 | `fillBatchArrays` returns correct data per coordinator | Unit | Register 3 items in bg, 2 in fg → bg.fillBatchArrays()=3, fg.fillBatchArrays()=2 |

---

## Verification Checklist (before shipping)

- [ ] AAR builds successfully
- [ ] CMP project compiles for Android
- [ ] CMP project compiles for iOS
- [ ] Test 1: Background Rive dimmed by popup overlay
- [ ] Test 2: Foreground Rive unchanged (backward compat)
- [ ] Test 3: Dynamic background switch works
- [ ] Test 4: Touch works on composables over background Rive
- [ ] Test 7: Auto-hide when only one layer active
- [ ] Test 11: Tab switch no crash
- [ ] Test 13: Adreno GPU no EGL crash
- [ ] Test 15: iOS no crash
