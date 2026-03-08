package com.rumoagente.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Rumo Agente color palette — matches iOS Theme.swift and the website exactly.
 *
 * Dark background : #0D1117
 * Secondary dark  : #0A0E14
 * Card background : #151A1F
 * Accent green    : #34D399
 * Accent blue     : #3B82F6
 * Accent purple   : #A855F7
 * Accent orange   : #F97316
 */
object RumoColors {

    // ── Primary backgrounds ────────────────────────────────────────────
    val DarkBg          = Color(0xFF0D1117)
    val SecondaryDark   = Color(0xFF0A0E14)
    val CardBg          = Color(0xFF151A1F)
    val SurfaceVariant  = Color(0xFF1C2128)
    val SurfaceElevated = Color(0xFF21262D)

    // ── Accent colors ──────────────────────────────────────────────────
    val Accent          = Color(0xFF34D399)   // green
    val AccentDark      = Color(0xFF2AB583)   // darker green for pressed states
    val AccentBlue      = Color(0xFF3B82F6)
    val AccentBlueDark  = Color(0xFF2563EB)
    val Purple          = Color(0xFFA855F7)
    val PurpleDark      = Color(0xFF9333EA)
    val Orange          = Color(0xFFF97316)
    val OrangeDark      = Color(0xFFEA580C)
    val Red             = Color(0xFFEF4444)
    val RedDark         = Color(0xFFDC2626)
    val Pink            = Color(0xFFEC4899)
    val Cyan            = Color(0xFF06B6D4)
    val Yellow          = Color(0xFFFACC15)

    // ── Text ───────────────────────────────────────────────────────────
    val TextPrimary     = Color.White
    val SubtleText      = Color.White.copy(alpha = 0.50f)
    val TextTertiary    = Color.White.copy(alpha = 0.35f)
    val TextDisabled    = Color.White.copy(alpha = 0.20f)

    // ── Borders / dividers ─────────────────────────────────────────────
    val CardBorder      = Color.White.copy(alpha = 0.06f)
    val Divider         = Color.White.copy(alpha = 0.08f)
    val BorderSubtle    = Color.White.copy(alpha = 0.04f)

    // ── Semantic ───────────────────────────────────────────────────────
    val Success         = Accent
    val Warning         = Orange
    val Error           = Red
    val Info            = AccentBlue

    // ── Basics ─────────────────────────────────────────────────────────
    val White           = Color.White
    val Black           = Color.Black
    val Transparent     = Color.Transparent

    // ── Gradient pairs ─────────────────────────────────────────────────
    val GradientGreen   = listOf(Accent, Color(0xFF10B981))
    val GradientBlue    = listOf(AccentBlue, Color(0xFF6366F1))
    val GradientPurple  = listOf(Purple, Color(0xFFEC4899))
    val GradientOrange  = listOf(Orange, Color(0xFFF59E0B))
    val GradientAccent  = listOf(Accent, AccentBlue)
}
