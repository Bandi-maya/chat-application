package com.example.chat.core.preferences

/** Result of running [PreferencesMigrator.migrate] over a raw serialized
 *  blob. Exact port of lib/ui/core/persistence/preferences_migrator.dart.
 *
 *  [data] is the sanitized/upgraded map, safe to apply to the typed models
 *  and safe to persist and sync. [legacySecrets] holds any plaintext
 *  credentials found in a pre-v2 blob so the caller can move them into
 *  platform secure storage before they are discarded. They are NEVER
 *  present in [data]. */
data class MigrationResult(
    val data: Map<String, Any?>,
    val fromVersion: Int,
    val legacySecrets: Map<String, String> = emptyMap(),
    val legacyPinLength: Int? = null,
) {
    val migratedSecrets: Boolean get() = legacySecrets.isNotEmpty()
}

/** Pure, deterministic upgrade of a serialized preference blob to
 *  [PreferenceKeys.currentSchemaVersion]. Never throws on malformed input:
 *  unknown or corrupt sections pass through untouched (the typed fromMap
 *  factories and the controller's per-group recovery handle field-level
 *  corruption). Its one behavioral change is the v1 -> v2 security
 *  hardening: plaintext PIN / pattern / password / recovery fields are
 *  removed from the blob so they can never again be written to disk or
 *  uploaded to the backend. */
object PreferencesMigrator {

    fun migrate(raw: Map<String, Any?>): MigrationResult {
        val data = raw.toMutableMap()
        val fromVersion = readVersion(data)

        val legacySecrets = mutableMapOf<String, String>()
        var legacyPinLength: Int? = null

        // v1 (or unversioned legacy) -> v2: strip synced plaintext credentials.
        if (fromVersion < 2) {
            val security = data[PreferenceKeys.security]
            if (security is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val sec = (security as Map<String, Any?>).toMutableMap()

                fun secretOf(key: String): String? {
                    val value = sec[key]
                    return if (value is String && value.isNotEmpty()) value else null
                }

                val pin = secretOf("pinCode")
                val pattern = secretOf("patternCode")
                val password = secretOf("password")
                if (pin != null) {
                    legacySecrets["PIN"] = pin
                    legacyPinLength = if (pin.length == 6) 6 else 4
                }
                if (pattern != null) legacySecrets["Pattern"] = pattern
                if (password != null) legacySecrets["Password"] = password

                for (field in PreferenceKeys.legacySecuritySecretFields) {
                    sec.remove(field)
                }
                data[PreferenceKeys.security] = sec
            }
        }

        data[PreferenceKeys.schemaVersion] = PreferenceKeys.currentSchemaVersion
        return MigrationResult(
            data = data,
            fromVersion = fromVersion,
            legacySecrets = legacySecrets,
            legacyPinLength = legacyPinLength,
        )
    }

    /** Whether a raw blob (local or remote) still carries any plaintext
     *  security secret. Decides whether a cleaned snapshot must be re-pushed
     *  to the backend to purge cloud-stored plaintext. */
    fun rawHasLegacySecrets(raw: Map<String, Any?>): Boolean {
        val security = raw[PreferenceKeys.security]
        if (security !is Map<*, *>) return false
        for (field in PreferenceKeys.legacySecuritySecretFields) {
            val value = security[field]
            if (value is String && value.isNotEmpty()) return true
        }
        return false
    }

    private fun readVersion(data: Map<String, Any?>): Int {
        val raw = data[PreferenceKeys.schemaVersion]
        if (raw is Int) return raw
        if (raw is Number) return raw.toInt()
        val parsed = (raw?.toString() ?: "").toIntOrNull()
        if (parsed != null) return parsed
        // No version field at all means the earliest (pre-versioning) schema.
        return if (data.isEmpty()) PreferenceKeys.currentSchemaVersion else 1
    }
}
