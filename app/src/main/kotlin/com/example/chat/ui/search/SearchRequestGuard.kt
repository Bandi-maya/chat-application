package com.example.chat.ui.search

import com.example.chat.domain.models.Conversation

/** Exact port of lib/features/search/search_request_guard.dart.
 *  Tracks monotonically increasing search requests so asynchronous results
 *  can never replace results for a newer query. */
class SearchRequestGuard {
    private var requestId = 0

    fun begin(): Int = ++requestId

    fun isCurrent(id: Int): Boolean = id == requestId

    /** Preserves backend ordering while removing repeated canonical records. */
    fun <T> deduplicate(values: Iterable<T>, idOf: (T) -> String): List<T> {
        val seen = HashSet<String>()
        return values.filter { seen.add(idOf(it)) }
    }
}

/** The local (non-remote) half of global_search_screen.dart _queueSearch:
 *  case-insensitive title/last-message match over the conversation list with
 *  the locked-chats privacy exclusion applied first. Pure so it is
 *  JVM-testable; the screen owns debounce + the remote user search. */
object LocalConversationSearch {
    fun filter(
        conversations: List<Conversation>,
        query: String,
        isHidden: (String) -> Boolean,
    ): List<Conversation> {
        if (query.isEmpty()) return emptyList()
        val lower = query.lowercase()
        return conversations.filter { conversation ->
            // Privacy rule: Exclude hidden locked chats from global search
            if (isHidden(conversation.id)) return@filter false
            conversation.title.lowercase().contains(lower) ||
                conversation.lastMessageText.lowercase().contains(lower)
        }
    }
}
