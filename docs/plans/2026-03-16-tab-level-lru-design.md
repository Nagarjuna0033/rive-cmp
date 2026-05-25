# Tab-Level LRU for Rive Rendering Optimization

## Problem

When navigating between tabs, Rive buttons show a rendering delay (200-500ms on lower-end devices). The `RiveFile` and `ViewModelInstance` are cached, but the artboard + state machine + first frame render must rebuild every time a composable re-enters composition.

## Constraints

- Android-only (iOS can be added later)
- 2-5 tabs, each with ~20 Rive buttons in a LazyColumn
- Rive SDK's `Rive()` composable creates artboards internally — cannot pool artboards directly
- Animation state restarts from beginning on reuse (no resume)
- Must work with current Rive SDK without forking

## Approach: Tab-Level LRU via `TabRetainer`

Instead of pooling individual artboards (not possible without SDK changes), keep the last N tabs alive in Compose composition. LazyColumn handles item-level recycling within each tab.

### How It Works

1. All tabs can be composed, but only `maxRetained` are kept alive at once
2. Active tab is visible and rendering
3. Recently visited tabs are composed but hidden (`graphicsLayer { alpha = 0f }`) — no GPU draw cost
4. Oldest tabs beyond `maxRetained` are removed from composition entirely
5. When an evicted tab is revisited, it rebuilds (cheap — file + VMI are cached)

### Architecture

```
TabRetainer(activeTab, maxRetained=3, tabs)
    │
    ├── Tab 0 (active)         → ALIVE, visible, rendering
    ├── Tab 1 (visited 1s ago) → ALIVE, hidden (alpha=0), idle
    ├── Tab 2 (visited 10s ago)→ ALIVE, hidden (alpha=0), idle
    ├── Tab 3 (visited 30s ago)→ DISPOSED, rebuilt on next visit
    └── Tab 4 (never visited)  → NOT COMPOSED, built on first visit
```

### API

```kotlin
@Composable
fun TabRetainer(
    activeTab: Int,
    maxRetained: Int = 3,
    tabs: List<@Composable () -> Unit>
)
```

### Usage

```kotlin
TabRetainer(
    activeTab = selectedTab,
    maxRetained = 3,
    tabs = listOf(
        { HomeScreen() },
        { ContestsScreen() },
        { LeaderboardScreen() },
        { ProfileScreen() },
        { SettingsScreen() }
    )
)
```

### Memory Profile

| maxRetained | Rive views in memory | Instant switch coverage |
|---|---|---|
| 2 | ~10 | Active + 1 recent tab |
| 3 | ~15 | Active + 2 recent tabs |
| 4 | ~20 | Active + 3 recent tabs |

Each Rive button view ≈ 0.5-1 MB. With `maxRetained=3` and ~5 visible items per tab: ~15 views × ~0.75 MB ≈ ~11 MB.

### Edge Cases

- **First visit:** Normal compose, no pool interaction
- **Rapid switching:** LRU order updates on each switch, main thread only, no races
- **Config change (rotation):** LRU order lost, all tabs rebuild. File + VMI caches survive
- **Memory pressure:** `maxRetained` is the hard cap

### Files to Create/Modify

- **Create:** `composeApp/src/commonMain/.../TabRetainer.kt` — the `TabRetainer` composable
- **Modify:** `composeApp/src/commonMain/.../App.kt` — replace `when (selectedTab)` with `TabRetainer`

### Why Not Pool Artboards Directly?

The Rive SDK's `Rive()` composable creates artboards internally. We pass `file` and `viewModelInstance`, but artboard/state machine creation happens inside the composable and is not exposed. Pooling individual artboards would require forking or wrapping the SDK — not worth the complexity for 2-5 tabs.
