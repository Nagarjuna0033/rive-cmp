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
    var showPopup by remember { mutableStateOf(true) }
    var dismissing by remember { mutableStateOf(false) }
//    val popupAlpha by animateFloatAsState(
//        targetValue = if (dismissing) 0f else 1f,
//        animationSpec = tween(durationMillis = 800, delayMillis = 0)
//    )

    val config = rewards(
        RewardsParams(
            price = 15f,
            itemType = RiveProps.Kicker.ItemSelectionValues.GEM,
            buttonText = "Continue",
            lives = 5f,
            energy = 50f,
            coinStart = 1300f,
            gemStart = 3000f,
        )
    )

    LaunchedEffect(controller) {
        controller?.setNumber(RiveProps.Kicker.COIN_ITEM_COUNT, 20f)
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

    if(showPopup) {
        RiveComponent(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .zIndex(1f),
            resourceName = RiveConfigs.Files.KICKER,
            instanceKey = "Kicker",
            config = config,
            viewModelName = RiveProps.Kicker.VIEWMODEL_NAME,
            eventCallback = eventCallback,
            onControllerReady = { controller = it },
            fit = RiveFit.CONTAIN,
            alignment = RiveAlignment.TOP_CENTER,
            batched = false,
        )
    }


    Box(modifier = Modifier.fillMaxSize()) {
        if (showPopup) {
            // Coins + popup together so coins stay visible until we dispose everything




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
                                    "${RiveProps.Button.VIEWMODEL_NAME}/${RiveProps.Button.PRESSED}"
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

//fun Modifier.passClicksThrough(): Modifier = this.pointerInput(Unit) {
//    awaitPointerEventScope {
//        while (true) {
//            val event = awaitPointerEvent(PointerEventPass.Initial)
//            // Don't consume — let events pass through to layers below
//            event.changes.forEach { it /* not consumed */ }
//        }
//    }
//}