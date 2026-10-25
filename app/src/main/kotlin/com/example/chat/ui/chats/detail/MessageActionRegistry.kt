package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType

/** Exact port of lib/features/messages/message_action_registry.dart:
 *  available message actions + availability policy. Icons are mapped at the
 *  UI layer so this file stays JVM-testable (no Compose imports). */
enum class MessageActionType {
    react,
    reply,
    copy,
    forward,
    edit,
    pin,
    star,
    translate,
    select,
    info,
    task,
    more,
    deleteForMe,
    deleteForEveryone,
    report,
}

data class MessageActionDescriptor(
    val type: MessageActionType,
    val label: String,
    val isDestructive: Boolean = false,
    val isPrimary: Boolean = true,
)

object MessageActionRegistry {

    fun getAvailableActions(
        message: ChatMessage,
        isMe: Boolean,
        allowEditPolicy: Boolean = true,
    ): List<MessageActionDescriptor> {
        val actions = mutableListOf<MessageActionDescriptor>()

        // Reply is available on all valid non-system messages.
        if (message.type != MessageType.system && !message.isDeletedForEveryone) {
            actions.add(MessageActionDescriptor(MessageActionType.reply, "Reply"))
        }

        // Copy is available for text messages.
        if (message.text.isNotEmpty() && message.type == MessageType.text) {
            actions.add(MessageActionDescriptor(MessageActionType.copy, "Copy"))
        }

        // Forward is available for all standard content.
        if (message.type != MessageType.system && !message.isDeletedForEveryone) {
            actions.add(MessageActionDescriptor(MessageActionType.forward, "Forward"))
        }

        // Star (non-system messages).
        if (message.type != MessageType.system) {
            actions.add(
                MessageActionDescriptor(
                    MessageActionType.star,
                    if (message.isStarred) "Unstar" else "Star",
                ),
            )
        }

        // Edit (only own text messages within the policy window).
        if (isMe && message.type == MessageType.text &&
            !message.isDeletedForEveryone && allowEditPolicy
        ) {
            actions.add(
                MessageActionDescriptor(MessageActionType.edit, "Edit", isPrimary = false),
            )
        }

        // Pin (non-system messages).
        if (message.type != MessageType.system) {
            actions.add(
                MessageActionDescriptor(
                    MessageActionType.pin,
                    if (message.isPinned) "Unpin" else "Pin",
                    isPrimary = false,
                ),
            )
        }

        // Task creation from text message.
        if (message.text.isNotEmpty() && message.type == MessageType.text) {
            actions.add(
                MessageActionDescriptor(MessageActionType.task, "Create Task", isPrimary = false),
            )
        }

        // Message Info for outgoing messages.
        if (isMe && message.type != MessageType.system) {
            actions.add(
                MessageActionDescriptor(MessageActionType.info, "Message info", isPrimary = false),
            )
        }

        // Multi-select mode.
        actions.add(
            MessageActionDescriptor(MessageActionType.select, "Select", isPrimary = false),
        )

        // Delete options.
        actions.add(
            MessageActionDescriptor(
                MessageActionType.deleteForMe,
                "Delete for me",
                isDestructive = true,
                isPrimary = false,
            ),
        )
        if (isMe && !message.isDeletedForEveryone) {
            actions.add(
                MessageActionDescriptor(
                    MessageActionType.deleteForEveryone,
                    "Delete for everyone",
                    isDestructive = true,
                    isPrimary = false,
                ),
            )
        }

        return actions.toList()
    }
}
