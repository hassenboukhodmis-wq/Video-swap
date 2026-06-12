package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CosmicDarkColorScheme = darkColorScheme(
    primary = CosmicPrimary,
    secondary = CosmicSecondary,
    tertiary = CosmicAccent,
    background = CosmicBackground,
    surface = CosmicSurface,
    onBackground = White,
    onSurface = White,
    surfaceVariant = CosmicSurfaceVariant,
    onPrimary = Color(0xFF020306),
    onSecondary = White,
    onTertiary = White,
    onSurfaceVariant = MutedText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force premium dark theme by default
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CosmicDarkColorScheme,
        typography = Typography,
        content = content
    )
}
