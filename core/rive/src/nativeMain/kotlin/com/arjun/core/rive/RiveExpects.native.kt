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
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.arjun.core.rive.utils.RiveAlignment
import com.arjun.core.rive.utils.RiveFit
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
    batched: Boolean,
) {
    val bridge = IOSRivePlatform.bridge ?: return

    val handle = remember(resourceName, instanceKey) {
        bridge.createHandle(resourceName, artboardName, stateMachineName)
    } ?: return

    val controller = remember(handle) { IOSRiveController(handle) }

    // Notify controller ready once per handle (not on every config change)
    LaunchedEffect(handle) {
        onControllerReady?.invoke(controller)
    }

    // Apply config SYNCHRONOUSLY during composition — no flash.
    // Same pattern as Android: remember(handle, config) { ... }
    @Suppress("RememberReturnType")
    remember(handle, config) {
        controller.applyConfig(config)
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
        properties = UIKitInteropProperties(
            isNativeAccessibilityEnabled = false,
            placedAsOverlay = true,
        ),
    )
}
