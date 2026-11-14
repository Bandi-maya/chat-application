package com.example.chat.bubbles

import com.example.chat.domain.models.ChatMessage
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class BubbleContentPolicyTest {
    private val message = ChatMessage("m", "room", "sender", text = "hello", createdAt = Instant.EPOCH)
    @Test fun ordinaryTextAllowed() { assertTrue(BubbleContentPolicy.plainText(message)) }
    @Test fun deleteForMeNotVisible() { assertFalse(BubbleContentPolicy.visible(message.copy(isDeletedForMe = true))) }
    @Test fun deletedForAllNotRenderedAsPlainText() { assertFalse(BubbleContentPolicy.plainText(message.copy(isDeletedForEveryone = true))) }
    @Test fun revokedTextNotRendered() { assertFalse(BubbleContentPolicy.plainText(message.copy(isRevokedBySender = true))) }
    @Test fun viewOnceNeverRendered() { assertFalse(BubbleContentPolicy.plainText(message.copy(metadata = mapOf("view_once" to true)))) }
    @Test fun ambiguousViewOnceNeverRendered() { assertFalse(BubbleContentPolicy.plainText(message.copy(metadata = mapOf("view_once" to "true")))) }
    @Test fun structuredTextNotRenderedAsPlainText() { assertFalse(BubbleContentPolicy.plainText(message.copy(text = "[POLL] hidden payload"))) }
}
