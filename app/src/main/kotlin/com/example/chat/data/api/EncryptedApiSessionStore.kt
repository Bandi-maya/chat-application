package com.example.chat.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Keystore-backed persistence for the custom API session. Session identity
 * is treated as an indivisible token/user pair: credentials from encrypted
 * and legacy stores are never mixed. A durable clear marker prevents a failed
 * legacy deletion from resurrecting a signed-out user on next launch.
 */
class EncryptedApiSessionStore(context: Context) : ApiSessionStore {
    private val app = context.applicationContext
    private val secure by lazy {
        EncryptedSharedPreferences.create(
            app,
            "chaty_api_backend_secure",
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val legacy by lazy { app.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE) }

    @Synchronized
    private fun migrate() {
        if (secure.getBoolean(CLEARED, false)) {
            // The marker is committed before legacy deletion; old credentials
            // cannot be restored even if the following cleanup fails.
            legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()
            return
        }

        val encryptedToken = secure.getString(TOKEN, null)
        val encryptedUser = secure.getString(USER, null)
        val hasEncryptedPair = !encryptedToken.isNullOrBlank() && !encryptedUser.isNullOrBlank()
        if (hasEncryptedPair) {
            legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()
            return
        }

        // Never pair an encrypted token with a legacy user, or vice versa.
        // Migrate only a complete legacy pair after the encrypted pair is
        // absent. Malformed identity JSON is discarded instead of restored.
        val oldToken = legacy.getString(TOKEN, null)
        val oldUser = legacy.getString(USER, null)
        if (oldToken.isNullOrBlank() || oldUser.isNullOrBlank()) return
        val userIsValid = runCatching {
            apiUserFromJson(oldUser).id.isNotBlank()
        }.getOrDefault(false)
        if (!userIsValid) {
            check(legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()) {
                "Unable to remove an invalid legacy API session."
            }
            return
        }

        val editor = secure.edit()
            .putString(TOKEN, oldToken)
            .putString(USER, oldUser)
            .remove(CLEARED)
        legacy.getString(REFRESH, null)?.let { editor.putString(REFRESH, it) }
        check(editor.commit()) { "Unable to migrate API session into encrypted storage." }
        check(legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()) {
            "Unable to remove migrated plaintext API session."
        }
    }

    override fun loadToken(): String? = synchronized(this) {
        migrate()
        if (secure.getBoolean(CLEARED, false)) null else secure.getString(TOKEN, null)
    }

    override fun loadUserJson(): String? = synchronized(this) {
        migrate()
        if (secure.getBoolean(CLEARED, false)) null else secure.getString(USER, null)
    }

    override fun save(token: String, userJson: String): Unit =
        saveSession(token, userJson, refreshToken = null)

    override fun saveSession(token: String, userJson: String, refreshToken: String?): Unit = synchronized(this) {
        require(token.isNotBlank()) { "API access token must not be blank." }
        val user = runCatching { apiUserFromJson(userJson) }.getOrElse {
            throw IllegalArgumentException("API session user payload is invalid.", it)
        }
        require(user.id.isNotBlank()) { "API session user ID must not be blank." }
        val editor = secure.edit()
            .putString(TOKEN, token)
            .putString(USER, userJson)
            .remove(CLEARED)
        if (refreshToken.isNullOrBlank()) editor.remove(REFRESH) else editor.putString(REFRESH, refreshToken)
        check(editor.commit()) { "Unable to persist encrypted API session." }
        // Encrypted pair is authoritative if legacy cleanup fails.
        legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()
        Unit
    }

    override fun loadRefreshToken(): String? = synchronized(this) {
        migrate()
        if (secure.getBoolean(CLEARED, false)) null else secure.getString(REFRESH, null)
    }

    override fun saveRefreshToken(token: String?): Unit = synchronized(this) {
        if (secure.getBoolean(CLEARED, false) && !token.isNullOrBlank()) {
            throw IllegalStateException("Cannot persist a refresh token without an active API session.")
        }
        val editor = secure.edit()
        if (token.isNullOrBlank()) editor.remove(REFRESH) else editor.putString(REFRESH, token)
        check(editor.commit()) { "Unable to persist encrypted API refresh token." }
        legacy.edit().remove(REFRESH).commit()
        Unit
    }

    override fun wasExplicitlyCleared(): Boolean = synchronized(this) {
        migrate()
        secure.getBoolean(CLEARED, false)
    }

    override fun clear(): Unit = synchronized(this) {
        // Commit the tombstone with credential removal first. If deleting the
        // old plaintext copy fails, migrate() will still refuse to restore it.
        check(
            secure.edit()
                .remove(TOKEN)
                .remove(USER)
                .remove(REFRESH)
                .putBoolean(CLEARED, true)
                .commit(),
        ) { "Unable to clear encrypted API session." }
        check(legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()) {
            "API session is disabled but stale legacy credentials could not be removed."
        }
        Unit
    }

    private companion object {
        const val TOKEN = "api_hub_token"
        const val USER = "api_hub_user"
        const val REFRESH = "api_hub_refresh_token"
        const val CLEARED = "api_hub_session_cleared"
    }
}
