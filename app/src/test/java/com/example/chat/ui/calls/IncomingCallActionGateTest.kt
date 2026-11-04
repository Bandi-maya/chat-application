package com.example.chat.ui.calls

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IncomingCallActionGateTest {
    @Test fun rapidTapsAdmitOnlyOneAction() {
        val gate = IncomingCallActionGate()
        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        assertTrue(gate.pending)
    }
    @Test fun failureAllowsExplicitRetry() = runBlocking {
        val gate = IncomingCallActionGate()
        assertTrue(gate.tryEnter())
        try { assertFalse(runCallAction({}) { throw SecurityException("permission denied") }) }
        finally { gate.complete() }
        assertFalse(gate.pending)
        assertTrue(gate.tryEnter())
    }
    @Test fun completedActionDoesNotLockNextAttempt() {
        val gate = IncomingCallActionGate()
        assertTrue(gate.tryEnter()); gate.complete(); gate.complete()
        assertTrue(gate.tryEnter())
    }
}
