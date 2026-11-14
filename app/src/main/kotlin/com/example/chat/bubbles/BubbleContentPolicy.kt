package com.example.chat.bubbles

import com.example.chat.domain.models.ChatMessage
import com.example.chat.core.messages.LocalMessageOverridePolicy

/** Never widen private/deleted/structured content into the plain-text bubble renderer. */
internal object BubbleContentPolicy {
    fun visible(message: ChatMessage): Boolean = !message.isDeletedForMe
    fun plainText(message: ChatMessage): Boolean = visible(message) &&
        LocalMessageOverridePolicy.editable(message) && message.attachment == null &&
        (!message.metadata.containsKey("view_once") || message.metadata["view_once"] == false)
}
