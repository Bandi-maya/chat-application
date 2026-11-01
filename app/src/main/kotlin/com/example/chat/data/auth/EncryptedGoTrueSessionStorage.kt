package com.example.chat.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.legacyAuthDataStore by preferencesDataStore(name = "chaty_session")

/** Production Keystore-backed session store; migrates the old DataStore session without logging users out. */
class EncryptedGoTrueSessionStorage(private val context: Context) : SessionStorage {
    private val encrypted by lazy {
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "chaty_secure_auth",
            MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun save(session: SupabaseAuthClient.AuthSession, userId: String) = withContext(Dispatchers.IO) {
        val expiry = System.currentTimeMillis() + session.expiresIn * 1000
        check(encrypted.edit()
            .putString(ACCESS, session.accessToken)
            .putString(REFRESH, session.refreshToken)
            .putLong(EXPIRY, expiry)
            .putString(USER, userId)
            .commit()) { "Could not save encrypted sign-in session." }
    }

    override suspend fun load(): SessionStore.StoredSession? = withContext(Dispatchers.IO) {
        val p = encrypted
        val access = p.getString(ACCESS, null)
        val refresh = p.getString(REFRESH, null)
        val expiry = p.getLong(EXPIRY, 0L)
        val user = p.getString(USER, null)
        if (!access.isNullOrBlank() && !refresh.isNullOrBlank() && expiry > 0L && !user.isNullOrBlank()) {
            return@withContext SessionStore.StoredSession(access, refresh, expiry, user)
        }

        val old = context.legacyAuthDataStore.data.first()
        val oldAccess = old[stringPreferencesKey("access_token")]
        val oldRefresh = old[stringPreferencesKey("refresh_token")]
        val oldExpiry = old[longPreferencesKey("expires_at_epoch_ms")]
        val oldUser = old[stringPreferencesKey("user_id")]
        if (oldAccess.isNullOrBlank() || oldRefresh.isNullOrBlank() || oldExpiry == null || oldUser.isNullOrBlank()) {
            return@withContext null
        }
        check(p.edit().putString(ACCESS, oldAccess).putString(REFRESH, oldRefresh)
            .putLong(EXPIRY, oldExpiry).putString(USER, oldUser).commit()) {
            "Could not migrate sign-in session to encrypted storage."
        }
        context.legacyAuthDataStore.edit { it.clear() }
        SessionStore.StoredSession(oldAccess, oldRefresh, oldExpiry, oldUser)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        encrypted.edit().remove(ACCESS).remove(REFRESH).remove(EXPIRY).remove(USER).commit()
        context.legacyAuthDataStore.edit { it.clear() }
    }

    private companion object {
        const val ACCESS = "gotrue_access_token"
        const val REFRESH = "gotrue_refresh_token"
        const val EXPIRY = "gotrue_expires_at_epoch_ms"
        const val USER = "gotrue_user_id"
    }
}
