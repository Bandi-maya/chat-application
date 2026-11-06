package com.example.chat

import com.example.chat.core.messages.ViewOnceOpenController
import com.example.chat.core.messages.ViewOnceOpenController.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Pins the view-once open ordering: consumed guard, durable write before
 *  reveal, in-flight gate, fail-closed write failure AND storage exceptions
 *  (cancellation propagates), and identity revalidation around the
 *  suspension — including a controller that survives an account switch to a
 *  newly valid session, and live auth revocation with an unchanged tuple.
 *  Pure JVM. */
class ViewOnceOpenControllerTest {

    private val sessionA = Triple("acc1", "sess1", "conv1")
    private val sessionB = Triple("acc2", "sess2", "conv2")

    private fun controller(
        current: () -> Any? = { sessionA },
        valid: () -> Boolean = { true },
        consumed: (String) -> Boolean = { false },
        persist: suspend (String) -> Boolean = { true },
    ) = ViewOnceOpenController(sessionA, current, valid, consumed, persist)

    @Test fun mediaRevealsOnlyAfterSuccessfulDurableWrite() = runBlocking {
        val order = mutableListOf<String>()
        val c = controller(persist = { id -> order.add("persist:$id"); true })
        val result = c.open("m1", isVoice = false)
        order.add("reveal")
        assertEquals(Result.RevealMedia("m1"), result)
        assertEquals(listOf("persist:m1", "reveal"), order)
        assertFalse(c.openInFlight)
    }

    @Test fun failedWriteRevealsNothing() = runBlocking {
        val c = controller(persist = { false })
        assertEquals(Result.ConsumeFailed("m1"), c.open("m1", isVoice = false))
        assertFalse(c.openInFlight)
    }

    @Test fun storageExceptionBecomesConsumeFailed() = runBlocking {
        val c = controller(persist = { throw RuntimeException("disk full") })
        assertEquals(Result.ConsumeFailed("m1"), c.open("m1", isVoice = false))
        assertFalse(c.openInFlight)
    }

    @Test fun cancellationFromStoragePropagates() = runBlocking {
        val c = controller(persist = { throw CancellationException("scope gone") })
        try {
            c.open("m1", isVoice = false)
            fail("CancellationException must propagate")
        } catch (expected: CancellationException) {
            // propagated, not swallowed into ConsumeFailed
        }
        assertFalse(c.openInFlight)
    }

    @Test fun alreadyConsumedIdDoesNotReopenOrWrite() = runBlocking {
        var writes = 0
        val c = controller(consumed = { it == "m1" }, persist = { writes++; true })
        assertEquals(Result.AlreadyConsumed("m1"), c.open("m1", isVoice = false))
        assertEquals(0, writes)
    }

    @Test fun duplicateTapAfterFirstWriteCompletesDoesNotReopen() = runBlocking {
        // The race from the review: tap 1's write has landed but the UI has
        // not yet swapped the locked card when tap 2 arrives.
        val persisted = mutableSetOf<String>()
        val c = controller(consumed = { it in persisted }, persist = { persisted.add(it); true })
        assertEquals(Result.RevealMedia("m1"), c.open("m1", isVoice = false))
        assertEquals(Result.AlreadyConsumed("m1"), c.open("m1", isVoice = false))
        assertEquals(setOf("m1"), persisted) // exactly one write
    }

    @Test fun consumedReadFailureBlocksOpenWithStorageError() = runBlocking {
        var writes = 0
        val c = controller(
            consumed = { throw RuntimeException("prefs unreadable") },
            persist = { writes++; true },
        )
        // Fail-closed: ConsumeFailed (storage error UI), NOT treated as an
        // empty set, and no write attempted.
        assertEquals(Result.ConsumeFailed("m1"), c.open("m1", isVoice = false))
        assertEquals(0, writes)
        assertFalse(c.openInFlight)
    }

    @Test fun consumedReadCancellationPropagates() = runBlocking {
        val c = controller(consumed = { throw CancellationException("gone") })
        try {
            c.open("m1", isVoice = false)
            fail("CancellationException must propagate")
        } catch (expected: CancellationException) {
            // propagated
        }
        assertFalse(c.openInFlight)
    }

    @Test fun rapidSecondTapIsRejectedWhileInFlight() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val c = controller(persist = { entered.complete(Unit); release.await(); true })
        val first = async { c.open("m1", isVoice = false) }
        entered.await()
        assertTrue(c.openInFlight)
        assertEquals(Result.Busy("m2"), c.open("m2", isVoice = false))
        release.complete(Unit)
        assertEquals(Result.RevealMedia("m1"), first.await())
        assertFalse(c.openInFlight)
    }

    @Test fun identityChangeDuringWriteDropsReveal() = runBlocking {
        var current: Any = sessionA
        val c = controller(current = { current }, persist = { current = sessionB; true })
        assertEquals(Result.StaleSession("m1"), c.open("m1", isVoice = false))
        assertFalse(c.openInFlight)
    }

    @Test fun oldControllerSurvivingSwitchToNewlyValidSessionDropsReveal() = runBlocking {
        // The account switches to a different, fully valid session. The old
        // controller (created for sessionA) must still refuse: comparing a
        // "is the session valid" Boolean would pass here; comparing the
        // captured identity tuple does not.
        var current: Any = sessionA
        val old = controller(current = { current })
        current = sessionB // new controller for sessionB would be valid now
        assertEquals(Result.StaleSession("m1"), old.open("m1", isVoice = false))
    }

    @Test fun liveAuthRevocationDropsRevealEvenWithMatchingTuple() = runBlocking {
        // Tuple unchanged on screen, but the original session's own checker
        // (captured at creation) reports revoked.
        val c = controller(valid = { false })
        assertEquals(Result.StaleSession("m1"), c.open("m1", isVoice = false))
    }

    @Test fun initiallyStaleSessionSkipsTheWrite() = runBlocking {
        var writes = 0
        val c = controller(current = { sessionB }, persist = { writes++; true })
        assertEquals(Result.StaleSession("m1"), c.open("m1", isVoice = false))
        assertEquals(0, writes) // never marked consumed on a stale session
    }

    @Test fun voiceRevealsWithoutDurableWrite() = runBlocking {
        var writes = 0
        val c = controller(persist = { writes++; true })
        assertEquals(Result.RevealVoice("v1"), c.open("v1", isVoice = true))
        assertEquals(0, writes)
    }

    @Test fun voiceWithMismatchedIdentityIsRejected() = runBlocking {
        val c = controller(current = { sessionB })
        assertEquals(Result.StaleSession("v1"), c.open("v1", isVoice = true))
    }

    @Test fun playbackConsumeIsBestEffortAndReportsResult() = runBlocking {
        assertTrue(controller().consumeAfterPlayback("v1"))
        assertFalse(controller(persist = { false }).consumeAfterPlayback("v1"))
        assertFalse(controller(persist = { throw RuntimeException("io") }).consumeAfterPlayback("v1"))
    }

    @Test fun playbackConsumeCancellationPropagates() = runBlocking {
        val c = controller(persist = { throw CancellationException("gone") })
        try {
            c.consumeAfterPlayback("v1")
            fail("CancellationException must propagate")
        } catch (expected: CancellationException) {
            // propagated
        }
    }
}
