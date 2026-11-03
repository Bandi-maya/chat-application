package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.chats.detail.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class AlbumSelectionPolicyTest {
    private fun album(count: Int = 6) = (0 until count).map { i ->
        ChatMessage("m$i", "c", "sender", MessageType.image, "",
            attachment = MessageAttachment("a$i", "image", "photo", "small", "remote"),
            metadata = MediaAlbumContract.stamp("album", i, count), createdAt = Instant.EPOCH)
    }
    @Test fun hiddenTilesArePartOfWholeSelectionUnit() {
        val rows = mediaTimelineRows(album())
        assertEquals((0..5).map { "m$it" }.toSet(), AlbumSelectionPolicy.unit(rows, "m3"))
    }
    @Test fun selectionDoesNotExpandOrChangeProjection() {
        val rows = mediaTimelineRows(album())
        val before = rows.map { row -> row.messages.map { it.id } }
        val selected = AlbumSelectionPolicy.toggle(rows, emptySet(), "m0")
        assertEquals(6, selected.size)
        assertEquals(before, rows.map { row -> row.messages.map { it.id } })
        assertEquals(1, rows.size)
    }
    @Test fun toggleAnyMemberDeselectsEntireAlbumButPreservesOtherSelection() {
        val rows = mediaTimelineRows(album())
        val selected = AlbumSelectionPolicy.toggle(rows, setOf("other"), "m0")
        assertEquals(setOf("other"), AlbumSelectionPolicy.toggle(rows, selected, "m5"))
    }
    @Test fun malformedBatchDoesNotBroadenSelection() {
        val messages = album().map { it.copy(metadata = emptyMap()) }
        assertEquals(setOf("m2"), AlbumSelectionPolicy.unit(mediaTimelineRows(messages), "m2"))
    }
    @Test fun restrictedBatchDoesNotBroadenSelection() {
        val messages = album().map { it.copy(metadata = it.metadata + ("view_once" to true)) }
        assertEquals(setOf("m2"), AlbumSelectionPolicy.unit(mediaTimelineRows(messages), "m2"))
    }
    @Test fun staleIdSelectsNothing() {
        val rows = mediaTimelineRows(album())
        assertTrue(AlbumSelectionPolicy.unit(rows, "gone").isEmpty())
        assertEquals(setOf("other"), AlbumSelectionPolicy.toggle(rows, setOf("other"), "gone"))
    }
}
