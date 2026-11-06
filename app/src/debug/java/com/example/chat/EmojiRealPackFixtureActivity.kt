package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageReaction
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.ChatMessageBubble
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant

/** debug-only host: a REAL ChatMessageBubble with one emoji-only message and a reaction badge,
 *  using the production resolver (real Noto Lottie URL). Synthetic data, nothing sent. */
class EmojiRealPackFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = ThemePresets.getSystemDefaultTheme(platformIsLight = true)
        setContent {
            ChatyTheme(config = theme) {
                val msg = remember {
                    ChatMessage(
                        id = "real-pack-1", conversationId = "r1", senderId = "u2", type = MessageType.text,
                        text = "\uD83D\uDE02", createdAt = Instant.now(),
                        reactions = listOf(MessageReaction("\u2764\uFE0F", listOf("me"))),
                    )
                }
                Column { ChatMessageBubble(message = msg, isMe = false, theme = theme) }
            }
        }
    }
}
