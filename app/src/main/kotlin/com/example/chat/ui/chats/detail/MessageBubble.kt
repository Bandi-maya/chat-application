package com.example.chat.ui.chats.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageReaction
import com.example.chat.domain.models.MessageType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.luminance

/** Optional color overrides mirroring the Dart convPrefs reads in
 *  message_bubble.dart. Null fields fall back to the ThemeConfig colors.
 *  Wiring ChatyPreferencesController.conversation values into this is an
 *  integration-owner seam. */
data class BubbleColorOverrides(
    val rightBubbleColor: Long? = null,
    val leftBubbleColor: Long? = null,
    val rightChatBubbleTextColor: Long? = null,
    val leftChatBubbleTextColor: Long? = null,
    val rightBubbleTimeColor: Long? = null,
    val leftBubbleTimeColor: Long? = null,
    val messageTextSize: Double? = null,
    val groupParticipantNameColor: Long? = null,
    val quotedBackgroundColor: Long? = null,
    val quotedDividerColor: Long? = null,
    val quotedNameColor: Long? = null,
    val quotedMessageColor: Long? = null,
    val deletedMessageIconColor: Long? = null,
    val infoBalloonsBgColor: Long? = null,
    val infoBalloonsTextColor: Long? = null,
)

/** Slot for remote image rendering. Defaults to the shared Coil-backed
 *  ChatyAsyncImage (ui/theme/ChatyAsyncImage.kt); inject a substitute in
 *  tests or previews. */
typealias RemoteImageSlot = @Composable (url: String, modifier: Modifier, contentDescription: String) -> Unit

/** Default bridge onto the shared wrapper: Crop scaling, Dart placeholder
 *  states come from the callers' container backgrounds. */
