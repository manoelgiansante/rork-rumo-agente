package com.rumoagente.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.delay

private const val AGENT_URL = "http://216.238.111.253"

@Composable
fun ScreenViewScreen() {
    var isConnected by remember { mutableStateOf(false) }
    var screenshotKey by remember { mutableIntStateOf(0) }

    // Auto-reload screenshot every 1.5s when connected
    LaunchedEffect(isConnected) {
        while (isConnected) {
            delay(1500L)
            screenshotKey++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .statusBarsPadding()
    ) {
        // Connection status bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = RumoColors.CardBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isConnected) RumoColors.Accent else RumoColors.Red,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isConnected) "Conectado" else "Desconectado",
                        color = if (isConnected) RumoColors.Accent else RumoColors.Red,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = { isConnected = !isConnected },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) RumoColors.Red.copy(alpha = 0.15f)
                        else RumoColors.Accent.copy(alpha = 0.15f)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (isConnected) RumoColors.Red else RumoColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "Desconectar" else "Conectar",
                        color = if (isConnected) RumoColors.Red else RumoColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        HorizontalDivider(color = RumoColors.CardBorder)

        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isConnected) {
                // Live screenshot view
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("$AGENT_URL/screenshot?t=$screenshotKey")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Tela do agente",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )

                        // Reload indicator
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-12).dp, y = 12.dp),
                            color = RumoColors.Accent.copy(alpha = 0.5f),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else {
                // Disconnected placeholder with pulse animation
                DisconnectedPlaceholder()
            }
        }
    }
}

@Composable
private fun DisconnectedPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pulse ring
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .background(
                            RumoColors.AccentBlue.copy(alpha = 0.1f),
                            CircleShape
                        )
                )
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            RumoColors.AccentBlue.copy(alpha = 0.12f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = RumoColors.AccentBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Tela do Agente",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Conecte para visualizar a tela\nremota do agente",
                color = RumoColors.SubtleText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

private val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ScreenViewScreenPreview() {
    RumoAgenteTheme {
        ScreenViewScreen()
    }
}
