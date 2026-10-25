package com.example.chat.data.auth

import com.example.chat.data.services.AuthUserSnapshot
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Storage seam over the GoTrue session so the session lifecycle can be
 *  exercised without Android; SessionStore (DataStore) is the production
 *  implementation, mirroring supabase_flutter's persisted session. */
interface SessionStorage {
    suspend fun load(): SessionStore.StoredSession?
    suspend fun save(session: SupabaseAuthClient.AuthSession, userId: String)
    suspend fun clear()
}

/** gotrue-dart AuthChangeEvent values the api branch observes
 *  (backend_service.dart reacts to onAuthStateChange). */
enum class AuthChangeEvent { initialSession, signedIn, signedOut, tokenRefreshed, userUpdated, passwordRecovery }

/**
 * Port of the session-lifecycle half of supabase_flutter's GoTrueClient:
 * session restore with refresh, lazy refresh on token access with refresh
 * de-duplication, auto-refresh scheduled before expiry, auth state events,
 * and fail-closed session clearing when the server rejects a refresh.
 *
 * The REST calls live in [SupabaseAuthClient]; UX-level flows (validation,
 * profile mapping) live in [AuthRepository]. This coordinator is what the
 * service locator hands to the PostgREST/realtime/storage clients as their
 * `accessToken` / `currentUserId` / `currentAuthUser` / `authSignOut` seams.
 */
class AuthSessionManager(
    private val auth: SupabaseAuthClient,
    private val storage: SessionStorage,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val refreshSkewMs: Long = DEFAULT_REFRESH_SKEW_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null

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

    /** supabase_flutter initializeAuth: restores the persisted session,
     *  refreshing first when the access token is already inside the skew.
     *  A rejected refresh clears the session (fail closed, signedOut); a
     *  network failure keeps the persisted session for a later retry. */
    suspend fun restore() {
        val stored = storage.load() ?: return
        currentSession = ActiveSession(
            accessToken = stored.accessToken,
            refreshToken = stored.refreshToken,
            expiresAtEpochMs = stored.expiresAtEpochMs,
            user = SupabaseAuthClient.GoTrueUser(id = stored.userId),
        )
        if (stored.isExpired(now(), refreshSkewMs) && stored.refreshToken.isNotEmpty()) {
            try {
                refreshSession()
            } catch (e: SupabaseAuthClient.AuthException) {
                clearLocalSession()
                return
            } catch (_: IOException) {
                // Keep the stale session; the next accessToken() read retries.
            }
        } else {
            emit(AuthChangeEvent.initialSession, currentSession)
        }
        scheduleAutoRefresh()
    }

    /** Persists a freshly issued session (login/register/OTP paths) and
     *  emits [event], defaulting to signedIn like gotrue-dart. */
    suspend fun adopt(
        session: SupabaseAuthClient.AuthSession,
        user: SupabaseAuthClient.GoTrueUser,
        event: AuthChangeEvent = AuthChangeEvent.signedIn,
    ) {
        storage.save(session, user.id)
        currentSession = ActiveSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtEpochMs = now() + session.expiresIn * 1000,
            user = user,
        )
        scheduleAutoRefresh()
        emit(event, currentSession)
    }

    /** Dart verifyOTP(..., type: OtpType.recovery) → passwordRecovery event. */
    suspend fun verifyRecoveryOtp(email: String, token: String): ActiveSession {
        val session = auth.verifyRecoveryOtp(email, token)
        val user = session.user
            ?: throw SupabaseAuthClient.AuthException("Verification failed. Invalid or expired OTP.")
        adopt(session, user, AuthChangeEvent.passwordRecovery)
        return currentSession!!
    }

    /** Dart updateUser(UserAttributes(password:)) → userUpdated event. */
    suspend fun updateUserPassword(password: String): SupabaseAuthClient.GoTrueUser {
        val token = accessToken()
            ?: throw SupabaseAuthClient.AuthException("Not signed in.")
        val user = auth.updateUserPassword(token, password)
        val session = currentSession?.copy(user = user)
        if (session != null) {
            currentSession = session
            emit(AuthChangeEvent.userUpdated, session)
        }
        return user
    }

    /** Dart signOut(): server logout is best-effort; the local session always
     *  clears and signedOut fires, matching gotrue-dart. */
    suspend fun signOut() {
        val token = currentSession?.accessToken
        if (token != null) {
            try {
                auth.signOutRemote(token)
            } catch (_: Exception) {
                // Local sign-out proceeds regardless.
            }
        }
        clearLocalSession()
    }

    /** Dart refreshSession() with de-duplication: concurrent refreshers share
     *  one network call. Rejected refresh tokens clear the session. */
    suspend fun refreshSession(): ActiveSession {
        val current = currentSession
            ?: throw SupabaseAuthClient.AuthException("No session to refresh.")
        if (current.refreshToken.isEmpty()) {
            throw SupabaseAuthClient.AuthException("No refresh token in session.")
        }
        return refreshMutex.withLock {
            val latest = currentSession
            if (latest != null && latest.accessToken != current.accessToken &&
                !latest.isExpired(now(), refreshSkewMs)
            ) {
                return@withLock latest
            }
            try {
                val refreshed = auth.refreshSession(current.refreshToken)
                val user = refreshed.user ?: current.user
                storage.save(refreshed, user.id)
                val next = ActiveSession(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken,
                    expiresAtEpochMs = now() + refreshed.expiresIn * 1000,
                    user = user,
                )
                currentSession = next
                scheduleAutoRefresh()
                emit(AuthChangeEvent.tokenRefreshed, next)
                next
            } catch (e: SupabaseAuthClient.AuthException) {
                clearLocalSession()
                throw e
            }
        }
    }

    /** Access-token seam consumed by the PostgREST/realtime/storage clients:
     *  returns the live token, refreshing first when inside the skew. */
    suspend fun accessToken(): String? {
        val session = currentSession ?: return null
        if (!session.isExpired(now(), refreshSkewMs)) return session.accessToken
        if (session.refreshToken.isEmpty()) return session.accessToken
        return try {
            refreshSession().accessToken
        } catch (e: SupabaseAuthClient.AuthException) {
            null
        } catch (_: IOException) {
            session.accessToken
        }
    }

    /** Idempotent: nested refresh/restore failure paths both funnel here;
     *  signedOut fires once, when a session was actually dropped. */
    private suspend fun clearLocalSession() {
        val hadSession = currentSession != null
        currentSession = null
        refreshJob?.cancel()
        refreshJob = null
        storage.clear()
        if (hadSession) emit(AuthChangeEvent.signedOut, null)
    }

    /** gotrue-dart autoRefreshToken: a refresh fires just before expiry. */
    private fun scheduleAutoRefresh() {
        refreshJob?.cancel()
        val session = currentSession ?: return
        if (session.refreshToken.isEmpty()) return
        val waitMs = (session.expiresAtEpochMs - refreshSkewMs - now()).coerceAtLeast(0)
        refreshJob = scope.launch {
            delay(waitMs)
            val latest = currentSession ?: return@launch
            if (latest.refreshToken.isEmpty()) return@launch
            try {
                refreshSession()
            } catch (_: SupabaseAuthClient.AuthException) {
                // refreshSession already cleared the session.
            } catch (_: IOException) {
                // Transient failure; next accessToken() read retries lazily.
            }
        }
    }

    companion object {
        const val DEFAULT_REFRESH_SKEW_MS = 60_000L
    }
}
