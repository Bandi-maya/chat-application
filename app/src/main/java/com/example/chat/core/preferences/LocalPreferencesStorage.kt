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
        if (userId == null) PreferenceKeys.preferencesBase
        else PreferenceKeys.scopedPreferences(userId)
}
