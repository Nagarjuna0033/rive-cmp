package com.arjun.core.rive

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.rive.Rive
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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

    remember(context) { RiveCore.init(context) }
    val riveWorker = rememberRiveWorker()

    val fileManager = remember(riveWorker) {
        AndroidRiveFileManager(context, riveWorker)
    }

    var loadState by remember { mutableStateOf<RiveLoadState>(RiveLoadState.Loading) }

    LaunchedEffect(configs) {
        RiveCore.init(context)
        loadState = fileManager.preloadAll(configs)
    }

    CompositionLocalProvider(
        LocalRiveFileManager provides fileManager
    ) {
        when (val state = loadState) {
            is RiveLoadState.Loading -> loadingContent()
            is RiveLoadState.Error -> errorContent(state.message)
            is RiveLoadState.Success -> content()
            is RiveLoadState.Idle -> loadingContent()
        }
    }
}

// ── Android actual: RiveComponent ─────────────────────────────────────
@Composable
actual fun RiveComponent(
    resourceName: String,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?
) {

    val fileManager = LocalRiveFileManager.current as? AndroidRiveFileManager

    val riveFile = remember(resourceName, fileManager) {
        fileManager?.getFile(resourceName)
    } ?: return

    val vmi = rememberViewModelInstance(
        file = riveFile,
    )

    val controller = remember(vmi) { AndroidRiveController(vmi) }

    LaunchedEffect(vmi, config) {
        controller.applyConfig(config)
        onControllerReady?.invoke(controller)
        config.triggers.forEach { trigger ->
            launch {
                vmi.getTriggerFlow(trigger)
                    .collect {
                        eventCallback?.onTriggerAnimation(trigger)
                    }
            }
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }

    Rive(
        file = riveFile,
        modifier = modifier,
        viewModelInstance = vmi
    )
}