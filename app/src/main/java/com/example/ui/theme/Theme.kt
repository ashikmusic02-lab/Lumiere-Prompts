package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PromptLensDarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = ElevatedSurface,
    onPrimaryContainer = PrimaryText,
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = PromptCyanContainer,
    onSecondaryContainer = PrimaryText,
    tertiary = AccentCyan,
    onTertiary = MainBackground,
    background = MainBackground,
    onBackground = PrimaryText,
    surface = CardBackground,
    onSurface = PrimaryText,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = SecondaryText,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder,
    error = CrimsonError,
    onError = Color.White
)

val PromptLensShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
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
