package com.example.chat

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.PresenceEvent
import com.example.chat.ui.chats.detail.PresenceEventType
import com.example.chat.ui.chats.detail.PresenceFeedStore
import com.example.chat.ui.chats.detail.PresenceToastPolicy
import com.example.chat.ui.chats.detail.ReadAlertController
import com.example.chat.ui.chats.detail.ReadAlertPolicy
import com.example.chat.ui.chats.detail.ReadAlertStore
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReadAlertPolicyTest {
    private fun msg(id: String, state: DeliveryState = DeliveryState.delivered, sender: String = "me") =
        ChatMessage(id = id, conversationId = "c", senderId = sender, type = MessageType.text, text = "hi", createdAt = Instant.EPOCH, deliveryState = state)

    private class MemStore : ReadAlertStore {
        val data = mutableMapOf<String, Set<String>>()
        val fired = mutableMapOf<String, Set<String>>()
        var commitOk = true
        override fun load(conversationId: String) = data[conversationId].orEmpty()
        override fun loadFired(conversationId: String) = fired[conversationId].orEmpty()
        override fun saveAll(conversationId: String, watched: Set<String>, fired: Set<String>): Boolean {
            if (!commitOk) return false
            data[conversationId] = watched
            this.fired[conversationId] = fired
            return true
        }
    }

    @Test fun canWatchOnlyOwnLiveUnreadDirectMessages() {
        assertTrue(ReadAlertPolicy.canWatch(msg("a"), "me", isDirect = true))
        assertFalse(ReadAlertPolicy.canWatch(msg("a"), "me", isDirect = false)) // groups later
        assertFalse(ReadAlertPolicy.canWatch(msg("a", sender = "peer"), "me", isDirect = true))
        assertFalse(ReadAlertPolicy.canWatch(msg("a", DeliveryState.read), "me", isDirect = true))
        assertFalse(ReadAlertPolicy.canWatch(msg("a", DeliveryState.failed), "me", isDirect = true))
        assertFalse(ReadAlertPolicy.canWatch(msg("a").copy(isRevokedBySender = true), "me", isDirect = true))
        assertFalse(ReadAlertPolicy.canWatch(msg("a").copy(isDeletedForMe = true), "me", isDirect = true))
    }

    @Test fun firesOnceWhenMessageBecomesRead() {
        val alerts = mutableListOf<String>()
        val controller = ReadAlertController(MemStore(), "c") { alerts += it.id }
        controller.watch("a")
        controller.onMessages(listOf(msg("a")))
        assertEquals(setOf("a"), controller.watched)
        controller.onMessages(listOf(msg("a", DeliveryState.read)))
        assertEquals(listOf("a"), alerts)
        assertTrue(controller.watched.isEmpty())
        controller.onMessages(listOf(msg("a", DeliveryState.read)))
        assertEquals(listOf("a"), alerts) // never twice
    }

    @Test fun failedCommitDoesNotFireAndRetriesNextUpdate() {
        val store = MemStore()
        val alerts = mutableListOf<String>()
        val controller = ReadAlertController(store, "c") { alerts += it.id }
        controller.watch("a")
        controller.onMessages(listOf(msg("a")))
        store.commitOk = false
        controller.onMessages(listOf(msg("a", DeliveryState.read)))
        assertTrue(alerts.isEmpty()) // no durable state, no alert
        store.commitOk = true
        controller.onMessages(listOf(msg("a", DeliveryState.read)))
        assertEquals(listOf("a"), alerts) // retried once, fired once
        controller.onMessages(listOf(msg("a", DeliveryState.read)))
        assertEquals(listOf("a"), alerts)
    }

    @Test fun pagedOutOrAbsentMessageKeepsItsWatch() {
        val controller = ReadAlertController(MemStore(), "c") {}
        controller.watch("a")
        controller.onMessages(listOf(msg("a")))
        controller.onMessages(listOf(msg("other"))) // "a" paged out
        assertEquals(setOf("a"), controller.watched) // absence is not terminal
        controller.onMessages(emptyList()) // empty initial page
        assertEquals(setOf("a"), controller.watched)
    }

    @Test fun deletedForMeAlsoBlocksTheAlert() {
        val alerts = mutableListOf<String>()
        val controller = ReadAlertController(MemStore(), "c") { alerts += it.id }
        controller.watch("a")
        controller.onMessages(listOf(msg("a")))
        // is_read and delete-for-me arriving together must not alert.
        controller.onMessages(listOf(msg("a", DeliveryState.read).copy(isDeletedForMe = true)))
        assertTrue(alerts.isEmpty())
        assertTrue(controller.watched.isEmpty()) // terminal: watch dropped
    }

    @Test fun firedLogSurvivesRestartAndBlocksRefire() {
        val store = MemStore()
        val alerts = mutableListOf<String>()
        val first = ReadAlertController(store, "c") { alerts += "a" }
        first.watch("a")
        first.onMessages(listOf(msg("a")))
        first.onMessages(listOf(msg("a", DeliveryState.read)))
        assertEquals(1, alerts.size)
        // Simulate process death between persisting and any later observation:
        val second = ReadAlertController(store, "c") { alerts += "a2" }
        second.watch("a") // pathological re-watch
        second.onMessages(listOf(msg("a", DeliveryState.read)))
        assertEquals(1, alerts.size) // durable at-most-once
    }

    @Test fun revokeOrDeleteCancelsSilently() {
        val alerts = mutableListOf<String>()
        val controller = ReadAlertController(MemStore(), "c") { alerts += it.id }
        controller.watch("a")
        controller.onMessages(listOf(msg("a")))
        controller.onMessages(listOf(msg("a").copy(isDeletedForEveryone = true)))
        assertTrue(controller.watched.isEmpty())
        controller.onMessages(listOf(msg("a", DeliveryState.read)))
        assertTrue(alerts.isEmpty())
    }

    @Test fun staysSilentWhenPeerNeverSharesReceipt() {
        val alerts = mutableListOf<String>()
        val controller = ReadAlertController(MemStore(), "c") { alerts += it.id }
        controller.watch("a")
        repeat(3) { controller.onMessages(listOf(msg("a"))) } // delivered forever
        assertTrue(alerts.isEmpty())
        assertEquals(setOf("a"), controller.watched)
    }
}

