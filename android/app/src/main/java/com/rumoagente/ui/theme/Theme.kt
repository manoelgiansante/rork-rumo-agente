package com.rumoagente.ui.theme

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ════════════════════════════════════════════════════════════════════════
//  Material 3 Dark Color Scheme
// ════════════════════════════════════════════════════════════════════════

private val RumoDarkColorScheme = darkColorScheme(
    primary = RumoColors.Accent,
    onPrimary = Color.Black,
    primaryContainer = RumoColors.Accent.copy(alpha = 0.15f),
    onPrimaryContainer = RumoColors.Accent,
    inversePrimary = RumoColors.AccentDark,

    secondary = RumoColors.AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = RumoColors.AccentBlue.copy(alpha = 0.15f),
    onSecondaryContainer = RumoColors.AccentBlue,

    tertiary = RumoColors.Purple,
    onTertiary = Color.White,
    tertiaryContainer = RumoColors.Purple.copy(alpha = 0.15f),
    onTertiaryContainer = RumoColors.Purple,

    background = RumoColors.DarkBg,
    onBackground = Color.White,
    surface = RumoColors.DarkBg,
    onSurface = Color.White,
    surfaceVariant = RumoColors.CardBg,
    onSurfaceVariant = RumoColors.SubtleText,
    surfaceTint = RumoColors.Accent,
    inverseSurface = Color.White,
    inverseOnSurface = RumoColors.DarkBg,

    surfaceContainerLowest = RumoColors.SecondaryDark,
    surfaceContainerLow = RumoColors.DarkBg,
    surfaceContainer = RumoColors.CardBg,
    surfaceContainerHigh = RumoColors.SurfaceVariant,
    surfaceContainerHighest = RumoColors.SurfaceElevated,
    surfaceBright = RumoColors.SurfaceElevated,
    surfaceDim = RumoColors.SecondaryDark,

    outline = RumoColors.CardBorder,
    outlineVariant = RumoColors.Divider,

    error = RumoColors.Red,
    onError = Color.White,
    errorContainer = RumoColors.Red.copy(alpha = 0.15f),
    onErrorContainer = RumoColors.Red,

    scrim = Color.Black,
)

// ════════════════════════════════════════════════════════════════════════
//  Typography
// ════════════════════════════════════════════════════════════════════════

private val RumoTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = Color.White
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = Color.White
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
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
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
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

// ════════════════════════════════════════════════════════════════════════
//  Shapes
// ════════════════════════════════════════════════════════════════════════

private val RumoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ════════════════════════════════════════════════════════════════════════
//  Extended color scheme (extras not in Material 3)
// ════════════════════════════════════════════════════════════════════════

data class RumoExtendedColors(
    val accent: Color,
    val accentDark: Color,
    val accentBlue: Color,
    val purple: Color,
    val orange: Color,
    val red: Color,
    val pink: Color,
    val cyan: Color,
    val yellow: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val subtleText: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val divider: Color,
    val borderSubtle: Color,
    val secondaryDark: Color,
    val surfaceElevated: Color,
    val gradientAccent: List<Color>,
    val gradientBlue: List<Color>,
    val gradientPurple: List<Color>,
    val gradientOrange: List<Color>,
    val gradientGreen: List<Color>,
)

val LocalRumoColors = staticCompositionLocalOf {
    RumoExtendedColors(
        accent = RumoColors.Accent,
        accentDark = RumoColors.AccentDark,
        accentBlue = RumoColors.AccentBlue,
        purple = RumoColors.Purple,
        orange = RumoColors.Orange,
        red = RumoColors.Red,
        pink = RumoColors.Pink,
        cyan = RumoColors.Cyan,
        yellow = RumoColors.Yellow,
        cardBg = RumoColors.CardBg,
        cardBorder = RumoColors.CardBorder,
        subtleText = RumoColors.SubtleText,
        textTertiary = RumoColors.TextTertiary,
        textDisabled = RumoColors.TextDisabled,
        divider = RumoColors.Divider,
        borderSubtle = RumoColors.BorderSubtle,
        secondaryDark = RumoColors.SecondaryDark,
        surfaceElevated = RumoColors.SurfaceElevated,
        gradientAccent = RumoColors.GradientAccent,
        gradientBlue = RumoColors.GradientBlue,
        gradientPurple = RumoColors.GradientPurple,
        gradientOrange = RumoColors.GradientOrange,
        gradientGreen = RumoColors.GradientGreen,
    )
}

