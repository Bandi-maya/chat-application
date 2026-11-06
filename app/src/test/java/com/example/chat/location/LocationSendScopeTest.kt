package com.example.chat.location

import com.example.chat.ui.chats.location.*
import kotlin.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocationSendScopeTest {
    private fun run(block: suspend () -> Unit): Throwable? {
        var failure: Throwable? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { failure = result.exceptionOrNull() }
        })
        return failure
    }
    private fun confirmed() = LocationSendGate().apply {
        select(LocationDraft(LocationCoordinate(12.0, 78.0), LocationSource.manualPin)); confirmSend()
    }
    @Test fun scopeRejectsWrongAccountAndConversation() {
        val scope = LocationSendScope("a", "c")
        assertTrue(runCatching { scope.requireCurrent("b", "c") }.isFailure)
        assertTrue(runCatching { scope.requireCurrent("a", "d") }.isFailure)
        assertTrue(runCatching { scope.requireCurrent(null, "c") }.isFailure)
    }
    @Test fun sendsOnlyOneConfirmedDraft() {
        var calls = 0
        val sender = ConfirmedLocationSender(LocationSendScope("a", "c"), { "a" to "c" }) { calls++ }
        val gate = confirmed()
        assertNull(run { sender.submit(gate) }); assertEquals(1, calls)
        assertNotNull(run { sender.submit(gate) }); assertEquals(1, calls)
    }
    @Test fun changedScopeNeverCallsTransport() {
        var calls = 0
        val sender = ConfirmedLocationSender(LocationSendScope("a", "c"), { "b" to "c" }) { calls++ }
        assertNotNull(run { sender.submit(confirmed()) }); assertEquals(0, calls)
    }
    @Test fun unconfirmedDraftNeverCallsTransport() {
        var calls = 0
        val sender = ConfirmedLocationSender(LocationSendScope("a", "c"), { "a" to "c" }) { calls++ }
        assertNotNull(run { sender.submit(LocationSendGate()) }); assertEquals(0, calls)
    }
    @Test fun uncertainFailureDoesNotPermitDuplicate() {
        var calls = 0
        val sender = ConfirmedLocationSender(LocationSendScope("a", "c"), { "a" to "c" }) { calls++; error("Uncertain transport result") }
        assertNotNull(run { sender.submit(confirmed()) })
        assertNotNull(run { sender.submit(confirmed()) }); assertEquals(1, calls)
    }
}
