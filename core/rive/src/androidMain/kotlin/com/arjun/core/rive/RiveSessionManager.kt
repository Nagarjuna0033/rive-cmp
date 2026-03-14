package com.arjun.core.rive

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ProcessLifecycleOwner
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.ViewModelInstance
import app.rive.core.RiveWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.rive.Result
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import app.rive.core.AudioEngine
import app.rive.core.ComposeFrameTicker
import kotlinx.coroutines.SupervisorJob
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
