# Rive Surface Recycling + Tab Retention Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate Rive animation delays on both scroll recycling (~10ms target) and tab switching (0ms target) by using Compose's `AndroidView(onReset=...)` for LazyColumn recycling and a Rive-only retained composable for tab switching.

**Architecture:** Two complementary strategies. (1) A `PoolableRiveView` composable replaces the SDK's `Rive()` — uses `AndroidView(onReset=...)` so TextureViews are reused when LazyColumn recycles items. (2) A `RiveRetainer` composable keeps only the Rive-containing content alive across tab switches (not the whole tab). Non-Rive tab content rebuilds normally. Both are Android-only; iOS uses existing `UIKitView` path unchanged.

**Tech Stack:** Kotlin, Compose Multiplatform 1.10.0 (Compose UI 1.7+ — `onReset` is stable), Rive Android SDK (local AAR), `RiveWorker` draw API.

---

## Architecture Decision Record

### Why not just use `Rive()` composable?

The SDK's `Rive()` composable creates a `TextureView` internally via `AndroidView(factory=...)` without `onReset`. When a LazyColumn item scrolls off-screen or a tab switches, the TextureView is destroyed and recreated on re-entry (~100-250ms delay). We cannot inject `onReset` without modifying the SDK.

### Why not fork the SDK to add `onReset`?

Adding `onReset` to `Rive()` in the SDK is the cleanest long-term fix, but it requires:
- Maintaining a fork against upstream rive-app/rive-android updates
- Rebuilding the AAR for every SDK change
- The SDK's `Rive()` composable is ~250 lines with settled state, pointer handling, fit resizing — modifying it risks regressions

### Why bypass `Rive()` and use `RiveWorker` directly?

The `RiveWorker` (CommandQueue) API is stable and public:
- `createDefaultArtboard(fileHandle)` -> `ArtboardHandle`
- `createDefaultStateMachine(artboardHandle)` -> `StateMachineHandle`
- `bindViewModelInstance(smHandle, vmiHandle)`
- `advanceStateMachine(smHandle, deltaTime)`
- `draw(artboardHandle, smHandle, surface, fit, bgColor)`
- `createRiveSurface(surfaceTexture)` -> `RiveSurface`
- `destroyRiveSurface(surface)`

This is the same API that `Rive()` uses internally, and that `RiveBatch.kt` already uses. We control the TextureView lifecycle, enabling `onReset` pooling.

### Why retain only Rive content on tab switch, not the whole tab?

The user's tabs contain mostly non-Rive content (search bars, filters, images, text) that Compose rebuilds in <16ms. Only the Rive elements (~25-35 buttons/cards) are expensive. Retaining the whole tab wastes ~5-10MB for content that rebuilds instantly. Retaining only the Rive-containing LazyColumn costs ~1-2MB.

---

## Test Cases

### Manual on-device tests (no unit tests — Rive requires GPU)

**T1: Scroll recycling in LazyColumn**
- Open Home tab with contest list
- Scroll down past 10 items, scroll back up
- **Expected:** Rive buttons appear instantly on scroll back (no blank flash)
- **Pass criteria:** No visible delay on button appearance

**T2: Tab switch — Rive buttons**
- Open Home tab, observe Rive buttons
- Switch to Contests tab
- Switch back to Home tab
- **Expected:** Rive buttons appear instantly (no blank flash, no ~100ms delay)
- **Pass criteria:** Zero visible flicker on tab switch

**T3: Button interaction after tab switch**
- Switch to Contests tab and back to Home
- Tap a Rive button
- **Expected:** Trigger animation plays, "Loading..." text appears, reverts after 2s
- **Pass criteria:** Interaction works identically to first visit

**T4: Button interaction after scroll recycle**
- Scroll a button off-screen and back on
- Tap the button
- **Expected:** Trigger animation plays correctly
- **Pass criteria:** Pointer events are correctly bound to the new artboard

**T5: Memory — no leak on repeated tab switching**
- Switch tabs 20 times rapidly
- **Expected:** Memory usage stays stable (no growth per switch)
- **Pass criteria:** RSS Anon does not increase beyond ~2MB over baseline

**T6: Memory — no leak on scroll**
- Scroll up/down 50 times
- **Expected:** Memory stable
- **Pass criteria:** No growth per scroll cycle

**T7: Config change (rotation)**
- Rotate device while on Home tab
- **Expected:** App rebuilds correctly, Rive buttons render after normal first-load delay
- **Pass criteria:** No crash, correct rendering after rotation

**T8: App background/foreground**
- Open app, background it, foreground it
- **Expected:** Rive buttons resume rendering
- **Pass criteria:** No black surfaces, no crash

---

## Tasks

### Task 1: Create `PoolableRiveView` composable

This replaces the SDK's `Rive()` for Android. It manages its own TextureView with `AndroidView(onReset=...)` and drives the draw loop via `RiveWorker`.

**Files:**
- Create: `core/rive/src/androidMain/kotlin/com/arjun/core/rive/PoolableRiveView.kt`

