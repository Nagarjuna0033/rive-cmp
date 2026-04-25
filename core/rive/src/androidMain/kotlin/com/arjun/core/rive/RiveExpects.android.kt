package com.arjun.core.rive

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.rive.Fit
import app.rive.ImageAsset
import app.rive.Rive
import app.rive.RiveBatchItem
import app.rive.RiveBatchSurface
import app.rive.core.CommandQueue
import app.rive.rememberArtboard
import app.rive.rememberRiveWorker
import app.rive.rememberStateMachine
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstance
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.arjun.core.rive.utils.RiveAlignment
import com.arjun.core.rive.utils.RiveFit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.ByteArrayOutputStream
import app.rive.runtime.kotlin.core.Rive as RiveCore

@SuppressLint("RememberReturnType")
@Composable
actual fun RiveProvider(
    configs: List<RiveFileConfig>,
    loadingContent: @Composable () -> Unit,
    errorContent: @Composable (String) -> Unit,
    content: @Composable () -> Unit
) {

    val context = LocalContext.current

    remember(context) {
        RiveCore.init(context)
    }

    val riveWorker = rememberRiveWorker()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Start worker polling immediately and keep it running
//    LaunchedEffect(riveWorker) {
//        riveWorker.beginPolling(lifecycle)
//    }

    val currentLifecycle by rememberUpdatedState(lifecycle)
    LaunchedEffect(riveWorker) {
        riveWorker.beginPolling(currentLifecycle)
    }

    val fileManager = remember(riveWorker) {
        AndroidRiveFileManager(context, riveWorker)
    }

//    val runtime = remember(fileManager) {
//        RiveRuntime(fileManager)
//    }

    var loadState by remember { mutableStateOf<RiveLoadState>(RiveLoadState.Loading) }

    LaunchedEffect(configs) {
        loadState = try {
            withContext(Dispatchers.Default) {
                fileManager.preloadAll(configs)
            }
        } catch (e: Exception) {
            RiveLoadState.Error(e.message ?: "Unknown error during preload")
        }
    }

//    DisposableEffect(runtime) {
//        onDispose {
//            runtime.clear()
//            fileManager.clearAll()
//        }
//    }

    CompositionLocalProvider(
        LocalRiveFileManager provides fileManager,
//        LocalRiveRuntime provides runtime
    ) {
        when (val state = loadState) {
            is RiveLoadState.Loading -> loadingContent()
            is RiveLoadState.Error -> errorContent(state.message)
            is RiveLoadState.Success -> {
                RiveBatchSurface(
                    riveWorker = riveWorker,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                ) {
                    content()
                }
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
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?,
    alignment: RiveAlignment,
    autoPlay: Boolean,
    artboardName: String?,
    fit: RiveFit,
    stateMachineName: String?,
    batched: Boolean,
) {

    val fileManager = LocalRiveFileManager.current as? AndroidRiveFileManager


//    val runtime = LocalRiveRuntime.current ?: return

    val riveFile = remember(resourceName, fileManager) {
        fileManager?.getFile(resourceName)
    } ?: return

//    val vmi = remember(resourceName, instanceKey) {
//        runtime.getInstance(
//            resourceName = resourceName,
//            instanceKey = instanceKey,
//            viewModelName = viewModelName,
//        )
//    }

    val vmiSource = remember(viewModelName) {
        if (viewModelName.isNotBlank()) {
            ViewModelSource.Named(viewModelName).defaultInstance()
        } else {
            null // let rememberViewModelInstance fall back to default
        }
    }
    val vmi = rememberViewModelInstance(riveFile, source = vmiSource)



    val controller = remember(vmi, fileManager) {
        AndroidRiveController(
            vmi = vmi,
            fileManager = fileManager ?: return@remember AndroidRiveController(vmi, null),
        )
    }

    // Create artboard/SM at bridge level so we can bind VMI before onControllerReady.
    // This ensures triggers fired in onControllerReady reach a bound SM.
    val artboard = rememberArtboard(riveFile)
    val stateMachine = rememberStateMachine(artboard)

    // Bind VMI to SM, THEN call onControllerReady.
    LaunchedEffect(stateMachine.stateMachineHandle, vmi) {
        riveFile.riveWorker.bindViewModelInstance(
            stateMachine.stateMachineHandle, vmi.instanceHandle
        )
        Log.d("Rive/Component", "VMI bound to SM, calling onControllerReady")
        onControllerReady?.invoke(controller)

        // TEST: re-fire trigger every 2s to rule out timing vs rendering issue
        while (true) {
            kotlinx.coroutines.delay(2000)
            vmi.fireTrigger("Button/Pressed")
            Log.d("Rive/Component", "TEST: re-fired Button/Pressed trigger")
        }
    }

    // Apply config synchronously during composition — before the first render frame.
    @SuppressLint("RememberReturnType")
    remember(vmi, config) {
        controller.applyConfig(config)
    }

    // Trigger flows
    LaunchedEffect(vmi) {
        config.triggers.forEach { trigger ->
            launch {
                vmi.getTriggerFlow(trigger)
                    .collect {
                        eventCallback?.onTriggerAnimation(trigger)
                    }
            }
        }
    }

    val riveFit = when (fit) {
        RiveFit.CONTAIN -> Fit.Contain()
        RiveFit.COVER -> Fit.Cover()
        RiveFit.FILL -> Fit.Fill
        RiveFit.FIT_WIDTH -> Fit.FitWidth()
        RiveFit.FIT_HEIGHT -> Fit.FitHeight()
        RiveFit.NONE -> Fit.None()
        RiveFit.SCALE_DOWN -> Fit.ScaleDown()
        RiveFit.LAYOUT -> Fit.Layout()
    }

    DisposableEffect(controller) {
        onDispose {
            controller.destroy()
        }
    }

    Log.d("Rive/Component", "RiveComponent — resource=$resourceName, batched=$batched, file=${riveFile.fileHandle}")

    if (batched) {
        RiveBatchItem(
            file = riveFile,
            artboard = artboard,
            stateMachine = stateMachine,
            modifier = modifier,
            viewModelInstance = vmi,
            fit = riveFit,
        )
    } else {
        val riveWorker = fileManager?.riveWorker ?: return
        RiveBatchSurface(
            riveWorker = riveWorker,
            modifier = modifier,
        ) {
            RiveBatchItem(
                file = riveFile,
                artboard = artboard,
                stateMachine = stateMachine,
                viewModelInstance = vmi,
                fit = riveFit,
            )
        }
    }
}

