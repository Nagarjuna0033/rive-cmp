package com.arjun.core.rive


import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.arjun.core.rive.utils.RiveAlignment
import com.arjun.core.rive.utils.RiveFit


val LocalRiveFileManager: ProvidableCompositionLocal<RiveFileManager?> =
    staticCompositionLocalOf { null }



// ── Expect: RiveProvider ───────────────────────────────────────────────
// Wraps app root — preloads all rive files before rendering children
@Composable
expect fun RiveProvider(
    configs: List<RiveFileConfig>,
    loadingContent: @Composable () -> Unit = {},
    errorContent: @Composable (String) -> Unit = {},
    content: @Composable () -> Unit
)

// ── Expect: RiveComponent ──────────────────────────────────────────────
@Composable
expect fun RiveComponent(
    resourceName: String,
    instanceKey: String,
    viewModelName: String,
//    height: Int?,
//    width: Int?,
    modifier: Modifier = Modifier,
    config: RiveItemConfig = RiveItemConfig(),
    eventCallback: RiveEventCallback? = null,
    onControllerReady: ((RiveController) -> Unit)? = null,
    alignment: RiveAlignment = RiveAlignment.CENTER,
    autoPlay: Boolean = true,
    artboardName: String? = null,
    fit: RiveFit = RiveFit.CONTAIN,
    stateMachineName: String? = null,
    batched: Boolean = true,
    background: Boolean = false,
)
