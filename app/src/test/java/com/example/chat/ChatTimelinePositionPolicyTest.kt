package com.example.chat
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
class ChatTimelinePositionPolicyTest {
    private fun msg(id: String, at: String = "2026-01-01T10:00:00Z") = ChatMessage(
        id, "synthetic", "other", text = "synthetic", createdAt = Instant.parse(at))
    @Test fun memberOfCollapsedAlbumMapsToContainingRow() {
        val rows = listOf(MediaTimelineRow(listOf(msg("1"), msg("2"), msg("3"))), MediaTimelineRow(listOf(msg("4"))))
        assertEquals(0, ChatTimelinePositionPolicy.rowIndex(rows, "3"))
        assertEquals(1, ChatTimelinePositionPolicy.rowIndex(rows, "4"))
        assertEquals(-1, ChatTimelinePositionPolicy.rowIndex(rows, "missing"))
        assertEquals(1, ChatTimelinePositionPolicy.latestRowIndex(rows))
    }
    @Test fun emptyTimelineHasNoScrollTarget() {
        assertNull(ChatTimelinePositionPolicy.latestRowIndex(emptyList()))
        assertEquals(-1, ChatTimelinePositionPolicy.rowIndex(emptyList(), "missing"))
    }
    @Test fun separatorUsesUserZoneDateNotUtcOrEveryMessage() {
        val rows = listOf(MediaTimelineRow(listOf(msg("1", "2026-01-01T18:29:00Z"))),
            MediaTimelineRow(listOf(msg("2", "2026-01-01T18:31:00Z"))),
            MediaTimelineRow(listOf(msg("3", "2026-01-01T18:32:00Z"))))
        val zone = ZoneId.of("Asia/Kolkata")
        assertTrue(ChatTimelinePositionPolicy.startsDay(rows, 0, zone))
        assertTrue(ChatTimelinePositionPolicy.startsDay(rows, 1, zone))
        assertFalse(ChatTimelinePositionPolicy.startsDay(rows, 2, zone))
    }
}
