package com.example.chat.ui.core.components.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.RowScope

/** Port of lib/ui/core/components/composer/chaty_composer_renderer.dart (api 3bee780d). */
enum class ComposerStateMode { idle, typing, reply, edit, recording, mediaDraft, offline }

/**
 * Port of ChatyComposerData. Dart's TextEditingController + FocusNode become [text]/[onTextChange] (value-hoisted
 * TextFieldValue, so selection/composition survive) and a [focusRequester]. [onMicRelease] is carried but, like
 * Dart, never read by this renderer (mic press handling lives in the owner).
 */
class ChatyComposerData(
    val text: TextFieldValue,
    val onTextChange: (TextFieldValue) -> Unit,
    val focusRequester: FocusRequester,
    val onSend: () -> Unit,
    val onMicPress: () -> Unit,
    val onAttach: () -> Unit,
    val onCamera: () -> Unit,
    val onEmojiToggle: () -> Unit,
    val mode: ComposerStateMode = ComposerStateMode.idle,
    val hasText: Boolean = false,
    val isRecording: Boolean = false,
    val isOffline: Boolean = false,
    val replyTitle: String? = null,
    val replySnippet: String? = null,
    val onCancelReply: (() -> Unit)? = null,
    val editOriginalText: String? = null,
    val onCancelEdit: (() -> Unit)? = null,
    val onMicRelease: (() -> Unit)? = null,
    val onMicCancel: (() -> Unit)? = null,
    val mediaDraftTray: (@Composable () -> Unit)? = null,
    val recordingLevels: List<Float> = emptyList(),
    val recordingDurationLabel: String? = null,
)

/** [reducedMotion] = Dart ChatyMotion.duration(context) collapsing to zero under disableAnimations. */
@Composable
fun ChatyComposerRenderer(
    variantId: String,
    data: ChatyComposerData,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    reducedMotion: Boolean = false,
) {
    val c = LocalAppColors.current
    val bg = backgroundColor ?: c.surface
    Column(modifier.fillMaxWidth().background(bg)) {
        Box(Modifier.fillMaxWidth().height(0.8.dp).background(c.borderSubtle))
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            data.mediaDraftTray?.invoke()
            if (data.mode == ComposerStateMode.reply && data.replyTitle != null) ReplyStrip(data)
            if (data.mode == ComposerStateMode.edit) EditStrip(data)
            Box(Modifier.padding(8.dp, 6.dp, 8.dp, 8.dp)) { InputRow(variantId, data, reducedMotion) }
        }
    }
}

@Composable
private fun StripBox(accent: Color, content: @Composable RowScope.() -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(10.dp, 6.dp, 10.dp, 0.dp)
            .clip(RoundedCornerShape(10.dp)).background(c.surfaceSecondary)
            .then(Modifier.border(0.dp, Color.Transparent))
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dart: left border 3.5 in accent color inside a 10-radius container (BorderRadius + left-only Border).
        Box(Modifier.width(3.5.dp).heightIn(min = 30.dp).background(accent))
        Spacer(Modifier.width(12.dp))
        content()
    }
}

@Composable
private fun SmallClose(onClick: (() -> Unit)?) {
    IconButton(onClick = { onClick?.invoke() }, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = LocalAppColors.current.foreground)
    }
}

@Composable
private fun ReplyStrip(data: ChatyComposerData) {
    val c = LocalAppColors.current
    StripBox(c.primary) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(data.replyTitle!!, color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.W700)
            data.replySnippet?.let {
                Text(it, color = c.foregroundSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SmallClose(data.onCancelReply)
    }
}

@Composable
private fun EditStrip(data: ChatyComposerData) {
    val c = LocalAppColors.current
    StripBox(c.warning) {
        Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = c.foreground)
        Spacer(Modifier.width(8.dp))
        Text("Editing message", color = c.foreground, fontSize = 12.5.sp, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        SmallClose(data.onCancelEdit)
    }
}

@Composable
private fun InputRow(variantId: String, data: ChatyComposerData, reducedMotion: Boolean) {
    if (data.isRecording) return RecordingRow(data)
    when (variantId) {
        "telegram_dense" -> TelegramDenseRow(data, reducedMotion)
        "signal_quiet" -> SignalQuietRow(data, reducedMotion)
        "imessage_expressive" -> IMessageRow(data)
        "instagram_media_first" -> InstagramRow(data)
        // messenger_action_row, command_composer, lens_first, chaty_capsule and unknown ids render the capsule (as Dart).
        else -> CapsuleRow(data, reducedMotion)
    }
}

@Composable
private fun RecordingRow(data: ChatyComposerData) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { data.onMicCancel?.invoke() }) { Icon(Icons.Rounded.DeleteOutline, null, tint = c.error) }
        Spacer(Modifier.width(8.dp))
        Text(data.recordingDurationLabel ?: "0:00", color = c.foreground, fontSize = 14.sp, fontWeight = FontWeight.W700)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            data.recordingLevels.forEach { lvl ->
                Box(
                    Modifier.weight(1f).padding(horizontal = 1.dp)
                        .height((4f + 20f * lvl.coerceIn(0f, 1f)).dp)
                        .background(c.primary, RoundedCornerShape(2.dp)),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // FloatingActionButton.small: 40dp circle, primary fill, white send icon 18, no elevation.
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.primary).clickable { data.onSend() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Send, null, Modifier.size(18.dp), tint = Color.White) }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, size: Dp, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, null, Modifier.size(size), tint = tint) }
}

