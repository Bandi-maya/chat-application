package com.example.chat

import android.content.Context
import com.example.chat.core.crypto.uniffi.UniffiMlsEngine
import com.example.chat.data.auth.AuthRepository
import com.example.chat.data.auth.AuthSessionManager
import com.example.chat.data.auth.SessionStore
import com.example.chat.data.auth.SupabaseAuthClient
import com.example.chat.data.e2ee.MlsE2eeService
import com.example.chat.data.local.LocalSnapshotCacheService
import com.example.chat.data.local.SecureKeyValueStoreImpl
import com.example.chat.data.messaging.ConversationsRepository
import com.example.chat.data.messaging.OutgoingMessageQueueEngine
import com.example.chat.data.messaging.PendingSecureSendStore
import com.example.chat.data.messaging.TasksRepository
import com.example.chat.data.realtime.RealtimeReconciliationService
import com.example.chat.data.realtime.RichChatRealtimeService
import com.example.chat.data.realtime.EventNotificationIcon
import com.example.chat.data.realtime.RealtimeBackend
import com.example.chat.data.realtime.RealtimePreferences
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.data.remote.PostgrestRpcClient
import com.example.chat.data.remote.SupabaseRealtimeChannel
import com.example.chat.data.remote.SupabaseStorageClient
import com.example.chat.data.services.CallHistoryService
import com.example.chat.data.services.ChatyNotificationService
import com.example.chat.data.services.ConnectionHealthService
import com.example.chat.data.services.ContactRelationshipService
import com.example.chat.data.services.GbFeatureBackendService
import com.example.chat.data.services.MessageAutomationService
import com.example.chat.data.services.PresenceService
import com.example.chat.data.services.ProfileService
import com.example.chat.data.services.SessionLifecycleService
import com.example.chat.data.services.SessionSnapshot
import com.example.chat.data.services.StatusService
import com.example.chat.domain.models.MessageAutomationPreferences
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Composition root mirroring lib/injection/locator.dart (api branch) for the
 * services ported so far. Every registration is lazy, like GetIt
 * registerLazySingleton. Seams whose Dart source reads a not-yet-ported
 * controller are wired to that controller's Dart defaults / null path, each
 * documented at the wiring site; no behavior is invented.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    /** Mirrors main.dart's pre-runApp wiring: config init + setupLocator().
     *  Called once from ChatyApplication.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
        // gotrue session restore, mirroring supabase_flutter's initializeAuth
        // which runs during Supabase.initialize before runApp.
        appScope.launch { sessionManager.restore() }
        preferencesController // start load + remote reconcile (Dart controller constructor)
    }

    private fun requireContext(): Context =
        appContext ?: throw IllegalStateException("ServiceLocator.init() must run from ChatyApplication first.")

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── config (dart-define equivalents; defaults match main.dart) ────────

    private val supabaseUrl: String get() = SupabaseAuthClient.SUPABASE_URL
    private val publishableKey: String get() = SupabaseAuthClient.SUPABASE_PUBLISHABLE_KEY

    // ── auth + session (M3) ───────────────────────────────────────────────

    val authClient: SupabaseAuthClient by lazy { SupabaseAuthClient() }

    private val sessionStorage: SessionStore by lazy { SessionStore(requireContext()) }

    val sessionManager: AuthSessionManager by lazy {
        AuthSessionManager(auth = authClient, storage = sessionStorage)
    }

    private val accessToken: suspend () -> String? = { sessionManager.accessToken() }
    private val currentUserId: () -> String? = { sessionManager.currentUserId() }

    val authRepository: AuthRepository by lazy {
        AuthRepository(auth = authClient, sessions = sessionStorage, rpc = rpcClient, sessionManager = sessionManager)
    }

    // ── remote clients ────────────────────────────────────────────────────

    val queryClient: PostgrestQueryClient by lazy {
        PostgrestQueryClient(supabaseUrl, publishableKey, accessToken)
    }

    val rpcClient: PostgrestRpcClient by lazy {
        PostgrestRpcClient(supabaseUrl, publishableKey, accessToken)
    }

    val storageClient: SupabaseStorageClient by lazy {
        SupabaseStorageClient(supabaseUrl, publishableKey, accessToken)
    }

    // ── local storage ─────────────────────────────────────────────────────

    private val secureStore: SecureKeyValueStoreImpl by lazy { SecureKeyValueStoreImpl(requireContext()) }

    val themeController: com.example.chat.core.theme.ThemeController by lazy {
        com.example.chat.core.theme.ThemeController(preferencesStorage, appScope)
    }

    val preferencesStorage: com.example.chat.core.preferences.LocalPreferencesStorage by lazy {
        com.example.chat.core.preferences.LocalPreferencesStorage(requireContext())
    }

    val localLockService: com.example.chat.data.services.LocalLockService by lazy {
        com.example.chat.data.services.LocalLockService(secureStore)
    }

    /** M8 preferences controller (port of ChatyPreferencesController). Starts
     *  its load + remote reconcile on first reference; referenced from
     *  [init] so startup mirrors the Dart controller's constructor. */
    val preferencesController: com.example.chat.core.preferences.ChatyPreferencesController by lazy {
        com.example.chat.core.preferences.ChatyPreferencesController(
            store = AndroidPreferencesBlobStore(preferencesStorage),
            remote = SupabaseRemoteSettingsStore(queryClient),
            lockStore = localLockService,
            auth = SessionAuthSource(sessionManager),
            scope = appScope,
        )
    }

    private val snapshotCache: LocalSnapshotCacheService by lazy { LocalSnapshotCacheService(requireContext()) }

    // ── core services (locator.dart order) ────────────────────────────────

    val connectionHealth: ConnectionHealthService by lazy {
        ConnectionHealthService(
            // Dart: _client.from('profiles').select('id').limit(1)
            backendPing = { queryClient.from("profiles").select("id").limit(1).execute() },
        )
    }

    val mls: MlsE2eeService by lazy {
        MlsE2eeService(
            rpc = rpcClient,
            secureStorage = secureStore,
            currentAuthUserId = currentUserId,
            dbPathForUser = { safeUser -> requireContext().filesDir.absolutePath + "/mls" },
        )
    }

    private val pendingSends: PendingSecureSendStore by lazy { PendingSecureSendStore(snapshotCache) }

    val conversationsRepository: ConversationsRepository by lazy {
        ConversationsRepository(
            rpc = rpcClient,
            queryClient = queryClient,
            mls = { mls },
            currentUserId = currentUserId,
            pendingStore = pendingSends,
            onQueuedCountChanged = { count -> connectionHealth.updateQueuedCount(count) },
            onPendingEnqueued = { appScope.launch { outgoingQueue.processQueue() } },
            snapshotCache = snapshotCache,
            snapshotScope = appScope,
        )
    }

    val tasksRepository: TasksRepository by lazy {
        TasksRepository(
            rpc = rpcClient,
            queryClient = queryClient,
            refreshConversations = { conversationsRepository.loadConversations(loadMembers = true) },
            refreshMessagesIfLoaded = { conversationId ->
                if (conversationsRepository.messagesByChatId.containsKey(conversationId)) {
                    conversationsRepository.loadMessages(conversationId)
                }
            },
        )
    }

    val outgoingQueue: OutgoingMessageQueueEngine by lazy {
        OutgoingMessageQueueEngine(
            store = pendingSends,
            healthService = connectionHealth,
            deliver = { pending -> conversationsRepository.retryPendingSecureMessage(pending); Unit },
            currentUserId = currentUserId,
        )
    }

    val notifications: ChatyNotificationService by lazy { ChatyNotificationService() }

    val presenceService: PresenceService by lazy {
        PresenceService(
            queryClient = queryClient,
            currentUserId = currentUserId,
            privacy = { preferencesController.privacy.toPresenceSnapshot() },
        )
    }

    val profileService: ProfileService by lazy {
        ProfileService(
            queryClient = queryClient,
            conversationsRepository = conversationsRepository,
            tasksRepository = tasksRepository,
            presenceService = presenceService,
            currentAuthUser = { sessionManager.currentAuthUser() },
            privacy = { preferencesController.privacy.toPresenceSnapshot() },
            scope = appScope,
            mls = mls,
        )
    }

    val contactRelationships: ContactRelationshipService by lazy {
        ContactRelationshipService(
            queryClient = queryClient,
            rpc = rpcClient,
            currentUserId = currentUserId,
            secureStore = object : com.example.chat.data.services.SecureValueStore {
                override suspend fun read(key: String): String? = secureStore.read(key)
                override suspend fun write(key: String, value: String) = secureStore.write(key, value)
            },
        )
    }

    val callHistory: CallHistoryService by lazy {
        CallHistoryService(queryClient = queryClient, currentUserId = currentUserId)
    }

    val gbFeatures: GbFeatureBackendService by lazy {
        GbFeatureBackendService(queryClient = queryClient, rpcClient = rpcClient, currentUserId = currentUserId)
    }

    val messageAutomation: MessageAutomationService by lazy {
        MessageAutomationService(
            automation = { preferencesController.automation },
            currentUserId = currentUserId,
            backend = gbFeatures,
            scope = appScope,
            addListener = { l -> preferencesController.addListener(l) },
            removeListener = { l -> preferencesController.removeListener(l) },
        )
    }

    val richRealtime: RichChatRealtimeService by lazy {
        RichChatRealtimeService(
            preferences = ControllerRealtimePreferences(preferencesController),
            notifications = EventNotificationSinkAdapter(notifications),
            backend = object : RealtimeBackend {
                override fun getUserById(userId: String) =
                    conversationsRepository.usersById[userId]
                        ?: profileService.currentUser?.takeIf { it.id == userId }
                override fun isConversationMuted(conversationId: String) =
                    conversationsRepository.conversationsById[conversationId]?.isMuted == true
            },
            query = queryClient,
            rpc = rpcClient,
            channelFactory = { topic, tables, onChange, onStatus ->
                SupabaseRealtimeChannel(supabaseUrl, publishableKey, accessToken, topic, tables, onChange, onStatus)
            },
            currentUserId = currentUserId,
            connectionHealth = ConnectionHealthSinkAdapter(connectionHealth),
        )
    }

    val realtimeReconciliation: RealtimeReconciliationService by lazy {
        RealtimeReconciliationService(
            supabaseUrl = supabaseUrl,
            publishableKey = publishableKey,
            accessToken = accessToken,
            currentUserId = currentUserId,
            hasConversation = { id -> conversationsRepository.conversationsById.containsKey(id) },
            hasLoadedMessages = { id -> conversationsRepository.messagesByChatId.containsKey(id) },
            conversationIdForLoadedMessage = { messageId ->
                conversationsRepository.messagesByChatId.entries
                    .firstOrNull { (_, msgs) -> msgs.any { it.id == messageId } }?.key
            },
            loadConversations = { loadMembers -> conversationsRepository.loadConversations(loadMembers) },
            loadTasks = { tasksRepository.loadTasks() },
            loadConversationMembers = { id -> conversationsRepository.loadConversationMembers(id) },
            loadMessages = { id -> conversationsRepository.loadMessages(id) },
            hydrateFallback = {
                sessionLifecycle.currentSessionSnapshot()?.let { sessionLifecycle.refreshAuthenticatedSession(it) }
            },
            kickPendingSends = { appScope.launch { outgoingQueue.processQueue() } },
        )
    }

    val sessionLifecycle: SessionLifecycleService by lazy {
        SessionLifecycleService(
            snapshotCache = snapshotCache,
            conversationsRepository = conversationsRepository,
            tasksRepository = tasksRepository,
            profileService = profileService,
            realtime = realtimeReconciliation,
            presenceService = presenceService,
            scope = appScope,
            mls = mls,
            authSignOut = { sessionManager.signOut() },
            kickPendingSends = { appScope.launch { outgoingQueue.processQueue() } },
        )
    }

    val statusService: StatusService by lazy {
        StatusService(
            queryClient = queryClient,
            storage = storageClient,
            rpc = rpcClient,
            currentUserId = currentUserId,
            privacy = { preferencesController.privacy.toPublicationSnapshot() },
        )
    }

    /** Mirrors backend_service.dart's onAuthStateChange wiring: the auth
     *  event stream feeds SessionLifecycleService.initialize as a Flow of
     *  GoTrue session snapshots (its Dart-faithful API), and the rich
     *  realtime service observes the same transitions. Call once when the UI
     *  layer is up (Dart does this in ChatyDataStore.start()). */
    suspend fun startSessionLifecycle() {
        sessionLifecycle.currentSessionSnapshot = { currentSessionSnapshot() }
        val snapshots = callbackFlow {
            val listener: (com.example.chat.data.auth.AuthChangeEvent, AuthSessionManager.ActiveSession?) -> Unit =
                { _, _ -> trySend(currentSessionSnapshot()) }
            sessionManager.addAuthStateListener(listener)
            awaitClose { sessionManager.removeAuthStateListener(listener) }
        }
        sessionManager.addAuthStateListener { _, session ->
            richRealtime.onSessionChanged(session?.user?.id)
        }
        sessionLifecycle.initialize(snapshots)
    }

    private fun currentSessionSnapshot(): SessionSnapshot? =
        sessionManager.currentSession?.let { s ->
            SessionSnapshot(
                userId = s.user.id,
                accessToken = s.accessToken,
                expiresAtSeconds = s.expiresAtEpochMs / 1000,
                email = s.user.email ?: "",
                phone = s.user.phone ?: "",
                userMetadata = s.user.userMetadata?.entries?.associate { (k, v) -> k to v.toString().trim('"') }
                    ?: emptyMap(),
            )
        }
}

private class EventNotificationSinkAdapter(
    private val service: ChatyNotificationService,
) : com.example.chat.data.realtime.EventNotificationSink {
    override fun triggerEventNotification(
        title: String,
        body: String,
        icon: EventNotificationIcon,
        colorArgb: Long,
        textColorArgb: Long?,
        userId: String?,
        avatarInitials: String?,
        avatarColorHex: String?,
    ) = service.triggerEventNotification(title, body, icon, colorArgb, textColorArgb, userId, avatarInitials, avatarColorHex)
}

private class ConnectionHealthSinkAdapter(
    private val service: ConnectionHealthService,
) : com.example.chat.data.realtime.ConnectionHealthSink {
    override fun notifyRealtimeStatus(status: com.example.chat.domain.models.RealtimeSocketStatus) =
        service.notifyRealtimeStatus(status)
}
