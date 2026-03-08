package com.rumoagente.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.AgentCommand
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.launch

data class UiAppItem(
    val name: String,
    val icon: ImageVector,
    val category: String,
    val status: UiAppStatus,
    val color: Color
)

enum class UiAppStatus(val label: String) {
    ACTIVE("Ativo"),
    AVAILABLE("Disponivel"),
    COMING_SOON("Em breve")
}

private val allApps = listOf(
    UiAppItem("Browser", Icons.Default.Language, "Navegacao", UiAppStatus.ACTIVE, Color(0xFF3B82F6)),
    UiAppItem("Terminal", Icons.Default.Terminal, "Desenvolvimento", UiAppStatus.ACTIVE, Color(0xFF34D399)),
    UiAppItem("Editor", Icons.Default.Edit, "Desenvolvimento", UiAppStatus.ACTIVE, Color(0xFF8B5CF6)),
    UiAppItem("Arquivos", Icons.Default.Folder, "Produtividade", UiAppStatus.ACTIVE, Color(0xFFF59E0B)),
    UiAppItem("Email", Icons.Default.Email, "Comunicacao", UiAppStatus.AVAILABLE, Color(0xFFEC4899)),
    UiAppItem("Calendario", Icons.Default.CalendarMonth, "Produtividade", UiAppStatus.AVAILABLE, Color(0xFF06B6D4)),
    UiAppItem("Notas", Icons.Default.StickyNote2, "Produtividade", UiAppStatus.AVAILABLE, Color(0xFFF59E0B)),
    UiAppItem("Planilhas", Icons.Default.TableChart, "Produtividade", UiAppStatus.COMING_SOON, Color(0xFF34D399)),
    UiAppItem("Slack", Icons.Default.Chat, "Comunicacao", UiAppStatus.COMING_SOON, Color(0xFFEC4899)),
    UiAppItem("GitHub", Icons.Default.Code, "Desenvolvimento", UiAppStatus.COMING_SOON, Color(0xFF8B5CF6)),
    UiAppItem("WhatsApp", Icons.Default.Phone, "Comunicacao", UiAppStatus.COMING_SOON, Color(0xFF34D399)),
    UiAppItem("Drive", Icons.Default.Cloud, "Navegacao", UiAppStatus.COMING_SOON, Color(0xFF3B82F6)),
)