**Step 1: Create the file with the composable**

```kotlin
package com.arjun.core.rive

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.Artboard
import app.rive.Fit
import app.rive.RiveFile
import app.rive.StateMachine
import app.rive.ViewModelInstance
import app.rive.core.RiveSurface
import app.rive.core.StateMachineHandle
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.filter
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A Rive view that supports TextureView recycling in LazyColumn via AndroidView's onReset.
 *
 * Unlike the SDK's Rive() composable which creates/destroys TextureViews on every
 * enter/leave composition, this composable reuses the TextureView when LazyColumn
 * recycles items. The surface stays alive, only the artboard/SM are swapped.
 *
 * Usage: Drop-in replacement for Rive() inside RiveComponent.
 */
@Composable
internal fun PoolableRiveView(
    file: RiveFile,
    modifier: Modifier = Modifier,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
    backgroundColor: Int = Color.Transparent.toArgb(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val riveWorker = file.riveWorker

    // Artboard + StateMachine — created fresh per item, cheap from cached file
    var artboard by remember { mutableStateOf<Artboard?>(null) }
    var stateMachine by remember { mutableStateOf<StateMachine?>(null) }
    var surface by remember { mutableStateOf<RiveSurface?>(null) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }
    var isSettled by remember { mutableStateOf(false) }

    // Create artboard + SM on first composition or when file changes
    LaunchedEffect(file) {
        val ab = Artboard.fromFile(file)
        val sm = StateMachine.fromArtboard(ab)
        artboard = ab
        stateMachine = sm
        // Initial advance to exit "design" state
        sm.advance(0.nanoseconds)
        isSettled = false
    }

    // Bind VMI to state machine
    LaunchedEffect(stateMachine, viewModelInstance) {
        val sm = stateMachine ?: return@LaunchedEffect
        val vmi = viewModelInstance ?: return@LaunchedEffect

        riveWorker.bindViewModelInstance(
            sm.stateMachineHandle,
            vmi.instanceHandle
        )
        isSettled = false

        // Unsettle when VMI properties change
        vmi.dirtyFlow.collect { isSettled = false }
    }

    // Listen for settled events
    LaunchedEffect(stateMachine) {
        val smHandle = stateMachine?.stateMachineHandle ?: return@LaunchedEffect
        riveWorker.settledFlow
            .filter { it.handle == smHandle.handle }
            .collect { isSettled = true }
    }

    // Unsettle on fit/bg change
    LaunchedEffect(fit, backgroundColor) {
        isSettled = false
    }

    // Clean up surface
    DisposableEffect(surface) {
        val s = surface ?: return@DisposableEffect onDispose {}
        onDispose { riveWorker.destroyRiveSurface(s) }
    }

    // Clean up artboard + SM when composable is permanently disposed
    DisposableEffect(Unit) {
        onDispose {
            stateMachine?.close()
            artboard?.close()
        }
    }

    // Draw loop
    LaunchedEffect(
        lifecycleOwner, surface, artboard, stateMachine,
        viewModelInstance, fit, backgroundColor
    ) {
        val ab = artboard ?: return@LaunchedEffect
        val sm = stateMachine ?: return@LaunchedEffect
        val s = surface ?: return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastFrameTime = 0.nanoseconds
            while (isActive) {
                val deltaTime = withFrameNanos { frameTimeNs ->
                    val frameTime = frameTimeNs.nanoseconds
                    (if (lastFrameTime == 0.nanoseconds) 0.nanoseconds
                    else frameTime - lastFrameTime).also {
                        lastFrameTime = frameTime
                    }
                }

                if (isSettled) continue

                riveWorker.advanceStateMachine(sm.stateMachineHandle, deltaTime)
                riveWorker.draw(
                    ab.artboardHandle,
                    sm.stateMachineHandle,
                    s, fit, backgroundColor
                )
            }
        }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        st: SurfaceTexture, width: Int, height: Int
                    ) {
                        surface = riveWorker.createRiveSurface(st)
                        surfaceWidth = width
                        surfaceHeight = height
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        surface = null
                        // Return false — we manage the SurfaceTexture lifecycle
                        return false
                    }

                    override fun onSurfaceTextureSizeChanged(
                        st: SurfaceTexture, width: Int, height: Int
                    ) {
                        surfaceWidth = width
                        surfaceHeight = height
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier,
        onReset = { textureView ->
            // Called when LazyColumn recycles this view for a new item.
            // TextureView and surface stay alive — only artboard/SM change.
            // The old artboard/SM are cleaned up by DisposableEffect(Unit).
            // New ones will be created by LaunchedEffect(file).
            isSettled = false
        },
        onRelease = { textureView ->
            // Called when the view is permanently discarded.
            // Surface cleanup handled by DisposableEffect(surface).
        }
    )
}
```

