package com.example.chat.ui.chats.detail

/** Monotonic active-time accounting. Paused time never satisfies the minimum-send duration. */
internal class ActiveVoiceClock(private val now: () -> Long) {
    private var activeSince: Long? = null
    private var accumulated = 0L
    private var started = false
    val elapsedMs: Long get() = accumulated + (activeSince?.let { (now() - it).coerceAtLeast(0) } ?: 0L)
    val paused: Boolean get() = started && activeSince == null
    fun start() { accumulated = 0L; started = true; activeSince = now() }
    fun pause() {
        if (!started || activeSince == null) return
        accumulated = elapsedMs
        activeSince = null
    }
    fun resume() { if (paused) activeSince = now() }
    fun reset() { started = false; accumulated = 0L; activeSince = null }
}
