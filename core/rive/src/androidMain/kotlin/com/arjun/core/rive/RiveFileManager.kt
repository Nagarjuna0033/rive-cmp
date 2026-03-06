package com.arjun.core.rive

import android.content.Context
import app.rive.RiveFile
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import app.rive.core.RiveWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class AndroidRiveFileManager(
    private val context: Context,
    private val riveWorker: RiveWorker
) : RiveFileManager {

    private val loadedFiles = mutableMapOf<String, RiveFile>()
    private val loadStates = mutableMapOf<String, RiveLoadState>()

    private val fontCache = mutableMapOf<String, FontHandle>()
    private val imageCache = mutableMapOf<String, ImageHandle>()

    // ───────────────────────────────────────────────────────────────
    // Preload single file with all its assets
    // ───────────────────────────────────────────────────────────────
    override suspend fun preloadFile(config: RiveFileConfig): RiveLoadState {
        if (isFileLoaded(config.resourceName)) {
            return RiveLoadState.Success
        }

        loadStates[config.resourceName] = RiveLoadState.Loading

        return try {

            coroutineScope {
                config.assets.map { asset ->
                    async(Dispatchers.IO) { loadAsset(asset) }
                }.awaitAll()
            }

            loadStates[config.resourceName] = RiveLoadState.Success
            RiveLoadState.Success

        } catch (e: Exception) {
            val error = RiveLoadState.Error(
                e.message ?: "Unknown error loading ${config.resourceName}"
            )
            loadStates[config.resourceName] = error
            error
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Preload multiple files
    // ───────────────────────────────────────────────────────────────
    override suspend fun preloadAll(configs: List<RiveFileConfig>): RiveLoadState {
        return try {
            coroutineScope {
                configs.map { config ->
                    async { preloadFile(config) }
                }.awaitAll()
            }
            RiveLoadState.Success
        } catch (e: Exception) {
            RiveLoadState.Error(e.message ?: "Failed to preload all files")
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Load asset (font/image)
    // ───────────────────────────────────────────────────────────────
    private suspend fun loadAsset(asset: RiveAssetConfig) {
        val bytes = loadRawBytes(asset.resourceName)

        when (asset.type) {

            RiveAssetType.FONT -> {
                val font = fontCache.getOrPut(asset.assetId) {
                    riveWorker.decodeFont(bytes)
                }
                riveWorker.registerFont(asset.assetId, font)
            }

            RiveAssetType.IMAGE -> {
                val image = imageCache.getOrPut(asset.assetId) {
                    riveWorker.decodeImage(bytes)
                }
                riveWorker.registerImage(asset.assetId, image)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Load raw bytes
    // ───────────────────────────────────────────────────────────────
    private suspend fun loadRawBytes(resourceName: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val resId = context.resources.getIdentifier(
                resourceName,
                "raw",
                context.packageName
            )

            require(resId != 0) { "Raw resource not found: $resourceName" }

            context.resources.openRawResource(resId).use {
                it.readBytes()
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Getters
    // ───────────────────────────────────────────────────────────────
    fun getFile(resourceName: String): RiveFile? =
        loadedFiles[resourceName]

    override fun isFileLoaded(resourceName: String) =
        loadedFiles.containsKey(resourceName)

    override fun getLoadState(resourceName: String): RiveLoadState =
        loadStates[resourceName] ?: RiveLoadState.Idle

    override fun clearAll() {
        loadedFiles.clear()
        loadStates.clear()
    }
}