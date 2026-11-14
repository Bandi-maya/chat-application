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
    val readAt: Instant? = null,
    val recipientReceipts: Map<String, com.example.chat.data.api.MessageReceiptTimes> = emptyMap(),
) {
    companion object {
        /** Exact port of ChatMessage.fromApi (lib/domain/models/chat_message.dart). */
        fun fromApi(json: Map<String, Any?>): ChatMessage {
            val senderName = json["sender_name"]?.toString() ?: ""
            val rawContent = json["content"]?.toString() ?: ""
            var parsedType = MessageType.text
            var parsedAttachment: MessageAttachment? = null
            var effectiveText = rawContent
            val attachmentMetadata = linkedMapOf<String, Any?>()

            if (rawContent.startsWith("{") && rawContent.endsWith("}")) {
                try {
                    val decoded = JSONObject(rawContent)
                    if (decoded.has("url")) {
                        val t = decoded.optString("type", "")
                        parsedType = messageTypeFromDatabase(t)
                        effectiveText = decoded.optString("text", "")
                        parsedAttachment = MessageAttachment(
                            id = decoded.optString("id", json["uid"]?.toString() ?: json["id"]?.toString() ?: ""),
                            type = t.ifEmpty { "image" },
                            name = decoded.optString("name", "attachment"),
                            size = decoded.optString("size", ""),
                            url = decoded.optString("url", "").ifEmpty { null },
                            durationSeconds = (decoded.opt("duration") as? Number)?.let { value ->
                                val seconds = value.toDouble()
                                if (seconds.isFinite() && seconds >= 0 && seconds <= Int.MAX_VALUE.toDouble() &&
                                    seconds == kotlin.math.floor(seconds)) seconds.toInt() else 0
                            } ?: 0,
                        )
                        // Only the explicit envelope contract is retained. Unknown names and
                        // values may contain secrets; record only their presence so projection
                        // remains conservative without persisting their payload.
                        val structuralKeys = setOf("id", "uid", "room_id", "conversation_id", "sender_id", "sender_name",
                            "type", "url", "name", "size", "text", "duration", "created_at")
                        val stickerKeys = setOf("chaty_sticker_version", "chaty_sticker_pack", "chaty_sticker_id")
                        val stickerHint = com.example.chat.core.stickers.StickerMessagePolicy.parse(t,
                            stickerKeys.associateWith { key -> if (decoded.has(key)) decoded.opt(key) else null })
                        var unsupported = false
                        decoded.keys().forEach { key ->
                            val value = decoded.opt(key)
                            when {
                                key == "view_once" -> attachmentMetadata[key] = when (value) {
                                    null, JSONObject.NULL -> null
                                    is Boolean -> value
                                    is String -> when (value.trim().lowercase(java.util.Locale.ROOT)) {
                                        "true" -> true
                                        "false" -> false
                                        else -> true
                                    }
                                    else -> true
                                }
                                key.equals("view_once", ignoreCase = true) -> {
                                    // An ambiguous protection spelling must never become unrestricted.
                                    unsupported = true
                                }
                                key == "forwarded" && value is Boolean -> attachmentMetadata[key] = value
                                key == "chaty_album_id" && value is String && value.isNotBlank() && value.length <= 128 -> attachmentMetadata[key] = value
                                key in setOf("chaty_album_v", "chaty_album_index", "chaty_album_count") && value is Number -> {
                                    val n = value.toDouble()
                                    if (n.isFinite() && n == kotlin.math.floor(n) && when (key) {
                                        "chaty_album_v" -> n == 1.0
                                        "chaty_album_index" -> n >= 0 && n < 100
                                        else -> n >= 2 && n <= 100
                                    }) attachmentMetadata[key] = n.toInt()
                                    else unsupported = true
                                }
                                key in setOf("p2p_invite_ciphertext", "p2p_invite_group") && value is String && value.isNotBlank() -> attachmentMetadata[key] = value
                                key in setOf("p2p_invite_epoch", "p2p_transfer_version") && value is Number -> {
                                    val n = value.toDouble()
                                    if (n.isFinite() && n == kotlin.math.floor(n) &&
                                        (if (key == "p2p_transfer_version") n == 1.0 else n >= 0 && n <= Int.MAX_VALUE.toDouble())) attachmentMetadata[key] = n.toInt()
                                    else unsupported = true
                                }
                                key in stickerKeys -> {
                                    // A complete validated display hint is allowed, never protection authority.
                                    if (stickerHint.isEmpty()) unsupported = true
                                }
                                key in structuralKeys -> Unit
                                else -> unsupported = true
                            }
                        }
                        if (decoded.keys().asSequence().any { it != "view_once" && it.equals("view_once", ignoreCase = true) }) {
                            attachmentMetadata["view_once"] = true
                        }
                        attachmentMetadata.putAll(stickerHint)
                        if (unsupported) attachmentMetadata["unsupported_envelope_metadata"] = true

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

            if (json["view_once"] == true || json["view_once"] == "true") {
                attachmentMetadata["view_once"] = true
                json["view_once_state"]?.let { attachmentMetadata["view_once_state"] = it }
                if (json["encrypted"] == true) attachmentMetadata["encrypted"] = true
            }
            if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(attachmentMetadata) && parsedType == MessageType.text) {
                effectiveText = com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
            }
            return ChatMessage(
                id = json["uid"]?.toString() ?: json["id"]?.toString() ?: "",
                conversationId = json["room_id"]?.toString() ?: "",
                senderId = json["sender_id"]?.toString() ?: "",
                type = parsedType,
                text = effectiveText,
                attachment = parsedAttachment,
                createdAt = parseIsoOrNow(json["created_at"]?.toString()),
                editedAt = json["edited_at"]?.toString()?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() },
                isDeletedForEveryone = json["is_deleted"] == true || json["deleted_at"] != null,
                reactions = (json["reactions"] as? List<*>)?.mapNotNull { item ->
                    val row = item as? Map<*, *> ?: return@mapNotNull null
                    val emoji = row["emoji"] as? String ?: return@mapNotNull null
                    val ids = (row["user_ids"] as? List<*>)?.filterIsInstance<String>()?.distinct() ?: return@mapNotNull null
                    MessageReaction(emoji, ids)
                }.orEmpty(),
                readAt = (json["read_at"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() },
                // Ticks for messages the caller sent: is_read = blue, is_delivered = two grey ticks,
                // otherwise the server has it but the other phone does not yet (one tick).
                // Older servers without delivery proof must not fabricate device delivery.
                deliveryState = when {
                    json["is_read"] == true || json["is_read"]?.toString() == "true" -> DeliveryState.read
                    !json.containsKey("is_delivered") -> DeliveryState.sent
                    json["is_delivered"] == true || json["is_delivered"]?.toString() == "true" -> DeliveryState.delivered
                    else -> DeliveryState.sent
                },
                metadata = buildMap { if (senderName.isNotEmpty()) put("sender_name", senderName); putAll(attachmentMetadata); json["updated_at"]?.let { put("reaction_updated_at", it.toString()) } },
            )
        }
    }
}
