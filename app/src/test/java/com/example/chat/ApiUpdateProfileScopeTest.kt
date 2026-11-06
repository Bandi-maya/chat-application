package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

/** Source/JVM only. Real ApiAuthSession + ApiAccountScopeOwner, fake transport. No Android, no network. */
class ApiUpdateProfileScopeTest {
    private class MemStore : ApiSessionStore {
        override fun loadToken(): String? = null; override fun loadUserJson(): String? = null
        override fun save(token: String, userJson: String) {}; override fun clear() {}
    }
    private class Env {
        val lock = Any(); val session = ApiAuthSession(MemStore(), lock)
        var routing = ApiRoutingSnapshot("t1", "https://a.invalid", "wss://a", 1)
        val owner = ApiAccountScopeOwner(lock, session::snapshot) { routing }
        fun login(u: String, t: String) = session.adopt(ApiAuthResult(t, ApiUserProfile(u, u, u, "U")))
    }
    private val trans: Map<String, (Env) -> Unit> = mapOf(
        "logout" to { e -> e.session.signOut() }, "switch" to { e -> e.login("u2", "b") },
        "same-uid-relogin" to { e -> e.session.signOut(); e.login("u1", "a2") },
        "tenant" to { e -> e.routing = ApiRoutingSnapshot("t2", "https://a.invalid", "wss://a", 2) },
        "base" to { e -> e.routing = ApiRoutingSnapshot("t1", "https://b.invalid", "wss://b", 3) })
    private class Call(val url: String, val auth: String?, val tenant: String?)
    private fun client(e: Env, calls: MutableList<Call>, status: List<Int>, hook: (Int) -> Unit) = ApiAuthClient(
        { e.routing.baseUrl }, { e.routing.tenantId },
        ApiHttpTransport { _, url, h, _ -> calls += Call(url, h["Authorization"], h["x-tenant-id"]); val i = calls.size - 1; hook(i); status[minOf(i, status.size - 1)] to "" },
        { e.session.token })

    @Test fun controlUsesCapturedRouteAndRetry() {
        val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); val c = mutableListOf<Call>()
        val r = client(e, c, listOf(404, 200)) { }.updateProfile(s, { e.owner.snapshot() === s }, about = "x")
        assertTrue(r.first)
        assertEquals(listOf("https://a.invalid/app-users/profile", "https://a.invalid/api/v1/app-users/profile"), c.map { it.url })
        assertTrue(c.all { it.auth == "Bearer a" && it.tenant == "t1" })
    }
    @Test fun retryNeverRereadsLiveBaseTenantToken() {
        val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); val c = mutableListOf<Call>()
        // stale check always true: proves routing itself comes from the captured scope, not live config
        client(e, c, listOf(404, 200)) { if (it == 0) { e.routing = ApiRoutingSnapshot("t2", "https://b.invalid", "wss://b", 9); e.login("u2", "b") } }
            .updateProfile(s, { true }, about = "x")
        assertEquals(listOf("https://a.invalid/app-users/profile", "https://a.invalid/api/v1/app-users/profile"), c.map { it.url })
        assertTrue(c.all { it.auth == "Bearer a" && it.tenant == "t1" })
    }
    @Test fun everyTransitionBeforeDuringAfterIsStale() {
        for ((n, tr) in trans) {
            run { val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); tr(e); val c = mutableListOf<Call>()
                try { client(e, c, listOf(200)) { }.updateProfile(s, { e.owner.snapshot() === s }, about = "x"); fail("$n before") } catch (_: ApiScopeChangedException) {}
                assertEquals("$n before", 0, c.size) }
            run { val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); val c = mutableListOf<Call>()
                try { client(e, c, listOf(404, 200)) { if (it == 0) tr(e) }.updateProfile(s, { e.owner.snapshot() === s }, about = "x"); fail("$n during") } catch (_: ApiScopeChangedException) {}
                assertEquals("$n during: no retry", 1, c.size) }
            run { val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); val c = mutableListOf<Call>()
                try { client(e, c, listOf(200)) { tr(e) }.updateProfile(s, { e.owner.snapshot() === s }, about = "x"); fail("$n after") } catch (_: ApiScopeChangedException) {} }
        }
    }
    @Test fun blankTokenOrUserOrBaseRejectedWithoutSending() {
        for ((u, t) in listOf("u1" to " ", " " to "a")) {
            val e = Env(); val c = mutableListOf<Call>(); e.login(u, t); val s = e.owner.snapshot()
            try { client(e, c, listOf(200)) { }.updateProfile(s, { true }, about = "x"); fail() } catch (_: ScopeFail) {} catch (_: Exception) {}
            assertEquals(0, c.size)
        }
        val e = Env(); e.routing = ApiRoutingSnapshot("t1", " ", "w", 1); e.login("u1", "a"); val s = e.owner.snapshot(); val c = mutableListOf<Call>()
        try { client(e, c, listOf(200)) { }.updateProfile(s, { true }, about = "x"); fail() } catch (_: Exception) {}
        assertEquals(0, c.size)
    }
    @Test fun serverErrorDetailStillSurfaced() {
        val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot()
        val cl = ApiAuthClient({ "https://a.invalid" }, { "t1" }, ApiHttpTransport { _, _, _, _ -> 422 to """{"detail":"bad"}""" }, { "a" })
        try { cl.updateProfile(s, { true }, about = "x"); fail() } catch (x: Exception) { assertEquals("bad", x.message) }
    }
    private class ScopeFail : Exception()
}
