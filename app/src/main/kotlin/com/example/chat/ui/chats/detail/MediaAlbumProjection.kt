package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState

/** Versioned client envelope hints, never adjacency/timestamp inference or authorization. */
object MediaAlbumContract {
    const val VERSION = "chaty_album_v"
    const val ID = "chaty_album_id"
    const val INDEX = "chaty_album_index"
    const val COUNT = "chaty_album_count"
    val keys = listOf(VERSION, ID, INDEX, COUNT)
    fun stamp(id: String, index: Int, count: Int): Map<String, Any?> =
        mapOf(VERSION to 1, ID to id, INDEX to index, COUNT to count)
}

data class MediaTimelineRow(val messages: List<ChatMessage>) {
    val anchor: ChatMessage get() = messages.first()
}

/** Incomplete, malformed, mixed or protected batches remain ordinary individual bubbles. */
fun mediaTimelineRows(
    messages: List<ChatMessage>,
    expand: Boolean = false,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): List<MediaTimelineRow> {
    if (expand) return messages.map { MediaTimelineRow(listOf(it)) }
    fun number(m: ChatMessage, key: String): Int? = (m.metadata[key] as? Number)?.let {
        it.toInt().takeIf { n -> n.toDouble() == it.toDouble() }
    }
    fun eligible(m: ChatMessage): Boolean = m.attachment?.type == "image" &&
        m.metadata.keys.all { it in MediaAlbumContract.keys || it == "sender_name" } &&
        !m.isDeletedForEveryone && !m.isDeletedForMe && !m.isRevokedBySender &&
        m.replyToMessageId == null && m.replyToPreviewText == null && m.replyToSenderName == null && m.linkedTaskId == null && m.reactions.isEmpty() &&
        !m.isPinned && !m.isStarred && m.editedAt == null &&
        m.deliveryState !in setOf(DeliveryState.failed, DeliveryState.queued, DeliveryState.sending)
    // WhatsApp parity (Bandi, 2026-11-14): separate single-image sends with no text
    // between them group into one album bubble. Display-only: the group is rebuilt on
    // every projection, keeps each real message/attachment id, and never merges across
    // explicit album envelopes, senders, conversations, delivery states or day headers.
    fun unstamped(m: ChatMessage): Boolean = m.metadata.keys.none { it in MediaAlbumContract.keys }
    fun localRun(start: Int): Int {
        val first = messages[start]
        if (!eligible(first) || !unstamped(first)) return 0
        val day = first.createdAt.atZone(zone).toLocalDate()
        var end = start + 1
        while (end < messages.size) {
            val m = messages[end]
            if (!eligible(m) || !unstamped(m) || m.text.isNotBlank() ||
                m.senderId != first.senderId || m.conversationId != first.conversationId ||
                m.deliveryState != first.deliveryState ||
                m.createdAt.atZone(zone).toLocalDate() != day) break
            end++
        }
        return if (end - start >= 2) end - start else 0
    }
    val rows = mutableListOf<MediaTimelineRow>()
    var i = 0
    while (i < messages.size) {
        val first = messages[i]
        val count = number(first, MediaAlbumContract.COUNT) ?: 0
        val id = first.metadata[MediaAlbumContract.ID] as? String
        val candidate = if (count in 2..100) messages.drop(i).take(count) else emptyList()
        val valid = !id.isNullOrBlank() && id.length <= 128 && candidate.size == count &&
            number(first, MediaAlbumContract.INDEX) == 0 &&
            messages.count { it.conversationId == first.conversationId && it.senderId == first.senderId && it.metadata[MediaAlbumContract.ID] == id } == count &&
            candidate.map { it.id }.distinct().size == count &&
            candidate.mapNotNull { it.attachment?.id }.distinct().size == count &&
            candidate.withIndex().all { (index, m) -> eligible(m) &&
                m.conversationId == first.conversationId && m.senderId == first.senderId &&
                m.deliveryState == first.deliveryState && (index == 0 || m.text.isBlank()) &&
                number(m, MediaAlbumContract.VERSION) == 1 && m.metadata[MediaAlbumContract.ID] == id &&
                number(m, MediaAlbumContract.INDEX) == index && number(m, MediaAlbumContract.COUNT) == count }
        when {
            valid -> { rows += MediaTimelineRow(candidate); i += count }
            else -> {
                val run = localRun(i)
                if (run >= 2) { rows += MediaTimelineRow(messages.subList(i, i + run)); i += run }
                else { rows += MediaTimelineRow(listOf(first)); i += 1 }
            }
        }
    }
    return rows
}

/** Bounded 2-column rows: two square tiles; odd tail uses the full width, no hidden items. */
fun mediaAlbumColumns(count: Int): List<Int> {
    require(count in 2..100)
    return List(count / 2) { 2 } + if (count % 2 == 1) listOf(1) else emptyList()
}
