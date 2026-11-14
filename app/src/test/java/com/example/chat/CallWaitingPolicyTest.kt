package com.example.chat

import com.example.chat.data.services.CallWaitingPolicy as P
import org.junit.Assert.*
import org.junit.Test

class CallWaitingPolicyTest {
    private val a = P.CallRef("account-a", "call-a", true)
    private val b = P.CallRef("account-b", "call-b", true)
    private val c = P.CallRef("account-a", "call-c", false)
    @Test fun secondAccountOfferWaitsWithoutReplacingCurrentCall() {
        val state = P.State(foreground = a)
        assertEquals(P.Admission.SHOW_WAITING, P.admit(state, P.Offer(b, 2000), 1000))
        assertEquals(a, state.foreground)
        assertEquals(P.Admission.REJECT_BUSY, P.admit(state.copy(waiting = b), P.Offer(c, 2000), 1000))
    }
    @Test fun holdNeedsIndependentRetainedMediaNotJustMuteFlag() {
        val state = P.State(foreground = a, waiting = b)
        assertFalse(P.choices(state, false, true).contains(P.Choice.HOLD_AND_ANSWER))
        assertNull(P.holdAndAnswerPrepared(state, false, true))
        val held = P.holdAndAnswerPrepared(state, true, true)!!
        assertEquals(a, held.held); assertEquals(b, held.foreground)
        val failed = P.secondCallFailed(held)
        assertNull(failed.foreground); assertEquals(a, failed.held)
        assertEquals(a, P.resumeHeldByUser(failed, true)!!.foreground)
    }
    @Test fun duplicateAndExpiredOfferCannotRingAgain() {
        assertEquals(P.Admission.IGNORE_REPLAY, P.admit(P.State(foreground = a), P.Offer(a, 2000), 1000))
        assertEquals(P.Admission.DROP_EXPIRED, P.admit(P.State(), P.Offer(b, 1000), 1000))
    }
}
