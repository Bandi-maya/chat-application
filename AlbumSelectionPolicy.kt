package com.example.chat.ui.chats.detail

/** Whole validated group selection, without changing the displayed timeline or media geometry. */
object AlbumSelectionPolicy {
    fun unit(rows: List<MediaTimelineRow>, messageId: String): Set<String> =
        rows.firstOrNull { row -> row.messages.any { it.id == messageId } }
            ?.messages?.mapTo(linkedSetOf()) { it.id }.orEmpty()
    fun toggle(rows: List<MediaTimelineRow>, selected: Set<String>, messageId: String): Set<String> {
        val unit = unit(rows, messageId)
        return if (unit.isNotEmpty() && selected.containsAll(unit)) selected - unit else selected + unit
    }
}
