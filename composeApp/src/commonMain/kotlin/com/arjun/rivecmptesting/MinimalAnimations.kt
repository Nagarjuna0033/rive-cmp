package com.arjun.rivecmptesting

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveEventCallback
import com.arjun.core.rive.RiveItemConfig
import io.github.alexzhirkevich.compottie.DotLottie
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieAnimatable
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import rivecmptesting.composeapp.generated.resources.Res

/**
 * A navigation bar item that uses Lottie animation for the icon.
 * Accepts a preloaded composition for instant animation.
 */
@Composable
fun LottieNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    composition: LottieComposition?,
    label: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 50.dp,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = LocalContentColor.current.copy(alpha = 0.6f)
) {
    // Stable per-item state; increment to restart animation
    val playTick = rememberSaveable(label) { mutableIntStateOf(0) }
    val animatable = rememberLottieAnimatable()

    LaunchedEffect(playTick.intValue, composition) {
        val comp = composition ?: return@LaunchedEffect
        if (playTick.intValue > 0) {
            animatable.snapTo(comp, 0f)
            animatable.animate(
                composition = comp,
                iterations = 1,
                continueFromPreviousAnimate = false,
                speed = 1f
            )
        }
    }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                playTick.intValue++
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = rememberLottiePainter(
                composition = composition,
                progress = { animatable.value },
                clipToCompositionBounds = false
            ),
            modifier = Modifier.size(iconSize),
            contentDescription = label,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) selectedColor else unselectedColor
        )
    }
}

/**
 * Helper to preload a Lottie composition from resources
 */
@Composable
fun rememberLottieCompositionFromFile(fileName: String): LottieComposition? {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.DotLottie(
            archive = Res.readBytes("files/$fileName")
        )
    }
    return composition
}

/**
 * Original MinimalAnimation composable - kept for standalone testing
 */
@Composable
fun MinimalAnimation() {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.DotLottie(
            archive = Res.readBytes("files/contest_icon_animation.lottie")
        )
    }

    val animatable = rememberLottieAnimatable()
    val scope = rememberCoroutineScope()
    val animationJob = remember { mutableStateOf<Job?>(null) }

    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { animatable.value },
            clipToCompositionBounds = false
        ),
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                composition?.let { comp ->
                    animationJob.value?.cancel()
                    animationJob.value = scope.launch {
                        animatable.animate(
                            composition = comp,
                            initialProgress = 0f,
                            continueFromPreviousAnimate = false,
                            speed = 1f,
                            iterations = 1
                        )
                    }
                }
            },
        contentDescription = "Like",
    )

}

/**
 * A navigation bar item that uses Rive animation for the icon.
 * Uses contest_nav.riv and handles click via Rive event callback.
 */
@Composable
fun RowScope.AddItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    instanceKey: String,
    fileName: String,
    riveConfig: RiveItemConfig,
    modifier: Modifier = Modifier,
    iconSize: Dp = 50.dp,
    viewModelName: String = "ViewModel1",
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = LocalContentColor.current.copy(alpha = 0.6f)
) {
    // Store onClick in a ref that persists across recompositions
    val onClickRef = remember { mutableStateOf(onClick) }
    onClickRef.value = onClick  // Always update to latest

    // Event callback to handle Rive click events - use stable key
    val eventCallback = remember(instanceKey) {
        object : RiveEventCallback {
            override fun onTriggerAnimation(animationName: String) {
                onClickRef.value()
            }
        }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RiveComponent(
            resourceName = fileName,
            config = riveConfig,
            instanceKey = instanceKey,
            viewModelName = viewModelName,
            modifier = Modifier.size(iconSize),
            eventCallback = eventCallback,
            autoPlay = true,
            batched = true,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) selectedColor else unselectedColor
        )
    }
}

