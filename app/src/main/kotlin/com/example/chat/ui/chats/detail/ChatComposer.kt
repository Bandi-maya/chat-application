package com.example.chat.ui.chats.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.templates.ComposerActionPlacement
import com.example.chat.core.templates.ComposerTemplate
import com.example.chat.core.theme.ThemeConfig

/** Port of the _Composer region of chat_detail_screen.dart (lines 3426-4405):
 *  the stock input row (integrated/split/powerRow placements) and all 29 GB
 *  Conversation Entry styles, plus the ComposerColors fallback chain from
 *  ConversationPreferences. Exact source rendering/behavior; the icon set is
 *  the Material-icons-extended mapping of ChatyIconId used across this slice.
 *
 *  Reused by ChatDetailScreen and by the Settings entry-style preview so both
 *  render the same composer (no duplicate implementation). */

/** Resolved composer colors (Dart _buildInputRow/_buildStyledEntryRow/
 *  _buildPowerRow fallback chains over conv prefs). */
data class ComposerColors(
    val uiButtons: Color,
    val emojiButton: Color,
    val sendFill: Color,
    val micCircleFill: Color,
    val inputBg: Color,
    val inputText: Color,
) {
    companion object {
        fun resolve(
            theme: ThemeConfig,
            uiButtonsColor: Long? = null,
            emojiButtonColor: Long? = null,
            sendButtonColor: Long? = null,
            micSendBgCircleColor: Long? = null,
            textEntryBackgroundColor: Long? = null,
            textEntryColor: Long? = null,
        ): ComposerColors {
            val uiButtons = uiButtonsColor?.let(::Color) ?: Color(theme.accentColor)
            return ComposerColors(
                uiButtons = uiButtons,
                emojiButton = emojiButtonColor?.let(::Color)
                    ?: (uiButtonsColor?.let(::Color) ?: Color(theme.secondaryTextColor)),
                sendFill = sendButtonColor?.let(::Color) ?: Color(theme.accentColor),
                micCircleFill = micSendBgCircleColor?.let(::Color)
                    ?: (sendButtonColor?.let(::Color) ?: Color(theme.accentColor)),
                inputBg = textEntryBackgroundColor?.let(::Color) ?: Color(theme.cardColor),
                inputText = textEntryColor?.let(::Color) ?: Color(theme.primaryTextColor),
            )
        }
    }
}

/** Dart _kStockEntryStyle + _normalizeEntryStyle. */
const val STOCK_ENTRY_STYLE = "Stock"

fun normalizeEntryStyle(raw: String): String {
    val v = raw.trim()
    return if (v.isEmpty() || v == "Stock (Chaty Default)" || v == STOCK_ENTRY_STYLE) {
        STOCK_ENTRY_STYLE
    } else {
        v
    }
}

/** The conversation composer. Stock renders the integrated/split/powerRow
 *  arrangements from the canonical ComposerTemplate; any other entry style
 *  renders its GB layout. Callbacks are identical across styles; only the
 *  arrangement differs (Dart doc on _buildStyledEntryRow). */
