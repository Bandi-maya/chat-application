package com.example.chat

import com.example.chat.data.api.ApiRealtimeClient
import com.example.chat.data.local.AttachmentCachePolicy
import com.example.chat.data.network.BackoffPolicy
import org.junit.Assert.*
import org.junit.Test

class OfflineNetworkPolicyTest {
    // ── BackoffPolicy ──
    @Test fun backoffFirstRetryUnchangedAndCapped() {
        val p = BackoffPolicy(random = { 0.5 })
        assertEquals(4_000L, p.delayMs(0))
        assertTrue(p.delayMs(1) in 4_000L..60_000L)
        assertEquals(60_000L, BackoffPolicy(random = { 1.0 }).delayMs(30))
        assertTrue(BackoffPolicy(random = { 0.0 }).delayMs(30) >= 4_000L)
    }

    // ── ApiRealtimeClient with fakes ──
    private class Sched : ApiRealtimeClient.Scheduler {
        private class T(val at: Long, val seq: Int, val run: () -> Unit)
        var now = 0L
        private var seq = 0
        private val tasks = sortedSetOf<T>(compareBy({ it.at }, { it.seq }))
        private val canceled = mutableSetOf<T>()
        override fun schedule(delayMillis: Long, task: () -> Unit): ApiRealtimeClient.Scheduler.Cancelable {
            val t = T(now + delayMillis, seq++, task); tasks.add(t)
            return ApiRealtimeClient.Scheduler.Cancelable { canceled.add(t) }
        }
        fun advanceBy(ms: Long) {
            val target = now + ms
            while (true) {
                val n = tasks.firstOrNull() ?: break
                if (n.at > target) break
                tasks.remove(n); now = n.at
                if (!canceled.remove(n)) n.run()
            }
            now = target
        }
        fun drain() = advanceBy(0)
    }

    private class Sock(val l: ApiRealtimeClient.SocketListener) : ApiRealtimeClient.Socket {
        override fun send(text: String) = true
        override fun close() {}
        fun fail() = l.onFailure(this, java.io.IOException("x"))
        fun incoming(t: String) = l.onMessage(this, t)
    }

    private class Fac : ApiRealtimeClient.SocketFactory {
        val socks = mutableListOf<Sock>()
        var throwOnConnect = false
        override fun connect(url: String, listener: ApiRealtimeClient.SocketListener): ApiRealtimeClient.Socket {
            if (throwOnConnect) throw java.io.IOException("refused")
            return Sock(listener).also { socks.add(it) }
        }
    }

    private class Rig(backoff: BackoffPolicy?) {
        val s = Sched(); val f = Fac(); var token: String? = "tok"
        val c = ApiRealtimeClient({ "ws://h/ws/u1" }, { token }, f, s, backoff)
        fun connect() = apply { c.connect(); s.drain() }
    }

    @Test fun nullBackoffKeepsFixed4sEveryTime() {
        val r = Rig(null).connect()
        r.f.throwOnConnect = true
        r.f.socks.last().fail(); r.s.drain()
        r.s.advanceBy(4_000)          // reconnect attempt throws, reschedules
        r.s.advanceBy(3_999); assertEquals(1, r.f.socks.size)
        r.f.throwOnConnect = false
        r.s.advanceBy(1); assertEquals(2, r.f.socks.size)
    }

    @Test fun backoffGrowsOnRepeatedFailureAndResetsOnMessage() {
        val r = Rig(BackoffPolicy(random = { 0.5 })).connect()   // jitter 0 at random 0.5
        r.f.throwOnConnect = true
        r.f.socks.last().fail(); r.s.drain()
        r.s.advanceBy(4_000)               // attempt 0 -> 4s, connect throws again
        r.s.advanceBy(7_999)               // attempt 1 -> 8s, not yet
        r.f.throwOnConnect = false
        assertEquals(1, r.f.socks.size)
        r.s.advanceBy(1)
        assertEquals(2, r.f.socks.size)
        r.f.socks.last().incoming("""{"type":"pong"}""")   // resets attempt counter
        r.s.drain()
        r.f.throwOnConnect = true
        r.f.socks.last().fail(); r.s.drain()
        r.s.advanceBy(4_000)               // back to the 4s first-retry delay
        r.f.throwOnConnect = false
        r.s.advanceBy(8_000)
        assertEquals(3, r.f.socks.size)
    }

