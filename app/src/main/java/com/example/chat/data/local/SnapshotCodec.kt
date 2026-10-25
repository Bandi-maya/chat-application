package com.example.chat.data.local

import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.EncryptionStatus
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageReaction
import com.example.chat.domain.models.MessageType
import java.time.Duration
import java.time.Instant

/**
 * Exact port of lib/data/services/snapshot_codec.dart: persistence codecs for
 * the offline-first snapshot cache. Unknown enum values fall back to safe
 * defaults so a snapshot written by a newer build never crashes an older one.
 * Key names, null-omission, and value shapes are byte-identical to Dart.
 */
object SnapshotCodec {

    fun conversationFromJson(json: Map<String, Any?>): Conversation = Conversation(
        id = json["id"]?.toString() ?: "",
        type = conversationType(json["type"]?.toString()),
        title = json["title"]?.toString() ?: "",
        participantIds = stringList(json["participant_ids"]),
        adminIds = stringList(json["admin_ids"]),
        avatarInitials = json["avatar_initials"]?.toString(),
        avatarColorHex = json["avatar_color_hex"]?.toString(),
        lastMessageText = json["last_message_text"]?.toString() ?: "",
        lastMessageTime = parseIsoToInstant(json["last_message_time"]?.toString())
            ?: Instant.ofEpochMilli(0),
        lastMessageSenderId = json["last_message_sender_id"]?.toString() ?: "",
        unreadCount = (json["unread_count"] ?: 0).toString().toIntOrNull() ?: 0,
        isPinned = json["is_pinned"] == true,
        isArchived = json["is_archived"] == true,
        isMuted = json["is_muted"] == true,
        wallpaperId = json["wallpaper_id"]?.toString(),
        disappearingDuration = json["disappearing_seconds"]?.let {
            Duration.ofSeconds(it.toString().toLongOrNull() ?: 0)
        },
        encryptionStatus = encryptionStatus(json["encryption_status"]?.toString()),
        draftText = json["draft_text"]?.toString() ?: "",
    )

    fun conversationToJson(conversation: Conversation): Map<String, Any?> =
        buildMap {
            put("id", conversation.id)
            put("type", conversation.type.name)
            put("title", conversation.title)
            put("participant_ids", conversation.participantIds)
            put("admin_ids", conversation.adminIds)
            conversation.avatarInitials?.let { put("avatar_initials", it) }
            conversation.avatarColorHex?.let { put("avatar_color_hex", it) }
            put("last_message_text", conversation.lastMessageText)
            put("last_message_time", conversation.lastMessageTime.toString())
            put("last_message_sender_id", conversation.lastMessageSenderId)
            put("unread_count", conversation.unreadCount)
            put("is_pinned", conversation.isPinned)
            put("is_archived", conversation.isArchived)
            put("is_muted", conversation.isMuted)
            conversation.wallpaperId?.let { put("wallpaper_id", it) }
            conversation.disappearingDuration?.let {
                put("disappearing_seconds", it.seconds.toString())
            }
            put("encryption_status", conversation.encryptionStatus.name)
            put("draft_text", conversation.draftText)
        }

