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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap


private const val TAG = "RiveFileManager"

class AndroidRiveFileManager(
    private val context: Context,
    val riveWorker: RiveWorker,
) : RiveFileManager {

    private val assetDir = File(context.filesDir, "app_assets/assets")

    private val loadedFiles = ConcurrentHashMap<String, RiveFile>().also { Log.d(TAG, "loadedFiles map created") }
    private val loadStates = ConcurrentHashMap<String, RiveLoadState>().also { Log.d(TAG, "loadStates map created") }

    private val fontCache = ConcurrentHashMap<String, FontHandle>().also { Log.d(FontAssetOps.tag, "fontCache map created") }
    private val imageCache = ConcurrentHashMap<String, ImageHandle>().also { Log.d(ImageAssetOps.tag, "imageCache map created") }
    private val audioCache = ConcurrentHashMap<String, AudioHandle>().also { Log.d(AudioAssetOps.tag, "audioCache map created") }

    private val registeredAssets = ConcurrentHashMap.newKeySet<String>().also { Log.d(TAG, "registeredAssets set created") }

    private val assetMutex = Mutex()

    override suspend fun preloadFile(config: RiveFileConfig): RiveLoadState {

        Log.d(TAG, "preloadFile: ${config.resourceName}, assets: ${config.assets.map { it.resourceName }}")

        if (isFileLoaded(config.resourceName)) {
            Log.d(TAG, "preloadFile: already loaded ${config.resourceName}")
            return RiveLoadState.Success
        }

        loadStates[config.resourceName] = RiveLoadState.Loading
        Log.d(TAG, "Set loadState to Loading for ${config.resourceName}")

        return try {
            config.assets.forEach { asset ->
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "loading asset: ${asset.resourceName} type: ${asset.type}")
                    loadAndRegisterAsset(asset)
                }
            }

            Log.d(TAG, "all assets loaded, building rive file: ${config.resourceName}")

            val riveFile = buildRiveFile(config.resourceName)
            Log.d(TAG, "riveFile built for ${config.resourceName}")

            loadedFiles[config.resourceName] = riveFile
            Log.d(TAG, "File cached for ${config.resourceName}")

            Log.d(TAG, "preloadFile SUCCESS: ${config.resourceName}")

            RiveLoadState.Success.also {
                loadStates[config.resourceName] = it
                Log.d(TAG, "Set loadState Success for ${config.resourceName}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "preloadFile ERROR: ${config.resourceName} -> ${e.message}", e)
            RiveLoadState.Error(e.message ?: "Unknown error")
                .also {
                    loadStates[config.resourceName] = it
                    Log.d(TAG, "Set loadState Error for ${config.resourceName}")
                }
        }
    }

    override suspend fun preloadAll(configs: List<RiveFileConfig>): RiveLoadState {
        Log.d(TAG, "preloadAll called for ${configs.size} configs")
        configs.forEach {
            Log.d(TAG, "Start preloadFile for ${it.resourceName}")
            val result = preloadFile(it)
            Log.d(TAG, "Result of preloadFile for ${it.resourceName}: $result")
            if (result is RiveLoadState.Error) {
                Log.e(TAG, "preloadAll failed for ${it.resourceName}")
                return result
            }
        }
        Log.d(TAG, "preloadAll succeeded")
        return RiveLoadState.Success
    }

    private suspend fun loadAndRegisterAsset(asset: RiveAssetConfig) {

        Log.d(TAG, "loadAndRegisterAsset start for ${asset.assetId}")

        // Fast check — no mutex needed
        if (registeredAssets.contains(asset.assetId)) {
            Log.d(TAG, "Skip already registered: ${asset.assetId}")
            return
        }

        // Load bytes — no mutex needed
        val bytes = loadFileBytes(asset.resourceName)
        Log.d(TAG, "bytes loaded: ${bytes.size} for ${asset.resourceName}")

        val decoded: Any = when (asset.type) {
            RiveAssetType.FONT -> {
                Log.d(FontAssetOps.tag, "Decoding font: ${asset.assetId}")
                riveWorker.decodeFont(bytes).also { Log.d(FontAssetOps.tag, "Font decoded: ${asset.assetId}") }
            }
            RiveAssetType.IMAGE -> {
                Log.d(ImageAssetOps.tag, "Decoding image: ${asset.assetId}")
                riveWorker.decodeImage(bytes).also { Log.d(ImageAssetOps.tag, "Image decoded: ${asset.assetId}") }
            }
            RiveAssetType.AUDIO -> {
                Log.d(AudioAssetOps.tag, "Decoding audio: ${asset.assetId}")
                riveWorker.decodeAudio(bytes).also { Log.d(AudioAssetOps.tag, "Audio decoded: ${asset.assetId}") }
            }
        }

        assetMutex.withLock {
            Log.d(TAG, "assetMutex locked for ${asset.assetId}")
            if (registeredAssets.contains(asset.assetId)) {
                Log.d(TAG, "Skip after decode (registered by another coroutine): ${asset.assetId}")
                return
            }

            when (asset.type) {
                RiveAssetType.FONT -> {
                    fontCache[asset.assetId] = decoded as FontHandle
                    Log.d(FontAssetOps.tag, "Font cached: ${asset.assetId}")
                    riveWorker.registerFont(asset.assetId, decoded)
                    Log.d(FontAssetOps.tag, "Font registered: ${asset.assetId}")
                }
                RiveAssetType.IMAGE -> {
                    imageCache[asset.assetId] = decoded as ImageHandle
                    Log.d(ImageAssetOps.tag, "Image cached: ${asset.assetId}")
                    riveWorker.registerImage(asset.assetId, decoded)
                    Log.d(ImageAssetOps.tag, "Image registered: ${asset.assetId}")
                }
                RiveAssetType.AUDIO -> {
                    audioCache[asset.assetId] = decoded as AudioHandle
                    Log.d(AudioAssetOps.tag, "Audio cached: ${asset.assetId}")
                    riveWorker.registerAudio(asset.assetId, decoded)
                    Log.d(AudioAssetOps.tag, "Audio registered: ${asset.assetId}")
                }
            }

            registeredAssets.add(asset.assetId)
            Log.d(TAG, "Asset registered: ${asset.assetId}")
        }
        Log.d(TAG, "loadAndRegisterAsset finished for ${asset.assetId}")
    }

    private suspend fun buildRiveFile(fileName: String): RiveFile {
        Log.d(TAG, "buildRiveFile: $fileName")
        val bytes = loadFileBytes(fileName)
        Log.d(TAG, "rive file bytes loaded: ${bytes.size} for $fileName")

        return when (
            val result = RiveFile.fromSource(
                source = RiveFileSource.Bytes(bytes),
                riveWorker = riveWorker
            )
        ) {
            is Result.Success -> {
                Log.d(TAG, "RiveFile built successfully: $fileName")
                result.value
            }
            is Result.Error -> {
                Log.e(TAG, "RiveFile build failed: $fileName -> ${result.throwable?.message}")
                throw Exception(result.throwable?.message)
            }
            is Result.Loading -> {
                Log.e(TAG, "RiveFile is in Unexpected loading state: $fileName")
                throw Exception("Unexpected loading state")
            }
        }
    }

    private suspend fun loadFileBytes(fileName: String): ByteArray =
        withContext(Dispatchers.IO) {

            val file = File(assetDir, fileName)
            Log.d(TAG, "loadFileBytes: $fileName on dispatcher")
            Log.d(TAG, "loadFileBytes: $fileName")

            require(file.exists()) {
                "Asset not found: ${file.absolutePath}"
            }

            file.readBytes()
        }

    fun getFile(resourceName: String): RiveFile? {
        Log.d(TAG, "getFile called for: $resourceName")
        return loadedFiles[resourceName]
    }

    override fun isFileLoaded(resourceName: String): Boolean {
        val loaded = loadedFiles.containsKey(resourceName)
        Log.d(TAG, "isFileLoaded for $resourceName: $loaded")
        return loaded
    }

    override fun getLoadState(resourceName: String): RiveLoadState {
        val state = loadStates[resourceName] ?: RiveLoadState.Idle
        Log.d(TAG, "getLoadState for $resourceName: $state")
        return state
    }

    override fun clearAll() {
        Log.d(TAG, "clearAll called")
        loadedFiles.values.forEach { runCatching { it.close() }.onFailure { Log.e(TAG, "Error closing loaded file", it) } }

        // Unregister + delete each handle ONCE (no double-delete!)
        fontCache.forEach { (key, handle) ->
            Log.d(FontAssetOps.tag, "Unregister + delete font: $key")
            FontAssetOps.unregister(riveWorker, key)
            FontAssetOps.delete(riveWorker, handle)
        }

        imageCache.forEach { (key, handle) ->
            Log.d(ImageAssetOps.tag, "Unregister + delete image: $key")
            ImageAssetOps.unregister(riveWorker, key)
            ImageAssetOps.delete(riveWorker, handle)
        }

        audioCache.forEach { (_, handle) ->
            Log.d(AudioAssetOps.tag, "Deleting audio handle")
            AudioAssetOps.delete(riveWorker, handle)
        }

        loadedFiles.clear()
        Log.d(TAG, "loadedFiles cleared")
        loadStates.clear()
        Log.d(TAG, "loadStates cleared")
        fontCache.clear()
        Log.d(FontAssetOps.tag, "fontCache cleared")
        imageCache.clear()
        Log.d(ImageAssetOps.tag, "imageCache cleared")
        audioCache.clear()
        Log.d(AudioAssetOps.tag, "audioCache cleared")
        registeredAssets.clear()
        Log.d(TAG, "registeredAssets cleared")
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
        Log.d(tag, "decode called")
        return worker.decodeImage(bytes).also { Log.d(tag, "decodeImage result: $it") }
    }

    override fun register(worker: RiveWorker, key: String, handle: ImageHandle) {
        Log.d(tag, "register called for key: $key")
        worker.registerImage(key, handle)
    }

    override fun unregister(worker: RiveWorker, key: String) {
        Log.d(tag, "unregister called for key: $key")
        worker.unregisterImage(key)
    }

    override fun delete(worker: RiveWorker, handle: ImageHandle) {
        Log.d(tag, "delete called for handle: $handle")
        worker.deleteImage(handle)
    }
}

