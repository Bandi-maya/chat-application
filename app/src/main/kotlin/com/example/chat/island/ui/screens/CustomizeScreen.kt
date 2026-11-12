package com.example.chat.island.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.island.core.AppLanguage
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.SwipeAction
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CustomizeScreen() {
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        ShapeSection()
        KindCustomizeCard()
        AnimationSection()
        PositionSection()
        BehaviorSection()
        SwipeSection()
        AccentSection()
        BackgroundSection()
        ThemeSection()
        LanguageSection()
        ResetSection()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Slider bound live to a settings flow; persists when editing completes. */
@Composable
private fun FlowSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: @Composable (Float) -> String = { String.format(Locale.US, "%.2f", it) },
    onLiveChange: (Float) -> Unit,
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
            onValueChange = onLiveChange,
            onValueChangeFinished = { IslandSettings.save() },
            valueRange = range,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = {
                onChecked(it)
                IslandSettings.save()
            },
        )
    }
}

// ---------------------------------------------------------------- Shape

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ShapeSection() {
    val scale by IslandSettings.scale.collectAsState()
    val widthScale by IslandSettings.widthScale.collectAsState()
    val heightScale by IslandSettings.heightScale.collectAsState()
    val corner by IslandSettings.corner.collectAsState()

    SectionCard(stringResource(R.string.customize_shape_title)) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetButton(stringResource(R.string.customize_preset_small)) {
                IslandSettings.scale.value = 0.8f
                IslandSettings.widthScale.value = 0.85f
                IslandSettings.heightScale.value = 1f
                IslandSettings.save()
            }
            PresetButton(stringResource(R.string.customize_preset_normal)) {
                IslandSettings.resetShape()
                IslandSettings.save()
            }
            PresetButton(stringResource(R.string.customize_preset_large)) {
                IslandSettings.scale.value = 1.2f
                IslandSettings.widthScale.value = 1.1f
                IslandSettings.heightScale.value = 1.1f
                IslandSettings.save()
            }
            PresetButton(stringResource(R.string.customize_preset_wide)) {
                IslandSettings.scale.value = 1f
                IslandSettings.widthScale.value = 1.45f
                IslandSettings.heightScale.value = 1f
                IslandSettings.save()
            }
            PresetButton(stringResource(R.string.customize_preset_tall)) {
                IslandSettings.scale.value = 1f
                IslandSettings.widthScale.value = 1f
                IslandSettings.heightScale.value = 1.5f
                IslandSettings.save()
            }
        }
        FlowSlider(
            label = stringResource(R.string.customize_scale),
            value = scale,
            range = 0.5f..1.8f,
        ) { IslandSettings.scale.value = it }
        FlowSlider(
            label = stringResource(R.string.customize_width),
            value = widthScale,
            range = 0.6f..1.6f,
        ) { IslandSettings.widthScale.value = it }
        FlowSlider(
            label = stringResource(R.string.customize_height),
            value = heightScale,
            range = 0.7f..1.6f,
        ) { IslandSettings.heightScale.value = it }
        FlowSlider(
            label = stringResource(R.string.customize_corner),
            value = corner,
            range = 0.2f..1f,
        ) { IslandSettings.corner.value = it }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}

// ---------------------------------------------------------------- Animation

@Composable
private fun AnimationSection() {
    val animSpeed by IslandSettings.animSpeed.collectAsState()
    val glow by IslandSettings.glow.collectAsState()
    val haptics by IslandSettings.haptics.collectAsState()

    SectionCard(stringResource(R.string.customize_animation_title)) {
        FlowSlider(
            label = stringResource(R.string.customize_anim_speed),
            value = animSpeed,
            range = 0.5f..2f,
        ) { IslandSettings.animSpeed.value = it }
        ToggleRow(
            label = stringResource(R.string.customize_glow),
            checked = glow,
        ) { IslandSettings.glow.value = it }
        ToggleRow(
            label = stringResource(R.string.customize_haptics),
            checked = haptics,
        ) { IslandSettings.haptics.value = it }
    }
}

// ---------------------------------------------------------------- Position