class PresenceFeedTest {
    private fun event(type: PresenceEventType, at: Instant = Instant.EPOCH) =
        PresenceEvent("c", "Aisha", type, at)

    @Test fun toastTextsFollowDisplayRule() {
        assertEquals("Aisha is online", PresenceToastPolicy.text(event(PresenceEventType.online)))
        assertEquals("Aisha is typing...", PresenceToastPolicy.text(event(PresenceEventType.typing)))
        assertNull(PresenceToastPolicy.text(event(PresenceEventType.offline))) // departures never toast
        // Gated until the server's recording event is confirmed:
        assertNull(PresenceToastPolicy.text(event(PresenceEventType.recording)))
        assertFalse(PresenceToastPolicy.RECORDING_EVENT_CONFIRMED)
    }

    @Test fun feedIsNewestFirstBoundedDeduplicatedAndObservable() {
        val store = PresenceFeedStore(capacity = 3)
        store.record(event(PresenceEventType.online, Instant.EPOCH))
        store.record(event(PresenceEventType.online, Instant.EPOCH.plusSeconds(10))) // dup within 30s
        assertEquals(1, store.events.value.size)
        store.record(event(PresenceEventType.typing, Instant.EPOCH.plusSeconds(40)))
        store.record(event(PresenceEventType.online, Instant.EPOCH.plusSeconds(80)))
        store.record(event(PresenceEventType.online, Instant.EPOCH.plusSeconds(200)))
        assertEquals(3, store.events.value.size) // bounded
        assertEquals(Instant.EPOCH.plusSeconds(200), store.events.value.first().at) // newest first
    }
}
