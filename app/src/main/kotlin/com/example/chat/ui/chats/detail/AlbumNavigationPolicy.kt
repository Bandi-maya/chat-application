package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.ChatMessage

/** Preview compression is separate from the complete validated viewer sequence. */
object AlbumNavigationPolicy {
    fun preview(messages: List<ChatMessage>): List<ChatMessage> = messages.take(4)
    fun remainingCount(messages: List<ChatMessage>): Int = (messages.size - 4).coerceAtLeast(0)
    /** Only the current validated projection may supply a shared viewer sequence. */
    fun sequence(rows: List<MediaTimelineRow>, messageId: String): List<ChatMessage> =
        rows.firstOrNull { row -> row.messages.any { it.id == messageId } }?.messages.orEmpty()
    fun adjacent(sequence: List<ChatMessage>, messageId: String, direction: Int): ChatMessage? {
        require(direction == -1 || direction == 1)
        val index = sequence.indexOfFirst { it.id == messageId }
        return if (index < 0) null else sequence.getOrNull(index + direction)
    }
}