@Composable
private fun PositionSection() {
    val offsetX by IslandSettings.offsetX.collectAsState()
    val offsetY by IslandSettings.offsetY.collectAsState()

    SectionCard(stringResource(R.string.customize_position_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.customize_offset_x),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                IslandSettings.offsetX.value = (offsetX - 1f).coerceIn(-180f, 180f)
                IslandSettings.save()
            }) { Icon(Icons.Outlined.Remove, contentDescription = null) }
            Text(
                stringResource(R.string.customize_dp_value, offsetX.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            IconButton(onClick = {
                IslandSettings.offsetX.value = (offsetX + 1f).coerceIn(-180f, 180f)
                IslandSettings.save()
            }) { Icon(Icons.Outlined.Add, contentDescription = null) }
            IconButton(onClick = {
                IslandSettings.offsetX.value = 0f
                IslandSettings.save()
            }) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null) }
        }
        FlowSlider(
            label = stringResource(R.string.customize_offset_x),
            value = offsetX,
            range = -180f..180f,
            format = { stringResource(R.string.customize_dp_value, it.roundToInt()) },
        ) { IslandSettings.offsetX.value = it }
        FlowSlider(
            label = stringResource(R.string.customize_offset_y),
            value = offsetY,
            range = 0f..300f,
            format = { stringResource(R.string.customize_dp_value, it.roundToInt()) },
        ) { IslandSettings.offsetY.value = it }
    }
}

// ---------------------------------------------------------------- Behavior

@Composable
private fun BehaviorSection() {
    val autoCollapseSec by IslandSettings.autoCollapseSec.collectAsState()
    val idlePill by IslandSettings.idlePill.collectAsState()
    val stickyNotif by IslandSettings.stickyNotif.collectAsState()
    val islandAppAccent by IslandSettings.islandAppAccent.collectAsState()
    val swipeSkip by IslandSettings.swipeSkip.collectAsState()

    SectionCard(stringResource(R.string.customize_behavior_title)) {
        FlowSlider(
            label = stringResource(R.string.customize_collapse_delay),
            value = autoCollapseSec.toFloat(),
            range = 0f..15f,
            format = {
                val sec = it.roundToInt()
                if (sec == 0) {
                    stringResource(R.string.customize_collapse_disabled)
                } else {
                    stringResource(R.string.customize_seconds_value, sec)
                }
            },
        ) { IslandSettings.autoCollapseSec.value = it.roundToInt() }
        ToggleRow(
            label = stringResource(R.string.customize_idle_pill),
            checked = idlePill,
        ) { IslandSettings.idlePill.value = it }
        ToggleRow(
            label = stringResource(R.string.customize_sticky_notif),
            checked = stickyNotif,
        ) { IslandSettings.stickyNotif.value = it }
        ToggleRow(
            label = stringResource(R.string.customize_app_accent),
            checked = islandAppAccent,
        ) { IslandSettings.islandAppAccent.value = it }
        ToggleRow(
            label = stringResource(R.string.customize_swipe_skip),
            checked = swipeSkip,
        ) { IslandSettings.swipeSkip.value = it }
    }
}

