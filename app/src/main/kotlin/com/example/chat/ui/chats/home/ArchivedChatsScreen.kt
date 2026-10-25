package com.example.chat.ui.chats.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.chat.domain.models.HomePreferences
import androidx.compose.foundation.ExperimentalFoundationApi

/** Port of _ArchivedChatsScreen: archived list with selection mode,
 *  unarchive + delete (confirm dialog copy from the Dart source) and its own
 *  simpler timestamp rules. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ArchivedChatsScreen(
    conversations: List<Conversation>,
    theme: ThemeConfig,
    homePrefs: HomePreferences,
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onToggleArchive: (String) -> Unit,
    onDeleteConversations: (List<String>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val archived = conversations.filter { it.isArchived }
    val isSelectionMode = selectedIds.isNotEmpty()

    if (showDeleteConfirm) {
        val count = selectedIds.size
        HomeConfirmDialog(
            title = "Delete $count chat${if (count == 1) "" else "s"}?",
            message = "This removes the selected conversations for this account.",
            confirmLabel = "Delete",
            onConfirm = { onDeleteConversations(selectedIds.toList()); selectedIds = emptySet() },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(theme.cardColor))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Clear selection",
                            tint = Color(theme.primaryTextColor),
                        )
                    }
                    Text(
                        selectedIds.size.toString(),
                        color = Color(theme.primaryTextColor),
                        fontWeight = FontWeight.W800,
                        fontSize = 19.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        selectedIds.forEach(onToggleArchive)
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Outlined.Unarchive, contentDescription = "Unarchive", tint = Color(theme.primaryTextColor))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(theme.primaryTextColor))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(theme.surfaceColor))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(theme.primaryTextColor),
                        )
                    }
                    Text("Archived", color = Color(theme.primaryTextColor), fontSize = 19.sp)
                }
            }
            if (archived.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Archive,
                        contentDescription = null,
                        tint = Color(theme.secondaryTextColor),
                        modifier = Modifier.width(56.dp).height(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No archived chats",
                        color = Color(theme.primaryTextColor),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Chats you archive will appear here",
                        color = Color(theme.secondaryTextColor),
                        fontSize = 13.5.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                    items(archived, key = { it.id }) { conversation ->
                        val isSelected = selectedIds.contains(conversation.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(argbWithAlpha(theme.accentColor, 0.12))
                                    else Color.Transparent,
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - conversation.id
                                            else selectedIds + conversation.id
                                        } else {
                                            onOpenConversation(conversation)
                                        }
                                    },
                                    onLongClick = {
                                        selectedIds = if (isSelected) selectedIds - conversation.id
                                        else selectedIds + conversation.id
                                    },
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HomeAvatar(
                                initials = conversation.avatarInitials
                                    ?: conversation.title.take(2).uppercase(),
                                color = parseAvatarColorHex(conversation.avatarColorHex, Color(theme.accentColor)),
                                size = 48f,
                                shape = homePrefs.avatarShape,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        conversation.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color(theme.primaryTextColor),
                                        fontSize = (16 * theme.fontScale).sp,
                                        fontWeight = if (conversation.unreadCount > 0) FontWeight.W800 else FontWeight.W600,
                                        modifier = Modifier.weight(1f),
                                    )
                                    HomeTimeLabel(
                                        text = HomeFormatters.formatArchivedTime(conversation.lastMessageTime),
                                        highlight = conversation.unreadCount > 0,
                                        color = Color(theme.secondaryTextColor),
                                        highlightColor = Color(theme.accentColor),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        conversation.lastMessageText.ifEmpty { "No messages" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (conversation.unreadCount > 0) {
                                            Color(theme.primaryTextColor)
                                        } else {
                                            Color(theme.secondaryTextColor)
                                        },
                                        fontSize = (12.5 * theme.fontScale).sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (conversation.unreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .background(Color(theme.accentColor), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                conversation.unreadCount.toString(),
                                                color = Color(theme.onAccentColor),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.W800,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
