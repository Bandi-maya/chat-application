package com.example.chat.ui.chats.detail

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.domain.models.ChatMessage
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import java.time.ZoneId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** Resolve IDs against live messages on every composition, never expose a stale revoked copy. */
@Composable
internal fun MessageDetailDialogs(
    messages: List<ChatMessage>, showStarred: Boolean, copyMessageId: String?, infoMessageId: String?,
    onCloseStarred: () -> Unit, onCloseCopy: () -> Unit, onCloseInfo: () -> Unit,
    onJumpToMessage: (String) -> Unit,
) {
    if (showStarred) {
        val starred = messages.filter { it.isStarred && SelectionAction.star in SelectionActionPolicy.available(listOf(it)) }
        AlertDialog(onDismissRequest = onCloseStarred,
            title = { Text("Starred messages in this chat") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (starred.isEmpty()) item { Text("No starred messages in this chat.") }
                    items(starred, key = { it.id }) { message ->
                        TextButton(onClick = { onJumpToMessage(message.id) }) {
                            Text(message.text.ifBlank { message.type.name }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onCloseStarred) { Text("Close") } })
    }
    messages.firstOrNull { it.id == copyMessageId }
        ?.takeIf { SelectionAction.copy in SelectionActionPolicy.available(listOf(it)) }?.let { message ->
        AlertDialog(onDismissRequest = onCloseCopy, title = { Text("Copy Selection") },
            text = { SelectionContainer { Text(message.text) } },
            confirmButton = { TextButton(onClick = onCloseCopy) { Text("Close") } })
    }
    messages.firstOrNull { it.id == infoMessageId }?.let { message ->
        AlertDialog(onDismissRequest = onCloseInfo, title = { Text("Message info") },
            text = { Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss z", java.util.Locale.getDefault()).withZone(ZoneId.systemDefault())
                val confirmed = message.deliveryState in setOf(com.example.chat.domain.models.DeliveryState.sent, com.example.chat.domain.models.DeliveryState.delivered, com.example.chat.domain.models.DeliveryState.read)
                Text("Status: ${message.deliveryState.name.replaceFirstChar { it.uppercase() }}")
                Text("${if (confirmed) "Message time" else "Created on this device"}: ${formatter.format(message.createdAt)}")
                ServerReceiptInfo(message.id, message.type == com.example.chat.domain.models.MessageType.audio)
                message.editedAt?.let { Text("Edited: ${formatter.format(it)}") }
                Text("Message type: ${message.type.name}")
                Text("Message ID: ${message.id}")
                Text("Receipt times come from the server. Hidden/private times and unknown historical receipts are never inferred.")
            } },
            confirmButton = { TextButton(onClick = onCloseInfo) { Text("Close") } })
    }
}

@Composable
internal fun LocalMessageEditDialog(
    value: String, onValueChanged: (String) -> Unit, onDismiss: () -> Unit,
    onRestore: () -> Unit, onSave: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Modify Chat - only on this device") },
        text = { Column {
            Text("Only your local display changes. Copy, share and quotes still use the original message. The sender's message, other devices, delivery status and server edit history stay unchanged.")
            androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onValueChanged, label = { Text("Local message text") })
            TextButton(onClick = onRestore) { Text("Restore original") }
        } },
        confirmButton = { TextButton(onClick = onSave) { Text("Save locally") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ServerReceiptInfo(messageId: String, isAudio: Boolean) {
    val api = com.example.chat.ServiceLocator.apiBackend
    val owner = api.authSnapshot()
    var page by androidx.compose.runtime.remember(messageId, owner) { androidx.compose.runtime.mutableStateOf<com.example.chat.data.api.MessageInfoPage?>(null) }
    var recipients by androidx.compose.runtime.remember(messageId, owner) { androidx.compose.runtime.mutableStateOf(emptyList<com.example.chat.data.api.MessageInfoRecipient>()) }
    var busy by androidx.compose.runtime.remember(messageId, owner) { androidx.compose.runtime.mutableStateOf(false) }
    var error by androidx.compose.runtime.remember(messageId, owner) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    suspend fun load(after: String?) {
        if (busy) return
        busy = true; error = null
        try {
            val result = api.messageInfo(messageId, after, owner)
            if (api.authSnapshot() !== owner) return
            recipients = (if (after == null) result.recipients else recipients + result.recipients).distinctBy { it.userId }
            page = result
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (failure: Exception) { if (api.authSnapshot() === owner) error = failure.message ?: "Could not load message receipts." }
        finally { busy = false }
    }
    androidx.compose.runtime.LaunchedEffect(messageId, owner) { load(null) }
    val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss z", java.util.Locale.getDefault()).withZone(ZoneId.systemDefault())
    fun time(value: java.time.Instant?) = value?.let(formatter::format) ?: "Not recorded"
    if (busy) Text("Loading receipts...")
    error?.let { Text(it); TextButton(onClick = { scope.launch { load(page?.nextAfterUserId) } }, enabled = !busy) { Text("Retry") } }
    page?.let { current ->
        if (!current.snapshotKnown) Text("Send-time recipients and exact receipts are unknown for this older message.")
        Text("Sent to service: ${time(current.sentAt)}")
        recipients.forEach { recipient ->
            Text("Recipient: ${recipient.userId}")
            Text("Delivered: ${time(recipient.times.deliveredAt)}")
            Text("Read: ${if (recipient.readHidden) "Private" else time(recipient.times.readAt)}")
            if (isAudio) Text("Played: ${if (recipient.readHidden) "Private" else time(recipient.times.playedAt)}")
        }
        if (current.nextAfterUserId != null) TextButton(onClick = { scope.launch { load(current.nextAfterUserId) } }, enabled = !busy) { Text("Load more recipients") }
        if (current.snapshotKnown && recipients.isEmpty() && current.nextAfterUserId == null) Text("No visible recipient receipts.")
    }
}
