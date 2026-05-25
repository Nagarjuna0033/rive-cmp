package com.arjun.rivecmptesting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


val ColorNeutralWhite = Color(0xFFFFFFFF)
val ColorGrayGray50 = Color(0xFFFDFCFD)
val ColorGrayGray900 = Color(0xFF261A2C)
val ColorBlueBlue900 = Color(0xFF000E3D)
val ColorGrayGray600 = Color(0xFF6E6672)


val ColorTextPrimary = ColorGrayGray900
val ColorBrandLight = ColorGrayGray50
val ColorSurfaceLow = ColorGrayGray50
val ColorTextSecondary = ColorGrayGray600

// ── Separate data per tab ─────────────────────────────────────────────────────

val homeTabData = listOf(
    ContestServerModel(
        id = 1,
        name = "Mega Ludo",
        playerCount = "2.4K",
        prizePool = "5000",
        currencyType = CurrencyType.Coin,
        cta = ContestCta(text = "Play Now", locked = false, cash = false, coin = true, isNew = true)
    ),
    ContestServerModel(
        id = 2,
        name = "Battle Chess",
        playerCount = "1.1K",
        prizePool = "1200",
        currencyType = CurrencyType.Cash,
        cta = ContestCta(text = "Locked", locked = true, cash = false, coin = true, isNew = true)
    )
)

val contestsTabData = listOf(
    ContestServerModel(
        id = 3,
        name = "Snake Arena",
        playerCount = "3.2K",
        prizePool = "8000",
        currencyType = CurrencyType.Cash,
        cta = ContestCta(text = "Join Now", locked = false, cash = true, coin = false, isNew = false)
    ),
    ContestServerModel(
        id = 4,
        name = "Ludo Masters",
        playerCount = "980",
        prizePool = "2500",
        currencyType = CurrencyType.Coin,
        cta = ContestCta(text = "Play", locked = false, cash = false, coin = true, isNew = true)
    )
)


// ── ContestLargeCards now accepts data + a tab tag for unique Rive keys ────────

@Composable
fun ContestLargeCards(
    contests: List<ContestServerModel> = homeTabData,
    // tabTag makes every Rive instanceKey unique across tabs.
    // Without this, tab 0 and tab 1 would produce the same key for the
    // same contest id, causing Rive to reuse the same animation instance.
    tabTag: String = "home"
) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = contests,
                key = { "$tabTag-${it.id}" }
            ) { contest ->
                LargeCardContent(
                    name = contest.name,
                    gameNameKey = contest.name,
                    playerCount = contest.playerCount,
                    prizePool = contest.prizePool,
                    currencyType = contest.currencyType,
                    onClick = {},
                ) {
                    // Pass tabTag so the Rive instanceKey is unique per tab
                    PrimaryButton(
                        contest = contest.toContestItem(),
                        tabTag = tabTag
                    )
                }
            }
        }
    }
}

// ── PrimaryButton accepts tabTag to build a unique instanceKey ────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(
    contest: ContestItem,
    tabTag: String = "home"
) {
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Button(
        content = {Text(text = "Button")},
        onClick = {},
        modifier = Modifier
            .height(52.dp)
            .width(205.dp),
        shapes = ButtonShapes(shape = RoundedCornerShape(16.dp), pressedShape = RoundedCornerShape(16.dp)),
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

    val backgroundGradient = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    Column(
        modifier = Modifier
            .width(332.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))

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

//            ComposeButton(
//                modifier = Modifier
//                    .height(52.dp)
//                    .width(205.dp),
//                text = "text",
//                animationType = ButtonAnimationType.COMBO
//            )
        }
    }
}