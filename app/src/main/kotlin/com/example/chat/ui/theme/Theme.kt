package com.example.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Baseline Chaty palette. The full customization engine
// (user-selectable themes, bubbles, ticks) ports in the theming slice.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B93F6),
    onPrimary = Color.White,
    secondary = Color(0xFF25D366),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F8FA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DA3F8),
    onPrimary = Color(0xFF06263F),
    secondary = Color(0xFF25D366),
    background = Color(0xFF0B141A),
    surface = Color(0xFF1F2C34),
)

@Composable
fun ChatyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
