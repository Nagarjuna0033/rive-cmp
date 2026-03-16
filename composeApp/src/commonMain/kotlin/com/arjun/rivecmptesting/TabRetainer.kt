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
import androidx.compose.ui.zIndex

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
                        .zIndex(if (isActive) 1f else 0f)
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
