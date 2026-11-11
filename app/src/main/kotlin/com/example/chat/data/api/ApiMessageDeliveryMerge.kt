package com.example.chat.data.api

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.DeliveryState
import java.time.Instant

/**
 * Pure delivery-tick and fetch-gating rules for [ApiBackendService].
 *
 * Keeps delivery progression monotonic (`sent` -> `delivered` -> `read`) across
 * WebSocket events and REST `GET /chat/rooms/{id}/messages` reloads, normalizes
 * the sender's own `new_message` echo (the backend broadcasts `is_delivered=true`
 * in WebSocket `new_message` even before any recipient receives the message), and
 * decides when a room actually needs a remote fetch vs. serving local storage.
 */
internal object ApiMessageDeliveryMerge {

    /**
     * Normalizes an inbound WebSocket `new_message` event.
     * When the sender receives their own message echoed back over WS, it represents
     * server acceptance (`sent`, single tick), never recipient delivery.
     */
    fun normalizeRealtimeMessage(message: ChatMessage, currentUserId: String?): ChatMessage {
        if (!currentUserId.isNullOrEmpty() &&
            message.senderId == currentUserId &&
            message.deliveryState == DeliveryState.delivered
        ) {
            return message.copy(deliveryState = DeliveryState.sent)
        }
        return message
    }

    /** Confirmed server delivery states ordered from lowest to highest progress. */
    private fun DeliveryState.isConfirmedServerState(): Boolean =
        this == DeliveryState.sent || this == DeliveryState.delivered || this == DeliveryState.read

    /**
     * Preserves monotonic confirmed delivery state (`sent < delivered < read`)
     * so REST reloads (which may report `is_delivered=false` on the backend) never
     * downgrade an already-delivered or already-read message back to a single tick.
     */
    fun preserveMonotonicDelivery(existing: ChatMessage?, incoming: ChatMessage): ChatMessage {
        if (existing != null &&
            existing.id == incoming.id &&
            existing.deliveryState.isConfirmedServerState() &&
            incoming.deliveryState.isConfirmedServerState() &&
            existing.deliveryState.ordinal > incoming.deliveryState.ordinal
        ) {
            return incoming.copy(deliveryState = existing.deliveryState)
        }
        return incoming
    }

    /**
     * Merges [remote] messages with any unconfirmed [current] pending messages while
     * preserving monotonic delivery progress for existing message IDs.
     */
    fun mergeRemoteMessages(
        current: List<ChatMessage>,
        remote: List<ChatMessage>,
        keepPending: (ChatMessage) -> Boolean,
    ): List<ChatMessage> {
        val existingById = current.associateBy { it.id }
        val mergedRemote = remote.map { m -> preserveMonotonicDelivery(existingById[m.id], m) }
        val pending = current.filter(keepPending)
        return (mergedRemote + pending).sortedBy { it.createdAt }
    }

    /**
     * Returns true only when the room actually needs a network `GET /messages` fetch
     * instead of relying on local storage + live WebSocket updates.
     */
    fun shouldFetchRoomMessages(
        hasSyncedThisSession: Boolean,
        isDirty: Boolean,
        socketConnected: Boolean,
        conversation: Conversation?,
        currentMessages: List<ChatMessage>,
    ): Boolean {
        if (!hasSyncedThisSession || isDirty || !socketConnected) return true
        if (conversation == null) return false
        if (conversation.unreadCount > 0) return true
        if (conversation.lastMessageText.isNotEmpty() && currentMessages.isEmpty()) return true
        val latestCachedAt = currentMessages.lastOrNull()?.createdAt ?: Instant.EPOCH
        if (currentMessages.isNotEmpty() && conversation.lastMessageTime.isAfter(latestCachedAt)) return true
        return false
    }
}
