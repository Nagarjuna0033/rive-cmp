package com.arjun.core.rive

import android.content.Context
import android.util.Log
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.Result
import app.rive.core.AudioHandle
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import app.rive.core.RiveWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val IMAGE_TAG = "Rive/Image"
private const val AUDIO_TAG = "Rive/Audio"
private const val FONT_TAG = "Rive/Font"

class AndroidRiveFileManager(
    private val context: Context,
    val riveWorker: RiveWorker
) : RiveFileManager {

    private val assetDir = File(context.filesDir, "app_assets/assets")
    internal val loadedBytes = ConcurrentHashMap<String, ByteArray>()
    internal val assetBytes = ConcurrentHashMap<String, ByteArray>()

    private val loadedFiles = ConcurrentHashMap<String, RiveFile>()
    private val loadStates = ConcurrentHashMap<String, RiveLoadState>()

    private val fontCache = ConcurrentHashMap<String, FontHandle>()
    private val imageCache = ConcurrentHashMap<String, ImageHandle>()
    private val audioCache = ConcurrentHashMap<String, AudioHandle>()

    private val registeredAssets = ConcurrentHashMap.newKeySet<String>()

    private val assetMutex = Mutex()

    override suspend fun preloadFile(config: RiveFileConfig): RiveLoadState {

        if (isFileLoaded(config.resourceName)) {
            return RiveLoadState.Success
        }

        loadStates[config.resourceName] = RiveLoadState.Loading

        // Track which assets we register so we can roll back on failure
        val registeredForThisFile = mutableListOf<RiveAssetConfig>()

        return try {

            coroutineScope {
                config.assets.map { asset ->
                    async(Dispatchers.IO) {
                        loadAndRegisterAsset(asset)
                        registeredForThisFile.add(asset)
                    }
                }.awaitAll()
            }

            val riveFile = buildRiveFile(config.resourceName)

            loadedFiles[config.resourceName] = riveFile

            RiveLoadState.Success.also {
                loadStates[config.resourceName] = it
            }

        } catch (e: Exception) {

            // Roll back registered assets so retry can re-register them
            registeredForThisFile.forEach { asset ->
                runCatching {
                    when (asset.type) {
                        RiveAssetType.FONT -> FontAssetOps.unregister(riveWorker, asset.assetId)
                        RiveAssetType.IMAGE -> ImageAssetOps.unregister(riveWorker, asset.assetId)
                        RiveAssetType.AUDIO -> AudioAssetOps.unregister(riveWorker, asset.assetId)
                    }
                    registeredAssets.remove(asset.assetId)
                }
            }

            RiveLoadState.Error(e.message ?: "Unknown error")
                .also { loadStates[config.resourceName] = it }
        }
    }

    override suspend fun preloadAll(configs: List<RiveFileConfig>): RiveLoadState {

        configs.forEach {

            val result = preloadFile(it)

            if (result is RiveLoadState.Error) {
                return result
            }
        }

        return RiveLoadState.Success
    }

    private suspend fun loadAndRegisterAsset(asset: RiveAssetConfig) {

        assetMutex.withLock {

            if (registeredAssets.contains(asset.assetId)) return

            val bytes = loadFileBytes(asset.resourceName)
            assetBytes[asset.assetId] = bytes
            assetBytes[asset.resourceName] = bytes

            when (asset.type) {

                RiveAssetType.FONT -> {

                    val font = fontCache.getOrPut(asset.assetId) {
                        FontAssetOps.decode(riveWorker, bytes)
                    }

                    FontAssetOps.register(riveWorker, asset.assetId, font)
                }

                RiveAssetType.IMAGE -> {

                    val image = imageCache.getOrPut(asset.assetId) {
                        ImageAssetOps.decode(riveWorker, bytes)
                    }

                    ImageAssetOps.register(riveWorker, asset.assetId, image)
                }

                RiveAssetType.AUDIO -> {

                    val audio = audioCache.getOrPut(asset.assetId) {
                        AudioAssetOps.decode(riveWorker, bytes)
                    }

                    AudioAssetOps.register(riveWorker, asset.assetId, audio)
                }
            }

            registeredAssets.add(asset.assetId)
        }
    }

    private suspend fun buildRiveFile(fileName: String): RiveFile {

        val bytes = loadFileBytes(fileName)
        loadedBytes[fileName] = bytes
        Log.d("RiveFileManager", "Loaded bytes for $fileName")
        return when (
            val result = RiveFile.fromSource(
                source = RiveFileSource.Bytes(bytes),
                riveWorker = riveWorker
            )
        ) {
            is Result.Success -> result.value
            is Result.Error -> throw Exception(result.throwable?.message)
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }

    private suspend fun loadFileBytes(fileName: String): ByteArray =
        withContext(Dispatchers.IO) {

            val file = File(assetDir, fileName)

            require(file.exists()) {
                "Asset not found: ${file.absolutePath}"
            }

            file.readBytes()
        }

    fun getFile(resourceName: String): RiveFile? = loadedFiles[resourceName]


    fun getAssetBytes(name: String): ByteArray? =
        assetBytes[name] ?: assetBytes.entries.firstOrNull {
            it.key.contains(name, ignoreCase = true)
        }?.value

    override fun isFileLoaded(resourceName: String) =
        loadedFiles.containsKey(resourceName)

    override fun getLoadState(resourceName: String) =
        loadStates[resourceName] ?: RiveLoadState.Idle

    override fun clearAll() {
        loadedFiles.values.forEach { runCatching { it.close() } }
        fontCache.values.forEach { FontAssetOps.delete(riveWorker, it) }
        imageCache.values.forEach { ImageAssetOps.delete(riveWorker, it) }
        audioCache.values.forEach { AudioAssetOps.delete(riveWorker, it) }

        loadedFiles.clear()
        loadStates.clear()
        fontCache.clear()
        imageCache.clear()
        audioCache.clear()
        registeredAssets.clear()
    }
}

interface RiveAssetOps<H> {

    val tag: String
    val label: String

    suspend fun decode(worker: RiveWorker, bytes: ByteArray): H

    fun register(worker: RiveWorker, key: String, handle: H)

    fun unregister(worker: RiveWorker, key: String)

    fun delete(worker: RiveWorker, handle: H)
}


object ImageAssetOps : RiveAssetOps<ImageHandle> {

    override val tag = "Rive/Image"
    override val label = "image"

    override suspend fun decode(worker: RiveWorker, bytes: ByteArray): ImageHandle {
        return worker.decodeImage(bytes)
    }

    override fun register(worker: RiveWorker, key: String, handle: ImageHandle) {
        worker.registerImage(key, handle)
    }

    override fun unregister(worker: RiveWorker, key: String) {
        worker.unregisterImage(key)
    }

    override fun delete(worker: RiveWorker, handle: ImageHandle) {
        worker.deleteImage(handle)
    }
}

object FontAssetOps : RiveAssetOps<FontHandle> {

    override val tag = "Rive/Font"
    override val label = "font"

    override suspend fun decode(worker: RiveWorker, bytes: ByteArray): FontHandle {
        return worker.decodeFont(bytes)
    }

    override fun register(worker: RiveWorker, key: String, handle: FontHandle) {
        worker.registerFont(key, handle)
    }

    override fun unregister(worker: RiveWorker, key: String) {
        worker.unregisterFont(key)
    }

    override fun delete(worker: RiveWorker, handle: FontHandle) {
        worker.deleteFont(handle)
    }
}

object AudioAssetOps : RiveAssetOps<AudioHandle> {

    override val tag = "Rive/Audio"
    override val label = "audio"

    override suspend fun decode(worker: RiveWorker, bytes: ByteArray): AudioHandle {
        return worker.decodeAudio(bytes)
    }

    override fun register(worker: RiveWorker, key: String, handle: AudioHandle) {
        worker.registerAudio(key, handle)
    }

    override fun unregister(worker: RiveWorker, key: String) {
        worker.unregisterAudio(key)
    }

    override fun delete(worker: RiveWorker, handle: AudioHandle) {
        worker.deleteAudio(handle)
    }
}
