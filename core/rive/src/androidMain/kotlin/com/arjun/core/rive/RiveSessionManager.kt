package com.arjun.core.rive

import androidx.compose.runtime.staticCompositionLocalOf
import app.rive.ViewModelInstance
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import java.util.concurrent.ConcurrentHashMap

//val LocalRiveRuntime = staticCompositionLocalOf<RiveRuntime?> { null }
//
//class RiveRuntime(
//    private val fileManager: AndroidRiveFileManager
//) {
//
//    private val instanceCache = ConcurrentHashMap<String, ViewModelInstance>()
//
//    fun getInstance(
//        resourceName: String,
//        instanceKey: String,
//        viewModelName: String,
//    ): ViewModelInstance {
//
//        val key = "$resourceName-$instanceKey"
//
//        val file = fileManager.getFile(resourceName)
//            ?: error("Rive file not loaded: $resourceName. Ensure RiveProvider has finished loading before using RiveComponent.")
//
//        // computeIfAbsent is atomic on ConcurrentHashMap — prevents duplicate VMI creation
//        return instanceCache.computeIfAbsent(key) {
//
//            val source =
//                ViewModelInstanceSource.Default(
//                    ViewModelSource.Named(viewModelName)
//            )
//
//            ViewModelInstance.fromFile(file, source)
//        }
//    }
//
//    fun clear() {
//        instanceCache.values.forEach { runCatching { it.close() } }
//        instanceCache.clear()
//    }
//}
