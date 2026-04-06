package com.arjun.rivecmptesting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveController
import com.arjun.core.rive.RiveItemConfigs.confettiConfig
import com.arjun.core.rive.RiveProps
import com.arjun.core.rive.utils.RiveFit

@Composable
fun ConfettiAnimation() {

    var controller by remember("MatchMaking") { mutableStateOf<RiveController?>(null) }

    LaunchedEffect(controller) {
        controller?.fireTrigger(RiveProps.Confetti.FIRE)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Testing")
        }

        RiveComponent(
            modifier = Modifier.fillMaxSize(),
            resourceName = RiveConfigs.Files.CONFETTI,
            config = confettiConfig(),
            instanceKey = "Confetti",
            viewModelName = RiveProps.Confetti.VIEWMODEL_NAME,
            fit = RiveFit.CONTAIN,
            onControllerReady = { controller = it }
        )
    }

}