// ════════════════════════════════════════════════════════════════════════
//  Theme entry point
// ════════════════════════════════════════════════════════════════════════

@Composable
fun RumoAgenteTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = RumoDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: transparent system bars, content draws behind them
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    val extendedColors = RumoExtendedColors(
        accent = RumoColors.Accent,
        accentDark = RumoColors.AccentDark,
        accentBlue = RumoColors.AccentBlue,
        purple = RumoColors.Purple,
        orange = RumoColors.Orange,
        red = RumoColors.Red,
        pink = RumoColors.Pink,
        cyan = RumoColors.Cyan,
        yellow = RumoColors.Yellow,
        cardBg = RumoColors.CardBg,
        cardBorder = RumoColors.CardBorder,
        subtleText = RumoColors.SubtleText,
        textTertiary = RumoColors.TextTertiary,
        textDisabled = RumoColors.TextDisabled,
        divider = RumoColors.Divider,
        borderSubtle = RumoColors.BorderSubtle,
        secondaryDark = RumoColors.SecondaryDark,
        surfaceElevated = RumoColors.SurfaceElevated,
        gradientAccent = RumoColors.GradientAccent,
        gradientBlue = RumoColors.GradientBlue,
        gradientPurple = RumoColors.GradientPurple,
        gradientOrange = RumoColors.GradientOrange,
        gradientGreen = RumoColors.GradientGreen,
    )

    CompositionLocalProvider(LocalRumoColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RumoTypography,
            shapes = RumoShapes,
            content = content
        )
    }
}

/** Shorthand to access extended colors from any composable. */
object RumoTheme {
    val colors: RumoExtendedColors
        @Composable
        get() = LocalRumoColors.current
}

// ════════════════════════════════════════════════════════════════════════
//  Helper composables & modifiers
// ════════════════════════════════════════════════════════════════════════

/** Standard card radius used across the app. */
val RumoCardRadius = 14.dp

/** Standard card shape. */
val RumoCardShape = RoundedCornerShape(RumoCardRadius)

/**
 * A themed card with the standard dark card background, subtle border,
 * and rounded corners matching the iOS card style.
 */
@Composable
fun RumoCard(
    modifier: Modifier = Modifier,
    borderColor: Color = RumoColors.CardBorder,
    containerColor: Color = RumoColors.CardBg,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RumoCardShape),
        shape = RumoCardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = content
    )
}

/** Convenience function to get card colors matching the theme. */
fun rumoCardColors(
    containerColor: Color = RumoColors.CardBg,
    contentColor: Color = Color.White,
) = CardDefaults.cardColors(
    containerColor = containerColor,
    contentColor = contentColor,
)

/** Standard accent-to-blue linear gradient used across the app. */
fun rumoAccentGradient(
    start: Offset = Offset.Zero,
    end: Offset = Offset.Infinite,
) = Brush.linearGradient(
    colors = RumoColors.GradientAccent,
    start = start,
    end = end,
)

/** Generic linear gradient builder for any color pair. */
fun rumoLinearGradient(
    colors: List<Color>,
    start: Offset = Offset.Zero,
    end: Offset = Offset.Infinite,
) = Brush.linearGradient(colors = colors, start = start, end = end)

/** Radial glow gradient for hero backgrounds (matches iOS). */
fun rumoRadialGlow(
    color: Color = RumoColors.Accent,
    centerAlpha: Float = 0.5f,
    center: Offset = Offset.Unspecified,
    radius: Float = Float.POSITIVE_INFINITY,
) = Brush.radialGradient(
    colors = listOf(color.copy(alpha = centerAlpha), Color.Transparent),
    center = center,
    radius = radius,
)

/** Modifier that applies the dark screen background. */
fun Modifier.rumoDarkBackground() =
    this.background(RumoColors.DarkBg)

/** Modifier that applies a card-style background with border. */
fun Modifier.rumoCardBackground(
    borderColor: Color = RumoColors.CardBorder,
    cornerRadius: Dp = RumoCardRadius,
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(RumoColors.CardBg)
    .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))

