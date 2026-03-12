package com.arjun.rivecmptesting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveProvider
import org.jetbrains.compose.resources.painterResource

import rivecmptesting.composeapp.generated.resources.Res
import rivecmptesting.composeapp.generated.resources.compose_multiplatform


@Composable
@Preview
fun App() {

    MaterialTheme {

        RiveProvider(
            configs = RiveConfigs.allConfigs,
            loadingContent = {
                CircularProgressIndicator()
            },
            errorContent = { msg ->
                Text("Rive error: $msg")
            }
        ) {

            var showContent by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .safeContentPadding()
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContestLargeCards()
            }
        }
    }
}
