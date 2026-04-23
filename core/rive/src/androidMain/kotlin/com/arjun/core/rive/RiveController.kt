package com.arjun.core.rive


import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import app.rive.ImageAsset
import app.rive.ViewModelInstance
import app.rive.core.ImageHandle
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import app.rive.Result
import coil3.request.ErrorResult
import coil3.svg.SvgDecoder

// ── Wraps Rive Android SDK ViewModelInstance ───────────────────────────
class AndroidRiveController(
    private val vmi: ViewModelInstance,
    private val fileManager: AndroidRiveFileManager?,
) : RiveController {

    private val imageAssetCache = ConcurrentHashMap<String, ImageAsset>()


    override fun setString(propertyName: String, value: String) {
        runCatching { vmi.setString(propertyName, value).also {
            Log.d("Rive_DEBUG", "setString: $propertyName, $value")
        } }.onFailure { logError("setString($propertyName)", it) }
    }

    override fun setEnum(propertyName: String, value: String) {
        runCatching { vmi.setEnum(propertyName, value) }
            .also { Log.d("Rive_DEBUG", "setEnum: $propertyName, $value") }
            .onFailure { logError("setEnum($propertyName)", it) }
    }

    override fun setBoolean(propertyName: String, value: Boolean) {
        runCatching { vmi.setBoolean(propertyName, value) }
            .also { Log.d("Rive_DEBUG", "setBoolean: $propertyName, $value") }
            .onFailure { logError("setBoolean($propertyName)", it) }
    }

    override fun setNumber(propertyName: String, value: Float) {
        runCatching { vmi.setNumber(propertyName, value) }
            .also { Log.d("Rive_DEBUG", "setNumber: $propertyName, $value") }
            .onFailure { logError("setNumber($propertyName)", it) }
    }

    override fun fireTrigger(triggerName: String) {
        runCatching { vmi.fireTrigger(triggerName) }
            .also { Log.d("Rive_DEBUG", "fireTrigger: $triggerName") }
            .onFailure { logError("fireTrigger($triggerName)", it) }
    }

//    fun setColor(propertyName: String, value: Int) {
//        runCatching { vmi.setColor(propertyName, value) }
//            .also { Log.d("Rive_DEBUG", "setColor: $propertyName, $value") }
//            .onFailure { logError("setColor($propertyName)", it) }
//    }


    // ── CDN URL → Coil bytes → ImageHandle → CommandQueue.setImageProperty ──
//    override suspend fun setImageFromUrl(propertyName: String, url: String) {
//        val worker = fileManager?.riveWorker ?: run {
//            Log.e("RiveController", "setImageFromUrl: fileManager is null")
//            return
//        }
//
//        runCatching {
//            val bytes = loadRiveImageBytes(url)
//                ?: error("Failed to fetch bytes from $url")
//
//            val handle = imageHandleCache.getOrPut(url) {
//                ImageAssetOps.decode(worker, bytes)
//            }
//
//            Log.d("RiveController", "setImageFromUrl($propertyName, $url)")
//            // Bypass vmi.setImage — call CommandQueue directly with ImageHandle
//            worker.setImageProperty(
//                viewModelInstanceHandle = vmi.instanceHandle,
//                propertyPath = propertyName,
//                imageHandle = handle,
//            )
//
//        }.onFailure { logError("setImageFromUrl($propertyName, $url)", it) }
//    }

    override suspend fun setImageFromUrl(propertyName: String, url: String) {
        val worker = fileManager?.riveWorker ?: run {
            Log.e("RiveController", "setImageFromUrl: fileManager is null")
            return
        }

        runCatching {
            val bytes = loadRiveImageBytes(url)
                ?: error("Failed to fetch bytes from $url")

            val imageAsset = imageAssetCache.getOrPut(url) {
                when (val result = ImageAsset.fromBytes(worker, bytes)) {
                    is Result.Success -> result.value
                    is Result.Error -> error("ImageAsset.fromBytes failed: ${result.throwable?.message}")
                    is Result.Loading -> error("Unexpected loading state")
                }
            }

            vmi.setImage(propertyName, imageAsset)

        }.onFailure { logError("setImageFromUrl($propertyName, $url)", it) }
    }


    override fun destroy() {
        val worker = fileManager?.riveWorker ?: return
        imageAssetCache.values.forEach { asset ->
            runCatching {
                worker.deleteImage(asset.handle)
            }
        }
        imageAssetCache.clear()
    }

    // Expose raw VMI for advanced use
    fun getRawVmi(): ViewModelInstance = vmi

    private fun logError(fn: String, e: Throwable) {
        println("[RiveController] Error in $fn: ${e.message}")
    }
}

suspend fun loadRiveImageBytes(url: String): ByteArray? {
    val context = getKoin().get<Context>()
    val imageLoader = getKoin().get<ImageLoader>()

    return try {
        val isSvg = url.contains("svg", ignoreCase = true)

        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(200, 200)
            .apply {
                if (isSvg) decoderFactory(SvgDecoder.Factory())
            }
            .build()

        val result = imageLoader.execute(request)

        if (result !is SuccessResult) {
            Log.e("RiveImage", "Coil failed for $url — ${(result as? ErrorResult)?.throwable?.message}")
            return null
        }

        val bitmap = (result.image as? BitmapImage)?.bitmap ?: run {
            Log.e("RiveImage", "Not a BitmapImage — got ${result.image::class.simpleName}")
            return null
        }

        Log.d("RiveImage", "Loaded bitmap ${bitmap.width}x${bitmap.height} from $url")

        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }

    } catch (e: Exception) {
        Log.e("RiveImage", "Coil fetch failed for $url — ${e.message}", e)
        null
    }
}