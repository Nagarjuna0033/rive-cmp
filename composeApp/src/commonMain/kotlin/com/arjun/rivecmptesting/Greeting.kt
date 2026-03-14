package com.arjun.rivecmptesting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveController
import com.arjun.core.rive.RiveEventCallback
import com.arjun.core.rive.RiveItemConfigs
import com.arjun.core.rive.RiveProps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


val ColorNeutralWhite = Color(0xFFFFFFFF)
val ColorGrayGray50 = Color(0xFFFDFCFD)
val ColorGrayGray900 = Color(0xFF261A2C)
val ColorBlueBlue900 = Color(0xFF000E3D)
val ColorGrayGray600 = Color(0xFF6E6672)


val ColorTextPrimary = ColorGrayGray900
val ColorBrandLight = ColorGrayGray50
val ColorSurfaceLow = ColorGrayGray50
val ColorTextSecondary = ColorGrayGray600


@Composable
fun PrimaryButton(contest: ContestItem) {
    var controller by remember { mutableStateOf<RiveController?>(null) }

    val scope = rememberCoroutineScope()
    RiveComponent(
        resourceName = RiveConfigs.Files.CONTEST_BUTTON,
        width = 150,
        height = 75,
        modifier = Modifier
            .clickable { controller?.fireTrigger("Press") },
        config = RiveItemConfigs.contestButton(
            buttonText = contest.name,
            showCash = contest.isCash,
            showCoin = contest.isCoin,
            showLock = contest.isLocked,
            isNew = contest.isNew,
        ),
        instanceKey = contest.id.toString(),
        viewModelName = "Button",
        eventCallback = object : RiveEventCallback {
            override fun onTriggerAnimation(animationName: String) {
                println("animationName = [$animationName]")
                scope.launch {

                    controller?.setString(
                        RiveProps.ContestButton.BUTTON_TEXT,
                        "Loading..."
                    )

                    delay(2000)

                    controller?.setString(RiveProps.ContestButton.BUTTON_TEXT, contest.name)
                }
            }
        },
        onControllerReady = { controller = it }
    )
}

data class ContestServerModel(
    val id: Int,
    val name: String,
    val playerCount: String,
    val prizePool: String,
    val currencyType: CurrencyType,
    val cta: ContestCta
)

data class ContestCta(
    val text: String,
    val locked: Boolean,
    val cash: Boolean,
    val coin: Boolean,
    val isNew: Boolean
)


data class ContestItem(
    val id: Int,
    val name: String,
    val isCash: Boolean = false,
    val isCoin: Boolean = false,
    val isLocked: Boolean = false,
    val isNew: Boolean = false
)

val dummyServerData = listOf(
    ContestServerModel(
        id = 1,
        name = "Mega Ludo",
        playerCount = "2.4K",
        prizePool = "5000",
        currencyType = CurrencyType.Coin,
        cta = ContestCta(
            text = "Play Now",
            locked = false,
            cash = false,
            coin = true,
            isNew = true
        )
    ),
    ContestServerModel(
        id = 2,
        name = "Battle Chess",
        playerCount = "1.1K",
        prizePool = "1200",
        currencyType = CurrencyType.Cash,
        cta = ContestCta(
            text = "Locked",
            locked = true,
            cash = false,
            coin = true,
            isNew = true
        )
    )
)

fun ContestServerModel.toContestItem(): ContestItem {
    return ContestItem(
        id = id,
        name = cta.text,
        isCash = cta.cash,
        isCoin = cta.coin,
        isLocked = cta.locked,
        isNew = cta.isNew
    )
}

@Composable
fun ContestLargeCards() {

    var contests by remember {
        mutableStateOf(dummyServerData)
    }

    Column (modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally){

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            items(
                items = contests,
                key = { it.id }
            ) { contest ->
                LargeCardContent(
                    name = contest.name,
                    gameNameKey = contest.name,
                    playerCount = contest.playerCount,
                    prizePool = contest.prizePool,
                    currencyType = contest.currencyType,
                    onClick = {},
                ) {
                    PrimaryButton(contest.toContestItem())
                }

            }
        }
    }
}

enum class CurrencyType(val value: String) {
    Cash("cash"),
    Coin("coin"),
    Xp("xp"),
    UNKNOWN("");

    companion object {
        fun fromString(value: String?): CurrencyType {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

@Stable
sealed class PrimaryButtonVariant {
    data object Yellow : PrimaryButtonVariant()
    data object LightYello : PrimaryButtonVariant()
    data object Green : PrimaryButtonVariant()
}



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargeCardContent(
    name: String,
    gameNameKey: String,
    imageUrl: String? = null,
    playerCount: String? = null,
    prizePool: String? = null,
    gradientColors: List<Color> = listOf(ColorNeutralWhite, ColorNeutralWhite),
    currencyType: CurrencyType? = null,
    onClick: () -> Unit = {},
    ctaContent: @Composable () -> Unit
) {

    val backgroundGradient by remember(gradientColors) {
        mutableStateOf(Brush.verticalGradient(colors = gradientColors))
    }

    Column(
        modifier = Modifier
            .width(332.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {

        Box(modifier = Modifier.fillMaxWidth().height(154.dp)) {

            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp
                )
            ) {

                Box(modifier = Modifier.fillMaxSize()) {

                    if (imageUrl != null) {
                        // image loader here
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ColorGrayGray50)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.4f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ColorBlueBlue900
                                    )
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {

                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .background(ColorSurfaceLow, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = playerCount ?: "0",
                            color = ColorTextPrimary,
                            style = MaterialTheme.typography.labelSmallEmphasized
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = name,
                    color = ColorBrandLight,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = backgroundGradient,
                    shape = RoundedCornerShape(
                        bottomStart = 8.dp,
                        bottomEnd = 8.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = "Prize Pool",
                    color = ColorTextSecondary,
                    style = MaterialTheme.typography.labelSmallEmphasized
                )

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = prizePool ?: "0",
                        color = ColorTextPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            ctaContent()
        }
    }
}