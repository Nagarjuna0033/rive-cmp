# Batched Rendering Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace per-item TextureViews with a single global `RiveBatchSurface` inside `RiveProvider`, making all Rive elements render instantly with native pointer interaction.

**Architecture:** `RiveProvider` wraps its `content()` in `RiveBatchSurface` (1 TextureView). `RiveComponent` uses `RiveBatchItem` instead of `PoolableRiveView` by default, with `batched = false` escape hatch for heavy animations.

**Tech Stack:** Rive Android SDK (`RiveBatchSurface`, `RiveBatchItem`, `RiveBatchCoordinator`), Compose Multiplatform, Kotlin

---

### Task 1: Add `batched` parameter to common expect declaration

**Files:**
- Modify: `core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveExpects.kt`

**Step 1: Add the `batched` parameter to `RiveComponent` expect function**

Add `batched: Boolean = true` as the last parameter:

```kotlin
@Composable
expect fun RiveComponent(
    resourceName: String,
    instanceKey: String,
    viewModelName: String,
    modifier: Modifier = Modifier,
    config: RiveItemConfig = RiveItemConfig(),
    eventCallback: RiveEventCallback? = null,
    onControllerReady: ((RiveController) -> Unit)? = null,
    alignment: RiveAlignment = RiveAlignment.CENTER,
    autoPlay: Boolean = true,
    artboardName: String? = null,
    fit: RiveFit = RiveFit.CONTAIN,
    stateMachineName: String? = null,
    batched: Boolean = true,  // NEW
)
```

**Step 2: Verify both actuals need updating**

Both `RiveExpects.android.kt` and `RiveExpects.native.kt` must add the parameter to their `actual fun RiveComponent` signatures.

**Step 3: Commit**

```bash
git add core/rive/src/commonMain/kotlin/com/arjun/core/rive/RiveExpects.kt
git commit -m "feat: add batched parameter to RiveComponent expect declaration"
```

---

### Task 2: Update iOS actual to accept `batched` parameter (no-op)

**Files:**
- Modify: `core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt`

**Step 1: Add `batched` parameter to the actual function signature**

Add `batched: Boolean` as the last parameter in the `actual fun RiveComponent` signature. Do NOT use it — iOS uses `UIKitView` and doesn't support batching.

```kotlin
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun RiveComponent(
    resourceName: String,
    instanceKey: String,
    viewModelName: String,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?,
    alignment: RiveAlignment,
    autoPlay: Boolean,
    artboardName: String?,
    fit: RiveFit,
    stateMachineName: String?,
    batched: Boolean,  // NEW — ignored on iOS
) {
    // ... existing iOS implementation unchanged ...
}
```

**Step 2: Commit**

```bash
git add core/rive/src/nativeMain/kotlin/com/arjun/core/rive/RiveExpects.native.kt
git commit -m "feat: add batched parameter to iOS RiveComponent actual (no-op)"
```

---

### Task 3: Wrap RiveProvider content in RiveBatchSurface

**Files:**
- Modify: `core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt`

**Step 1: Add imports**

Add these imports at the top of the file:

```kotlin
import app.rive.RiveBatchSurface
import app.rive.LocalRiveBatchCoordinator
```

**Step 2: Wrap content in RiveBatchSurface**

In the `RiveProvider` actual function, wrap the `content()` call inside `RiveBatchSurface`. The batch surface must be INSIDE the `CompositionLocalProvider` (so it can access `LocalRiveFileManager` and `LocalRiveRuntime`), and it needs the `riveWorker` reference.

Find this block in `RiveProvider`:

```kotlin
        when (val state = loadState) {
            is RiveLoadState.Loading -> loadingContent()
            is RiveLoadState.Error -> errorContent(state.message)
            is RiveLoadState.Success -> {
                RivePerfLogger.log("TOTAL provider setup", totalStart)
                content()
            }
            is RiveLoadState.Idle -> loadingContent()
        }
```

Replace the `content()` call with:

