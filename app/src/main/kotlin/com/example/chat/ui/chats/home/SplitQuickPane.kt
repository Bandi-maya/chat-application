package com.example.chat.ui.chats.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.Conversation

/** _SplitQuickPane: the 'Tablet Split View' quick-access pane listing pinned
 *  chats and groups; every entry opens its real conversation screen. */
@Composable
fun SplitQuickPane(
    theme: ThemeConfig,
    pinned: List<Conversation>,
    groups: List<Conversation>,
    onOpen: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasContent = pinned.isNotEmpty() || groups.isNotEmpty()
    if (!hasContent) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(argbWithAlpha(theme.surfaceColor, 0.35)))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Pin chats or create groups to see them here.",
                textAlign = TextAlign.Center,
                color = Color(theme.secondaryTextColor),
                fontSize = 12.5.sp,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(argbWithAlpha(theme.surfaceColor, 0.35))),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        if (pinned.isNotEmpty()) {
            item { PaneLabel("PINNED CHATS", theme) }
            item { Spacer(Modifier.height(6.dp)) }
            items(pinned.size) { i -> PaneTile(pinned[i], theme, onOpen) }
            item { Spacer(Modifier.height(14.dp)) }
        }
        if (groups.isNotEmpty()) {
            item { PaneLabel("GROUPS", theme) }
            item { Spacer(Modifier.height(6.dp)) }
            items(groups.size) { i -> PaneTile(groups[i], theme, onOpen) }
        }
    }
}

@Composable
private fun PaneLabel(text: String, theme: ThemeConfig) {
    Text(
        text,
        color = Color(theme.secondaryTextColor),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun PaneTile(conversation: Conversation, theme: ThemeConfig, onOpen: (Conversation) -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .background(Color(theme.cardColor), RoundedCornerShape(12.dp))
            .clickable { onOpen(conversation) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeAvatar(
            initials = conversation.avatarInitials ?: conversation.title.take(2).uppercase(),
            color = parseAvatarColorHex(conversation.avatarColorHex, Color(theme.accentColor)),
            size = 34f,
            shape = "circle",
        )
        Spacer(Modifier.width(10.dp))
        Text(
            conversation.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(theme.primaryTextColor),
            fontSize = 13.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.weight(1f),
        )
        if (conversation.unreadCount > 0) {
            Text(
                conversation.unreadCount.toString(),
                color = Color(theme.accentColor),
                fontWeight = FontWeight.W800,
                fontSize = 13.sp,
            )
        }
    }
}
