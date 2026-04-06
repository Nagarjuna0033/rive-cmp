package com.arjun.rivecmptesting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp
import com.arjun.core.rive.RiveComponent
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveController
import com.arjun.core.rive.RiveItemConfig
import com.arjun.core.rive.RiveItemConfigs.confettiConfig
import com.arjun.core.rive.RiveItemConfigs.contestBottomBarConfig
import com.arjun.core.rive.RiveItemConfigs.dealsBottomBarConfig
import com.arjun.core.rive.RiveItemConfigs.homeBottomBarConfig
import com.arjun.core.rive.RiveItemConfigs.storeBottomBarConfig
import com.arjun.core.rive.RiveProps
import com.arjun.core.rive.RiveProvider
import com.arjun.core.rive.utils.RiveFit
import io.github.alexzhirkevich.compottie.LottieComposition


// CompositionLocal for preloaded Lottie compositions
val LocalLottieCompositions = compositionLocalOf<LottieCompositions> { 
    LottieCompositions(null) 
}

data class LottieCompositions(
    val contestIcon: LottieComposition?
)

@Composable
fun App() {
    MaterialTheme {
        // Preload Lottie compositions at the top level for instant display
        val contestComposition = rememberLottieCompositionFromFile("contest_icon_animation.lottie")
        
        val lottieCompositions = remember(contestComposition) {
            LottieCompositions(contestIcon = contestComposition)
        }
        
        CompositionLocalProvider(LocalLottieCompositions provides lottieCompositions) {
            RiveProvider(
                configs = RiveConfigs.allConfigs,
                loadingContent = { CircularProgressIndicator() },
                errorContent = { msg -> Text("Rive error: $msg") }
            ) {
                AppNav()
            }
        }
    }
}

// ── Routes ────────────────────────────────────────────────────────────────────

private object Routes {
    const val MAIN          = "main"
    const val NOTIFICATIONS = "notifications"

    const val COMPOSE_ANIMATIONS = "compose_animations"
}

// ── Root nav host ─────────────────────────────────────────────────────────────

@Composable
fun AppNav() {
    var currentRoute by remember { mutableStateOf(Routes.MAIN) }

    // With batched rendering, a single batch surface persists — no need to keep
    // MainScreen always in composition. Items re-register instantly on re-entry.
    when (currentRoute) {
        Routes.MAIN -> MainScreen(
            onNotificationClick = { currentRoute = Routes.NOTIFICATIONS }
        )
        Routes.NOTIFICATIONS -> NotificationsScreen(
            onBack = { currentRoute = Routes.MAIN }
        )
    }
}

// ── Main screen (tabs live here) ──────────────────────────────────────────────

// Toggle between Rive and Lottie navigation bars for memory comparison
private const val USE_RIVE_NAV = true  // Set to false to use Lottie

@Composable
private fun MainScreen(onNotificationClick: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var useRiveNav by remember { mutableStateOf(USE_RIVE_NAV) }

//    if (selectedTab == 3) {
//        MatchMakingScreen(onBack = { selectedTab = 0 })
//        return
//    }

    Scaffold(
        topBar = {
//            AppTopBar(onNotificationClick = onNotificationClick)
        },

        bottomBar = {
//            NavigationBar {
//                NavigationBarItem(
//                    selected = selectedTab == 0,
//                    onClick = { selectedTab = 0 },
//                    icon = { Text("🏠") },
//                    label = { Text("Home") }
//                )
//                NavigationBarItem(
//                    selected = selectedTab == 1,
//                    onClick = { selectedTab = 1 },
//                    icon = { Text("🏆") },
//                    label = { Text("Contests") },
//                            NavigationBarItem(
//                            selected = selectedTab == 2,
//                    onClick = { selectedTab = 2 },
//                    icon = { Text("🤖") },
//                    label = { Text("Compose") }
//                )
//                NavigationBarItem(
//                    selected = selectedTab == 2,
//                    onClick = { selectedTab = 2 },
//                    icon = { Text("🤖") },
//                    label = { Text("Compose") }
//                )
//                NavigationBarItem(
//                    selected = selectedTab == 3,
//                    onClick = { selectedTab = 3 },
//                    icon = { Text("🎯") },
//                    label = { Text("PvP") }
//                )
//            }

            RiveNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onToggleNav = { useRiveNav = false }
            )

//            if (useRiveNav) {
//                RiveNavigationBar(
//                    selectedTab = selectedTab,
//                    onTabSelected = { selectedTab = it },
//                    onToggleNav = { useRiveNav = false }
//                )
//            } else {
//                LottieNavigationBar(
//                    selectedTab = selectedTab,
//                    onTabSelected = { selectedTab = it },
//                    onToggleNav = { useRiveNav = true }
//                )
//            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ContestLargeCards(contests = homeTabData, tabTag = "home")
                1 -> KickerAnimation()
                2 -> ComposeAnimations()
                4 -> StoreShadeAnimation()
                5 -> ConfettiAnimation()

//                0 -> {}
//                1 -> {}
//                2 -> {}
//                3 -> {}
//                4 -> TestingText(if (useRiveNav) "Rive Animation" else "Lottie Testing")
            }
        }
    }
}


@Composable
fun StoreShadeAnimation() {

    var controller by remember("MatchMaking") { mutableStateOf<RiveController?>(null) }

    LaunchedEffect(controller) {
        controller?.fireTrigger(RiveProps.Store.FIRE)
    }

//    Box(
//        modifier = Modifier.background(Color.Red),
//        contentAlignment = Alignment.TopStart,
//    ) {
        RiveComponent(
            modifier = Modifier.height(75.dp),
            resourceName = RiveConfigs.Files.STORE,
            config = confettiConfig(),
            instanceKey = "Confetti",
            viewModelName = RiveProps.Store.VIEWMODEL_NAME,
            fit = RiveFit.FIT_WIDTH,
            onControllerReady = { controller = it }
        )
//    }
}