```kotlin
            is RiveLoadState.Success -> {
                RivePerfLogger.log("TOTAL provider setup", totalStart)
                RiveBatchSurface(
                    riveWorker = riveWorker,
                ) {
                    content()
                }
            }
```

**Important:** `riveWorker` is already available in scope (line 51 of the current file).

**Step 3: Commit**

```bash
git add core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt
git commit -m "feat: wrap RiveProvider content in RiveBatchSurface"
```

---

### Task 4: Replace PoolableRiveView with RiveBatchItem in RiveComponent

**Files:**
- Modify: `core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt`

**Step 1: Add imports**

```kotlin
import app.rive.RiveBatchItem
```

**Step 2: Add `batched` parameter to the actual function signature**

```kotlin
@Composable
actual fun RiveComponent(
    resourceName: String,
    instanceKey: String,
    viewModelName: String,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?,
    alignment: RiveAlignment,
    autoPlay: Boolean,
    artboardName: String?,
    fit: RiveFit,
    stateMachineName: String?,
    batched: Boolean,  // NEW
) {
```

**Step 3: Replace the PoolableRiveView call with a branch**

Find this block (currently around line 208):

```kotlin
    PoolableRiveView(
        file = riveFile,
        modifier = modifier,
        viewModelInstance = vmi,
        fit = Fit.Contain(),
    )
```

Replace with:

```kotlin
    if (batched) {
        RiveBatchItem(
            file = riveFile,
            modifier = modifier,
            viewModelInstance = vmi,
            fit = Fit.Contain(),
        )
    } else {
        PoolableRiveView(
            file = riveFile,
            modifier = modifier,
            viewModelInstance = vmi,
            fit = Fit.Contain(),
        )
    }
```

**Step 4: Commit**

```bash
git add core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt
git commit -m "feat: use RiveBatchItem for batched rendering, keep PoolableRiveView as fallback"
```

---

### Task 5: Remove the PoolableRiveView render loop (cleanup)

The `PoolableRiveView` render loop (advance + draw) is now only needed for the `batched = false` fallback. For the batched path, the render loop runs inside `RiveBatchSurface`. No changes needed here — `PoolableRiveView` already works as a standalone renderer.

**Action:** No code changes. Verify `PoolableRiveView.kt` is still valid as a fallback. Read the file and confirm it compiles independently.

**Step 1: Commit (no-op, just verify)**

Nothing to commit.

---

### Task 6: Verify RiveRetainer still works with batched rendering

**Context:** `RiveRetainer` keeps hidden tabs in composition with `graphicsLayer { alpha = 0f }`. With batched rendering, hidden tab items are still registered with the `RiveBatchCoordinator`. The `RiveBatchSurface` render loop will still advance their state machines and draw them — but they're at off-screen positions (alpha=0 hides the Compose layout, but the batch surface still draws at their coordinates).

**Issue:** Hidden tab items will be drawn on the batch surface even though they're invisible. This wastes GPU.

