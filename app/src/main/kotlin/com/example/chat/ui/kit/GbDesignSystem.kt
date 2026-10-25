package com.example.chat.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Port of lib/ui/core/design_system/gb_design_system.dart rows/cards used by the conversation,
 * header, notifications and GB feature screens: GbCustomSwitch, GbSettingRow, GbSwitchTile,
 * GbSliderTile, GbSectionDivider, GbCardContainer, GbLivePreviewCard.
 * UNCOMPILED (Compose).
 */

@Composable
fun GbCustomSwitch(
    value: Boolean,
    onChanged: (Boolean) -> Unit,
    activeColor: Color? = null,
) {
    val colors = LocalAppColors.current
    val config = LocalThemeConfig.current
    val haptic = LocalHapticFeedback.current
    val active = activeColor ?: Color(config.accentColor)
    val track by animateColorAsState(if (value) active else colors.borderSubtle.copy(alpha = 0.8f), tween(200), label = "gbTrack")
    val thumbOffset by animateDpAsState(if (value) 22.dp else 0.dp, tween(200), label = "gbThumb")
    Box(
        Modifier.size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .border(1.2.dp, if (value) active else colors.borderSubtle, RoundedCornerShape(15.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChanged(!value) }
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = thumbOffset)
                .size(22.dp)
                .shadow(4.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(if (value) colors.onPrimary else colors.foregroundTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (value) Icon(Icons.Rounded.Check, null, tint = active, modifier = Modifier.size(14.dp))
            else Box(Modifier.size(width = 8.dp, height = 2.dp).background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(1.dp)))
        }
    }
}

/**
 * GbSettingRow. [icon] as ImageVector, or [iconContent] for the Dart `Widget` icon variant.
 * Trailing priority matches Dart: switch, color dot, trailing, chevron.
 */
@Composable
fun GbSettingRow(
    title: String,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    switchValue: Boolean? = null,
    onSwitchChanged: ((Boolean) -> Unit)? = null,
    colorValue: Long? = null,
    colorHex: Any? = null,
    onColorTap: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    showChevron: Boolean = false,
    hasSubScreen: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    iconColor: Color? = null,
    iconBgColor: Color? = null,
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val accent = Color(config.accentColor)
    val effIcon = iconColor ?: accent
    val effBg = iconBgColor ?: effIcon.copy(alpha = 0.12f)
    var effColor: Long? = colorValue
    if (effColor == null && colorHex != null) {
        effColor = when (colorHex) {
            is Long -> colorHex
            is Int -> colorHex.toLong() and 0xFFFFFFFFL
            is String -> {
                val h = colorHex.replace("#", "")
                when (h.length) {
                    6 -> h.toLongOrNull(16)?.let { 0xFF000000L or it }
                    8 -> h.toLongOrNull(16)
                    else -> null
                }
            }
            else -> null
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (onSwitchChanged != null && switchValue != null) onSwitchChanged(!switchValue)
                else if (onColorTap != null) onColorTap()
                else onTap?.invoke()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(effBg, CircleShape).border(1.2.dp, effIcon.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconContent != null -> iconContent()
                icon != null -> Icon(icon, null, tint = effIcon, modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Rounded.Circle, null, tint = effIcon, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(config.primaryTextColor), fontSize = 14.5.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.1).sp)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color(config.secondaryTextColor), fontSize = 12.sp, lineHeight = 15.sp)
            }
        }
        when {
            switchValue != null && onSwitchChanged != null -> GbCustomSwitch(switchValue, onSwitchChanged)
            effColor != null -> Box(
                Modifier.size(26.dp).background(Color(effColor), CircleShape).border(1.5.dp, colors.borderSubtle, CircleShape)
                    .clickable { onColorTap?.invoke() },
            )
            trailing != null -> trailing()
            showChevron || hasSubScreen -> Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun GbSwitchTile(icon: ImageVector, title: String, value: Boolean, onChanged: (Boolean) -> Unit, subtitle: String? = null) {
    GbSettingRow(title = title, icon = icon, subtitle = subtitle, switchValue = value, onSwitchChanged = onChanged)
}

@Composable
fun GbSliderTile(
    title: String,
    value: Float,
    onChanged: (Float) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    min: Float = 10f,
    max: Float = 24f,
    divisions: Int? = null,
    suffix: String = "",
    unit: String? = null,
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val accent = Color(config.accentColor)
    val steps = ((divisions ?: (max - min).toInt()) - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(title, Modifier.weight(1f), color = Color(config.primaryTextColor), fontSize = 14.sp, fontWeight = FontWeight.W600)
            Box(Modifier.background(accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${value.toInt()}${unit ?: suffix}", color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!subtitle.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(config.secondaryTextColor), fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChanged,
            valueRange = min..max,
            steps = steps,
            colors = SliderDefaults.colors(
                activeTrackColor = accent, inactiveTrackColor = colors.borderSubtle, thumbColor = accent,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun GbSectionDivider(label: String, accent: Color? = null) {
    val eff = accent ?: Color(LocalThemeConfig.current.accentColor)
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(eff.copy(alpha = 0f), eff.copy(alpha = 0.4f)))))
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(eff, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, color = eff, fontSize = 12.5.sp, fontWeight = FontWeight.W700, letterSpacing = 0.4.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(5.dp).background(eff, CircleShape))
        }
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(eff.copy(alpha = 0.4f), eff.copy(alpha = 0f)))))
    }
}

@Composable
fun GbCardContainer(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(vertical = 4.dp),
    margin: PaddingValues = PaddingValues(vertical = 6.dp),
    headerText: String? = null,
    content: @Composable () -> Unit,
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(config.cornerRadius.toFloat().dp)
    val card: @Composable () -> Unit = {
        Column(
            modifier.fillMaxWidth().padding(margin)
                .shadow(8.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(colors.surface, shape)
                .border(0.9.dp, colors.borderSubtle, shape)
                .padding(padding),
        ) { content() }
    }
    if (!headerText.isNullOrEmpty()) {
        Column {
            Text(
                headerText, Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                color = Color(config.accentColor), fontSize = 13.sp, fontWeight = FontWeight.W700, letterSpacing = 0.4.sp,
            )
            card()
        }
    } else card()
}

@Composable
fun GbLivePreviewCard(
    modifier: Modifier = Modifier,
    label: String = "LIVE INTERACTIVE PREVIEW",
    height: Dp? = null,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit,
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val accent = Color(config.accentColor)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp)
            .shadow(12.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surfaceElevated, shape)
            .border(1.2.dp, accent.copy(alpha = 0.35f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.W800, letterSpacing = 0.6.sp)
        }
        Box(Modifier.padding(padding).let { if (height != null) it.height(height) else it }) { content() }
    }
}
