package com.example.chat.ui.settings

import androidx.compose.ui.semantics.disabled

import androidx.compose.ui.semantics.toggleableState

import androidx.compose.ui.semantics.role

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.clearAndSetSemantics

import androidx.compose.foundation.selection.selectable

import androidx.compose.foundation.selection.toggleable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.theme.LocalAppColors

/**
 * Faithful port of lib/ui/core/design_system/settings_primitives.dart
 * (ChatySettingsPage/Section/Card/Tile/SwitchTile/ColorTile/SliderTile/RadioTile/
 * ChoiceTile/ExpandableTile/InfoTile/DangerTile/PreviewCard/SectionDivider/Badge/Switch/Slider).
 *
 * Replaces the earlier approximate SettingsComponents.kt look for new screens. Icons are real
 * Material vectors (Icons.Rounded.*), as Dart uses `Icons.*_rounded`.
 * UNCOMPILED (Compose).
 *
 * Mapping of Flutter theme values: theme.cardColor = cardColor (surfaceSecondary), dividerColor =
 * secondaryText @0.2, bodyLarge colour = primaryText, bodyMedium colour = secondaryText,
 * hintColor ~ foreground @0.6, disabledColor ~ foreground @0.38.
 */
/** Marker so the card can skip separators next to a section divider (Dart `is! ChatySectionDivider`). */
@Composable
fun ChatySectionDivider() {
    val colors = LocalAppColors.current
    HorizontalDivider(thickness = 1.dp, color = colors.foregroundSecondary.copy(alpha = 0.15f))
}

@Composable
fun ChatySettingsPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailingHeaderWidget: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ChatyAppBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            actions = { trailingHeaderWidget?.invoke() },
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item { Column { content() } }
        }
        bottomBar?.invoke()
    }
}

@Composable
fun ChatySettingsSection(
    title: String? = null,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title.uppercase(),
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp, top = 12.dp),
                color = colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 1.1.sp,
            )
        }
        ChatySettingsCard(content = content)
        if (description != null) {
            Text(
                description,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
                color = colors.foregroundSecondary.copy(alpha = 0.65f),
                fontSize = 12.sp,
                lineHeight = 16.2.sp,
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Card: radius 16, 1dp border at dividerColor@0.12, no elevation, clipped. Rows use [ChatySettingsRowDivider]. */
@Composable
fun ChatySettingsCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor ?: colors.surfaceSecondary, shape)
            .border(1.dp, colors.foregroundSecondary.copy(alpha = 0.12f), shape)
            .padding(padding),
    ) { content() }
}

/** Separator between rows (Divider 1 / thickness 0.7 / indent 52 / endIndent 12 / @0.15). Call between rows. */
@Composable
fun ChatySettingsRowDivider() {
    val colors = LocalAppColors.current
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 12.dp),
        thickness = 0.7.dp,
        color = colors.foregroundSecondary.copy(alpha = 0.15f),
    )
}

