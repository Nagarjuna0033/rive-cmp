package com.arjun.core.rive

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.rive.Alignment
import app.rive.Fit
import app.rive.rememberRiveWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.rive.runtime.kotlin.core.Rive as RiveCore

@SuppressLint("RememberReturnType")
@Composable
actual fun RiveProvider(
    configs: List<RiveFileConfig>,
    loadingContent: @Composable () -> Unit,
    errorContent: @Composable (String) -> Unit,
    content: @Composable () -> Unit
) {

    val totalStart = remember { android.os.SystemClock.elapsedRealtime() }

    val context = LocalContext.current

    remember(context) {
        RivePerfLogger.measure("RiveCore.init") {
            RiveCore.init(context)
        }
    }

    val riveWorker = rememberRiveWorker()
    val lifecycle = LocalLifecycleOwner.current.lifecycle


    // Start worker polling immediately and keep it running
    LaunchedEffect(riveWorker) {
        riveWorker.beginPolling(lifecycle)
    }

    val fileManager = remember(riveWorker) {
        RivePerfLogger.measure("Create FileManager") {
            AndroidRiveFileManager(context, riveWorker)
        }
    }

    val runtime = remember(fileManager) {
        RivePerfLogger.measure("Create Runtime") {
            RiveRuntime(fileManager)
        }
    }

    var loadState by remember { mutableStateOf<RiveLoadState>(RiveLoadState.Loading) }

    LaunchedEffect(configs) {
        val preloadStart = android.os.SystemClock.elapsedRealtime()

        loadState = RivePerfLogger.measureSuspend("preloadAll") {
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                fileManager.preloadAll(configs)
            }
        }

        RivePerfLogger.log("TOTAL preload pipeline", preloadStart)
    }

    DisposableEffect(runtime) {
        onDispose {
            RivePerfLogger.measure("runtime.clear") {
                runtime.clear()
            }

            RivePerfLogger.measure("fileManager.clearAll") {
                fileManager.clearAll()
            }
        }
    }

    CompositionLocalProvider(
        LocalRiveFileManager provides fileManager,
        LocalRiveRuntime provides runtime
    ) {
        when (val state = loadState) {
            is RiveLoadState.Loading -> loadingContent()
            is RiveLoadState.Error -> errorContent(state.message)
            is RiveLoadState.Success -> {
                RivePerfLogger.log("TOTAL provider setup", totalStart)
                content()
            }
            is RiveLoadState.Idle -> loadingContent()
        }
    }
}

// ── Android actual: RiveComponent ─────────────────────────────────────
@Composable
actual fun RiveComponent(
    resourceName: String,
    instanceKey: String,
    viewModelName: String,
//    height: Int?,
//    width: Int?,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?,
    alignment: RiveAlignment,
    autoPlay: Boolean,
    artboardName: String?,
    fit: RiveFit,
    stateMachineName: String?,
) {

    val componentStart = remember { android.os.SystemClock.elapsedRealtime() }

    val fileManager = LocalRiveFileManager.current as? AndroidRiveFileManager
    val runtime = LocalRiveRuntime.current ?: return

    val riveFile = remember(resourceName, fileManager) {
        RivePerfLogger.measure("getFile: $resourceName") {
            fileManager?.getFile(resourceName)
        }
    } ?: return

    val vmi = remember(resourceName, instanceKey) {
        RivePerfLogger.measure("getInstance: $resourceName-$instanceKey") {
            runtime.getInstance(
                resourceName = resourceName,
                instanceKey = instanceKey,
                viewModelName = viewModelName,
            )
        }
    }

    val controller = remember(vmi) {
        RivePerfLogger.measure("Create Controller") {
            AndroidRiveController(vmi)
        }
    }

    LaunchedEffect(vmi) {
        onControllerReady?.invoke(controller)
    }

    // Config application timing
    LaunchedEffect(vmi, config) {
        RivePerfLogger.measure("Apply Config") {

            config.booleans.forEach { (k, v) ->
                controller.setBoolean(k, v)
            }

            config.strings.forEach { (k, v) ->
                controller.setString(k, v)
            }

            config.enums.forEach { (k, v) ->
                controller.setEnum(k, v)
            }

            config.numbers.forEach { (k, v) ->
                controller.setNumber(k, v)
            }
        }
    }

    // Trigger flows timing
    LaunchedEffect(vmi) {
        config.triggers.forEach { trigger ->
            launch {
                RivePerfLogger.measureSuspend("TriggerFlow: $trigger") {
                    vmi.getTriggerFlow(trigger)
                        .collect {
                            eventCallback?.onTriggerAnimation(trigger)
                        }
                }
            }
        }
    }

    LaunchedEffect(vmi) {
        val start = android.os.SystemClock.elapsedRealtime()

        withFrameNanos {
            RivePerfLogger.log("First frame render", start)
        }
    }

    PoolableRiveView(
        file = riveFile,
        viewModelInstance = vmi,
        fit = Fit.Contain(),
    )

    LaunchedEffect(Unit) {
        RivePerfLogger.log("TOTAL Component Load: $resourceName", componentStart)
    }
}


object RivePerfLogger {

    const val TAG = "RIVE_PERF"

    inline fun <T> measure(label: String, block: () -> T): T {
        val start = android.os.SystemClock.elapsedRealtime()
        val result = block()
        val end = android.os.SystemClock.elapsedRealtime()

        android.util.Log.d(TAG, "⏱ $label = ${end - start} ms")

        return result
    }

    suspend inline fun <T> measureSuspend(
        label: String,
        crossinline block: suspend () -> T
    ): T {
        val start = android.os.SystemClock.elapsedRealtime()
        val result = block()
        val end = android.os.SystemClock.elapsedRealtime()

        android.util.Log.d(TAG, "⏱ $label = ${end - start} ms")

        return result
    }

    fun log(label: String, start: Long) {
        val end = android.os.SystemClock.elapsedRealtime()
        android.util.Log.d(TAG, "⏱ $label = ${end - start} ms")
    }
}