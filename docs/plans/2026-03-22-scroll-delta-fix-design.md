# Scroll Delta Fix Design

## Problem

Rive batched rendering buttons detach from their parent cards during LazyColumn scroll. Observed symptoms:
- Buttons float independently of cards during scroll
- Buttons appear on wrong cards (content swapping)
- Buttons disappear entirely during fast fling

## Root Cause

`RiveBatchCoordinator.applyScrollDelta()` has an **inverted sign** on the delta application:

```kotlin
// WRONG (current):
x = item.x - dxInt,
y = item.y - dyInt,
```

When user scrolls up: `available.y = -10` (negative), item should move up (y decreases).
Code does: `y - (-10) = y + 10` — moves items DOWN (opposite direction).

This causes positions to diverge from reality between `onPreScroll` and `onPlaced` corrections, resulting in buttons floating, swapping, and disappearing during scroll.

## Fix

Change the operator from `-` to `+`:

```kotlin
// CORRECT:
x = item.x + dxInt,
y = item.y + dyInt,
```

| Gesture | `available.y` | Item should move | Before (`- dy`) | After (`+ dy`) |
|---------|--------------|-----------------|-----------------|-----------------|
| Scroll up | -10 | Up (y decreases) | y + 10 (WRONG) | y - 10 (correct) |
| Scroll down | +10 | Down (y increases) | y - 10 (WRONG) | y + 10 (correct) |

## File

`kotlin/src/main/kotlin/app/rive/RiveBatch.kt` in `Nagarjuna0033/rive-android` SDK fork.

## Workflow

1. Fix `RiveBatch.kt` in SDK fork
2. Rebuild AAR
3. Copy AAR to app libs
4. Commit to both repos
5. Test on device
