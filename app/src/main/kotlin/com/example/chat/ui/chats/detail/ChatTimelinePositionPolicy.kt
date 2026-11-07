package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.ChatMessage
import java.time.ZoneId
import java.time.LocalDate

/** Message IDs map to rendered rows, never raw-message indices when albums are collapsed. */
object ChatTimelinePositionPolicy {
    fun rowIndex(rows: List<MediaTimelineRow>, messageId: String): Int =
        rows.indexOfFirst { row -> row.messages.any { it.id == messageId } }
    fun latestRowIndex(rows: List<MediaTimelineRow>): Int? = rows.lastIndex.takeIf { it >= 0 }
    fun dayAt(rows: List<MediaTimelineRow>, index: Int, zone: ZoneId): LocalDate =
        rows[index].anchor.createdAt.atZone(zone).toLocalDate()
    fun startsDay(rows: List<MediaTimelineRow>, index: Int, zone: ZoneId): Boolean =
        index == 0 || dayAt(rows, index, zone) != dayAt(rows, index - 1, zone)
}
