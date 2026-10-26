package com.example.chat.core.preferences

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test

class PreferencesControllerLoadGuardTest {
    private class GatedStore : PreferencesBlobStore {
        val blobs = mutableMapOf<String?, Map<String, Any?>>()
        val gates = mutableMapOf<String?, CompletableDeferred<Unit>>()
        val saved = mutableListOf<String?>()
        var failSaves = false
        override suspend fun load(userId: String?): Map<String, Any?> { gates[userId]?.await(); return blobs[userId] ?: emptyMap() }
        override suspend fun hasScoped(userId: String) = blobs.containsKey(userId)
        override suspend fun save(userId: String?, data: Map<String, Any?>) {
            if (failSaves) error("disk"); saved.add(userId); blobs[userId] = data
        }
        override suspend fun clearGlobal() { blobs.remove(null) }
        override suspend fun storedUserId(): String? = null
        override suspend fun setStoredUserId(userId: String?) {}
    }
    private class Lock : LockCredentialStore {
        override suspend fun hasCredential(method: String) = false
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {}
    }
    private class Auth(var userId: String?, val fireOnAdd: String? = null) : PreferencesAuthSource {
        val ls = mutableListOf<(String?) -> Unit>()
        override fun currentUserId() = userId
        override fun addListener(listener: (String?) -> Unit) { ls.add(listener); if (fireOnAdd != null) { userId = fireOnAdd; listener(fireOnAdd) } }
        override fun removeListener(listener: (String?) -> Unit) { ls.remove(listener) }
        fun emit(u: String?) { userId = u; ls.toList().forEach { it(u) } }
    }
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private fun make(store: PreferencesBlobStore, auth: PreferencesAuthSource) =
        ChatyPreferencesController(store, null, Lock(), auth, scope())

    @Test fun constructionWithImmediateDispatcherAndInlineAuthListenerDoesNotNpe() {
        val store = GatedStore()
        val c = make(store, Auth("u1", fireOnAdd = "u2")) // listener runs inside init, inline
        assertEquals(SaveState.Idle, c.saveState.value)
    }

    @Test fun slowOldAccountLoadFinishingAfterSwitchIsDropped() {
        val store = GatedStore()
        store.blobs["u1"] = mapOf(PreferenceKeys.favorites to listOf("OLD-U1"))
        store.blobs["u2"] = mapOf(PreferenceKeys.favorites to listOf("NEW-U2"))
        val gate = CompletableDeferred<Unit>(); store.gates["u1"] = gate
        val auth = Auth("u1")
        val c = make(store, auth)            // u1 load suspended on the gate
        assertFalse(c.preferencesReady)
        auth.emit("u2")                       // fast switch, u2 loads immediately
        assertTrue(c.preferencesReady)
        assertEquals(setOf("NEW-U2"), c.favorites())
        gate.complete(Unit)                   // old u1 load resumes late
        assertEquals("old account data must not be applied", setOf("NEW-U2"), c.favorites())
        assertTrue(c.preferencesReady)
        assertFalse("old load must not write the store", store.saved.contains("u1"))
    }

    @Test fun lateOldLoadAfterSwitchToSignedOutDoesNotRestoreReadiness() {
        val store = GatedStore()
        store.blobs["u1"] = mapOf(PreferenceKeys.favorites to listOf("OLD-U1"))
        val gate = CompletableDeferred<Unit>(); store.gates["u1"] = gate
        val auth = Auth("u1"); val c = make(store, auth)
        store.gates[null] = CompletableDeferred<Unit>() // signed-out load stays pending
        auth.emit(null)
        assertFalse(c.preferencesReady)
        gate.complete(Unit)
        assertFalse(c.preferencesReady)
        assertTrue(c.favorites().isEmpty())
    }

    private fun await(cond: () -> Boolean) { val end = System.currentTimeMillis() + 3000; while (!cond() && System.currentTimeMillis() < end) Thread.sleep(10) }

    @Test fun persistFailureShowsFailedThenRetrySucceedsClears() {
        val store = GatedStore(); val auth = Auth("u1"); val c = make(store, auth)
        assertTrue(c.preferencesReady)
        store.failSaves = true
        c.toggleFavorite("x")
        await { c.saveState.value is SaveState.Failed }
        assertTrue(c.saveState.value is SaveState.Failed)
        store.failSaves = false
        c.retrySave()
        await { c.saveState.value == SaveState.Saved }
        assertEquals(SaveState.Saved, c.saveState.value)
    }

    @Test fun oldUserInflightFailedWriteFinishingAfterSwitchDoesNotMarkNewUser() {
        val store = GatedStore(); val auth = Auth("u1"); val c = make(store, auth)
        store.failSaves = true
        c.toggleFavorite("x")          // write now in bounded retry/backoff for u1
        store.failSaves = false
        auth.emit("u2")                // switch while u1's write is still in flight
        assertEquals(SaveState.Idle, c.saveState.value)
        Thread.sleep(800)              // let u1's retries finish (they may fail or succeed)
        assertEquals("old user's write result must not touch the new user's state", SaveState.Idle, c.saveState.value)
    }

