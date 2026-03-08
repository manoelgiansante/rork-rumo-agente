package com.rumoagente.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors

// ════════════════════════════════════════════════════════════════════════
//  Bottom nav items — matches iOS ContentView tabs exactly:
//  Dashboard (square.grid.2x2.fill), Tela (desktopcomputer),
//  Chat (message.fill), Apps (square.grid.2x2), Perfil (person.fill)
// ════════════════════════════════════════════════════════════════════════

private data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    BottomNavItem("Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    BottomNavItem("Tela", Icons.Filled.DesktopWindows, Icons.Outlined.DesktopWindows),
    BottomNavItem("Chat", Icons.Filled.Chat, Icons.Outlined.Chat),
    BottomNavItem("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    BottomNavItem("Perfil", Icons.Filled.Person, Icons.Outlined.Person)
)

// ════════════════════════════════════════════════════════════════════════
//  MainScreen — matches iOS ContentView with TabView + 5 tabs
//  Green accent active tab, proper icons, navigation animations
// ════════════════════════════════════════════════════════════════════════

@Composable
fun MainScreen(
    onNavigateToSubscription: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = RumoColors.DarkBg,
        bottomBar = {
            NavigationBar(
                containerColor = RumoColors.CardBg,
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.selectedIcon
                                else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RumoColors.Accent,
                            selectedTextColor = RumoColors.Accent,
                            unselectedIconColor = RumoColors.SubtleText,
                            unselectedTextColor = RumoColors.SubtleText,
                            indicatorColor = RumoColors.Accent.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val context = LocalContext.current

            // Crossfade animation between tabs (matches iOS .animation(.snappy))
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 250),
                label = "tab_crossfade"
            ) { tab ->
                when (tab) {
                    0 -> DashboardScreen(
                        onNavigateToChat = { selectedTab = 2 },
                        onNavigateToScreen = { selectedTab = 1 },
                        onNavigateToApps = { selectedTab = 3 },
                        onNavigateToProfile = { selectedTab = 4 }
                    )
                    1 -> ScreenViewScreen()
                    2 -> ChatScreen()
                    3 -> AppsScreen(context = context)
                    4 -> ProfileScreen(
                        onNavigateToSubscription = onNavigateToSubscription,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun MainScreenPreview() {
    RumoAgenteTheme {
        MainScreen()
    }
}
