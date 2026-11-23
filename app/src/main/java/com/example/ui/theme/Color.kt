package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// PromptLens Deep Navy-Indigo & Violet-Blue Luxury Palette

// Global Background Vertical Gradient (#07041A top to #0F0A3C bottom)
val NavyIndigoBgTop = Color(0xFF07041A)
val NavyIndigoBgBottom = Color(0xFF0F0A3C)

// Indigo Surfaces (#120C4A to #1B1370) & Soft Border (#2A1F8F at 60% alpha)
val IndigoSurfaceDark = Color(0xFF120C4A)
val IndigoSurfaceLight = Color(0xFF1B1370)
val IndigoInnerBox = Color(0xFF0B072E)
val IndigoBorderBase = Color(0xFF2A1F8F)
val IndigoSoftBorder = Color(0x992A1F8F) // 60% alpha of #2A1F8F

// Accent Gradient: Violet #8B5CF6 to Blue #3B82F6
val AccentViolet = Color(0xFF8B5CF6)
val AccentBlue = Color(0xFF3B82F6)

val PromptAccentGradientBrush = Brush.horizontalGradient(
    colors = listOf(AccentViolet, AccentBlue)
)

val PromptVerticalBackgroundBrush = Brush.verticalGradient(
    colors = listOf(NavyIndigoBgTop, NavyIndigoBgBottom)
)

val PromptCardSurfaceBrush = Brush.verticalGradient(
    colors = listOf(IndigoSurfaceLight, IndigoSurfaceDark)
)

// Backward-compatible & semantic colors
val CosmicIndigoBackground = NavyIndigoBgTop
val CosmicIndigoSurface = IndigoSurfaceDark
val CosmicIndigoCard = IndigoSurfaceLight
val CosmicIndigoElevated = Color(0xFF221882)
val CosmicIndigoBorder = IndigoBorderBase

val PromptVioletPrimary = AccentViolet
val PromptVioletDark = Color(0xFF7C3AED)
val PromptVioletContainer = Color(0xFF281B78)

val PromptCyanSecondary = AccentBlue
val PromptCyanDark = Color(0xFF2563EB)
val PromptCyanContainer = Color(0xFF172554)

val PromptGoldAccent = Color(0xFFFBBF24)
val PromptGoldSoft = Color(0xFFFDE68A)
val PromptGoldDark = Color(0xFFD97706)

// Text: White & Soft Lavender Gray
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFC4B5FD)
val DarkTextMuted = Color(0xFF9D8EC7)

// Light Theme Fallback (app uses rich dark navy-indigo theme by default)
val LightBackground = NavyIndigoBgTop
val LightSurface = IndigoSurfaceDark
val LightCardSurface = IndigoSurfaceLight
val LightBorder = IndigoBorderBase
val LightTextPrimary = DarkTextPrimary
val LightTextSecondary = DarkTextSecondary

// Semantic Accents
val ChampagneGold = PromptGoldAccent
val SoftChampagneGold = PromptGoldSoft
val LuminousViolet = AccentViolet
val EmeraldSuccess = Color(0xFF10B981)
val CrimsonError = Color(0xFFF43F5E)
val HeartLikeRed = Color(0xFFFF3B5C)

// Profile Badge Colors
val BadgeEmeraldGreen = Color(0xFF10B981)
val BadgeVioletPurple = Color(0xFF8B5CF6)
val BadgeElectricBlue = Color(0xFF2563EB)
val BadgeAmberOrange = Color(0xFFF59E0B)
val BadgeShieldGreen = Color(0xFF65A30D)
val BadgeTealCyan = Color(0xFF14B8A6)
val BadgeSkyBlue = Color(0xFF0EA5E9)

val IndigoSurfaceTop = IndigoSurfaceLight
val IndigoSurfaceBottom = IndigoSurfaceDark
val TextPureWhite = DarkTextPrimary
val TextLavenderGray = DarkTextSecondary
val TextLavenderMuted = DarkTextMuted

