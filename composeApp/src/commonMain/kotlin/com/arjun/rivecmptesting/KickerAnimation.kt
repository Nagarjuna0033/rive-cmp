package com.arjun.rivecmptesting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveController
import com.arjun.core.rive.RiveEventCallback
import com.arjun.core.rive.RiveProps
import com.arjun.core.rive.utils.RiveAlignment
import com.arjun.core.rive.utils.RiveFit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun KickerAnimation() {

    var controller by remember { mutableStateOf<RiveController?>(null) }

    // TEST: Using Box instead of Dialog to isolate EGL context issue
    Box(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .size(1000.dp),
                    contentAlignment = Alignment.Center
                ) {

                    val density = LocalDensity.current
                    val width = with(density) { maxWidth.toPx() }
                    val height = with(density) { maxHeight.toPx() }

                    LaunchedEffect(controller, width, height) {

                        controller?.let { ctrl ->

                            ctrl.setEnum(
                                "${RiveProps.Kicker.Item.VIEWMODEL_NAME}/${RiveProps.Kicker.Item.SELECTION}",
                                RiveProps.Kicker.Item.Values.COIN
                            )

                            ctrl.setNumber(
                                "${RiveProps.Kicker.Coin.VIEWMODEL_NAME}/${RiveProps.Kicker.Coin.COIN_START_VALUE}",
                                1300f
                            )

                            ctrl.setNumber(RiveProps.Kicker.ITEM_COUNT, 20f)
                            ctrl.setNumber(RiveProps.Kicker.PRICE_VALUE, 15f)

                            // Spread
                            ctrl.setNumber(RiveProps.Kicker.Particle.MAX_X, 200f)
                            ctrl.setNumber(RiveProps.Kicker.Particle.MAX_Y, 100f)



                            ctrl.setNumber(RiveProps.Kicker.Particle.START_X, 500f)
                            ctrl.setNumber(RiveProps.Kicker.Particle.START_Y, 1500f)

                            ctrl.setNumber(RiveProps.Kicker.Particle.END_X, 500f)
                            ctrl.setNumber(RiveProps.Kicker.Particle.END_Y, 140f)

                            ctrl.setNumber(RiveProps.Kicker.Hud.X, 500f)
                            ctrl.setNumber(RiveProps.Kicker.Hud.Y, 140f)

                            ctrl.fireTrigger("${RiveProps.Kicker.Button.VIEWMODEL_NAME}/${RiveProps.Kicker.Button.PRESSED}")
                        }
                    }

                    RiveComponent(
                        modifier = Modifier.fillMaxSize(),
                        resourceName = RiveConfigs.Files.KICKER,
                        instanceKey = "Kicker",
                        viewModelName = RiveProps.Kicker.VIEWMODEL_NAME,
                        onControllerReady = { controller = it },
                        fit = RiveFit.COVER,
                        alignment = RiveAlignment.CENTER,
                        batched = false,
                    )
                }
    }
}

data class RivePosition(
    val xFraction: Float, // 0 → 1
    val yFraction: Float, // 0 → 1
    val offsetX: Float = 0f, // px
    val offsetY: Float = 0f
)


fun mapToRive(
    position: RivePosition,
    screenWidthPx: Float,
    screenHeightPx: Float,
    artboardWidth: Float,
    artboardHeight: Float
): Pair<Float, Float> {

    val scale = min(
        screenWidthPx / artboardWidth,
        screenHeightPx / artboardHeight
    )

    val offsetX = (screenWidthPx - artboardWidth * scale) / 2
    val offsetY = (screenHeightPx - artboardHeight * scale) / 2

    val targetX = screenWidthPx * position.xFraction + position.offsetX
    val targetY = screenHeightPx * position.yFraction + position.offsetY

    val riveX = (targetX - offsetX) / scale
    val riveY = (targetY - offsetY) / scale

    return riveX to riveY
}