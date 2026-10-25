package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.ChatDetailScreen
import com.example.chat.ui.chats.ChatsHomeScreen
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant

/** debug-build-only host (lives in src/debug so the instrumentation process can launch it; test-APK activities resolve to the test process) that renders the chat screens with local fixture
 *  data. No backend, no account, no messages leave the device: this verifies
 *  rendering and interaction pixels only, never real backend E2E. */
class ChatFixtureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_HOME
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)) {
                when (screen) {
                    SCREEN_DETAIL -> ChatDetailScreen(
                        conversation = fixtureConversations().first(),
                        messages = fixtureMessages(),
                        currentUserId = "me",
                        onBack = {},
                        onSend = {},
                    )
                    else -> ChatsHomeScreen(
                        conversations = fixtureConversations(),
                        requiresSubscription = false,
                        subscriptionMessage = null,
                        onOpenConversation = {},
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_HOME = "home"
        const val SCREEN_DETAIL = "detail"

        fun fixtureConversations(): List<Conversation> = listOf(
            Conversation(
                id = "r1",
                type = ConversationType.direct,
                title = "Alice Rao",
                participantIds = listOf("me", "u2"),
                avatarInitials = "AR",
                avatarColorHex = "0xFF6366F1",
                lastMessageText = "See you at 6!",
                lastMessageTime = Instant.now().minusSeconds(300),
                lastMessageSenderId = "u2",
                unreadCount = 2,
                isPinned = true,
            ),
            Conversation(
                id = "r2",
                type = ConversationType.group,
                title = "Weekend Trek",
                participantIds = listOf("me", "u3", "u4"),
                avatarInitials = "WT",
                avatarColorHex = "0xFF6366F1",
                lastMessageText = "\uD83D\uDCF7 Photo",
                lastMessageTime = Instant.now().minusSeconds(3600),
                lastMessageSenderId = "u3",
                unreadCount = 0,
            ),
            Conversation(
                id = "r3",
                type = ConversationType.direct,
                title = "Bob Verma",
                participantIds = listOf("me", "u5"),
                avatarInitials = "BV",
                avatarColorHex = "0xFF6366F1",
                lastMessageText = "Sent the documents",
                lastMessageTime = Instant.now().minusSeconds(90000),
                lastMessageSenderId = "me",
            ),
        )

        fun fixtureMessages(): List<ChatMessage> = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.text,
                text = "Hey, are we still on for today?",
                createdAt = Instant.now().minusSeconds(1800),
                deliveryState = DeliveryState.delivered,
            ),
            ChatMessage(
                id = "m2",
                conversationId = "r1",
                senderId = "me",
                type = MessageType.text,
                text = "Yes! Leaving office by 5:30.",
                createdAt = Instant.now().minusSeconds(1500),
                deliveryState = DeliveryState.read,
            ),
            ChatMessage(
                id = "m3",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.text,
                text = "See you at 6!",
                createdAt = Instant.now().minusSeconds(300),
                deliveryState = DeliveryState.delivered,
            ),
            ChatMessage(
                id = "temp_fixture",
                conversationId = "r1",
                senderId = "me",
                type = MessageType.text,
                text = "Perfect",
                createdAt = Instant.now().minusSeconds(60),
                deliveryState = DeliveryState.sending,
            ),
        )
    }
}
