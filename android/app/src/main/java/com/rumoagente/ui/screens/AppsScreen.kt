package com.rumoagente.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

// --- Models matching iOS CloudApp ---

enum class AppStatus(val displayName: String) {
    INSTALLED("Instalado"),
    INSTALLING("Instalando..."),
    NOT_INSTALLED("Não instalado"),
    RUNNING("Em uso")
}

enum class AppCategory(val displayName: String) {
    AGRO("Agronegócio"),
    FINANCE("Financeiro"),
    PRODUCTIVITY("Produtividade"),
    COMMUNICATION("Comunicação"),
    OTHER("Outros")
}

data class CloudAppItem(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val status: AppStatus,
    val category: AppCategory,
    val isSelected: Boolean = false
)

// --- Fallback apps matching iOS exactly ---

private val fallbackApps = listOf(
    CloudAppItem("1", "Ponta do S", Icons.Default.Eco, AppStatus.INSTALLED, AppCategory.AGRO),
    CloudAppItem("2", "Rumo Máquinas", Icons.Default.Settings, AppStatus.INSTALLED, AppCategory.AGRO),
    CloudAppItem("3", "Aegro", Icons.Default.BarChart, AppStatus.INSTALLED, AppCategory.AGRO),
    CloudAppItem("4", "Conta Azul", Icons.Default.CreditCard, AppStatus.INSTALLED, AppCategory.FINANCE),
    CloudAppItem("5", "Excel Online", Icons.Default.TableChart, AppStatus.INSTALLED, AppCategory.PRODUCTIVITY),
    CloudAppItem("6", "Google Sheets", Icons.Default.Description, AppStatus.INSTALLED, AppCategory.PRODUCTIVITY),
    CloudAppItem("7", "WhatsApp Web", Icons.Default.Chat, AppStatus.INSTALLED, AppCategory.COMMUNICATION),
    CloudAppItem("8", "Slack", Icons.Default.Forum, AppStatus.NOT_INSTALLED, AppCategory.COMMUNICATION),
    CloudAppItem("9", "Siagri", Icons.Default.Business, AppStatus.INSTALLED, AppCategory.AGRO),
    CloudAppItem("10", "Totvs Agro", Icons.Default.Inventory, AppStatus.NOT_INSTALLED, AppCategory.AGRO),
)

