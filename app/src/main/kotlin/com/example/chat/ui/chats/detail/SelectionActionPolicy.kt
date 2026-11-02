package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType

enum class SelectionAction { forward, copy, delete, star }

/** Shared capability intersection. No translation action until a real provider exists. */
object SelectionActionPolicy {
    fun available(messages: List<ChatMessage>): Set<SelectionAction> {
        if (messages.isEmpty()) return emptySet()
        val actions = linkedSetOf(SelectionAction.delete)
        val standard = messages.all { it.type != MessageType.system && !it.isDeletedForEveryone && !it.isDeletedForMe && it.metadata["view_once"] != true }
        if (standard) {
            actions += SelectionAction.forward
            actions += SelectionAction.star
            if (messages.all { it.type == MessageType.text && it.text.isNotBlank() }) actions += SelectionAction.copy
        }
        return actions
    }
}
