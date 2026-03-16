package com.rumoagente.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.Config
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.ChatMessage
import com.rumoagente.data.models.ChatRequest
import com.rumoagente.data.models.ChatResponse
import com.rumoagente.data.models.MessageRole
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

// ── UI Chat Message ──────────────────────────────────────────────────────────

data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isConfirmation: Boolean = false,
    val screenshotUrl: String? = null
)

// ── Main Chat Screen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val messages = remember {
        mutableStateListOf(
            UiChatMessage(
                content = "Ol\u00e1! \uD83D\uDC4B Sou o Rumo Agente, seu assistente inteligente. Como posso ajudar voc\u00ea hoje?\n\nVoc\u00ea pode me pedir para:\n\u2022 Abrir e operar aplicativos\n\u2022 Lan\u00e7ar dados e relat\u00f3rios\n\u2022 Executar tarefas nos seus softwares\n\u2022 Instalar novos programas\n\n\u00c9 s\u00f3 digitar o comando!",
                isUser = false,
                timestamp = System.currentTimeMillis() - 60000
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Apps list (matching iOS: "Geral" is the null/default, others are installed apps)
    val apps = listOf("Browser", "Terminal", "Editor", "Arquivos")

    // Auto-scroll when messages change or typing starts
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(
                index = messages.size - 1 + if (isTyping) 1 else 0
            )
        }
    }

    // Send message function
    fun sendMessage(text: String = inputText.trim()) {
        if (text.isBlank() || isTyping) return

        val userMsg = UiChatMessage(content = text, isUser = true)
        messages.add(userMsg)
        inputText = ""
        isTyping = true
        errorMessage = null

        coroutineScope.launch {
            // Build conversation history (last 20 messages)
            val historyForApi = messages
                .filter { !it.isStreaming || it.content.isNotBlank() }
                .takeLast(20)
                .map { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    """{"role":"$role","content":${com.google.gson.Gson().toJson(msg.content)}}"""
                }

            // Try streaming first
            val streamSuccess = tryStreamChat(
                historyJson = historyForApi,
                appContext = selectedApp,
                onStreamStart = {
                    val streamingMsg = UiChatMessage(
                        content = "",
                        isUser = false,
                        isStreaming = true
                    )
                    messages.add(streamingMsg)
                    isTyping = false
                },
                onChunk = { chunk ->
                    val lastIndex = messages.lastIndex
                    if (lastIndex >= 0 && !messages[lastIndex].isUser) {
                        val current = messages[lastIndex]
                        messages[lastIndex] = current.copy(
                            content = current.content + chunk
                        )
                    }
                },
                onStreamEnd = {
                    val lastIndex = messages.lastIndex
                    if (lastIndex >= 0 && !messages[lastIndex].isUser) {
                        val current = messages[lastIndex]
                        // Check if needs confirmation
                        val needsConfirmation = current.content.contains("?") && (
                                current.content.lowercase().contains("confirmar") ||
                                        current.content.lowercase().contains("deseja") ||
                                        current.content.lowercase().contains("correto") ||
                                        current.content.lowercase().contains("qual")
                                )
                        messages[lastIndex] = current.copy(
                            isStreaming = false,
                            isConfirmation = needsConfirmation
                        )
                    }
                },
                onError = { /* fall back */ }
            )

            if (!streamSuccess) {
                // Fallback to regular chat API (matches iOS ClaudeService format)
                try {
                    val chatMessages = messages
                        .filter { !it.isStreaming || it.content.isNotBlank() }
                        .takeLast(20)
                        .map { msg ->
                            mapOf(
                                "role" to if (msg.isUser) "user" else "assistant",
                                "content" to msg.content
                            )
                        }
                    val chatRequest = ChatRequest(
                        messages = chatMessages
                    )
                    val response = RetrofitInstance.chatApi.chat(
                        body = chatRequest
                    )
                    isTyping = false
                    if (response.isSuccessful && response.body() != null) {
                        val responseText = response.body()!!.text
                        val needsConfirmation = responseText.contains("?") && (
                                responseText.lowercase().contains("confirmar") ||
                                        responseText.lowercase().contains("deseja") ||
                                        responseText.lowercase().contains("correto") ||
                                        responseText.lowercase().contains("qual")
                                )
                        messages.add(
                            UiChatMessage(
                                content = responseText,
                                isUser = false,
                                isConfirmation = needsConfirmation
                            )
                        )
                    } else {
                        messages.add(
                            UiChatMessage(
                                content = "Desculpe, ocorreu um erro ao processar seu comando. Tente novamente.",
                                isUser = false
                            )
                        )
                    }
                } catch (e: Exception) {
                    isTyping = false
                    val errorText = if (e is java.net.UnknownHostException || e is java.net.ConnectException) {
                        "Sem conex\u00e3o com a internet. Verifique sua rede e tente novamente."
                    } else {
                        "Desculpe, ocorreu um erro ao processar seu comando. Tente novamente."
                    }
                    errorMessage = e.localizedMessage
                    messages.add(
                        UiChatMessage(
                            content = errorText,
                            isUser = false
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Agente IA",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = RumoColors.SubtleText
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = RumoColors.CardBg
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Limpar Conversa", color = Color.White)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = RumoColors.SubtleText
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                messages.clear()
                                messages.add(
                                    UiChatMessage(
                                        content = "Ol\u00e1! \uD83D\uDC4B Sou o Rumo Agente, seu assistente inteligente. Como posso ajudar voc\u00ea hoje?\n\nVoc\u00ea pode me pedir para:\n\u2022 Abrir e operar aplicativos\n\u2022 Lan\u00e7ar dados e relat\u00f3rios\n\u2022 Executar tarefas nos seus softwares\n\u2022 Instalar novos programas\n\n\u00c9 s\u00f3 digitar o comando!",
                                        isUser = false,
                                        timestamp = System.currentTimeMillis() - 60000
                                    )
                                )
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RumoColors.DarkBg,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = RumoColors.DarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── App Selector Bar ─────────────────────────────────────────────
            AppSelectorBar(
                selectedApp = selectedApp,
                apps = apps,
                onSelectApp = { selectedApp = it }
            )

            HorizontalDivider(color = RumoColors.CardBorder)

            // ── Error Banner ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Red.copy(alpha = 0.8f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { errorMessage = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // ── Messages List ────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                )
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onConfirm = {
                            coroutineScope.launch {
                                inputText = "Sim, pode prosseguir."
                                sendMessage("Sim, pode prosseguir.")
                            }
                        },
                        onCancel = {
                            coroutineScope.launch {
                                inputText = "N\u00e3o, cancele essa a\u00e7\u00e3o."
                                sendMessage("N\u00e3o, cancele essa a\u00e7\u00e3o.")
                            }
                        }
                    )
                }

                if (isTyping) {
                    item(key = "typing") {
                        TypingIndicator()
                    }
                }
            }

            // ── Input Bar ────────────────────────────────────────────────────
            InputBar(
                inputText = inputText,
                isTyping = isTyping,
                onTextChange = { inputText = it },
                onSend = { sendMessage() }
            )
        }
    }
}