// --- Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(context: Context? = null) {
    var apps by remember { mutableStateOf(fallbackApps) }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var searchText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Load apps on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        // TODO: fetch from API: GET /apps with auth
        // For now use fallback apps
        apps = fallbackApps
        isLoading = false
    }

    // Filter logic matching iOS
    val filteredByCategory = if (selectedCategory == null) apps
        else apps.filter { it.category == selectedCategory }

    val filteredApps = if (searchText.isBlank()) filteredByCategory
        else filteredByCategory.filter {
            it.name.contains(searchText, ignoreCase = true)
        }

    fun selectApp(app: CloudAppItem) {
        apps = apps.map { existing ->
            if (existing.id == app.id) existing.copy(isSelected = !existing.isSelected)
            else existing.copy(isSelected = false)
        }
        // Send command to agent chat
        val selected = apps.firstOrNull { it.isSelected }
        if (selected != null) {
            coroutineScope.launch {
                val token = RetrofitInstance.authToken ?: return@launch
                try {
                    val action = if (selected.status == AppStatus.INSTALLED || selected.status == AppStatus.RUNNING)
                        "open_app" else "install_app"
                    RetrofitInstance.agentApi.execute(
                        authorization = "Bearer $token",
                        command = AgentCommand(
                            action = action,
                            appContext = selected.name.lowercase(),
                            parameters = mapOf("app_name" to selected.name)
                        )
                    )
                    context?.let {
                        val msg = if (action == "open_app") "${selected.name} aberto no agente"
                            else "${selected.name} instalação iniciada"
                        Toast.makeText(it, msg, Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    context?.let {
                        Toast.makeText(it, "Erro de conexão", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .statusBarsPadding()
    ) {
        // Header
        Text(
            text = "Apps",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            placeholder = {
                Text(
                    "Buscar aplicativos...",
                    color = RumoColors.SubtleText,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = RumoColors.SubtleText,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Limpar",
                            tint = RumoColors.SubtleText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RumoColors.CardBg,
                unfocusedContainerColor = RumoColors.CardBg,
                focusedBorderColor = RumoColors.Accent.copy(alpha = 0.5f),
                unfocusedBorderColor = RumoColors.CardBorder,
                cursorColor = RumoColors.Accent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Category filter chips (horizontal scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Todos" chip
            CategoryChip(
                name = "Todos",
                isSelected = selectedCategory == null,
                onClick = { selectedCategory = null }
            )
            // Category chips
            AppCategory.entries.forEach { category ->
                CategoryChip(
                    name = category.displayName,
                    isSelected = selectedCategory == category,
                    onClick = { selectedCategory = category }
                )
            }
        }

        // Content
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = RumoColors.Accent,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        } else if (filteredApps.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = RumoColors.SubtleText,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhum aplicativo encontrado",
                        color = RumoColors.SubtleText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tente outro termo ou categoria",
                        color = RumoColors.SubtleText.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Apps grid - 2 columns (matching iOS)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredApps, key = { it.id }) { app ->
                    AppCard(
                        app = app,
                        onClick = { selectApp(app) }
                    )
                }
            }
        }
    }
}

// --- Category Chip (matching iOS capsule style) ---

@Composable
private fun CategoryChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isSelected) RumoColors.Accent.copy(alpha = 0.2f) else RumoColors.CardBg,
        label = "chipBg"
    )
    val borderColor by animateColorAsState(
        if (isSelected) RumoColors.Accent.copy(alpha = 0.5f) else RumoColors.CardBorder,
        label = "chipBorder"
    )
    val textColor by animateColorAsState(
        if (isSelected) RumoColors.Accent else RumoColors.SubtleText,
        label = "chipText"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier
            .border(1.dp, borderColor, CircleShape)
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// --- App Card (matching iOS AppCard exactly) ---

@Composable
private fun AppCard(
    app: CloudAppItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateDpAsState(
        targetValue = if (isPressed) 0.97.dp else 1.0.dp,
        animationSpec = spring(),
        label = "cardScale"
    )

    // Colors matching iOS categories
    val iconColor = when (app.category) {
        AppCategory.AGRO -> RumoColors.Accent         // green
        AppCategory.FINANCE -> RumoColors.AccentBlue   // blue
        AppCategory.PRODUCTIVITY -> RumoColors.Orange  // orange
        AppCategory.COMMUNICATION -> RumoColors.Purple // purple
        AppCategory.OTHER -> RumoColors.SubtleText     // gray
    }

    val statusColor = when (app.status) {
        AppStatus.INSTALLED -> RumoColors.SubtleText
        AppStatus.RUNNING -> Color(0xFF22C55E) // green
        AppStatus.INSTALLING -> RumoColors.Orange
        AppStatus.NOT_INSTALLED -> Color(0xFFEF4444).copy(alpha = 0.6f) // red 0.6
    }

    val borderColor by animateColorAsState(
        if (app.isSelected) RumoColors.Accent.copy(alpha = 0.5f) else RumoColors.CardBorder,
        label = "cardBorder"
    )
    val borderWidth by animateDpAsState(
        if (app.isSelected) 2.dp else 1.dp,
        label = "cardBorderWidth"
    )
    val cardBg by animateColorAsState(
        if (app.isSelected) RumoColors.Accent.copy(alpha = 0.08f) else RumoColors.CardBg,
        label = "cardBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isPressed) 0.97f else 1f)
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon in colored rounded square (matching iOS: 56x56 with cornerRadius 16)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    app.icon,
                    contentDescription = app.name,
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App name
            Text(
                text = app.name,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Status text (matching iOS - plain text, not badge)
            Text(
                text = app.status.displayName,
                color = statusColor,
                fontSize = 12.sp
            )
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
