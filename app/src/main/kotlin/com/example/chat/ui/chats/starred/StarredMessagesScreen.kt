package com.example.chat.ui.chats.starred

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.MessageType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/** Exact port of lib/features/messages/starred_messages_screen.dart (api@3bee780).
 *
 *  House-style stateless consumer: the Dart widget reads ChatyDataStore directly
 *  and rebuilds via dataStore.addListener; here the integration layer owns the
 *  service listener and passes fresh lists down. Wiring contract:
 *  - conversations / messagesFor  <- service conversations + getMessages(id);
 *    the integration listener drives recomposition (Dart _onStoreChanged).
 *  - onRefresh <- runs ensureConversationLoaded for EVERY conversation
 *    (Dart Future.wait over dataStore.conversations); returns false when any
 *    load failed (Dart catch -> loadError banner). Called once on first
 *    composition (Dart initState) and again from the app bar, the banner Retry,
 *    and pull-to-refresh.
 *  - onToggleStar(conversationId, messageId) <- toggleStarMessage
 *    (chaty_data_store.dart:559): the service looks up the message and flips
 *    the 'starred' user-state RPC; the screen intentionally passes no value.
 *  - onOpenConversation(conversationId, messageId) <- push ChatDetailScreen
 *    with initialMessageId = messageId (this screen always passes it).
 *  Colors come from MaterialTheme.colorScheme (Dart Theme.of(context).colorScheme),
 *  not ThemeConfig - the source screen uses the Material scheme directly.
 *  Deviation: Dart MaterialBanner has no stable material3 equivalent; the error
 *  banner is rebuilt as an equivalent row (leading icon + message + Retry). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    conversations: List<Conversation>,
    messagesFor: (String) -> List<ChatMessage>,
    onRefresh: suspend () -> Boolean,
    onToggleStar: (conversationId: String, messageId: String) -> Unit,
    onOpenConversation: (conversationId: String, messageId: String?) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            loadError = null
            val ok = runCatching { onRefresh() }.getOrDefault(false)
            if (!ok) {
                loadError = "Some conversations could not be refreshed. Pull to retry."
            }
            loading = false
        }
    }
    // Dart initState: unawaited(_loadConversations()).
    LaunchedEffect(Unit) {
        loading = true
        loadError = null
        val ok = runCatching { onRefresh() }.getOrDefault(false)
        if (!ok) {
            loadError = "Some conversations could not be refreshed. Pull to retry."
        }
        loading = false
    }

    // Dart _starredMessages: per-conversation scan, skip unstarred/deleted-for-me,
    // query over "title text typeName", newest first.
    val starred = conversations.flatMap { conversation ->
        messagesFor(conversation.id)
            .filter { it.isStarred && !it.isDeletedForMe }
            .filter { message ->
                if (query.isEmpty()) return@filter true
                "${conversation.title} ${message.text} ${message.type.name}"
                    .lowercase()
                    .contains(query)
            }
            .map { conversation to it }
    }.sortedByDescending { it.second.createdAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starred messages") },
                navigationIcon = {
                    // Dart AppBar automaticallyImplyLeading on a pushed route.
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh starred messages",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
            ) {
                TextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        query = it.trim().lowercase()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    placeholder = { Text("Search starred messages") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(
                                onClick = {
                                    searchText = ""
                                    query = ""
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerHighest.copy(alpha = 0.45f),
                        unfocusedContainerColor = colors.surfaceContainerHighest.copy(alpha = 0.45f),
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
                if (loadError != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SyncProblem,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            loadError ?: "",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { refresh() }) { Text("Retry") }
                    }
                }
                PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = { refresh() },
                    modifier = Modifier.weight(1f),
                ) {
                    if (starred.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 72.dp),
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.fillParentMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        if (query.isEmpty()) {
                                            Icons.Rounded.StarBorder
                                        } else {
                                            Icons.Rounded.SearchOff
                                        },
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        if (query.isEmpty()) {
                                            "No starred messages yet"
                                        } else {
                                            "No matching messages"
                                        },
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.W700,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (query.isEmpty()) {
                                            "Open a message menu and choose Star. Your saved messages will appear here."
                                        } else {
                                            "Try another word or conversation name."
                                        },
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = 8.dp,
                                end = 12.dp,
                                bottom = 24.dp,
                            ),
                        ) {
                            items(starred.size) { index ->
                                val (conversation, message) = starred[index]
                                StarredMessageRow(
                                    conversation = conversation,
                                    message = message,
                                    onOpen = {
                                        onOpenConversation(conversation.id, message.id)
                                    },
                                    onUnstar = { onToggleStar(conversation.id, message.id) },
                                )
                                if (index < starred.lastIndex) Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dart Card + ListTile row (contentPadding 14/7/6/7) with the trailing
 *  PopupMenuButton ('open' / 'unstar') rendered as star + date. */
@Composable
private fun StarredMessageRow(
    conversation: Conversation,
    message: ChatMessage,
    onOpen: () -> Unit,
    onUnstar: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, top = 7.dp, end = 6.dp, bottom = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (conversation.type == ConversationType.group) {
                        Icons.Rounded.Groups
                    } else {
                        Icons.Outlined.ChatBubbleOutline
                    },
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.W700,
                )
                Text(
                    previewText(message),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { menuOpen = true }
                        .padding(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = "Message options",
                        tint = colors.primary,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatStarredDate(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape,
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                        },
                        text = { Text("Open conversation") },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Rounded.StarBorder, contentDescription = null)
                        },
                        text = { Text("Remove star") },
                        onClick = {
                            menuOpen = false
                            onUnstar()
                        },
                    )
                }
            }
        }
    }
}

/** Dart _previewText: deleted-for-everyone placeholder, trimmed text, else the
 *  per-type label (MessageType.text's own fallback is "Message attachment"). */
private fun previewText(message: ChatMessage): String {
    if (message.isDeletedForEveryone) return "This message was deleted"
    val text = message.text.trim()
    if (text.isNotEmpty()) return text
    return when (message.type) {
        MessageType.image -> "Photo"
        MessageType.video -> "Video"
        MessageType.audio -> "Voice message"
        MessageType.document -> "Document"
        MessageType.location -> "Location"
        MessageType.contact -> "Contact"
        MessageType.taskCard -> "Task"
        MessageType.system -> "System message"
        MessageType.text -> "Message attachment"
    }
}

/** Dart _formatDate: today -> h:mm AM/PM (12-hour, no leading zero), else
 *  dd/MM/yyyy, all in the device-local zone. */
private fun formatStarredDate(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault())
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    if (local.year == now.year && local.month == now.month && local.dayOfMonth == now.dayOfMonth) {
        val hour12 = if (local.hour % 12 == 0) 12 else local.hour % 12
        val amPm = if (local.hour >= 12) "PM" else "AM"
        return "%d:%02d %s".format(hour12, local.minute, amPm)
    }
    return "%02d/%02d/%d".format(local.dayOfMonth, local.monthValue, local.year)
}
