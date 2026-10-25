package com.example.chat.data.messaging

import com.example.chat.core.util.parseIsoOrNow
import java.time.Instant

/**
 * A user-authored message that cannot be MLS-encrypted yet because one or
 * more conversation devices have not completed MLS registration.
 *
 * The payload is never uploaded in cleartext. Persistence is delegated to the
 * snapshot cache, which AES-256-GCM encrypts the complete list at rest with a
 * key held in platform secure storage.
 * Exact port of pending_secure_send_store.dart / PendingSecureSend.
 */
data class PendingSecureSend(
    val clientMessageId: String,
    val conversationId: String,
    val type: String,
    val text: String,
    val metadata: Map<String, Any?>,
    val createdAt: Instant,
    val attachment: Map<String, Any?>? = null,
    val replyToMessageId: String? = null,
    val replyToPreviewText: String? = null,
    val replyToSenderName: String? = null,
    val linkedTaskId: String? = null,
) {
    fun toJson(): Map<String, Any?> = buildMap {
        put("client_message_id", clientMessageId)
        put("conversation_id", conversationId)
        put("type", type)
        put("text", text)
        put("metadata", metadata)
        put("created_at", createdAt.toString())
        attachment?.let { put("attachment", it) }
        replyToMessageId?.let { put("reply_to_message_id", it) }
        replyToPreviewText?.let { put("reply_to_preview_text", it) }
        replyToSenderName?.let { put("reply_to_sender_name", it) }
        linkedTaskId?.let { put("linked_task_id", it) }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): PendingSecureSend {
            val metadata = (json["metadata"] as? Map<String, Any?>) ?: emptyMap()
            val attachment = json["attachment"] as? Map<String, Any?>
            return PendingSecureSend(
                clientMessageId = json["client_message_id"]?.toString() ?: "",
                conversationId = json["conversation_id"]?.toString() ?: "",
                type = json["type"]?.toString() ?: "text",
                text = json["text"]?.toString() ?: "",
                metadata = metadata,
                createdAt = parseIsoOrNow(json["created_at"]?.toString()),
                attachment = attachment,
                replyToMessageId = json["reply_to_message_id"]?.toString(),
                replyToPreviewText = json["reply_to_preview_text"]?.toString(),
                replyToSenderName = json["reply_to_sender_name"]?.toString(),
                linkedTaskId = json["linked_task_id"]?.toString(),
            )
        }
    }
}
