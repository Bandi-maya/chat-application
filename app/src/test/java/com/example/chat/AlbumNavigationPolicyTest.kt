package com.example.chat

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class AlbumNavigationPolicyTest {
    private fun msg(id: String) = ChatMessage(id = id, conversationId = "synthetic", senderId = "synthetic",
        type = MessageType.image, text = "", createdAt = Instant.EPOCH)
    @Test fun previewCapsFourWithoutDroppingViewerItems() {
        val messages = (1..9).map { msg(it.toString()) }
        val rows = listOf(MediaTimelineRow(messages))
        assertEquals(listOf("1", "2", "3", "4"), AlbumNavigationPolicy.preview(messages).map { it.id })
        assertEquals(5, AlbumNavigationPolicy.remainingCount(messages))
        assertEquals(messages, AlbumNavigationPolicy.sequence(rows, "4"))
    }
    @Test fun smallPreviewHasNoRemainingCount() {
        for (size in 0..4) { val messages = (1..size).map { msg(it.toString()) }
            assertEquals(messages, AlbumNavigationPolicy.preview(messages))
            assertEquals(0, AlbumNavigationPolicy.remainingCount(messages)) }
    }
    @Test fun navigationDoesNotWrapOrResurrectMissingTarget() {
        val sequence = listOf(msg("1"), msg("2"), msg("3"))
        assertNull(AlbumNavigationPolicy.adjacent(sequence, "1", -1))
        assertEquals("3", AlbumNavigationPolicy.adjacent(sequence, "2", 1)?.id)
        assertEquals("1", AlbumNavigationPolicy.adjacent(sequence, "2", -1)?.id)
        assertNull(AlbumNavigationPolicy.adjacent(sequence, "3", 1))
        assertNull(AlbumNavigationPolicy.adjacent(sequence, "gone", 1))
    }
    @Test fun unrelatedAndReprojectedRowsNeverJoin() {
        val rows = listOf(MediaTimelineRow(listOf(msg("1"))), MediaTimelineRow(listOf(msg("2"))))
        assertEquals(listOf("1"), AlbumNavigationPolicy.sequence(rows, "1").map { it.id })
        assertTrue(AlbumNavigationPolicy.sequence(rows, "gone").isEmpty())
    }
}