/** TextField equivalent: borderless, dense, vertical content padding 8, min 1 / max [maxLines] lines, 15sp. */
@Composable
private fun ComposerField(data: ChatyComposerData, hint: String, maxLines: Int, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Box(modifier.padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
        if (data.text.text.isEmpty()) Text(hint, color = c.foregroundSecondary, fontSize = 15.sp)
        BasicTextField(
            value = data.text,
            onValueChange = data.onTextChange,
            modifier = Modifier.fillMaxWidth().focusRequester(data.focusRequester),
            textStyle = TextStyle(fontSize = 15.sp, color = c.foreground),
            cursorBrush = SolidColor(c.primary),
            minLines = 1,
            maxLines = maxLines,
        )
    }
}

@Composable
private fun CapsuleRow(data: ChatyComposerData, reducedMotion: Boolean) {
    val c = LocalAppColors.current
    val showSend = data.hasText || data.mode == ComposerStateMode.reply || data.mode == ComposerStateMode.edit
    Row(verticalAlignment = Alignment.Bottom) {
        Row(
            Modifier.weight(1f).background(c.inputFill, RoundedCornerShape(24.dp)).border(0.8.dp, c.border, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconBtn(Icons.Outlined.EmojiEmotions, 22.dp, c.foregroundSecondary, data.onEmojiToggle)
            ComposerField(data, if (data.isOffline) "Message (Offline - will queue)" else "Message...", 5, Modifier.weight(1f))
            IconBtn(Icons.Rounded.AttachFile, 22.dp, c.foregroundSecondary, data.onAttach)
            if (!showSend) IconBtn(Icons.Outlined.CameraAlt, 22.dp, c.foregroundSecondary, data.onCamera)
        }
        Spacer(Modifier.width(6.dp))
        ActionBubble(showSend, data.onSend, data.onMicPress, reducedMotion)
    }
}

@Composable
private fun TelegramDenseRow(data: ChatyComposerData, reducedMotion: Boolean) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        IconBtn(Icons.Rounded.AttachFile, 22.dp, c.foregroundSecondary, data.onAttach)
        Row(Modifier.weight(1f).background(c.inputFill, RoundedCornerShape(18.dp)), verticalAlignment = Alignment.Bottom) {
            ComposerField(data, "Message", 5, Modifier.weight(1f).padding(horizontal = 12.dp))
            IconBtn(Icons.Outlined.EmojiEmotions, 20.dp, c.foregroundSecondary, data.onEmojiToggle)
        }
        Spacer(Modifier.width(4.dp))
        ActionBubble(data.hasText, data.onSend, data.onMicPress, reducedMotion)
    }
}

@Composable
private fun SignalQuietRow(data: ChatyComposerData, reducedMotion: Boolean) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        IconBtn(Icons.Outlined.AddCircleOutline, 24.dp, c.foregroundSecondary, data.onAttach)
        ComposerField(data, "Signal message...", 4,
            Modifier.weight(1f).background(c.surfaceSecondary, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp))
        Spacer(Modifier.width(6.dp))
        ActionBubble(data.hasText, data.onSend, data.onMicPress, reducedMotion)
    }
}

@Composable
private fun IMessageRow(data: ChatyComposerData) {
    val c = LocalAppColors.current
    val showSend = data.hasText
    Row(verticalAlignment = Alignment.Bottom) {
        IconBtn(Icons.Rounded.Add, 26.dp, c.primary, data.onAttach)
        Row(
            Modifier.weight(1f).background(c.inputFill, RoundedCornerShape(20.dp)).border(0.8.dp, c.border, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ComposerField(data, "iMessage", 5, Modifier.weight(1f))
            if (!showSend) IconBtn(Icons.Outlined.MicNone, 22.dp, c.primary, data.onMicPress)
            if (showSend) IconBtn(Icons.Rounded.ArrowUpward, 22.dp, c.primary, data.onSend)
        }
    }
}

@Composable
private fun InstagramRow(data: ChatyComposerData) {
    val c = LocalAppColors.current
    val showSend = data.hasText
    Row(verticalAlignment = Alignment.Bottom) {
        IconBtn(Icons.Rounded.CameraAlt, 22.dp, c.primary, data.onCamera)
        ComposerField(data, "Message...", 4,
            Modifier.weight(1f).background(c.surfaceSecondary, RoundedCornerShape(22.dp)).padding(horizontal = 12.dp))
        Spacer(Modifier.width(4.dp))
        if (!showSend) {
            IconBtn(Icons.Outlined.MicNone, 22.dp, c.foreground, data.onMicPress)
            IconBtn(Icons.Outlined.Image, 22.dp, c.foreground, data.onAttach)
        } else {
            TextButton(onClick = data.onSend) { Text("Send", color = c.primary, fontWeight = FontWeight.W700) }
        }
    }
}

/** _ActionBubble: 44dp primary circle, selection haptic, send (20) <-> mic (22) switched over fast (160ms). */
@Composable
private fun ActionBubble(isSend: Boolean, onSend: () -> Unit, onMicPress: () -> Unit, reducedMotion: Boolean) {
    val c = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.primary).clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isSend) onSend() else onMicPress()
        },
        contentAlignment = Alignment.Center,
    ) {
        val ms = if (reducedMotion) 0 else 160
        AnimatedContent(isSend, transitionSpec = { fadeIn(tween(ms)) togetherWith fadeOut(tween(ms)) }, label = "actionBubble") { send ->
            if (send) Icon(Icons.Rounded.Send, null, Modifier.size(20.dp), tint = Color.White)
            else Icon(Icons.Rounded.Mic, null, Modifier.size(22.dp), tint = Color.White)
        }
    }
}
