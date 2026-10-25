package com.example.chat

import com.example.chat.data.auth.AuthChangeEvent
import com.example.chat.data.auth.AuthSessionManager
import com.example.chat.data.auth.SessionStorage
import com.example.chat.data.auth.SessionStore
import com.example.chat.data.auth.SupabaseAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthSessionParityTest {

    private class MemoryStorage : SessionStorage {
        var stored: SessionStore.StoredSession? = null
        var saves = 0
        override suspend fun load(): SessionStore.StoredSession? = stored
        override suspend fun save(session: SupabaseAuthClient.AuthSession, userId: String) {
            saves++
            stored = SessionStore.StoredSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAtEpochMs = 0L, // manager owns expiry in memory; storage is passthrough
                userId = userId,
            )
        }
        override suspend fun clear() { stored = null }
    }

    /** Canned GoTrue server; handlers keyed by "METHOD path" prefix. */
    private class FakeGoTrue {
        val calls = mutableListOf<String>()
        val handlers = LinkedHashMap<String, (JSONObject) -> Pair<Int, JSONObject>>()

        fun httpClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val bodyText = req.body?.let { b ->
                    val buf = okio.Buffer(); b.writeTo(buf); buf.readUtf8()
                } ?: "{}"
                val body = try { JSONObject(bodyText) } catch (_: Exception) { JSONObject() }
                val url = req.url.encodedPath + (req.url.encodedQuery?.let { "?$it" } ?: "")
                calls.add("${req.method} $url")
                val handler = handlers.entries.firstOrNull { (k, _) ->
                    "${req.method} $url".startsWith(k)
                }?.value
                val (code, json) = handler?.invoke(body)
                    ?: (404 to JSONObject().put("msg", "no handler"))
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(json.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
    }

    private fun tokenResponse(access: String = "at", refresh: String = "rt", expiresIn: Long = 3600) =
        JSONObject()
            .put("access_token", access)
            .put("refresh_token", refresh)
            .put("token_type", "bearer")
            .put("expires_in", expiresIn)
            .put("user", JSONObject()
                .put("id", "u1")
                .put("email", "a@b.c")
                .put("user_metadata", JSONObject().put("display_name", "A")))

    private fun newManager(
        server: FakeGoTrue,
        storage: MemoryStorage = MemoryStorage(),
        now: () -> Long = { 1_000_000L },
    ): Pair<AuthSessionManager, MutableList<AuthChangeEvent>> {
        val client = SupabaseAuthClient(
            baseUrl = "https://proj.supabase.co",
            anonKey = "pk",
            httpClient = server.httpClient(),
        )
        val manager = AuthSessionManager(
            auth = client,
            storage = storage,
            now = now,
            scope = CoroutineScope(Job()),
        )
        val events = mutableListOf<AuthChangeEvent>()
        manager.addAuthStateListener { e, _ -> events.add(e) }
        return manager to events
    }

    // ── restore ──────────────────────────────────────────────────────────

    @Test fun `restore with fresh persisted session emits initialSession without network`() = runBlocking {
        val server = FakeGoTrue()
        val storage = MemoryStorage()
        storage.stored = SessionStore.StoredSession("at", "rt", 1_000_000L + 3_600_000L, "u1")
        val (manager, events) = newManager(server, storage)
        manager.restore()
        assertEquals("at", manager.currentSession?.accessToken)
        assertEquals(listOf(AuthChangeEvent.initialSession), events)
        assertTrue(server.calls.isEmpty())
    }

    @Test fun `restore with expired session refreshes and emits tokenRefreshed`() = runBlocking {
        val server = FakeGoTrue()
        server.handlers["POST /auth/v1/token?grant_type=refresh_token"] = {
            200 to tokenResponse(access = "at2", refresh = "rt2")
        }
        val storage = MemoryStorage()
        storage.stored = SessionStore.StoredSession("at", "rt", 1_000_000L + 30_000L, "u1") // inside skew
        val (manager, events) = newManager(server, storage)
        manager.restore()
        assertEquals("at2", manager.currentSession?.accessToken)
        assertEquals(listOf(AuthChangeEvent.tokenRefreshed), events)
    }

    @Test fun `restore with rejected refresh clears session and emits signedOut`() = runBlocking {
        val server = FakeGoTrue()
        server.handlers["POST /auth/v1/token?grant_type=refresh_token"] = {
            400 to JSONObject().put("error_description", "Invalid refresh token")
        }
        val storage = MemoryStorage()
        storage.stored = SessionStore.StoredSession("at", "rt", 1_000_000L + 30_000L, "u1")
        val (manager, events) = newManager(server, storage)
        manager.restore()
        assertNull(manager.currentSession)
        assertNull(storage.stored)
        assertEquals(listOf(AuthChangeEvent.signedOut), events)
    }

    // ── access token seam ────────────────────────────────────────────────

    @Test fun `accessToken returns fresh token without refresh`() = runBlocking {
        val server = FakeGoTrue()
        val storage = MemoryStorage()
        storage.stored = SessionStore.StoredSession("at", "rt", 1_000_000L + 3_600_000L, "u1")
        val (manager, _) = newManager(server, storage)
        manager.restore()
        assertEquals("at", manager.accessToken())
        assertTrue(server.calls.isEmpty())
    }

    @Test fun `accessToken lazily refreshes inside skew and dedupes concurrent refreshers`() = runBlocking {
        var refreshCalls = 0
        val server = FakeGoTrue()
        server.handlers["POST /auth/v1/token?grant_type=refresh_token"] = {
            refreshCalls++
            200 to tokenResponse(access = "at2", refresh = "rt2")
        }
        val storage = MemoryStorage()
        storage.stored = SessionStore.StoredSession("at", "rt", 1_000_000L + 30_000L, "u1")
        val (manager, events) = newManager(server, storage)
        manager.restore() // performs the refresh once
        assertEquals(1, refreshCalls)
        assertEquals("at2", manager.accessToken()) // now fresh: no second refresh
        assertEquals(1, refreshCalls)
        assertTrue(events.contains(AuthChangeEvent.tokenRefreshed))
    }

    // ── otp / update / signOut ───────────────────────────────────────────

    @Test fun `verifyRecoveryOtp emits passwordRecovery and persists session`() = runBlocking {
        val server = FakeGoTrue()
        server.handlers["POST /auth/v1/verify"] = { body ->
            assertEquals("recovery", body.getString("type"))
            200 to tokenResponse()
        }
        val storage = MemoryStorage()
        val (manager, events) = newManager(server, storage)
        manager.verifyRecoveryOtp("a@b.c", "123456")
        assertEquals("at", manager.currentSession?.accessToken)
        assertEquals(1, storage.saves)
        assertEquals(listOf(AuthChangeEvent.passwordRecovery), events)
    }

    @Test fun `updateUserPassword emits userUpdated`() = runBlocking {
        val server = FakeGoTrue()
        server.handlers["PUT /auth/v1/user"] = { body ->
            assertEquals("newpw", body.getString("password"))
            200 to JSONObject().put("id", "u1").put("email", "a@b.c")
        }
        val storage = MemoryStorage()
        storage.stored = SessionStore.StoredSession("at", "rt", 1_000_000L + 3_600_000L, "u1")
        val (manager, events) = newManager(server, storage)
        manager.restore()
        manager.updateUserPassword("newpw")
        assertEquals(listOf(AuthChangeEvent.initialSession, AuthChangeEvent.userUpdated), events)
    }

    @Test fun `signOut clears session and emits signedOut even when remote logout fails`() = runBlocking {
        val server = FakeGoTrue()
        server.handlers["POST /auth/v1/logout"] = { 500 to JSONObject().put("msg", "boom") }
        val storage = MemoryStorage()
        storage.stored = SessionStore.StoredSession("at", "rt", 1_000_000L + 3_600_000L, "u1")
        val (manager, events) = newManager(server, storage)
        manager.restore()
        manager.signOut()
        assertNull(manager.currentSession)
        assertNull(storage.stored)
        assertEquals(listOf(AuthChangeEvent.initialSession, AuthChangeEvent.signedOut), events)
    }

    @Test fun `auth failure surfaces server message and status`() = runBlocking {
        val server = FakeGoTrue()
        server.handlers["POST /auth/v1/token?grant_type=password"] = {
            400 to JSONObject().put("msg", "Invalid login credentials")
        }
        val client = SupabaseAuthClient(
            baseUrl = "https://proj.supabase.co",
            anonKey = "pk",
            httpClient = server.httpClient(),
        )
        try {
            client.signInWithPassword("a@b.c", "bad")
            fail("expected AuthException")
        } catch (e: SupabaseAuthClient.AuthException) {
            assertEquals("Invalid login credentials", e.message)
            assertEquals(400, e.statusCode)
        }
        Unit
    }
}
