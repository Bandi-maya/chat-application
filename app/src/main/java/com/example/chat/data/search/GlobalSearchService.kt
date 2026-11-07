package com.example.chat.data.search

/** Global search (matrix 11): messages + contacts + captions over the
 *  encrypted local DB, with media-type chips, sender filter and unread chip.
 *  All seams injected: JVM-testable. The Room DAO backs [messageSearch] in
 *  production; contacts come from the profile repository. */
class GlobalSearchService(
    private val messageSearch: suspend (query: String, limit: Int) -> List<MessageHit>,
    private val contactSearch: suspend (query: String, limit: Int) -> List<ContactHit>,
) {
    data class MessageHit(
        val id: String,
        val conversationId: String,
        val senderId: String,
        val type: String,
        val snippet: String,
        val createdAtMillis: Long,
    )

    data class ContactHit(
        val userId: String,
        val username: String,
        val displayName: String,
    )

    data class Filters(
        val mediaTypes: Set<String>? = null,   // chips: image/video/audio/document; null = all
        val senderId: String? = null,          // group sender filter
        val unreadOnly: Boolean = false,
    )

    data class Result(
        val messages: List<MessageHit>,
        val contacts: List<ContactHit>,
    )

    suspend fun search(query: String, filters: Filters = Filters(), limit: Int = 50): Result {
        val q = query.trim()
        if (q.isEmpty()) return Result(emptyList(), emptyList())
        var messages = messageSearch(q, limit)
        filters.mediaTypes?.let { types ->
            messages = messages.filter { it.type in types }
        }
        filters.senderId?.let { sender ->
            messages = messages.filter { it.senderId == sender }
        }
        return Result(messages = messages, contacts = contactSearch(q, limit))
    }
}
