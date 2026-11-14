package com.example.chat

import com.example.chat.core.messages.ViewOnceTextPolicy
import com.example.chat.data.local.SnapshotCodec
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ViewOnceTextLocalSafetyTest {
    @Test fun snapshotNeverContainsRestrictedTextCaptionUrlOrReply() {
        val message = ChatMessage(id = "m", conversationId = "c", senderId = "s", text = "PRIVATE_BODY",
            attachment = MessageAttachment("a", "image", "PRIVATE_NAME", "1", "https://example.invalid/PRIVATE_URL"),
            metadata = mapOf("view_once" to true, "caption" to "PRIVATE_CAPTION", "unknown_alias" to "PRIVATE_ALIAS"),
            replyToPreviewText = "PRIVATE_REPLY", createdAt = Instant.EPOCH)
        val serialized = SnapshotCodec.chatMessageToJson(message).toString()
        listOf("PRIVATE_BODY", "PRIVATE_NAME", "PRIVATE_URL", "PRIVATE_CAPTION", "PRIVATE_ALIAS", "PRIVATE_REPLY").forEach {
            assertFalse("Leaked $it", serialized.contains(it))
        }
        assertTrue(serialized.contains(ViewOnceTextPolicy.PLACEHOLDER))
    }
    @Test fun apiViewOnceTextNeverBecomesHistoryPlaintext() {
        val message = ChatMessage.fromApi(mapOf("id" to "m", "room_id" to "c", "sender_id" to "s",
            "content" to "PRIVATE_BODY", "view_once" to true, "view_once_state" to "consumed", "created_at" to Instant.EPOCH.toString()))
        assertEquals(ViewOnceTextPolicy.PLACEHOLDER, message.text)
        assertTrue(ViewOnceTextPolicy.consumed(message))
    }
    @Test fun oldSnapshotIsRedactedOnRead() {
        val decoded = SnapshotCodec.chatMessageFromJson(mapOf("id" to "m", "conversation_id" to "c", "sender_id" to "s",
            "text" to "PRIVATE_BODY", "type" to MessageType.text.name,
            "metadata" to mapOf("view_once" to true, "caption" to "PRIVATE_CAPTION")))
        assertEquals(ViewOnceTextPolicy.PLACEHOLDER, decoded.text)
        assertNull(decoded.metadata["caption"])
    }
    @Test fun tombstonesNeverOfferAnOpen() {
        val message = ChatMessage(id = "m", conversationId = "c", senderId = "s", text = "",
            metadata = mapOf("view_once" to true), createdAt = Instant.EPOCH)
        assertFalse(ViewOnceTextPolicy.unavailable(message))
        assertTrue(ViewOnceTextPolicy.unavailable(message.copy(isDeletedForEveryone = true)))
        assertTrue(ViewOnceTextPolicy.unavailable(message.copy(isDeletedForMe = true)))
        assertTrue(ViewOnceTextPolicy.unavailable(message.copy(isRevokedBySender = true)))
        assertTrue(ViewOnceTextPolicy.unavailable(message.copy(metadata = message.metadata + ("view_once_state" to "consumed"))))
    }

}