    fun chatMessageFromJson(json: Map<String, Any?>): ChatMessage {
        val attachmentRaw = json["attachment"]
        val attachment = if (attachmentRaw is Map<*, *>) {
            MessageAttachment(
                id = attachmentRaw["id"]?.toString() ?: "",
                type = attachmentRaw["type"]?.toString() ?: "file",
                name = attachmentRaw["name"]?.toString() ?: "",
                size = attachmentRaw["size"]?.toString() ?: "",
                url = attachmentRaw["url"]?.toString(),
                durationSeconds = (attachmentRaw["duration_seconds"] ?: 0)
                    .toString().toIntOrNull() ?: 0,
            )
        } else {
            null
        }
        val reactions = mutableListOf<MessageReaction>()
        val reactionsRaw = json["reactions"]
        if (reactionsRaw is List<*>) {
            for (item in reactionsRaw) {
                if (item !is Map<*, *>) continue
                reactions.add(
                    MessageReaction(
                        emoji = item["emoji"]?.toString() ?: "",
                        userIds = stringList(item["user_ids"]),
                    ),
                )
            }
        }
        val metadataRaw = json["metadata"]
        @Suppress("UNCHECKED_CAST")
        val metadata = if (metadataRaw is Map<*, *>) {
            metadataRaw.entries.associate { (k, v) -> k.toString() to v }
        } else {
            emptyMap()
        }
        return ChatMessage(
            id = json["id"]?.toString() ?: "",
            conversationId = json["conversation_id"]?.toString() ?: "",
            senderId = json["sender_id"]?.toString() ?: "",
            type = messageType(json["type"]?.toString()),
            text = json["text"]?.toString() ?: "",
            attachment = attachment,
            metadata = metadata,
            replyToMessageId = json["reply_to_message_id"]?.toString(),
            replyToPreviewText = json["reply_to_preview_text"]?.toString(),
            replyToSenderName = json["reply_to_sender_name"]?.toString(),
            linkedTaskId = json["linked_task_id"]?.toString(),
            reactions = reactions,
            createdAt = parseIsoToInstant(json["created_at"]?.toString())
                ?: Instant.ofEpochMilli(0),
            editedAt = json["edited_at"]?.let { parseIsoToInstant(it.toString()) },
            deliveryState = deliveryState(json["delivery_state"]?.toString()),
            isPinned = json["is_pinned"] == true,
            isStarred = json["is_starred"] == true,
            isDeletedForEveryone = json["deleted_for_everyone"] == true,
            isDeletedForMe = json["deleted_for_me"] == true,
        )
    }

    fun chatMessageToJson(message: ChatMessage): Map<String, Any?> =
        buildMap {
            put("id", message.id)
            put("conversation_id", message.conversationId)
            put("sender_id", message.senderId)
            put("type", message.type.name)
            put("text", message.text)
            message.attachment?.let { a ->
                put(
                    "attachment",
                    buildMap {
                        put("id", a.id)
                        put("type", a.type)
                        put("name", a.name)
                        put("size", a.size)
                        a.url?.let { put("url", it) }
                        put("duration_seconds", a.durationSeconds)
                    },
                )
            }
            put("metadata", message.metadata)
            message.replyToMessageId?.let { put("reply_to_message_id", it) }
            message.replyToPreviewText?.let { put("reply_to_preview_text", it) }
            message.replyToSenderName?.let { put("reply_to_sender_name", it) }
            message.linkedTaskId?.let { put("linked_task_id", it) }
            put(
                "reactions",
                message.reactions.map { reaction ->
                    mapOf(
                        "emoji" to reaction.emoji,
                        "user_ids" to reaction.userIds,
                    )
                },
            )
            put("created_at", message.createdAt.toString())
            message.editedAt?.let { put("edited_at", it.toString()) }
            put("delivery_state", message.deliveryState.name)
            put("is_pinned", message.isPinned)
            put("is_starred", message.isStarred)
            put("deleted_for_everyone", message.isDeletedForEveryone)
            put("deleted_for_me", message.isDeletedForMe)
        }

    private fun conversationType(value: String?): ConversationType = when (value) {
        "group" -> ConversationType.group
        else -> ConversationType.direct
    }

    private fun encryptionStatus(value: String?): EncryptionStatus = when (value) {
        "encrypted" -> EncryptionStatus.encrypted
        "demoMode" -> EncryptionStatus.demoMode
        else -> EncryptionStatus.verificationNeeded
    }

    private fun messageType(value: String?): MessageType = when (value) {
        "image" -> MessageType.image
        "video" -> MessageType.video
        "audio" -> MessageType.audio
        "document" -> MessageType.document
        "location" -> MessageType.location
        "contact" -> MessageType.contact
        "taskCard" -> MessageType.taskCard
        "system" -> MessageType.system
        else -> MessageType.text
    }

    private fun deliveryState(value: String?): DeliveryState = when (value) {
        "queued" -> DeliveryState.queued
        "sending" -> DeliveryState.sending
        "sent" -> DeliveryState.sent
        "read" -> DeliveryState.read
        "failed" -> DeliveryState.failed
        else -> DeliveryState.delivered
    }

    private fun stringList(source: Any?): List<String> =
        (source as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
}