@Composable
fun ChatEntryBar(
    entryStyle: String,
    colors: ComposerColors,
    template: ComposerTemplate,
    theme: ThemeConfig,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    emojiPickerOpen: Boolean = false,
    onEmoji: () -> Unit = {},
    onAttach: () -> Unit = {},
    onCamera: (() -> Unit)? = null,
    onGallery: (() -> Unit)? = null,
    onAudio: (() -> Unit)? = null,
    onLocation: (() -> Unit)? = null,
    onContact: (() -> Unit)? = null,
    onVoice: () -> Unit = {},
    onVoiceHoldStart: () -> Unit = {},
    onVoiceMove: (Offset) -> Unit = {},
    onVoiceHoldEnd: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    val hasText = text.trim().isNotEmpty()

    // --- Shared building blocks (Dart local widget closures) ----------------

    @Composable
    fun emojiBtn(iconSize: Dp = 22.dp) = ComposerActionButton(
        semanticsLabel = if (emojiPickerOpen) "Return to keyboard" else "Emoji",
        icon = Icons.Rounded.EmojiEmotions,
        iconColor = colors.emojiButton,
        iconSize = iconSize,
        onTap = onEmoji,
    )

    @Composable
    fun attachBtn() = ComposerActionButton(
        semanticsLabel = "Attach",
        icon = Icons.Rounded.Add,
        iconColor = colors.uiButtons,
        onTap = onAttach,
    )

    // Dart: cameraBtn exists only when template.showCameraShortcut.
    val cameraSlot: (@Composable () -> Unit)? =
        if (template.showCameraShortcut && onCamera != null) {
            {
                ComposerActionButton(
                    semanticsLabel = "Camera",
                    icon = Icons.Rounded.PhotoCamera,
                    iconColor = colors.uiButtons,
                    onTap = onCamera,
                )
            }
        } else {
            null
        }

    @Composable
    fun sendMic() = SendMicMorph(
        theme = theme,
        template = template,
        sendFill = colors.sendFill,
        micFill = colors.micCircleFill,
        hasText = hasText,
        onSend = onSend,
        onVoice = onVoice,
        onVoiceHoldStart = onVoiceHoldStart,
        onVoiceMove = onVoiceMove,
        onVoiceHoldEnd = onVoiceHoldEnd,
    )

    @Composable
    fun field(
        radius: Dp = 24.dp,
        transparent: Boolean = false,
        borderColor: Color? = null,
        contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        prefix: (@Composable () -> Unit)? = null,
        suffix: (@Composable () -> Unit)? = null,
    ) = ComposerTextField(
        text = text,
        onTextChange = onTextChange,
        theme = theme,
        colors = colors,
        radius = radius,
        transparent = transparent,
        borderColor = borderColor,
        contentPadding = contentPadding,
        prefix = prefix,
        suffix = suffix,
    )

    // GB quick actions (Dart gbQa/gbGallery/gbAudio/gbLocation/gbContact/
    // gbMore/gbCamera): extra buttons route to the attachment sheet unless a
    // dedicated callback is wired.
    @Composable
    fun gbQa(icon: ImageVector, label: String, color: Color? = null, onTap: () -> Unit) =
        ComposerActionButton(
            semanticsLabel = label,
            icon = icon,
            iconColor = color ?: colors.uiButtons,
            onTap = onTap,
        )

    @Composable
    fun gbGallery() = gbQa(Icons.Rounded.PhotoLibrary, "Gallery") { (onGallery ?: onAttach)() }

    @Composable
    fun gbAudio() = gbQa(Icons.Rounded.Mic, "Audio") { (onAudio ?: onAttach)() }

    @Composable
    fun gbLocation() = gbQa(Icons.Rounded.LocationOn, "Location") { (onLocation ?: onAttach)() }

    @Composable
    fun gbContact() = gbQa(Icons.Rounded.Person, "Contact") { (onContact ?: onAttach)() }

    @Composable
    fun gbMore() = gbQa(Icons.Rounded.MoreHoriz, "More attachments") { onAttach() }

    @Composable
    fun gbCamera() {
        val cam = cameraSlot
        if (cam != null) cam() else gbGallery()
    }

    @Composable
    fun gbQuickRow(content: @Composable () -> Unit) = Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }

    @Composable
    fun gbFieldRow(
        radius: Dp = 24.dp,
        transparent: Boolean = false,
        borderColor: Color? = null,
        prefix: (@Composable () -> Unit)? = null,
        suffix: (@Composable () -> Unit)? = null,
    ) = Row(
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.weight(1f)) {
            field(
                radius = radius,
                transparent = transparent,
                borderColor = borderColor,
                prefix = prefix,
                suffix = suffix,
            )
        }
        Spacer(Modifier.width(6.dp))
        sendMic()
    }

    val secondaryBorder = Color(theme.secondaryTextColor).copy(alpha = 0.3f)

    // --- Dispatch (Dart _buildInputRow + _buildStyledEntryRow switch) -------
    val style = normalizeEntryStyle(entryStyle)
    Box(modifier) {
        when {
            style == STOCK_ENTRY_STYLE && template.actionPlacement == ComposerActionPlacement.powerRow -> {
                // Dart _buildPowerRow.
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        attachBtn()
                        cameraSlot?.invoke()
                        emojiBtn(24.dp)
                        Spacer(Modifier.weight(1f))
                        sendMic()
                    }
                    Spacer(Modifier.height(5.dp))
                    field(radius = template.cornerRadius.dp)
                }
            }

            style == STOCK_ENTRY_STYLE -> {
                // Dart _buildInputRow stock: split puts camera+attach outside
                // left; integrated keeps them inside the pill on the right.
                Row(verticalAlignment = Alignment.Bottom) {
                    if (template.actionPlacement == ComposerActionPlacement.split) {
                        cameraSlot?.invoke()
                        attachBtn()
                    }
                    Box(Modifier.weight(1f)) {
                        field(
                            radius = template.cornerRadius.dp,
                            prefix = { emojiBtn() },
                            suffix = if (template.actionPlacement == ComposerActionPlacement.split) {
                                null
                            } else {
                                {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        attachBtn()
                                        cameraSlot?.invoke()
                                    }
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    sendMic()
                }
            }

            // --- GB reference entry styles (23 designs) ---------------------
            style == "YOWA Style" -> gbFieldRow(prefix = { emojiBtn(20.dp) }, suffix = { attachBtn() })
            style == "WAMOD" -> Column {
                gbFieldRow(prefix = { emojiBtn(20.dp) }, suffix = { attachBtn() })
                gbQuickRow {
                    gbGallery(); gbCamera(); gbAudio(); gbLocation(); gbContact(); gbMore()
                }
            }
            style == "Hangouts" -> Column {
                gbFieldRow(radius = 12.dp, prefix = { emojiBtn(20.dp) })
                gbQuickRow {
                    emojiBtn(); gbGallery(); gbCamera(); gbAudio(); gbLocation(); gbContact(); gbMore()
                }
            }
            style == "Simple" -> gbFieldRow(suffix = { attachBtn() })
            style == "Aran V2" || style == "WANH" -> Row(verticalAlignment = Alignment.Bottom) {
                attachBtn()
                emojiBtn()
                Box(Modifier.weight(1f)) { field(radius = 20.dp) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "Mood" -> gbFieldRow(radius = 28.dp, prefix = { emojiBtn(20.dp) }, suffix = { attachBtn() })
            style == "Hangouts V2" -> Column {
                gbQuickRow {
                    gbGallery(); gbCamera(); gbLocation(); gbContact(); gbAudio(); gbMore()
                }
                gbFieldRow(radius = 12.dp, prefix = { emojiBtn(20.dp) })
            }
            style == "Dribble" -> Column {
                gbQuickRow {
                    emojiBtn(); gbGallery(); gbCamera(); gbAudio(); gbLocation(); gbMore()
                }
                gbFieldRow()
            }
            style == "Dribble V2" -> Column {
                gbQuickRow {
                    emojiBtn(); gbGallery(); gbCamera(); gbAudio(); gbLocation(); gbMore()
                }
                gbFieldRow(radius = 16.dp, transparent = true, borderColor = secondaryBorder)
            }
            style == "Allo" -> gbFieldRow(prefix = { attachBtn() }, suffix = { emojiBtn(20.dp) })
            style == "Rounded" -> Column {
                gbFieldRow(prefix = { emojiBtn(20.dp) }, suffix = { attachBtn() })
                gbQuickRow {
                    gbGallery(); gbCamera(); gbLocation(); gbContact(); gbAudio(); gbMore()
                }
            }
            style == "Old Stock (2016)" ->
                gbFieldRow(radius = 8.dp, prefix = { emojiBtn(20.dp) }, suffix = { attachBtn() })
            style == "RC-MSG Google" ->
                // The reference APK ships no msggoogle layout and falls back
                // to its Stock entry; mirror that fallback exactly.
                gbFieldRow(prefix = { emojiBtn(20.dp) }, suffix = { attachBtn() })
            style == "RC-Telegram X iOS" -> Row(verticalAlignment = Alignment.Bottom) {
                attachBtn()
                Box(Modifier.weight(1f)) { field(radius = 20.dp, suffix = { emojiBtn(20.dp) }) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "RC-IOS 11" -> Row(verticalAlignment = Alignment.Bottom) {
                emojiBtn()
                Box(Modifier.weight(1f)) { field(radius = 20.dp, suffix = { attachBtn() }) }
                cameraSlot?.invoke()
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "RC-Line" -> Row(verticalAlignment = Alignment.Bottom) {
                attachBtn()
                cameraSlot?.invoke()
                Box(Modifier.weight(1f)) { field(radius = 20.dp, suffix = { emojiBtn(20.dp) }) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "Concept" -> Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    field(
                        radius = 20.dp,
                        suffix = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                cameraSlot?.invoke()
                                emojiBtn(20.dp)
                            }
                        },
                    )
                }
                Spacer(Modifier.width(6.dp))
                sendMic()
                attachBtn()
            }
            style == "Instagram" -> Row(verticalAlignment = Alignment.Bottom) {
                cameraSlot?.invoke()
                Box(Modifier.weight(1f)) {
                    field(
                        radius = 20.dp,
                        suffix = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                emojiBtn(20.dp)
                                gbGallery()
                                attachBtn()
                            }
                        },
                    )
                }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "iOS 14" -> Row(verticalAlignment = Alignment.Bottom) {
                attachBtn()
                Box(Modifier.weight(1f)) { field(radius = 22.dp, suffix = { emojiBtn(20.dp) }) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "FB Messenger" -> Row(verticalAlignment = Alignment.Bottom) {
                attachBtn()
                cameraSlot?.invoke()
                gbGallery()
                Box(Modifier.weight(1f)) { field(radius = 22.dp, suffix = { emojiBtn(20.dp) }) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "RC Mess" -> Row(verticalAlignment = Alignment.Bottom) {
                attachBtn()
                cameraSlot?.invoke()
                Box(Modifier.weight(1f)) { field(radius = 22.dp) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }

            // --- Chaty's own added styles -----------------------------------
            style == "Telegram Modern" -> Row(verticalAlignment = Alignment.Bottom) {
                attachBtn()
                Spacer(Modifier.width(2.dp))
                Box(Modifier.weight(1f)) { field(radius = 18.dp, suffix = { emojiBtn(20.dp) }) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "iOS Clean Rounded" -> Row(verticalAlignment = Alignment.Bottom) {
                ComposerActionButton(
                    semanticsLabel = "Attach",
                    icon = Icons.Rounded.Add,
                    iconColor = Color(theme.onAccentColor),
                    fillColor = colors.uiButtons,
                    onTap = onAttach,
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) { field(radius = 26.dp) }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "Hangouts Minimal" -> Row(verticalAlignment = Alignment.Bottom) {
                emojiBtn()
                Box(Modifier.weight(1f)) {
                    field(
                        radius = 10.dp,
                        transparent = true,
                        borderColor = secondaryBorder,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
                attachBtn()
                Spacer(Modifier.width(2.dp))
                sendMic()
            }
            style == "Simple Pill" -> Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { field(radius = 26.dp, prefix = { emojiBtn(20.dp) }) }
                attachBtn()
                cameraSlot?.invoke()
                Spacer(Modifier.width(2.dp))
                sendMic()
            }
            style == "RC-Flat Floating" -> Box(
                Modifier
                    .padding(start = 4.dp, top = 0.dp, end = 4.dp, bottom = 6.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(18.dp),
                        clip = false,
                        ambientColor = Color(0x2E000000),
                        spotColor = Color(0x2E000000),
                    )
                    .background(colors.inputBg, RoundedCornerShape(18.dp))
                    .padding(start = 2.dp, top = 2.dp, end = 6.dp, bottom = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) {
                        field(
                            radius = 14.dp,
                            transparent = true,
                            prefix = { emojiBtn(20.dp) },
                            suffix = { attachBtn() },
                        )
                    }
                    sendMic()
                }
            }
            style == "WhatsApp Classic 3D" -> Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier
                        .weight(1f)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(24.dp),
                            clip = false,
                            ambientColor = Color(0x29000000),
                            spotColor = Color(0x29000000),
                        )
                        .background(colors.inputBg, RoundedCornerShape(24.dp)),
                ) {
                    field(
                        radius = 24.dp,
                        transparent = true,
                        prefix = { emojiBtn(20.dp) },
                        suffix = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                attachBtn()
                                cameraSlot?.invoke()
                            }
                        },
                    )
                }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }
            style == "Signal Compact Bubble" -> Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    field(
                        radius = 20.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        suffix = { attachBtn() },
                    )
                }
                Spacer(Modifier.width(6.dp))
                sendMic()
            }

            else -> {
                // Unknown saved value: fall back to the stock integrated layout.
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) {
                        field(
                            radius = template.cornerRadius.dp,
                            prefix = { emojiBtn() },
                            suffix = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    attachBtn()
                                    cameraSlot?.invoke()
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    sendMic()
                }
            }
        }
    }
}

/** Dart ChatyComposerActionButton: 48dp circle, optional fill, pressed
 *  scale 0.94 / opacity 0.85 (0.45 disabled), emphasized taps haptic,
 *  long-press start/move/end for the voice hold gesture. */
@Composable
private fun ComposerActionButton(
    semanticsLabel: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    fillColor: Color? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onTap: (() -> Unit)? = null,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressMove: ((Offset) -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
) {
    val pressedState = remember { mutableStateOf(false) }
    val pressed = pressedState.value
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "press-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (!enabled) 0.45f else if (pressed) 0.85f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "press-alpha",
    )
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(fillColor ?: Color.Transparent, CircleShape)
            .clip(CircleShape)
            .semantics { contentDescription = semanticsLabel }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressedState.value = true
                        tryAwaitRelease()
                        pressedState.value = false
                    },
                    onTap = {
                        if (emphasized) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTap?.invoke()
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        pressedState.value = true
                        onLongPressStart?.invoke()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onLongPressMove?.invoke(change.position)
                    },
                    onDragEnd = {
                        pressedState.value = false
                        onLongPressEnd?.invoke()
                    },
                    onDragCancel = {
                        pressedState.value = false
                        onLongPressEnd?.invoke()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Dart _buildSendMicMorph: AnimatedSwitcher (fade, 180ms, easeOutCubic /
 *  easeInCubic) between the send and voice actions keyed on hasText; zero
 *  duration when the template disables the morph. */
@Composable
private fun SendMicMorph(
    theme: ThemeConfig,
    template: ComposerTemplate,
    sendFill: Color,
    micFill: Color,
    hasText: Boolean,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onVoiceHoldStart: () -> Unit,
    onVoiceMove: (Offset) -> Unit,
    onVoiceHoldEnd: () -> Unit,
) {
    val duration = if (template.enableSendMorph) 180 else 0
    AnimatedContent(
        targetState = hasText,
        transitionSpec = {
            fadeIn(tween(duration, easing = EaseOutCubic)) togetherWith
                fadeOut(tween(duration, easing = EaseInCubic))
        },
        label = "send-mic-morph",
    ) { textPresent ->
        if (textPresent) {
            ComposerActionButton(
                semanticsLabel = "Send message",
                icon = Icons.AutoMirrored.Rounded.Send,
                iconColor = Color(theme.onAccentColor),
                fillColor = sendFill,
                emphasized = true,
                onTap = onSend,
            )
        } else {
            ComposerActionButton(
                semanticsLabel = "Voice note. Tap to start locked recording, or hold to record and slide.",
                icon = Icons.Rounded.Mic,
                iconColor = Color(theme.onAccentColor),
                fillColor = micFill,
                onTap = onVoice,
                onLongPressStart = onVoiceHoldStart,
                onLongPressMove = onVoiceMove,
                onLongPressEnd = onVoiceHoldEnd,
            )
        }
    }
}

/** Dart composer TextField port: hint 'Message…  /task or #reply', dense
 *  padding (default h12/v11), 14sp * fontScale, min 1 / max 5 lines. */
@Composable
private fun ComposerTextField(
    text: String,
    onTextChange: (String) -> Unit,
    theme: ThemeConfig,
    colors: ComposerColors,
    radius: Dp,
    transparent: Boolean,
    borderColor: Color?,
    contentPadding: PaddingValues,
    prefix: (@Composable () -> Unit)?,
    suffix: (@Composable () -> Unit)?,
) {
    val shape = RoundedCornerShape(radius)
    val fontSize = (14 * theme.fontScale).sp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (transparent) {
                    Modifier
                } else {
                    Modifier.background(colors.inputBg, shape)
                },
            )
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, shape)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
    ) {
        prefix?.invoke()
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(
                    "Message…  /task or #reply",
                    color = Color(theme.secondaryTextColor),
                    fontSize = fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(color = colors.inputText, fontSize = fontSize),
                cursorBrush = SolidColor(Color(theme.accentColor)),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        suffix?.invoke()
    }
}
