package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

/** Desired fail-closed contracts against real ApiAccountClient. Expected RED on frozen Next18.
 * No live network. Separate from existing parity tests, which must remain unchanged.
 * Does not prove ApiBackendService publication, picker lifecycle, or editor identity binding. */
class ApiProfilePhotoScopeContractTest {
    @Test fun blankTokenMustNotUpload() {
        var calls = 0
        val client = ApiAccountClient({ "https://fixture.invalid" }, { "fixture" }, { " " },
            ApiHttpTransport { _, _, _, _ -> 500 to "" },
            ApiBinaryTransport { _, _, _, _ -> calls++; 200 to """{"data":{"url":"https://fixture.invalid/a.jpg"}}""" })
        runCatching { client.uploadProfilePhoto("fixture.jpg", byteArrayOf(1)) }
        assertEquals("Blank token must fail before transport", 0, calls)
    }

    @Test fun scopeSwitchDuring404MustNotRetargetRetry() {
        var base = "https://a.invalid"
        var tenant = "tenantA"
        var token = "fixtureA"
        val urls = mutableListOf<String>()
        val client = ApiAccountClient({ base }, { tenant }, { token },
            ApiHttpTransport { _, _, _, _ -> 500 to "" },
            ApiBinaryTransport { _, url, _, _ ->
                urls += url
                base = "https://b.invalid"; tenant = "tenantB"; token = "fixtureB"
                if (urls.size == 1) 404 to "" else 200 to """{"data":{"url":"https://b.invalid/a.jpg"}}"""
            })
        val result = runCatching { client.uploadProfilePhoto("fixture.jpg", byteArrayOf(1)) }
        assertEquals("No retry after account/routing transition", listOf("https://a.invalid/app-users/upload-photo"), urls)
        assertTrue("Stale success must never escape", result.isFailure)
    }
}

/** Real ApiAuthSession + ApiAccountScopeOwner against the scoped overload. */
class ApiProfilePhotoRealOwnerTest {
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
    private val ok = """{"data":{"url":"https://x/a.jpg"}}"""
    private val trans: Map<String, (Env) -> Unit> = mapOf(
        "ABA-same-values" to { e -> e.login("u2", "b"); e.login("u1", "a") },
        "logout" to { e -> e.session.signOut() }, "switch" to { e -> e.login("u2", "b") },
        "same-uid-relogin" to { e -> e.session.signOut(); e.login("u1", "a2") },
        "tenant" to { e -> e.routing = ApiRoutingSnapshot("t2", "https://a.invalid", "wss://a", 2) },
        "base" to { e -> e.routing = ApiRoutingSnapshot("t1", "https://b.invalid", "wss://b", 3) })
    private fun client(e: Env, urls: MutableList<String>, status: List<Int>, hook: (Int) -> Unit) = ApiAccountClient(
        { e.routing.baseUrl }, { e.routing.tenantId }, { e.session.token }, ApiHttpTransport { _, _, _, _ -> 500 to "" },
        ApiBinaryTransport { _, url, _, _ -> urls += url; val i = urls.size - 1; hook(i); status[minOf(i, status.size - 1)] to (if (status[minOf(i, status.size - 1)] == 200) ok else "") })

    @Test fun controlSucceedsAndRetryUsesCapturedBase() {
        val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); val urls = mutableListOf<String>()
        assertEquals("https://x/a.jpg", client(e, urls, listOf(404, 200)) { }.uploadProfilePhoto(s, { e.owner.snapshot() === s }, "f.jpg", byteArrayOf(1)))
        assertEquals(listOf("https://a.invalid/app-users/upload-photo", "https://a.invalid/api/v1/app-users/upload-photo"), urls)
    }
    @Test fun everyTransitionBeforeDuringAfterIsStaleAndNeverRetriesOrReturns() {
        for ((n, tr) in trans) {
            run { val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); tr(e); val urls = mutableListOf<String>()
                try { client(e, urls, listOf(200)) { }.uploadProfilePhoto(s, { e.owner.snapshot() === s }, "f", byteArrayOf(1)); fail("$n before") } catch (_: ApiScopeChangedException) {}
                assertEquals("$n before: nothing sent", 0, urls.size) }
            run { val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); val urls = mutableListOf<String>()
                try { client(e, urls, listOf(404, 200)) { if (it == 0) tr(e) }.uploadProfilePhoto(s, { e.owner.snapshot() === s }, "f", byteArrayOf(1)); fail("$n during") } catch (_: ApiScopeChangedException) {}
                assertEquals("$n during: no 404 retry", 1, urls.size) }
            run { val e = Env(); e.login("u1", "a"); val s = e.owner.snapshot(); val urls = mutableListOf<String>()
                try { client(e, urls, listOf(200)) { tr(e) }.uploadProfilePhoto(s, { e.owner.snapshot() === s }, "f", byteArrayOf(1)); fail("$n after") } catch (_: ApiScopeChangedException) {} }
        }
    }
    @Test fun blankTokenOrUserRejectedWithoutSending() {
        val e = Env(); val urls = mutableListOf<String>()
        e.login("u1", " "); val s = e.owner.snapshot()
        try { client(e, urls, listOf(200)) { }.uploadProfilePhoto(s, { true }, "f", byteArrayOf(1)); fail() } catch (_: Exception) {}
        assertEquals(0, urls.size)
        for (bad in listOf(
            ApiAccountScope(null, "a", "t", "https://a.invalid", "", 1),
            ApiAccountScope(" ", "a", "t", "https://a.invalid", "", 1),
            ApiAccountScope("u1", null, "t", "https://a.invalid", "", 1)
        )) {
            try { client(e, urls, listOf(200)) { }.uploadProfilePhoto(bad, { true }, "f", byteArrayOf(1)); fail() } catch (_: Exception) {}
            assertEquals(0, urls.size)
        }
    }
}
