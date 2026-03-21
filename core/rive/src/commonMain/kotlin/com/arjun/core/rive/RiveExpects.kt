package com.arjun.core.rive


import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier


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
)


enum class RiveAlignment {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}


/**
 * Defines how Rive animations should be fitted within their container.
 * This enum provides a unified API across Android and iOS platforms.
 */
enum class RiveFit {
    /**
     * Scale the animation to fill the entire container, potentially cropping content.
     */
    FILL,

    /**
     * Scale the animation to fit within the container, maintaining aspect ratio.
     * This ensures the entire animation is visible within the bounds.
     */
    CONTAIN,

    /**
     * Scale the animation to cover the entire container, maintaining aspect ratio.
     * This may crop parts of the animation to fill the container.
     */
    COVER,

    /**
     * Scale the animation to fit the width of the container.
     */
    FIT_WIDTH,

    /**
     * Scale the animation to fit the height of the container.
     */
    FIT_HEIGHT,

    /**
     * Do not scale the animation, use its original size.
     */
    NONE
}