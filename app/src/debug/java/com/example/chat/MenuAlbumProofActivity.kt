package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.ChatDetailScreen
import com.example.chat.ui.chats.detail.MediaAlbumContract
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant

/** Debug-build-only and exported so `am start` from the shell/capture harness can
 *  reach it (ChatFixtureActivity is non-exported and cannot). Renders the real
 *  ChatDetailScreen with local fixture data for pixel proof of (a) the chat header
 *  overflow menu with icons and (b) a >4 image album capped at 2x2 with the +N scrim.
 *  No backend, no account, no messages leave the device; test-only, no product
 *  behavior change. */
class MenuAlbumProofActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MENU
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = false)) {
                ChatDetailScreen(
                    conversation = ChatFixtureActivity.fixtureConversations().first(),
                    messages = if (screen == SCREEN_ALBUM) albumMessages() else ChatFixtureActivity.fixtureMessages(),
                    currentUserId = "me",
                    initialChatMenuOpen = screen == SCREEN_MENU,
                    onBack = {},
                    onSend = {},
                )
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_MENU = "menu"
        const val SCREEN_ALBUM = "album"

        /** Six envelope-stamped images: the timeline groups them and the preview
         *  caps at four tiles with a "+2" scrim on the last visible tile. */
        fun albumMessages(): List<ChatMessage> = (0 until 6).map { index ->
            ChatMessage(
                id = "proof_album_$index",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.image,
                text = "",
                attachment = MessageAttachment(
                    id = "proof_att_$index",
                    type = "image",
                    name = "proof_$index.jpg",
                    size = "1 KB",
                    url = null,
                ),
                metadata = MediaAlbumContract.stamp("proof-album", index, 6),
                createdAt = Instant.now().minusSeconds(600 - index * 10L),
                deliveryState = DeliveryState.delivered,
            )
        }
    }
}
