package com.rumoagente.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.AgentTask
import com.rumoagente.data.models.UserProfile
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors

@Composable
fun DashboardScreen() {
    var userName by remember { mutableStateOf("Usuario") }
    var userInitial by remember { mutableStateOf("U") }
    var credits by remember { mutableStateOf(0) }
    var maxCredits by remember { mutableStateOf(10) }
    var tasks by remember { mutableStateOf<List<AgentTask>>(emptyList()) }

    LaunchedEffect(Unit) {
        val token = RetrofitInstance.authToken ?: return@LaunchedEffect
        try {
            val profileResponse = RetrofitInstance.supabaseApi.getProfiles(
                authorization = "Bearer $token"
            )
            if (profileResponse.isSuccessful) {
                val profiles = profileResponse.body()
                val profile = profiles?.firstOrNull()
                if (profile != null) {
                    userName = profile.displayName ?: profile.email.substringBefore("@")
                    userInitial = userName.firstOrNull()?.uppercase() ?: "U"
                    credits = profile.credits
                }
            }
        } catch (_: Exception) { }
        try {
            val tasksResponse = RetrofitInstance.supabaseApi.getAgentTasks(
                authorization = "Bearer $token"
            )
            if (tasksResponse.isSuccessful) {
                tasks = tasksResponse.body() ?: emptyList()
            }
        } catch (_: Exception) { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Greeting header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Ola, $userName",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bem-vindo ao Rumo Agente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RumoColors.SubtleText
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(RumoColors.Accent, RumoColors.AccentBlue)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userInitial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Agent status card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(RumoColors.Accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Agente IA",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Pronto",
                            color = RumoColors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RumoColors.Accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Online",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = RumoColors.Accent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Credits card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Creditos",
                    color = RumoColors.SubtleText,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$credits",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                        Text(
                            text = " / $maxCredits",
                            color = RumoColors.SubtleText,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Usados hoje",
                            color = RumoColors.SubtleText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "3",
                            color = RumoColors.AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (maxCredits > 0) credits.toFloat() / maxCredits.toFloat() else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = RumoColors.Accent,
                    trackColor = RumoColors.Accent.copy(alpha = 0.15f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick action buttons
        Text(
            text = "Acoes rapidas",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Chat,
                label = "Chat",
                color = RumoColors.Accent
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.DesktopWindows,
                label = "Tela",
                color = RumoColors.AccentBlue
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.AutoAwesome,
                label = "Tarefa",
                color = RumoColors.Purple
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Settings,
                label = "Config",
                color = RumoColors.Orange
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Recent tasks
        Text(
            text = "Tarefas recentes",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            // Empty state
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        tint = RumoColors.SubtleText,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nenhuma tarefa recente",
                        color = RumoColors.SubtleText,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Inicie uma conversa para comecar",
                        color = RumoColors.SubtleText.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            tasks.take(5).forEach { task ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = task.status.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                color = RumoColors.SubtleText,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "-${task.creditsUsed}",
                            color = RumoColors.AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg),
        onClick = { }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun DashboardScreenPreview() {
    RumoAgenteTheme {
        DashboardScreen()
    }
}
