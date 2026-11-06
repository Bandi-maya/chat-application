package com.example.chat.data.services

/** Bounded protocol values only. No arbitrary text or durable peer side effects. */
internal object CallExtrasPolicy {
    val reactions = setOf("👍", "❤️", "😂", "👏", "🎉", "👋")
    const val reactionLifetimeMillis = 4000L
    const val minimumReactionIntervalMillis = 1500L
    fun permittedReaction(emoji: String) = emoji in reactions
}

data class CallReaction(val id: String, val emoji: String, val fromPeer: Boolean, val deliveredToPeer: Boolean, val expiresAtMillis: Long)
