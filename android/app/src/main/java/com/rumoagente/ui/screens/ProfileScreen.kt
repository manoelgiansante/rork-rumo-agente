package com.rumoagente.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.UserProfile
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors

@Composable
fun ProfileScreen(
    onNavigateToSubscription: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var displayName by remember { mutableStateOf("Carregando...") }
    var displayEmail by remember { mutableStateOf("") }
    var displayPlan by remember { mutableStateOf("Free") }
    var displayCredits by remember { mutableStateOf(0) }
    var displayInitial by remember { mutableStateOf("?") }

    LaunchedEffect(Unit) {
        val token = RetrofitInstance.authToken ?: return@LaunchedEffect
        try {
            val response = RetrofitInstance.supabaseApi.getProfiles(
                authorization = "Bearer $token"
            )
            if (response.isSuccessful) {
                val profile = response.body()?.firstOrNull()
                if (profile != null) {
                    displayName = profile.displayName ?: profile.email.substringBefore("@")
                    displayEmail = profile.email
                    displayPlan = profile.plan.lowercase()
                        .replaceFirstChar { it.uppercase() }
                    displayCredits = profile.credits
                    displayInitial = displayName.firstOrNull()?.uppercase() ?: "?"
                }
            }
        } catch (_: Exception) { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Avatar + info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(RumoColors.Accent, RumoColors.AccentBlue)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayInitial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = displayEmail,
                color = RumoColors.SubtleText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Plan card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(1.dp, RumoColors.Accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = RumoColors.Accent.copy(alpha = 0.08f)
            ),
            onClick = onNavigateToSubscription
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(RumoColors.Accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = RumoColors.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Plano $displayPlan",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "$displayCredits creditos restantes",
                            color = RumoColors.Accent,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = RumoColors.SubtleText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings section
        SettingsSection(title = "Configuracoes") {
            SettingsRow(
                icon = Icons.Default.Language,
                title = "Idioma",
                subtitle = "Portugues (BR)",
                iconColor = RumoColors.AccentBlue
            )
            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Notificacoes",
                subtitle = "Ativadas",
                iconColor = RumoColors.Orange
            )
            SettingsRow(
                icon = Icons.Default.DarkMode,
                title = "Aparencia",
                subtitle = "Escuro",
                iconColor = RumoColors.Purple
            )
            SettingsRow(
                icon = Icons.Default.Lock,
                title = "Privacidade",
                subtitle = "Gerenciar dados",
                iconColor = RumoColors.Pink
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Support section
        SettingsSection(title = "Suporte") {
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Help,
                title = "Central de ajuda",
                iconColor = RumoColors.AccentBlue
            )
            SettingsRow(
                icon = Icons.Default.Feedback,
                title = "Enviar feedback",
                iconColor = RumoColors.Accent
            )
            SettingsRow(
                icon = Icons.Default.Info,
                title = "Sobre o app",
                subtitle = "Versao 1.0.0",
                iconColor = RumoColors.SubtleText
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout button
        Button(
            onClick = {
                RetrofitInstance.authToken = null
                onLogout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RumoColors.Red.copy(alpha = 0.12f)
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = RumoColors.Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sair da conta",
                color = RumoColors.Red,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            color = RumoColors.SubtleText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(14.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconColor: Color = RumoColors.AccentBlue,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = RumoColors.SubtleText,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = RumoColors.SubtleText.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ProfileScreenPreview() {
    RumoAgenteTheme {
        ProfileScreen()
    }
}
