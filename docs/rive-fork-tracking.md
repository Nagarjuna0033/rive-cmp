# Rive Fork Tracking

Tracks which upstream Rive versions we use and what we changed on top.

Last updated: 2026-06-08

---

## iOS — RiveRuntime (upstream via SPM)

**No fork.** Uses the official `rive-app/rive-ios` package via Swift Package Manager.

- **Package URL**: `https://github.com/rive-app/rive-ios.git`
- **Pinned version**: `6.20.5`
- **Integration**: SPM dependency in `iosApp/iosApp.xcodeproj`

All iOS Rive interaction goes through standard public APIs (`RiveFile`, `RiveModel`, `RiveViewModel`, `enableAutoBind`, VMI properties). No custom rendering or batch changes needed.

### History

Previously used a forked xcframework with batch rendering additions (`drawBatchConfigurations`, `RiveBatchSurface`, `RiveBatchItem`). These were never wired into the KMP layer — iOS always rendered one `UIKitView` per `RiveComponent`. The fork was removed in favor of upstream SPM to eliminate maintenance overhead.

---

## Android — rive-android-local.aar

### Source repo
- **Local fork**: `/Users/peeyush.gulati/Desktop/Projects/Rive/rive-android`
- **Fork (Github)**: `git@github.com:Nagarjuna0033/rive-android.git`
- **Upstream**: `https://github.com/rive-app/rive-android.git`

### Base version
- **Upstream tag**: `11.6.1` of `rive-app/rive-android` (uses `app.rive` package structure — NOT 9.x which uses `app.rive.runtime`)
- Pulled fresh on each build via `git archive 11.6.1 -- ...`

### Changes on top of base
1. **NEW** `kotlin/src/main/kotlin/app/rive/RiveBatch.kt` — `RiveBatchCoordinator`, `RiveBatchSurface`, `RiveBatchItem` for batched rendering into a single shared TextureView/EGL surface
2. **MODIFIED** `kotlin/src/main/kotlin/app/rive/core/CommandQueue.kt` — added `drawBatch()` method with explicit `count` parameter
3. **MODIFIED** `kotlin/src/main/kotlin/app/rive/core/CommandQueueBridge.kt` — added `cppDrawBatch()` JNI external with explicit `count` parameter
4. **MODIFIED** `kotlin/src/main/cpp/src/bindings/bindings_command_queue.cpp` — added `Java_app_rive_core_CommandQueueJNIBridge_cppDrawBatch` (1 beginFrame → N artboard draws → 1 flush → 1 present) using `LazyFramebufferRenderTargetGL`

These are mirrored in this repo's SDK files via the `SDK/kotlin/` overlay.

### Key adaptation from 11.3.1 → 11.6.1
- Render target type changed from `rive::gpu::RenderTargetGL*` to `LazyFramebufferRenderTargetGL*` with `getOrCreate()` lazy initialization
- `createRiveSurface(SurfaceTexture)` deprecated; `RiveBatch.kt` now uses `createRiveSurface(SurfaceTextureSurface(...))`
- `destroyRiveSurface()` deprecated; now uses `surface.close()` directly
- `cppCreateRiveRenderTarget` removed upstream — no longer in our overlay

### Cherry-picked fixes
None yet.

### Note on current AAR shipped
- The AAR in `app/libs/rive-android-local.aar` is still built against 11.3.1. It must be rebuilt against 11.6.1 using the updated overlay files to match this branch.

---

## Update protocol

When applying a new cherry-pick or upstream sync:
1. Apply the patch to the appropriate submodule / source tree
2. Add a row to the cherry-picked fixes table above with date, commit, title, files, and rationale
3. Rebuild and copy artifacts per the build instructions above
4. Commit both the source change AND the rebuilt binary together with the same commit message referencing the upstream fix
