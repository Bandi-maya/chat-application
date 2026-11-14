package com.example.chat.data.api

/** Unread mutation for a newly admitted realtime message, before rows and notifications read it. */
object ApiUnreadPolicy {
    fun afterRealtime(
        previous: Int,
        isNewMessage: Boolean,
        fromSelf: Boolean,
        conversationVisible: Boolean,
        isContentMessage: Boolean,
    ): Int {
        val current = previous.coerceAtLeast(0)
        if (!isNewMessage || fromSelf || !isContentMessage) return current
        if (conversationVisible) return 0
        return if (current == Int.MAX_VALUE) current else current + 1
    }
}
