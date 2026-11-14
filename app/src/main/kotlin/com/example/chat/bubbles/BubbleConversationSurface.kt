package com.example.chat.bubbles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.ServiceLocator
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.ChatEntryBar
import com.example.chat.ui.chats.detail.ChatMessageBubble
import com.example.chat.ui.chats.detail.ComposerColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Narrow first phase: real shared text composer and bubbles. No unscoped ChatDetailHost callbacks. */
@Composable
internal fun BubbleConversationSurface(ticket: ChatBubbleController.Ticket, openFullApp: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    var text by remember(ticket.id) { mutableStateOf("") }
    var busy by remember(ticket.id) { mutableStateOf(false) }
    var error by remember(ticket.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = ServiceLocator.apiBackend
    val theme = LocalThemeConfig.current
    DisposableEffect(ticket.id) {
        val listener: () -> Unit = { revision++ }
        api.addListener(listener)
        onDispose { api.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") revision
    if (ChatBubbleController.resolve(ticket.id) == null) return
    // Snapshot reads share the API's immutable account guard, not the local override namespace.
    var messages: List<ChatMessage> = emptyList()
    var title = "Chaty"
    val current = api.withAuthScope(ticket.scope) {
        messages = api.getMessages(ticket.roomId).filter(BubbleContentPolicy::visible)
        title = api.conversations.firstOrNull { it.id == ticket.roomId }?.title ?: "Chaty"
    }
    if (!current || ChatBubbleController.resolve(ticket.id) == null) return
    Column(Modifier.fillMaxSize()) {
        Text(title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (messages.isEmpty()) Text("No messages loaded yet. Open Chaty to sync this chat.", Modifier.padding(16.dp))
        Text("Text bubble. Open Chaty for media, calls and older history.", Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(messages.asReversed(), key = { it.id }) { message ->
                if (BubbleContentPolicy.plainText(message)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = if (message.senderId == ticket.scope.userId) Arrangement.End else Arrangement.Start) {
                        // No remote media fetch, view-once access, local message actions or account-switching routes.
                        ChatMessageBubble(message = message, isMe = message.senderId == ticket.scope.userId,
                            theme = theme, remoteImage = null)
                    }
                } else {
                    Text("Open Chaty to view this message", Modifier.padding(16.dp))
                }
            }
        }
        androidx.compose.material3.TextButton(onClick = openFullApp) { Text("Open Chaty for all features") }
        error?.let { Text(it, Modifier.padding(8.dp)) }
        if (busy) Text("Sending...", Modifier.padding(8.dp))
        ChatEntryBar(
            entryStyle = "Stock", colors = ComposerColors.resolve(theme),
            template = ServiceLocator.templateController.composer, theme = theme,
            text = text, onTextChange = { if (!busy && ChatBubbleController.resolve(ticket.id) != null) text = it },
            onEmoji = openFullApp, onAttach = openFullApp, onVoice = openFullApp,
            onVoiceHoldStart = { error = "Open Chaty to record voice messages." },
            onSend = {
                if (!busy && text.isNotBlank() && ChatBubbleController.resolve(ticket.id) != null) {
                    val body = text
                    busy = true
                    scope.launch {
                        try {
                            // Original entry scope remains the authority even if a queued tap runs after switching.
                            if (ChatBubbleController.resolve(ticket.id) == null) return@launch
                            api.sendMessage(ticket.roomId, body, expectedScope = ticket.scope)
                            if (ChatBubbleController.resolve(ticket.id) != null) { text = ""; error = null }
                        } catch (_: Exception) {
                            if (ChatBubbleController.resolve(ticket.id) != null) error = "Could not send. Open Chaty to check the message."
                        } finally { busy = false }
                    }
                }
            },
        )
    }
}
