package com.arjun.rivecmptesting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveController
import com.arjun.core.rive.RiveEvent
import com.arjun.core.rive.RiveEventCallback
import com.arjun.core.rive.RiveItemConfigs


class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}




@Composable
fun ContestScreen(contests: List<ContestItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(contests) { contest ->
            ContestButton(contest = contest)
        }
    }
}

@Composable
fun ContestButton(contest: ContestItem) {
    var controller by remember { mutableStateOf<RiveController?>(null) }

    RiveComponent(
        resourceName = RiveConfigs.Files.CONTEST_BUTTON,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable { controller?.fireTrigger("Press") },
        config = RiveItemConfigs.contestButton(
            buttonText = contest.name,
            showCash   = contest.isCash,
            showCoin   = contest.isCoin,
            showLock   = contest.isLocked,
            isNew      = contest.isNew,
        ),
        eventCallback = object : RiveEventCallback {
            override fun onRiveEventReceived(event: RiveEvent) {
                println("Rive event: ${event.name}")
            }

            override fun onTriggerAnimation(animationName: String) {
                println("animationName = [${animationName}]")
            }


        },
        onControllerReady = { controller = it }
    )
}

data class ContestItem(
    val name: String,
    val isCash: Boolean = false,
    val isCoin: Boolean = false,
    val isLocked: Boolean = false,
    val isNew: Boolean = false,
)

