package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PromptLensDarkColorScheme = darkColorScheme(
    primary = AccentViolet,
    onPrimary = Color.White,
    primaryContainer = PromptVioletContainer,
    onPrimaryContainer = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = PromptCyanContainer,
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = PromptGoldAccent,
    onTertiary = Color(0xFF1F1600),
    background = NavyIndigoBgTop,
    onBackground = DarkTextPrimary,
    surface = IndigoSurfaceDark,
    onSurface = DarkTextPrimary,
    surfaceVariant = IndigoSurfaceLight,
    onSurfaceVariant = DarkTextSecondary,
    outline = IndigoBorderBase,
    outlineVariant = IndigoSoftBorder,
    error = CrimsonError,
    onError = Color.White
)

val PromptLensShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LumiereTheme(
    darkTheme: Boolean = true,
    isBangla: Boolean = false,
    content: @Composable () -> Unit
) {
    val typography = buildAppTypography(isBangla = isBangla)

    MaterialTheme(
        colorScheme = PromptLensDarkColorScheme,
        typography = typography,
        shapes = PromptLensShapes,
        content = content
    )
}