// ── App Selector Bar ─────────────────────────────────────────────────────────

@Composable
private fun AppSelectorBar(
    selectedApp: String?,
    apps: List<String>,
    onSelectApp: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "Geral" chip (always first, represents no specific app)
        AppChip(
            name = "Geral",
            icon = Icons.Default.AutoAwesome,
            isSelected = selectedApp == null,
            onClick = { onSelectApp(null) }
        )

        apps.forEach { app ->
            AppChip(
                name = app,
                icon = when (app) {
                    "Browser" -> Icons.Default.Language
                    "Terminal" -> Icons.Default.Terminal
                    "Editor" -> Icons.Default.Edit
                    "Arquivos" -> Icons.Default.Folder
                    else -> Icons.Default.Apps
                },
                isSelected = selectedApp == app,
                onClick = { onSelectApp(app) }
            )
        }
    }
}

@Composable
private fun AppChip(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) RumoColors.Accent.copy(alpha = 0.2f) else RumoColors.CardBg
    val borderColor = if (isSelected) RumoColors.Accent.copy(alpha = 0.5f) else RumoColors.CardBorder
    val contentColor = if (isSelected) RumoColors.Accent else RumoColors.SubtleText

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = name,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Message Bubble ───────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: UiChatMessage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (message.isUser) {
            Spacer(modifier = Modifier.width(60.dp))
        }

        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Agent label (only for assistant messages)
            if (!message.isUser) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = RumoColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Agente",
                        color = RumoColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Message bubble
            val bubbleShape = RoundedCornerShape(
                topStart = if (message.isUser) 20.dp else 6.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = if (message.isUser) 6.dp else 20.dp
            )

            Surface(
                shape = bubbleShape,
                color = if (message.isUser) RumoColors.Accent else RumoColors.CardBg
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (message.isUser) {
                        Text(
                            text = message.content,
                            color = Color.Black,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    } else {
                        MarkdownText(
                            text = message.content,
                            color = Color.White
                        )
                    }

                    // Blinking cursor during streaming
                    if (message.isStreaming) {
                        BlinkingCursor()
                    }
                }
            }

            // Confirmation action buttons
            if (message.isConfirmation && !message.isUser) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Confirm button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = RumoColors.Accent,
                        onClick = onConfirm
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Confirmar",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Cancel button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Red.copy(alpha = 0.6f),
                        onClick = onCancel
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Cancelar",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Timestamp
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                color = RumoColors.SubtleText,
                fontSize = 10.sp,
                modifier = Modifier.padding(
                    start = if (!message.isUser) 4.dp else 0.dp,
                    end = if (message.isUser) 4.dp else 0.dp
                )
            )
        }

        if (!message.isUser) {
            Spacer(modifier = Modifier.width(60.dp))
        }
    }
}

