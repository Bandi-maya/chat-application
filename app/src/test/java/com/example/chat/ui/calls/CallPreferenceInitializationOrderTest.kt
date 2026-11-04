package com.example.chat.ui.calls

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CallPreferenceInitializationOrderTest {
    private class SlowStorage : CallPresentationPrefsStorage {
        val loadStarted = CompletableDeferred<Unit>()
        val allowLoad = CompletableDeferred<Unit>()
        var bundle = "apple_ios"
        override suspend fun getBool(key: String): Boolean? = null
        override suspend fun getString(key: String): String? {
            if (key != CallPresentationPreferencesStore.CALL_EXPERIENCE_BUNDLE_KEY) return null
            val snapshot = bundle
            loadStarted.complete(Unit)
            allowLoad.await()
            return snapshot
        }
        override suspend fun putBool(key: String, value: Boolean) = true
        override suspend fun putString(key: String, value: String): Boolean { bundle = value; return true }
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
            assertEquals("samsung_one_ui", storage.bundle)
        } finally { scope.cancel() }
    }
}