@Composable
fun TestingText(text: String = "Rive Animation") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
        )
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(onNotificationClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "Rive",
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
            IconButton(onClick = onNotificationClick) {
                Text(text = "🔔")
            }
        }
    )
}

// ── Notifications screen ──────────────────────────────────────────────────────
val notificationsTabData = listOf(
    ContestServerModel(
        id = 10,
        name = "Flash Ludo",
        playerCount = "5.1K",
        prizePool = "15000",
        currencyType = CurrencyType.Cash,
        cta = ContestCta(text = "Play Now", locked = false, cash = true, coin = false, isNew = true)
    ),
    ContestServerModel(
        id = 11,
        name = "Chess Blitz",
        playerCount = "720",
        prizePool = "3000",
        currencyType = CurrencyType.Coin,
        cta = ContestCta(text = "Join", locked = false, cash = false, coin = true, isNew = false)
    ),
    ContestServerModel(
        id = 12,
        name = "Snake Rush",
        playerCount = "2.8K",
        prizePool = "6500",
        currencyType = CurrencyType.Cash,
        cta = ContestCta(text = "Locked", locked = true, cash = true, coin = false, isNew = false)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Featured Contests",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            ContestLargeCards(
                contests = notificationsTabData,
                tabTag = "notifications"
            )
        }
    }
}

// ── Lottie Navigation Bar ─────────────────────────────────────────────────────

/**
 * Custom Navigation Bar that uses Lottie animations for tab icons.
 * Uses preloaded compositions from CompositionLocal for instant display.
 */
@Composable
private fun LottieNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onToggleNav: () -> Unit
) {
    // Get preloaded composition from CompositionLocal (loaded at App level)
    val lottieCompositions = LocalLottieCompositions.current
    val contestComposition = lottieCompositions.contestIcon

    Surface(
        color = NavigationBarDefaults.containerColor,
        tonalElevation = NavigationBarDefaults.Elevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home tab with Lottie animation
            LottieNavigationBarItem(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                composition = contestComposition,
                label = "Home"
            )

            // Kicker tab with Lottie animation
            LottieNavigationBarItem(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                composition = contestComposition,
                label = "Kicker"
            )

            // Compose tab with Lottie animation
            LottieNavigationBarItem(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                composition = contestComposition,
                label = "Compose"
            )

            // PvP tab with Lottie animation
            LottieNavigationBarItem(
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                composition = contestComposition,
                label = "PvP"
            )

            // Minimal tab with Lottie animation
            LottieNavigationBarItem(
                selected = selectedTab == 4,
                onClick = {
                    onTabSelected(4)
                    onToggleNav()
                },
                composition = contestComposition,
                label = "Store"
            )
        }
    }
}

// ── Rive Navigation Bar ───────────────────────────────────────────────────────

/**
 * Custom Navigation Bar that uses Rive animations for tab icons.
 * Uses contest_nav.riv with fireTrigger for instant animation on click.
 */
@Composable
fun RiveNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onToggleNav: () -> Unit
) {
    Surface(
        color = NavigationBarDefaults.containerColor,
        tonalElevation = NavigationBarDefaults.Elevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            orderedNavScreens.forEachIndexed { index, screen ->
                val config = riveConfigMap[screen] ?: return@forEachIndexed
                RiveNavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    label = screen.title,
                    instanceKey = config.instanceKey,
                    fileName = config.file,
                    riveConfig = config.config
                )
            }
        }
    }
}


sealed class DashboardScreens(
    val route: String,
    val title: String,
) {

    data object HomeScreen :
        DashboardScreens(
            "Home",
            "Home"
        )

    data object TournamentScreen :
        DashboardScreens(
            "Tour",
            "Tour"
        )

    data object StoreScreen :
        DashboardScreens(
            "Store",
            "Store"
        )
    data object DealsScreen :
        DashboardScreens(
            "Deals",
            "Deals"
        )
}
data class BottomNavRiveConfig(
    val file: String,
    val config: RiveItemConfig,
    val viewModel: String,
    val instanceKey: String
)

val riveConfigMap = mapOf(
    DashboardScreens.HomeScreen to BottomNavRiveConfig(
        file = RiveConfigs.Files.HOME_BOTTOM_NAV,
        config = homeBottomBarConfig(),
        viewModel = RiveProps.Common.VIEWMODEL_NAME,
        instanceKey = "nav_home"
    ),
    DashboardScreens.TournamentScreen to BottomNavRiveConfig(
        file = RiveConfigs.Files.TOURNAMENT_BOTTOM_NAV,
        config = contestBottomBarConfig(),
        viewModel = RiveProps.Common.VIEWMODEL_NAME,
        instanceKey = "nav_tournament"
    ),
    DashboardScreens.StoreScreen to BottomNavRiveConfig(
        file = RiveConfigs.Files.STORE_BOTTOM_NAV,
        config = storeBottomBarConfig(),
        viewModel = RiveProps.Common.VIEWMODEL_NAME,
        instanceKey = "nav_store"
    ),
    DashboardScreens.DealsScreen to BottomNavRiveConfig(
        file = RiveConfigs.Files.DEALS_BOTTOM_NAV,
        config = dealsBottomBarConfig(),
        viewModel = RiveProps.Common.VIEWMODEL_NAME,
        instanceKey = "nav_deals"
    )
)

// Ordered list drives the navigation bar — index == tab index
val orderedNavScreens = listOf(
    DashboardScreens.HomeScreen,
    DashboardScreens.TournamentScreen,
    DashboardScreens.StoreScreen,
    DashboardScreens.DealsScreen
)