// ── Blinking Cursor ──────────────────────────────────────────────────────────

@Composable
private fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    Text(
        text = "\u258C",
        color = RumoColors.Accent.copy(alpha = cursorAlpha),
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
}

// ── Markdown Text ────────────────────────────────────────────────────────────

@Composable
private fun MarkdownText(
    text: String,
    color: Color
) {
    val annotatedString = buildAnnotatedString {
        var i = 0
        val chars = text.toCharArray()
        val len = chars.size

        while (i < len) {
            when {
                // Code block: ```...```
                i + 2 < len && chars[i] == '`' && chars[i + 1] == '`' && chars[i + 2] == '`' -> {
                    val endIdx = text.indexOf("```", i + 3)
                    if (endIdx != -1) {
                        val codeContent = text.substring(i + 3, endIdx)
                            .trimStart('\n').trimEnd('\n')
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = RumoColors.Accent,
                                background = RumoColors.SurfaceVariant
                            )
                        ) {
                            append(codeContent)
                        }
                        i = endIdx + 3
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                // Inline code: `...`
                chars[i] == '`' -> {
                    val endIdx = text.indexOf('`', i + 1)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = RumoColors.Accent,
                                background = RumoColors.SurfaceVariant
                            )
                        ) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                // Bold: **...**
                i + 1 < len && chars[i] == '*' && chars[i + 1] == '*' -> {
                    val endIdx = text.indexOf("**", i + 2)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                            append(text.substring(i + 2, endIdx))
                        }
                        i = endIdx + 2
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                // Italic: *...*
                chars[i] == '*' -> {
                    val endIdx = text.indexOf('*', i + 1)
                    if (endIdx != -1 && endIdx > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                // Bullet points: - or * at start of line
                (text.substring(i).startsWith("- ") || text.substring(i).startsWith("* ")) &&
                        (i == 0 || chars[i - 1] == '\n') -> {
                    append("\u2022 ")
                    i += 2
                }
                else -> {
                    append(chars[i])
                    i++
                }
            }
        }
    }

    Text(
        text = annotatedString,
        color = color,
        fontSize = 15.sp,
        lineHeight = 21.sp
    )
}

// ── Typing Indicator (bouncing dots matching iOS) ────────────────────────────

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = RumoColors.CardBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brain icon like iOS
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = RumoColors.Accent.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp)
                )

                Spacer(modifier = Modifier.width(2.dp))

                // Bouncing dots (scale animation matching iOS easeInOut with staggered delay)
                repeat(3) { index ->
                    val dotScale by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 500,
                                delayMillis = index * 150,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(dotScale)
                            .background(RumoColors.SubtleText, CircleShape)
                    )
                }
            }
        }
    }
}