object FontAssetOps : RiveAssetOps<FontHandle> {

    override val tag = "Rive/Font"
    override val label = "font"

    override suspend fun decode(worker: RiveWorker, bytes: ByteArray): FontHandle {
        Log.d(tag, "decode called")
        return worker.decodeFont(bytes).also { Log.d(tag, "decodeFont result: $it") }
    }

    override fun register(worker: RiveWorker, key: String, handle: FontHandle) {
        Log.d(tag, "register called for key: $key")
        worker.registerFont(key, handle)
    }

    override fun unregister(worker: RiveWorker, key: String) {
        Log.d(tag, "unregister called for key: $key")
        worker.unregisterFont(key)
    }

    override fun delete(worker: RiveWorker, handle: FontHandle) {
        Log.d(tag, "delete called for handle: $handle")
        worker.deleteFont(handle)
    }
}

object AudioAssetOps : RiveAssetOps<AudioHandle> {

    override val tag = "Rive/Audio"
    override val label = "audio"

    override suspend fun decode(worker: RiveWorker, bytes: ByteArray): AudioHandle {
        Log.d(tag, "decode called")
        return worker.decodeAudio(bytes).also { Log.d(tag, "decodeAudio result: $it") }
    }

    override fun register(worker: RiveWorker, key: String, handle: AudioHandle) {
        Log.d(tag, "register called for key: $key")
        worker.registerAudio(key, handle)
    }

    override fun unregister(worker: RiveWorker, key: String) {
        Log.d(tag, "unregister called for key: $key")
        worker.unregisterAudio(key)
    }

    override fun delete(worker: RiveWorker, handle: AudioHandle) {
        Log.d(tag, "delete called for handle: $handle")
        worker.deleteAudio(handle)
    }
}