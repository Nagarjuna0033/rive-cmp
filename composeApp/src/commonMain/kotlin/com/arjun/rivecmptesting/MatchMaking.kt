package com.arjun.rivecmptesting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.arjun.core.rive.MatchMakingParams
import com.arjun.core.rive.PrimaryButtonParams
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveController
import com.arjun.core.rive.RiveEventCallback
import com.arjun.core.rive.RiveItemConfigs
import com.arjun.core.rive.RiveProps
import com.arjun.core.rive.utils.RiveFit
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MatchMakingScreen(onBack: () -> Unit = {}) {

    BackHandler { onBack() }

    var isMatchFound by remember { mutableStateOf(false) }
    var currentUserName by remember { mutableStateOf("Current User") }
    var opponentUserName by remember { mutableStateOf("Finding Players...") }
    var controller by remember("MatchMaking") { mutableStateOf<RiveController?>(null) }


    val params = remember( isMatchFound, currentUserName, opponentUserName) {
        MatchMakingParams(
            currentUserName = currentUserName,
            opponentUserName = opponentUserName,
            isMatchFound = isMatchFound
        )
    }

    LaunchedEffect(controller) {
        controller?.let { ctrl ->

            currentUserName = "Arjun"
            ctrl.setImageFromUrl(RiveProps.MatchMaking.CURRENT_USER_PICTURE, "https://media.bebetta.in/public/ProfilePictures/AV_1.webp")
            ctrl.fireTrigger(RiveProps.MatchMaking.INTRO)

            delay(3000)

            opponentUserName = "Nag"
            ctrl.setImageFromUrl(RiveProps.MatchMaking.OPPONENT_USER_PICTURE, "https://media.bebetta.in/public/ProfilePictures/AV_1.webp")

            isMatchFound = true

            delay(2000)

            ctrl.fireTrigger(RiveProps.MatchMaking.OUTRO)
        }
    }

    val config = remember(params) {
        RiveItemConfigs.matchMaking(params)
    }

    val eventCallback = remember(isMatchFound) {
        object : RiveEventCallback {
            override fun onTriggerAnimation(animationName: String) {

            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        RiveComponent(
            resourceName = RiveConfigs.Files.MATCHMAKING,
            instanceKey = "MatchMaking",
            config = config,
            viewModelName = RiveProps.MatchMaking.VIEWMODEL_NAME,
            eventCallback = eventCallback,
            onControllerReady = { controller = it },
            fit = RiveFit.COVER,
        )
    }

}


