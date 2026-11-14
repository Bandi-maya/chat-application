package com.example.chat.data.api

/** Ephemeral cadence only. Caller owns account+conversation scope; no persistence or offline replay. */
object TypingCadencePolicy {
    const val HEARTBEAT_MS = 3_000L
    const val IDLE_MS = 5_000L
    fun shouldPublish(nonempty: Boolean, alreadyPublished: Boolean, nowMs: Long, lastPublishedMs: Long): Boolean =
        nonempty && (!alreadyPublished || nowMs - lastPublishedMs >= HEARTBEAT_MS)
}
