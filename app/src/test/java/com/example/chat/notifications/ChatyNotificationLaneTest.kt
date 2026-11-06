package com.example.chat.notifications

import com.example.chat.data.api.ApiAccountScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

/**
 * Pure-JVM tests for ChatyNotificationLane. Deterministic: a manually pumped FIFO executor, a recording sink and the REAL
 * ChatyActionRegistry. Behavior only; no physical-thread, timing or "zero exposure" claims.
 */
class ChatyNotificationLaneTest {
    private class Queued : SerialDeferredExecutor {
        val q = ArrayDeque<Runnable>()
        var reject = false
        override fun executeSerial(command: Runnable) { if (reject) throw java.util.concurrent.RejectedExecutionException(); q.add(command) }
        fun runNext(): Boolean { val r = q.poll() ?: return false; r.run(); return true }
        fun drain(): Int { var n = 0; while (runNext()) n++; return n }
    }
    private class Sink : ChatyNotificationSink {
        val log = ArrayList<String>()
        val active = LinkedHashSet<String>()
        val kinds = ArrayList<String>()
        var throwOnActive = false; var failOrphan: String? = null
        var throwOnPost = false; var throwOnCancel = false; var failCancelFor: String? = null
        var hook: (() -> Unit)? = null
        override fun postMessage(tag: String, title: String, body: String, whenEpochMs: Long, replyNonce: String?, dismissNonce: String?) {
            log += "post:$tag"; hook?.invoke(); if (throwOnPost) throw IllegalStateException("sink"); active += tag
        }
        override fun cancelMessage(tag: String) { log += "cancel:$tag"; kinds += "msg:$tag"; if (tag == failOrphan) throw IllegalStateException("sink"); if (throwOnCancel) throw IllegalStateException("sink"); active -= tag }
        override fun postIncomingCall(tag: String, callerName: String, isVideo: Boolean, declineNonce: String?) {
            log += "post:$tag"; hook?.invoke(); active += tag
        }
        override fun cancelIncomingCall(tag: String) { log += "cancel:$tag"; kinds += "call:$tag"; if (tag == failOrphan) throw IllegalStateException("sink"); if (throwOnCancel || tag == failCancelFor) throw IllegalStateException("sink"); active -= tag }
        override fun activeTags(): List<String> { if (throwOnActive) throw IllegalStateException("sink"); return active.toList() }
    }

    private var gen = 0L
    private fun scope(user: String? = "u", token: String? = "t") = ApiAccountScope(user, token, "tn", "https://x", "ws", ++gen)
    private var cur: ApiAccountScope? = null
    private var callId: String? = null
    private val ex = Queued(); private val sink = Sink()
    private val reg = ChatyActionRegistry<ApiAccountScope>()
    private val lane = ChatyNotificationLane({ cur }, { callId }, reg, sink, ex)

    private fun post(owner: ApiAccountScope, convo: String = "c1"): Pair<String, String> {
        val r = reg.register(owner, ChatyNotificationPolicy.ACTION_REPLY, conversationId = convo)
        val d = reg.register(owner, ChatyNotificationPolicy.ACTION_DISMISS, conversationId = convo)
        lane.postMessage(owner, convo, "t", "b", 1L, r, d); return r to d
    }

    @Test fun nothing_runs_inline_and_listener_path_only_enqueues() {
        val a = scope(); cur = a
        post(a); lane.cancelMessageFor(a, "c1"); lane.cleanupNonCurrent(); lane.cleanupNonCurrent()
        assertTrue(sink.log.isEmpty())
        assertEquals(3, ex.q.size) // post, cancel, ONE coalesced cleanup
    }

    @Test fun same_conversation_under_A_then_B_cleanup_keeps_B() {
        val a = scope(); val b = scope(); cur = a
        post(a); ex.drain()
        cur = b; lane.cleanupNonCurrent(); post(b); ex.drain()
        assertEquals(setOf(lane.messageTag(b, "c1")), sink.active)
        assertTrue(lane.messageTag(a, "c1") != lane.messageTag(b, "c1"))
    }

    @Test fun transition_during_notify_is_removed_by_following_cleanup() {
        val a = scope(); val b = scope(); cur = a
        sink.hook = { cur = b; lane.cleanupNonCurrent() }
        post(a); ex.runNext()
        assertEquals(setOf(lane.messageTag(a, "c1")), sink.active) // in-flight window: visible until the drain
        sink.hook = null; ex.drain()
        assertTrue(sink.active.isEmpty())
    }

    @Test fun stale_admission_drops_without_sink_and_discards_nonces() {
        val a = scope(); val b = scope(); cur = a
        val (r, d) = post(a); cur = b
        ex.drain()
        assertTrue(sink.log.isEmpty())
        assertNull(reg.resolve(r)); assertNull(reg.resolve(d))
    }

