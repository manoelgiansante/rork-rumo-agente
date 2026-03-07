package com.rumoagente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rumoagente.data.repository.AuthRepository
import com.rumoagente.ui.screens.AuthScreen
import com.rumoagente.ui.screens.MainScreen
import com.rumoagente.ui.screens.SubscriptionScreen
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RumoAgenteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RumoColors.DarkBg
                ) {
                    val navController = rememberNavController()
                    val authRepository = remember { AuthRepository(this@MainActivity) }
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val hasSession = authRepository.checkSession()
                        startDestination = if (hasSession) "main" else "auth"
                    }

                    if (startDestination == null) {
                        // Show loading while checking session
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = RumoColors.Accent
                            )
                        }
                        return@Surface
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!
                    ) {
                        composable("auth") {
                            AuthScreen(
                                onAuthSuccess = {
                                    navController.navigate("main") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }
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
                        composable("subscription") {
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
