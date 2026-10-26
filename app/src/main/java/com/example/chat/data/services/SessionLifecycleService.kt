package com.example.chat.data.services

import com.example.chat.data.e2ee.MlsE2eeService
import com.example.chat.data.messaging.SnapshotCacheStore
import com.example.chat.data.local.SnapshotCodec
import com.example.chat.data.messaging.ConversationsRepository
import com.example.chat.data.messaging.TasksRepository
import com.example.chat.data.realtime.RealtimeReconciliationService
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** GoTrue session fields _handleSession / _mapSession read. */
data class SessionSnapshot(
    val userId: String,
    val accessToken: String,
    /** Seconds since epoch, as GoTrue expires_at; null when absent. */
    val expiresAtSeconds: Long?,
    val email: String = "",
    val phone: String = "",
    val userMetadata: Map<String, Any?> = emptyMap(),
)

/** Ported AuthSession shape produced by _mapSession. */
data class AuthSessionInfo(
    val userId: String,
    val token: String,
    val expiresAt: Instant,
    val deviceId: String,
)

/**
 * Exact port of backend_service.dart _handleSession / _hydrateCachedState /
 * _refreshAuthenticatedSession / _mapSession / logout.
 *
 * Offline-first order is preserved: teardown on null session; otherwise a
 * metadata fallback profile, snapshot hydration (paint before network),
 * unawaited network catch-up, then realtime subscription. Demo mode does not
 * exist on api and is not ported.
 */
