package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Port of lib/core/design_system/specular_glass_card.dart: topLeft->bottomRight gradient of baseTint at
 * opacity*1.5 -> opacity*0.6 (baseTint white in dark / black in light), 1dp border (white .12 / black .08),
 * default shadow (black .35 dark / .04 light, blur 16, dy 6), radius 18, padding 16, clickable.
 * KNOWN GAP: Dart wraps this in BackdropFilter(blur sigma = [blur]); Compose has no backdrop blur of
 * the content behind a node without RenderEffect on a captured layer, so [blur] is accepted but not rendered.
 * Needs the visual pass. Uncompiled.
 */
@Composable
fun SpecularGlassCard(
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") blur: Float = 16f,
    borderRadius: Dp = 18.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    tintColor: Color? = null,
    opacity: Float = 0.08f,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val isDark = LocalThemeConfig.current.isDark
    val baseTint = tintColor ?: if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(borderRadius)
    val border = borderColor ?: if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.04f)
    Box(
        modifier
            .shadow(8.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .background(Brush.linearGradient(listOf(baseTint.copy(alpha = (opacity * 1.5f).coerceIn(0f, 1f)), baseTint.copy(alpha = (opacity * 0.6f).coerceIn(0f, 1f)))))
            .border(1.dp, border, shape)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(padding),
    ) { content() }
}
