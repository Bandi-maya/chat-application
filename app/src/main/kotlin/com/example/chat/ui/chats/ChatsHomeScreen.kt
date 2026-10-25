package com.example.chat.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.Conversation
import com.example.chat.ui.theme.LocalThemeConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** First-slice port of chats_home_screen.dart's conversation list: pinned
 *  section first (Dart's pinned entries ordering), tiles with avatar
 *  initials, title, last-message preview, time, unread badge. The Dart
 *  screen's template/density styling, swipe actions, archive entry, calls,
 *  and selection mode land in their own slices; this slice proves the
 *  sign-in -> chat list -> conversation flow against the real REST plane. */
@Composable
fun ChatsHomeScreen(
    conversations: List<Conversation>,
    requiresSubscription: Boolean,
    subscriptionMessage: String?,
    onOpenConversation: (Conversation) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val pinned = conversations.filter { it.isPinned }
    val unpinned = conversations.filter { !it.isPinned }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Chats",
                color = Color(theme.primaryTextColor),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
            if (requiresSubscription) {
                Text(
                    subscriptionMessage ?: "Workspace subscription required. Please select a plan.",
                    color = Color(theme.dangerColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .background(Color(argbWithAlpha(theme.dangerColor, 0.10)), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            if (conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No conversations yet",
                        color = Color(theme.secondaryTextColor),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn {
                    if (pinned.isNotEmpty()) {
                        item { SectionLabel("Pinned", theme) }
                        items(pinned, key = { "p_${it.id}" }) { c ->
                            ConversationTile(c, theme, onOpenConversation)
                        }
                    }
                    if (unpinned.isNotEmpty()) {
                        if (pinned.isNotEmpty()) item { SectionLabel("All Chats", theme) }
                        items(unpinned, key = { it.id }) { c ->
                            ConversationTile(c, theme, onOpenConversation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String, theme: ThemeConfig) {
    Text(
        label,
        color = Color(theme.secondaryTextColor),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConversationTile(
    conversation: Conversation,
    theme: ThemeConfig,
    onOpen: (Conversation) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(conversation) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color(theme.accentColor), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                conversation.avatarInitials ?: "C",
                color = Color(theme.onAccentColor),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                conversation.title,
                color = Color(theme.primaryTextColor),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                conversation.lastMessageText,
                color = Color(theme.secondaryTextColor),
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatConversationTime(conversation.lastMessageTime),
                color = if (conversation.unreadCount > 0) Color(theme.accentColor)
                else Color(theme.secondaryTextColor),
                fontSize = 11.5.sp,
            )
            if (conversation.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(Color(theme.accentColor), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        conversation.unreadCount.toString(),
                        color = Color(theme.onAccentColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** WhatsApp-style recency label: time today, date otherwise (weekday labels
 *  land with chat_formatters parity). */
fun formatConversationTime(instant: Instant): String {
    val zone = ZoneId.systemDefault()
    val now = java.time.LocalDate.now(zone)
    val date = instant.atZone(zone).toLocalDate()
    return if (date == now) timeFormatter.format(instant.atZone(zone))
    else dateFormatter.format(instant.atZone(zone))
}
