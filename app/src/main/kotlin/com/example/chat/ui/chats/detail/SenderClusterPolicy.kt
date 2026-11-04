package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.MessageType

/** Chronological render projection only, never media grouping or authorization. */
enum class SenderClusterPosition { SINGLE, FIRST, MIDDLE, LAST }
object SenderClusterPolicy {
    data class Item(val sender: String, val conversation: String, val millis: Long, val eligible: Boolean, val day: String = "")
    fun positions(items: List<Item>): List<SenderClusterPosition> {
        fun joins(a: Item?, b: Item?): Boolean = a != null && b != null && a.eligible && b.eligible &&
            a.sender == b.sender && a.conversation == b.conversation &&
            b.millis >= a.millis && b.millis - a.millis <= 120_000L &&
            a.day == b.day
        return items.indices.map { i ->
            val before = joins(items.getOrNull(i - 1), items[i])
            val after = joins(items[i], items.getOrNull(i + 1))
            when { before && after -> SenderClusterPosition.MIDDLE
                before -> SenderClusterPosition.LAST
                after -> SenderClusterPosition.FIRST
                else -> SenderClusterPosition.SINGLE }
        }
    }
    fun forRows(rows: List<MediaTimelineRow>, zone: java.time.ZoneId) = positions(rows.map { row ->
        val m = row.anchor
        Item(m.senderId, m.conversationId, m.createdAt.toEpochMilli(),
            row.messages.size == 1 && m.type == MessageType.text && !m.isDeletedForEveryone &&
                !m.isDeletedForMe && !m.isRevokedBySender && m.replyToMessageId == null,
            m.createdAt.atZone(zone).toLocalDate().toString())
    })
}
