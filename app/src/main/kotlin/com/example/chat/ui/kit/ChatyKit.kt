package com.example.chat.ui.kit

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chat.ui.theme.LocalAppColors

/**
 * Faithful port of the Dart design-system kit pieces that settings screens use
 * (ChatyScaffold / ChatyAppBar / ChatyBackButton / ChatySingleChoiceModal /
 * ChatyMotion). Dart source: lib/ui/core/design_system/components/app_components.dart,
 * single_choice_modal.dart, chaty_motion.dart, tokens/app_tokens.dart.
 *
 * UNCOMPILED (Compose) - verified only by reading against the Dart.
 */
object ChatyMotionTokens {
    const val INSTANT_MS = 80   // chaty_motion.dart instant
    const val FAST_MS = 140
    const val BASE_MS = 220
    const val ACTIVE_ICON_SCALE = 0.92f
    const val ACTIVE_SCALE = 0.97f
    val enter = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f) // Curves.easeOutCubic
}

@Composable
fun rememberChatySelectionHaptic(): () -> Unit {
    val view = androidx.compose.ui.platform.LocalView.current
    return { ChatyHapticsPort.selection(view) }
}

/** ChatyBackButton: 34dp circle, surfaceSecondary fill, 0.8dp border, chevron_left 24, press scale 0.92. */
@Composable
fun ChatyBackButton(
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 34,
) {
    val colors = LocalAppColors.current
    val haptic = rememberChatySelectionHaptic()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) ChatyMotionTokens.ACTIVE_ICON_SCALE else 1f,
        tween(ChatyMotionTokens.INSTANT_MS, easing = ChatyMotionTokens.enter),
        label = "backScale",
    )
    Box(
        modifier.size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .scale(scale)
                .size(size.dp)
                .clip(CircleShape)
                .background(colors.surfaceSecondary, CircleShape)
                .border(0.8.dp, colors.border, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            val released = tryAwaitRelease()
                            pressed = false
                            if (released) {
                                haptic()
                                onPressed()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = colors.foreground, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * ChatyAppBar (toolbar height 56, title 16.5/w600 + optional caption at 0.6 alpha, no elevation,
 * background = scaffold background). Leading is the back button when [onBack] != null.
 */
@Composable
fun ChatyAppBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    backgroundColor: Color? = null,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(backgroundColor ?: colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.width(56.dp).padding(start = 8.dp), contentAlignment = Alignment.Center) {
                ChatyBackButton(onPressed = onBack)
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text(
                title,
                color = colors.onSurface,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = colors.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
        Spacer(Modifier.width(8.dp))
    }
}

/** ChatySingleChoiceModal: draft selection, Apply enabled only when changed (returns via [onApply]). */
@Composable
fun <T> ChatySingleChoiceModal(
    title: String,
    value: T,
    options: List<T>,
    labelBuilder: (T) -> String,
    onApply: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    descriptionBuilder: ((T) -> String)? = null,
    previewBuilder: (@Composable (T) -> Unit)? = null,
    applyLabel: String = "Apply",
) {
    val colors = LocalAppColors.current
    var draft by remember { mutableStateOf(value) }
    val changed = draft != value
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(24.dp)),
        ) {
            Column(Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp)) {
                Text(title, color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.W800)
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(description, color = colors.foregroundSecondary, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = colors.border, thickness = 1.dp)
            if (previewBuilder != null) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { previewBuilder(draft) }
            }
            LazyColumn(Modifier.weight(1f, fill = false).padding(vertical = 8.dp)) {
                itemsIndexed(options) { index, option ->
                    val desc = descriptionBuilder?.invoke(option)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == draft, onClick = { draft = option })
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == draft,
                            onClick = { draft = option },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                labelBuilder(option),
                                color = if (option == draft) colors.primary else colors.foreground,
                                fontSize = 16.sp,
                            )
                            if (!desc.isNullOrEmpty()) {
                                Text(desc, color = colors.foregroundSecondary, fontSize = 14.sp)
                            }
                        }
                        if (previewBuilder != null) previewBuilder(option)
                    }
                    if (index < options.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.borderSubtle, thickness = 1.dp)
                    }
                }
            }
            HorizontalDivider(color = colors.border, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.primary) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onApply(draft) },
                    enabled = changed,
                ) { Text(applyLabel) }
            }
        }
    }
}
