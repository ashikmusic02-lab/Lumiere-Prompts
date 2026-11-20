package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PromptLensDarkColorScheme = darkColorScheme(
    primary = PromptVioletPrimary,
    onPrimary = Color(0xFF14082E),
    primaryContainer = PromptVioletContainer,
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = PromptCyanSecondary,
    onSecondary = Color(0xFF042033),
    secondaryContainer = PromptCyanContainer,
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = PromptGoldAccent,
    onTertiary = Color(0xFF1F1600),
    background = CosmicIndigoBackground,
    onBackground = DarkTextPrimary,
    surface = CosmicIndigoSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = CosmicIndigoCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = CosmicIndigoBorder,
    outlineVariant = Color(0xFF282058),
    error = CrimsonError,
    onError = Color.White
)

private val PromptLensLightColorScheme = lightColorScheme(
    primary = PromptVioletDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = PromptCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = PromptGoldDark,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCardSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFE5E0F8),
    error = CrimsonError,
    onError = Color.White
)

val PromptLensShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LumiereTheme(
    darkTheme: Boolean = true,
    isBangla: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PromptLensDarkColorScheme else PromptLensLightColorScheme
    val typography = buildAppTypography(isBangla = isBangla)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = PromptLensShapes,
        content = content
    )
}
