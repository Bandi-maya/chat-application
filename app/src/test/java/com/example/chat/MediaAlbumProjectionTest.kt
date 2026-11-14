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
    @Test fun unstampedBackToBackImagesGroupWithOrderedIdentitiesAndAnchorCaption() {
        val input = batch().map { it.copy(metadata = emptyMap()) }
        val rows = mediaTimelineRows(input)
        assertEquals(1, rows.size)
        assertEquals(input, rows.single().messages)
        assertEquals("One caption", rows.single().anchor.text)
    }
    @Test fun localGroupingStopsAtTextSenderDeliveryStateAndDayBoundaries() {
        val pair = batch().map { it.copy(metadata = emptyMap()) }
        val textBetween = listOf(pair[0],
            ChatMessage("t1", "conversation", "sender", MessageType.text, "hello", createdAt = Instant.EPOCH), pair[1])
        assertEquals(3, mediaTimelineRows(textBetween).size)
        assertEquals(2, mediaTimelineRows(listOf(pair[0], pair[1].copy(text = "Second caption"))).size)
        assertEquals(2, mediaTimelineRows(listOf(pair[0], pair[1].copy(senderId = "other"))).size)
        assertEquals(2, mediaTimelineRows(listOf(pair[0], pair[1].copy(conversationId = "other"))).size)
        assertEquals(2, mediaTimelineRows(listOf(pair[0], pair[1].copy(deliveryState = DeliveryState.read))).size)
        assertEquals(2, mediaTimelineRows(listOf(pair[0], pair[1].copy(createdAt = Instant.EPOCH.plusSeconds(172800)))).size)
    }
    @Test fun localGroupingLeavesProtectedAndUnsupportedMessagesSeparate() {
        val base = batch().map { it.copy(metadata = emptyMap()) }
        val modified = listOf(base[1].copy(metadata = mapOf("view_once" to true)),
            base[1].copy(metadata = mapOf("unsupported_envelope_metadata" to true)),
            base[1].copy(metadata = mapOf("forwarded" to true)),
            base[1].copy(replyToMessageId = "reply"), base[1].copy(isDeletedForMe = true),
            base[1].copy(isRevokedBySender = true), base[1].copy(isStarred = true),
            base[1].copy(reactions = listOf(MessageReaction("x", listOf("peer")))),
            base[1].copy(deliveryState = DeliveryState.failed))
        for (second in modified) assertEquals(2, mediaTimelineRows(listOf(base[0], second)).size)
    }
    @Test fun localRunNeverMergesWithExplicitAlbumEnvelope() {
        val stamped = batch()
        val loose = batch().map { it.copy(metadata = emptyMap()) }
        assertEquals(2, mediaTimelineRows(stamped + loose.first()).size)
        assertEquals(2, mediaTimelineRows(listOf(loose.first()) + stamped).size)
        assertEquals(1, mediaTimelineRows(listOf(loose[0], loose[1], loose[1].copy(id = "m2", attachment = loose[1].attachment!!.copy(id = "a2")))).size)
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
