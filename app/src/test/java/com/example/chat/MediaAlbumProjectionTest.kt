package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.chats.detail.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MediaAlbumProjectionTest {
    private fun batch(count: Int = 2) = (0 until count).map { i ->
        ChatMessage("m$i", "conversation", "sender", MessageType.image, if (i == 0) "One caption" else "",
            attachment = MessageAttachment("a$i", "image", "Photo $i", "local", "offline"),
            metadata = MediaAlbumContract.stamp("batch", i, count), createdAt = Instant.EPOCH)
    }
    @Test fun explicitCompleteBatchGroupsWithOriginalOrderedIdentitiesAndCaption() {
        val input = batch()
        val rows = mediaTimelineRows(input)
        assertEquals(1, rows.size)
        assertEquals(input, rows.single().messages)
        assertEquals("One caption", rows.single().anchor.text)
    }
    @Test fun adjacencyWithoutBatchNeverGroups() {
        assertEquals(2, mediaTimelineRows(batch().map { it.copy(metadata = emptyMap()) }).size)
    }
    @Test fun incompleteWrongOrderDuplicateAndMixedSenderRemainSeparate() {
        val b = batch()
        assertEquals(1, mediaTimelineRows(b.take(1)).single().messages.size)
        for (input in listOf(b.reversed(), listOf(b[0], b[0]), listOf(b[0], b[1].copy(senderId = "other")),
            listOf(b[0], b[1].copy(conversationId = "other")))) {
            assertTrue(mediaTimelineRows(input).all { it.messages.size == 1 })
        }
    }
    @Test fun protectedAndPerMessageOperationsFallbackToNormalBubbles() {
        val b = batch()
        val modified = listOf(b[1].copy(metadata = b[1].metadata + ("view_once" to true)),
            b[1].copy(metadata = b[1].metadata + ("view_once" to "malformed")),
            b[1].copy(metadata = b[1].metadata + ("forwarded" to true)),
            b[1].copy(replyToMessageId = "reply"), b[1].copy(isDeletedForMe = true),
            b[1].copy(isRevokedBySender = true), b[1].copy(isStarred = true),
            b[1].copy(reactions = listOf(MessageReaction("x", listOf("peer")))),
            b[1].copy(deliveryState = DeliveryState.failed), b[1].copy(text = "Second caption"))
        for (second in modified) assertEquals(2, mediaTimelineRows(listOf(b[0], second)).size)
        assertEquals(2, mediaTimelineRows(b, expand = true).size)
    }
    @Test fun layoutIncludesEveryItemAtBoundedColumnCount() {
        for (count in 2..100) {
            val columns = mediaAlbumColumns(count)
            assertEquals(count, columns.sum())
            assertTrue(columns.all { it in 1..2 })
        }
    }
    @Test fun geometryBeginsAtTileCenterAndEndsAtDestinationWithoutStretch() {
        val start = mediaOpeningTransform(150f, 90f, 140f, 300f, 150f, 400f, 0f)
        assertEquals(0.5f, start.scale, 0.001f); assertEquals(-60f, start.x, 0.001f); assertEquals(-260f, start.y, 0.001f)
        assertEquals(MediaOpeningTransform(1f, 0f, 0f), mediaOpeningTransform(150f, 90f, 140f, 300f, 150f, 400f, 1f))
    }
}
