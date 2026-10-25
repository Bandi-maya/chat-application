package com.example.chat.ui.core.theme

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha

/** Shared semantic color resolver, exact port of theme_config.dart's
 *  AppColors construction (accent -> accentColor, foreground ->
 *  primaryTextColor, border/borderSubtle/divider/inputFill fixed light/dark
 *  constants, warning 0xFFF59E0B, info -> linkColor, shadow per brightness).
 *  Used by the tasks, updates, search and navigation slices. */
class ChatyColors(theme: ThemeConfig) {
    val isDark = theme.isDark
    val background = Color(theme.backgroundColor)
    val surface = Color(theme.surfaceColor)
    val surfaceElevated = Color(theme.cardColor)
    val surfaceSecondary = Color(theme.cardColor)
    val primary = Color(theme.accentColor)
    val onPrimary = Color(theme.onAccentColor)
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)
    val foregroundTertiary = Color(argbWithAlpha(theme.secondaryTextColor, 0.65))
    val border = if (theme.isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)
    val borderSubtle = if (theme.isDark) Color(0xFF18181B) else Color(0xFFF4F4F5)
    val divider = if (theme.isDark) Color(0xFF27272A) else Color(0xFFE5E7EB)
    val inputFill = if (theme.isDark) Color(0xFF18181B) else Color(0xFFF4F4F5)
    val success = Color(theme.successColor)
    val warning = Color(0xFFF59E0B)
    val error = Color(theme.dangerColor)
    val info = Color(theme.linkColor)
    val shadow = if (theme.isDark) Color(0x66000000) else Color(0x0F000000)
}
