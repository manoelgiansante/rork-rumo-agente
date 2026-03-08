package com.rumoagente.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.ChatMessage
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

data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

@Composable
fun ChatScreen() {
    val messages = remember {
        mutableStateListOf(
            UiChatMessage(
                content = "Ola! Sou o Rumo Agente. Como posso ajudar voce hoje?",
                isUser = false,
                timestamp = System.currentTimeMillis() - 60000
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf("Geral") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val apps = listOf("Geral", "Browser", "Terminal", "Editor", "Arquivos")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .statusBarsPadding()
    ) {
        // App selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RumoColors.CardBg)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEach { app ->
                FilterChip(
                    selected = selectedApp == app,
                    onClick = { selectedApp = app },
                    label = {
                        Text(
                            text = app,
                            fontSize = 13.sp,
                            fontWeight = if (selectedApp == app) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RumoColors.Accent.copy(alpha = 0.15f),
                        selectedLabelColor = RumoColors.Accent,
                        containerColor = RumoColors.SurfaceVariant,
                        labelColor = RumoColors.SubtleText
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color.Transparent,
                        selectedBorderColor = RumoColors.Accent.copy(alpha = 0.3f),
                        enabled = true,
                        selected = selectedApp == app
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        HorizontalDivider(color = RumoColors.CardBorder)

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }

            if (isTyping) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Input bar
        Surface(
            color = RumoColors.CardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HorizontalDivider(color = RumoColors.CardBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Digite sua mensagem...",
                                color = RumoColors.SubtleText,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RumoColors.Accent.copy(alpha = 0.3f),
                            unfocusedBorderColor = RumoColors.CardBorder,
                            focusedContainerColor = RumoColors.SurfaceVariant,
                            unfocusedContainerColor = RumoColors.SurfaceVariant,
                            cursorColor = RumoColors.Accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isTyping) {
                                val userMsg = inputText.trim()
                                messages.add(UiChatMessage(content = userMsg, isUser = true))
                                inputText = ""
                                isTyping = true

                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size - 1)

                                    // Try streaming first, fall back to regular API
                                    val streamSuccess = tryStreamChat(
                                        messages = messages,
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
                                                messages[lastIndex] = current.copy(isStreaming = false)
                                            }
                                        },
                                        onError = { /* will fall back */ }
                                    )

                                    if (!streamSuccess) {
                                        // Fallback to regular chat API
                                        try {
                                            val apiMessages = messages
                                                .filter { !it.isStreaming || it.content.isNotBlank() }
                                                .map { msg ->
                                                    ChatMessage(
                                                        role = if (msg.isUser) MessageRole.USER else MessageRole.ASSISTANT,
                                                        content = msg.content
                                                    )
                                                }
                                            val token = RetrofitInstance.authToken ?: ""
                                            val response = RetrofitInstance.chatApi.chat(
                                                authorization = "Bearer $token",
                                                messages = apiMessages
                                            )
                                            isTyping = false
                                            if (response.isSuccessful && response.body() != null) {
                                                messages.add(
                                                    UiChatMessage(
                                                        content = response.body()!!.message,
                                                        isUser = false
                                                    )
                                                )
                                            } else {
                                                messages.add(
                                                    UiChatMessage(
                                                        content = "Erro ao processar sua mensagem. Tente novamente.",
                                                        isUser = false
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            isTyping = false
                                            messages.add(
                                                UiChatMessage(
                                                    content = "Erro de conexao: ${e.localizedMessage ?: "Tente novamente."}",
                                                    isUser = false
                                                )
                                            )
                                        }
                                    }
                                    delay(100)
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isTyping) RumoColors.SubtleText else RumoColors.Accent,
                            contentColor = Color.Black
                        ),
                        enabled = !isTyping && inputText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Enviar",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Attempt to stream chat via SSE. Returns true if streaming was used, false to fall back.
 */
private suspend fun tryStreamChat(
    messages: List<UiChatMessage>,
    onStreamStart: () -> Unit,
    onChunk: (String) -> Unit,
    onStreamEnd: () -> Unit,
    onError: (Exception) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val token = RetrofitInstance.authToken ?: return@withContext false
        val baseUrl = com.rumoagente.data.api.Config.API_URL.removeSuffix("/api")

        val apiMessages = messages
            .filter { !it.isStreaming || it.content.isNotBlank() }
            .map { msg ->
                val role = if (msg.isUser) "user" else "assistant"
                """{"role":"$role","content":${com.google.gson.Gson().toJson(msg.content)}}"""
            }

        val jsonBody = """{"messages":[${apiMessages.joinToString(",")}],"stream":true}"""

        val request = Request.Builder()
            .url("$baseUrl/api/chat")
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
            // Not a stream, read as regular JSON
            val bodyStr = body.string()
            response.close()

            // Try to parse as regular JSON response
            try {
                val jsonResponse = com.google.gson.Gson().fromJson(bodyStr, com.rumoagente.data.models.ChatResponse::class.java)
                withContext(Dispatchers.Main) {
                    onStreamStart()
                    onChunk(jsonResponse.message)
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
                        // Simple text/message response
                        val message = jsonObj.get("message")?.asString
                            ?: jsonObj.get("text")?.asString
                            ?: jsonObj.get("content")?.asString
                        if (!message.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) { onChunk(message) }
                        }
                    }
                } catch (_: Exception) {
                    // Raw text chunk
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

@Composable
private fun MessageBubble(message: UiChatMessage) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) RumoColors.Accent
            else RumoColors.CardBg,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.isUser) {
                    Text(
                        text = message.content,
                        color = Color.Black,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
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

        Text(
            text = timeFormat.format(Date(message.timestamp)),
            color = RumoColors.SubtleText.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier.padding(
                start = if (!message.isUser) 4.dp else 0.dp,
                end = if (message.isUser) 4.dp else 0.dp,
                top = 3.dp
            )
        )
    }
}

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
        text = "|",
        color = RumoColors.Accent.copy(alpha = cursorAlpha),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
}

/**
 * Simple markdown renderer for chat messages.
 * Supports: **bold**, *italic*, `code`, ```code blocks```
 */
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
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = RumoColors.Accent,
                            background = RumoColors.SurfaceVariant
                        )) {
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
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = RumoColors.Accent,
                            background = RumoColors.SurfaceVariant
                        )) {
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
                // Bullet points
                i < len && (text.substring(i).startsWith("- ") || text.substring(i).startsWith("* ")) &&
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
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = RumoColors.CardBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = index * 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(dotAlpha)
                            .background(RumoColors.SubtleText, CircleShape)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ChatScreenPreview() {
    RumoAgenteTheme {
        ChatScreen()
    }
}