    @Test fun reconnectNowSkipsWaitAndResetsAttempt() {
        val r = Rig(BackoffPolicy(random = { 0.5 })).connect()
        r.f.throwOnConnect = true
        r.f.socks.last().fail(); r.s.drain()
        r.s.advanceBy(4_000)               // fails, next wait would be 8s
        r.f.throwOnConnect = false
        r.c.reconnectNow(); r.s.drain()
        assertEquals(2, r.f.socks.size)
    }

    @Test fun reconnectNowNoOpWhenConnectedOrSignedOut() {
        val r = Rig(BackoffPolicy()).connect()
        r.c.reconnectNow(); r.s.drain()
        assertEquals(1, r.f.socks.size)
        r.f.socks.last().fail(); r.s.drain()
        r.token = null
        r.c.reconnectNow(); r.s.drain()
        r.s.advanceBy(120_000)
        assertEquals(1, r.f.socks.size)
    }

    // ── cache policy ──
    @Test fun cachePolicy() {
        assertFalse(AttachmentCachePolicy.mayPersist(viewOnce = true, restricted = false))
        assertFalse(AttachmentCachePolicy.mayPersist(viewOnce = false, restricted = true))
        assertTrue(AttachmentCachePolicy.mayPersist(false, false))
        assertNotEquals(AttachmentCachePolicy.accountPath("t", "u1"), AttachmentCachePolicy.accountPath("t", "u2"))
        assertNotEquals(AttachmentCachePolicy.accountPath("t1", "u"), AttachmentCachePolicy.accountPath("t2", "u"))
        assertEquals(AttachmentCachePolicy.userDir("u"), AttachmentCachePolicy.userDir("u"))
        assertFalse(AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c","m","a")).contains("/"))
        val ev = AttachmentCachePolicy.evictionOrder(mapOf("a" to (60L to 1L), "b" to (60L to 2L), "c" to (60L to 3L)), 100L)
        assertEquals(listOf("a", "b"), ev)   // 180 > 100: drop a (120 left, still over), then b (60)
        assertEquals(listOf("a"), AttachmentCachePolicy.evictionOrder(mapOf("a" to (60L to 1L), "b" to (60L to 2L)), 100L))
    }

    @Test fun entryNameDependsOnConversationMessageAndAttachmentNotUrl() {
        assertEquals(AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c","m","att-1")), AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c","m","att-1")))
        assertNotEquals(AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c","m","att-1")), AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c","m","att-2")))
    }

    @Test fun sameAttachmentIdInDifferentMessageOrConversationGetsDifferentEntry() {
        val a = AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c1", "m1", "x"))
        assertNotEquals(a, AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c2", "m1", "x")))
        assertNotEquals(a, AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("c1", "m2", "x")))
        // field boundaries cannot be shifted to forge a collision
        assertNotEquals(AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("ab", "c", "d")),
            AttachmentCachePolicy.entryName(com.example.chat.data.local.AttachmentRef("a", "bc", "d")))
    }

    @Test fun viewOnceMetadataTruthTableFailsClosedOnMalformed() {
        fun r(vararg p: Pair<String, Any?>) = AttachmentCachePolicy.viewOnceRestricted(mapOf(*p))
        assertFalse(AttachmentCachePolicy.viewOnceRestricted(emptyMap()))
        assertFalse(r("view_once" to null)); assertFalse(r("view_once" to false)); assertFalse(r("view_once" to "false"))
        assertFalse(r("view_once" to " FALSE "))
        assertTrue(r("view_once" to true)); assertTrue(r("view_once" to "true")); assertTrue(r("view_once" to " True "))
        for (bad in listOf<Any>(1, 0, 1.5, "", " ", "yes", "1", "null", mapOf("a" to 1), listOf(true), emptyList<Any>(), Any()))
            assertTrue("malformed $bad must be restricted", r("view_once" to bad))
    }
}
