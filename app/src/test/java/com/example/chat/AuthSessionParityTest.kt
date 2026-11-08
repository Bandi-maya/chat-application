package com.example.chat

import com.example.chat.data.auth.AuthChangeEvent
import com.example.chat.data.auth.AuthSessionManager
import com.example.chat.data.auth.SessionStorage
import com.example.chat.data.auth.SessionStore
import com.example.chat.data.auth.SupabaseAuthClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local-shell session contract (backend migration): restore is local-only,
 *  an expired persisted session is dropped without any network call, and
 *  clearLocal signs out. The GoTrue refresh/OTP/password paths are retired;
 *  ApiBackendService owns live authentication. */
class AuthSessionParityTest {

    private class MemStorage(var stored: SessionStore.StoredSession? = null) : SessionStorage {
        var saves = 0
        override suspend fun load() = stored
        override suspend fun save(session: SupabaseAuthClient.AuthSession, userId: String) {
            saves++
        }
        override suspend fun clear() {
            stored = null
        }
    }

    private fun stored(expiresAtEpochMs: Long) = SessionStore.StoredSession(
        accessToken = "tok",
        refreshToken = "refresh",
        expiresAtEpochMs = expiresAtEpochMs,
        userId = "user-1",
    )

    @Test fun `restore with fresh persisted session emits initialSession without network`() = runBlocking {
        val storage = MemStorage(stored(System.currentTimeMillis() + 3_600_000))
        val manager = AuthSessionManager(storage = storage)
        val events = mutableListOf<AuthChangeEvent>()
        manager.addAuthStateListener { event, _ -> events.add(event) }
        manager.restore()
        assertEquals("user-1", manager.currentUserId())
        assertEquals(listOf(AuthChangeEvent.initialSession), events)
    }

    @Test fun `restore with expired session clears locally with no network`() = runBlocking {
        val storage = MemStorage(stored(System.currentTimeMillis() - 3_600_000))
        val manager = AuthSessionManager(storage = storage)
        val events = mutableListOf<AuthChangeEvent>()
        manager.addAuthStateListener { event, _ -> events.add(event) }
        manager.restore()
        // Expired session dropped locally; signedOut fires because a session
        // was present, and nothing is persisted anymore.
        assertNull(manager.currentUserId())
        assertNull(storage.stored)
        assertEquals(listOf(AuthChangeEvent.signedOut), events)
    }

    @Test fun `restore without a persisted session is a no-op`() = runBlocking {
        val storage = MemStorage(null)
        val manager = AuthSessionManager(storage = storage)
        val events = mutableListOf<AuthChangeEvent>()
        manager.addAuthStateListener { event, _ -> events.add(event) }
        manager.restore()
        assertNull(manager.currentUserId())
        assertTrue(events.isEmpty())
    }

    @Test fun `clearLocal emits signedOut only when a session was present`() = runBlocking {
        val storage = MemStorage(stored(System.currentTimeMillis() + 3_600_000))
        val manager = AuthSessionManager(storage = storage)
        manager.restore()
        val events = mutableListOf<AuthChangeEvent>()
        manager.addAuthStateListener { event, _ -> events.add(event) }
        manager.clearLocal()
        manager.clearLocal()
        assertNull(manager.currentUserId())
        assertEquals(listOf(AuthChangeEvent.signedOut), events)
    }

    @Test fun `hasAnySession reflects live or persisted identity`() = runBlocking {
        val storage = MemStorage(stored(System.currentTimeMillis() + 3_600_000))
        val manager = AuthSessionManager(storage = storage)
        assertTrue(manager.hasAnySession())
        manager.restore()
        manager.clearLocal()
        assertTrue(!manager.hasAnySession())
    }
}
