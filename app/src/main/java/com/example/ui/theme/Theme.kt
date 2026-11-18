package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LuxuryDarkColorScheme = darkColorScheme(
    primary = LuminousViolet,
    onPrimary = Color(0xFF14092A),
    primaryContainer = DeepVioletContainer,
    onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = ChampagneGold,
    onSecondary = Color(0xFF1F1804),
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = SoftChampagneGold,
    tertiary = SoftChampagneGold,
    onTertiary = Color(0xFF211A05),
    background = GraphiteBackground,
    onBackground = IvoryTextPrimary,
    surface = CharcoalSurface,
    onSurface = IvoryTextPrimary,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = SilverTextSecondary,
    outline = CharcoalBorder,
    outlineVariant = Color(0xFF232332),
    error = CrimsonError,
    onError = Color.White
)

private val LuxuryLightColorScheme = lightColorScheme(
    primary = DeepVioletLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = WarmGoldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = WarmGoldLight,
    onTertiary = Color.White,
    background = PearlBackground,
    onBackground = ObsidianTextPrimary,
    surface = AlabasterSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = SoftLavenderCard,
    onSurfaceVariant = SlateTextSecondary,
    outline = Color(0xFFD8D4E8),
    outlineVariant = Color(0xFFE9E6F4),
    error = CrimsonError,
    onError = Color.White
)

val LumiereShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LumiereTheme(
    darkTheme: Boolean = true, // Dark mode is default per specification
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LuxuryDarkColorScheme else LuxuryLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = LumiereShapes,
        content = content
    )
}