val chatyRemoteImage: RemoteImageSlot = { url, modifier, contentDescription ->
    com.example.chat.ui.theme.ChatyAsyncImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

private val bubbleMetaTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Dart: MediaQuery.sizeOf(context).width * theme.bubbleMaxWidthFactor. */
@Composable
private fun bubbleMaxWidth(theme: ThemeConfig) =
    (LocalConfiguration.current.screenWidthDp * theme.bubbleMaxWidthFactor).toFloat().dp

/** Exact port of lib/features/messages/message_bubble.dart against the
 *  current model/theme surface. Flutter-plugin-dependent renderers keep the
 *  Dart layout and state machine but delegate playback/loading to injected
 *  slots (remoteImage, onMediaTap, onVoicePlay). */
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    theme: ThemeConfig,
    modifier: Modifier = Modifier,
    senderName: String? = null,
    showGroupAvatar: Boolean = true,
    isSenderAdmin: Boolean = false,
    makeTextSelectable: Boolean = true,
    groupAdminIcon: String = "Default",
    hideAdminNameIcon: Boolean = false,
    voicePlayButtonColor: Color? = null,
    isSelected: Boolean = false,
    showDeletedContent: Boolean = false,
    showEditedLabel: Boolean = true,
    viewOnceOpened: Boolean = false,
    retainViewOnce: Boolean = false,
    colorOverrides: BubbleColorOverrides? = null,
    remoteImage: RemoteImageSlot? = chatyRemoteImage,
    onLongPress: () -> Unit = {},
    onLongPressWithRect: ((Rect) -> Unit)? = null,
    onSwipeReply: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onReactionTap: ((String) -> Unit)? = null,
    onReactionBadgeTap: ((MessageReaction) -> Unit)? = null,
    onMediaTap: (() -> Unit)? = null,
    onVoicePlay: ((MessageAttachment) -> Unit)? = null,
    onViewOnceOpen: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    task: ChatTask? = null,
    onTaskTap: (() -> Unit)? = null,
    onTaskToggle: (() -> Unit)? = null,
    onTaskMenu: ((Rect) -> Unit)? = null,
) {
    val bubbleBg = if (isMe) {
        colorOverrides?.rightBubbleColor ?: theme.outgoingBubbleColor
    } else {
        colorOverrides?.leftBubbleColor ?: theme.incomingBubbleColor
    }
    val textColor = if (isMe) {
        colorOverrides?.rightChatBubbleTextColor ?: theme.outgoingTextColor
    } else {
        colorOverrides?.leftChatBubbleTextColor ?: theme.incomingTextColor
    }
    val timestampColor = if (isMe) {
        colorOverrides?.rightBubbleTimeColor ?: argbWithAlpha(textColor, 0.65)
    } else {
        colorOverrides?.leftBubbleTimeColor ?: argbWithAlpha(textColor, 0.65)
    }
    val rawTextSize = colorOverrides?.messageTextSize ?: 15.0
    val messageTextSize = rawTextSize.coerceIn(10.0, 30.0)

    // --- System message pill ---
    if (message.type == MessageType.system) {
        val infoBg = colorOverrides?.infoBalloonsBgColor ?: argbWithAlpha(theme.surfaceColor, 0.8)
        val infoText = colorOverrides?.infoBalloonsTextColor ?: theme.secondaryTextColor
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .background(Color(infoBg), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(theme.cardColor), RoundedCornerShape(12.dp))
                    .padding(vertical = 5.dp, horizontal = 12.dp),
            ) {
                Text(
                    message.text,
                    color = Color(infoText),
                    fontSize = (11.5 * theme.fontScale).sp,
                )
            }
        }
        return
    }

    // --- Deleted for everyone placeholder ---
    if (message.isDeletedForEveryone && !showDeletedContent) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        ) {
            Row(
                Modifier
                    .padding(vertical = 3.dp, horizontal = 12.dp)
                    .background(
                        Color(argbWithAlpha(theme.surfaceColor, 0.55)),
                        RoundedCornerShape(theme.cornerRadius.dp),
                    )
                    .border(
                        1.dp, Color(theme.cardColor),
                        RoundedCornerShape(theme.cornerRadius.dp),
                    )
                    .padding(vertical = 9.dp, horizontal = 13.dp)
                    .pointerInput(message.id) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = Color(colorOverrides?.deletedMessageIconColor ?: theme.secondaryTextColor),
                    modifier = Modifier.size(17.dp),
                )
                Box(Modifier.width(7.dp))
                Text(
                    "This message was deleted",
                    color = Color(theme.secondaryTextColor),
                    fontSize = (13 * theme.fontScale).sp,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
        return
    }

    // --- Emoji-only messages render without the standard bubble fill ---
    val isTextOnly = message.attachment == null &&
        !message.isDeletedForEveryone &&
        message.replyToMessageId == null &&
        message.type == MessageType.text &&
        message.text.isNotEmpty()
    val emojiInfo = if (isTextOnly) classifyEmojiOnly(message.text) else EmojiOnlyInfo(false, 0)

    if (emojiInfo.isEmojiOnly) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = (3 * theme.density).dp, horizontal = 12.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
        ) {
            Text(
                message.text,
                fontSize = emojiInfo.fontSize(messageTextSize * theme.fontScale).sp,
                lineHeight = (emojiInfo.fontSize(messageTextSize * theme.fontScale) * 1.25).sp,
            )
            Box(Modifier.height(3.dp))
            Row(
                Modifier
                    .background(
                        Color(argbWithAlpha(bubbleBg, 0.55)),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bubbleMetaTimeFormatter.format(message.createdAt.atZone(ZoneId.systemDefault())),
                    color = Color(timestampColor),
                    fontSize = (10 * theme.fontScale).sp,
                )
                Box(Modifier.width(4.dp))
                DeliveryStatusIcon(
                    style = theme.deliveryTickStyle,
                    state = message.deliveryState,
                    unreadColor = tickUnreadColor(Color(bubbleBg), Color(theme.secondaryTextColor)),
                    readColor = Color(theme.accentColor),
                    size = 15.dp,
                )
            }
        }
        return
    }

    // --- Standard bubble ---
    SwipeToReplyContainer(
        onSwipeReply = onSwipeReply,
        accentColor = Color(theme.accentColor),
        surfaceColor = Color(theme.surfaceColor),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = (3 * theme.density).dp, horizontal = 12.dp),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isMe && senderName != null && showGroupAvatar) {
                Box(
                    Modifier
                        .padding(end = 8.dp, bottom = 2.dp)
                        .size(28.dp)
                        .background(Color(theme.accentColor), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        senderName.split(" ").filter { it.isNotEmpty() }
                            .take(2).joinToString("") { it[0].toString() },
                        color = Color(theme.onAccentColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            var bubbleRect by remember { mutableStateOf<Rect?>(null) }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .onGloballyPositioned { coords ->
                        // AppContextMenu anchorRect contract: window px.
                        bubbleRect = coords.boundsInWindow()
                    }
                    .pointerInput(message.id, isSelected) {
                        detectTapGestures(
                            onLongPress = {
                                val rect = bubbleRect
                                if (onLongPressWithRect != null && rect != null) {
                                    onLongPressWithRect(rect)
                                } else {
                                    onLongPress()
                                }
                            },
                            onDoubleTap = onDoubleTap?.let { d -> { d() } },
                        )
                    }
                    .background(
                        if (isSelected) Color(argbWithAlpha(theme.accentColor, 0.12))
                        else Color.Transparent,
                        RoundedCornerShape(16.dp),
                    ),
                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            ) {
                val geometry = BubbleStyleRegistry.getGeometry(theme.bubbleStyle)
                Column(
                    Modifier
                        .widthIn(max = bubbleMaxWidth(theme))
                        .padding(
                            start = geometry.bubbleMargin.left.dp,
                            top = geometry.bubbleMargin.top.dp,
                            end = geometry.bubbleMargin.right.dp,
                            bottom = geometry.bubbleMargin.bottom.dp,
                        )
                        .drawBehind {
                            drawBubble(
                                styleId = theme.bubbleStyle,
                                isMe = isMe,
                                fillColor = Color(bubbleBg),
                                strokeColor = Color(argbWithAlpha(theme.accentColor, 0.4)),
                                accentColor = Color(theme.accentColor),
                            )
                        }
                        .padding(
                            start = geometry.contentPadding.left.dp,
                            top = geometry.contentPadding.top.dp,
                            end = geometry.contentPadding.right.dp,
                            bottom = geometry.contentPadding.bottom.dp,
                        ),
                    horizontalAlignment = Alignment.Start,
                ) {
                    if (!isMe && senderName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                senderName,
                                color = Color(
                                    colorOverrides?.groupParticipantNameColor ?: theme.accentColor,
                                ),
                                fontSize = (11.5 * theme.fontScale).sp,
                                fontWeight = FontWeight.W700,
                            )
                            // Dart message_bubble.dart:443: admin badge next to the sender name.
                            if (isSenderAdmin && !hideAdminNameIcon) {
                                Box(Modifier.width(4.dp))
                                Icon(
                                    when (groupAdminIcon) {
                                        "Star" -> Icons.Rounded.Star
                                        "Shield" -> Icons.Rounded.Shield
                                        "Crown" -> Icons.Rounded.MilitaryTech
                                        "Badge" -> Icons.Rounded.WorkspacePremium
                                        else -> Icons.Rounded.VerifiedUser
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(
                                        colorOverrides?.groupParticipantNameColor ?: theme.accentColor,
                                    ),
                                )
                            }
                        }
                        Box(Modifier.height(3.dp))
                    }
                    if (message.isDeletedForEveryone && showDeletedContent) {
                        Row(
                            Modifier
                                .padding(bottom = 7.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.12f),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Cancel,
                                contentDescription = null,
                                tint = Color(
                                    colorOverrides?.deletedMessageIconColor
                                        ?: argbWithAlpha(textColor, 0.8),
                                ),
                                modifier = Modifier.size(14.dp),
                            )
                            Box(Modifier.width(5.dp))
                            Text(
                                "Deleted message",
                                color = Color(argbWithAlpha(textColor, 0.8)),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.W700,
                            )
                        }
                    }
                    if (message.replyToMessageId != null) {
                        Column(
                            Modifier
                                .padding(bottom = 6.dp)
                                .background(
                                    Color(
                                        colorOverrides?.quotedBackgroundColor
                                            ?: argbWithAlpha(0xFF000000L, 0.13),
                                    ),
                                    RoundedCornerShape(8.dp),
                                )
                                .drawBehind {
                                    // 3dp left quote divider bar.
                                    drawRect(
                                        color = Color(
                                            colorOverrides?.quotedDividerColor
                                                ?: (if (isMe) 0xB3FFFFFFL else theme.accentColor),
                                        ),
                                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .padding(start = 4.dp),
                        ) {
                            Text(
                                message.replyToSenderName ?: "Reply",
                                color = Color(
                                    colorOverrides?.quotedNameColor
                                        ?: (if (isMe) 0xFFFFFFFFL else theme.accentColor),
                                ),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                message.replyToPreviewText ?: "",
                                color = Color(
                                    colorOverrides?.quotedMessageColor
                                        ?: argbWithAlpha(textColor, 0.8),
                                ),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (message.metadata["forwarded"] == true) {
                        Row(
                            Modifier.padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Shortcut,
                                contentDescription = null,
                                tint = Color(argbWithAlpha(textColor, 0.6)),
                                modifier = Modifier.size(11.dp),
                            )
                            Box(Modifier.width(3.dp))
                            Text(
                                "Forwarded",
                                color = Color(argbWithAlpha(textColor, 0.6)),
                                fontSize = 10.sp,
                                fontStyle = FontStyle.Italic,
                            )
                        }
                    }
                    val isViewOnceMedia = message.metadata["view_once"] == true &&
                        (message.attachment?.type == "image" || message.attachment?.type == "video")
                    if (isViewOnceMedia && !isMe && !viewOnceOpened) {
                        ViewOnceLockedCard(
                            isVideo = message.attachment?.type == "video",
                            textColor = Color(textColor),
                            accentColor = Color(theme.accentColor),
                            surfaceColor = Color(theme.surfaceColor),
                            onTap = onViewOnceOpen,
                        )
                    } else if (isViewOnceMedia && !isMe && viewOnceOpened && !retainViewOnce) {
                        ViewOnceExpiredCard(textColor = Color(textColor))
                    } else {
                        when (message.attachment?.type) {
                            "image" -> ImageAttachmentPreview(
                                attachment = message.attachment!!,
                                remoteImage = remoteImage,
                                onTap = onMediaTap,
                            )
                            "video" -> VideoAttachmentPreview(
                                attachment = message.attachment!!,
                                textColor = Color(textColor),
                                accentColor = Color(theme.accentColor),
                                remoteImage = remoteImage,
                                onTap = onMediaTap,
                            )
                            "audio" -> VoiceNoteCard(
                                attachment = message.attachment!!,
                                textColor = Color(textColor),
                                accentColor = Color(theme.accentColor),
                                playButtonColor = voicePlayButtonColor,
                                onPlay = onVoicePlay,
                            )
                            "document" -> DocumentPreview(
                                attachment = message.attachment!!,
                                textColor = Color(textColor),
                                accentColor = Color(theme.accentColor),
                                onTap = onMediaTap,
                            )
                        }
                        if (message.type == MessageType.location) {
                            LocationCard(
                                message = message,
                                textColor = Color(textColor),
                                accentColor = Color(theme.accentColor),
                            )
                        }
                        if (message.type == MessageType.contact) {
                            ContactCard(
                                message = message,
                                textColor = Color(textColor),
                                accentColor = Color(theme.accentColor),
                            )
                        }
                        if (message.type == MessageType.taskCard) {
                            TaskCardChip(
                                message = message,
                                task = task,
                                textColor = Color(textColor),
                                theme = theme,
                                onTaskTap = onTaskTap,
                                onTaskToggle = onTaskToggle,
                                onTaskMenu = onTaskMenu,
                            )
                        }
                    }
                    if (message.type != MessageType.taskCard &&
                        message.type != MessageType.location &&
                        message.type != MessageType.contact &&
                        message.text.isNotEmpty()
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            // Dart: convPrefs.makeTextSelectable (default true) wraps
                            // the body text in a selectable region.
                            if (makeTextSelectable) {
                                SelectionContainer(Modifier.weight(1f, fill = false)) {
                                    BasicText(
                                        text = message.text,
                                        style = TextStyle(
                                            color = Color(textColor),
                                            fontSize = (messageTextSize * theme.fontScale).sp,
                                            lineHeight = (messageTextSize * theme.fontScale * 1.35).sp,
                                        ),
                                    )
                                }
                            } else {
                                BasicText(
                                    text = message.text,
                                    style = TextStyle(
                                        color = Color(textColor),
                                        fontSize = (messageTextSize * theme.fontScale).sp,
                                        lineHeight = (messageTextSize * theme.fontScale * 1.35).sp,
                                    ),
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            Box(Modifier.width(8.dp))
                            MessageMetaRow(message, isMe, textColor, timestampColor, theme,
                                showEditedLabel, bubbleBg, onRetry)
                        }
                    } else {
                        Row {
                            MessageMetaRow(message, isMe, textColor, timestampColor, theme,
                                showEditedLabel, bubbleBg, onRetry)
                        }
                    }
                }
                if (message.reactions.isNotEmpty()) {
                    Row(
                        Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.reactions.forEach { reaction ->
                            ReactionBadge(
                                emoji = reaction.emoji,
                                count = reaction.userIds.size,
                                isSelected = isMe,
                                backgroundColor = Color(theme.cardColor),
                                activeBorderColor = Color(theme.accentColor),
                                textColor = Color(theme.primaryTextColor),
                                onTap = {
                                    if (onReactionBadgeTap != null) onReactionBadgeTap(reaction)
                                    else onReactionTap?.invoke(reaction.emoji)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ticks must contrast with the ACTUAL bubble fill they sit on (Dart:
 *  ThemeData.estimateBrightnessForColor(surface)). */
/** Exact port of the task-card block in lib/features/messages/message_bubble.dart
 *  (message.type == MessageType.taskCard, lines ~586-918). Dart parity notes:
 *  - Container margin vertical 4 / padding 12 / radius 12, 1.2px state border.
 *  - Toggle circle: Dart AnimatedContainer 200ms -> animateColorAsState(tween(200)).
 *  - Dart strikethrough decorationColor (textColor a0.6) has no Compose TextStyle
 *    equivalent; LineThrough renders in text color (framework limitation).
 *  - Menu anchor = chip bounds in window px (Dart: taskCtx RenderBox localToGlobal). */
@Composable
private fun TaskCardChip(
    message: ChatMessage,
    task: ChatTask?,
    textColor: Color,
    theme: ThemeConfig,
    onTaskTap: (() -> Unit)?,
    onTaskToggle: (() -> Unit)?,
    onTaskMenu: ((Rect) -> Unit)?,
) {
    val isCompleted = task?.status == TaskStatus.completed
    val isBlocked = task?.status == TaskStatus.blocked
    val isOverdue = task?.isOverdue == true
    var chipRect by remember { mutableStateOf(Rect.Zero) }

    val toggleBg by animateColorAsState(
        targetValue = if (isCompleted) Color(theme.successColor) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "taskToggleBg",
    )
    val toggleBorder by animateColorAsState(
        targetValue = if (isCompleted) {
            Color(theme.successColor)
        } else if (isOverdue) {
            Color(theme.dangerColor)
        } else {
            Color(theme.accentColor)
        },
        animationSpec = tween(durationMillis = 200),
        label = "taskToggleBorder",
    )

    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .onGloballyPositioned { chipRect = it.boundsInWindow() }
            .background(
                color = if (isCompleted) {
                    Color.Black.copy(alpha = 0.1f)
                } else {
                    Color(theme.surfaceColor).copy(alpha = 0.85f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.2.dp,
                color = if (isCompleted) {
                    Color(theme.successColor).copy(alpha = 0.4f)
                } else if (isBlocked) {
                    Color(theme.dangerColor).copy(alpha = 0.5f)
                } else if (isOverdue) {
                    Color(theme.dangerColor).copy(alpha = 0.4f)
                } else {
                    Color(theme.accentColor).copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(toggleBg, CircleShape)
                    .border(2.dp, toggleBorder, CircleShape)
                    .then(
                        if (onTaskToggle != null) {
                            Modifier.clickable { onTaskToggle() }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = task?.title ?: message.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = textColor,
                    fontWeight = FontWeight.W700,
                    fontSize = (14 * theme.fontScale).sp,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onTaskTap != null) {
                            Modifier.clickable { onTaskTap() }
                        } else {
                            Modifier
                        },
                    ),
            )
            if (onTaskMenu != null) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                        .clickable { onTaskMenu(chipRect) },
                )
            }
        }
        if (task != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val priorityColor = when (task.priority) {
                    TaskPriority.urgent -> Color(theme.dangerColor)
                    TaskPriority.high -> Color(0xFFFF9800)
                    else -> Color(0xFF2196F3)
                }
                Text(
                    text = task.priority.name.uppercase(),
                    style = TextStyle(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.W700,
                        color = priorityColor,
                    ),
                    modifier = Modifier
                        .background(priorityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = if (isOverdue) Color(theme.dangerColor) else textColor.copy(alpha = 0.65f),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    task.dueRelativeText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = if (isOverdue) FontWeight.W700 else FontWeight.W500,
                        color = if (isOverdue) Color(theme.dangerColor) else textColor.copy(alpha = 0.75f),
                    ),
                )
                if (task.checklistItems.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.Checklist,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "${task.completedChecklistCount}/${task.checklistItems.size}",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W600,
                            color = textColor.copy(alpha = 0.75f),
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "View details →",
            style = TextStyle(
                color = Color(theme.accentColor),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.W600,
            ),
            modifier = if (onTaskTap != null) Modifier.clickable { onTaskTap() } else Modifier,
        )
    }
}

private fun tickUnreadColor(surface: Color, secondaryText: Color): Color =
    if (surface.luminance() < 0.5f) Color(0xB3FFFFFF) else secondaryText

@Composable
private fun MessageMetaRow(
    message: ChatMessage,
    isMe: Boolean,
    textColor: Long,
    timestampColor: Long,
    theme: ThemeConfig,
    showEditedLabel: Boolean,
    bubbleBg: Long,
    onRetry: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (message.editedAt != null && showEditedLabel) {
            Text(
                "edited",
                color = Color(argbWithAlpha(textColor, 0.58)),
                fontSize = 9.5.sp,
            )
            Box(Modifier.width(4.dp))
        }
        if (message.isPinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = null,
                tint = Color(argbWithAlpha(textColor, 0.7)),
                modifier = Modifier.size(11.dp),
            )
            Box(Modifier.width(3.dp))
        }
        if (message.isStarred) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(11.dp),
            )
            Box(Modifier.width(3.dp))
        }
        Text(
            bubbleMetaTimeFormatter.format(message.createdAt.atZone(ZoneId.systemDefault())),
            color = Color(timestampColor),
            fontSize = (10.5 * theme.fontScale).sp,
        )
        if (isMe) {
            Box(Modifier.width(4.dp))
            Box(
                Modifier.pointerInput(message.id, message.deliveryState) {
                    if (message.deliveryState == DeliveryState.failed &&
                        onRetry != null
                    ) {
                        detectTapGestures(onTap = { onRetry() })
                    }
                },
            ) {
                DeliveryStatusIcon(
                    style = theme.deliveryTickStyle,
                    state = message.deliveryState,
                    unreadColor = tickUnreadColor(Color(bubbleBg), Color(theme.secondaryTextColor)),
                    readColor = Color(theme.accentColor),
                    size = 15.dp,
                )
            }
        }
    }
}

@Composable
private fun ReactionBadge(
    emoji: String,
    count: Int,
    isSelected: Boolean,
    backgroundColor: Color,
    activeBorderColor: Color,
    textColor: Color,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) activeBorderColor
                else activeBorderColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp),
            )
            .pointerInput(emoji) { detectTapGestures(onTap = { onTap() }) }
            .padding(horizontal = 7.dp, vertical = 3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 19.sp)
        if (count > 0) {
            Box(Modifier.width(4.dp))
            Text("$count", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.W700)
        }
    }
}

@Composable
private fun ImageAttachmentPreview(
    attachment: MessageAttachment,
    remoteImage: RemoteImageSlot?,
    onTap: (() -> Unit)?,
) {
    Box(
        Modifier
            .size(width = 270.dp, height = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.26f))
            .pointerInput(attachment.url) { detectTapGestures(onTap = { onTap?.invoke() }) },
        contentAlignment = Alignment.Center,
    ) {
        val url = attachment.url
        if (url.isNullOrEmpty() || remoteImage == null) {
            Icon(
                Icons.Rounded.Description,
                contentDescription = attachment.name,
                tint = Color.White.copy(alpha = 0.6f),
            )
        } else {
            remoteImage(url, Modifier.matchParentSize(), attachment.name)
        }
    }
}

@Composable
private fun VideoAttachmentPreview(
    attachment: MessageAttachment,
    textColor: Color,
    accentColor: Color,
    remoteImage: RemoteImageSlot?,
    onTap: (() -> Unit)?,
) {
    Box(
        Modifier
            .size(width = 270.dp, height = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.26f))
            .pointerInput(attachment.url) { detectTapGestures(onTap = { onTap?.invoke() }) },
        contentAlignment = Alignment.Center,
    ) {
        val url = attachment.url
        if (!url.isNullOrEmpty() && remoteImage != null) {
            remoteImage(url, Modifier.matchParentSize(), attachment.name)
        }
        Box(
            Modifier
                .size(52.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play video",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun VoiceNoteCard(
    attachment: MessageAttachment,
    textColor: Color,
    accentColor: Color,
    playButtonColor: Color? = null,
    onPlay: ((MessageAttachment) -> Unit)?,
) {
    val totalSeconds = attachment.durationSeconds
    val label = "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    Row(
        Modifier
            .width(250.dp)
            .padding(vertical = 4.dp)
            .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                // Dart _VoiceNotePlayer: playButtonColor ?? accentColor.
                .background(playButtonColor ?: accentColor, CircleShape)
                .pointerInput(attachment.id) {
                    detectTapGestures(onTap = { onPlay?.invoke(attachment) })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play voice note",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(textColor.copy(alpha = 0.24f), RoundedCornerShape(3.dp)),
            )
            Box(Modifier.height(4.dp))
            Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun DocumentPreview(
    attachment: MessageAttachment,
    textColor: Color,
    accentColor: Color,
    onTap: (() -> Unit)?,
) {
    val extension = if (attachment.name.contains('.')) {
        attachment.name.substringAfterLast('.').uppercase()
    } else "FILE"
    Row(
        Modifier
            .width(270.dp)
            .padding(vertical = 3.dp)
            .background(Color.Black.copy(alpha = 0.13f), RoundedCornerShape(12.dp))
            .pointerInput(attachment.id) { detectTapGestures(onTap = { onTap?.invoke() }) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .width(44.dp)
                .height(48.dp)
                .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                extension,
                color = accentColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.W800,
                maxLines = 1,
            )
        }
        Box(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                attachment.name,
                color = textColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W800,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.height(3.dp))
            Text(
                "${attachment.size} • $extension",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 10.5.sp,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun LocationCard(message: ChatMessage, textColor: Color, accentColor: Color) {
    val latitude = (message.metadata["latitude"] as? Number)?.toDouble()
        ?: message.metadata["latitude"]?.toString()?.toDoubleOrNull()
    val longitude = (message.metadata["longitude"] as? Number)?.toDouble()
        ?: message.metadata["longitude"]?.toString()?.toDoubleOrNull()
    Column(
        Modifier
            .width(270.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(142.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(46.dp),
            )
        }
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
            Box(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Shared location", color = textColor, fontWeight = FontWeight.W800)
                Text(
                    if (latitude == null || longitude == null) "Tap to open location"
                    else "%.5f, %.5f".format(latitude, longitude),
                    color = textColor.copy(alpha = 0.72f),
                    fontSize = 10.5.sp,
                )
            }
            Icon(
                Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun ContactCard(message: ChatMessage, textColor: Color, accentColor: Color) {
    val name = message.metadata["contact_name"]?.toString()?.trim()
    val phones = (message.metadata["phones"] as? List<*>)?.map { it.toString() }?.filter { it.isNotEmpty() }
        ?: emptyList()
    val emails = (message.metadata["emails"] as? List<*>)?.map { it.toString() }?.filter { it.isNotEmpty() }
        ?: emptyList()
    val fallbackName = message.text.split("\n").first().replaceFirst("👤", "").trim()
    val displayName = if (!name.isNullOrEmpty()) name
    else if (fallbackName.isEmpty()) "Shared contact" else fallbackName
    val initials = displayName.split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it[0].toString() }.uppercase()
    Column(
        Modifier
            .width(270.dp)
            .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (initials.isEmpty()) "C" else initials,
                    color = accentColor,
                    fontWeight = FontWeight.W800,
                )
            }
            Box(Modifier.width(10.dp))
            Text(
                displayName,
                color = textColor,
                fontWeight = FontWeight.W800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (phones.isNotEmpty()) {
            Box(Modifier.height(8.dp))
            Text(phones.first(), color = textColor.copy(alpha = 0.78f), fontSize = 11.5.sp)
        }
        if (emails.isNotEmpty()) {
            Box(Modifier.height(3.dp))
            Text(
                emails.first(),
                color = textColor.copy(alpha = 0.72f),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ViewOnceLockedCard(
    isVideo: Boolean,
    textColor: Color,
    accentColor: Color,
    surfaceColor: Color,
    onTap: (() -> Unit)?,
) {
    Column(
        Modifier
            .width(210.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceColor.copy(alpha = 0.55f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .pointerInput("viewOnce") { detectTapGestures(onTap = { onTap?.invoke() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (isVideo) Icons.Rounded.VideocamOff else Icons.Outlined.ImageNotSupported,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(30.dp),
        )
        Box(Modifier.height(8.dp))
        Text(
            "View once",
            color = textColor.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
        )
        Box(Modifier.height(2.dp))
        Text(
            "Tap to open • opens only once",
            color = textColor.copy(alpha = 0.5f),
            fontSize = 9.5.sp,
        )
    }
}

@Composable
private fun ViewOnceExpiredCard(textColor: Color) {
    Row(
        Modifier
            .width(210.dp)
            .height(64.dp)
            .background(textColor.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.VisibilityOff,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp),
        )
        Box(Modifier.width(7.dp))
        Text(
            "Opened • view-once media expired",
            color = textColor.copy(alpha = 0.6f),
            fontSize = 11.sp,
        )
    }
}

/** Port of _SwipeToReplyContainer: progressive swipe-to-reply with a haptic
 *  threshold at 48dp and a 68dp max drag; the reply icon fades/scales in. */
@Composable
private fun SwipeToReplyContainer(
    onSwipeReply: (() -> Unit)?,
    accentColor: Color,
    surfaceColor: Color,
    content: @Composable () -> Unit,
) {
    if (onSwipeReply == null) {
        content()
        return
    }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 48.dp.toPx() }
    val maxDragPx = with(density) { 68.dp.toPx() }
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffset, label = "swipeReply")
    val progress = (animatedOffset / thresholdPx).coerceIn(0f, 1f)

    Box(
        Modifier.pointerInput(onSwipeReply) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (dragOffset >= thresholdPx) onSwipeReply()
                    dragOffset = 0f
                },
                onDragCancel = { dragOffset = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    if (!(dragOffset <= 0f && dragAmount < 0f)) {
                        dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxDragPx)
                    }
                },
            )
        },
    ) {
        if (progress > 0.05f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = with(density) { (animatedOffset * 0.45f - 24f).toDp() })
                    .size(32.dp)
                    .background(surfaceColor.copy(alpha = progress), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Reply,
                    contentDescription = "Reply",
                    tint = accentColor.copy(alpha = progress),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(Modifier.offset(x = with(density) { animatedOffset.toDp() }, y = 0.dp)) {
            content()
        }
    }
}
