package com.rumoagente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rumoagente.data.repository.AuthRepository
import com.rumoagente.ui.screens.AuthScreen
import com.rumoagente.ui.screens.MainScreen
import com.rumoagente.ui.screens.OnboardingScreen
import com.rumoagente.ui.screens.OnboardingPrefs
import com.rumoagente.ui.screens.SubscriptionScreen
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════════════════════
//  MainActivity — matches iOS RumoAgenteApp exactly:
//  1. Splash screen
//  2. Onboarding check (hasOnboarded)
//  3. Auth check (isAuthenticated)
//  4. Route to appropriate screen
//  5. Edge-to-edge + dark theme forced
// ════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ splash screen API
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Edge-to-edge (matches iOS full-screen layout)
        enableEdgeToEdge()

        // Keep splash visible while determining start destination
        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }

        setContent {
            // Force dark theme (matches iOS .preferredColorScheme(.dark))
            RumoAgenteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RumoColors.DarkBg
                ) {
                    val navController = rememberNavController()
                    val authRepository = remember { AuthRepository(this@MainActivity) }
                    var startDestination by remember { mutableStateOf<String?>(null) }
                    var showSplashContent by remember { mutableStateOf(true) }

                    // Matches iOS .task { await supabase.checkSession() }
                    LaunchedEffect(Unit) {
                        val hasOnboarded = OnboardingPrefs.hasOnboarded(this@MainActivity)
                        if (!hasOnboarded) {
                            startDestination = "onboarding"
                        } else {
                            val hasSession = authRepository.checkSession()
                            startDestination = if (hasSession) "main" else "auth"
                        }
                        // Brief delay for smooth transition
                        delay(300)
                        isReady = true
                        showSplashContent = false
                    }

                    // Splash content while loading (matches iOS Group with animations)
                    AnimatedVisibility(
                        visible = showSplashContent && startDestination == null,
                        exit = fadeOut(animationSpec = tween(300))
                    ) {
                        SplashContent()
                    }

                    // Main navigation (matches iOS Group switching between views)
                    if (startDestination != null && !showSplashContent) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination!!,
                            enterTransition = {
                                fadeIn(animationSpec = tween(300))
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(300))
                            }
                        ) {
                            // Matches iOS: if !hasOnboarded → OnboardingView
                            composable("onboarding") {
                                OnboardingScreen(
                                    onOnboardingComplete = {
                                        navController.navigate("auth") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // Matches iOS: if !supabase.isAuthenticated → AuthView
                            composable("auth") {
                                AuthScreen(
                                    onAuthSuccess = {
                                        navController.navigate("main") {
                                            popUpTo("auth") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // Matches iOS: ContentView (TabView with 5 tabs)
                            composable("main") {
                                MainScreen(
                                    onNavigateToSubscription = {
                                        navController.navigate("subscription")
                                    },
                                    onLogout = {
                                        navController.navigate("auth") {
                                            popUpTo("main") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // Matches iOS: SubscriptionView (presented as sheet)
                            composable(
                                "subscription",
                                enterTransition = {
                                    slideInVertically(
                                        initialOffsetY = { it },
                                        animationSpec = tween(350)
                                    ) + fadeIn(animationSpec = tween(350))
                                },
                                exitTransition = {
                                    slideOutVertically(
                                        targetOffsetY = { it },
                                        animationSpec = tween(350)
                                    ) + fadeOut(animationSpec = tween(350))
                                }
                            ) {
                                SubscriptionScreen(
                                    onDismiss = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Splash content — shown while determining start route
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App icon/logo
            Text(
                text = "Rumo Agente",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = RumoColors.Accent,
                strokeWidth = 3.dp
            )
        }
    }
}
