package com.arjun.core.rive

import android.content.Context
import androidx.compose.runtime.Composable
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.Result
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

    private val loadedFiles      = mutableMapOf<String, RiveFile>()
    private val loadStates       = mutableMapOf<String, RiveLoadState>()

    // ── Asset caches — decoded ONCE, shared across all files ──────────
    private val fontCache        = mutableMapOf<String, FontHandle>()
    private val imageCache       = mutableMapOf<String, ImageHandle>()
    private val registeredAssets = mutableSetOf<String>()

    // ── Preload single file ────────────────────────────────────────────
    override suspend fun preloadFile(config: RiveFileConfig): RiveLoadState {
        if (isFileLoaded(config.resourceName)) {
            println("[RiveFileManager] Already loaded: ${config.resourceName}")
            return RiveLoadState.Success
        }

        loadStates[config.resourceName] = RiveLoadState.Loading

        return try {
            // Step 1: Register all assets in parallel
            coroutineScope {
                config.assets.map { asset ->
                    async(Dispatchers.IO) { loadAndRegisterAsset(asset) }
                }.awaitAll()
            }

            // Step 2: Build RiveFile AFTER assets are registered
            val riveFile = buildRiveFile(config.resourceName)

            // Step 3: Store file
            loadedFiles[config.resourceName] = riveFile
            println("[RiveFileManager] Successfully loaded: ${config.resourceName}")

            RiveLoadState.Success.also {
                loadStates[config.resourceName] = it
            }

        } catch (e: Exception) {
            println("[RiveFileManager] Error loading ${config.resourceName}: ${e.message}")
            RiveLoadState.Error(
                e.message ?: "Unknown error loading ${config.resourceName}"
            ).also {
                loadStates[config.resourceName] = it
            }
        }
    }

    // ── Preload all — sequential to avoid asset registration races ─────
    override suspend fun preloadAll(configs: List<RiveFileConfig>): RiveLoadState {
        // Sequential — ensures assets registered before next file starts
        configs.forEach { config ->
            val result = preloadFile(config)
            if (result is RiveLoadState.Error) {
                println("[RiveFileManager] preloadAll failed at: ${config.resourceName}")
                return result
            }
        }
        return RiveLoadState.Success
    }

    // ── Load + register asset only ONCE per assetId ───────────────────
    private suspend fun loadAndRegisterAsset(asset: RiveAssetConfig) {
        if (registeredAssets.contains(asset.assetId)) {
            println("[RiveFileManager] Skip already registered: ${asset.assetId}")
            return
        }

        val bytes = loadRawBytes(asset.resourceName)

        when (asset.type) {
            RiveAssetType.FONT -> {
                val font = fontCache.getOrPut(asset.assetId) {
                    riveWorker.decodeFont(bytes).also {
                        println("[RiveFileManager] Decoded font: ${asset.assetId} -> $it")
                    }
                }
                riveWorker.registerFont(asset.assetId, font)
                println("[RiveFileManager] Registered font: ${asset.assetId}")
            }

            RiveAssetType.IMAGE -> {
                val image = imageCache.getOrPut(asset.assetId) {
                    riveWorker.decodeImage(bytes).also {
                        println("[RiveFileManager] Decoded image: ${asset.assetId} -> $it")
                    }
                }
                riveWorker.registerImage(asset.assetId, image)
                println("[RiveFileManager] Registered image: ${asset.assetId}")
            }
        }

        registeredAssets.add(asset.assetId)
    }

    // ── Build RiveFile from raw resource ──────────────────────────────
    private suspend fun buildRiveFile(resourceName: String): RiveFile {
        val resId = context.resources.getIdentifier(
            resourceName, "raw", context.packageName
        )
        require(resId != 0) { "Raw resource not found: $resourceName" }

        val bytes = withContext(Dispatchers.IO) {
            context.resources.openRawResource(resId).use { it.readBytes() }
        }

        // ── Use ByteArray source instead ───────────────────────────────────
        return when (
            val result = RiveFile.fromSource(
                source = RiveFileSource.Bytes(bytes),
                riveWorker = riveWorker
            )
        ) {
            is Result.Success -> result.value
            is Result.Error   -> throw Exception(
                "Failed to build RiveFile[$resourceName]: ${result.throwable?.message}"
            )
            is Result.Loading -> throw Exception(
                "Unexpected Loading state for RiveFile[$resourceName]"
            )
        }
    }

    // ── Load raw bytes from res/raw ────────────────────────────────────
    private suspend fun loadRawBytes(resourceName: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val resId = context.resources.getIdentifier(
                resourceName, "raw", context.packageName
            )
            require(resId != 0) { "Raw resource not found: $resourceName" }
            context.resources.openRawResource(resId).use { it.readBytes() }
        }
    }

    // ── Getters ────────────────────────────────────────────────────────
    fun getFile(resourceName: String): RiveFile? = loadedFiles[resourceName]

    override fun isFileLoaded(resourceName: String) = loadedFiles.containsKey(resourceName)

    override fun getLoadState(resourceName: String): RiveLoadState =
        loadStates[resourceName] ?: RiveLoadState.Idle

    override fun clearAll() {
        loadedFiles.clear()
        loadStates.clear()
        fontCache.clear()
        imageCache.clear()
        registeredAssets.clear()
    }
}