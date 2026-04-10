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
    var controller by remember("MatchMaking") { mutableStateOf<RiveController?>(null) }
    var showPopup by remember { mutableStateOf(true) }
    var showRive by remember { mutableStateOf(true) }

//    val popupAlpha by animateFloatAsState(
//        targetValue = if (dismissing) 0f else 1f,
//        animationSpec = tween(durationMillis = 800, delayMillis = 0)
//    )
    val scope = rememberCoroutineScope()

    val eventCallback = remember {
        object : RiveEventCallback {
            override fun onTriggerAnimation(animationName: String) {}
        }
    }

    if(showRive) {

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
        ) {

            val density = LocalDensity.current

            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            LaunchedEffect(controller, screenWidthPx, screenHeightPx) {

                val position = RivePosition(
                    xFraction = 1f,
                    yFraction = 0f,
                    offsetX = with(density) { (-100).dp.toPx() },
                    offsetY = with(density) { (75).dp.toPx() }
                )

                val centerPosition = RivePosition(
                    xFraction = 0.5f,
                    yFraction = 0.4f
                )

                val (startX, startY) = mapToRive(
                    position = centerPosition,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    artboardWidth = RiveProps.Kicker.ARTBOARD_WIDTH,
                    artboardHeight = RiveProps.Kicker.ARTBOARD_HEIGHT
                )

                val (riveX, riveY) = mapToRive(
                    position = position,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    artboardWidth = RiveProps.Kicker.ARTBOARD_WIDTH,
                    artboardHeight = RiveProps.Kicker.ARTBOARD_HEIGHT
                )

                controller?.let { ctrl ->


                    ctrl.setEnum(
                        "${RiveProps.Kicker.Item.VIEWMODEL_NAME}/${RiveProps.Kicker.Item.SELECTION}",
                        RiveProps.Kicker.Item.Values.COIN
                    )

                    ctrl.setNumber(
                        "${RiveProps.Kicker.Coin.VIEWMODEL_NAME}/${RiveProps.Kicker.Coin.COIN_START_VALUE}",
                        1300f
                    )

                    // =========================
                    // SPREAD
                    // =========================
                    ctrl.setNumber(RiveProps.Kicker.Particle.MAX_X, 150f)
                    ctrl.setNumber(RiveProps.Kicker.Particle.MAX_Y, 150f)


                    // =========================
                    // PARTICLES → FROM CENTER
                    // =========================
                    ctrl.setNumber(RiveProps.Kicker.Particle.START_X, startX)
                    ctrl.setNumber(RiveProps.Kicker.Particle.START_Y, startY)

                    // =========================
                    // SCALE
                    // =========================
                    ctrl.setNumber(RiveProps.Kicker.Particle.MAX_SCALE, 200f)
                    ctrl.setNumber(RiveProps.Kicker.Particle.MIN_SCALE, 100f)


                    // HUD position
                    ctrl.setNumber(RiveProps.Kicker.Hud.X, riveX)
                    ctrl.setNumber(RiveProps.Kicker.Hud.Y, riveY)

                    ctrl.setNumber(RiveProps.Kicker.Particle.END_X, riveX)
                    ctrl.setNumber(RiveProps.Kicker.Particle.END_Y, riveY)


                    // =========================
                    // PARTICLES → TO HUD
                    // =========================
                    ctrl.setNumber(RiveProps.Kicker.Particle.END_X, riveX)
                    ctrl.setNumber(RiveProps.Kicker.Particle.END_Y, riveY)

//                    delay(2000)

                }
            }

            // Rive view
            RiveComponent(
                modifier = Modifier.fillMaxSize(),
                resourceName = RiveConfigs.Files.KICKER,
                instanceKey = "Kicker",
                viewModelName = RiveProps.Kicker.VIEWMODEL_NAME,
                onControllerReady = { controller = it },
                eventCallback = eventCallback,
                fit = RiveFit.CONTAIN,
                alignment = RiveAlignment.CENTER,
            )
        }

    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showPopup) {

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("You've earned rewards!", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Collect your coins", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                controller?.let { ctrl ->
                                    ctrl.setNumber(RiveProps.Kicker.ITEM_COUNT, 20f)
                                    ctrl.setNumber(RiveProps.Kicker.PRICE_VALUE, 15f)

                                    ctrl.fireTrigger(
                                        "${RiveProps.Kicker.Button.VIEWMODEL_NAME}/${RiveProps.Kicker.Button.PRESSED}"
                                    )

                                }

                                showPopup = false

                                scope.launch {
                                    delay(3000)
                                    showRive = false
                                }
                            }
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }

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