package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

/** Fake-transport regressions: media upload, message send and WS fallback stay bound to the scope the action
 *  started under across switch/logout/same-uid re-login/tenant change. No network, no real accounts. */
class ScopedMediaTransportTest {
    private fun scope(user: String = "u1", token: String? = "tokA", tenant: String = "t1", base: String = "https://a.example/", gen: Long = 1) =
        ApiAccountScope(user, token, tenant, base, "wss://a.example/ws", gen)

    private class UploadT(val onCall: (Int) -> Unit = {}, val statuses: List<Int>) : ApiMediaUploadClient.Transport {
        val urls = mutableListOf<String>(); val auth = mutableListOf<String>(); val tenants = mutableListOf<String>()
        override fun upload(url: String, authorization: String, tenantId: String, filename: String, fileBytes: ByteArray): ApiMediaUploadClient.Response {
            urls += url; auth += authorization; tenants += tenantId; val i = urls.size - 1; onCall(i)
            val st = statuses[minOf(i, statuses.size - 1)]
            return ApiMediaUploadClient.Response(st, if (st == 200) """{"data":{"url":"https://cdn/x"}}""" else """{"detail":"no"}""")
        }
    }
    // The live suppliers return a DIFFERENT account on purpose: the scoped path must never read them.
    private fun uploader(t: ApiMediaUploadClient.Transport) = ApiMediaUploadClient({ "https://LIVE-B.example" }, { "LIVE-TENANT" }, { "LIVE-TOKEN" }, t)

    @Test fun uploadUsesOnlyCapturedScopeIncluding404Retry() {
        val t = UploadT(statuses = listOf(404, 200)); val s = scope()
        val r = uploader(t).uploadMedia(s, { true }, "a.jpg", byteArrayOf(1))
        assertEquals("https://cdn/x", r["url"])
        assertEquals(listOf("https://a.example/chat/upload", "https://a.example/api/v1/chat/upload"), t.urls)
        assertTrue(t.auth.all { it == "Bearer tokA" }); assertTrue(t.tenants.all { it == "t1" })
    }
    @Test fun switchBeforeStartSendsNothing() {
        val t = UploadT(statuses = listOf(200))
        try { uploader(t).uploadMedia(scope(), { false }, "a", byteArrayOf(1)); fail() } catch (_: ApiScopeChangedException) {}
        assertEquals(0, t.urls.size)
    }
    @Test fun switchDuringPrimaryUploadSkipsThe404Retry() {
        var cur = true; val t = UploadT(onCall = { if (it == 0) cur = false }, statuses = listOf(404, 200))
        try { uploader(t).uploadMedia(scope(), { cur }, "a", byteArrayOf(1)); fail() } catch (_: ApiScopeChangedException) {}
        assertEquals("no retry under a stale scope", 1, t.urls.size)
    }
    @Test fun switchAfterSuccessResponseNeverReturnsData() {
        var cur = true; val t = UploadT(onCall = { cur = false }, statuses = listOf(200))
        try { uploader(t).uploadMedia(scope(), { cur }, "a", byteArrayOf(1)); fail() } catch (_: ApiScopeChangedException) {}
    }
    @Test fun blankTokenOrUserIsNotAuthenticatedAndSendsNothing() {
        for (s in listOf(scope(token = null), scope(token = " "), scope(user = ""))) {
            val t = UploadT(statuses = listOf(200))
            try { uploader(t).uploadMedia(s, { true }, "a", byteArrayOf(1)); fail() } catch (_: ApiMediaUploadClient.UploadException) {}
            assertEquals(0, t.urls.size)
        }
    }