    @Test fun A_B_Aprime_three_distinct_tags_only_current_survives() {
        val a = scope(); val b = scope(); val a2 = scope(); cur = a
        post(a); ex.drain(); cur = b; lane.cleanupNonCurrent(); post(b); ex.drain()
        cur = a2; lane.cleanupNonCurrent(); post(a2); ex.drain()
        assertEquals(setOf(lane.messageTag(a2, "c1")), sink.active)
        assertEquals(3, setOf(lane.messageTag(a, "c1"), lane.messageTag(b, "c1"), lane.messageTag(a2, "c1")).size)
        assertEquals(0, ex.q.size)
    }

    @Test fun signed_out_scope_is_not_admitted_and_cleanup_cancels_everything() {
        val a = scope(); cur = a; post(a); ex.drain()
        val out = scope(user = "", token = null); cur = out
        val (r, _) = post(out); lane.cleanupNonCurrent(); ex.drain()
        assertTrue(sink.active.isEmpty()); assertNull(reg.resolve(r))
        cur = null; post(a); lane.cleanupNonCurrent(); ex.drain(); assertTrue(sink.active.isEmpty())
    }

    @Test fun stale_cancelMessageFor_cannot_touch_another_generation() {
        val a = scope(); val b = scope(); cur = a; post(a); ex.drain()
        cur = b; lane.cleanupNonCurrent(); post(b); ex.drain()
        sink.log.clear(); lane.cancelMessageFor(a, "c1"); lane.cancelMessageFor(b, "unknown"); ex.drain()
        assertTrue(sink.log.isEmpty()); assertEquals(setOf(lane.messageTag(b, "c1")), sink.active)
        lane.cancelMessageFor(b, "c1"); ex.drain(); assertTrue(sink.active.isEmpty())
    }

    @Test fun call_replacement_and_stale_cancel_do_not_remove_new_call() {
        val a = scope(); cur = a
        callId = "k1"; lane.postIncomingCall(a, "k1", "n", false, null); ex.drain()
        callId = "k2"; lane.postIncomingCall(a, "k2", "n", false, null); ex.drain()
        assertEquals(setOf(lane.callTag(a, "k2")), sink.active)
        lane.cancelCallFor(a, "k1"); ex.drain()
        assertEquals(setOf(lane.callTag(a, "k2")), sink.active)
        lane.cancelCallFor(a, "k2"); ex.drain(); assertTrue(sink.active.isEmpty())
    }

    @Test fun call_post_for_a_no_longer_live_call_is_dropped_with_nonce_discard() {
        val a = scope(); cur = a; callId = "k1"
        val n = reg.register(a, ChatyNotificationPolicy.ACTION_DECLINE, callId = "k1")
        lane.postIncomingCall(a, "k1", "n", false, n); callId = "k9"; ex.drain()
        assertTrue(sink.active.isEmpty()); assertNull(reg.resolve(n))
    }

    @Test fun coalescing_one_pending_cleanup_and_reset_after_reject() {
        val a = scope(); cur = a
        repeat(5) { lane.cleanupNonCurrent() }
        assertEquals(1, ex.q.size); ex.drain()
        ex.reject = true; lane.cleanupNonCurrent(); assertEquals(0, ex.q.size)
        ex.reject = false; lane.cleanupNonCurrent(); assertEquals(1, ex.q.size) // flag was reset, not wedged
    }

    @Test fun rejecting_executor_never_throws_and_still_drops_nonces() {
        val a = scope(); cur = a; ex.reject = true
        val (r, d) = post(a)
        assertNull(reg.resolve(r)); assertNull(reg.resolve(d)); assertTrue(sink.log.isEmpty())
    }

    @Test fun sink_throw_does_not_kill_the_queue_and_post_stays_cancellable() {
        val a = scope(); cur = a
        sink.throwOnPost = true; post(a, "c1"); ex.drain()
        sink.throwOnPost = false; post(a, "c2"); ex.drain()
        assertTrue(lane.messageTag(a, "c2") in sink.active)
        val b = scope(); cur = b; lane.cleanupNonCurrent(); ex.drain()
        assertTrue("partial post for c1 was still cancel-attempted", sink.log.contains("cancel:${lane.messageTag(a, "c1")}"))
        assertTrue(sink.active.isEmpty())
    }

