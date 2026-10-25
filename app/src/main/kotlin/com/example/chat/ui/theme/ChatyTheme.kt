package com.example.chat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.theme.argbWithAlpha

private fun c(argb: Long): Color = Color(argb)

/** Port of the AppColors extension ThemeConfig.toThemeData() builds
 *  (lib/ui/core/theme/theme_config.dart lines ~272-318). */
class AppColors(config: ThemeConfig) {
    private val isDark = config.isDark
    val primary = c(config.accentColor)
    val onPrimary = c(config.onAccentColor)
    val secondary = c(argbWithAlpha(config.accentColor, 0.8))
    val onSecondary = c(config.onAccentColor)
    val accent = c(config.accentColor)
    val onAccent = c(config.onAccentColor)
    val background = c(config.backgroundColor)
    val onBackground = c(config.primaryTextColor)
    val surface = c(config.surfaceColor)
    val onSurface = c(config.primaryTextColor)
    val surfaceSecondary = c(config.cardColor)
    val onSurfaceSecondary = c(config.secondaryTextColor)
    val surfaceElevated = c(config.cardColor)
    val onSurfaceElevated = c(config.primaryTextColor)
    val foreground = c(config.primaryTextColor)
    val foregroundSecondary = c(config.secondaryTextColor)
    val foregroundTertiary = c(argbWithAlpha(config.secondaryTextColor, 0.65))
    val border = c(if (isDark) 0xFF27272AL else 0xFFE4E4E7L)
    val borderSubtle = c(if (isDark) 0xFF18181BL else 0xFFF4F4F5L)
    val divider = c(if (isDark) 0xFF27272AL else 0xFFE5E7EBL)
    val input = c(config.surfaceColor)
    val inputBorder = c(if (isDark) 0xFF27272AL else 0xFFE4E4E7L)
    val inputFill = c(if (isDark) 0xFF18181BL else 0xFFF4F4F5L)
    val disabled = c(if (isDark) 0xFF27272AL else 0xFFE4E4E7L)
    val disabledForeground = c(if (isDark) 0xFF52525BL else 0xFFA1A1AAL)
    val selected = c(config.accentColor)
    val onSelected = c(config.onAccentColor)
    val hover = c(argbWithAlpha(config.accentColor, 0.08))
    val pressed = c(argbWithAlpha(config.accentColor, 0.16))
    val success = c(config.successColor)
    val onSuccess = Color.White
    val warning = c(0xFFF59E0BL)
    val onWarning = Color.Black
    val error = c(config.dangerColor)
    val onError = Color.White
    val info = c(config.linkColor)
    val onInfo = Color.White
    val link = c(config.linkColor)
    val icon = c(config.primaryTextColor)
    val iconSecondary = c(config.secondaryTextColor)
    val shadow = c(if (isDark) 0x66000000L else 0x0F000000L)
}

/** Port of the ChatColors extension (theme_config.dart lines ~320-340). */
class ChatColors(config: ThemeConfig) {
    private val isDark = config.isDark
    val incomingBubble = c(config.incomingBubbleColor)
    val incomingText = c(config.incomingTextColor)
    val outgoingBubble = c(config.outgoingBubbleColor)
    val outgoingText = c(config.outgoingTextColor)
    val replySurface = c(if (isDark) 0xFF0F172AL else 0xFFF1F5F9L)
    val replyBorder = c(config.accentColor)
    val composerSurface = c(config.surfaceColor)
    val composerBorder = c(if (isDark) 0xFF27272AL else 0xFFE4E4E7L)
    val messageMetadata = c(argbWithAlpha(config.secondaryTextColor, 0.7))
    val deliveryTick = c(argbWithAlpha(config.secondaryTextColor, 0.8))
    val readTick = when (config.tickStyle) {
        "Neon" -> c(0xFF39FF14L)
        "iOS Style" -> c(0xFF34C759L)
        "Minimal" -> c(config.primaryTextColor)
        else -> c(0xFF38BDF8L)
    }
    val reactionSurface = c(config.cardColor)
    val reactionBorder = c(argbWithAlpha(config.accentColor, 0.25))
}

val LocalThemeConfig = compositionLocalOf { ThemePresets.getSystemDefaultTheme(true) }
val LocalAppColors = compositionLocalOf { AppColors(ThemePresets.getSystemDefaultTheme(true)) }
val LocalChatColors = compositionLocalOf { ChatColors(ThemePresets.getSystemDefaultTheme(true)) }

/** Chaty themed wrapper: feeds the ported ThemeConfig engine into Compose.
 *  Replaces the placeholder ChatyTheme(darkTheme) from the shell milestone. */
@Composable
fun ChatyTheme(config: ThemeConfig, content: @Composable () -> Unit) {
    val appColors = AppColors(config)
    val chatColors = ChatColors(config)
    val scheme = if (config.isDark) darkColorScheme(
        primary = appColors.primary,
        onPrimary = appColors.onPrimary,
        secondary = appColors.secondary,
        onSecondary = appColors.onSecondary,
        background = appColors.background,
        onBackground = appColors.onBackground,
        surface = appColors.surface,
        onSurface = appColors.onSurface,
        surfaceVariant = appColors.surfaceSecondary,
        onSurfaceVariant = appColors.onSurfaceSecondary,
        error = appColors.error,
        onError = appColors.onError,
        outline = appColors.border,
    ) else lightColorScheme(
        primary = appColors.primary,
        onPrimary = appColors.onPrimary,
        secondary = appColors.secondary,
        onSecondary = appColors.onSecondary,
        background = appColors.background,
        onBackground = appColors.onBackground,
        surface = appColors.surface,
        onSurface = appColors.onSurface,
        surfaceVariant = appColors.surfaceSecondary,
        onSurfaceVariant = appColors.onSurfaceSecondary,
        error = appColors.error,
        onError = appColors.onError,
        outline = appColors.border,
    )
    CompositionLocalProvider(
        LocalThemeConfig provides config,
        LocalAppColors provides appColors,
        LocalChatColors provides chatColors,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
