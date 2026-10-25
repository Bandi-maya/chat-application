package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Kotlin port of the GB-styled pieces from lib/ui/core/design_system/gb_design_system.dart that settings
 * screens use (GbModalSheet, GbRadioSelectionModal, GbColorPickerModal -> ChatyColorPickerModal) plus the
 * row helpers that privacy / conversation / header settings each declare locally in Dart
 * (_buildCardContainer/_buildSectionDivider/_buildSwitchRow/_buildChevronRow/_buildColorRow/_buildSubpageRow/_buildDivider).
 * UNCOMPILED (Compose).
 */

@Composable
fun GbModalSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val config = LocalThemeConfig.current
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 480.dp)
                .shadow(28.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(colors.surfaceElevated, shape)
                .border(1.dp, colors.borderSubtle, shape)
                .clip(shape),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(38.dp).height(4.dp).background(colors.foregroundTertiary.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
            }
            Row(Modifier.padding(start = 20.dp, top = 10.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color(config.primaryTextColor), fontSize = 18.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp)
                    if (!subtitle.isNullOrEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(subtitle, color = Color(config.secondaryTextColor), fontSize = 12.5.sp)
                    }
                }
                trailing?.invoke()
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = colors.foregroundSecondary, modifier = Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = colors.divider, thickness = 1.dp)
            Box(Modifier.weight(1f, fill = false)) { content() }
        }
    }
}

/** GbRadioSelectionModal. [onSelected] is called after the dialog closes, like Dart pop-then-callback. */
@Composable
fun <T> GbRadioSelectionModal(
    title: String,
    options: List<T>,
    selectedOption: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    labelBuilder: ((T) -> String)? = null,
    subtitleBuilder: ((T) -> String?)? = null,
    iconBuilder: (@Composable (T) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val config = LocalThemeConfig.current
    val haptic = LocalHapticFeedback.current
    val accent = Color(config.accentColor)
    val primary = Color(config.primaryTextColor)
    GbModalSheet(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        LazyColumn(Modifier.padding(vertical = 8.dp, horizontal = 10.dp)) {
            itemsIndexed(options) { i, opt ->
                val sel = opt == selectedOption
                val label = labelBuilder?.invoke(opt) ?: opt.toString()
                val sub = subtitleBuilder?.invoke(opt)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                            onSelected(opt)
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (iconBuilder != null) { iconBuilder(opt); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            color = if (sel) primary else primary.copy(alpha = 0.85f),
                            fontSize = 14.5.sp,
                            fontWeight = if (sel) FontWeight.W700 else FontWeight.W500,
                        )
                        if (!sub.isNullOrEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(sub, color = Color(config.secondaryTextColor), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(22.dp).border(2.dp, if (sel) accent else colors.border, CircleShape).padding(3.5.dp),
                    ) { if (sel) Box(Modifier.fillMaxWidth().height(22.dp).background(accent, CircleShape)) }
                }
                if (i < options.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 48.dp), color = colors.divider.copy(alpha = 0.4f), thickness = 1.dp)
                }
            }
        }
    }
}

