package com.example.chat.data.chatlist

/** Gap-pass chat-list features (client, JVM-testable): pinned conversations,
 *  per-chat drafts, swipe actions, multi-account session switching. */

/** Pinned conversations float to the top, cap 3 like WhatsApp/iMessage. */
class PinnedConversationsPolicy(private val maxPins: Int = 3) {
    private val pinned = mutableListOf<String>()   // most recently pinned first

    fun pin(conversationId: String): Boolean {
        if (conversationId in pinned) return true
        if (pinned.size >= maxPins) return false
        pinned.add(0, conversationId)
        return true
    }

    fun unpin(conversationId: String): Boolean = pinned.remove(conversationId)

    fun isPinned(conversationId: String): Boolean = conversationId in pinned

    /** Pinned first (pin order preserved), then everything else in input order. */
    fun ordered(conversationIds: List<String>): List<String> =
        pinned.filter { it in conversationIds } + conversationIds.filter { it !in pinned }
}

/** Per-chat unsent drafts; cleared on send. Telegram/WhatsApp behaviour. */
class MessageDraftStore {
    private val drafts = mutableMapOf<String, String>()

    fun save(conversationId: String, text: String) {
        if (text.isBlank()) drafts.remove(conversationId) else drafts[conversationId] = text
    }

    fun draft(conversationId: String): String? = drafts[conversationId]

    fun clear(conversationId: String) = drafts.remove(conversationId)

    fun conversationsWithDrafts(): Set<String> = drafts.keys
}

/** Swipe actions on the conversation list, user-configurable per direction. */
enum class SwipeAction { NONE, ARCHIVE, PIN, MUTE, READ }

data class SwipeActionConfig(
    val right: SwipeAction = SwipeAction.ARCHIVE,
    val left: SwipeAction = SwipeAction.MUTE,
) {
    init { require(right != SwipeAction.NONE || left != SwipeAction.NONE) { "at least one swipe action" } }
}

/** Multiple accounts in one install (Telegram): sessions switch without
 *  logging the other out; each account keeps its own storage namespace. */
class MultiAccountPolicy(private val maxAccounts: Int = 3) {
    private val accounts = mutableListOf<String>()   // user ids, active first

    fun add(userId: String): Boolean {
        if (userId in accounts) return switchTo(userId)
        if (accounts.size >= maxAccounts) return false
        accounts.add(0, userId)
        return true
    }

    fun switchTo(userId: String): Boolean =
        if (userId in accounts) { accounts.remove(userId); accounts.add(0, userId); true } else false

    fun active(): String? = accounts.firstOrNull()

    fun remove(userId: String) { accounts.remove(userId) }

    /** Per-account storage namespace so messages/keys never mix. */
    fun storageNamespace(userId: String): String = "acct_$userId"
}
