package com.example.chat

import com.example.chat.data.services.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RetainedCallConnectionRegistryTest {
    private class Peer(override val accountHash: String, override val callId: String, val timeline: MutableList<String>) : RetainedCallConnection {
        var valid = true; var canHold = true; var accepted = true; var openMic = true
        override fun credentialsStillValid() = valid
        override fun isConnected() = true
        override suspend fun holdMedia(): Boolean { timeline += "hold:$callId"; if (canHold) openMic = false; return canHold }
        override suspend fun resumeMedia(): Boolean { timeline += "resume:$callId"; openMic = true; return true }
        override suspend fun acceptIncoming(): Boolean { timeline += "accept:$callId"; openMic = accepted; return accepted }
        override suspend fun end(reason: String) { timeline += "end:$callId"; openMic = false }
    }
    @Test fun holdCompletesBeforeSecondAnswerAndResumeIsExplicit() = runTest {
        val events = mutableListOf<String>(); val a = Peer("a", "1", events); val b = Peer("b", "2", events)
        val registry = RetainedCallConnectionRegistry()
        registry.registerForeground(a)
        assertEquals(RetainedCallConnectionRegistry.Result.APPLIED, registry.holdAndAnswer(b))
        assertEquals(listOf("hold:1", "accept:2"), events); assertFalse(a.openMic); assertTrue(b.openMic)
        registry.endForeground("2")
        assertFalse(a.openMic); assertNull(registry.view().foregroundCallId); assertEquals("1", registry.view().heldCallId)
        registry.resumeHeldByUser("1"); assertTrue(a.openMic)
    }
    @Test fun failedAnswerKeepsPriorCallHeldAndNeverAutoUnmutes() = runTest {
        val events = mutableListOf<String>(); val a = Peer("a", "1", events); val b = Peer("b", "2", events).apply { accepted = false }
        val registry = RetainedCallConnectionRegistry(); registry.registerForeground(a)
        assertEquals(RetainedCallConnectionRegistry.Result.ANSWER_FAILED, registry.holdAndAnswer(b))
        assertFalse(a.openMic); assertNull(registry.view().foregroundCallId); assertEquals("1", registry.view().heldCallId)
    }
}
