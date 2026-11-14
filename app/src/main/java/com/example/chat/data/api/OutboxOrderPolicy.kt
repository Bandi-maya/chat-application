package com.example.chat.data.api

/** Preserve FIFO within a conversation; a blocked head must not stall unrelated conversations. */
object OutboxOrderPolicy {
    fun <T> next(rows: List<T>, conversation: (T) -> String, eligible: (T) -> Boolean): T? {
        val seen = mutableSetOf<String>()
        return rows.firstOrNull { seen.add(conversation(it)) && eligible(it) }
    }
}