    private class ChatT(val onCall: (Int) -> Unit = {}, val results: List<Pair<Int, String>>, val throwOn: Int = -1) : ApiHttpTransport {
        val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            calls += Triple(method, url, headers); val i = calls.size - 1; onCall(i)
            if (i == throwOn) throw java.io.IOException("net")
            return results[minOf(i, results.size - 1)]
        }
    }
    private val okMsg = """{"data":{"uid":"m1","room_id":"r","sender_id":"u1","content":"hi","created_at":"2026-10-26T10:00:00Z"}}"""
    private fun chat(t: ApiHttpTransport) = ApiChatClient({ "https://LIVE-B.example" }, { "LIVE-TENANT" }, { "LIVE-TOKEN" }, t)

    @Test fun sendUsesCapturedScopeAndRetries404WithSameScope() {
        val t = ChatT(results = listOf(404 to "", 200 to okMsg))
        assertNotNull(chat(t).sendMessage(scope(), { true }, "r", "hi"))
        assertEquals(listOf("https://a.example/chat/rooms/r/messages", "https://a.example/api/v1/chat/rooms/r/messages"), t.calls.map { it.second })
        assertTrue(t.calls.all { it.third["Authorization"] == "Bearer tokA" && it.third["x-tenant-id"] == "t1" })
    }
    @Test fun sendSwitchBeforeDuringAndAfterThrowsAndNeverRetriesOrReturns() {
        var cur = true
        try { chat(ChatT(results = listOf(200 to okMsg))).sendMessage(scope(), { false }, "r", "hi"); fail() } catch (_: ApiScopeChangedException) {}
        val t = ChatT(onCall = { if (it == 0) cur = false }, results = listOf(404 to "", 200 to okMsg))
        try { chat(t).sendMessage(scope(), { cur }, "r", "hi"); fail() } catch (_: ApiScopeChangedException) {}
        assertEquals(1, t.calls.size)
        cur = true; val t2 = ChatT(onCall = { cur = false }, results = listOf(200 to okMsg))
        try { chat(t2).sendMessage(scope(), { cur }, "r", "hi"); fail() } catch (_: ApiScopeChangedException) {}
    }
    @Test fun httpFailureAfterSwitchIsASwitchNotAWsFallbackTrigger() {
        var cur = true
        val t = ChatT(onCall = { cur = false }, results = listOf(200 to okMsg), throwOn = 0)
        try { chat(t).sendMessage(scope(), { cur }, "r", "hi"); fail() } catch (_: ApiScopeChangedException) {}
        // same failure with the scope intact keeps the existing contract: null (caller may use the WS fallback)
        assertNull(chat(ChatT(results = listOf(200 to okMsg), throwOn = 0)).sendMessage(scope(), { true }, "r", "hi"))
        assertNull(chat(ChatT(results = listOf(500 to ""))).sendMessage(scope(), { true }, "r", "hi"))
        assertNull(chat(ChatT(results = listOf(200 to okMsg))).sendMessage(scope(token = null), { true }, "r", "hi"))
    }

    private class Sock(val l: ApiRealtimeClient.SocketListener) : ApiRealtimeClient.Socket {
        val sent = mutableListOf<String>(); override fun send(text: String): Boolean { sent += text; return true }; override fun close() {}
    }
    @Test fun wsFallbackOnlyOnSocketOpenedUnderExactlyTheExpectedScope() {
        // Adapted to the effective ApiRealtimeClient: sendMessageFallback(roomId, content) takes no
        // scope argument - the client captures socketScope at connect() and every send runs inside
        // connectionScopeGuard + an identity recheck. The original scope-spoof calls (a caller
        // claiming scope A or B per send) are UNEXPRESSIBLE against the real API; this test preserves
        // the same account-switch race instead: a socket captured under A must reject sends once the
        // current scope becomes B, and no frame may leave the stale socket. (Not identical coverage:
        // the spoofed-scope parametrization has no real-API counterpart.)
        var cur = scope(); val sockets = mutableListOf<Sock>()
        val sched = object : ApiRealtimeClient.Scheduler {
            override fun schedule(delayMillis: Long, run: () -> Unit): ApiRealtimeClient.Scheduler.Cancelable {
                if (delayMillis == 0L) run(); return ApiRealtimeClient.Scheduler.Cancelable { }
            }
        }
        val rc = ApiRealtimeClient({ "wss://a.example/ws" }, { "tokA" },
            object : ApiRealtimeClient.SocketFactory { override fun connect(url: String, listener: ApiRealtimeClient.SocketListener): ApiRealtimeClient.Socket = Sock(listener).also { sockets += it } },
            sched, null, { cur }, { e, a -> if (e === cur) { a(); true } else false })
        assertFalse("no socket yet", rc.sendMessageFallback("r", "hi"))
        rc.connect(); assertEquals(1, sockets.size)
        assertTrue(rc.sendMessageFallback("r", "hi")); assertEquals(1, sockets[0].sent.size)
        cur = scope(user = "u2", token = "tokB", tenant = "t2", gen = 2)      // switch; old socket not yet cleaned up
        assertFalse("socket captured under A rejects after the switch", rc.sendMessageFallback("r", "hi"))
        assertEquals("no frame leaked after the switch", 1, sockets[0].sent.size)
        // Positive control: reconnecting under the new scope sends normally (no over-blocking).
        rc.connect(); assertEquals(2, sockets.size)
        assertTrue(rc.sendMessageFallback("r", "hi")); assertEquals(1, sockets[1].sent.size)
        assertEquals("stale socket still untouched", 1, sockets[0].sent.size)
    }
}
