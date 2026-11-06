package com.example.chat.data.demo

import com.example.chat.data.api.ApiUserProfile
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.UserProfile
import java.time.Duration
import java.time.Instant

/**
 * Sample content for the offline demo session (NOT in Flutter). Every id is
 * synthetic ("demo-*") and the data never leaves the device: no upload, no
 * realtime, no account. Restarting the app re-seeds this same sample set.
 */
object DemoSampleData {

    /** One captured instant for the whole sample set: conversations and their
     *  messages must agree on last-message fields exactly (DemoSampleDataTest),
     *  so every generator derives from the same seed. A process restart
     *  re-captures it, matching the re-seeded demo session. */
    private val seedInstant: Instant = Instant.now()

    fun demoUser(): ApiUserProfile = ApiUserProfile(
        id = DemoMode.USER_ID,
        username = "demo",
        displayName = "Demo User",
        avatarInitials = "DU",
        avatarColorHex = "0xFF6366F1",
        about = "Offline sample account",
    )

    fun demoUsers(): List<UserProfile> {
        val now = seedInstant
        return listOf(
            UserProfile(
                id = DemoMode.USER_ID,
                displayName = "Demo User",
                username = "demo",
                avatarInitials = "DU",
                avatarColorHex = "0xFF6366F1",
                about = "Offline sample account",
                presence = PresenceState.online,
                lastSeenAt = now,
            ),
            contact("demo:aisha", "Aisha (sample)", "AI", "0xFF0EA5E9", PresenceState.online, now),
            contact("demo:ravi", "Ravi (sample)", "RS", "0xFFF59E0B", PresenceState.offline, now.minus(Duration.ofHours(3))),
            contact("demo:meera", "Meera (sample)", "MK", "0xFF10B981", PresenceState.offline, now.minus(Duration.ofDays(1))),
        )
    }

    private fun contact(
        id: String,
        name: String,
        initials: String,
        color: String,
        presence: PresenceState,
        lastSeen: Instant,
    ) = UserProfile(
        id = id,
        displayName = name,
        username = id,
        avatarInitials = initials,
        avatarColorHex = color,
        about = "Sample contact",
        presence = presence,
        lastSeenAt = lastSeen,
    )

    fun demoConversations(): List<Conversation> {
        val now = seedInstant
        return listOf(
            Conversation(
                id = "demo:room-aisha",
                type = ConversationType.direct,
                title = "Aisha (sample)",
                participantIds = listOf(DemoMode.USER_ID, "demo:aisha"),
                avatarInitials = "AI",
                avatarColorHex = "0xFF0EA5E9",
                lastMessageText = "See you tomorrow!",
                lastMessageTime = now.minus(Duration.ofMinutes(12)),
                lastMessageSenderId = "demo:aisha",
                unreadCount = 1,
            ),
            Conversation(
                id = "demo:room-crew",
                type = ConversationType.group,
                title = "Product Crew (sample)",
                participantIds = listOf(DemoMode.USER_ID, "demo:aisha", "demo:ravi", "demo:meera"),
                avatarInitials = "PC",
                avatarColorHex = "0xFF8B5CF6",
                lastMessageText = "Demo messages stay on this device.",
                lastMessageTime = now.minus(Duration.ofHours(1)),
                lastMessageSenderId = DemoMode.USER_ID,
            ),
            Conversation(
                id = "demo:room-ravi",
                type = ConversationType.direct,
                title = "Ravi (sample)",
                participantIds = listOf(DemoMode.USER_ID, "demo:ravi"),
                avatarInitials = "RS",
                avatarColorHex = "0xFFF59E0B",
                lastMessageText = "Thanks!",
                lastMessageTime = now.minus(Duration.ofHours(6)),
                lastMessageSenderId = DemoMode.USER_ID,
            ),
        )
    }

    fun demoMessages(): Map<String, List<ChatMessage>> {
        val now = seedInstant
        return mapOf(
            "demo:room-aisha" to listOf(
                msg("m1", "demo:room-aisha", "demo:aisha", "Hi! This is a sample chat.", now.minus(Duration.ofMinutes(20))),
                msg("m2", "demo:room-aisha", DemoMode.USER_ID, "Nice - no server needed?", now.minus(Duration.ofMinutes(18)), DeliveryState.read),
                msg("m3", "demo:room-aisha", "demo:aisha", "Exactly. Everything here is offline demo data.", now.minus(Duration.ofMinutes(15))),
                msg("m4", "demo:room-aisha", "demo:aisha", "See you tomorrow!", now.minus(Duration.ofMinutes(12))),
            ),
            "demo:room-crew" to listOf(
                msg("m5", "demo:room-crew", "demo:meera", "Welcome to the sample group.", now.minus(Duration.ofHours(2))),
                msg("m6", "demo:room-crew", DemoMode.USER_ID, "Demo messages stay on this device.", now.minus(Duration.ofHours(1)), DeliveryState.delivered),
            ),
            "demo:room-ravi" to listOf(
                msg("m7", "demo:room-ravi", DemoMode.USER_ID, "Thanks!", now.minus(Duration.ofHours(6)), DeliveryState.read),
            ),
        )
    }

    private fun msg(
        id: String,
        room: String,
        sender: String,
        text: String,
        at: Instant,
        state: DeliveryState = DeliveryState.delivered,
    ) = ChatMessage(
        id = "demo:$id",
        conversationId = room,
        senderId = sender,
        text = text,
        createdAt = at,
        deliveryState = state,
    )
}
