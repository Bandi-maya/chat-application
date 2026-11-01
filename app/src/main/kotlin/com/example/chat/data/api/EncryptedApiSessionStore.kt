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
        if (!secure.getString(TOKEN, null).isNullOrBlank()) return
        val token = legacy.getString(TOKEN, null) ?: return
        val user = legacy.getString(USER, null) ?: return
        check(secure.edit().putString(TOKEN, token).putString(USER, user).commit())
        check(legacy.edit().remove(TOKEN).remove(USER).commit())
    }

    override fun loadToken(): String? = synchronized(this) {
        migrate()
        secure.getString(TOKEN, null)
    }

    override fun loadUserJson(): String? = synchronized(this) {
        migrate()
        secure.getString(USER, null)
    }

    override fun save(token: String, userJson: String) = synchronized(this) {
        check(secure.edit().putString(TOKEN, token).putString(USER, userJson).commit())
        legacy.edit().remove(TOKEN).remove(USER).commit()
    }

    override fun clear() = synchronized(this) {
        secure.edit().remove(TOKEN).remove(USER).commit()
        legacy.edit().remove(TOKEN).remove(USER).commit()
    }

    private companion object {
        const val TOKEN = "api_hub_token"
        const val USER = "api_hub_user"
    }
}
