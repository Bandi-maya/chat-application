package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.domain.models.ChatMessage
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real OkHttp + real ScopedCallHttpTransport + real ApiChatClient against MockWebServer. */
class ScopedMessageSenderTest {
    private val ok = """{"data":{"id":"m1","room_id":"r1","sender_id":"A","content":"hi","created_at":"2026-10-27T09:00:00Z"}}"""

    private class Env(server: MockWebServer) {
        val lock = Any()
        val a = ApiAccountScope("A", "tokenA", "t", server.url("/").toString(), "", 1)
        @Volatile var cur: ApiAccountScope = a
        val registry = CallRequestRegistry()
        val merged = mutableListOf<ChatMessage>()
        val guarded: (ApiAccountScope, () -> Unit) -> Boolean = { e, b -> synchronized(lock) { if (cur !== e) false else { b(); true } } }
        val sender = ScopedMessageSender({ cur }, guarded, { ScopedCallHttpTransport(it, guarded, registry) }, { merged += it })
        fun switchTo(next: ApiAccountScope) = synchronized(lock) { cur = next; registry.cancelAll() }
    }

    @Test fun staleScopeAtStartSendsNothing() {
        val s = MockWebServer(); s.start()
        try {
            val e = Env(s)
            e.cur = ApiAccountScope("B", "tokenB", "t", s.url("/").toString(), "", 2) // B active, action built under A
            assertFalse(e.sender.send(e.a, "r1", "hi"))
            assertEquals(0, s.requestCount)
            assertTrue(e.merged.isEmpty())
        } finally { s.shutdown() }
    }

    @Test fun happyPathUsesScopeTokenTenantAndMerges() {
        val s = MockWebServer(); s.enqueue(MockResponse().setBody(ok)); s.start()
        try {
            val e = Env(s)
            assertTrue(e.sender.send(e.a, "r1", "hi"))
            val r = s.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("Bearer tokenA", r.getHeader("Authorization"))
            assertEquals("t", r.getHeader("x-tenant-id"))
            assertEquals("/chat/rooms/r1/messages", r.path)
            assertEquals(1, e.merged.size)
        } finally { s.shutdown() }
    }

    @Test fun transitionDuringFlightCancelsRequestAndMergesNothing() {
        val s = MockWebServer(); s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); s.start()
        try {
            val e = Env(s); val done = CountDownLatch(1); var result: Boolean? = null
            Thread { result = e.sender.send(e.a, "r1", "hi"); done.countDown() }.start()
            assertNotNull(s.takeRequest(2, TimeUnit.SECONDS))
            e.switchTo(ApiAccountScope("A", "tokenA2", "t", s.url("/").toString(), "", 3)) // A -> A re-login, new scope object
            assertTrue(done.await(3, TimeUnit.SECONDS))
            assertEquals(false, result)
            assertTrue(e.merged.isEmpty())
        } finally { s.shutdown() }
    }

    @Test fun staleConfirmationIsNotMerged() {
        val s = MockWebServer(); s.enqueue(MockResponse().setBody(ok)); s.start()
        try {
            val e = Env(s)
            val sender = ScopedMessageSender({ e.cur },
                { x, b -> e.cur = ApiAccountScope("B", "tokenB", "t", "", "", 9); e.guarded(x, b) },
                { ScopedCallHttpTransport(it, e.guarded, e.registry) }, { e.merged += it })
            assertFalse(sender.send(e.a, "r1", "hi"))
            assertTrue(e.merged.isEmpty())
        } finally { s.shutdown() }
    }

    @Test fun blankInputsAndMissingTokenSendNothing() {
        val s = MockWebServer(); s.start()
        try {
            val e = Env(s)
            assertFalse(e.sender.send(e.a, "r1", "   "))
            assertFalse(e.sender.send(e.a, "", "hi"))
            val noToken = ApiAccountScope("A", null, "t", s.url("/").toString(), "", 1); e.cur = noToken
            assertFalse(e.sender.send(noToken, "r1", "hi"))
            assertEquals(0, s.requestCount)
        } finally { s.shutdown() }
    }

    @Test fun serverRejectionReturnsFalseWithoutMerge() {
        val s = MockWebServer(); s.enqueue(MockResponse().setResponseCode(500)); s.start()
        try {
            val e = Env(s)
            assertFalse(e.sender.send(e.a, "r1", "hi"))
            assertTrue(e.merged.isEmpty())
        } finally { s.shutdown() }
    }
}
