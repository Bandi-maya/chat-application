package com.example.chat

import com.example.chat.data.services.CallForegroundService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression matrix for owner-scoped stop authority (run 118 hang-up evidence:
 * a stale couriered stop killed a newer test's service; review: stop authority
 * must originate from the caller/session being disposed, never a current
 * process-global read). Pure decisions only - no instrumentation timing.
 *
 * Effective-source adaptation: the StopDisposition/stopDisposition/
 * shouldDiscardPendingStart API exists only in the shadowed override copy; the
 * effective branch service implements the same stale-stop protection through
 * its owner-aware stop(context, ownerToken, nextOwnerToken), whose decision is
 * extracted as the pure [CallForegroundService.ownerStopMayProceed] seam that
 * the production stop path actually consults. The assertions below test that
 * real consulted behavior.
 *
 * UNRESOLVED COVERAGE GAPS (no effective-source implementation to assert
 * against; NOT covered here rather than fabricated):
 * - Pending-start discard protection (override-only shouldDiscardPendingStart
 *   machinery): the branch design has no queued/pending start record, so the
 *   stale-owner-cannot-discard-a-queued-start scenario has no production
 *   behavior to test.
 * - Couriered CLEANUP disposition for pending requirements (FGS start-race
 *   cleanup): the branch design has no stop-courier protocol, so there is no
 *   CLEANUP-equivalent path; with nothing serving, a tokened stop is ignored
 *   and an untokened stop is a harmless no-op.
 */
class CallForegroundOwnerScopeTest {

    @Test fun oldTokenStopAfterNewTokenForegroundIsIgnored() {
        assertFalse(
            CallForegroundService.ownerStopMayProceed("callfg-new:call-2", "callfg-old:call-1"),
        )
    }

    @Test fun sameCallIdUnderDifferentOwnerTokenIsIgnored() {
        // Locator/activity recreation can serve the same callId under a newer
        // wiring token: the old generation's stop must not kill it.
        assertFalse(
            CallForegroundService.ownerStopMayProceed("tokB:call-1", "tokA:call-1"),
        )
    }

    @Test fun servingOwnersOwnStopIsAccepted() {
        // The real hang-up path: the current session's own teardown stops
        // promptly.
        assertTrue(
            CallForegroundService.ownerStopMayProceed("tokA:call-1", "tokA:call-1"),
        )
    }

    @Test fun untokenedStopNeverMatchesAServingSession() {
        assertFalse(
            CallForegroundService.ownerStopMayProceed("tokA:call-1", null),
        )
    }

    @Test fun untokenedStopWithNothingServingProceeds() {
        // Real branch behavior: with no serving session and no token on either
        // side the stop proceeds (harmless no-op against a stopped service).
        // This does NOT cover the override design's couriered pending-
        // requirement CLEANUP scenario; see the class-level gap note.
        assertTrue(
            CallForegroundService.ownerStopMayProceed(null, null),
        )
        // A tokened stop when nothing is serving is ignored, never treated as
        // authority to tear down a future session.
        assertFalse(
            CallForegroundService.ownerStopMayProceed(null, "tokA:call-1"),
        )
    }
}
