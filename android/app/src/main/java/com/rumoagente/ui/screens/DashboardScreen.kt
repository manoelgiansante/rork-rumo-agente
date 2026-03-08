package com.rumoagente.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.AgentTask
import com.rumoagente.data.models.TaskStatus
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToChat: () -> Unit = {},
    onNavigateToScreen: () -> Unit = {},
    onNavigateToApps: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    var userName by remember { mutableStateOf("Produtor") }
    var userInitial by remember { mutableStateOf("U") }
    var credits by remember { mutableIntStateOf(10) }
    var maxCredits by remember { mutableIntStateOf(10) }
    var tasks by remember { mutableStateOf<List<AgentTask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var creditsUsedToday by remember { mutableIntStateOf(0) }
    var agentOnline by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<AgentTask?>(null) }

    // Staggered entrance animations
    val headerAlpha = remember { Animatable(0f) }
    val statusAlpha = remember { Animatable(0f) }
    val creditsAlpha = remember { Animatable(0f) }
    val actionsAlpha = remember { Animatable(0f) }
    val tasksAlpha = remember { Animatable(0f) }

    val headerOffset = remember { Animatable(20f) }
    val statusOffset = remember { Animatable(20f) }
    val creditsOffset = remember { Animatable(20f) }
    val actionsOffset = remember { Animatable(20f) }
    val tasksOffset = remember { Animatable(20f) }

    val scope = rememberCoroutineScope()

    suspend fun loadData() {
        val token = RetrofitInstance.authToken ?: run {
            isLoading = false
            return
        }
        try {
            val profileResponse = RetrofitInstance.supabaseApi.getProfiles(
                authorization = "Bearer $token"
            )
            if (profileResponse.isSuccessful) {
                val profile = profileResponse.body()?.firstOrNull()
                if (profile != null) {
                    userName = profile.displayName ?: profile.email.substringBefore("@")
                    userInitial = userName.firstOrNull()?.uppercase() ?: "U"
                    credits = profile.credits
                    maxCredits = when (profile.plan.lowercase()) {
                        "starter" -> 100
                        "pro" -> 500
                        "enterprise" -> 2000
                        else -> 10
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val statusResponse = RetrofitInstance.agentApi.getStatus()
            agentOnline = statusResponse.isSuccessful &&
                    statusResponse.body()?.status == "online"
        } catch (_: Exception) {
            agentOnline = false
        }

        try {
            val tasksResponse = RetrofitInstance.supabaseApi.getAgentTasks(
                authorization = "Bearer $token"
            )
            if (tasksResponse.isSuccessful) {
                tasks = tasksResponse.body() ?: emptyList()
                val todayPrefix = LocalDate.now().toString()
                creditsUsedToday = tasks
                    .filter { it.createdAt?.startsWith(todayPrefix) == true }
                    .sumOf { it.creditsUsed }
            }
        } catch (_: Exception) {}

        isLoading = false
    }

    // Initial load + entrance animation
    LaunchedEffect(Unit) {
        loadData()

        val springSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )

        launch { headerAlpha.animateTo(1f, springSpec) }
        launch { headerOffset.animateTo(0f, springSpec) }
        delay(80)
        launch { statusAlpha.animateTo(1f, springSpec) }
        launch { statusOffset.animateTo(0f, springSpec) }
        delay(80)
        launch { creditsAlpha.animateTo(1f, springSpec) }
        launch { creditsOffset.animateTo(0f, springSpec) }
        delay(80)
        launch { actionsAlpha.animateTo(1f, springSpec) }
        launch { actionsOffset.animateTo(0f, springSpec) }
        delay(80)
        launch { tasksAlpha.animateTo(1f, springSpec) }
        launch { tasksOffset.animateTo(0f, springSpec) }
    }

    // Format date in Portuguese
    val formattedDate = remember {
        val formatter = DateTimeFormatter.ofPattern(
            "EEEE, d 'de' MMMM", Locale("pt", "BR")
        )
        LocalDate.now().format(formatter).replaceFirstChar { it.uppercase() }
    }

    // Task detail bottom sheet
    if (selectedTask != null) {
        TaskDetailBottomSheet(
            task = selectedTask!!,
            onDismiss = { selectedTask = null }
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                loadData()
                isRefreshing = false
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(headerAlpha.value)
                        .offset(y = headerOffset.value.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Olá, $userName!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RumoColors.SubtleText
                        )
                    }
                    // Avatar circle
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                RumoColors.Accent.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitial,
                            color = RumoColors.Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            // Agent status card
            item {
                AgentStatusCard(
                    agentOnline = agentOnline,
                    modifier = Modifier
                        .alpha(statusAlpha.value)
                        .offset(y = statusOffset.value.dp)
                )
            }

            // Credits card
            item {
                CreditsCard(
                    credits = credits,
                    maxCredits = maxCredits,
                    creditsUsedToday = creditsUsedToday,
                    modifier = Modifier
                        .alpha(creditsAlpha.value)
                        .offset(y = creditsOffset.value.dp)
                )
            }

            // Quick actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(actionsAlpha.value)
                        .offset(y = actionsOffset.value.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.DesktopWindows,
                        label = "Ver Tela",
                        color = RumoColors.AccentBlue,
                        onClick = onNavigateToScreen
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Chat,
                        label = "Chat",
                        color = RumoColors.Accent,
                        onClick = onNavigateToChat
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Apps,
                        label = "Apps",
                        color = RumoColors.Orange,
                        onClick = onNavigateToApps
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CreditCard,
                        label = "Planos",
                        color = RumoColors.Purple,
                        onClick = onNavigateToProfile
                    )
                }
            }

            // Recent tasks header
            item {
                Text(
                    text = "Tarefas Recentes",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .alpha(tasksAlpha.value)
                        .offset(y = tasksOffset.value.dp)
                )
            }

            // Tasks content
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(RumoColors.CardBg, RoundedCornerShape(20.dp))
                            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = RumoColors.Accent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else if (tasks.isEmpty()) {
                item {
                    EmptyTasksCard(
                        modifier = Modifier
                            .alpha(tasksAlpha.value)
                            .offset(y = tasksOffset.value.dp)
                    )
                }
            } else {
                itemsIndexed(tasks.take(5)) { _, task ->
                    TaskRowCard(
                        task = task,
                        onClick = { selectedTask = task },
                        modifier = Modifier
                            .alpha(tasksAlpha.value)
                            .offset(y = tasksOffset.value.dp)
                    )
                }
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ── Agent Status Card ──────────────────────────────────────────────────────

@Composable
private fun AgentStatusCard(
    agentOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = if (agentOnline) Color(0xFF22C55E) else RumoColors.Orange
    val statusText = if (agentOnline) "Pronto" else "Iniciando"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(RumoColors.CardBg, RoundedCornerShape(20.dp))
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Agent logo with gradient circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                RumoColors.Accent.copy(alpha = 0.2f),
                                RumoColors.AccentBlue.copy(alpha = 0.15f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = RumoColors.Accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Agente",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    // Status badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Green dot with glow
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .then(
                                        if (agentOnline) Modifier.shadow(
                                            elevation = 4.dp,
                                            shape = CircleShape,
                                            ambientColor = statusColor.copy(alpha = 0.5f),
                                            spotColor = statusColor.copy(alpha = 0.5f)
                                        ) else Modifier
                                    )
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusColor
                            )
                        }
                    }
                }
                Text(
                    text = "Envie comandos pelo chat e o agente executa para você",
                    fontSize = 12.sp,
                    color = RumoColors.SubtleText,
                    maxLines = 2
                )
            }
        }
    }
}

