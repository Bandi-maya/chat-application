package com.example.chat.core.messages

import com.example.chat.domain.models.MessageReaction

object ReactionUpdatePolicy {
    /** One choice per user; clicking their current choice removes it. */
    fun toggle(current: List<MessageReaction>, userId: String, emoji: String): List<MessageReaction> {
        val remove = current.any { it.emoji == emoji && userId in it.userIds }
        val byEmoji = linkedMapOf<String, LinkedHashSet<String>>()
        current.forEach { r -> byEmoji.getOrPut(r.emoji) { linkedSetOf() }.addAll(r.userIds.filter { it != userId }) }
        if (!remove) byEmoji.getOrPut(emoji) { linkedSetOf() }.add(userId)
        return byEmoji.filterValues { it.isNotEmpty() }.map { (key, ids) -> MessageReaction(key, ids.toList()) }
    }
}
