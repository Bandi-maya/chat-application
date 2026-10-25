package com.example.chat

import com.example.chat.data.services.LocalLockService
import com.example.chat.data.services.ProtectedResourceGate
import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Parity tests for ProtectedResourceGate against
 *  lib/data/services/protected_resource_gate.dart. */
class ProtectedResourceGateParityTest {

    private class MemoryStore : SecureKeyValueStore {
        val map = mutableMapOf<String, String>()
        override suspend fun read(key: String): String? = map[key]
        override suspend fun write(key: String, value: String) { map[key] = value }
        override suspend fun delete(key: String) { map.remove(key) }
    }

    private lateinit var lockService: LocalLockService

    @Before
    fun setUp() {
        lockService = LocalLockService(MemoryStore())
        ProtectedResourceGate.invalidateAllSessions()
    }

    @Test
    fun unprotectedConversationIsAlwaysActive() {
        assertTrue(
            ProtectedResourceGate.isConversationSessionActive("c1", false, "1m"),
        )
    }

    @Test
    fun immediateTimeoutNeverKeepsSession() = runBlocking {
        val ok = authorizeOnce("c1", "1m")
        assertTrue(ok)
        assertFalse(
            ProtectedResourceGate.isConversationSessionActive("c1", true, "Immediately"),
        )
    }

    @Test
    fun sessionStaysActiveWithinTimeoutAndExpiresAfter() = runBlocking {
        val t0 = 1_000_000L
        authorizeOnce("c1", "1m", now = t0)
        assertTrue(
            ProtectedResourceGate.isConversationSessionActive("c1", true, "1m", nowMillis = t0 + 59_000),
        )
        assertFalse(
            ProtectedResourceGate.isConversationSessionActive("c1", true, "1m", nowMillis = t0 + 61_000),
        )
    }

    @Test
    fun timeoutParsingMatchesDart() {
        assertEquals(15_000L, ProtectedResourceGate.parseTimeoutMillis("15s"))
        assertEquals(30_000L, ProtectedResourceGate.parseTimeoutMillis("30s"))
        assertEquals(60_000L, ProtectedResourceGate.parseTimeoutMillis("1m"))
        assertEquals(300_000L, ProtectedResourceGate.parseTimeoutMillis("5m"))
        assertEquals(900_000L, ProtectedResourceGate.parseTimeoutMillis("15m"))
        assertEquals(0L, ProtectedResourceGate.parseTimeoutMillis("Immediately"))
        assertEquals(0L, ProtectedResourceGate.parseTimeoutMillis("bogus"))
    }

    @Test
    fun failedLockPromptDeniesAccess() = runBlocking {
        lockService.setCredential("PIN", "1234", 4)
        val ok = ProtectedResourceGate.authorizeConversation(
            conversationId = "c9",
            isProtected = true,
            autoLockTimeout = "1m",
            lockMethod = "PIN",
            lockService = lockService,
            setupPrompt = ProtectedResourceGate.SetupPrompt { _, _ -> error("setup not needed") },
            lockPrompt = ProtectedResourceGate.LockPrompt { _, _ -> false },
        )
        assertFalse(ok)
        assertFalse(ProtectedResourceGate.hasRecentAppUnlock)
    }

    @Test
    fun missingCredentialRoutesThroughSetupFirst() = runBlocking {
        val calls = mutableListOf<String>()
        val ok = ProtectedResourceGate.authorizeConversation(
            conversationId = "c2",
            isProtected = true,
            autoLockTimeout = "1m",
            lockMethod = "PIN",
            lockService = lockService,
            setupPrompt = ProtectedResourceGate.SetupPrompt { method, pinLen ->
                calls.add("setup:$method:$pinLen")
                lockService.setCredential("PIN", "1234", 4)
                true
            },
            lockPrompt = ProtectedResourceGate.LockPrompt { _, _ ->
                calls.add("lock")
                true
            },
        )
        assertTrue(ok)
        assertEquals(listOf("setup:PIN:4", "lock"), calls)
    }

    @Test
    fun generalActionSkipsCheckWhenNoCredentialEverExisted() = runBlocking {
        val ok = ProtectedResourceGate.authorizeGeneralAction(
            lockMethod = "PIN",
            lockService = lockService,
            lockPrompt = ProtectedResourceGate.LockPrompt { _, _ -> error("must not show") },
            title = "t",
            reason = "r",
        )
        assertTrue(ok)
    }

    @Test
    fun invalidationClearsConversationAndAppSessions() = runBlocking {
        authorizeOnce("c3", "5m")
        assertTrue(ProtectedResourceGate.hasRecentAppUnlock)
        ProtectedResourceGate.invalidateConversationSession("c3")
        assertFalse(
            ProtectedResourceGate.isConversationSessionActive("c3", true, "5m"),
        )
        ProtectedResourceGate.invalidateAllSessions()
        assertFalse(ProtectedResourceGate.hasRecentAppUnlock)
    }

    @Test
    fun methodConfiguredMatchesDartCases() = runBlocking {
        assertFalse(ProtectedResourceGate.isMethodConfigured(lockService, "PIN"))
        lockService.setCredential("PIN", "1234", 4)
        assertTrue(ProtectedResourceGate.isMethodConfigured(lockService, "PIN"))
        assertTrue(ProtectedResourceGate.isMethodConfigured(lockService, "Device Credential"))
        assertFalse(ProtectedResourceGate.isMethodConfigured(lockService, "Unknown"))
    }

    private suspend fun authorizeOnce(
        conversationId: String,
        timeout: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        lockService.setCredential("PIN", "1234", 4)
        return ProtectedResourceGate.authorizeConversation(
            conversationId = conversationId,
            isProtected = true,
            autoLockTimeout = timeout,
            lockMethod = "PIN",
            lockService = lockService,
            setupPrompt = ProtectedResourceGate.SetupPrompt { _, _ -> error("setup not needed") },
            lockPrompt = ProtectedResourceGate.LockPrompt { _, _ -> true },
            nowMillis = now,
        )
    }
}