// ── Credits Card ───────────────────────────────────────────────────────────

@Composable
private fun CreditsCard(
    credits: Int,
    maxCredits: Int,
    creditsUsedToday: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (maxCredits > 0) creditsUsedToday.toFloat() / maxCredits.toFloat() else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        RumoColors.Accent.copy(alpha = 0.08f),
                        RumoColors.CardBg
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: credits remaining
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Créditos",
                    fontSize = 14.sp,
                    color = RumoColors.SubtleText
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$credits",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "restantes",
                        fontSize = 12.sp,
                        color = RumoColors.SubtleText,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right: used today
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Usados hoje",
                    fontSize = 14.sp,
                    color = RumoColors.SubtleText
                )
                Text(
                    text = "$creditsUsedToday",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = RumoColors.AccentBlue
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Circular progress
            CircularCreditsProgress(
                progress = progress.coerceIn(0f, 1f),
                size = 52.dp,
                strokeWidth = 6.dp
            )
        }
    }
}

@Composable
private fun CircularCreditsProgress(
    progress: Float,
    size: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp
) {
    val accentColor = RumoColors.Accent
    val trackColor = Color.White.copy(alpha = 0.08f)

    Canvas(modifier = Modifier.size(size)) {
        val sweepAngle = 360f * progress
        val stroke = strokeWidth.toPx()
        val diameter = this.size.minDimension
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(diameter - stroke, diameter - stroke)

        // Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Progress
        drawArc(
            color = accentColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

// ── Quick Action Button ────────────────────────────────────────────────────

@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = RumoColors.SubtleText,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Task Row Card ──────────────────────────────────────────────────────────

@Composable
private fun TaskRowCard(
    task: AgentTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (task.status) {
        TaskStatus.COMPLETED -> Color(0xFF22C55E)
        TaskStatus.RUNNING -> RumoColors.AccentBlue
        TaskStatus.FAILED -> RumoColors.Red
        TaskStatus.PENDING, TaskStatus.WAITING -> RumoColors.Orange
    }
    val statusIcon = when (task.status) {
        TaskStatus.COMPLETED -> Icons.Default.CheckCircle
        TaskStatus.RUNNING -> Icons.Default.PlayCircle
        TaskStatus.FAILED -> Icons.Default.ErrorOutline
        TaskStatus.PENDING -> Icons.Default.Schedule
        TaskStatus.WAITING -> Icons.Default.HourglassTop
    }

    // Parse time from createdAt
    val timeText = remember(task.createdAt) {
        try {
            task.createdAt?.let {
                val dateTime = java.time.OffsetDateTime.parse(it)
                String.format("%02d:%02d", dateTime.hour, dateTime.minute)
            } ?: ""
        } catch (_: Exception) { "" }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RumoColors.CardBg)
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status icon in circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(statusColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Title + meta
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!task.appName.isNullOrBlank()) {
                        Text(
                            text = task.appName,
                            fontSize = 12.sp,
                            color = RumoColors.Accent
                        )
                    }
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            fontSize = 12.sp,
                            color = RumoColors.SubtleText
                        )
                    }
                }
            }

            // Credits + chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${task.creditsUsed}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RumoColors.SubtleText
                    )
                    Text(
                        text = "créditos",
                        fontSize = 10.sp,
                        color = RumoColors.SubtleText.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = RumoColors.SubtleText.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Empty Tasks Card ───────────────────────────────────────────────────────

@Composable
private fun EmptyTasksCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(RumoColors.CardBg, RoundedCornerShape(20.dp))
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = RumoColors.SubtleText,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Nenhuma tarefa",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = RumoColors.SubtleText
            )
            Text(
                text = "Suas tarefas aparecerão aqui",
                fontSize = 13.sp,
                color = RumoColors.SubtleText.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ── Task Detail Bottom Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailBottomSheet(
    task: AgentTask,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val statusColor = when (task.status) {
        TaskStatus.COMPLETED -> Color(0xFF22C55E)
        TaskStatus.RUNNING -> RumoColors.AccentBlue
        TaskStatus.FAILED -> RumoColors.Red
        TaskStatus.PENDING, TaskStatus.WAITING -> RumoColors.Orange
    }
    val statusIcon = when (task.status) {
        TaskStatus.COMPLETED -> Icons.Default.CheckCircle
        TaskStatus.RUNNING -> Icons.Default.PlayCircle
        TaskStatus.FAILED -> Icons.Default.ErrorOutline
        TaskStatus.PENDING -> Icons.Default.Schedule
        TaskStatus.WAITING -> Icons.Default.HourglassTop
    }
    val statusText = when (task.status) {
        TaskStatus.COMPLETED -> "Concluída"
        TaskStatus.RUNNING -> "Em execução"
        TaskStatus.FAILED -> "Falhou"
        TaskStatus.PENDING -> "Pendente"
        TaskStatus.WAITING -> "Aguardando"
    }

    val createdAtFormatted = remember(task.createdAt) {
        try {
            task.createdAt?.let {
                val dt = java.time.OffsetDateTime.parse(it)
                val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("pt", "BR"))
                dt.format(fmt)
            } ?: "-"
        } catch (_: Exception) { "-" }
    }
    val completedAtFormatted = remember(task.completedAt) {
        try {
            task.completedAt?.let {
                val dt = java.time.OffsetDateTime.parse(it)
                val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("pt", "BR"))
                dt.format(fmt)
            }
        } catch (_: Exception) { null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RumoColors.DarkBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(RumoColors.SubtleText.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(statusColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Title + status text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = task.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = statusText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            // Detail rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RumoColors.CardBg, RoundedCornerShape(16.dp))
                    .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(16.dp))
            ) {
                DetailRow(
                    icon = Icons.Default.Apps,
                    label = "Aplicativo",
                    value = task.appName ?: "Geral"
                )
                HorizontalDivider(color = RumoColors.CardBorder)
                DetailRow(
                    icon = Icons.Default.CreditCard,
                    label = "Créditos usados",
                    value = "${task.creditsUsed}"
                )
                HorizontalDivider(color = RumoColors.CardBorder)
                DetailRow(
                    icon = Icons.Default.Schedule,
                    label = "Iniciada em",
                    value = createdAtFormatted
                )
                if (completedAtFormatted != null) {
                    HorizontalDivider(color = RumoColors.CardBorder)
                    DetailRow(
                        icon = Icons.Default.CheckCircle,
                        label = "Concluída em",
                        value = completedAtFormatted
                    )
                }
            }

            // Running indicator
            if (task.status == TaskStatus.RUNNING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            RumoColors.AccentBlue.copy(alpha = 0.08f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = RumoColors.AccentBlue,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tarefa em execução...",
                        fontSize = 14.sp,
                        color = RumoColors.AccentBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = RumoColors.Accent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = RumoColors.SubtleText
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun DashboardScreenPreview() {
    RumoAgenteTheme {
        DashboardScreen()
    }
}
