package com.example.chat.data.auth

import com.example.chat.data.services.AuthUserSnapshot

/** Storage seam over the legacy GoTrue session snapshot. The backend
 *  migration retired every GoTrue network call: this coordinator is now a
 *  LOCAL-ONLY shell. It restores a persisted session for identity fallbacks,
 *  drops an expired one without contacting any server, and clears local
 *  state on sign-out. The API backend session (ApiBackendService) owns
 *  authentication, token refresh and sign-out against the live backend. */
interface SessionStorage {
    suspend fun load(): SessionStore.StoredSession?
    suspend fun save(session: SupabaseAuthClient.AuthSession, userId: String)
    suspend fun clear()
}

/** gotrue-dart AuthChangeEvent values the app observes. */
enum class AuthChangeEvent { initialSession, signedIn, signedOut, tokenRefreshed, userUpdated, passwordRecovery }

class AuthSessionManager(
    private val storage: SessionStorage,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val refreshSkewMs: Long = DEFAULT_REFRESH_SKEW_MS,
) {
    data class ActiveSession(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochMs: Long,
        val user: SupabaseAuthClient.GoTrueUser,
    ) {
        fun isExpired(nowMs: Long, skewMs: Long): Boolean = nowMs + skewMs >= expiresAtEpochMs
    }

    @Volatile
    var currentSession: ActiveSession? = null
        private set

    val currentUser: SupabaseAuthClient.GoTrueUser? get() = currentSession?.user

    fun currentUserId(): String? = currentUser?.id

    fun currentAuthUser(): AuthUserSnapshot? = currentUser?.let {
        AuthUserSnapshot(
            id = it.id,
            email = it.email ?: "",
            phone = it.phone ?: "",
            userMetadata = it.userMetadata?.entries?.associate { (k, v) -> k to v.toString().trim('"') }
                ?: emptyMap(),
        )
    }

    private val listeners = mutableListOf<(AuthChangeEvent, ActiveSession?) -> Unit>()
    private val listenersLock = Any()

    fun addAuthStateListener(listener: (AuthChangeEvent, ActiveSession?) -> Unit) {
        synchronized(listenersLock) { listeners.add(listener) }
    }

    fun removeAuthStateListener(listener: (AuthChangeEvent, ActiveSession?) -> Unit) {
        synchronized(listenersLock) { listeners.remove(listener) }
    }

    private fun emit(event: AuthChangeEvent, session: ActiveSession?) {
        val snapshot = synchronized(listenersLock) { listeners.toList() }
        for (listener in snapshot) listener(event, session)
    }

    /** Local restore: a valid persisted session emits initialSession; an
     *  expired one is dropped locally (no GoTrue refresh - that path is
     *  retired). */
    suspend fun restore() {
        val stored = storage.load() ?: return
        currentSession = ActiveSession(
            accessToken = stored.accessToken,
            refreshToken = stored.refreshToken,
            expiresAtEpochMs = stored.expiresAtEpochMs,
            user = SupabaseAuthClient.GoTrueUser(id = stored.userId),
        )
        if (stored.isExpired(now(), refreshSkewMs)) {
            clearLocalSession()
            return
        }
        emit(AuthChangeEvent.initialSession, currentSession)
    }

    /** Offline demo entry guard: true when any legacy identity is live or
     *  persisted. Demo activation must refuse in that case. */
    suspend fun hasAnySession(): Boolean = currentSession != null || storage.load() != null

    /** Public local-only sign-out: no GoTrue network. */
    suspend fun clearLocal() = clearLocalSession()

    private suspend fun clearLocalSession() {
        val hadSession = currentSession != null
        currentSession = null
        storage.clear()
        if (hadSession) emit(AuthChangeEvent.signedOut, null)
    }

    companion object {
        const val DEFAULT_REFRESH_SKEW_MS = 60_000L
    }
}
