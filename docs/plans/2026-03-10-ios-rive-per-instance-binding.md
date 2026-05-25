# iOS Rive Per-Instance Data Binding Fix

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix iOS Rive data binding so each contest button gets independent VMI bindings (matching Android behavior).

**Architecture:** Instead of sharing one `RiveModel` across all handles for the same resource, store the file config (fileName + customLoader closure) and create a fresh `RiveModel` per `createHandle()` call. Each handle gets its own `enableAutoBind` VMI, so property mutations are isolated per button.

**Tech Stack:** Swift (RiveRuntime iOS SDK), Kotlin/Native (Compose Multiplatform)

---

## Problem Summary

All 4 contest buttons on iOS show "New Mode" because:
1. `SwiftRiveBridge.loadedModels` stores **one** `RiveModel` per resource name
2. `createHandle()` passes that **same** `RiveModel` to all 4 `SwiftRiveHandle` instances
3. All 4 call `riveModel.enableAutoBind()` on the same model — last callback/VMI wins
4. The last config applied ("New Mode") overwrites all others

On Android, `rememberViewModelInstance(file)` creates independent VMIs per composable.

## Fix Strategy

Store a **factory closure** instead of a preloaded model. Each `createHandle()` call creates a fresh `RiveModel` with its own `enableAutoBind` lifecycle.

---

### Task 1: Refactor SwiftRiveBridge to store model factories instead of shared models

**Files:**
- Modify: `iosApp/iosApp/RiveBridge.swift:143-197`

**Step 1: Replace `loadedModels` dictionary with a factory-based approach**

Change `SwiftRiveBridge` from:
```swift
private var loadedModels: [String: RiveModel] = [:]
```

To storing the asset map so we can recreate models on demand:
```swift
private var loadedConfigs: [String: [String: RiveAssetConfig]] = [:]
```

**Step 2: Update `preloadFiles` to validate loading but store config, not model**

Replace `preloadFiles` with logic that:
1. Validates the .riv file exists and can be loaded (create a test model)
2. Stores the `assetMap` for later re-creation
3. Does NOT hold onto the `RiveModel` instance

```swift
func preloadFiles(configs: [RiveFileConfig]) -> Bool {
    for config in configs {
        if loadedConfigs[config.resourceName] != nil {
            continue
        }

        var assetMap: [String: RiveAssetConfig] = [:]
        for asset in config.assets {
            assetMap[asset.assetId] = asset
        }

        // Validate the file can be loaded
        do {
            let _ = try RiveModel(
                fileName: config.resourceName,
                loadCdn: false,
                customLoader: { [assetMap] asset, data, factory in
                    return Self.loadAsset(
                        asset: asset,
                        data: data,
                        factory: factory,
                        assetMap: assetMap
                    )
                }
            )
            loadedConfigs[config.resourceName] = assetMap
            print("[SwiftRiveBridge] Validated: \(config.resourceName)")
        } catch {
            print("[SwiftRiveBridge] Failed to load \(config.resourceName): \(error)")
            return false
        }
    }
    return true
}
```

**Step 3: Update `createHandle` to create a fresh RiveModel each time**

```swift
func createHandle(resourceName: String) -> IOSRiveHandle? {
    guard let assetMap = loadedConfigs[resourceName] else {
        print("[SwiftRiveBridge] No config for: \(resourceName)")
        return nil
    }
    do {
        let model = try RiveModel(
            fileName: resourceName,
            loadCdn: false,
            customLoader: { [assetMap] asset, data, factory in
                return Self.loadAsset(
                    asset: asset,
                    data: data,
                    factory: factory,
                    assetMap: assetMap
                )
            }
        )
        return SwiftRiveHandle(riveModel: model)
    } catch {
        print("[SwiftRiveBridge] Failed to create model for \(resourceName): \(error)")
        return nil
    }
}
```

**Step 4: Update `isFileLoaded` and `clearAll`**

```swift
func isFileLoaded(resourceName: String) -> Bool {
    return loadedConfigs[resourceName] != nil
}

func clearAll() {
    loadedConfigs.removeAll()
}
```

**Step 5: Build and verify**

Run: `cd iosApp && xcodebuild build -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16e'`
Expected: Build succeeds

**Step 6: Commit**

```bash
git add iosApp/iosApp/RiveBridge.swift
git commit -m "fix iOS Rive: create fresh RiveModel per handle for independent VMI binding"
```

---

### Task 2: Verify on simulator

**Step 1: Run app on iOS simulator**

Launch the app and verify:
- Button 1 shows "Testing" with cash icon and "35"
- Button 2 shows "Practice" with coin icon and "35"
- Button 3 shows "Locked" with lock icon (top-right)
- Button 4 shows "New Mode" with new tag

**Step 2: Check console logs**

Verify each handle gets its own VMI:
```
[SwiftRiveHandle] AutoBind VMI received, N properties:
```
Should appear 4 times (once per button), not just once.

---

## Risk Assessment

- **Low risk**: The change is isolated to `SwiftRiveBridge` in `RiveBridge.swift`
- **Trade-off**: Each handle now loads the .riv file from disk. For 4 buttons this is negligible. For large lists, consider caching the file `Data` bytes and using `RiveModel(riveFile:)` with a shared `RiveFile`
- **No Kotlin changes needed**: The bridge interface (`IOSRiveBridge`) is unchanged