    @Test fun retrySaveBeforeReadyIsIgnored() {
        val store = GatedStore(); store.gates["u1"] = CompletableDeferred()
        val c = make(store, Auth("u1"))
        c.retrySave()
        Thread.sleep(50)
        assertTrue("must not write defaults over unloaded blob", store.saved.isEmpty())
    }

    private class LegacyStore : PreferencesBlobStore {
        val blobs = mutableMapOf<String?, Map<String, Any?>>()
        val mutations = mutableListOf<String>()
        @Volatile var gateNextGlobalLoad: CompletableDeferred<Unit>? = null
        override suspend fun load(userId: String?): Map<String, Any?> {
            if (userId == null) { val g = gateNextGlobalLoad; if (g != null) { gateNextGlobalLoad = null; g.await() } }
            return blobs[userId] ?: emptyMap()
        }
        override suspend fun hasScoped(userId: String) = blobs.containsKey(userId)
        override suspend fun save(userId: String?, data: Map<String, Any?>) { mutations.add("save:$userId"); blobs[userId] = data }
        override suspend fun clearGlobal() { mutations.add("clearGlobal"); blobs.remove(null) }
        override suspend fun storedUserId(): String? = null
        override suspend fun setStoredUserId(userId: String?) { mutations.add("setStored:$userId") }
    }

    @Test fun switchWhileOldUserResolvesLegacyBlobDoesNoOldWritesOrClears() {
        val store = LegacyStore()
        store.blobs[null] = mapOf(PreferenceKeys.favorites to listOf("LEGACY"))
        val gate = CompletableDeferred<Unit>(); store.gateNextGlobalLoad = gate
        val auth = Auth("u1")
        val c = make(store, auth)           // u1 stuck reading the legacy global blob
        auth.emit("u2")                     // u2 resolves fully: adopts legacy, clears global
        val afterU2 = store.mutations.toList()
        assertTrue(afterU2.contains("save:u2") && afterU2.contains("setStored:u2"))
        gate.complete(Unit)                 // old u1 resumes
        assertEquals("no mutation from the old account after the switch", afterU2, store.mutations.toList())
        assertFalse(store.mutations.any { it == "save:u1" || it == "setStored:u1" })
        assertEquals(setOf("LEGACY"), c.favorites())
        assertTrue(c.preferencesReady)
    }

    @Test fun switchBeforeLegacyAdoptionKeepsLegacyBlobForNewAccount() {
        val store = LegacyStore()
        store.blobs[null] = mapOf(PreferenceKeys.favorites to listOf("LEGACY"))
        val gate = CompletableDeferred<Unit>(); store.gateNextGlobalLoad = gate
        val auth = Auth("u1"); val c = make(store, auth)
        auth.emit("u2")
        gate.complete(Unit)
        // legacy was adopted exactly once, by the current account only
        assertEquals(1, store.mutations.count { it == "clearGlobal" })
        assertFalse(store.mutations.contains("save:u1"))
        assertEquals("save:u2", store.mutations.first { it.startsWith("save:") })
    }

    private class GatedLock : LockCredentialStore {
        val sets = mutableListOf<String>()
        @Volatile var hasGate: CompletableDeferred<Unit>? = null
        @Volatile var setGate: CompletableDeferred<Unit>? = null
        override suspend fun hasCredential(method: String): Boolean { val g = hasGate; if (g != null) { hasGate = null; g.await() }; return false }
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {
            val g = setGate; if (g != null) { setGate = null; g.await() }; sets.add("$method:$secret")
        }
    }
    private fun legacyBlob(pin: String) = mapOf<String, Any?>("schemaVersion" to 1, "security" to mapOf("pinCode" to pin))

    @Test fun switchWhileAwaitingHasCredentialDoesNotWriteOldSecret() {
        val store = GatedStore(); store.blobs["u1"] = legacyBlob("1111"); store.blobs["u2"] = emptyMap<String, Any?>() + mapOf("schemaVersion" to 2)
        val lock = GatedLock(); val gate = CompletableDeferred<Unit>(); lock.hasGate = gate
        val auth = Auth("u1")
        ChatyPreferencesController(store, null, lock, auth, scope())   // u1 suspended inside hasCredential
        auth.emit("u2")
        gate.complete(Unit)
        assertTrue("old account's PIN must not be written after the switch: ${lock.sets}", lock.sets.isEmpty())
    }

    @Test fun switchWhileMigrationSetCredentialInFlightDoesNotSaveSnapshotUnderOldUser() {
        val store = GatedStore(); store.blobs["u1"] = legacyBlob("2222"); store.blobs["u2"] = mapOf("schemaVersion" to 2)
        val lock = GatedLock(); val gate = CompletableDeferred<Unit>(); lock.setGate = gate
        val auth = Auth("u1")
        ChatyPreferencesController(store, null, lock, auth, scope())   // u1 suspended inside setCredential
        auth.emit("u2")
        val savedBefore = store.saved.toList()
        gate.complete(Unit)
        assertEquals("no store.save after the switch from the old load (pre-switch heal save is allowed)", savedBefore, store.saved.toList())
    }
}
