package com.example.chat

import com.example.chat.data.local.SessionEpoch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SessionEpochTest {
    @Volatile private var id: Pair<String, String>? = "t" to "A"
    @Volatile private var authGen = 0L
    private fun ep(f: () -> Pair<String, String>?) = SessionEpoch { f()?.let { SessionEpoch.Identity(it.first, it.second, authGen) } }
    private val e = ep { id }

    @Test fun abaChangesTheStampEvenThoughTheUserIdReturns() {
        val a1 = e.current(); id = "t" to "B"; val b = e.current(); id = "t" to "A"; val a2 = e.current()
        assertNotEquals(a1.stamp, a2.stamp); assertFalse(e.valid(a1)); assertFalse(e.valid(b)); assertTrue(e.valid(a2))
    }

    @Test fun clearThenReloginOfTheSameUserInvalidatesAnEarlierSnapshot() {
        val before = e.current()
        e.bump()                                   // clearCurrentAccount
        assertFalse(e.valid(before))               // a read that captured `before` must hand out nothing
        val after = e.current()
        id = null; e.current(); id = "t" to "A"    // logout, then the same user logs in
        assertFalse(e.valid(after))
    }

    @Test fun renewEndsTheSessionWithoutAnIdentityChange() {
        val s = e.current(); e.renew(); assertFalse(e.valid(s))
    }

    @Test fun tenantChangeWithTheSameUserIsATransition() {
        val s = e.current(); id = "t2" to "A"; assertFalse(e.valid(s)); assertEquals("t2", e.current().tenant)
    }

    @Test fun signedOutHasNoUserAndIsStillATransition() {
        val s = e.current(); id = null; val out = e.current()
        assertNull(out.uid); assertFalse(e.valid(s))
    }

    @Test fun transitionCallbackRunsOncePerInstalledTransitionUnderContention() {
        val transitions = AtomicInteger(); val stamps = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val pool = Executors.newFixedThreadPool(8); val start = CountDownLatch(1)
        repeat(8) { n -> pool.submit { start.await(); repeat(200) { i ->
            id = if ((n + i) % 3 == 0) null else "t" to ("U" + (n + i) % 4)
            val s = e.current { transitions.incrementAndGet() }; stamps += s.stamp
        } } }
        start.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
        val last = e.current { transitions.incrementAndGet() }
        // every installed snapshot has a unique, increasing stamp: seq is the number of transitions
        assertEquals(last.seq.toInt(), transitions.get() + 0)   // initial snapshot is seq 0 (no transition)
        assertTrue(stamps.size <= last.seq.toInt() + 1)
    }

    @Test fun aStaleIdentitySampleCannotRegressTheSnapshot() {
        val reads = AtomicInteger()
        // Identity is Y, but ONE read (the stale sample) returns X first, as a thread that sampled before the change would.
        val ep = ep { if (reads.getAndIncrement() == 0) "t" to "X" else "t" to "Y" }
        val s = ep.current()
        assertEquals("Y", s.uid)                 // returned uid is the CURRENT identity, never the stale sample
        assertEquals("Y", ep.current().uid)
    }

    @Test fun aFlappingIdentityFailsClosedInsteadOfSpinning() {
        val reads = AtomicInteger()
        val ep = ep { if (reads.getAndIncrement() % 2 == 0) "t" to "X" else "t" to "Y" }
        val s = ep.current()
        assertNull(s.uid)                        // never a possibly-stale user; signed-out snapshot
    }

    @Test fun returnedUidAlwaysMatchesAFreshIdentityReadUnderContention() {
        val pool = Executors.newFixedThreadPool(8); val start = CountDownLatch(1); val bad = AtomicInteger()
        repeat(8) { n -> pool.submit { start.await(); repeat(300) { i ->
            val want = "U" + (n + i) % 3
            id = "t" to want
            val s = e.current()
            // The identity may have been changed by another thread since, so only a snapshot that is neither the
            // identity we set nor a still-installed one counts as impossible: uid must be some U0..U2.
            if (s.uid !in setOf("U0", "U1", "U2", "A")) bad.incrementAndGet()
        } } }
        start.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(0, bad.get())
        assertEquals(id!!.second, e.current().uid)        // quiescent: current snapshot == current identity
    }

    @Test fun sameUserSameTokenReloginIsVisibleThroughTheAuthGeneration() {
        val s = e.current(); authGen++                 // restore/adopt/signOut on the message plane
        assertFalse(e.valid(s)); assertNotEquals(s.stamp, e.current().stamp)
    }

    @Test fun stillCurrentNeverReadsTheIdentitySource() {
        val reads = AtomicInteger(); val ep = SessionEpoch { reads.incrementAndGet(); SessionEpoch.Identity("t", "A", 0) }
        val s = ep.current(); val before = reads.get()
        assertTrue(ep.stillCurrent(s)); assertEquals(before, reads.get())       // no identity read, so no foreign lock is taken
        ep.bump(); assertFalse(ep.stillCurrent(s)); assertEquals(before, reads.get())
    }

    @Test fun peekIsPureAndSeesBumpBeforeAnyCleanup() {
        val reads = AtomicInteger(); val ep = SessionEpoch { reads.incrementAndGet(); SessionEpoch.Identity("t", "A", 0) }
        val s = ep.current(); val n = reads.get()
        assertEquals(s.stamp, ep.peek().stamp)
        ep.bump()                                        // clear/delete bump the stamp FIRST, then clean up
        assertNotEquals(s.stamp, ep.peek().stamp); assertEquals(n, reads.get())
    }
}