**Fix:** Items in hidden tabs need to unregister from the coordinator. `RiveBatchItem` uses `DisposableEffect` to unregister on dispose, but hidden tabs are NOT disposed (that's the point of `RiveRetainer`).

**Step 1: Check if this is actually a problem**

Hidden tab items are positioned behind the active tab (z-order). The batch surface draws them at their coordinates, but since the active tab's Compose content sits on top, the user won't see them. The only cost is GPU draw calls for invisible items.

For ~8-10 hidden items at 150x75dp, this is negligible. **Skip fixing this for now.** If profiling shows GPU waste, revisit by passing `isActive` from `RiveRetainer` through to `RiveComponent`.

**Step 2: Document decision**

Add a comment in `RiveExpects.android.kt` above the `RiveBatchItem` call:

```kotlin
    // NOTE: Hidden tab items (via RiveRetainer) remain registered with the batch coordinator.
    // They still draw on the shared surface but are visually hidden behind the active tab.
    // GPU cost is negligible for small items. If profiling shows waste, pass RiveRetainer's
    // isActive flag to skip registration for hidden items.
```

**Step 3: Commit**

```bash
git add core/rive/src/androidMain/kotlin/com/arjun/core/rive/RiveExpects.android.kt
git commit -m "docs: note hidden tab batch behavior for future optimization"
```

---

### Task 7: Build and test

**Step 1: Build the project**

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive-IOS
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

**Step 2: If build fails due to missing SDK APIs**

If `RiveBatchSurface` or `RiveBatchItem` are not found in the current AAR:
- Check the AAR version being used (look for rive dependency in `build.gradle.kts`)
- These APIs are in the SDK fork at `Nagarjuna0033/rive-android` — verify the AAR was built from a commit that includes `RiveBatch.kt`
- If the AAR doesn't include batching APIs, a new AAR must be built from the fork

**Step 3: If build fails due to `rememberArtboard` / `rememberStateMachine` not found**

These are internal SDK composables used by `RiveBatchItem`. They must be compiled into the AAR. If not:
- Check if these functions exist in the SDK source under a different package
- They may need to be added to the SDK fork and AAR rebuilt

**Step 4: Install and test on device**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Test checklist:
- [ ] App loads without crash
- [ ] Home tab buttons render
- [ ] Buttons are interactive (tap triggers animation)
- [ ] Scroll LazyColumn — items appear instantly
- [ ] Switch tabs — instant
- [ ] Open Notifications popup — buttons appear instantly
- [ ] Return from popup — buttons still visible
- [ ] Rapid tab switching (10x) — no crash

**Step 5: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve build/runtime issues with batched rendering"
```

---

### Task 8: Remove Modifier.clickable workaround from PrimaryButton

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/Greeting.kt`

**Context:** `PrimaryButton` currently uses `Modifier.clickable { controller?.fireTrigger("Press") }` as a workaround for missing pointer input. With `RiveBatchItem`, native pointer events are forwarded to the state machine. The `Modifier.clickable` may interfere with `RiveBatchItem`'s pointer input handling.

**Step 1: Test with and without Modifier.clickable**

First test: Does the button animation work WITHOUT `Modifier.clickable`? If `RiveBatchItem`'s pointer forwarding handles the press/release, the animation should trigger natively.

**Step 2: If native interaction works, remove the clickable**

Remove `Modifier.clickable { controller?.fireTrigger("Press") }` from PrimaryButton. Keep the `eventCallback` and `onControllerReady` for non-pointer interactions (config changes, trigger flows).

**Step 3: If native interaction does NOT work**

The Rive file may rely on VMI triggers rather than pointer listeners. In that case, keep `Modifier.clickable` but ensure it doesn't consume events before `RiveBatchItem`'s pointer handler. Test by moving `clickable` to wrap the `RiveComponent` externally instead of passing it as the modifier.

**Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/Greeting.kt
git commit -m "refactor: remove clickable workaround now that batch handles pointer input"
```

---

## Summary of Changes

| Task | File | Change |
|---|---|---|
| 1 | `RiveExpects.kt` (common) | Add `batched: Boolean = true` |
| 2 | `RiveExpects.native.kt` (iOS) | Add `batched` param, ignored |
| 3 | `RiveExpects.android.kt` | Wrap content in `RiveBatchSurface` |
| 4 | `RiveExpects.android.kt` | Branch: `RiveBatchItem` vs `PoolableRiveView` |
| 5 | `PoolableRiveView.kt` | No change — kept as fallback |
| 6 | `RiveExpects.android.kt` | Add comment about hidden tab behavior |
| 7 | Build + test | Verify on device |
| 8 | `Greeting.kt` | Remove `Modifier.clickable` workaround |

## SDK Requirements

The AAR from `Nagarjuna0033/rive-android` must include:
- `RiveBatchSurface` composable (public)
- `RiveBatchItem` composable (public)
- `RiveBatchCoordinator` class (public)
- `BatchItemDescriptor` data class (public)
- `LocalRiveBatchCoordinator` CompositionLocal (public)
- `rememberArtboard` / `rememberStateMachine` composables (used internally by `RiveBatchItem`)
- `CommandQueue.drawBatch()` method (public)

If any of these are missing from the current AAR, rebuild from the SDK fork.
