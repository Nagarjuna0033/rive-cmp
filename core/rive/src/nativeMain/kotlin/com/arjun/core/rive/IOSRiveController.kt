package com.arjun.core.rive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy
import kotlin.coroutines.resume

class IOSRiveController(
    private val handle: IOSRiveHandle
) : RiveController {

    override fun setString(propertyName: String, value: String) {
        runCatching { handle.setStringProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setString($propertyName) error: ${it.message}") }
    }

    override fun setEnum(propertyName: String, value: String) {
        runCatching { handle.setEnumProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setEnum($propertyName) error: ${it.message}") }
    }

    override fun setBoolean(propertyName: String, value: Boolean) {
        runCatching { handle.setBooleanProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setBoolean($propertyName) error: ${it.message}") }
    }

    override fun setNumber(propertyName: String, value: Float) {
        runCatching { handle.setNumberProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setNumber($propertyName) error: ${it.message}") }
    }

    override suspend fun setImageFromUrl(propertyName: String, url: String) {
        runCatching {
            val bytes = loadRiveImageBytes(url)
                ?: error("Failed to fetch image bytes from $url")

            handle.setImageProperty(propertyName, bytes)

        }.onFailure {
            println("[IOSRiveController] setImageFromUrl($propertyName) error: ${it.message}")
        }
    }


    override fun fireTrigger(triggerName: String) {
        runCatching { handle.fireTrigger(triggerName) }
            .onFailure { println("[IOSRiveController] fireTrigger($triggerName) error: ${it.message}") }
    }

    override fun destroy() {
        handle.destroy()
    }
}


// iosMain
suspend fun loadRiveImageBytes(url: String): ByteArray? {
    val nsUrl = NSURL.URLWithString(url) ?: run {
        println("[RiveImage] Invalid URL: $url")
        return null
    }

    return suspendCancellableCoroutine { continuation ->
        val session = NSURLSession.sharedSession

        val task = session.dataTaskWithURL(nsUrl) { data, response, error ->
            when {
                error != null -> {
                    println("[RiveImage] URLSession error: ${error.localizedDescription}")
                    continuation.resume(null)
                }
                data == null -> {
                    println("[RiveImage] No data received from $url")
                    continuation.resume(null)
                }
                else -> {

                    continuation.resume(data.toByteArray())
                }
            }
        }

        continuation.invokeOnCancellation { task.cancel() }
        task.resume() // NSURLSessionTask.resume() — starts the request
    }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    return ByteArray(length.toInt()).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}