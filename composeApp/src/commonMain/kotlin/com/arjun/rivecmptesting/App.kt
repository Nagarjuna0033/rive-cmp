package com.arjun.rivecmptesting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.arjun.core.rive.RiveConfigs
import com.arjun.core.rive.RiveProvider
import com.arjun.core.rive.RiveRetainer


@Composable
fun App() {
    MaterialTheme {
        RiveProvider(
            configs = RiveConfigs.allConfigs,
            loadingContent = { CircularProgressIndicator() },
            errorContent = { msg -> Text("Rive error: $msg") }
        ) {
            AppNav()
        }
    }
}

// ── Routes ────────────────────────────────────────────────────────────────────

private object Routes {
    const val MAIN          = "main"
    const val NOTIFICATIONS = "notifications"
}

// ── Root nav host ─────────────────────────────────────────────────────────────

@Composable
fun AppNav() {
    var currentRoute by remember { mutableStateOf(Routes.MAIN) }

    // MainScreen is ALWAYS in composition — never destroyed on navigation.
    // This preserves all TextureViews and Rive surfaces across screen transitions.
    Box {
        MainScreen(
            onNotificationClick = { currentRoute = Routes.NOTIFICATIONS }
        )

        if (currentRoute == Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { currentRoute = Routes.MAIN }
            )
        }
    }
}

// ── Main screen (tabs live here) ──────────────────────────────────────────────

@Composable
private fun MainScreen(onNotificationClick: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            AppTopBar(onNotificationClick = onNotificationClick)
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🏠") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("🏆") },
                    label = { Text("Contests") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            RiveRetainer(
                activeTab = selectedTab,
                maxRetained = 3,
                tabs = listOf(
                    { _ -> ContestLargeCards(contests = homeTabData, tabTag = "home") },
                    { _ -> ContestLargeCards(contests = contestsTabData, tabTag = "contests") },
                )
            )
        }
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