/** A full-screen container with the dark background already applied. */
@Composable
fun RumoDarkSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg),
        content = content
    )
}

/** Primary (green accent) button colors. */
@Composable
fun rumoPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = RumoColors.Accent,
    contentColor = Color.Black,
    disabledContainerColor = RumoColors.Accent.copy(alpha = 0.3f),
    disabledContentColor = Color.Black.copy(alpha = 0.5f),
)

/** Secondary (blue accent) button colors. */
@Composable
fun rumoSecondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = RumoColors.AccentBlue,
    contentColor = Color.White,
    disabledContainerColor = RumoColors.AccentBlue.copy(alpha = 0.3f),
    disabledContentColor = Color.White.copy(alpha = 0.5f),
)

/** Destructive (red) button colors. */
@Composable
fun rumoDestructiveButtonColors() = ButtonDefaults.buttonColors(
    containerColor = RumoColors.Red,
    contentColor = Color.White,
    disabledContainerColor = RumoColors.Red.copy(alpha = 0.3f),
    disabledContentColor = Color.White.copy(alpha = 0.5f),
)

/** Ghost/outline button colors (transparent with accent text). */
@Composable
fun rumoGhostButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = RumoColors.Accent,
    disabledContentColor = RumoColors.Accent.copy(alpha = 0.3f),
)

/** Switch colors matching the theme. */
@Composable
fun rumoSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = RumoColors.Accent,
    uncheckedThumbColor = RumoColors.SubtleText,
    uncheckedTrackColor = RumoColors.SurfaceVariant,
    uncheckedBorderColor = RumoColors.Divider,
)

/** Outlined text field colors matching the theme. */
@Composable
fun rumoTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = RumoColors.Accent,
    focusedBorderColor = RumoColors.Accent,
    unfocusedBorderColor = RumoColors.CardBorder,
    focusedLabelColor = RumoColors.Accent,
    unfocusedLabelColor = RumoColors.SubtleText,
    focusedContainerColor = RumoColors.CardBg,
    unfocusedContainerColor = RumoColors.CardBg,
    errorBorderColor = RumoColors.Red,
    errorLabelColor = RumoColors.Red,
    errorCursorColor = RumoColors.Red,
    focusedPlaceholderColor = RumoColors.SubtleText,
    unfocusedPlaceholderColor = RumoColors.SubtleText,
)

/** Navigation bar item colors matching the iOS tab bar. */
@Composable
fun rumoNavBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = RumoColors.Accent,
    selectedTextColor = RumoColors.Accent,
    unselectedIconColor = RumoColors.SubtleText,
    unselectedTextColor = RumoColors.SubtleText,
    indicatorColor = RumoColors.Accent.copy(alpha = 0.12f),
)

/** Standard border stroke for cards. */
val RumoCardBorder = BorderStroke(1.dp, RumoColors.CardBorder)

/** Accent-tinted border stroke. */
fun rumoAccentBorder(alpha: Float = 0.2f) =
    BorderStroke(1.dp, RumoColors.Accent.copy(alpha = alpha))

/** Color-specific border stroke. */
fun rumoColorBorder(color: Color, alpha: Float = 0.2f) =
    BorderStroke(1.dp, color.copy(alpha = alpha))

/** Icon badge background (small colored circle behind an icon). */
fun Modifier.rumoIconBadge(
    color: Color = RumoColors.Accent,
    alpha: Float = 0.15f,
    cornerRadius: Dp = 10.dp,
) = this.background(color.copy(alpha = alpha), RoundedCornerShape(cornerRadius))

/** Filter chip colors matching the dark theme. */
@Composable
fun rumoFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = RumoColors.CardBg,
    labelColor = RumoColors.SubtleText,
    selectedContainerColor = RumoColors.Accent.copy(alpha = 0.15f),
    selectedLabelColor = RumoColors.Accent,
)

/** Filter chip border matching the dark theme. */
@Composable
fun rumoFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    borderColor = RumoColors.CardBorder,
    selectedBorderColor = RumoColors.Accent.copy(alpha = 0.3f),
    enabled = true,
    selected = selected,
)
