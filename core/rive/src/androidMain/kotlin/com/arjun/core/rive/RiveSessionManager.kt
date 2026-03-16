package com.arjun.core.rive

import androidx.compose.runtime.staticCompositionLocalOf
import app.rive.ViewModelInstance
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import java.util.concurrent.ConcurrentHashMap

// RiveSessionManager.kt — lives as a DI singleton or in your Application class
// Owns the worker + file cache. ViewModel holds a reference to this.

val LocalRiveRuntime = staticCompositionLocalOf<RiveRuntime?> { null }

class RiveRuntime(
    private val fileManager: AndroidRiveFileManager
) {

    private val instanceCache = ConcurrentHashMap<String, ViewModelInstance>()

    fun getInstance(
        resourceName: String,
        instanceKey: String,
        viewModelName: String,
    ): ViewModelInstance {

        val key = "$resourceName-$instanceKey"

        val file = fileManager.getFile(resourceName)
            ?: error("Rive file not loaded: $resourceName")

        return instanceCache.getOrPut(key) {

            val source =
                ViewModelInstanceSource.Default(
                    ViewModelSource.Named(viewModelName)
            )

            ViewModelInstance.fromFile(file, source)
        }
    }

    fun clear() {
        instanceCache.values.forEach { runCatching { it.close() } }
        instanceCache.clear()
    }
}
