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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class UiUiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
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
                            if (inputText.isNotBlank()) {
                                val userMsg = inputText.trim()
                                messages.add(UiChatMessage(content = userMsg, isUser = true))
                                inputText = ""
                                isTyping = true

                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                    delay(1500)
                                    isTyping = false
                                    messages.add(
                                        UiChatMessage(
                                            content = "Entendi! Vou processar sua solicitacao: \"$userMsg\". Aguarde um momento...",
                                            isUser = false
                                        )
                                    )
                                    delay(100)
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = RumoColors.Accent,
                            contentColor = Color.Black
                        )
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
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (message.isUser) Color.Black else Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
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
