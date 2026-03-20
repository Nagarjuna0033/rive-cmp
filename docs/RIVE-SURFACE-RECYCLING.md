# Rive Surface Recycling Architecture

## Problem

Rive animations in Compose create a `TextureView` + EGL surface per instance. When items scroll off-screen in a `LazyColumn` or tabs switch, the `TextureView` is destroyed and recreated on re-entry. The EGL surface creation takes ~100-250ms, causing a visible blank flash.

**Two scenarios:**
1. **Scroll recycling** — LazyColumn items leave/re-enter composition
2. **Tab switching** — Bottom nav tabs destroy and rebuild their content

---

## Solution

Two complementary composables, each targeting one scenario:

### 1. `PoolableRiveView` (scroll recycling)

**File:** `core/rive/src/androidMain/kotlin/com/arjun/core/rive/PoolableRiveView.kt`

Replaces the Rive SDK's `Rive()` composable. Uses `AndroidView(onReset=..., onRelease=...)` so the `TextureView` is reused when LazyColumn recycles items instead of being destroyed.

**How it works:**
- `factory` creates a `TextureView` with a `SurfaceTextureListener`
- `onSurfaceTextureDestroyed` returns `false` — keeps the `SurfaceTexture` alive
- `onReset` re-wraps the existing `SurfaceTexture` into a new `RiveSurface`
- `onRelease` destroys the `RiveSurface` and releases the `SurfaceTexture`
- Creates its own artboard + state machine via `RiveWorker` (the SDK's command queue API)
- Runs its own draw loop: `advanceStateMachine` + `draw` each frame via `withFrameNanos`
- Draw loop only runs when lifecycle is `RESUMED` (via `repeatOnLifecycle`)
- Skips drawing when the state machine has settled (idle optimization)

**Performance:**
- Scroll recycle: ~10ms (artboard creation only, TextureView reused)
- vs. SDK's `Rive()`: ~100-250ms (full TextureView + EGL surface creation)

### 2. `RiveRetainer` (tab switching)

**File:** `core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveRetainer.kt`

Keeps Rive-containing content alive across tab switches without caching entire tabs.

**How it works:**
- Wraps only the Rive-heavy portion of each tab (not the whole tab)
- Active tab renders normally
- Hidden tabs use `graphicsLayer { alpha = 0f }` — zero GPU draw cost
- LRU eviction beyond `maxRetained` frees old tab resources
- Rive animations pause automatically on hidden tabs (lifecycle-aware draw loop)

**Performance:**
- Tab switch: 0ms (composable never leaves composition)
- Extra memory: ~1-2MB per retained tab (only visible LazyColumn items)

---

## Caching Architecture

Four layers of caching, from top (disk) to bottom (GPU):

```
Layer 1: RiveFile cache (pre-existing)
├── Location: AndroidRiveFileManager.loadedFiles (ConcurrentHashMap)
├── Loaded once at app start via RiveProvider → preloadAll()
├── Stays in memory for entire app lifetime
└── Cleaned up: DisposableEffect in RiveProvider → fileManager.clearAll()

Layer 2: ViewModelInstance cache (pre-existing)
├── Location: RiveRuntime.instanceCache (ConcurrentHashMap)
├── Key format: "$resourceName-$instanceKey" (e.g. "contest_button-contest_42")
├── Created on first use via computeIfAbsent (thread-safe)
└── Cleaned up: DisposableEffect in RiveProvider → runtime.clear()

Layer 3: TextureView recycling (NEW — PoolableRiveView)
├── Mechanism: AndroidView(onReset=...) tells Compose to REUSE the TextureView
├── The GPU surface (SurfaceTexture) stays alive across recycles
├── Only the artboard + state machine are recreated per recycle (~10ms)
├── Old RiveSurface wrapper destroyed before new one is created
└── Cleaned up: onRelease → destroyRiveSurface() + surfaceTexture.release()

Layer 4: Tab retention (NEW — RiveRetainer)
├── Mechanism: Keeps composables alive via graphicsLayer { alpha = 0f }
├── LRU eviction — only maxRetained tabs kept in composition
├── Hidden tabs: zero GPU draw cost, zero CPU cost (draw loop pauses)
└── Cleaned up: Evicted tabs leave composition → DisposableEffect fires
```

### How cleanup flows

**Scroll off-screen (LazyColumn):**
```
Item scrolls out
  → AndroidView triggers onReset (NOT onRelease)
  → TextureView REUSED (not destroyed)
  → Old artboard/SM deleted via DisposableEffect
  → New artboard/SM created for the new item (~10ms)
  → Old RiveSurface destroyed, new one wraps existing SurfaceTexture
  → Draw loop restarts
```

**Tab switch (RiveRetainer):**
```
Tab changes
  → Old tab's Box gets Modifier.graphicsLayer { alpha = 0f }
  → Draw loop continues but skips drawing (isSettled = true)
  → No cleanup — composable stays alive in composition
  → If tab count > maxRetained → oldest tab evicted from composition
    → DisposableEffect fires → artboard/SM/surface cleaned up
```

**App exit / Activity destroy:**
```
Activity destroyed
  → RiveProvider's DisposableEffect fires
  → runtime.clear() closes all ViewModelInstances
  → fileManager.clearAll() releases all RiveFiles
  → PoolableRiveView's onRelease fires for each view
    → destroyRiveSurface() + surfaceTexture.release()
```

---

## Usage Guide

### Scroll recycling (automatic — no code changes needed)

`RiveComponent` now internally uses `PoolableRiveView` instead of the SDK's `Rive()`. Any `RiveComponent` inside a `LazyColumn` gets TextureView recycling automatically.

```kotlin
// This just works — PoolableRiveView is used internally by RiveComponent
LazyColumn {
    items(contests) { contest ->
        RiveComponent(
            resourceName = "contest_button",
            instanceKey = "contest_${contest.id}",
            viewModelName = "button",
            config = RiveItemConfig(
                strings = mapOf("label" to contest.name)
            )
        )
    }
}
```

### Tab retention (opt-in — wrap Rive-heavy content)

Wrap **only** the Rive-heavy content in `RiveRetainer`. Keep non-Rive UI (headers, search bars, filters) outside — Compose rebuilds those in <16ms.

```kotlin
Scaffold(bottomBar = { /* nav bar */ }) { padding ->
    // Non-Rive content like headers goes here (rebuilds normally on switch)
    TopBar(selectedTab)

    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        RiveRetainer(
            activeTab = selectedTab,
            maxRetained = 3,   // keep up to 3 tabs alive
            tabs = listOf(
                { _ -> HomeTabRiveContent() },
                { _ -> ContestsTabRiveContent() },
                { _ -> ProfileTabRiveContent() },
            )
        )
    }
}
```

**Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `activeTab` | `Int` | required | Currently selected tab index (0-based). Must be in `tabs.indices`. |
| `maxRetained` | `Int` | `3` | Max tabs to keep alive including active. Clamped to minimum 1. |
| `tabs` | `List<@Composable (Boolean) -> Unit>` | required | Composable lambdas per tab. Each receives `isActive` boolean. |

**Tuning `maxRetained`:**
- `2` — Good for 2-tab apps (both tabs always alive)
- `3` — Good for 3-5 tab apps (most recently visited 3 stay alive)
- Higher values use more memory (~1-2MB per retained tab)

---

## Monitoring Guide

### 1. Logcat — watch for cleanup failures

```bash
adb logcat -s "Rive/PoolableView"
```

Any `W/Rive/PoolableView` messages indicate cleanup failures (failed to delete artboard/SM or destroy surface). These suggest potential native memory leaks that need investigation.

**Healthy output:** No messages from this tag during normal operation.

**Unhealthy output:**
```
W/Rive/PoolableView: Failed to delete state machine
W/Rive/PoolableView: Failed to destroy RiveSurface
```

### 2. GPU rendering — verify no blank flash

Enable on-device GPU profiling:
```bash
adb shell setprop debug.hwui.profile true
```

Or use **Settings > Developer Options > GPU Rendering Profile > On screen as bars**.

| Scenario | Before (Rive()) | After (PoolableRiveView) |
|----------|-----------------|--------------------------|
| Item scrolls into view (first time) | Tall green bar ~100-250ms | Same (first creation) |
| Item scrolls back into view (recycle) | Tall green bar ~100-250ms | Short bar ~10ms |
| Tab switch back | Tall green bar ~100-250ms | No bar (0ms, retained) |

### 3. Memory monitoring — verify no leaks

**Quick check:**
```bash
# Baseline reading
adb shell dumpsys meminfo com.arjun.rivecmptesting | grep "TOTAL"

# Do 50 scroll cycles or 20 tab switches

# Post-test reading
adb shell dumpsys meminfo com.arjun.rivecmptesting | grep "TOTAL"

# Compare TOTAL PSS — should be within 1-2MB of baseline
```

**Continuous monitoring:**
```bash
# Watch every 2 seconds while testing
watch -n 2 "adb shell dumpsys meminfo com.arjun.rivecmptesting | grep 'TOTAL'"
```

**Android Studio Profiler:**
- **Memory tab:** Watch native allocations — should stay flat after initial load
- **CPU tab:** When idle (no animation playing), CPU should be near 0% (settled optimization skips draw calls)
- **GPU tab:** Hidden tabs should contribute zero draw calls

### 4. Frame timing — verify no jank

```bash
# Reset stats
adb shell dumpsys gfxinfo com.arjun.rivecmptesting reset

# Do tab switches / scrolling

# Check janky frames
adb shell dumpsys gfxinfo com.arjun.rivecmptesting | grep "Janky"
```

**Target:** 0 janky frames on tab switch, <5% janky frames during scroll.

---

## Test Cases

### Functional Tests

| # | Category | Test | Steps | Expected Result | Pass Criteria |
|---|----------|------|-------|-----------------|---------------|
| T1 | Scroll | Recycle visual | Open Home tab, scroll down past 10 items, scroll back up | Rive buttons appear instantly on scroll back | No visible blank flash on any button |
| T2 | Tab | Switch visual | Open Home, switch to Contests, switch back to Home | Rive buttons appear instantly | Zero visible flicker on tab switch |
| T3 | Tab | Interaction after switch | Switch to Contests and back to Home, tap a Rive button | Trigger animation plays, text changes | Interaction works identically to first visit |
| T4 | Scroll | Interaction after recycle | Scroll a button off-screen and back on, tap it | Trigger animation plays correctly | Pointer events bound to recycled view |
| T5 | Tab | Rapid switching | Switch tabs 10x rapidly (~2 taps/sec) | No crash, correct tab content renders each time | App stays responsive, no ANR |
| T6 | Scroll | Deep scroll + tab switch | Scroll to bottom (50+ items), switch tab, switch back | Buttons render (scroll position may reset) | No crash, all buttons visible |
| T7 | Scroll | First appearance | Cold start, open Home tab | Rive buttons appear after initial load | All buttons render within 1-2 seconds of tab load |

### Memory Tests

| # | Test | Steps | Measurement | Pass Criteria |
|---|------|-------|-------------|---------------|
| T8 | Scroll leak | Scroll up/down 50 times, check `dumpsys meminfo` | Compare TOTAL PSS before and after | Growth < 1MB |
| T9 | Tab switch leak | Switch tabs 20 times rapidly, check `dumpsys meminfo` | Compare TOTAL PSS before and after | Growth < 2MB |
| T10 | LRU eviction | With `maxRetained=2`: visit tabs 0, 1, 2, then back to 0 | Tab 0 should rebuild (it was evicted when tab 2 was visited) | Tab 0 shows brief creation then renders. Memory does not grow. |
| T11 | Long session | Use app for 5 minutes: scroll, switch tabs, interact | Monitor memory continuously | No linear growth trend |

### Lifecycle Tests

| # | Test | Steps | Expected Result | Pass Criteria |
|---|------|-------|-----------------|---------------|
| T12 | Background/foreground | Open app, press Home, reopen app | Rive buttons resume rendering | No black surfaces, no crash |
| T13 | Rotation | Rotate device while on Home tab | App rebuilds, Rive buttons render after normal first-load delay | No crash, correct rendering |
| T14 | Back navigation | Navigate to a detail screen, press back | Rive buttons render on return | No blank flash |
| T15 | Process death | Open app, kill process via Android Studio, reopen | App cold-starts normally | No crash, normal startup |

### Performance Tests

| # | Test | Tool | Target |
|---|------|------|--------|
| T16 | Scroll render time | GPU profiler (on-screen bars) | Green bar < 16ms on recycled items |
| T17 | Idle CPU | Android Studio CPU profiler | ~0% CPU when all animations settled |
| T18 | Tab switch frame drops | `adb shell dumpsys gfxinfo` | 0 janky frames on tab switch |
| T19 | Idle GPU | Android Studio GPU profiler | Zero draw calls from hidden tabs |

### Edge Case Tests

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| T20 | Empty list | Show a tab with 0 Rive items | No crash, empty content renders |
| T21 | Single item | List with 1 Rive item, scroll up/down | Item renders correctly, no crash |
| T22 | Same .riv, many instances | 30+ buttons all using `contest_button.riv` | All render correctly, RiveFile loaded only once |
| T23 | Different .riv files | Mix of button.riv, card.riv, fab.riv in same list | Each renders with correct animation |
| T24 | Config change mid-animation | Change a Rive input while animation is playing | Animation updates to reflect new input |

---

## Key Files

| File | Location | Purpose |
|------|----------|---------|
| `PoolableRiveView.kt` | `core/rive/src/androidMain/.../` | TextureView recycling via AndroidView `onReset` (Android only) |
| `RiveRetainer.kt` | `core/rive/src/commonMain/.../` | Tab-level Rive content retention with LRU eviction (cross-platform) |
| `RiveExpects.android.kt` | `core/rive/src/androidMain/.../` | Android `RiveComponent` actual — calls `PoolableRiveView` internally |
| `RiveExpects.native.kt` | `core/rive/src/nativeMain/.../` | iOS `RiveComponent` actual — unchanged, uses `UIKitView` |
| `RiveExpects.kt` | `core/rive/src/commonMain/.../` | Expect declarations for `RiveProvider` and `RiveComponent` |
| `RiveSessionManager.kt` | `core/rive/src/androidMain/.../` | `RiveRuntime` with VMI cache (`instanceCache`) |
| `RiveFileManager.kt` | `core/rive/src/androidMain/.../` | `AndroidRiveFileManager` with file/font/image/audio caches |
| `App.kt` | `composeApp/src/commonMain/.../` | Main app — uses `RiveRetainer` for tab switching |

---

## Architecture Decision: Why Bypass the SDK's `Rive()` Composable?

The SDK's `Rive()` composable (~250 lines) creates an `AndroidView` **without** `onReset`. We cannot inject recycling behavior without modifying the SDK source. Forking the SDK to add `onReset` would require:
- Maintaining a fork against upstream `rive-app/rive-android` updates
- Rebuilding the AAR for every SDK change
- Risk of regressions in the SDK's settled state handling, pointer input, and fit resizing

Instead, `PoolableRiveView` uses the same `RiveWorker` command queue API that `Rive()` uses internally:
- `createDefaultArtboard(fileHandle)` / `deleteArtboard(handle)`
- `createDefaultStateMachine(artboardHandle)` / `deleteStateMachine(handle)`
- `advanceStateMachine(handle, deltaTime)`
- `draw(artboardHandle, smHandle, surface, fit, bgColor)`
- `createRiveSurface(surfaceTexture)` / `destroyRiveSurface(surface)`
- `bindViewModelInstance(smHandle, vmiHandle)`
- `settledFlow` for idle detection

This gives us full control over the `TextureView` lifecycle while using the SDK's stable public API.

---

## Known Limitations

1. **Animation restarts on scroll recycle** — When a Rive button scrolls off and back on, its animation starts from the beginning (new artboard + state machine are created). This is acceptable for buttons and cards where the animation is short or state-driven.

2. **No pointer/touch forwarding** — `PoolableRiveView` does not forward touch events to the Rive state machine canvas. Rive button interactions that rely on Compose `onClick` (via `RiveItemConfig` triggers) work fine. If you need direct tap-on-canvas interaction inside the Rive artboard, touch forwarding must be added to `PoolableRiveView`.

3. **Android only** — `PoolableRiveView` is Android-specific. iOS continues using `UIKitView` with its own lifecycle semantics. `RiveRetainer` is cross-platform (in commonMain) and works on both.

4. **Hidden tab accessibility** — `RiveRetainer` hides tabs with `graphicsLayer { alpha = 0f }` which makes them visually invisible but does NOT remove them from the accessibility/semantics tree. Screen readers may announce content from hidden tabs. If this is an issue, add `Modifier.clearAndSetSemantics {}` to hidden tab wrappers.

5. **`maxRetained` tuning** — Each retained tab costs ~1-2MB for the TextureViews of visible LazyColumn items. Start with `maxRetained = 2` for 2-tab apps or `3` for 3-5 tab apps. Monitor memory with `dumpsys meminfo` to find the right value for your app.

6. **Settled optimization caveat** — The draw loop skips frames when `isSettled = true`. If a Rive animation has an infinite loop that never emits a settled event, the draw loop runs every frame indefinitely. This is correct behavior (the animation needs to draw) but costs GPU/battery. Design Rive files to settle when idle.
