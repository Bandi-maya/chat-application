package com.example.chat.ui.chats.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.HomePreferences

/** Live presence snapshot for one (conversation, other-user) pair. Null from
 *  the provider means "no presence data" - the Dart screen renders exactly
 *  the same rows with an empty presence string then. */
data class HomePresence(
    val isOnline: Boolean = false,
    val isTyping: Boolean = false,
    val isRecording: Boolean = false,
    val lastSeenAt: java.time.Instant? = null,
)

/** Port of chats_home_screen.dart _conversationTile: density-scaled row with
 *  template/prefs-driven colors, presence + typing line, draft highlight,
 *  mention badge, delivery ticks for the user's own last message, selection
 *  overlay, online dot, mute/pin indicators, unread badge and hairline
 *  divider. */
@Composable
fun ConversationRow(
    conversation: Conversation,
    theme: ThemeConfig,
    homePrefs: HomePreferences,
    chatList: ChatListTemplate,
    density: Double,
    isDark: Boolean,
    cardRows: Boolean,
    isTelegramRows: Boolean,
    displayTitle: String,
    presence: HomePresence?,
    currentUserId: String,
    lastOwnMessageState: DeliveryState?,
    selected: Boolean,
    locked: Boolean,
    elapsedTime: Boolean,
    gbOnlineChat: Boolean,
    gbOnlineDotChat: Boolean,
    gbModOnlineColor: Long?,
    gbModLastSeenColor: Long?,
    gbOnlineDotColor: Long?,
    gbHomeCounterBk: Long?,
    gbHomeCounterText: Long?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowRadius = theme.cornerRadius.coerceIn(0.0, 24.0)
    val tileRadius = if (cardRows) (rowRadius + 2).coerceIn(0.0, 26.0) else rowRadius
    val effectiveDensity = density * chatListDensityFactor(chatList.density)
    val textSizeScale = (homePrefs.screenTextSize / 17.0).coerceIn(0.7, 1.5)

    val otherId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
    val online = conversation.type == ConversationType.direct &&
        otherId.isNotEmpty() &&
        presence?.isOnline == true &&
        !homePrefs.disableContactOnlineLastSeen &&
        gbOnlineChat
    val presenceText =
        if (conversation.type == ConversationType.direct && otherId.isNotEmpty() &&
            !homePrefs.disableContactOnlineLastSeen
        ) {
            when {
                presence?.isRecording == true -> "recording…"
                presence?.isTyping == true -> "typing…"
                presence?.isOnline == true -> "online"
                else -> HomeFormatters.formatLastSeen(presence?.lastSeenAt)
            }
        } else {
            ""
        }

    // Color resolution: typed HomePreferences over legacy GB keys over theme.
    val contactNameColor = homePrefs.rowContactNameColor?.let { Color(it) } ?: Color(theme.primaryTextColor)
    val messageTextColor = homePrefs.rowTextColor?.let { Color(it) } ?: Color(theme.secondaryTextColor)
    val onlineColor = homePrefs.contactOnlineColor?.let { Color(it) }
        ?: gbModOnlineColor?.let { Color(it) }
        ?: Color(theme.successColor)
    val lastSeenColor = homePrefs.lastSeenColor?.let { Color(it) }
        ?: gbModLastSeenColor?.let { Color(it) }
        ?: Color(theme.secondaryTextColor)
    val onlineDotColor = homePrefs.onlineDotColor?.let { Color(it) }
        ?: gbOnlineDotColor?.let { Color(it) }
        ?: Color(theme.successColor)
    val unreadBgColor = homePrefs.unreadCounterColor?.let { Color(it) }
        ?: gbHomeCounterBk?.let { Color(it) }
        ?: Color(theme.accentColor)
    val unreadTextColor = homePrefs.unreadCounterTextColor?.let { Color(it) }
        ?: gbHomeCounterText?.let { Color(it) }
        ?: Color(theme.onAccentColor)

    val tileColor = when {
        selected -> Color(argbWithAlpha(theme.accentColor, if (isDark) 0.16 else 0.10))
        cardRows -> Color(theme.cardColor)
        isTelegramRows -> Color(argbWithAlpha(theme.cardColor, if (isDark) 0.68 else 0.58))
        conversation.isPinned -> Color(theme.cardColor)
        else -> Color.Transparent
    }

    val avatarSize = 50f * effectiveDensity.toFloat()
    val avatarShape = if (homePrefs.avatarShape == "circle") chatList.avatarShape else homePrefs.avatarShape
    val subtitleActive = presence?.isTyping == true || presence?.isRecording == true

    Column(modifier) {
        Box(
            Modifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(tileColor, RoundedCornerShape(tileRadius.dp))
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = (chatList.itemHeight * effectiveDensity).dp)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    HomeAvatar(
                        initials = conversation.avatarInitials
                            ?: displayTitle.take(2).uppercase(),
                        color = parseAvatarColorHex(conversation.avatarColorHex, Color(theme.accentColor)),
                        size = avatarSize,
                        shape = avatarShape,
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(avatarSize.dp)
                                .background(
                                    Color(argbWithAlpha(theme.accentColor, 0.86)),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(theme.onAccentColor),
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    } else if (chatList.showPresenceBadge && online &&
                        !homePrefs.disableOnlineDot && gbOnlineDotChat
                    ) {
                        // WA-iOS presence dot sits bottom-right at (-1, -1).
                        Box(Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp)) {
                            HomeOnlineDot(
                                active = true,
                                avatarSize = 50f * density.toFloat(),
                                color = onlineDotColor,
                                ringColor = Color(theme.backgroundColor),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (locked) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = Color(theme.accentColor),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                displayTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = contactNameColor,
                                fontSize = (16 * effectiveDensity * textSizeScale * theme.fontScale).sp,
                                fontWeight = if (conversation.unreadCount > 0) FontWeight.W800 else FontWeight.W600,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (conversation.lastMessageText.contains('@')) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(19.dp)
                                        .background(
                                            homePrefs.mentionIndicatorBgColor?.let { Color(it) }
                                                ?: Color(argbWithAlpha(theme.accentColor, 0.14)),
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.AlternateEmail,
                                        contentDescription = null,
                                        tint = homePrefs.mentionIconColor?.let { Color(it) }
                                            ?: Color(theme.accentColor),
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        HomeTimeLabel(
                            text = if (elapsedTime) {
                                HomeFormatters.formatElapsedTime(conversation.lastMessageTime)
                            } else {
                                HomeFormatters.formatConversationTimestamp(conversation.lastMessageTime)
                            },
                            highlight = conversation.unreadCount > 0,
                            color = Color(theme.secondaryTextColor),
                            highlightColor = Color(theme.accentColor),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lastOwnMessageState != null) {
                            // Same glyph ticks the Kotlin ChatDetailScreen
                            // already renders; the 16-style tick painter lands
                            // with its own slice.
                            val (glyph, tickColor) = when (lastOwnMessageState) {
                                DeliveryState.sending, DeliveryState.queued -> "⏱" to theme.secondaryTextColor
                                DeliveryState.failed -> "!" to theme.dangerColor
                                DeliveryState.sent -> "✓" to theme.secondaryTextColor
                                DeliveryState.delivered -> "✓✓" to theme.secondaryTextColor
                                DeliveryState.read -> "✓✓" to theme.accentColor
                            }
                            Text(glyph, color = Color(tickColor), fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        val subtitleSize = (13.5 * effectiveDensity * textSizeScale * theme.fontScale).sp
                        if (conversation.draftText.isNotEmpty()) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color(theme.dangerColor), fontWeight = FontWeight.W700)) {
                                        append("Draft: ")
                                    }
                                    withStyle(SpanStyle(color = messageTextColor)) {
                                        append(conversation.draftText)
                                    }
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = subtitleSize,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            val subtitle = when {
                                subtitleActive -> presenceText
                                conversation.lastMessageText.isEmpty() -> presenceText
                                else -> conversation.lastMessageText
                            }
                            Text(
                                subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = when {
                                    subtitleActive -> Color(theme.successColor)
                                    conversation.unreadCount > 0 -> contactNameColor
                                    else -> messageTextColor
                                },
                                fontSize = subtitleSize,
                                fontWeight = if (subtitleActive) FontWeight.W700 else FontWeight.W400,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (presenceText.isNotEmpty() && conversation.lastMessageText.isNotEmpty() && !subtitleActive) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                presenceText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (online) onlineColor else lastSeenColor,
                                fontSize = (9.5 * textSizeScale).sp,
                                fontWeight = FontWeight.W600,
                                modifier = Modifier.widthIn(max = 105.dp),
                            )
                        }
                        if (conversation.isMuted) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.VolumeOff,
                                contentDescription = null,
                                tint = Color(theme.secondaryTextColor),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (conversation.isPinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = Color(theme.secondaryTextColor),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (conversation.unreadCount > 0) {
                            Spacer(Modifier.width(7.dp))
                            HomeCountBadge(
                                count = conversation.unreadCount,
                                color = unreadBgColor,
                                textColor = unreadTextColor,
                            )
                        }
                    }
                }
            }
        }
        if (chatList.showDivider && !homePrefs.hideChatsDivider) {
            Box(
                Modifier
                    .padding(start = 76.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(argbWithAlpha(theme.cardColor, 0.55))),
            )
        }
    }
}