@Composable
fun ChatyBadge(text: String, color: Color? = null) {
    val colors = LocalAppColors.current
    val bg = color ?: colors.primary
    Box(
        Modifier
            .background(bg.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) { Text(text, color = bg, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ChatySwitch(value: Boolean, onChanged: ((Boolean) -> Unit)?, interactive: Boolean = true) {
    val colors = LocalAppColors.current
    if (onChanged != null) {
        Box(if (interactive) Modifier.toggleable(value, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChanged) else Modifier) {
            Box(Modifier.clearAndSetSemantics { }) {
                com.example.chat.ui.kit.GbCustomSwitch(value, onChanged, interactive = false)
            }
        }
    } else {
        Box(
            Modifier.size(width = 52.dp, height = 30.dp)
                .background(if (value) colors.primary.copy(alpha = 0.45f) else colors.borderSubtle, RoundedCornerShape(15.dp))
                .semantics { role = androidx.compose.ui.semantics.Role.Switch; toggleableState = androidx.compose.ui.state.ToggleableState(value); disabled() }
                .padding(3.dp),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(22.dp).background(colors.foregroundTertiary, CircleShape)) }
    }
}

@Composable
fun ChatySlider(value: Float, onChanged: (Float) -> Unit, min: Float = 0f, max: Float = 1f, divisions: Int? = null) {
    val colors = LocalAppColors.current
    Slider(
        value = value.coerceIn(min, max),
        onValueChange = onChanged,
        valueRange = min..max,
        steps = if (divisions != null && divisions > 1) divisions - 1 else 0,
        colors = SliderDefaults.colors(
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.primary.copy(alpha = 0.18f),
            thumbColor = colors.primary,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/** ListTile: padding h14 v2, leading icon box 34/r10 (iconColor@0.12), title 14.5 w600, subtitle 12.5 @0.7. */
@Composable
fun ChatySettingsTile(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    badgeText: String? = null,
    badgeColor: Color? = null,
    onTap: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val tint = iconColor ?: colors.primary
    val base = if (onTap != null && enabled) modifier.clickable(onClick = onTap) else modifier
    Row(
        base.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp).defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        } else if (icon != null) {
            Box(
                Modifier.size(34.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (enabled) colors.foreground else colors.foreground.copy(alpha = 0.38f),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.W600,
                )
                if (badgeText != null) {
                    Spacer(Modifier.width(6.dp))
                    ChatyBadge(badgeText, badgeColor)
                }
            }
            if (subtitle != null) {
                Text(subtitle, color = colors.foregroundSecondary.copy(alpha = 0.7f), fontSize = 12.5.sp)
            }
        }
        val trail = trailing
        if (trail != null) {
            Spacer(Modifier.width(16.dp))
            trail()
        } else if (onTap != null) {
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.foreground.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ChatySwitchTile(
    title: String,
    value: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Box(Modifier.toggleable(
        value = value, enabled = enabled && onChanged != null,
        role = androidx.compose.ui.semantics.Role.Switch,
        onValueChange = { onChanged?.invoke(it) },
    )) {
        ChatySettingsTile(
            icon = icon, iconColor = iconColor, title = title, subtitle = subtitle, enabled = enabled,
            onTap = null,
            trailing = {
                Box(Modifier.clearAndSetSemantics { }) {
                    ChatySwitch(value, if (enabled) onChanged else null, interactive = false)
                }
            },
        )
    }
}

@Composable
fun ChatyColorTile(
    title: String,
    color: Color,
    onTap: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    ChatySettingsTile(
        icon = icon,
        iconColor = color,
        title = title,
        subtitle = subtitle,
        onTap = onTap,
        trailing = {
            Box(
                Modifier
                    .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                    .size(32.dp)
                    .background(color, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            )
        },
    )
}

@Composable
fun ChatySliderTile(
    title: String,
    value: Float,
    onChanged: (Float) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    min: Float = 0f,
    max: Float = 1f,
    divisions: Int? = null,
    valueFormatter: ((Float) -> String)? = null,
) {
    val colors = LocalAppColors.current
    val display = valueFormatter?.invoke(value) ?: String.format("%.1f", value)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(32.dp).background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
            }
            Text(title, Modifier.weight(1f), color = colors.foreground, fontSize = 14.5.sp, fontWeight = FontWeight.W600)
            Box(
                Modifier.background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text(display, color = colors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = colors.foregroundSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        ChatySlider(value, onChanged, min, max, divisions)
    }
}

@Composable
fun <T> ChatyRadioTile(
    title: String,
    value: T,
    groupValue: T,
    onChanged: (T) -> Unit,
    subtitle: String? = null,
) {
    val colors = LocalAppColors.current
    val selected = value == groupValue
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onChanged(value) }).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                title,
                color = if (selected) colors.primary else colors.foreground,
                fontSize = 14.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.W500,
            )
            if (subtitle != null) {
                Text(subtitle, color = colors.foregroundSecondary.copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }
        Box(
            Modifier.size(22.dp).border(2.dp, if (selected) colors.primary else colors.borderSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).background(colors.primary, CircleShape))
        }
    }
}

/**
 * Draft choice selector. Existing explicit external modal hosts retain their route; all other callers
 * open the centered shared selector and commit only on Apply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChatyChoiceTile(
    title: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    subtitle: String? = null,
    requireApply: Boolean = false,
    onOpenModal: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    var showChoices by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable {
            if (requireApply && onOpenModal != null) onOpenModal() else showChoices = true
        }.padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, color = colors.foreground, fontSize = 14.5.sp, fontWeight = FontWeight.W600)
            Text(
                if (subtitle == null) "Selected: ${optionLabel(selectedOption)}"
                else "$subtitle\nSelected: ${optionLabel(selectedOption)}",
                color = colors.foregroundSecondary, fontSize = 14.sp,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.foreground.copy(alpha = 0.6f))
    }
    if (showChoices) com.example.chat.ui.kit.ChatySingleChoiceModal(
        title = "Select $title", value = selectedOption, options = options, labelBuilder = optionLabel,
        onApply = { choice -> onSelected(choice); showChoices = false },
        onDismiss = { showChoices = false },
    )
}

@Composable
fun ChatyExpandableTile(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    Column {
        ChatySettingsTile(
            icon = icon,
            title = title,
            subtitle = subtitle,
            onTap = { expanded = !expanded },
            trailing = {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = colors.foreground.copy(alpha = 0.6f),
                )
            },
        )
        if (expanded) Column(Modifier.padding(start = 16.dp, bottom = 8.dp)) { content() }
    }
}

@Composable
fun ChatyInfoTile(message: String, icon: ImageVector = Icons.Outlined.Info, color: Color? = null) {
    val colors = LocalAppColors.current
    val c = color ?: colors.primary
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(12.dp).fillMaxWidth()
            .background(c.copy(alpha = 0.08f), shape)
            .border(1.dp, c.copy(alpha = 0.2f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, Modifier.weight(1f), color = c, fontSize = 12.5.sp, lineHeight = 16.9.sp)
    }
}

@Composable
fun ChatyDangerTile(title: String, onTap: () -> Unit, subtitle: String? = null) {
    val colors = LocalAppColors.current
    ChatySettingsTile(
        icon = Icons.Rounded.DeleteOutline,
        iconColor = colors.error,
        title = title,
        subtitle = subtitle,
        onTap = onTap,
        trailing = { Icon(Icons.Rounded.ArrowForwardIos, null, tint = colors.error, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
fun ChatyPreviewCard(title: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            color = colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        )
        Box(
            Modifier.fillMaxWidth()
                .background(colors.surfaceSecondary, shape)
                .border(1.dp, colors.foregroundSecondary.copy(alpha = 0.15f), shape)
                .padding(14.dp),
        ) { content() }
        Spacer(Modifier.height(14.dp))
    }
}