/** ChatyColorPickerModal (bottom sheet). [onColorSelected] receives ARGB Long after Apply. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatyColorPickerModal(
    title: String,
    currentColor: Long,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    backgroundContextColor: Long? = null,
) {
    val colors = LocalAppColors.current
    var color by remember { mutableStateOf(currentColor) }
    var hex by remember { mutableStateOf(ColorPickerLogic.toHex(currentColor)) }
    val ratio = backgroundContextColor?.let { ColorPickerLogic.contrastRatio(color, it) }
    val warn = ColorPickerLogic.hasContrastWarning(ratio)
    val statusColor = if (warn) colors.error else colors.success
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
    ) {
        val imeBottom = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = imeBottom + 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(Color(color), CircleShape).border(2.dp, Color.White, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = colors.foreground) }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(backgroundContextColor?.let { Color(it) } ?: colors.surfaceSecondary, RoundedCornerShape(16.dp))
                    .border(1.dp, LocalThemeConfig.current.let { Color(it.secondaryTextColor).copy(alpha = 0.2f) }, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sample Text Preview", Modifier.weight(1f), color = Color(color), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (ratio != null) {
                    Row(
                        Modifier.background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (warn) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle, null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(String.format("%.1f:1", ratio), color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (warn) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Low contrast warning! Text may be hard to read.", Modifier.weight(1f), color = colors.error, fontSize = 12.sp)
                    TextButton(onClick = {
                        val bg = backgroundContextColor ?: return@TextButton
                        // Dart: colors.foreground / colors.onPrimary tokens
                        val fixed = ColorPickerLogic.autoFix(bg, colorToArgb(colors.foreground), colorToArgb(colors.onPrimary))
                        color = fixed; hex = ColorPickerLogic.toHex(fixed)
                    }) {
                        Icon(Icons.Rounded.AutoFixHigh, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Auto Fix Contrast", fontSize = 12.sp, color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Preset Swatches", fontSize = 13.sp, fontWeight = FontWeight.W600, color = colors.foreground)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorPickerLogic.presetSwatches.forEach { c ->
                    val sel = c == color
                    Box(
                        Modifier.size(40.dp).background(Color(c), CircleShape)
                            .border(if (sel) 3.dp else 1.5.dp, if (sel) colors.primary else colors.border, CircleShape)
                            .clip(CircleShape)
                            .clickable { color = c; hex = ColorPickerLogic.toHex(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sel) Icon(
                            Icons.Rounded.Check, null, modifier = Modifier.size(20.dp),
                            tint = if (ColorPickerLogic.luminance(c) > 0.5) Color.Black else Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it; ColorPickerLogic.fromHex(it)?.let { v -> color = v } },
                    modifier = Modifier.weight(1f),
                    label = { Text("HEX Color") },
                    prefix = { Text("# ") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { onColorSelected(color) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                ) { Text("Apply Color", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun colorToArgb(c: Color): Long = (c.value shr 32).toLong() and 0xFFFFFFFFL

// ---- GB row helpers (declared locally in several Dart settings screens, identical look) ----

@Composable
fun GbPanelCard(content: @Composable () -> Unit) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(if (config.isDark) Color(0xFF1E2124) else colors.surface, shape)
            .border(0.9.dp, colors.borderSubtle, shape)
            .clip(shape),
    ) { content() }
}

@Composable
fun GbSectionLabel(label: String) {
    val accent = Color(LocalThemeConfig.current.accentColor)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp)) {
        Text(label, Modifier.fillMaxWidth(), color = accent, fontWeight = FontWeight.W700, fontSize = 16.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.2.dp).background(
            Brush.horizontalGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.7f), accent.copy(alpha = 0f)))))
    }
}

@Composable
fun GbRowDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), thickness = 0.8.dp, color = LocalAppColors.current.borderSubtle.copy(alpha = 0.4f))
}

@Composable
private fun GbIconBox(icon: ImageVector, size: Int, radius: Int, iconSize: Int) {
    val accent = Color(LocalThemeConfig.current.accentColor)
    Box(Modifier.size(size.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(radius.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun GbTextBlock(title: String, subtitle: String?, modifier: Modifier) {
    val config = LocalThemeConfig.current
    val fs = config.fontScale.toFloat()
    Column(modifier) {
        Text(title, color = Color(config.primaryTextColor), fontSize = (14.5f * fs).sp, fontWeight = FontWeight.W600)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color(config.secondaryTextColor), fontSize = (11.5f * fs).sp)
        }
    }
}

@Composable
fun GbSwitchRow(icon: ImageVector, title: String, subtitle: String, value: Boolean, onChanged: (Boolean) -> Unit) {
    val accent = Color(LocalThemeConfig.current.accentColor)
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChanged(!value) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GbIconBox(icon, 38, 11, 20)
        Spacer(Modifier.width(14.dp))
        GbTextBlock(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = value, onCheckedChange = null, enabled = false,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                disabledCheckedThumbColor = accent, disabledCheckedTrackColor = accent.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
fun GbChevronRow(icon: ImageVector, title: String, subtitle: String, onTap: () -> Unit) {
    val accent = Color(LocalThemeConfig.current.accentColor)
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GbIconBox(icon, 38, 11, 20)
        Spacer(Modifier.width(14.dp))
        GbTextBlock(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun GbSubpageRow(icon: ImageVector, title: String, onTap: () -> Unit) {
    val config = LocalThemeConfig.current
    val accent = Color(config.accentColor)
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GbIconBox(icon, 40, 12, 21)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), color = Color(config.primaryTextColor), fontSize = (15.5f * config.fontScale.toFloat()).sp, fontWeight = FontWeight.W600)
        Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun GbColorRow(icon: ImageVector, title: String, colorValue: Long, onTap: () -> Unit) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GbIconBox(icon, 38, 11, 20)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), color = Color(config.primaryTextColor), fontSize = (14.5f * config.fontScale.toFloat()).sp, fontWeight = FontWeight.W600)
        Box(Modifier.size(26.dp).background(Color(colorValue), CircleShape).border(1.5.dp, colors.borderSubtle, CircleShape))
    }
}

// ---- Imperative-looking helpers for Compose (Dart showDialog / showModalBottomSheet call sites) ----

class GbPickers {
    internal var radio by mutableStateOf<RadioSpec?>(null)
    internal var color by mutableStateOf<ColorSpec?>(null)
    internal class RadioSpec(val title: String, val options: List<String>, val selected: String, val onSelected: (String) -> Unit, val subtitle: String?)
    internal class ColorSpec(val title: String, val current: Long, val onChanged: (Long) -> Unit, val background: Long?)

    /** GbRadioSelectionModal.show<String>. */
    fun showRadio(title: String, options: List<String>, selected: String, subtitle: String? = null, onSelected: (String) -> Unit) {
        radio = RadioSpec(title, options, selected, onSelected, subtitle)
    }

    /** GbColorPickerModal.show: [onChanged] runs only when the user applies a colour. */
    fun showColor(title: String, current: Long, backgroundContextColor: Long? = null, onChanged: (Long) -> Unit) {
        color = ColorSpec(title, current, onChanged, backgroundContextColor)
    }

    @Composable
    fun Host() {
        radio?.let { r ->
            GbRadioSelectionModal(
                title = r.title, subtitle = r.subtitle, options = r.options, selectedOption = r.selected,
                onSelected = r.onSelected, onDismiss = { radio = null },
            )
        }
        color?.let { c ->
            ChatyColorPickerModal(
                title = c.title, currentColor = c.current, backgroundContextColor = c.background,
                onColorSelected = { v -> color = null; c.onChanged(v) }, onDismiss = { color = null },
            )
        }
    }
}

@Composable
fun rememberGbPickers(): GbPickers = remember { GbPickers() }

/**
 * Shared screen frame used by the GB-style settings screens: back button in 8dp padding, title
 * [titleSize]*fontScale w700 (letterSpacing -0.3 for 20sp titles), body padding h14 v10.
 */
@Composable
fun GbScreenScaffold(
    title: String,
    onBack: () -> Unit,
    titleSize: Float = 20f,
    appBarColor: Color? = null,
    letterSpacing: Float = -0.3f,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val config = LocalThemeConfig.current
    val fs = config.fontScale.toFloat()
    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth()
                .background(appBarColor ?: Color(config.backgroundColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text(title, color = Color(config.primaryTextColor), fontSize = (titleSize * fs).sp, fontWeight = FontWeight.W700, letterSpacing = letterSpacing.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            content = content,
        )
    }
}
