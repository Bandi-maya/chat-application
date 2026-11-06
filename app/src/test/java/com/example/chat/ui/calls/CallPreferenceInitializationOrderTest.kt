package com.example.chat.ui.calls

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CallPreferenceInitializationOrderTest {
    private class SlowStorage : CallPresentationPrefsStorage {
        val loadStarted = CompletableDeferred<Unit>()
        val allowLoad = CompletableDeferred<Unit>()
        var bundle = "apple_ios"
        var scopes: String? = null
        var writes = 0
        override suspend fun getBool(key: String): Boolean? = null
        override suspend fun getString(key: String): String? {
            if (key == CallPresentationPreferencesStore.BUNDLE_SCOPES_KEY) return scopes
            if (key != CallPresentationPreferencesStore.CALL_EXPERIENCE_BUNDLE_KEY) return null
            val snapshot = bundle
            loadStarted.complete(Unit)
            allowLoad.await()
            return snapshot
        }
        override suspend fun putBool(key: String, value: Boolean) = true
        override suspend fun putString(key: String, value: String): Boolean {
            check(key == CallPresentationPreferencesStore.BUNDLE_SCOPES_KEY) { "Unexpected legacy write: $key" }
            scopes = value; writes++; return true
        }
    }
    @Test fun selectionDuringSlowRestoreWinsAfterInitialization() = runBlocking {
        val storage = SlowStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = CallPresentationPreferencesStore(storage, scope)
            store.initialize()
            storage.loadStarted.await()
            store.setCallExperienceBundle("samsung_one_ui")
            assertEquals("apple_ios", storage.bundle)
            storage.allowLoad.complete(Unit)
            withTimeout(1000) { while (store.value.callExperienceBundleId != "samsung_one_ui") yield() }
            assertTrue(store.isInitialized)
            assertEquals("Legacy saved ID is not rewritten", "apple_ios", storage.bundle)
            assertEquals("samsung_one_ui|", storage.scopes)
            assertEquals(1, storage.writes)
            assertEquals("", store.value.messageExperienceBundleId)
            val restored = CallPresentationPreferencesStore(storage, scope)
            restored.initialize()
            withTimeout(1000) { while (!restored.isInitialized) yield() }
            assertEquals("samsung_one_ui", restored.value.callExperienceBundleId)
            assertEquals("", restored.value.messageExperienceBundleId)
        } finally { scope.cancel() }
    }
}