@Composable
fun AppsScreen(context: Context? = null) {
    var selectedCategory by remember { mutableStateOf("Todos") }
    val categories = listOf("Todos", "Produtividade", "Desenvolvimento", "Comunicacao", "Navegacao")
    val coroutineScope = rememberCoroutineScope()

    // Dialog state
    var showInstallDialog by remember { mutableStateOf(false) }
    var selectedAppForInstall by remember { mutableStateOf<UiAppItem?>(null) }
    var isLaunching by remember { mutableStateOf<String?>(null) }

    val filteredApps = if (selectedCategory == "Todos") allApps
    else allApps.filter { it.category == selectedCategory }

    // Install confirmation dialog
    if (showInstallDialog && selectedAppForInstall != null) {
        val app = selectedAppForInstall!!
        AlertDialog(
            onDismissRequest = {
                showInstallDialog = false
                selectedAppForInstall = null
            },
            containerColor = RumoColors.CardBg,
            titleContentColor = Color.White,
            textContentColor = RumoColors.SubtleText,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(app.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        app.icon,
                        contentDescription = null,
                        tint = app.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = { Text("Instalar ${app.name}?") },
            text = {
                Text(
                    if (app.status == UiAppStatus.COMING_SOON)
                        "${app.name} ainda nao esta disponivel. Voce sera notificado quando estiver pronto."
                    else
                        "O app ${app.name} sera instalado no seu agente e estara disponivel para uso."
                )
            },
            confirmButton = {
                if (app.status != UiAppStatus.COMING_SOON) {
                    Button(
                        onClick = {
                            showInstallDialog = false
                            selectedAppForInstall = null
                            coroutineScope.launch {
                                val token = RetrofitInstance.authToken ?: return@launch
                                try {
                                    val response = RetrofitInstance.agentApi.execute(
                                        authorization = "Bearer $token",
                                        command = AgentCommand(
                                            action = "install_app",
                                            appContext = app.name.lowercase(),
                                            parameters = mapOf("app_name" to app.name)
                                        )
                                    )
                                    if (response.isSuccessful) {
                                        context?.let {
                                            Toast.makeText(it, "${app.name} instalado com sucesso!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        context?.let {
                                            Toast.makeText(it, "Erro ao instalar ${app.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (_: Exception) {
                                    context?.let {
                                        Toast.makeText(it, "Erro de conexao ao instalar", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RumoColors.Accent)
                    ) {
                        Text("Instalar", color = Color.Black)
                    }
                } else {
                    Button(
                        onClick = {
                            showInstallDialog = false
                            selectedAppForInstall = null
                            context?.let {
                                Toast.makeText(it, "Voce sera notificado quando ${app.name} estiver disponivel", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RumoColors.AccentBlue)
                    ) {
                        Text("Notificar-me", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInstallDialog = false
                        selectedAppForInstall = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .statusBarsPadding()
    ) {
        // Header
        Text(
            text = "Aplicativos",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        // Category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            text = category,
                            fontSize = 13.sp,
                            fontWeight = if (selectedCategory == category) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RumoColors.Accent.copy(alpha = 0.15f),
                        selectedLabelColor = RumoColors.Accent,
                        containerColor = RumoColors.CardBg,
                        labelColor = RumoColors.SubtleText
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = RumoColors.CardBorder,
                        selectedBorderColor = RumoColors.Accent.copy(alpha = 0.3f),
                        enabled = true,
                        selected = selectedCategory == category
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        // Apps grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredApps) { app ->
                AppCard(
                    app = app,
                    isLaunching = isLaunching == app.name,
                    onClick = {
                        when (app.status) {
                            UiAppStatus.ACTIVE -> {
                                // Launch the installed app on the agent
                                isLaunching = app.name
                                coroutineScope.launch {
                                    val token = RetrofitInstance.authToken
                                    if (token != null) {
                                        try {
                                            val response = RetrofitInstance.agentApi.execute(
                                                authorization = "Bearer $token",
                                                command = AgentCommand(
                                                    action = "open_app",
                                                    appContext = app.name.lowercase(),
                                                    parameters = mapOf("app_name" to app.name)
                                                )
                                            )
                                            isLaunching = null
                                            if (response.isSuccessful) {
                                                context?.let {
                                                    Toast.makeText(it, "${app.name} aberto no agente", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                context?.let {
                                                    Toast.makeText(it, "Erro ao abrir ${app.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (_: Exception) {
                                            isLaunching = null
                                            context?.let {
                                                Toast.makeText(it, "Erro de conexao", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        isLaunching = null
                                    }
                                }
                            }
                            UiAppStatus.AVAILABLE, UiAppStatus.COMING_SOON -> {
                                selectedAppForInstall = app
                                showInstallDialog = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppCard(
    app: UiAppItem,
    isLaunching: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg),
        onClick = onClick,
        enabled = !isLaunching
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(app.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isLaunching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = app.color,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        app.icon,
                        contentDescription = app.name,
                        tint = app.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = app.name,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status badge
            val statusColor = when (app.status) {
                UiAppStatus.ACTIVE -> RumoColors.Accent
                UiAppStatus.AVAILABLE -> RumoColors.AccentBlue
                UiAppStatus.COMING_SOON -> RumoColors.SubtleText
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (isLaunching) "Abrindo..." else app.status.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun AppsScreenPreview() {
    RumoAgenteTheme {
        AppsScreen()
    }
}