// ---------------------------------------------------------------- Swipe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionChooser(
    label: String,
    current: SwipeAction,
    onSelected: (SwipeAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(current.label),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SwipeAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    onClick = {
                        onSelected(action)
                        IslandSettings.save()
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwipeSection() {
    val swipeUp by IslandSettings.swipeUp.collectAsState()
    val swipeDown by IslandSettings.swipeDown.collectAsState()
    val swipeLeft by IslandSettings.swipeLeft.collectAsState()
    val swipeRight by IslandSettings.swipeRight.collectAsState()

    SectionCard(stringResource(R.string.customize_swipe_title)) {
        SwipeActionChooser(stringResource(R.string.customize_swipe_up), swipeUp) {
            IslandSettings.swipeUp.value = it
        }
        SwipeActionChooser(stringResource(R.string.customize_swipe_down), swipeDown) {
            IslandSettings.swipeDown.value = it
        }
        SwipeActionChooser(stringResource(R.string.customize_swipe_left), swipeLeft) {
            IslandSettings.swipeLeft.value = it
        }
        SwipeActionChooser(stringResource(R.string.customize_swipe_right), swipeRight) {
            IslandSettings.swipeRight.value = it
        }
    }
}

// ---------------------------------------------------------------- Accent

private val ACCENT_PRESETS: List<Long> = listOf(
    0xFF7B3DF5, // Chaty default purple
    0xFF3B82F6, // blue
    0xFF10B981, // green
    0xFFF59E0B, // amber
    0xFFEF4444, // red
    0xFFEC4899, // pink
)

@Composable
private fun AccentSection() {
    val accentSource by IslandSettings.accentSource.collectAsState()
    val accentSeed by IslandSettings.accentSeed.collectAsState()

    SectionCard(stringResource(R.string.customize_accent_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = accentSource == 0,
                onClick = {
                    IslandSettings.accentSource.value = 0
                    IslandSettings.save()
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            ) { Text(stringResource(R.string.customize_accent_wallpaper)) }
            SegmentedButton(
                selected = accentSource == 1,
                onClick = {
                    IslandSettings.accentSource.value = 1
                    IslandSettings.save()
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.customize_accent_custom)) }
        }

        if (accentSource == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENT_PRESETS.forEach { argb ->
                    ColorSwatch(argb = argb, selected = accentSeed == argb.toInt()) {
                        IslandSettings.accentSeed.value = argb.toInt()
                        IslandSettings.save()
                    }
                }
            }

            val hsv = remember(accentSeed) {
                FloatArray(3).also { android.graphics.Color.colorToHSV(accentSeed.toInt(), it) }
            }
            FlowSlider(
                label = stringResource(R.string.customize_hue),
                value = hsv[0],
                range = 0f..360f,
                format = { "${it.roundToInt()}\u00B0" },
            ) { newHue ->
                IslandSettings.accentSeed.value =
                    android.graphics.Color.HSVToColor(floatArrayOf(newHue, hsv[1], hsv[2]))
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Long, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = borderColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

// ---------------------------------------------------------------- Background

@Composable
private fun BackgroundSection() {
    val bgStyle by IslandSettings.bgStyle.collectAsState()
    val bgColor by IslandSettings.bgColor.collectAsState()

    SectionCard(stringResource(R.string.customize_background_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(
                R.string.customize_bg_default,
                R.string.customize_bg_tinted,
                R.string.customize_bg_custom,
            )
            labels.forEachIndexed { index, labelRes ->
                SegmentedButton(
                    selected = bgStyle == index,
                    onClick = {
                        IslandSettings.bgStyle.value = index
                        IslandSettings.save()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                ) { Text(stringResource(labelRes)) }
            }
        }

        if (bgStyle == 2) {
            CustomBackgroundControls(bgColor)
        }
    }
}

/**
 * Independent hue / saturation / brightness sliders for the custom island
 * background. The hue survives saturation or brightness hitting zero because
 * it is tracked separately from the stored color.
 */
@Composable
private fun CustomBackgroundControls(bgColor: Int) {
    val current = remember(bgColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(bgColor.toInt(), it) }
    }
    // -1 until the stored color carries a usable hue (saturation & value > 0).
    var rememberedHue by rememberSaveable { mutableFloatStateOf(-1f) }
    if (current[1] > 0.02f && current[2] > 0.02f) {
        rememberedHue = current[0]
    }
    val hue = if (rememberedHue >= 0f) rememberedHue else 0f

    fun write(h: Float, s: Float, v: Float) {
        IslandSettings.bgColor.value =
            android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    }

    FlowSlider(
        label = stringResource(R.string.customize_hue),
        value = hue,
        range = 0f..360f,
        format = { "${it.roundToInt()}\u00B0" },
    ) { newHue ->
        rememberedHue = newHue
        write(newHue, current[1], current[2])
    }
    FlowSlider(
        label = stringResource(R.string.customize_saturation),
        value = current[1],
        range = 0f..1f,
    ) { write(hue, it, current[2]) }
    FlowSlider(
        label = stringResource(R.string.customize_brightness),
        value = current[2],
        range = 0f..1f,
    ) { write(hue, current[1], it) }
}

// ---------------------------------------------------------------- Theme

@Composable
private fun ThemeSection() {
    val theme by IslandSettings.theme.collectAsState()
    val labels = listOf(
        R.string.theme_system,
        R.string.theme_light,
        R.string.theme_dark,
        R.string.theme_amoled,
    )
    SectionCard(stringResource(R.string.customize_theme_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, labelRes ->
                SegmentedButton(
                    selected = theme == index,
                    onClick = {
                        IslandSettings.theme.value = index
                        IslandSettings.save()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                ) { Text(stringResource(labelRes)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Language

@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    val current by AppLanguage.language.collectAsState()
    val options = listOf(
        AppLanguage.SYSTEM to R.string.lang_system,
        AppLanguage.ENGLISH to R.string.lang_english,
        AppLanguage.HINDI to R.string.lang_hindi,
    )
    SectionCard(stringResource(R.string.customize_language_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (language, labelRes) ->
                SegmentedButton(
                    selected = current == language,
                    onClick = {
                        AppLanguage.set(context, language)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(stringResource(labelRes)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Reset

@Composable
private fun ResetSection() {
    var confirm by remember { mutableStateOf(false) }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = { confirm = true }) {
                Text(
                    stringResource(R.string.customize_reset_all),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.customize_reset_all)) },
            text = { Text(stringResource(R.string.customize_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    IslandSettings.resetAll()
                    IslandSettings.save()
                    confirm = false
                }) { Text(stringResource(R.string.customize_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(R.string.customize_cancel))
                }
            },
        )
    }
}
