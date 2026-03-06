package com.arjun.core.rive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun RiveProvider(
    configs: List<RiveFileConfig>,
    loadingContent: @Composable (() -> Unit),
    errorContent: @Composable ((String) -> Unit),
    content: @Composable (() -> Unit)
) {
}

@Composable
actual fun RiveComponent(
    resourceName: String,
    modifier: Modifier,
    config: RiveItemConfig,
    eventCallback: RiveEventCallback?,
    onControllerReady: ((RiveController) -> Unit)?
) {
}