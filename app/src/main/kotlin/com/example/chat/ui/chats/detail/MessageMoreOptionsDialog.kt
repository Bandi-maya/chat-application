package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.ChatyDropdownMenuItem

/** Separate centered single-tap presentation. Capability ownership stays with the screen. */
data class MessageMoreOption(val label: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
fun MessageMoreOptionsDialog(options: List<MessageMoreOption>, onDismiss: () -> Unit) {
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text("More options") },
        text = {
            Column {
                options.forEach { option ->
                    ChatyDropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = { Icon(messageOptionIcon(option.label), null) },
                        enabled = option.enabled,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDismiss(); option.onClick() },
                    )
                }
                Text("Unavailable actions need their supporting service or settings.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun messageOptionIcon(label: String): ImageVector = when (label) {
    "Modify Chat" -> Icons.Outlined.Edit
    "Copy" -> Icons.Outlined.ContentCopy
    "Copy Selection" -> Icons.Outlined.SelectAll
    "Translate", "Change Translator Language" -> Icons.Outlined.Translate
    "interaction" -> Icons.Outlined.EmojiEmotions
    "Double Tap Reaction" -> Icons.Outlined.EmojiEmotions
    "Image quote" -> Icons.Outlined.FormatQuote
    "Share" -> Icons.Outlined.Share
    "Star", "Unstar", "Starred messages" -> Icons.Outlined.StarOutline
    "Notify When Message Read" -> Icons.Outlined.Notifications
    else -> Icons.Outlined.Info
}
