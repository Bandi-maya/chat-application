package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.ui.GalleryLeaseRegistry
import org.junit.Assert.*
import org.junit.Test

class GalleryLeaseRegistryTest {
    private class MemStore : ApiSessionStore {
        override fun loadToken(): String? = null; override fun loadUserJson(): String? = null
        override fun save(token: String, userJson: String) {}; override fun clear() {}
    }
    private class Env {
        val lock = Any(); val queue = ArrayDeque<() -> Unit>()
        var inLock = false; var ranUnderLock = false; val published = mutableListOf<Int>(); var cancelled = 0
        val reg = GalleryLeaseRegistry { queue.addLast(it) }
        var routing = ApiRoutingSnapshot("t1", "https://a", "wss://a", 1)
        lateinit var owner: ApiAccountScopeOwner
        val session: ApiAuthSession = ApiAuthSession(MemStore(), lock) {
            inLock = true
            try { reg.onTransition(owner.snapshot(), { published += it; if (inLock) ranUnderLock = true }, { cancelled++; if (inLock) ranUnderLock = true }) } finally { inLock = false }
        }
        init { owner = ApiAccountScopeOwner(lock, session::snapshot) { routing } }
        fun login(u: String, t: String) = session.adopt(ApiAuthResult(t, ApiUserProfile(u, u, u, "U")))
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }
        fun snap() = owner.snapshot()
    }
    @Test fun leaseDiesSynchronouslyAndNothingRunsInlineUnderLock() {
        val e = Env(); e.login("u1", "a"); e.drain()
        val flowScope = e.snap(); val lease = e.reg.issue(flowScope) { e.snap() }
        val flow = e.reg.begin(e.snap(), null) { e.snap() }!!
        e.login("u2", "b")
        assertFalse(lease.isValid); assertTrue(flow.isCancelled)
        val pub0 = e.published.size; val can0 = e.cancelled; assertTrue(e.queue.isNotEmpty())
        assertEquals(pub0, e.published.size); e.drain(); assertFalse(e.ranUnderLock); assertEquals(can0 + 1, e.cancelled); assertEquals(pub0 + 1, e.published.size)
    }
    @Test fun delayedOldEventNeverTouchesNewFlowOrEpochOutOfOrder() {
        val e = Env(); e.login("u1", "a"); e.drain()
        val a = e.reg.begin(e.snap(), null) { e.snap() }!!
        e.login("u2", "b")                 // queued event for A (epoch n)
        e.reg.end(a)                       // A ends before the event runs
        val b = e.reg.begin(e.snap(), null) { e.snap() }!!    // new flow B under new account
        e.drain()
        assertEquals("A's delayed cancel must not hit B's waiters", 0, e.cancelled)
        assertFalse(b.isCancelled)
        e.reg.end(a); assertNull("B still active: a second begin is refused", e.reg.begin(e.snap(), null) { e.snap() })
    }
    @Test fun epochIsMonotonicWhenQueueRunsOutOfOrder() {
        val e = Env(); e.login("u1", "a"); e.drain()
        e.login("u2", "b"); e.login("u3", "c")
        val q = e.queue.toList().reversed(); e.queue.clear(); q.forEach { it() }
        assertEquals(listOf(e.published.max()), listOf(e.published.last()))
        assertTrue(e.published.zipWithNext().all { it.first < it.second })
    }
    @Test fun leaseIsSingleUseAndCloseKillsQueuedCallbackReuse() {
        val e = Env(); e.login("u1", "a"); val s = e.snap()
        val l1 = e.reg.issue(s) { e.snap() }
        val f = e.reg.begin(s, l1) { e.snap() }!!; e.reg.end(f)
        assertNull("consumed lease cannot start a second flow (success path)", e.reg.begin(s, l1) { e.snap() })
        val l2 = e.reg.issue(s) { e.snap() }; e.reg.release(l2)
        assertFalse(l2.isValid); assertNull("closed lease cannot be reused by a queued callback", e.reg.begin(s, l2) { e.snap() })
    }
    @Test fun secondFlowNeverOverwritesActiveOne() {
        val e = Env(); e.login("u1", "a"); val s = e.snap()
        val f1 = e.reg.begin(s, null) { e.snap() }!!; assertNull(e.reg.begin(s, null) { e.snap() }); 
        val l = e.reg.issue(s) { e.snap() }; assertNull(e.reg.begin(s, l) { e.snap() }); 
    }
    @Test fun sameUidReloginAndTenantChangeKillLeases() {
        val e = Env(); e.login("u1", "a"); val l = e.reg.issue(e.snap()) { e.snap() }
        e.session.signOut(); e.login("u1", "a2"); assertFalse(l.isValid)
        val l2 = e.reg.issue(e.snap()) { e.snap() }
        e.routing = ApiRoutingSnapshot("t2", "https://a", "wss://a", 2)
        e.reg.onTransition(e.snap(), {}, {}); assertFalse(l2.isValid)
    }
    @Test fun transitionBetweenCaptureAndIssueIsCaught() {
        val e = Env(); e.login("u1", "a"); val s = e.snap(); e.login("u2", "b")
        assertFalse(e.reg.issue(s) { e.snap() }.isValid)
    }

    @Test fun transitionBetweenCaptureAndBeginIsCancelledByRecheck() {
        val e = Env(); e.login("u1", "a"); val s = e.snap(); e.login("u2", "b")   // capture, transition, then begin
        val f = e.reg.begin(s, null) { e.snap() }!!
        assertTrue("stale-scope flow is cancelled at begin so no chooser opens", f.isCancelled)
    }
    @Test fun onlyTheFlowsOwnCoroutineContextCarriesItsScope() {
        val e = Env(); e.login("u1", "a"); val f = e.reg.begin(e.snap(), null) { e.snap() }!!
        suspend fun scopeFor() = kotlin.coroutines.coroutineContext[com.example.chat.ui.AttachFlowElement]?.flow
        kotlinx.coroutines.runBlocking {
            assertNull("camera/voice-style call outside the flow borrows nothing", scopeFor())
            assertSame(f, kotlinx.coroutines.withContext(com.example.chat.ui.AttachFlowElement(f)) { scopeFor() })
        }
    }

    @Test fun lateResultOfAbandonedPickerIsDroppedNotGivenToLaterFlow() {
        val g = com.example.chat.ui.ResultGate()
        assertTrue("no abandoned picker: result accepted", g.accept())
        g.orphan()                                    // A's picker abandoned by a transition
        assertFalse("A's late callback dropped", g.accept())
        assertTrue("B's own callback accepted", g.accept())
        g.orphan(); g.orphan()
        assertFalse(g.accept()); assertFalse(g.accept()); assertTrue(g.accept())
    }

    // Per-launcher gates, out-of-order distinct launchers, ABA, and no-callback (blocked reuse of THAT launcher only).
    @Test fun perLauncherGatesDoNotCrossAndAbaAndAbsence() {
        val multi = com.example.chat.ui.ResultGate(); val single = com.example.chat.ui.ResultGate()
        multi.orphan()                                   // A used multi and was abandoned
        assertTrue("B uses single: its callback is accepted even though it arrives first", single.accept())
        assertFalse("A's late multi callback is dropped", multi.accept())
        assertTrue(multi.accept())
        multi.orphan(); assertFalse(multi.accept()); multi.orphan(); assertFalse(multi.accept())   // A, A', both dropped in turn
        // ABA: orphan multi(A), single launch/callback(B) interleaved, then multi again(C)
        multi.orphan(); assertTrue(single.accept()); assertFalse(multi.accept()); assertTrue(multi.accept())
        // absence: A's callback never arrives -> only that launcher refuses new launches; others unaffected
        val lost = com.example.chat.ui.ResultGate(); lost.orphan()
        assertTrue(lost.hasOrphans()); assertFalse(single.hasOrphans())
        assertTrue("other launcher still works", single.accept())
    }

    @Test fun captureSessionCancelledAtomicallyCleanupQueuedAndConditional() {
        val e = Env(); e.login("u1", "a"); e.drain()
        var cancels = 0
        val a = e.reg.openSession(e.snap(), { e.snap() }) { cancels++ }
        val closed = e.reg.openSession(e.snap(), { e.snap() }) { cancels += 100 }; e.reg.closeSession(closed)
        e.login("u2", "b")
        assertTrue("invalid immediately under the lock", a.isCancelled); assertEquals("cleanup is queued, not inline", 0, cancels)
        e.drain(); assertEquals("only the still-open session cleans up", 1, cancels)
        assertSame(e.snap().let { a.expectedScope }, a.expectedScope)
    }
    @Test fun captureSessionOpenedOnStaleScopeIsCancelledImmediately() {
        val e = Env(); e.login("u1", "a"); val old = e.snap(); e.login("u2", "b"); e.drain()
        var c = 0; val s = e.reg.openSession(old, { e.snap() }) { c++ }
        assertTrue(s.isCancelled); e.drain(); assertEquals(1, c)
    }
}
