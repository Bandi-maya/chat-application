package com.example.chat.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.theme.LocalAppColors

/**
 * Re-reads preference-derived UI whenever the controller notifies, mirroring
 * the Dart ListenableBuilder(listenable: preferencesController). The returned
 * Int is only a recomposition key; read controller state after calling it.
 */
@Composable
fun rememberPreferencesVersion(controller: ChatyPreferencesController): Int {
    var version by remember(controller) { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val l: () -> Unit = { version += 1 }
        controller.addListener(l)
        onDispose { controller.removeListener(l) }
    }
    return version
}

/** Port of ChatySettingsPage: title bar with back, subtitle, scrolling body. */
@Composable
fun SettingsPage(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("\u2039", color = colors.foreground, fontSize = 30.sp) }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(title, color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.5.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 36.dp)) {
            content()
        }
    }
}

/** Port of ChatySettingsSection: accent title, optional description, rounded card. */
@Composable
fun SettingsSection(
    title: String?,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
        if (title != null) {
            Text(
                title,
                color = colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (description == null) 8.dp else 2.dp),
            )
        }
        if (description != null) {
            Text(
                description,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.borderSubtle), RoundedCornerShape(24.dp)),
        ) { content() }
    }
}

/** Inset divider between rows inside a section card (GB style, indent 68). */
@Composable
fun SettingsDivider() {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp).height(1.dp).background(colors.divider))
}

/** Ringed glyph slot. Icons are text glyphs until icons-extended is approved. */
@Composable
fun SettingsIconSlot(glyph: String, tint: Color) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(BorderStroke(1.5.dp, tint), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = tint, fontSize = 18.sp) }
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier) {
        Text(title, color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 16.sp)
        }
    }
}

/** Port of _SettingsItemTile / ChatySettingsTile (navigation row with chevron). */
@Composable
fun SettingsNavTile(
    glyph: String,
    title: String,
    subtitle: String? = null,
    iconTint: Color = LocalAppColors.current.primary,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconSlot(glyph, iconTint)
        Spacer(Modifier.width(12.dp))
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("\u203A", color = colors.foregroundSecondary, fontSize = 22.sp)
    }
}

/** Port of ChatySwitchTile. */
@Composable
fun SettingsSwitchTile(
    glyph: String,
    title: String,
    subtitle: String? = null,
    value: Boolean,
    iconTint: Color = LocalAppColors.current.primary,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconSlot(glyph, iconTint)
        Spacer(Modifier.width(12.dp))
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
            ),
        )
    }
}

/** Single-choice row group (radio style) for string-valued options. */
@Composable
fun SettingsChoiceTile(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(title, color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .border(BorderStroke(2.dp, if (option == selected) colors.primary else colors.border), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (option == selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
                }
                Text(option, color = colors.foreground, fontSize = 14.sp)
            }
        }
    }
}

/** Port of ChatySliderTile (divisions = number of intervals, as in Flutter). */
@Composable
fun SettingsSliderTile(
    glyph: String,
    title: String,
    value: Double,
    min: Double,
    max: Double,
    divisions: Int,
    format: (Double) -> String,
    onChange: (Double) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIconSlot(glyph, colors.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(format(value), color = colors.foregroundSecondary, fontSize = 13.sp)
        }
        androidx.compose.material3.Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val step = (max - min) / divisions
                val snapped = min + Math.round((v - min) / step) * step
                onChange(snapped)
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = divisions - 1,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
            ),
        )
    }
}

/** Port of GbRadioSelectionModal: titled dialog, one radio row per option,
 *  selecting applies and dismisses. */
@Composable
fun SettingsRadioDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    com.example.chat.ui.kit.ChatyConfirmationDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        title = { Text(title, color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(option); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .border(BorderStroke(2.dp, if (option == selected) colors.primary else colors.border), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (option == selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary)) }
                        Text(option, color = colors.foreground, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = colors.primary) }
        },
    )
}
