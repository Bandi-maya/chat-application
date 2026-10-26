package com.example.chat

import com.example.chat.data.e2ee.SecureKeyValueStore
import com.example.chat.data.services.LocalLockService
import com.example.chat.ui.settings.security.AppLockController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Isolated, in-memory contract for the lock overlay's "set up missing credential" path
 *  (Dart _setupMissingCredential: show the setup modal, then reload capabilities). Uses a fake
 *  secure store, so no real credential, vault, device or account state is touched. */
class AppLockSetupReloadTest {
    private class Mem : SecureKeyValueStore {
        val m = mutableMapOf<String, String>()
        var failWrites = false
        override suspend fun read(key: String) = m[key]
        override suspend fun write(key: String, value: String) {
            if (failWrites) throw IllegalStateException("synthetic write failure")
            m[key] = value
        }
        override suspend fun delete(key: String) { m.remove(key) }
    }

    private fun controller(lock: LocalLockService) =
        AppLockController(lock, { "PIN" }, "Authenticate to unlock Chaty", {})

    @Test
    fun cancelledSetupLeavesNoCredentialAndReloadStaysUnconfigured() = runBlocking {
        val store = Mem()
        val lock = LocalLockService(store, null) { 1_000L }
        val c = controller(lock)
        c.loadCapabilities()
        assertFalse(c.state.value.hasConfiguredCredential)
        // Sheet cancelled (onResult(false)): the overlay does not reload and nothing was written.
        assertTrue(store.m.isEmpty())
        c.loadCapabilities()
        assertFalse(c.state.value.hasConfiguredCredential)
        assertEquals(0, c.state.value.cooldownSeconds)
    }

    @Test
    fun finishedSetupThenReloadShowsConfiguredCredentialWithoutLockout() = runBlocking {
        val store = Mem()
        val lock = LocalLockService(store, null) { 1_000L }
        val c = controller(lock)
        c.loadCapabilities()
        assertFalse(c.state.value.hasConfiguredCredential)
        lock.setCredential("PIN", "2468", 4) // what the setup sheet saves on finish
        c.loadCapabilities()                 // overlay reload after onResult(true)
        assertTrue(c.state.value.hasConfiguredCredential)
        assertEquals(4, c.state.value.pinLength)
        assertEquals(0, c.state.value.cooldownSeconds)
        assertTrue(lock.verifyCredential("PIN", "2468"))
    }

    @Test
    fun failedSaveKeepsOverlayUnconfiguredAndRecoverable() = runBlocking {
        val store = Mem().also { it.failWrites = true }
        val lock = LocalLockService(store, null) { 1_000L }
        val c = controller(lock)
        try {
            lock.setCredential("PIN", "2468", 4)
            throw AssertionError("expected the synthetic write failure")
        } catch (_: IllegalStateException) {
        }
        c.loadCapabilities()
        assertFalse(c.state.value.hasConfiguredCredential)
        store.failWrites = false
        lock.setCredential("PIN", "2468", 4)
        c.loadCapabilities()
        assertTrue(c.state.value.hasConfiguredCredential)
    }
}
