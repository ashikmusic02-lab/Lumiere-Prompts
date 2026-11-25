package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// PromptLens Premium Global Dark Color System

// Core Backgrounds & Surfaces
val MainBackground = Color(0xFF080B2A)       // Main Background: #080B2A
val SecondaryBackground = Color(0xFF0C1035)  // Secondary Background: #0C1035
val CardBackground = Color(0xFF11164A)       // Card Background: #11164A
val ElevatedSurface = Color(0xFF171D58)      // Elevated Surface: #171D58

// Brand Accents
val PrimaryPurple = Color(0xFF713CFF)        // Primary Purple: #713CFF
val SecondaryBlue = Color(0xFF258BFF)        // Secondary Blue: #258BFF
val AccentCyan = Color(0xFF21D6E8)           // Accent Cyan: #21D6E8

// Text Palette
val PrimaryText = Color(0xFFF5F6FF)          // Primary Text: #F5F6FF
val SecondaryText = Color(0xFFA8ACD1)        // Secondary Text: #A8ACD1
val InactiveChipText = Color(0xFFB8BCE0)     // Inactive Category / Comment Row Text: #B8BCE0
val MutedText = Color(0xFF777FAE)            // Muted Text: #777FAE

// Border
val SubtleBorder = Color(0xFF252B69)         // Border: #252B69

// Gradients (Reserved strictly for Primary CTA Buttons, Active Navigation, and Selected Elements)
val PromptAccentGradientBrush = Brush.horizontalGradient(
    colors = listOf(PrimaryPurple, SecondaryBlue) // #713CFF → #258BFF
)

val PromptSecondaryGradientBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFA23CFF), Color(0xFF21A8FF)) // #A23CFF → #21A8FF
)

// Clean Vertical Background Brush (#080B2A → #0C1035)
val PromptVerticalBackgroundBrush = Brush.verticalGradient(
    colors = listOf(MainBackground, SecondaryBackground)
)

// Clean Solid Card Surface Brush (#11164A) so cards remain minimal without excessive gradients
val PromptCardSurfaceBrush = Brush.verticalGradient(
    colors = listOf(CardBackground, CardBackground)
)

// Semantic & Mapped Aliases Used Across the App
val NavyIndigoBgTop = MainBackground
val NavyIndigoBgBottom = SecondaryBackground

val IndigoSurfaceDark = CardBackground
val IndigoSurfaceLight = ElevatedSurface
val IndigoInnerBox = MainBackground
val IndigoBorderBase = SubtleBorder
val IndigoSoftBorder = SubtleBorder

val AccentViolet = PrimaryPurple
val AccentBlue = SecondaryBlue

val CosmicIndigoBackground = MainBackground
val CosmicIndigoSurface = SecondaryBackground
val CosmicIndigoCard = CardBackground
val CosmicIndigoElevated = ElevatedSurface
val CosmicIndigoBorder = SubtleBorder

val PromptVioletPrimary = PrimaryPurple
val PromptVioletDark = Color(0xFF5E2CE6)
val PromptVioletContainer = ElevatedSurface

val PromptCyanSecondary = SecondaryBlue
val PromptCyanDark = Color(0xFF1B74E4)
val PromptCyanContainer = Color(0xFF102454)

val PromptGoldAccent = AccentCyan
val PromptGoldSoft = Color(0xFF99F0F8)
val PromptGoldDark = Color(0xFF17A9B8)

val DarkTextPrimary = PrimaryText
val DarkTextSecondary = SecondaryText
val DarkTextMuted = MutedText

val LightBackground = MainBackground
val LightSurface = CardBackground
val LightCardSurface = ElevatedSurface
val LightBorder = SubtleBorder
val LightTextPrimary = PrimaryText
val LightTextSecondary = SecondaryText

// Action Button Soft Accents (Detail Screen)
val ActionLikeContainer = Color(0xFF2A1432)
val ActionLikeIcon = Color(0xFFFF6B93)
val HeartLikeRed = Color(0xFFFF3B5C)

val ActionCommentContainer = Color(0xFF0E2942)
val ActionCommentIcon = AccentCyan

val ActionShareContainer = Color(0xFF11244D)
val ActionShareIcon = SecondaryBlue

val ActionBookmarkContainer = Color(0xFF1D194B)
val ActionBookmarkIcon = Color(0xFFB39DFF)

// Status & Profile Badge Colors
val ChampagneGold = AccentCyan
val SoftChampagneGold = PromptGoldSoft
val LuminousViolet = PrimaryPurple
val EmeraldSuccess = Color(0xFF10B981)
val CrimsonError = Color(0xFFF43F5E)

val BadgeEmeraldGreen = Color(0xFF10B981)
val BadgeVioletPurple = PrimaryPurple
val BadgeElectricBlue = SecondaryBlue
val BadgeAmberOrange = Color(0xFFF59E0B)
val BadgeShieldGreen = Color(0xFF22C55E)
val BadgeTealCyan = AccentCyan
val BadgeSkyBlue = Color(0xFF38BDF8)

val IndigoSurfaceTop = CardBackground
val IndigoSurfaceBottom = SecondaryBackground
val TextPureWhite = PrimaryText
val TextLavenderGray = SecondaryText
val TextLavenderMuted = MutedText
