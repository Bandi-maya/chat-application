package com.example.chat.core.preferences

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Exact port of lib/ui/core/persistence/preferences_storage.dart
 *  (LocalPreferencesStorage): preference blobs are JSON strings namespaced
 *  per authenticated user so two accounts on one device can never read or
 *  overwrite each other's settings. The bare key is the signed-out bucket
 *  and the legacy pre-namespacing read. Errors surface as empty/false, never
 *  with payload content (preference values are sensitive). */
class LocalPreferencesStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_local_preferences", Context.MODE_PRIVATE)

    /** Loads the serialized blob for [userId]'s namespace (bare key when
     *  null). Returns an empty map on absence or any error. */
    suspend fun loadPreferences(userId: String? = null): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            val raw = prefs.getString(keyFor(userId), null) ?: return@withContext emptyMap()
            try {
                unwrap(JSONObject(raw)) as Map<String, Any?>
            } catch (_: Exception) {
                emptyMap()
            }
        }

    /** Persists the serialized blob for [userId]'s namespace. */
    suspend fun savePreferences(data: Map<String, Any?>, userId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                prefs.edit().putString(keyFor(userId), JSONObject(data).toString()).apply()
                true
            } catch (_: Exception) {
                false
            }
        }

    /** Whether a per-user blob already exists (legacy-adoption guard). */
    suspend fun hasScopedPreferences(userId: String): Boolean = withContext(Dispatchers.IO) {
        prefs.getString(PreferenceKeys.scopedPreferences(userId), null)?.isNotEmpty() == true
    }


    /** Removes the legacy/global blob after one-time adoption so a second
     *  account can never inherit the first account's data. */
    suspend fun clearGlobalPreferences(): Unit = withContext(Dispatchers.IO) {
        try { prefs.edit().remove(PreferenceKeys.preferencesBase).apply() } catch (_: Exception) {}
    }

    // ── device-global theme/template state (not account-sensitive) ──────

    suspend fun loadThemeState(): Map<String, Any?> = loadJsonMap(PreferenceKeys.themeState)

    suspend fun saveThemeState(data: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        try {
            prefs.edit().putString(PreferenceKeys.themeState, JSONObject(data).toString()).apply()
            true
        } catch (_: Exception) { false }
    }

    suspend fun loadTemplateState(): Map<String, Any?> = loadJsonMap(PreferenceKeys.templateState)

    suspend fun saveTemplateState(data: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        try {
            prefs.edit().putString(PreferenceKeys.templateState, JSONObject(data).toString()).apply()
            true
        } catch (_: Exception) { false }
    }

    // ── stored authenticated-user id (legacy global adoption guard) ─────

    suspend fun getStoredUserId(): String? = withContext(Dispatchers.IO) {
        try { prefs.getString(PreferenceKeys.storedUserId, null) } catch (_: Exception) { null }
    }

    suspend fun setStoredUserId(userId: String): Unit = withContext(Dispatchers.IO) {
        try { prefs.edit().putString(PreferenceKeys.storedUserId, userId).apply() } catch (_: Exception) {}
    }

    suspend fun saveUserId(userId: String) = setStoredUserId(userId)

    suspend fun clearStoredUserId(): Unit = withContext(Dispatchers.IO) {
        try { prefs.edit().remove(PreferenceKeys.storedUserId).apply() } catch (_: Exception) {}
    }

    private suspend fun loadJsonMap(key: String): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            val raw = prefs.getString(key, null)
            if (raw.isNullOrEmpty()) return@withContext emptyMap()
            try {
                @Suppress("UNCHECKED_CAST")
                unwrap(JSONObject(raw)) as Map<String, Any?>
            } catch (_: Exception) {
                emptyMap()
            }
        }

    companion object {
        /** Mirrors Dart jsonDecode: nested objects/arrays arrive as plain
         *  Map<String, Any?> / List<Any?>, never org.json types. */
        fun unwrap(value: Any?): Any? = when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { key ->
                if (value.isNull(key)) null else unwrap(value.get(key))
            }
            is org.json.JSONArray -> (0 until value.length()).map { i ->
                if (value.isNull(i)) null else unwrap(value.get(i))
            }
            else -> value
        }
    }

    private fun keyFor(userId: String?): String =
        if (userId.isNullOrEmpty()) PreferenceKeys.preferencesBase
        else PreferenceKeys.scopedPreferences(userId)
}
