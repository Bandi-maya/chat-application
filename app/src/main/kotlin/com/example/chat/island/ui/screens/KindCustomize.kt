package com.example.chat.island.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.island.engine.DynamicIslandUi
import com.example.chat.island.core.IslandKind
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.engine.IslandMode
import com.example.chat.island.engine.IslandLevel
import com.example.chat.island.engine.LiveKind
import com.example.chat.island.engine.ToggleKind
import com.example.chat.island.core.KindStyle
import java.util.Locale
import kotlin.math.roundToInt


private enum class KindGroup(val titleRes: Int) {
    Banners(R.string.kinds_group_banners),
    CallMediaTimer(R.string.kinds_group_call_media_timer),
    LiveActivities(R.string.kinds_group_live_activities),
}

private fun groupOf(kind: IslandKind): KindGroup = when (kind) {
    IslandKind.Notification,
    IslandKind.Charging,
    IslandKind.Unplugged,
    IslandKind.LowBattery,
    IslandKind.Device,
    IslandKind.Silent,
    IslandKind.Vibrate,
    IslandKind.Dnd,
    IslandKind.Flashlight,
    -> KindGroup.Banners

    IslandKind.Call,
    IslandKind.Media,
    IslandKind.Timer,
    -> KindGroup.CallMediaTimer

    IslandKind.Navigation,
    IslandKind.Tracking,
    IslandKind.Download,
    -> KindGroup.LiveActivities
}

@Composable
fun KindCustomizeCard() {
    val styles by IslandSettings.kindStyles.collectAsState()
    var editingKind by remember { mutableStateOf<IslandKind?>(null) }
    var editingAll by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.kinds_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { editingAll = true }) {
                    Text(stringResource(R.string.kinds_edit_all))
                }
            }

            KindGroup.entries.forEach { group ->
                Text(
                    stringResource(group.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IslandKind.entries
                    .filter { groupOf(it) == group }
                    .forEach { kind ->
                        KindRow(
                            kind = kind,
                            style = styles[kind.key] ?: KindStyle(),
                            onEdit = { editingKind = kind },
                        )
                    }
            }
        }
    }

    editingKind?.let { kind ->
        KindStyleEditorDialog(
            title = stringResource(kind.label),
            kind = kind,
            initial = styles[kind.key] ?: KindStyle(),
            showDuration = kind.popup,
            onDismiss = { editingKind = null },
            onApply = { style ->
                IslandSettings.setKindStyle(kind, style)
                IslandSettings.save()
                editingKind = null
            },
            onReset = {
                IslandSettings.setKindStyle(kind, KindStyle())
                IslandSettings.save()
                editingKind = null
            },
        )
    }

    if (editingAll) {
        KindStyleEditorDialog(
            title = stringResource(R.string.kinds_edit_all),
            kind = IslandKind.Notification,
            initial = KindStyle(),
            showDuration = true,
            onDismiss = { editingAll = false },
            onApply = { style ->
                IslandKind.entries.forEach { IslandSettings.setKindStyle(it, style) }
                IslandSettings.save()
                editingAll = false
            },
            onReset = {
                IslandKind.entries.forEach { IslandSettings.setKindStyle(it, KindStyle()) }
                IslandSettings.save()
                editingAll = false
            },
        )
    }
}

@Composable
private fun KindRow(
    kind: IslandKind,
    style: KindStyle,
    onEdit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(kind.label), style = MaterialTheme.typography.bodyMedium)
                if (kind.popup) {
                    Text(
                        stringResource(R.string.kinds_popup_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.kinds_edit)) }
        }

        // Live compact/banner and expanded previews of the current style.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            KindPreview(
                kind = kind,
                style = style,
                expanded = false,
                caption = stringResource(R.string.kinds_preview_compact),
            )
            KindPreview(
                kind = kind,
                style = style,
                expanded = true,
                caption = stringResource(R.string.kinds_preview_expanded),
            )
        }
    }
}

