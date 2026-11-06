package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

/** Same cases as ScopedMediaTransportTest but with the REAL ApiAuthSession + ApiAccountScopeOwner minting the
 *  scopes: every transition is a real login/logout/route change, fired inside the fake HTTP transport at a barrier
 *  (before start, during the primary request, before the 404 retry, after the response). */
class RealScopeOwnerHarnessTest {
    private class MemStore : ApiSessionStore {
        override fun loadToken(): String? = null; override fun loadUserJson(): String? = null
        override fun save(token: String, userJson: String) {}; override fun clear() {}
    }
    private class Env {
        val lock = Any(); val session = ApiAuthSession(MemStore(), lock)
        var routing = ApiRoutingSnapshot("t1", "https://a.example", "wss://a.example/ws", 1)
        val owner = ApiAccountScopeOwner(lock, session::snapshot) { routing }
        fun login(uid: String, token: String) = session.adopt(ApiAuthResult(token, ApiUserProfile(uid, uid, uid, "U")))
        fun snap() = owner.snapshot()
    }
    private val transitions: Map<String, (Env) -> Unit> = mapOf(
        "logout" to { e -> e.session.signOut() },
        "switch-user" to { e -> e.login("u2", "tokB") },
        "same-uid-relogin(A-B-A shape)" to { e -> e.session.signOut(); e.login("u1", "tokA2") },
        "tenant-change" to { e -> e.routing = ApiRoutingSnapshot("t2", "https://a.example", "wss://a.example/ws", 2) },
        "route-change" to { e -> e.routing = ApiRoutingSnapshot("t1", "https://b.example", "wss://b.example/ws", 3) },
    )

    private class UpT(val onCall: (Int) -> Unit, val st: List<Int>) : ApiMediaUploadClient.Transport {
        val urls = mutableListOf<String>()
        override fun upload(url: String, authorization: String, tenantId: String, filename: String, fileBytes: ByteArray): ApiMediaUploadClient.Response {
            urls += url; val i = urls.size - 1; onCall(i); val s = st[minOf(i, st.size - 1)]
            return ApiMediaUploadClient.Response(s, if (s == 200) """{"data":{"url":"u"}}""" else "{}")
        }
    }
    private fun up(e: Env, t: ApiMediaUploadClient.Transport) = ApiMediaUploadClient({ "live" }, { "live" }, { e.session.token }, t)
    private val msg = """{"data":{"uid":"m1","room_id":"r","sender_id":"u1","content":"hi","created_at":"2026-10-26T10:00:00Z"}}"""
    private class ChT(val onCall: (Int) -> Unit, val res: List<Int>, val msg: String) : ApiHttpTransport {
        var n = 0
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val i = n++; onCall(i); val s = res[minOf(i, res.size - 1)]; return s to (if (s == 200) msg else "")
        }
    }
    private fun ch(e: Env, t: ApiHttpTransport) = ApiChatClient({ "live" }, { "live" }, { e.session.token }, t)
    private fun fresh(): Env = Env().also { it.login("u1", "tokA") }

    @Test fun controlNoTransitionSucceeds() {
        val e = fresh(); val s = e.snap()
        assertEquals("u", up(e, UpT({}, listOf(404, 200))).uploadMedia(s, { e.snap() === s }, "a", byteArrayOf(1))["url"])
        assertNotNull(ch(e, ChT({}, listOf(200), msg)).sendMessage(s, { e.snap() === s }, "r", "hi"))
        assertSame("scope identity is stable without a transition", s, e.snap())
    }

    @Test fun everyRealTransitionAtEveryBarrierIsStaleForUploadAndSend() {
        for ((name, tr) in transitions) {
            // before start
            run { val e = fresh(); val s = e.snap(); tr(e); val t = UpT({}, listOf(200))
                try { up(e, t).uploadMedia(s, { e.snap() === s }, "a", byteArrayOf(1)); fail("$name before-start upload") } catch (_: ApiScopeChangedException) {}
                assertEquals("$name before-start sends nothing", 0, t.urls.size)
                val c = ChT({}, listOf(200), msg)
                try { ch(e, c).sendMessage(s, { e.snap() === s }, "r", "hi"); fail("$name before-start send") } catch (_: ApiScopeChangedException) {}
                assertEquals(0, c.n) }
            // during the primary request, and 404 retry must not happen
            run { val e = fresh(); val s = e.snap(); val t = UpT({ if (it == 0) tr(e) }, listOf(404, 200))
                try { up(e, t).uploadMedia(s, { e.snap() === s }, "a", byteArrayOf(1)); fail("$name during upload") } catch (_: ApiScopeChangedException) {}
                assertEquals("$name no 404 retry", 1, t.urls.size)
                val e2 = fresh(); val s2 = e2.snap(); val c = ChT({ if (it == 0) tr(e2) }, listOf(404, 200), msg)
                try { ch(e2, c).sendMessage(s2, { e2.snap() === s2 }, "r", "hi"); fail("$name during send") } catch (_: ApiScopeChangedException) {}
                assertEquals("$name no 404 retry (send)", 1, c.n) }
            // after the success response: nothing is returned or published
            run { val e = fresh(); val s = e.snap(); val t = UpT({ tr(e) }, listOf(200))
                try { up(e, t).uploadMedia(s, { e.snap() === s }, "a", byteArrayOf(1)); fail("$name after upload") } catch (_: ApiScopeChangedException) {}
                val e2 = fresh(); val s2 = e2.snap()
                try { ch(e2, ChT({ tr(e2) }, listOf(200), msg)).sendMessage(s2, { e2.snap() === s2 }, "r", "hi"); fail("$name after send") } catch (_: ApiScopeChangedException) {}
                // the publish gate the service uses: block must not run for a stale scope
                var ran = false; assertFalse("$name publish gate", e2.owner.withScope(s2) { ran = true }); assertFalse(ran) }
        }
    }
}
