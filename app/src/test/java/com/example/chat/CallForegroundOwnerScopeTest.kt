package com.example.chat

import com.example.chat.data.services.CallForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression matrix for owner-scoped stop authority (run 118 hang-up evidence:
 * a stale couriered stop killed a newer test's service; review: stop authority
 * must originate from the caller/session being disposed, never a current
 * process-global read). Pure decisions only - no instrumentation timing.
 */
class CallForegroundOwnerScopeTest {

    @Test fun oldTokenStopAfterNewTokenForegroundIsIgnored() {
        assertEquals(
            CallForegroundService.StopDisposition.IGNORE,
            CallForegroundService.stopDisposition("callfg-new:call-2", "callfg-old:call-1"),
        )
    }

    @Test fun sameCallIdUnderDifferentOwnerTokenIsIgnored() {
        // Locator/activity recreation can serve the same callId under a newer
        // wiring token: the old generation's stop must not kill it.
        assertEquals(
            CallForegroundService.StopDisposition.IGNORE,
            CallForegroundService.stopDisposition("tokB:call-1", "tokA:call-1"),
        )
    }

    @Test fun servingOwnersOwnStopIsAccepted() {
        // The real hang-up path: the current session's own teardown stops
        // promptly.
        assertEquals(
            CallForegroundService.StopDisposition.ACCEPT,
            CallForegroundService.stopDisposition("tokA:call-1", "tokA:call-1"),
        )
    }

    @Test fun noServingSessionIsPendingRequirementCleanup() {
        assertEquals(
            CallForegroundService.StopDisposition.CLEANUP,
            CallForegroundService.stopDisposition(null, "tokA:call-1"),
        )
        assertEquals(
            CallForegroundService.StopDisposition.CLEANUP,
            CallForegroundService.stopDisposition(null, null),
        )
    }

    @Test fun untokenedStopNeverMatchesAServingSession() {
        assertEquals(
            CallForegroundService.StopDisposition.IGNORE,
            CallForegroundService.stopDisposition("tokA:call-1", null),
        )
    }

    @Test fun staleOwnerCannotDiscardAnotherOwnersQueuedStart() {
        assertFalse(CallForegroundService.shouldDiscardPendingStart("tok:call-1", "tok:call-2"))
        assertFalse(CallForegroundService.shouldDiscardPendingStart(null, "tok:call-1"))
        assertFalse(CallForegroundService.shouldDiscardPendingStart("tok:call-1", null))
        assertTrue(CallForegroundService.shouldDiscardPendingStart("tok:call-1", "tok:call-1"))
    }
}
