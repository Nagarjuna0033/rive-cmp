package com.arjun.core.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi

@Composable
actual fun RiveProvider(
    configs: List<RiveFileConfig>,
    loadingContent: @Composable (() -> Unit),
    errorContent: @Composable ((String) -> Unit),
    content: @Composable (() -> Unit)
) {
    val bridge = IOSRivePlatform.bridge
    if (bridge == null) {
        errorContent("IOSRivePlatform.bridge not configured")
        return
    }

    val fileManager = remember(bridge) { IOSRiveFileManager(bridge) }
    var loadState by remember { mutableStateOf<RiveLoadState>(RiveLoadState.Loading) }

    LaunchedEffect(configs) {
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

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun RiveComponent(
    resourceName: String,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?
) {
    val bridge = IOSRivePlatform.bridge ?: return

    val handle = remember(resourceName) {
        bridge.createHandle(resourceName)
    } ?: return

    val controller = remember(handle) { IOSRiveController(handle) }

    LaunchedEffect(handle, config) {
        controller.applyConfig(config)
    }

    LaunchedEffect(controller) {
        onControllerReady?.invoke(controller)
    }

    DisposableEffect(handle) {
        onDispose { handle.destroy() }
    }

    UIKitView(
        factory = { handle.getUIView() },
        modifier = modifier
    )
}
