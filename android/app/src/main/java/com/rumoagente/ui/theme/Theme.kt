package com.rumoagente.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val RumoDarkColorScheme = darkColorScheme(
    primary = RumoColors.Accent,
    onPrimary = Color.Black,
    primaryContainer = RumoColors.Accent.copy(alpha = 0.15f),
    onPrimaryContainer = RumoColors.Accent,
    secondary = RumoColors.AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = RumoColors.AccentBlue.copy(alpha = 0.15f),
    onSecondaryContainer = RumoColors.AccentBlue,
    tertiary = RumoColors.Purple,
    background = RumoColors.DarkBg,
    onBackground = Color.White,
    surface = RumoColors.DarkBg,
    onSurface = Color.White,
    surfaceVariant = RumoColors.CardBg,
    onSurfaceVariant = RumoColors.SubtleText,
    outline = RumoColors.CardBorder,
    outlineVariant = RumoColors.Divider,
    error = RumoColors.Red,
    onError = Color.White,
)

private val RumoTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = Color.White
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = Color.White
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = Color.White
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = Color.White
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = Color.White
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color.White
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color.White
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.White
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = RumoColors.SubtleText
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.White
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = RumoColors.SubtleText
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = RumoColors.SubtleText
    )
)

@Composable
fun RumoAgenteTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = RumoDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = RumoColors.DarkBg.toArgb()
            window.navigationBarColor = RumoColors.DarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RumoTypography,
        content = content
    )
}