    @Test fun failed_cancel_is_retained_and_retried_once_per_drain_then_cleared_on_success() {
        val a = scope(); val b = scope(); cur = a; post(a); ex.drain()
        val tag = lane.messageTag(a, "c1")
        sink.throwOnCancel = true; cur = b; lane.cleanupNonCurrent(); ex.drain()
        assertEquals(1, sink.log.count { it == "cancel:$tag" }) // one attempt in the drain, no loop
        assertTrue(tag in sink.active) // residual alert is real while the sink keeps failing
        lane.cleanupNonCurrent(); ex.drain()
        assertEquals(2, sink.log.count { it == "cancel:$tag" }) // retried once for this next drain
        sink.throwOnCancel = false; lane.cleanupNonCurrent(); ex.drain()
        assertFalse(tag in sink.active)
        val n = sink.log.size; lane.cleanupNonCurrent(); ex.drain()
        assertEquals(n, sink.log.size) // tracking cleared after success
        post(b); ex.drain(); assertTrue(lane.messageTag(b, "c1") in sink.active) // queue alive
    }

    @Test fun failed_dismiss_of_current_owner_is_retried_by_next_cleanup_even_though_owner_is_current() {
        val a = scope(); cur = a; post(a); ex.drain()
        val tag = lane.messageTag(a, "c1")
        sink.throwOnCancel = true; lane.cancelMessageFor(a, "c1"); ex.drain()
        assertTrue(tag in sink.active)
        sink.throwOnCancel = false; lane.cleanupNonCurrent(); ex.drain()
        assertFalse(tag in sink.active)
    }

    @Test fun reconcileOnStart_retries_known_pending_tag_and_keeps_known_healthy_tag() {
        val a = scope(); cur = a; post(a, "bad"); post(a, "good"); ex.drain()
        sink.throwOnCancel = true; lane.cancelMessageFor(a, "bad"); ex.drain()
        sink.throwOnCancel = false; lane.reconcileOnStart(); ex.drain()
        assertFalse(lane.messageTag(a, "bad") in sink.active)
        assertTrue(lane.messageTag(a, "good") in sink.active)
    }

    @Test fun same_tag_call_update_is_quiet_and_does_not_cancel() {
        val a = scope(); cur = a; callId = "k1"
        lane.postIncomingCall(a, "k1", "n", false, null); ex.drain()
        sink.log.clear(); lane.postIncomingCall(a, "k1", "n2", false, null); ex.drain()
        assertEquals(listOf("post:${lane.callTag(a, "k1")}"), sink.log)
    }

    @Test fun same_tag_call_update_still_needs_full_admission() {
        val a = scope(); cur = a; callId = "k1"
        lane.postIncomingCall(a, "k1", "n", false, null); ex.drain(); sink.log.clear()
        // (1) call no longer live
        var n = reg.register(a, ChatyNotificationPolicy.ACTION_DECLINE, callId = "k1")
        callId = null; lane.postIncomingCall(a, "k1", "n", false, n); ex.drain()
        assertTrue(sink.log.isEmpty()); assertNull(reg.resolve(n))
        // (2) owner no longer current
        callId = "k1"; val b = scope(); cur = b
        n = reg.register(a, ChatyNotificationPolicy.ACTION_DECLINE, callId = "k1")
        lane.postIncomingCall(a, "k1", "n", false, n); ex.drain()
        assertTrue(sink.log.isEmpty()); assertNull(reg.resolve(n))
        // (3) owner signed out (same reference, blank token)
        val out = scope(user = "u", token = ""); cur = out
        n = reg.register(out, ChatyNotificationPolicy.ACTION_DECLINE, callId = "k1")
        lane.postIncomingCall(out, "k1", "n", false, n); ex.drain()
        assertTrue(sink.log.isEmpty()); assertNull(reg.resolve(n))
    }

    @Test fun failed_old_call_cancel_on_replacement_stays_tracked_and_is_retried() {
        val a = scope(); cur = a
        callId = "k1"; lane.postIncomingCall(a, "k1", "n", false, null); ex.drain()
        sink.failCancelFor = lane.callTag(a, "k1")
        callId = "k2"; lane.postIncomingCall(a, "k2", "n", false, null); ex.drain()
        assertTrue(lane.callTag(a, "k2") in sink.active); assertTrue(lane.callTag(a, "k1") in sink.active)
        sink.failCancelFor = null; lane.cleanupNonCurrent(); ex.drain()
        assertEquals(setOf(lane.callTag(a, "k2")), sink.active)
    }

    @Test fun reconcileOnStart_cancels_orphans_and_keeps_lane_owned_tags() {
        val a = scope(); cur = a; post(a); ex.drain()
        sink.active += "m:1:old"; sink.active += "c:1:oldcall"; sink.active += "foreign:x"
        lane.reconcileOnStart(); ex.drain()
        assertFalse("m:1:old" in sink.active); assertFalse("c:1:oldcall" in sink.active)
        assertTrue("foreign:x" in sink.active); assertTrue(lane.messageTag(a, "c1") in sink.active)
    }

