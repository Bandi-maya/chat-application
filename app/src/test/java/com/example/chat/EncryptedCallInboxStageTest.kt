package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EncryptedCallInboxStageTest {
    private class Store : SecureKeyValueStore {
        val values = mutableMapOf<String, String>(); var fail = false
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { check(!fail); values[key] = value }
        override suspend fun delete(key: String) { values.remove(key) }
    }
    private val now = Instant.parse("2026-11-14T10:00:00Z")
    private val a = "a".repeat(64); private val b = "b".repeat(64)
    private fun event(id: String, seq: Long, kind: String) = DurableCallEvent(id, seq, "33333333-3333-4333-8333-333333333333",
        JSONObject().put("call_id", "call").put("signal_type", kind).put("sender_id", "33333333-3333-4333-8333-333333333333"), now, now.plusSeconds(60))
    @Test fun stageIsAccountScopedDeduplicatedAndTerminalSupersedesOffer() = runTest {
        val store = Store(); val stage = EncryptedCallInboxStage(store)
        val offer = event("11111111-1111-4111-8111-111111111111", 1, "offer")
        val end = event("22222222-2222-4222-8222-222222222222", 2, "end")
        stage.stage(a, listOf(offer), now); stage.stage(a, listOf(offer), now)
        assertEquals(1, stage.pending(a, now).size); assertTrue(stage.pending(b, now).isEmpty())
        stage.stage(a, listOf(end), now)
        assertEquals(listOf("end"), stage.pending(a, now).map { it.kind })
        assertTrue(stage.pending(a, now.plusSeconds(61)).isEmpty())
    }
    @Test fun persistenceFailureReturnsNoAckEligibleResult() = runTest {
        val store = Store().apply { fail = true }; val stage = EncryptedCallInboxStage(store)
        try { stage.stage(a, listOf(event("11111111-1111-4111-8111-111111111111", 1, "offer")), now); fail("Must not ACK") }
        catch (_: IllegalStateException) { }
        assertTrue(store.values.isEmpty())
    }
    @Test fun resolvedCallSurvivesRestartAndSuppressesDelayedOffer() = runTest {
        val store = Store(); val stage = EncryptedCallInboxStage(store)
        val offer = event("11111111-1111-4111-8111-111111111111", 1, "offer")
        stage.stage(a, listOf(offer), now)
        stage.resolveCall(a, "call", now)
        val restarted = EncryptedCallInboxStage(store)
        restarted.stage(a, listOf(offer), now.plusSeconds(1))
        assertTrue(restarted.pending(a, now.plusSeconds(2)).isEmpty())
        assertTrue(restarted.pending(b, now.plusSeconds(2)).isEmpty())
    }
    @Test fun failedDispositionSaveLeavesIncomingRecoverable() = runTest {
        val store = Store(); val stage = EncryptedCallInboxStage(store)
        stage.stage(a, listOf(event("11111111-1111-4111-8111-111111111111", 1, "offer")), now)
        store.fail = true
        try { stage.resolveCall(a, "call", now); fail("Must not answer after failure") }
        catch (_: IllegalStateException) { }
        assertEquals(1, EncryptedCallInboxStage(store).pending(a, now).size)
    }

    @Test fun answerDispositionNeverDropsLaterIceOrRemoteHangup() = runTest {
        val store = Store(); val stage = EncryptedCallInboxStage(store)
        stage.resolveCall(a, "call", now)
        val ice = event("22222222-2222-4222-8222-222222222222", 2, "ice")
        stage.stage(a, listOf(ice), now.plusSeconds(1))
        assertEquals(listOf("ice"), stage.pending(a, now.plusSeconds(2)).map { it.kind })
        val end = event("44444444-4444-4444-8444-444444444444", 3, "end")
        stage.stage(a, listOf(end), now.plusSeconds(3))
        assertEquals(listOf("end"), stage.pending(a, now.plusSeconds(4)).map { it.kind })
    }

    @Test fun resolutionDoesNotBlockIceRestartOfferOnAcceptedCall() = runTest {
        val stage = EncryptedCallInboxStage(Store())
        stage.resolveCall(a, "call", now)
        val restart = event("11111111-1111-4111-8111-111111111111", 2, "offer")
        restart.data.put("ice_restart", true)
        stage.stage(a, listOf(restart), now.plusSeconds(1))
        assertEquals(1, stage.pending(a, now.plusSeconds(2)).size)
    }

}
