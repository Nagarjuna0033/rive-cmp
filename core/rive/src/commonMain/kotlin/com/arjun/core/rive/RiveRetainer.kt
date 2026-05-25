package com.arjun.core.rive

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Retains Rive-heavy composable content across tab switches using LRU eviction.
 *
 * **Purpose**: Use this composable to wrap only the Rive animation content within
 * each tab. Place non-Rive UI (text, buttons, lists) outside this composable so
 * that only the expensive Rive renderers are retained in memory.
 *
 * **How it works**:
 * - The active tab is rendered normally (fully visible).
 * - Previously visited tabs are kept alive in composition but hidden via
 *   `graphicsLayer { alpha = 0f }`, which has zero GPU draw cost — the render
 *   node is skipped entirely by the graphics layer.
 * - Tabs beyond [maxRetained] are evicted from composition, freeing their
 *   Rive renderer resources.
 * - Rive animations on hidden tabs pause automatically via lifecycle awareness,
 *   so there is no CPU cost for off-screen animations.
 *
 * **Usage**:
 * ```kotlin
 * RiveRetainer(
 *     activeTab = selectedTabIndex,
 *     maxRetained = 3,
 *     tabs = listOf(
 *         { isActive -> RiveAnimation(file = "hero.riv", isPlaying = isActive) },
 *         { isActive -> RiveAnimation(file = "stats.riv", isPlaying = isActive) },
 *         { isActive -> RiveAnimation(file = "settings.riv", isPlaying = isActive) },
 *     )
 * )
 * ```
 *
 * @param activeTab Index of the currently visible tab.
 * @param maxRetained Maximum number of tabs to keep alive in composition (including the active one).
 * @param tabs List of composable lambdas, one per tab. Each receives an [isActive] boolean
 *             indicating whether the tab is currently the visible one.
 */
@Composable
fun RiveRetainer(
    activeTab: Int,
    maxRetained: Int = 3,
    tabs: List<@Composable (isActive: Boolean) -> Unit>,
) {
    if (activeTab !in tabs.indices) return
    val effectiveMaxRetained = maxRetained.coerceAtLeast(1)

    val lruOrder = remember { mutableStateListOf<Int>() }

    // Update LRU order after composition succeeds — avoids mutating snapshot state
    // during composition (which violates the side-effect-free composition contract).
    SideEffect {
        if (lruOrder.firstOrNull() != activeTab) {
            lruOrder.remove(activeTab)
            lruOrder.add(0, activeTab)
            while (lruOrder.size > effectiveMaxRetained) {
                lruOrder.removeLast()
            }
        }
    }

    // Build the render list: ensure activeTab is always included even on first frame
    // (before SideEffect runs), so we never render a blank frame.
    val renderList = if (activeTab in lruOrder) lruOrder else listOf(activeTab) + lruOrder

    // Compose hidden tabs FIRST (behind in z-order), then active tab LAST (on top).
    // This ensures the active tab receives all touch events. Hidden tabs also block
    // pointer input explicitly to prevent invisible buttons from intercepting taps.
    renderList.filter { it != activeTab }.forEach { tabIndex ->
        key(tabIndex) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = 0f }
                    .pointerInput(Unit) {
                        // Consume all pointer events so hidden tabs never intercept touches.
                        awaitPointerEventScope { while (true) { awaitPointerEvent() } }
                    },
            ) {
                tabs[tabIndex](false)
            }
        }
    }
    // Active tab last — on top in z-order, receives all touches.
    key(activeTab) {
        Box {
            tabs[activeTab](true)
        }
    }
}
