package com.example.chat.domain.models

import com.example.chat.core.util.parseIsoOrNow
import java.time.Instant
import org.json.JSONObject

enum class MessageType { text, image, video, audio, document, location, contact, taskCard, system }

fun messageTypeToDatabase(type: MessageType): String = when (type) {
    MessageType.image -> "image"
    MessageType.video -> "video"
    MessageType.audio -> "audio"
    MessageType.document -> "document"
    MessageType.location -> "location"
    MessageType.contact -> "contact"
    MessageType.taskCard -> "task"
    MessageType.system -> "system"
    MessageType.text -> "text"
}

fun messageTypeFromDatabase(type: String?): MessageType = when (type) {
    "image" -> MessageType.image
    "video" -> MessageType.video
    "audio" -> MessageType.audio
    "document" -> MessageType.document
    "location" -> MessageType.location
    "contact" -> MessageType.contact
    "task" -> MessageType.taskCard
    "system" -> MessageType.system
    else -> MessageType.text
}

enum class DeliveryState { queued, sending, sent, delivered, read, failed }

data class MessageReaction(val emoji: String, val userIds: List<String>)

data class MessageAttachment(
    val id: String,
    val type: String,
    val name: String,
    val size: String,
    val url: String? = null,
    val durationSeconds: Int = 0,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val type: MessageType = MessageType.text,
    val text: String,
    val attachment: MessageAttachment? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyToPreviewText: String? = null,
    val replyToSenderName: String? = null,
    val linkedTaskId: String? = null,
    val reactions: List<MessageReaction> = emptyList(),
    val createdAt: Instant,
    val editedAt: Instant? = null,
    val deliveryState: DeliveryState = DeliveryState.delivered,
    val isPinned: Boolean = false,
    val isStarred: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val isDeletedForMe: Boolean = false,
    val isRevokedBySender: Boolean = false,
) {
    companion object {
        /** Exact port of ChatMessage.fromApi (lib/domain/models/chat_message.dart). */
        fun fromApi(json: Map<String, Any?>): ChatMessage {
            val senderName = json["sender_name"]?.toString() ?: ""
            val rawContent = json["content"]?.toString() ?: ""
            var parsedType = MessageType.text
            var parsedAttachment: MessageAttachment? = null
            var effectiveText = rawContent

            if (rawContent.startsWith("{") && rawContent.endsWith("}")) {
                try {
                    val decoded = JSONObject(rawContent)
                    if (decoded.has("url")) {
                        val t = decoded.optString("type", "")
                        parsedType = messageTypeFromDatabase(t)
                        effectiveText = decoded.optString("text", "")
                        parsedAttachment = MessageAttachment(
                            id = json["uid"]?.toString() ?: json["id"]?.toString() ?: "",
                            type = t.ifEmpty { "image" },
                            name = decoded.optString("name", "attachment"),
                            size = decoded.optString("size", ""),
                            url = decoded.optString("url", "").ifEmpty { null },
                        )
                    }
                } catch (_: Exception) {
                }
            } else if (rawContent.startsWith("http") || rawContent.startsWith("/static/uploads/")) {
                val lower = rawContent.lowercase()
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                    lower.endsWith(".webp") || lower.endsWith(".gif")
                ) {
                    parsedType = MessageType.image
                    parsedAttachment = MessageAttachment(
                        id = json["uid"]?.toString() ?: json["id"]?.toString() ?: "",
                        type = "image",
                        name = "Photo",
                        size = "",
                        url = rawContent,
                    )
                    effectiveText = ""
                }
            }

            return ChatMessage(
                id = json["uid"]?.toString() ?: json["id"]?.toString() ?: "",
                conversationId = json["room_id"]?.toString() ?: "",
                senderId = json["sender_id"]?.toString() ?: "",
                type = parsedType,
                text = effectiveText,
                attachment = parsedAttachment,
                createdAt = parseIsoOrNow(json["created_at"]?.toString()),
                deliveryState = DeliveryState.delivered,
                metadata = if (senderName.isNotEmpty()) mapOf("sender_name" to senderName) else emptyMap(),
            )
        }
    }
}
