package com.example.chat.core.messages

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType

/** Display/cache policy, never an authorization or proof of server consumption. */
object ViewOnceTextPolicy {
    const val PLACEHOLDER = "View once message"
    const val UNAVAILABLE = "No longer available"
    fun restricted(metadata: Map<String, Any?>): Boolean = metadata["view_once"] == true || metadata["view_once"] == "true"
    fun text(message: ChatMessage): Boolean = message.type == MessageType.text && restricted(message.metadata)
    fun consumed(message: ChatMessage): Boolean = message.metadata["view_once_state"] == "consumed"
    fun unavailable(message: ChatMessage): Boolean = consumed(message) || message.isDeletedForEveryone || message.isDeletedForMe || message.isRevokedBySender
    fun preview(message: ChatMessage): String = if (restricted(message.metadata)) PLACEHOLDER else message.text
    fun redact(message: ChatMessage): ChatMessage = if (!restricted(message.metadata)) message else message.copy(
        text = if (message.type == MessageType.text) PLACEHOLDER else "", attachment = null,
        replyToPreviewText = null,
        metadata = buildMap {
            put("view_once", true)
            message.metadata["view_once_state"]?.let { put("view_once_state", it) }
            // Preserve the type hint only, never body/ciphertext/URL/caption aliases.
            if (message.metadata["encrypted"] == true) put("encrypted", true)
        })
}
