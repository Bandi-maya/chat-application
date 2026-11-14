package com.example.chat.ui.chats.detail

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Presence events the server delivered, newest first, for the notifications screen. */
enum class PresenceEventType { online, offline, typing, recording }

data class PresenceEvent(
    val conversationId: String,
    val displayName: String,
    val type: PresenceEventType,
    val at: Instant,
)

/**
 * Bounded newest-first feed for ONE account. Observable: the notifications screen
 * collects [events]. In-memory by design - presence is ephemeral in every compared
 * app; only the current process's events are shown.
 */
class PresenceFeedStore(private val capacity: Int = 100) {
    private val eventsInner = MutableStateFlow<List<PresenceEvent>>(emptyList())
    val events: StateFlow<List<PresenceEvent>> = eventsInner

    fun record(event: PresenceEvent) {
        val current = eventsInner.value
        // Collapse immediate duplicates (same peer + type within 30s) so flapping
        // presence does not flood the feed.
        val last = current.firstOrNull()
        if (last != null && last.conversationId == event.conversationId && last.type == event.type &&
            kotlin.math.abs(event.at.epochSecond - last.at.epochSecond) < 30) return
        eventsInner.value = (listOf(event) + current).take(capacity)
    }
}

/** Account-scoped feeds: an account switch never shows another account's events. */
object PresenceFeed {
    private val stores = mutableMapOf<String, PresenceFeedStore>()

    @Synchronized
    fun forAccount(accountKey: String): PresenceFeedStore =
        stores.getOrPut(accountKey) { PresenceFeedStore() }
}

object PresenceToastPolicy {
    /**
     * The recording policy enum exists (PrivacyPublication.recording) but no inbound
     * recording consumer is verified in the codebase. Until the server's recording
     * event is confirmed, recording toasts stay gated OFF (Bandi's sequence: recording
     * only after the event is confirmed).
     */
    const val RECORDING_EVENT_CONFIRMED = false

    /**
     * Display rule: toast only events the server actually delivered (the peer's
     * privacy settings govern what is published). Departures never toast.
     */
    fun text(event: PresenceEvent): String? = when (event.type) {
        PresenceEventType.online -> "${event.displayName} is online"
        PresenceEventType.offline -> null
        PresenceEventType.typing -> "${event.displayName} is typing..."
        PresenceEventType.recording ->
            if (RECORDING_EVENT_CONFIRMED) "${event.displayName} is recording a voice note..." else null
    }
}
