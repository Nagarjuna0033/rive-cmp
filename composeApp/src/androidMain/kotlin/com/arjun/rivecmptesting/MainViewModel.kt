package com.arjun.rivecmptesting

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import app.rive.RiveFile
import app.rive.ViewModelInstance
import app.rive.core.RiveWorker
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.runtime.kotlin.RiveAnimationView


class MainViewModel : ViewModel() {
//    private val worker = RiveWorker()
//    private val riveFileCache = mutableMapOf<String, RiveFile>()
//    private val viewModelCache = mutableMapOf<String, ViewModelInstance>()
//
//
//    /**
//     * Load and cache a Rive file from raw resources or bytes.
//     * Subsequent calls return the cached instance immediately.
//     */
//    fun getCachedRiveFile(
//        resourceName: String,
//        inputStream: FileHandle
//    ): RiveFile {
//        return riveFileCache.getOrPut(resourceName) {
//            RiveFile(inputStream, worker)
//        }
//    }
//
//
//
//    /**
//     * Get or create a cached ViewModelInstance for a specific artboard/state machine.
//     * This avoids recreating the runtime object when navigating between screens.
//     */
//    fun getCachedViewModelInstance(
//        cacheKey: String,
//        riveFile: RiveFile,
//        artboardName: String,
//        stateMachineName: String
//    ): ViewModelInstance {
//        return viewModelCache.getOrPut(cacheKey) {
//            val artboard = riveFile.artboard(artboardName) ?: return@getOrPut null
//            artboard.stateMachineInstance(stateMachineName, worker)
//        } ?: throw IllegalStateException("Failed to create ViewModelInstance")
//    }
//
//
//    fun pollWorker() {
//        worker.pollMessages()
//    }


}