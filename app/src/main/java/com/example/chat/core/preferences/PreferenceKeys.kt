package com.example.chat.core.preferences

/** Exact port of lib/ui/core/persistence/preference_keys.dart (api branch).
 *  Every string used to store or transport preference data lives here so a
 *  rename can never silently orphan saved user data. */
object PreferenceKeys {
    /** Current schema version of the serialized preference blob. */
    const val currentSchemaVersion = 2

    // Storage-level keys (SharedPreferences entries).

    /** Base key for the serialized preference blob; namespaced per user when
     *  authenticated (see [scopedPreferences]). The bare key is only the
     *  signed-out bucket and the legacy pre-namespacing read. */
    const val preferencesBase = "chaty_preferences_v1"

    /** Device-global theme/display state; loaded before auth is known. */
    const val themeState = "chaty_theme_v1"

    /** Device-global UI template configuration state. */
    const val templateState = "chaty_template_v1"

    /** Id of the last authenticated user; lets a legacy global blob be
     *  adopted into its owner's namespace exactly once. */
    const val storedUserId = "chaty_stored_user_id"

    /** Per-user namespaced preferences key. */
    fun scopedPreferences(userId: String): String = "$preferencesBase.$userId"

    // Top-level fields inside the serialized preference blob.

    const val schemaVersion = "schemaVersion"
    const val privacy = "privacy"
    const val security = "security"
    const val home = "home"
    const val conversation = "conversation"
    const val notification = "notification"
    const val automation = "automation"
    const val effects = "effects"
    const val universal = "universal"
    const val status = "status"
    const val toasts = "toasts"
    const val gbFeatures = "gbFeatures"
    const val favorites = "favorites"

    /** Top-level group keys, used by corruption recovery to know which fields
     *  are structurally expected to be maps. */
    val groupKeys: List<String> = listOf(
        privacy, security, home, conversation, notification,
        automation, effects, universal, status, toasts,
    )

    /** Legacy plaintext credential fields purged on migration; real
     *  credentials live only in platform secure storage (LocalLockService). */
    val legacySecuritySecretFields: List<String> = listOf(
        "pinCode", "password", "patternCode", "recoveryQuestion", "recoveryAnswer",
    )
}
