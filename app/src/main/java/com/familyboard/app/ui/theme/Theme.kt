package com.familyboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    tertiary = Coral,
    background = LightBackground,
    onBackground = Color(0xFF1A1B1E),
    surface = LightSurface,
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFEDEFF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DA2FB),
    onPrimary = Color(0xFF10193B),
    secondary = Teal,
    tertiary = Coral,
    background = DarkBackground,
    onBackground = Color(0xFFECECEC),
    surface = DarkSurface,
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF2A2C33),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun FamilyBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
