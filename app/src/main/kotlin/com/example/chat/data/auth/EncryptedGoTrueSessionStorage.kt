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
            .remove(CLEARED)
            .commit()) { "Could not save encrypted sign-in session." }
    }

    override suspend fun load(): SessionStore.StoredSession? = withContext(Dispatchers.IO) {
        val p = encrypted
        // A durable sign-out tombstone must win over any stale plaintext
        // DataStore copy left behind by a crash or failed legacy cleanup.
        if (p.getBoolean(CLEARED, false)) {
            context.sessionDataStore.edit { it.clear() }
            return@withContext null
        }
        val access = p.getString(ACCESS, null)
        val refresh = p.getString(REFRESH, null)
        val expiry = p.getLong(EXPIRY, 0L)
        val user = p.getString(USER, null)
        if (!access.isNullOrBlank() && !refresh.isNullOrBlank() && expiry > 0L && !user.isNullOrBlank()) {
            return@withContext SessionStore.StoredSession(access, refresh, expiry, user)
        }

        val old = context.sessionDataStore.data.first()
        val legacyAccess = old[stringPreferencesKey("access_token")]
        val legacyRefresh = old[stringPreferencesKey("refresh_token")]
        val legacyExpiry = old[longPreferencesKey("expires_at_epoch_ms")]
        val legacyUser = old[stringPreferencesKey("user_id")]
        if (legacyAccess.isNullOrBlank() || legacyRefresh.isNullOrBlank() || legacyExpiry == null || legacyUser.isNullOrBlank()) {
            return@withContext null
        }
        check(p.edit().putString(ACCESS, legacyAccess).putString(REFRESH, legacyRefresh)
            .putLong(EXPIRY, legacyExpiry).putString(USER, legacyUser).commit()) {
            "Could not migrate sign-in session to encrypted storage."
        }
        context.sessionDataStore.edit { it.clear() }
        SessionStore.StoredSession(legacyAccess, legacyRefresh, legacyExpiry, legacyUser)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            // Commit the tombstone first. If legacy DataStore cleanup is
            // interrupted, the next load must not migrate the signed-out user.
            check(encrypted.edit().remove(ACCESS).remove(REFRESH).remove(EXPIRY).remove(USER)
                .putBoolean(CLEARED, true).commit()) {
                "Could not clear encrypted sign-in session."
            }
            context.sessionDataStore.edit { it.clear() }
        }
    }

    private companion object {
        const val ACCESS = "gotrue_access_token"
        const val REFRESH = "gotrue_refresh_token"
        const val EXPIRY = "gotrue_expires_at_epoch_ms"
        const val USER = "gotrue_user_id"
        const val CLEARED = "gotrue_session_cleared"
    }
}
