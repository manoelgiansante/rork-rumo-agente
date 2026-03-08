package com.rumoagente.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

@Composable
fun ScreenViewScreen() {
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var isFullScreen by remember { mutableStateOf(false) }
    var screenBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Zoom & pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var lastZoomScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastOffset by remember { mutableStateOf(Offset.Zero) }

    val coroutineScope = rememberCoroutineScope()
    var refreshJob by remember { mutableStateOf<Job?>(null) }

    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING

    // Handle back from fullscreen
    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    // Cancel refresh on dispose (matches iOS onDisappear)
    DisposableEffect(Unit) {
        onDispose {
            refreshJob?.cancel()
            refreshJob = null
        }
    }

    // ── Fetch screenshot as raw bytes via Retrofit ──────────────────────
    suspend fun fetchScreenshot(): ImageBitmap? {
        return try {
            val response = RetrofitInstance.agentApi.getScreenshot()
            if (response.code() == 401) {
                errorText = "Sessão expirada"
                return null
            }
            if (!response.isSuccessful) return null
            val bytes = response.body()?.bytes() ?: return null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bmp.asImageBitmap()
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            null
        }
    }

    // ── Start auto-refresh every 1.5s (matches iOS) ────────────────────
    fun startRefreshing() {
        refreshJob?.cancel()
        refreshJob = coroutineScope.launch {
            while (isActive) {
                val bmp = fetchScreenshot()
                if (bmp != null) {
                    screenBitmap = bmp
                } else if (errorText == "Sessão expirada") {
                    // Session expired — disconnect
                    connectionState = ConnectionState.DISCONNECTED
                    screenBitmap = null
                    zoomScale = 1f; lastZoomScale = 1f
                    offset = Offset.Zero; lastOffset = Offset.Zero
                    break
                }
                delay(1500L)
            }
        }
    }

    // ── Connect (matches iOS connect()) ─────────────────────────────────
    fun connect() {
        if (RetrofitInstance.authToken == null) {
            errorText = "Faça login primeiro"
            return
        }
        connectionState = ConnectionState.CONNECTING
        errorText = null

        coroutineScope.launch {
            try {
                // 1) Check desktop status
                val statusResponse = RetrofitInstance.agentApi.getDesktopStatus()
                if (!statusResponse.isSuccessful) {
                    errorText = "Servidor offline"
                    connectionState = ConnectionState.DISCONNECTED
                    return@launch
                }

                val desktopRunning = statusResponse.body()?.desktop == true

                // 2) Start desktop if not running
                if (!desktopRunning) {
                    val startResponse = RetrofitInstance.agentApi.startDesktop()
                    if (!startResponse.isSuccessful) {
                        val body = startResponse.errorBody()?.string() ?: "Erro ao iniciar desktop"
                        errorText = body
                        connectionState = ConnectionState.DISCONNECTED
                        return@launch
                    }
                    delay(4000) // Wait for boot
                }

                // 3) Fetch initial screenshot to verify connection
                val bmp = fetchScreenshot()
                if (bmp != null) {
                    screenBitmap = bmp
                }

                connectionState = ConnectionState.CONNECTED
                startRefreshing()
            } catch (_: CancellationException) {
                connectionState = ConnectionState.DISCONNECTED
            } catch (_: Exception) {
                errorText = "Sem conexão"
                connectionState = ConnectionState.DISCONNECTED
            }
        }
    }

    // ── Disconnect (matches iOS disconnect()) ───────────────────────────
    fun disconnect() {
        refreshJob?.cancel()
        refreshJob = null

        if (RetrofitInstance.authToken != null) {
            coroutineScope.launch {
                try {
                    RetrofitInstance.agentApi.stopDesktop()
                } catch (_: Exception) {
                    // Ignore stop errors
                }
            }
        }

        connectionState = ConnectionState.DISCONNECTED
        screenBitmap = null
        zoomScale = 1f; lastZoomScale = 1f
        offset = Offset.Zero; lastOffset = Offset.Zero
    }

    fun resetZoom() {
        zoomScale = 1f
        lastZoomScale = 1f
        offset = Offset.Zero
        lastOffset = Offset.Zero
    }

    // ── UI ───────────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isFullScreen) Modifier.statusBarsPadding()
                    else Modifier
                )
        ) {
            // Connection status bar (hidden in fullscreen)
            AnimatedVisibility(
                visible = !isFullScreen,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                ConnectionStatusBar(
                    connectionState = connectionState,
                    errorMessage = errorText,
                    onDisconnect = { disconnect() }
                )
            }

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (isFullScreen) 0.dp else 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isConnected -> {
                        // Screen area with black background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(if (isFullScreen) 0.dp else 12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = screenBitmap
                            if (bmp != null) {
                                // Pinch-to-zoom + pan (matches iOS MagnifyGesture + DragGesture)
                                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                                    val newScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
                                    zoomScale = newScale
                                    lastZoomScale = newScale
                                    if (newScale > 1f) {
                                        offset = Offset(
                                            x = offset.x + panChange.x,
                                            y = offset.y + panChange.y
                                        )
                                        lastOffset = offset
                                    } else {
                                        offset = Offset.Zero
                                        lastOffset = Offset.Zero
                                    }
                                }

                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Tela do desktop",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .transformable(state = transformState)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (zoomScale > 1f) {
                                                        zoomScale = 1f
                                                        lastZoomScale = 1f
                                                        offset = Offset.Zero
                                                        lastOffset = Offset.Zero
                                                    } else {
                                                        zoomScale = 2.5f
                                                        lastZoomScale = 2.5f
                                                    }
                                                }
                                            )
                                        }
                                        .graphicsLayer {
                                            scaleX = zoomScale
                                            scaleY = zoomScale
                                            translationX = offset.x
                                            translationY = offset.y
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    isConnecting -> {
                        // Loading state (matches iOS ProgressView + text)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = RumoColors.Accent,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Conectando ao seu desktop...",
                                    color = RumoColors.SubtleText,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    else -> {
                        // Disconnected placeholder (matches iOS ScreenPlaceholderView)
                        ScreenPlaceholder(onConnect = { connect() })
                    }
                }
            }

            // Bottom toolbar when connected (hidden in fullscreen)
            AnimatedVisibility(
                visible = !isFullScreen && isConnected,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ScreenToolbar(
                    zoomScale = zoomScale,
                    onFullScreen = { isFullScreen = true },
                    onResetZoom = { resetZoom() }
                )
            }
        }

        // Fullscreen close button overlay (matches iOS xmark.circle.fill overlay)
        AnimatedVisibility(
            visible = isFullScreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
        ) {
            IconButton(
                onClick = { isFullScreen = false },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Sair da tela cheia",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ── Connection Status Bar (matches iOS connectionStatusBar) ──────────────────

@Composable
private fun ConnectionStatusBar(
    connectionState: ConnectionState,
    errorMessage: String?,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionState == ConnectionState.CONNECTED

    val statusColor = when (connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF22C55E)    // green
        ConnectionState.CONNECTING -> RumoColors.Orange    // orange
        ConnectionState.DISCONNECTED -> RumoColors.Red     // red
    }

    val statusText = when (connectionState) {
        ConnectionState.CONNECTED -> "Conectado"
        ConnectionState.CONNECTING -> "Conectando..."
        ConnectionState.DISCONNECTED -> "Desconectado"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Status dot with glow (matches iOS Circle + shadow)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .then(
                        if (isConnected) Modifier.shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = statusColor.copy(alpha = 0.5f),
                            spotColor = statusColor.copy(alpha = 0.5f)
                        ) else Modifier
                    )
                    .background(statusColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = statusText,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    color = RumoColors.Red,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        // Disconnect button (matches iOS capsule button)
        if (isConnected) {
            TextButton(
                onClick = onDisconnect,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = RumoColors.Red.copy(alpha = 0.15f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Desconectar",
                    color = RumoColors.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Screen Toolbar (matches iOS screenToolbar) ───────────────────────────────

@Composable
private fun ScreenToolbar(
    zoomScale: Float,
    onFullScreen: () -> Unit,
    onResetZoom: () -> Unit
) {
    Surface(
        color = RumoColors.CardBg.copy(alpha = 0.9f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fullscreen button (matches iOS Label "Tela Cheia")
            TextButton(
                onClick = onFullScreen,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = null,
                    tint = RumoColors.SubtleText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tela Cheia",
                    color = RumoColors.SubtleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reset zoom button (matches iOS — only shown when zoomed)
                AnimatedVisibility(visible = zoomScale > 1f) {
                    TextButton(
                        onClick = onResetZoom,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = RumoColors.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Resetar Zoom",
                            color = RumoColors.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Zoom percentage (matches iOS monospacedDigit)
                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    color = RumoColors.SubtleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

// ── Disconnected Placeholder (matches iOS ScreenPlaceholderView) ─────────────

@Composable
private fun ScreenPlaceholder(onConnect: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // Pulse animation matching iOS easeInOut(duration: 2).repeatForever(autoreverses: true)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = ScreenEaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = ScreenEaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pulsing circles behind monitor icon (matches iOS ZStack of circles)
        Box(contentAlignment = Alignment.Center) {
            // Outer pulse ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .background(
                        RumoColors.Accent.copy(alpha = 0.06f),
                        CircleShape
                    )
            )
            // Inner static circle
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        RumoColors.Accent.copy(alpha = 0.1f),
                        CircleShape
                    )
            )
            // Monitor icon (matches iOS "display" SF Symbol)
            Icon(
                Icons.Default.DesktopWindows,
                contentDescription = null,
                tint = RumoColors.Accent.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title (matches iOS "Seu Computador na Nuvem" title3.bold)
        Text(
            text = "Seu Computador na Nuvem",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description (matches iOS multiline subheadline)
        Text(
            text = "Desktop privado e isolado.\nSeus dados ficam seguros e separados.",
            color = RumoColors.SubtleText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connect button (matches iOS capsule with play.fill + "Conectar")
        Button(
            onClick = onConnect,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = RumoColors.Accent
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Conectar",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private val ScreenEaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ScreenViewScreenPreview() {
    RumoAgenteTheme {
        ScreenViewScreen()
    }
}