    @Test fun registry_dropNonce_is_idempotent_and_ignores_null_and_unknown() {
        val a = scope(); val n = reg.register(a, ChatyNotificationPolicy.ACTION_DISMISS, conversationId = "c")
        reg.dropNonce(null, "nope", n); reg.dropNonce(n)
        assertNull(reg.resolve(n)); assertNotNull(reg)
    }

    @Test fun fifo_cleanup_runs_after_earlier_ops_and_late_old_owner_ops_are_dropped() {
        val a = scope(); val b = scope(); cur = a
        post(a); cur = b; lane.cleanupNonCurrent(); post(a, "late") // enqueued after the transition with the OLD owner
        ex.drain()
        assertTrue(sink.active.isEmpty())
        assertFalse(sink.log.any { it.endsWith(":late") })
    }

    @Test fun orphan_cancel_failure_is_retained_with_exact_tag_and_kind_and_retried_next_drain() {
        val a = scope(); cur = a // generation of a equals the number in the orphan tag below on purpose
        val msgOrphan = "m:${a.generation}:old"; val callOrphan = "c:${a.generation}:oldcall"
        sink.active += msgOrphan; sink.active += callOrphan
        sink.failOrphan = msgOrphan
        lane.reconcileOnStart(); ex.drain()
        assertTrue(msgOrphan in sink.active); assertFalse(callOrphan in sink.active)
        sink.kinds.clear(); sink.failOrphan = null
        lane.cleanupNonCurrent(); ex.drain() // next drain retries ONCE, even though its generation equals the current one
        assertEquals(listOf("msg:$msgOrphan"), sink.kinds)
        assertFalse(msgOrphan in sink.active)
        sink.kinds.clear(); lane.cleanupNonCurrent(); ex.drain(); assertTrue(sink.kinds.isEmpty())
    }

    @Test fun orphan_call_failure_retries_with_call_kind() {
        val a = scope(); cur = a
        val callOrphan = "c:99:oldcall"; sink.active += callOrphan; sink.failOrphan = callOrphan
        lane.reconcileOnStart(); ex.drain(); sink.kinds.clear(); sink.failOrphan = null
        lane.cleanupNonCurrent(); ex.drain()
        assertEquals(listOf("call:$callOrphan"), sink.kinds); assertFalse(callOrphan in sink.active)
    }

    @Test fun duplicate_pending_and_orphan_in_one_reconcile_drain_is_attempted_at_most_once() {
        val a = scope(); cur = a; post(a); ex.drain()
        val tag = lane.messageTag(a, "c1")
        sink.throwOnCancel = true; lane.cancelMessageFor(a, "c1"); ex.drain() // now pending, and still reported active
        sink.throwOnCancel = false; sink.kinds.clear()
        lane.reconcileOnStart(); ex.drain()
        assertEquals(listOf("msg:$tag"), sink.kinds)
        assertFalse(tag in sink.active)
    }

    @Test fun activeTags_throw_skips_orphan_pass_but_pending_retry_ran_and_queue_survives() {
        val a = scope(); cur = a; post(a); ex.drain()
        val tag = lane.messageTag(a, "c1")
        sink.throwOnCancel = true; lane.cancelMessageFor(a, "c1"); ex.drain()
        sink.throwOnCancel = false; sink.throwOnActive = true
        lane.reconcileOnStart(); ex.drain()
        assertFalse("pending retry still ran", tag in sink.active)
        sink.throwOnActive = false; post(a, "c2"); ex.drain()
        assertTrue(lane.messageTag(a, "c2") in sink.active)
    }

    @Test fun foreign_and_unparsable_tags_are_never_cancelled_by_reconcile() {
        val a = scope(); cur = a
        sink.active += "foreign:x"; sink.active += "m:notanumber:y"
        lane.reconcileOnStart(); ex.drain()
        assertTrue("foreign:x" in sink.active); assertFalse("m:notanumber:y" in sink.active) // m: prefix is ours
    }

    @Test fun throwing_scope_supplier_does_not_wedge_cleanup_or_later_ops() {
        val a = scope(); cur = a; post(a); ex.drain()
        val ok = lane
        var boom = true
        val lane2 = ChatyNotificationLane({ if (boom) throw IllegalStateException("x") else cur }, { callId }, reg, sink, ex)
        lane2.cleanupNonCurrent()
        try { ex.drain() } catch (_: Throwable) {}
        boom = false
        lane2.cleanupNonCurrent(); assertEquals("flag was not wedged", 1, ex.q.size); ex.drain()
        assertNotNull(ok)
    }
}