// ── Input Bar ────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    inputText: String,
    isTyping: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = RumoColors.DarkBg.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Text field with rounded border (matching iOS)
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        "Digite um comando...",
                        color = RumoColors.SubtleText,
                        fontSize = 15.sp
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RumoColors.CardBorder,
                    unfocusedBorderColor = RumoColors.CardBorder,
                    focusedContainerColor = RumoColors.CardBg,
                    unfocusedContainerColor = RumoColors.CardBg,
                    cursorColor = RumoColors.Accent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 15.sp
                )
            )

            // Send button (large circle icon matching iOS arrow.up.circle.fill)
            val canSend = inputText.trim().isNotEmpty() && !isTyping
            val sendColor = if (canSend) RumoColors.Accent else RumoColors.SubtleText

            IconButton(
                onClick = { if (canSend) onSend() },
                enabled = canSend,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ArrowCircleUp,
                    contentDescription = "Enviar",
                    tint = sendColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// ── SSE Streaming ────────────────────────────────────────────────────────────

private suspend fun tryStreamChat(
    historyJson: List<String>,
    appContext: String?,
    onStreamStart: () -> Unit,
    onChunk: (String) -> Unit,
    onStreamEnd: () -> Unit,
    onError: (Exception) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val token = RetrofitInstance.authToken ?: return@withContext false
        val baseUrl = Config.AGENT_URL.removeSuffix("/")

        // Build iOS-matching request format: {message, appContext, history, stream}
        val lastUserMessage = historyJson.lastOrNull()?.let { json ->
            try {
                val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
                if (obj.get("role").asString == "user") obj.get("content").asString else null
            } catch (_: Exception) { null }
        } ?: ""

        val messageJson = com.google.gson.Gson().toJson(lastUserMessage)
        val appContextJson = com.google.gson.Gson().toJson(appContext ?: "")

        val jsonBody = """{"message":$messageJson,"appContext":$appContextJson,"history":[${historyJson.joinToString(",")}],"stream":true}"""

        val request = Request.Builder()
            .url("$baseUrl/chat")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()
            return@withContext false
        }

        val contentType = response.header("Content-Type") ?: ""
        val body = response.body ?: run {
            response.close()
            return@withContext false
        }

        // Check if it's actually a stream response
        if (!contentType.contains("text/event-stream") && !contentType.contains("text/plain")) {
            val bodyStr = body.string()
            response.close()

            try {
                val jsonResponse = com.google.gson.Gson().fromJson(
                    bodyStr,
                    ChatResponse::class.java
                )
                withContext(Dispatchers.Main) {
                    onStreamStart()
                    onChunk(jsonResponse.text)
                    onStreamEnd()
                }
                return@withContext true
            } catch (_: Exception) {
                return@withContext false
            }
        }

        withContext(Dispatchers.Main) { onStreamStart() }

        val reader = BufferedReader(InputStreamReader(body.byteStream()))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue

            if (currentLine.startsWith("data: ")) {
                val data = currentLine.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val jsonObj = com.google.gson.JsonParser.parseString(data).asJsonObject
                    val choices = jsonObj.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                        val content = delta?.get("content")?.asString
                        if (!content.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) { onChunk(content) }
                        }
                    } else {
                        val message = jsonObj.get("message")?.asString
                            ?: jsonObj.get("text")?.asString
                            ?: jsonObj.get("content")?.asString
                        if (!message.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) { onChunk(message) }
                        }
                    }
                } catch (_: Exception) {
                    if (data.isNotBlank()) {
                        withContext(Dispatchers.Main) { onChunk(data) }
                    }
                }
            }
        }

        reader.close()
        response.close()
        withContext(Dispatchers.Main) { onStreamEnd() }
        return@withContext true
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onError(e) }
        return@withContext false
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ChatScreenPreview() {
    RumoAgenteTheme {
        ChatScreen()
    }
}