**Step 2: Commit**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
git add core/rive/src/androidMain/kotlin/com/arjun/core/rive/PoolableRiveView.kt
git commit -m "feat: add PoolableRiveView with AndroidView onReset for TextureView recycling"
```

---

### Task 2: Create `RiveRetainer` composable for tab switching

This keeps Rive-containing content alive across tab switches. Unlike TabRetainer, it's designed to wrap only the Rive-heavy portion of each tab.

**Files:**
- Create: `core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveRetainer.kt`

**Step 1: Create the composable**

```kotlin
package com.arjun.core.rive

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Retains Rive-heavy content across tab switches without caching the entire tab.
 *
 * Only the content passed to [tabs] is retained. Place non-Rive tab content
 * (search bars, filters, headers) outside this composable so it rebuilds
 * normally on tab switch.
 *
 * Hidden tabs use graphicsLayer { alpha = 0f } — no GPU draw cost.
 * Rive animations in hidden tabs are paused via lifecycle (the draw loop
 * only runs when lifecycle is RESUMED and the composable is active).
 *
 * @param activeTab Currently selected tab index.
 * @param maxRetained Max tabs to keep alive (including active). Default 3.
 * @param tabs List of tab content composables, each receiving isActive flag.
 */
@Composable
fun RiveRetainer(
    activeTab: Int,
    maxRetained: Int = 3,
    tabs: List<@Composable (isActive: Boolean) -> Unit>
) {
    val retainedTabs = remember { mutableStateListOf<Int>() }

    // Update LRU: move active tab to front
    if (retainedTabs.firstOrNull() != activeTab) {
        retainedTabs.remove(activeTab)
        retainedTabs.add(0, activeTab)

        // Evict beyond maxRetained
        while (retainedTabs.size > maxRetained) {
            retainedTabs.removeLast()
        }
    }

    retainedTabs.forEach { tabIndex ->
        val isActive = tabIndex == activeTab
        key(tabIndex) {
            Box(
                modifier = if (isActive) Modifier
                else Modifier.graphicsLayer { alpha = 0f }
            ) {
                tabs[tabIndex](isActive)
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveRetainer.kt
git commit -m "feat: add RiveRetainer for tab-level Rive content retention"
```

---

### Task 3: Wire `PoolableRiveView` into `RiveComponent` (Android)

Replace the SDK's `Rive()` call with `PoolableRiveView` inside the Android actual.

**Files:**
- Modify: `core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt`

**Step 1: Replace `Rive()` with `PoolableRiveView()`**

Change lines 146-151:

```kotlin
// BEFORE:
Rive(
    file = riveFile,
    modifier = riveModifier,
    viewModelInstance = vmi,
    fit = Fit.Contain(Alignment.Center)
)

// AFTER:
PoolableRiveView(
    file = riveFile,
    modifier = riveModifier,
    viewModelInstance = vmi,
    fit = Fit.Contain(Alignment.Center)
)
```

Remove the unused `import app.rive.Rive` import.

**Step 2: Commit**

```bash
git add core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt
git commit -m "feat: use PoolableRiveView in RiveComponent for TextureView recycling"
```

---

### Task 4: Wire `RiveRetainer` into `App.kt`

Use `RiveRetainer` to wrap only the Rive-containing content in each tab.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/App.kt`

**Step 1: Replace `when(selectedTab)` with `RiveRetainer`**

```kotlin
Box(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
) {
    RiveRetainer(
        activeTab = selectedTab,
        maxRetained = 2,
        tabs = listOf(
            { _ -> ContestLargeCards() },
            { _ -> ContestLargeCards() },
        )
    )
}
```

Add the import: `import com.arjun.core.rive.RiveRetainer`

**Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/App.kt
git commit -m "feat: use RiveRetainer in App.kt for instant tab switching"
```

---

### Task 5: On-device testing

**Step 1: Build**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
./gradlew :composeApp:assembleDebug
```

**Step 2: Install and run on Xiaomi device**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

**Step 3: Execute test cases T1-T8 from the Test Cases section above**

**Step 4: Fix any issues, commit fixes**

---

### Task 6: Write architecture documentation

**Files:**
- Create: `docs/RIVE-SURFACE-RECYCLING.md`

**Content:** Architecture doc covering the problem, solution, memory impact, key files, when to use what, and limitations. See the architecture section at the top of this plan for the full content.

**Step 1: Write and commit**

```bash
git add docs/RIVE-SURFACE-RECYCLING.md
git commit -m "docs: add Rive surface recycling architecture documentation"
```

---

## Summary of all changes

| File | Action | Purpose |
|------|--------|---------|
| `core/rive/src/androidMain/.../PoolableRiveView.kt` | Create | TextureView recycling via onReset |
| `core/rive/src/commonMain/.../RiveRetainer.kt` | Create | Tab-level Rive content retention |
| `core/rive/src/androidMain/.../RiveExpects.android.kt` | Modify | Use PoolableRiveView instead of Rive() |
| `composeApp/src/commonMain/.../App.kt` | Modify | Use RiveRetainer for tab switching |
| `docs/RIVE-SURFACE-RECYCLING.md` | Create | Architecture documentation |
