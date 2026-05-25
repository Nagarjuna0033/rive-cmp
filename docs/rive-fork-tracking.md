# Rive Fork Tracking

Tracks which upstream Rive versions we forked, what we changed on top, and which fixes we cherry-picked.

Last updated: 2026-04-07

---

## iOS — RiveRuntime.xcframework

### Source repo
- **Local fork**: `/Users/peeyush.gulati/Desktop/Projects/Rive/rive-ios`
- **Upstream**: `https://github.com/rive-app/rive-ios.git`
- **Fork branch**: `main` (tracking upstream)

### Base version
- **Upstream commit**: `5143127` — `fix(apple): implement various missing command queue functionality (#11802)`
- **Tag**: VERSION file says `6.15.2` (but VERSION file is stale; commit is post-6.15.2 main)
- **Pulled**: 2026-03-04

### Pinned submodule
- **Repo**: `submodules/rive-runtime` → `https://github.com/rive-app/rive-runtime.git`
- **Base commit**: `f4e896a4` — `fix: reinit scripted objects owned by the state machine (#11783)`

### Changes on top of base (additive only)
1. **`Source/Experimental/Renderer/Renderer.h`** — added `BatchRendererConfiguration` struct and `drawBatchConfigurations:...` method declaration
2. **`Source/Experimental/Renderer/Renderer.mm`** — implemented `drawBatchConfigurations:...` for batched rendering of multiple artboards into one shared surface in a single beginFrame/flush pass
3. **`Source/Experimental/Batch/RiveBatchCoordinator.swift`** — NEW (registry of items, fillBatchArrays)
4. **`Source/Experimental/Batch/RiveBatchSurface.swift`** — NEW (single MTKView with render loop calling drawBatch)
5. **`Source/Experimental/Batch/RiveBatchItem.swift`** — NEW (registers with coordinator on layout)

These additions are mirrored in this repo at `SDK/swift/src/` for reference / so they aren't lost on a clean clone of the fork.

### Cherry-picked fixes (on top of base)

None currently applied.

#### Reverted / verified inert

| Date | Upstream commit | Title | Outcome |
|---|---|---|---|
| 2026-04-07 → reverted 2026-04-09 | `0d15033d` | fix(renderer) gamma correction fix (#11949) | Cherry-picked into `renderer/src/shaders/atomic_draw.glsl` while debugging the iOS "white box behind nav icons" bug. **Verified inert at the binary level**: a clean rebuild after reverting the patch produced byte-identical iOS / iOS-sim / Catalyst / tvOS / tvOS-sim / xrOS / xrOS-sim binaries (only macOS build metadata differed). The touched shader file lives in a code path that the Metal-based iOS builds don't compile, so the patch never reached any binary we ship. **Do not re-apply** without first verifying it actually changes the iOS binary. The real white-box fix turned out to be on the Compose side: pass `UIKitInteropProperties(placedAsOverlay = true)` to `UIKitView`. See `RiveExpects.native.kt`. |

### How to rebuild the xcframework
See `docs/aar-build-process.md` for the Android AAR. For iOS:

```bash
cd /Users/peeyush.gulati/Desktop/Projects/Rive/rive-ios
# After applying any patch to submodules/rive-runtime:
rm -rf submodules/rive-runtime/renderer/out/iphoneos_release \
       submodules/rive-runtime/renderer/out/iphonesimulator_release \
       out/iphoneos_release out/iphonesimulator_release
bash scripts/build.rive.sh release ios
bash scripts/build.rive.sh release ios_sim
# Other platforms (only if needed by build_framework.sh):
bash scripts/build.rive.sh release xros
bash scripts/build.rive.sh release xrsimulator
bash scripts/build.rive.sh release appletvos
bash scripts/build.rive.sh release appletvsimulator
bash scripts/build.rive.sh release maccatalyst
bash scripts/build.rive.sh release macosx
# Assemble the xcframework:
bash scripts/build_framework.sh -c Release
# Output: archive/RiveRuntime.xcframework
```

After build, copy to:
- `/Users/peeyush.gulati/Desktop/Projects/Rive-IOS/SDK/swift/RiveRuntime.xcframework` (linked by iosApp.xcodeproj)

---

## Android — rive-android-local.aar

### Source repo
- **Local fork**: `/Users/peeyush.gulati/Desktop/Projects/Rive/rive-android`
- **Fork (Github)**: `git@github.com:Nagarjuna0033/rive-android.git`
- **Upstream**: `https://github.com/rive-app/rive-android.git`

### Base version
- **Upstream tag**: `11.3.1` of `rive-app/rive-android` (uses `app.rive` package structure — NOT 9.x which uses `app.rive.runtime`)
- Pulled fresh on each build via `git archive 11.3.1 -- ...`

### Changes on top of base
1. **NEW** `kotlin/src/main/kotlin/app/rive/RiveBatch.kt` — `RiveBatchCoordinator`, `RiveBatchSurface`, `RiveBatchItem` for batched rendering into a single shared TextureView/EGL surface
2. **MODIFIED** `kotlin/src/main/kotlin/app/rive/core/CommandQueue.kt` — added `drawBatch()` method
3. **MODIFIED** `kotlin/src/main/kotlin/app/rive/core/CommandQueueBridge.kt` — added `cppDrawBatch()` JNI external
4. **MODIFIED** `kotlin/src/main/cpp/src/bindings/bindings_command_queue.cpp` — added `Java_app_rive_core_CommandQueueJNIBridge_cppDrawBatch` (1 beginFrame → N artboard draws → 1 flush → 1 present), surface clear logic for empty batches

These are mirrored in this repo's SDK files via the `SDK/` overlay (see `~/.claude/.../memory/aar-build-process.md`).

### Cherry-picked fixes
None yet.

### Note on current AAR shipped
- The AAR currently in `app/libs/rive-android-local.aar` is the verbatim binary from commit `fa8fcec0` (2026-03-25) which contains the production-tested batch surface clear logic. The SDK source in this repo's `SDK/` folder is a partial reconstruction and does NOT byte-match this binary. Fix-forward requires getting the real source tree from the BeBetta team.

---

## Update protocol

When applying a new cherry-pick or upstream sync:
1. Apply the patch to the appropriate submodule / source tree
2. Add a row to the cherry-picked fixes table above with date, commit, title, files, and rationale
3. Rebuild and copy artifacts per the build instructions above
4. Commit both the source change AND the rebuilt binary together with the same commit message referencing the upstream fix
