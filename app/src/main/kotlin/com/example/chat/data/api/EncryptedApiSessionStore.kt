package com.example.chat.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keystore-backed persistence for custom API session credentials. */
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
        val encryptedToken = secure.getString(TOKEN, null)
        val encryptedUser = secure.getString(USER, null)
        val encryptedRefresh = secure.getString(REFRESH, null)
        val oldToken = legacy.getString(TOKEN, null)
        val oldUser = legacy.getString(USER, null)
        val oldRefresh = legacy.getString(REFRESH, null)

        val token = encryptedToken ?: oldToken
        val user = encryptedUser ?: oldUser
        if (token.isNullOrBlank() || user.isNullOrBlank()) return

        val editor = secure.edit().putString(TOKEN, token).putString(USER, user)
        (encryptedRefresh ?: oldRefresh)?.let { editor.putString(REFRESH, it) }
        check(editor.commit()) { "Unable to migrate API session into encrypted storage." }
        check(legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()) {
            "Unable to remove migrated plaintext API session."
        }
    }

    override fun loadToken(): String? = synchronized(this) {
        migrate()
        secure.getString(TOKEN, null)
    }

    override fun loadUserJson(): String? = synchronized(this) {
        migrate()
        secure.getString(USER, null)
    }

    override fun save(token: String, userJson: String): Unit = synchronized(this) {
        check(secure.edit().putString(TOKEN, token).putString(USER, userJson).remove(REFRESH).commit()) {
            "Unable to persist encrypted API session."
        }
        legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()
        Unit
    }

    override fun loadRefreshToken(): String? = synchronized(this) {
        migrate()
        secure.getString(REFRESH, null)
    }

    override fun saveRefreshToken(token: String?): Unit = synchronized(this) {
        val editor = secure.edit()
        if (token.isNullOrBlank()) editor.remove(REFRESH) else editor.putString(REFRESH, token)
        check(editor.commit()) { "Unable to persist encrypted API refresh token." }
        legacy.edit().remove(REFRESH).commit()
        Unit
    }

    override fun clear(): Unit = synchronized(this) {
        secure.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()
        legacy.edit().remove(TOKEN).remove(USER).remove(REFRESH).commit()
        Unit
    }

    private companion object {
        const val TOKEN = "api_hub_token"
        const val USER = "api_hub_user"
        const val REFRESH = "api_hub_refresh_token"
    }
}
