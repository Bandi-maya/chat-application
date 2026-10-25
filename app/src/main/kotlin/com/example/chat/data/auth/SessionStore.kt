package com.example.chat.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "chaty_session")

/**
 * Persists the GoTrue session, mirroring supabase_flutter's local session
 * persistence (access + refresh token, expiry tracking).
 */
class SessionStore(private val context: Context) : SessionStorage {
    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at_epoch_ms")
        val USER_ID = stringPreferencesKey("user_id")
    }

    data class StoredSession(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochMs: Long,
        val userId: String,
    ) {
        fun isExpired(nowMs: Long, skewMs: Long = 60_000): Boolean =
            nowMs + skewMs >= expiresAtEpochMs
    }

    override suspend fun save(session: SupabaseAuthClient.AuthSession, userId: String) {
        val expiresAt = System.currentTimeMillis() + session.expiresIn * 1000
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ACCESS] = session.accessToken
            prefs[Keys.REFRESH] = session.refreshToken
            prefs[Keys.EXPIRES_AT] = expiresAt
            prefs[Keys.USER_ID] = userId
        }
    }

    override suspend fun load(): StoredSession? {
        val prefs = context.sessionDataStore.data.first()
        val access = prefs[Keys.ACCESS] ?: return null
        val refresh = prefs[Keys.REFRESH] ?: return null
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: return null
        val userId = prefs[Keys.USER_ID] ?: return null
        return StoredSession(access, refresh, expiresAt, userId)
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
