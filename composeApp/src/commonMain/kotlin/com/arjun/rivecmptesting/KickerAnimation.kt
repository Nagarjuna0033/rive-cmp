package com.arjun.rivecmptesting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arjun.core.rive.RewardsParams
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveController
import com.arjun.core.rive.RiveEventCallback
import com.arjun.core.rive.RiveItemConfigs.rewards
import com.arjun.core.rive.RiveProps
import com.arjun.core.rive.utils.RiveAlignment
import com.arjun.core.rive.utils.RiveFit
import kotlinx.coroutines.delay

@Composable
fun KickerAnimation() {
    var controller by remember("MatchMaking") { mutableStateOf<RiveController?>(null) }
    var showPopup by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
//    val popupAlpha by animateFloatAsState(
//        targetValue = if (dismissing) 0f else 1f,
//        animationSpec = tween(durationMillis = 800, delayMillis = 0)
//    )

    val space = remember {
        detectCoordinateSpace(
            artboardWidth = 1252f,
            artboardHeight = 2223f,
            detectedHudX = 4597f,
            detectedHudY = 31f
        )
    }

    val adapter = remember { RiveCoordinateAdapter(space) }

    val config = rewards(
        RewardsParams(
            price = 15f,
            itemType = RiveProps.Kicker.Item.Values.COIN,


            coinStart = 1300f,
            gemStart = 3000f,

            // =========================
            // HUD → TOP RIGHT
            // =========================
            hudX = 300f,   // right edge
            hudY = 0f,     // top

            hudTextX = 300f,
            hudTextY = 0f,

            // =========================
            // PARTICLES → FROM CENTER
            // =========================
            particleStartX = 150f,   // width / 2
            particleStartY = 150f,   // height / 2

            // =========================
            // PARTICLES → TO HUD
            // =========================
            particleEndX = 300f,     // SAME AS HUD
            particleEndY = 0f,       // SAME AS HUD

            // =========================
            // SPREAD
            // =========================
            particleMaxX = 120f,
            particleMaxY = 150f,

            // =========================
            // SCALE
            // =========================
            particleMaxScale = 260f,
            particleMinScale = 80f,
            itemCount = 0f
        )
    )

    LaunchedEffect(controller) {
        controller?.let { ctrl ->

            // =========================
            // SPREAD
            // =========================
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MAX_X}", 3f)
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MAX_Y}", 3f)


            // =========================
            // PARTICLES → FROM CENTER
            // =========================
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.START_X}", 0f)
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.START_Y}", 0f)

            // =========================
            // SCALE
            // =========================
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MAX_SCALE}", 20f)
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MIN_SCALE}", 10f)

            // =========================
            // HUD → TOP RIGHT
            // =========================
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.X}", 300f)
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.Y}", 150f)

            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.TEXT_X}", 300f)
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.TEXT_Y}", 150f)



            // =========================
            // ROOT (Rewards)
            // =========================
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.PRICE_VALUE}", 15f)
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.ITEM_COUNT}", 20f)



            // =========================
            // PARTICLES → TO HUD
            // =========================
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.END_X}", 0f)
            ctrl.setNumber("${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.END_Y}", 150f)


            // =========================
            // ENUM (Item Type)
            // =========================
            ctrl.setEnum(
                "${RiveProps.Kicker.Item.VIEWMODEL_NAME}/${RiveProps.Kicker.Item.SELECTION}",
                RiveProps.Kicker.Item.Values.GEM
            )

            delay(2000)


            ctrl.fireTrigger(
                "${RiveProps.Kicker.Button.VIEWMODEL_NAME}/${RiveProps.Kicker.Button.PRESSED}"
            )
        }
    }

    val eventCallback = remember {
        object : RiveEventCallback {
            override fun onTriggerAnimation(animationName: String) {}
        }
    }

    // Remove popup + coins after flight completes
    LaunchedEffect(dismissing) {
        if (dismissing) {
            // let coins fly while popup fades
            delay(4000L)
            showPopup = false
        }
    }

//    if(showPopup) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RiveComponent(
            modifier = Modifier
                .size(500.dp).background(Color.Black.copy(0.5f)),
//                .fillMaxWidth()
//                .fillMaxHeight()
//                .zIndex(1f),
            resourceName = RiveConfigs.Files.KICKER,
            instanceKey = "Kicker",
//            config = config,
            viewModelName = RiveProps.Kicker.VIEWMODEL_NAME,
            eventCallback = eventCallback,
            onControllerReady = { controller = it },
            fit = RiveFit.CONTAIN,
            alignment = RiveAlignment.CENTER,
            batched = true,
        )
    }

//    }


    Box(modifier = Modifier.fillMaxSize()) {
        if (showPopup) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
//                    .graphicsLayer { alpha = popupAlpha }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .zIndex(3f),
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
                            enabled = !dismissing,
                            onClick = {
                                dismissing = true
                                controller?.fireTrigger(
                                    "${RiveProps.Kicker.Button.VIEWMODEL_NAME}/${RiveProps.Kicker.Button.PRESSED}"
                                )
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

data class RiveCoordinateSpace(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float
)

fun detectCoordinateSpace(
    artboardWidth: Float,
    artboardHeight: Float,
    detectedHudX: Float,
    detectedHudY: Float
): RiveCoordinateSpace {
    return RiveCoordinateSpace(
        offsetX = detectedHudX,   // THIS IS YOUR 4597
        offsetY = detectedHudY,
        width = artboardWidth,
        height = artboardHeight
    )
}

class RiveCoordinateAdapter(
    private val space: RiveCoordinateSpace
) {

    fun toRiveX(x: Float): Float {
        return space.offsetX + x
    }

    fun toRiveY(y: Float): Float {
        return space.offsetY + y
    }

    fun centerX(): Float = space.offsetX + space.width / 2f
    fun centerY(): Float = space.offsetY + space.height / 2f

    fun topRightX(padding: Float = 0f): Float =
        space.offsetX + space.width - padding

    fun topRightY(padding: Float = 0f): Float =
        space.offsetY + padding
}