class SessionLifecycleService(
    private val snapshotCache: SnapshotCacheStore,
    private val conversationsRepository: ConversationsRepository,
    private val tasksRepository: TasksRepository?,
    private val profileService: ProfileService,
    private val realtime: RealtimeReconciliationService,
    private val presenceService: PresenceService,
    private val scope: CoroutineScope,
    private val mls: MlsE2eeService? = null,
    /** _client.auth.signOut (GoTrue). */
    private val authSignOut: suspend () -> Unit,
    /** unawaited OutgoingMessageQueueEngine.processQueue /
     *  _retryPendingSecureSends kick after catch-up. */
    private val kickPendingSends: () -> Unit = {},
    private val notifyChanged: () -> Unit = {},
    /** Runs when the session becomes null (logout/expiry); e.g. purge decrypted media temps. Must not throw. */
    private val onSignedOut: () -> Unit = {},
) {
    var currentSession: AuthSessionInfo? = null
        private set

    @Volatile
    var isInitialized = false
        private set

    private val initializeMutex = kotlinx.coroutines.sync.Mutex()
    private var initializeAttempted = false

    /** Exact port of initialize()/_initialize(): one shared attempt, an
     *  auth-state-change subscription feeding handleSession, then the
     *  current-session path. A failed attempt resets so a later caller can
     *  retry (Dart clears _initializeFuture on error). The demo-mode branch
     *  does not exist on api and is not ported. */
    suspend fun initialize(authStateChanges: kotlinx.coroutines.flow.Flow<SessionSnapshot?>) {
        if (isInitialized) return
        initializeMutex.lock()
        try {
            if (isInitialized) return
            try {
                if (!initializeAttempted) {
                    initializeAttempted = true
                    scope.launch {
                        authStateChanges.collect { session -> handleSession(session) }
                    }
                }
                handleSession(currentSessionSnapshot())
                isInitialized = true
                notifyChanged()
            } catch (error: Throwable) {
                initializeAttempted = false
                throw error
            }
        } finally {
            initializeMutex.unlock()
        }
    }

    /** Seam for _client.auth.currentSession at initialize time; wiring
     *  supplies the GoTrue session snapshot. */
    var currentSessionSnapshot: () -> SessionSnapshot? = { null }

    /** Exact port of _handleSession. */
    suspend fun handleSession(session: SessionSnapshot?) {
        if (session == null) {
            realtime.removeRealtimeChannel()
            mls?.close()
            currentSession = null
            conversationsRepository.usersById.clear()
            conversationsRepository.conversationsById.clear()
            conversationsRepository.messagesByChatId.clear()
            tasksRepository?.tasks?.clear()
            runCatching { onSignedOut() }
            notifyChanged()
            return
        }

        val previousUserId = currentSession?.userId
        currentSession = mapSession(session)
        // Non-null -> different non-null account: revoke the previous account's decrypted temps/leases too.
        if (previousUserId != null && previousUserId != session.userId) runCatching { onSignedOut() }
        val current = profileService.currentUser
        if (current == null || current.id != session.userId) {
            val emailOrNull = session.email.ifEmpty { null }
            val fallback = UserProfile(
                id = session.userId,
                displayName = session.userMetadata["display_name"]?.toString()
                    ?: emailOrNull?.split('@')?.first()
                    ?: "Chaty User",
                username = session.userMetadata["username"]?.toString() ?: "user",
                avatarInitials = ProfileService.initials(emailOrNull ?: "CU"),
                avatarColorHex = "0xFF6366F1",
                about = "",
                presence = PresenceState.online,
                lastSeenAt = Instant.now(),
                isVerified = false,
                email = session.email,
                phone = session.phone,
                safetyNumber = "",
            )
            conversationsRepository.usersById[fallback.id] = fallback
        }
        // Offline-first: paint the shell from encrypted local snapshots before
        // any network work, then refresh from the backend without blocking.
        hydrateCachedState(session.userId)
        notifyChanged()
        scope.launch { refreshAuthenticatedSession(session) }
        realtime.subscribeRealtime()
    }

    /** Exact port of _hydrateCachedState; a corrupt or missing snapshot is
     *  skipped, never fatal. */
    suspend fun hydrateCachedState(userId: String) {
        try {
            val cachedConversations = snapshotCache.readJson(
                userId = userId, scope = "conversations",
            )
            if (cachedConversations is List<*>) {
                for (item in cachedConversations) {
                    if (item !is Map<*, *>) continue
                    @Suppress("UNCHECKED_CAST")
                    val conversation = SnapshotCodec.conversationFromJson(
                        item.entries.associate { (k, v) -> k.toString() to v },
                    )
                    if (conversation.id.isEmpty()) continue
                    conversationsRepository.conversationsById[conversation.id] = conversation
                }
            }
            val hydratedIds = conversationsRepository.conversationsById.keys.toList()
            coroutineScope {
                hydratedIds.map { conversationId ->
                    launch {
                        val cachedMessages = snapshotCache.readJson(
                            userId = userId, scope = "messages_$conversationId",
                        )
                        if (cachedMessages !is List<*> || cachedMessages.isEmpty()) return@launch
                        val messages = cachedMessages.mapNotNull { item ->
                            if (item !is Map<*, *>) return@mapNotNull null
                            SnapshotCodec.chatMessageFromJson(
                                item.entries.associate { (k, v) -> k.toString() to v },
                            )
                        }
                        conversationsRepository.messagesByChatId[conversationId] =
                            messages.toMutableList()
                    }
                }.forEach { it.join() }
            }
        } catch (error: Throwable) {
            // Dart logs "snapshot hydration skipped" and continues.
        }
    }

    /** Exact port of _refreshAuthenticatedSession. */
    suspend fun refreshAuthenticatedSession(session: SessionSnapshot) {
        profileService.hydrateAuthenticatedState()
        kickPendingSends()
    }

    /** Exact port of _mapSession (1h default expiry, device_<id prefix>). */
    fun mapSession(session: SessionSnapshot): AuthSessionInfo {
        val expiresSeconds = session.expiresAtSeconds
            ?: (System.currentTimeMillis() + 3_600_000L) / 1000L
        return AuthSessionInfo(
            userId = session.userId,
            token = session.accessToken,
            expiresAt = Instant.ofEpochSecond(expiresSeconds),
            deviceId = "device_${session.userId.substring(0, minOf(8, session.userId.length))}",
        )
    }

    /** Exact port of logout's non-demo path: best-effort offline presence,
     *  MLS teardown, auth sign-out, then the null-session path. */
    suspend fun logout() {
        runCatching { presenceService.setPresence(PresenceState.offline) }
        mls?.close()
        authSignOut()
        handleSession(null)
    }
}
