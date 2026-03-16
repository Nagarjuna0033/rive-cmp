# Tab-Level LRU Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate Rive rendering delay on tab switch by keeping recently visited tabs alive in composition with bounded memory.

**Architecture:** A `TabRetainer` composable tracks tab visit order and keeps the last N tabs composed but hidden. Active tab renders normally; retained tabs use `graphicsLayer { alpha = 0f }` to skip drawing. Evicted tabs leave composition entirely and rebuild on next visit (cheap — file + VMI are cached).

**Tech Stack:** Kotlin, Jetpack Compose (Compose Multiplatform), Rive Android SDK

---

### Task 1: Create `TabRetainer` composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/TabRetainer.kt`

**Step 1: Create the file with the full implementation**

```kotlin
package com.arjun.rivecmptesting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Keeps the last [maxRetained] visited tabs alive in composition.
 * Active tab is visible. Retained tabs are composed but hidden (alpha=0).
 * Evicted tabs leave composition and rebuild on next visit.
 *
 * @param activeTab  Currently selected tab index
 * @param maxRetained  Max tabs to keep alive (including active). Default 3.
 * @param tabs  List of composable tab content lambdas
 */
@Composable
fun TabRetainer(
    activeTab: Int,
    maxRetained: Int = 3,
    tabs: List<@Composable () -> Unit>
) {
    // Track visit order — most recent first
    var visitOrder by remember { mutableStateOf(listOf<Int>()) }

    // Update visit order when active tab changes
    val updatedOrder = remember(activeTab) {
        val newOrder = visitOrder.toMutableList()
        newOrder.remove(activeTab)
        newOrder.add(0, activeTab) // most recent at front
        visitOrder = newOrder
        newOrder
    }

    // Tabs to keep alive: top maxRetained from visit order
    val retainedTabs = updatedOrder.take(maxRetained).toSet()

    Box(modifier = Modifier.fillMaxSize()) {
        tabs.forEachIndexed { index, tabContent ->
            if (index in retainedTabs) {
                val isActive = index == activeTab
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (isActive) 1f else 0f
                        }
                ) {
                    tabContent()
                }
            }
            // Tabs not in retainedTabs are not composed at all
        }
    }
}
```

**Step 2: Verify it compiles**

Run: `./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/TabRetainer.kt
git commit -m "feat: add TabRetainer composable for tab-level LRU retention"
```

---

### Task 2: Wire `TabRetainer` into `App.kt`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/App.kt:70-83`

**Step 1: Replace the `when (selectedTab)` block**

In `App.kt`, find this block (lines 70-83):

```kotlin
Box(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
) {

    when (selectedTab) {

        0 -> ContestLargeCards()

        1 -> ContestLargeCards()

    }
}
```

Replace with:

```kotlin
Box(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
) {
    TabRetainer(
        activeTab = selectedTab,
        maxRetained = 3,
        tabs = listOf(
            { ContestLargeCards() },
            { ContestLargeCards() },
        )
    )
}
```

**Step 2: Remove unused imports if any**

Check that `AnimatedVisibility`, `Image`, `painterResource`, and other unused imports are cleaned up.

**Step 3: Verify it compiles**

Run: `./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/arjun/rivecmptesting/App.kt
git commit -m "feat: use TabRetainer in App.kt for instant tab switching"
```

---

### Task 3: Manual test on device

**Step 1: Build and run on Android**

Run: `./gradlew installDebug` or run from Android Studio

**Step 2: Test tab switching**

1. Open app — Tab 0 should show Rive buttons normally
2. Switch to Tab 1 — should appear **instantly** (no rendering delay)
3. Switch back to Tab 0 — should appear **instantly** (was retained)
4. Verify Rive animations play correctly on both tabs
5. Verify trigger (Press) and text toggle still work on both tabs

**Step 3: Test with more tabs (if available)**

If you add more tabs later:
1. Visit Tab 0, 1, 2, 3 in sequence
2. Switch back to Tab 3 — instant (retained)
3. Switch back to Tab 2 — instant (retained)
4. Switch back to Tab 0 — small delay (evicted, rebuilding)

**Step 4: Commit the final state**

```bash
git add -A
git commit -m "test: verify TabRetainer with Rive buttons on device"
```

---

### Task 4: Push to main

**Step 1: Push all commits**

```bash
git push origin main
```