@Composable
private fun KindPreview(kind: IslandKind, style: KindStyle, expanded: Boolean, caption: String) {
    val globalCorner by IslandSettings.corner.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DynamicIslandUi(
            mode = previewMode(kind),
            level = if (expanded) IslandLevel.Expanded else if (kind.popup) IslandLevel.Banner else IslandLevel.Compact,
            scale = 0.65f,
            cornerFactor = if (style.corner >= 0f) style.corner else globalCorner,
            animSpeed = 1f,
            glow = false,
            haptics = false,
            onClick = {},
            styleOverride = style,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KindStyleEditorDialog(
    title: String,
    kind: IslandKind,
    initial: KindStyle,
    showDuration: Boolean,
    onDismiss: () -> Unit,
    onApply: (KindStyle) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KindPreview(kind = kind, style = draft, expanded = false, caption = "")
                        Spacer(Modifier.padding(horizontal = 12.dp))
                        KindPreview(kind = kind, style = draft, expanded = true, caption = "")
                    }
                }

                StyleSlider(stringResource(R.string.kinds_width), draft.w, 0.8f..1.8f) {
                    draft = draft.copy(w = it)
                }
                StyleSlider(stringResource(R.string.kinds_height), draft.h, 0.8f..2f) {
                    draft = draft.copy(h = it)
                }
                StyleSlider(stringResource(R.string.kinds_expanded_width), draft.cardW, 0.7f..1.3f) {
                    draft = draft.copy(cardW = it)
                }
                StyleSlider(stringResource(R.string.kinds_expanded_height), draft.cardH, 0.7f..1.6f) {
                    draft = draft.copy(cardH = it)
                }
                StyleSlider(stringResource(R.string.kinds_text_scale), draft.text, 0.8f..1.8f) {
                    draft = draft.copy(text = it)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.kinds_inherit_corner),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = draft.corner < 0f,
                        onCheckedChange = { inherit ->
                            draft = draft.copy(corner = if (inherit) -1f else 1f)
                        },
                    )
                }
                if (draft.corner >= 0f) {
                    StyleSlider(stringResource(R.string.kinds_roundness), draft.corner, 0.2f..1f) {
                        draft = draft.copy(corner = it)
                    }
                }

                if (showDuration) {
                    StyleSlider(
                        label = stringResource(R.string.kinds_popup_duration),
                        value = draft.durationSec.toFloat(),
                        range = 0f..10f,
                        format = {
                            val sec = it.roundToInt()
                            if (sec == 0) {
                                stringResource(R.string.kinds_popup_duration_default)
                            } else {
                                stringResource(R.string.kinds_seconds_value, sec)
                            }
                        },
                    ) { draft = draft.copy(durationSec = it.roundToInt()) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) {
                Text(stringResource(R.string.kinds_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(stringResource(R.string.kinds_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.kinds_cancel)) }
            }
        },
    )
}

@Composable
private fun StyleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: @Composable (Float) -> String = { String.format(Locale.US, "%.2f", it) },
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                format(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}


private fun previewMode(kind: IslandKind): IslandMode = when (kind) {
    IslandKind.Notification -> IslandMode.Notification("Chaty", "New message", "Your message preview", true, true)
    IslandKind.Charging -> IslandMode.Charging(72, true)
    IslandKind.Unplugged -> IslandMode.Charging(72, false)
    IslandKind.LowBattery -> IslandMode.LowBattery(15)
    IslandKind.Device -> IslandMode.Device("Headphones", true)
    IslandKind.Silent -> IslandMode.Toggle(ToggleKind.Silent, true)
    IslandKind.Vibrate -> IslandMode.Toggle(ToggleKind.Vibrate, true)
    IslandKind.Dnd -> IslandMode.Toggle(ToggleKind.Dnd, true)
    IslandKind.Flashlight -> IslandMode.Toggle(ToggleKind.Flashlight, true)
    IslandKind.Call -> IslandMode.Call("Alex", true, 0)
    IslandKind.Media -> IslandMode.Media("Evening playlist", "Music", true, durationMs = 240000, positionMs = 80000)
    IslandKind.Timer -> IslandMode.Timer(225, 300)
    IslandKind.Navigation -> IslandMode.Live(LiveKind.Navigation, "Maps", "Turn left", "In 200 metres")
    IslandKind.Tracking -> IslandMode.Live(LiveKind.Tracking, "Delivery", "On the way", "Arriving in 10 minutes")
    IslandKind.Download -> IslandMode.Live(LiveKind.Download, "Downloads", "Photo backup", "58% complete", 58)
}
