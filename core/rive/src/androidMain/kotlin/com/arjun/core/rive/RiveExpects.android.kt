package com.arjun.core.rive

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
import app.rive.runtime.kotlin.core.Rive as RiveCore
import app.rive.Result

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

    val riveWorker = rememberRiveWorker()

    val context = LocalContext.current

    val resId = remember(resourceName) {
        context.resources.getIdentifier(resourceName, "raw", context.packageName)
    }

    val riveFileResult = rememberRiveFile(
        source = RiveFileSource.RawRes.from(resId),
        riveWorker = riveWorker
    )

    if (riveFileResult !is Result.Success) return

    val riveFile = riveFileResult.value

    val vmi = rememberViewModelInstance(
        file = riveFile,
    )

    val controller = remember(vmi) { AndroidRiveController(vmi) }

    LaunchedEffect(riveFile) {
        val vmNames = riveFile.getViewModelNames()
        println("[RiveComponent] ViewModels: $vmNames")

        vmNames.forEach { vmName ->
            val props = riveFile.getViewModelProperties(vmName)
            println("[RiveComponent] VM[$vmName] properties: $props")

            val instances = riveFile.getViewModelInstanceNames(vmName)
            println("[RiveComponent] VM[$vmName] instances: $instances")
        }

        val artboards = riveFile.getArtboardNames()
        println("[RiveComponent] Artboards: $artboards")
    }

    LaunchedEffect(vmi, config) {
        controller.applyConfig(config)
        println("@@@@@@@@@@ $config")
    }

    LaunchedEffect(controller) {
        onControllerReady?.invoke(controller)
    }

    Rive(
        file = riveFile,
        modifier = modifier,
        viewModelInstance = vmi
    )
}