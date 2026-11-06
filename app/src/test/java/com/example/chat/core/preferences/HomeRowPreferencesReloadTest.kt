package com.example.chat.core.preferences

import com.example.chat.domain.models.HomePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/** Controller+serialized-blob seam proof, NOT Android disk or settings navigation acceptance. */
class HomeRowPreferencesReloadTest {
    private class Store : PreferencesBlobStore {
        val blobs = mutableMapOf<String?, String>()
        override suspend fun load(userId: String?): Map<String, Any?> {
            val raw = blobs[userId] ?: return emptyMap()
            @Suppress("UNCHECKED_CAST")
            return unwrap(JSONObject(raw)) as Map<String, Any?>
        }
        private fun unwrap(value: Any?): Any? = when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { unwrap(value.get(it)) }
            is org.json.JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
            JSONObject.NULL -> null
            else -> value
        }
        override suspend fun hasScoped(userId: String) = blobs.containsKey(userId)
        override suspend fun save(userId: String?, data: Map<String, Any?>) { blobs[userId] = JSONObject(data).toString() }
        override suspend fun clearGlobal() { blobs.remove(null) }
        override suspend fun storedUserId(): String? = null
        override suspend fun setStoredUserId(userId: String?) {}
    }
    private class Auth(private val id: String) : PreferencesAuthSource {
        override fun currentUserId() = id
        override fun addListener(listener: (String?) -> Unit) {}
        override fun removeListener(listener: (String?) -> Unit) {}
    }
    private class Lock : LockCredentialStore {
        override suspend fun hasCredential(method: String) = false
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {}
    }
    @Test fun actualSliderEndpointsAndRowOptionsReloadWithoutCrossAccountBleed() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = Store()
        fun controller(id: String) = ChatyPreferencesController(store, null, Lock(), Auth(id), scope)
        try {
            for (size in listOf(10.0, 24.0)) {
                val first = controller("fixture-owner")
                assertTrue(first.preferencesReady)
                val expected = HomePreferences(screenTextSize = size, homeStyle = "Telegram Style",
                    elapsedTime = true, hideArchivedChats = true, disableOnlineDot = true,
                    rowTextColor = 0xFF123456L, rowContactNameColor = 0xFF654321L)
                first.updateHome(expected)
                assertEquals(SaveState.Saved, first.saveState.value)
                first.dispose()
                val reloaded = controller("fixture-owner")
                assertEquals(expected, reloaded.home)
                reloaded.dispose()
                val other = controller("fixture-other")
                assertEquals("Other account must retain defaults", HomePreferences(), other.home)
                other.dispose()
            }
        } finally { scope.cancel() }
    }
}
