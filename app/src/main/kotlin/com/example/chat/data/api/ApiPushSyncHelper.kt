package com.example.chat.data.api

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType

/**
 * Pure selection and deduplication rules for push-wake ("type": "sync") catch-up.
 * Kept context-free so JVM unit tests exercise the exact logic used on FCM wake.
 */
object ApiPushSyncHelper {

    fun selectRoomsToSyncForPush(
        conversationsBefore: List<Conversation>,
        conversationsAfter: List<Conversation>,
        dirtyRoomIds: Set<String>,
        syncedRoomIds: Set<String>,
        cachedMessagesByRoom: Map<String, List<ChatMessage>>,
        maxRooms: Int = 10,
    ): List<Conversation> {
        val beforeById = conversationsBefore.associateBy { it.id }
        return conversationsAfter
            .asSequence()
            .filter { conv ->
                val prev = beforeById[conv.id]
                val cached = cachedMessagesByRoom[conv.id]
                conv.id in dirtyRoomIds ||
                    conv.unreadCount > (prev?.unreadCount ?: 0) ||
                    (prev != null && conv.lastMessageTime.isAfter(prev.lastMessageTime)) ||
                    (conv.unreadCount > 0 && (prev == null || conv.id !in syncedRoomIds || cached.isNullOrEmpty()))
            }
            .sortedByDescending { it.lastMessageTime }
            .distinctBy { it.id }
            .take(maxRooms)
            .toList()
    }

    fun findNewIncomingMessagesForPush(
        myUserId: String?,
        conversation: Conversation,
        messagesBefore: List<ChatMessage>,
        messagesAfter: List<ChatMessage>,
        alreadyNotifiedIds: Set<String>,
        maxNotifyPerRoom: Int = 5,
    ): List<ChatMessage> {
        val knownIds = messagesBefore.mapTo(HashSet()) { it.id }
        val latestKnownTime = messagesBefore.maxOfOrNull { it.createdAt }
        val candidates = messagesAfter
            .asSequence()
            .filter { m ->
                m.senderId.isNotBlank() &&
                    m.senderId != myUserId &&
                    m.id !in knownIds &&
                    m.id !in alreadyNotifiedIds &&
                    m.type != MessageType.system &&
                    !m.isDeletedForEveryone &&
                    !m.isDeletedForMe &&
                    !m.isRevokedBySender
            }
            .sortedBy { it.createdAt }
            .toList()
        if (candidates.isEmpty()) return emptyList()
        if (messagesBefore.isEmpty()) {
            if (conversation.unreadCount <= 0) return emptyList()
            val count = minOf(conversation.unreadCount, maxNotifyPerRoom)
            return candidates.takeLast(count)
        }
        return candidates
            .filter { m ->
                m.deliveryState != DeliveryState.read || latestKnownTime == null || m.createdAt.isAfter(latestKnownTime)
            }
            .takeLast(maxNotifyPerRoom)
    }
}
