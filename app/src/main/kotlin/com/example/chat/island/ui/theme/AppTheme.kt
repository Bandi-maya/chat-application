package com.example.chat.island.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.chat.R
import com.example.chat.island.core.IslandSettings

enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Amoled(R.string.theme_amoled)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val selection by IslandSettings.theme.collectAsState()
    val wallpaper by IslandSettings.accentSource.collectAsState()
    val accent by IslandSettings.accentSeed.collectAsState()
    val backgroundType by IslandSettings.bgStyle.collectAsState()
    val backgroundColor by IslandSettings.bgColor.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = selection == 2 || selection == 3 || (selection == 0 && systemDark)
    val base = if (wallpaper == 0 && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else seedScheme(accent, dark)
    val palette = withBackground(base, backgroundType, backgroundColor, dark)
    val colors = if (selection == 3 && backgroundType != 2) palette.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1A1A1A), surfaceContainerHighest = Color(0xFF222222)
    ) else palette
    SideEffect {
        val window = (context as? Activity)?.window
        if (window != null && !view.isInEditMode) {
            val lightIcons = colors.background.luminance() > 0.45f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightNavigationBars = lightIcons
                isAppearanceLightStatusBars = lightIcons
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
