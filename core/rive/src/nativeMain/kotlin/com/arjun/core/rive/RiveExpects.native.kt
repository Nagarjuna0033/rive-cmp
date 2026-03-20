package com.arjun.core.rive

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
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

    DisposableEffect(fileManager) {
        onDispose { fileManager.clearAll() }
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
    val bridge = IOSRivePlatform.bridge ?: return

    val handle = remember(resourceName, instanceKey) {
        bridge.createHandle(resourceName)
    } ?: return

    val controller = remember(handle) { IOSRiveController(handle) }

    // Notify controller ready once per handle (not on every config change)
    LaunchedEffect(handle) {
        onControllerReady?.invoke(controller)
    }

    // Apply all properties when config changes
    LaunchedEffect(handle, config) {
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

    // Trigger listeners in separate LaunchedEffect — matches Android pattern
    LaunchedEffect(handle) {
        config.triggers.forEach { trigger ->
            handle.addTriggerListener(trigger) {
                eventCallback?.onTriggerAnimation(trigger)
            }
        }
    }

    DisposableEffect(handle) {
        onDispose { handle.destroy() }
    }

    UIKitView(
        factory = { handle.getUIView() },
        modifier = modifier,
    )
}
