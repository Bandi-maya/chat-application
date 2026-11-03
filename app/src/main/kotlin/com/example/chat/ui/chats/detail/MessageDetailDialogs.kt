package com.example.chat.ui.chats.detail

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
            text = { Column {
                Text("Sent: ${message.createdAt.atZone(ZoneId.systemDefault())}")
                Text("Status: ${message.deliveryState}")
                message.editedAt?.let { Text("Edited: ${it.atZone(ZoneId.systemDefault())}") }
                Text("Per-recipient delivery and read times are not provided by this conversation.")
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
