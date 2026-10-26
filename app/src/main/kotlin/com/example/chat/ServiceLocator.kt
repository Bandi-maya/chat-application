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

    /** REST backend (ApiBackendService on the api branch): the service the
     *  auth screens actually call; its session takes facade precedence. */
    /** The ApiBackendService under construction. initialize() runs the FCM sync hook synchronously when a session is
     *  stored; fcmRegistrar must reach the service through this field, not through the apiBackend lazy (re-entering
     *  it recursed until StackOverflowError at launch, CI run 40267033954 job 121395203962). */
    @Volatile private var apiBackendUnderConstruction: com.example.chat.data.api.ApiBackendService? = null

    val apiBackend: com.example.chat.data.api.ApiBackendService by lazy {
        com.example.chat.data.api.ApiBackendService(requireContext()).also { service ->
            apiBackendUnderConstruction = service
            // Dart locator wiring for _syncFcmToken / unregisterOnLogout:
            // deferred lambdas so the registrar's lazy init cannot cycle
            // back into this lazy block during construction.
            service.syncFcmTokenHook = { fcmRegistrar.syncTokenOnLogin() }
            service.unregisterFcmHook = { fcmRegistrar.unregisterOnLogout() }
            service.initialize()
        }
    }

    /** FCM token lifecycle (firebase_notification_service.dart port),
     *  installed into FcmRegistrarHolder so the framework-created
     *  ChatyFirebaseMessagingService reaches it. Runtime delivery stays
     *  inert until a real google-services.json lands. */
    val fcmRegistrar: com.example.chat.data.api.FcmTokenRegistrar by lazy {
        com.example.chat.data.api.FcmTokenRegistrar(
            apiDevices = (apiBackendUnderConstruction ?: apiBackend).deviceTokenClient,
        ).also { com.example.chat.notifications.FcmRegistrarHolder.registrar = it }
    }

    val themeController: com.example.chat.core.theme.ThemeController by lazy {
        com.example.chat.core.theme.ThemeController(preferencesStorage, appScope)
    }

    val preferencesStorage: com.example.chat.core.preferences.LocalPreferencesStorage by lazy {
        com.example.chat.core.preferences.LocalPreferencesStorage(requireContext())
    }

    /** Android BiometricPrompt adapter behind LocalLockService (Dart local_auth). */
    val lockBiometrics: com.example.chat.platform.AndroidLockBiometrics by lazy {
        com.example.chat.platform.AndroidLockBiometrics(requireContext())
    }

    val localLockService: com.example.chat.data.services.LocalLockService by lazy {
        com.example.chat.data.services.LocalLockService(secureStore, biometrics = lockBiometrics)
    }

    /** locator.dart TemplateController + AppearanceVariantController, persisted through
     *  LocalPreferencesStorage ('chaty_template_v1') and the same SharedPreferences file. */
    /** Dart locator AppIconController + MethodChannel('chaty/launcher_icon') bridge. */
    val appIconBridge: com.example.chat.ui.settings.appicon.AndroidLauncherIconBridge by lazy {
        com.example.chat.ui.settings.appicon.AndroidLauncherIconBridge(requireContext())
    }
    /** Dart locator<EffectEngine>: one shared engine for the picker sheet and the camera. */
    val effectEngine: com.example.chat.ui.camera.EffectEngine by lazy { com.example.chat.ui.camera.EffectEngine() }
    val nativeCallPip: com.example.chat.data.services.NativeCallPipService by lazy { com.example.chat.data.services.NativeCallPipService() }
    val appIconController: com.example.chat.ui.settings.appicon.AppIconController by lazy {
        com.example.chat.ui.settings.appicon.AppIconController(
            bridge = appIconBridge,
            prefs = com.example.chat.ui.settings.appicon.SharedPrefsAppIconStore(requireContext()),
            processor = com.example.chat.ui.settings.appicon.AndroidCustomIconProcessor(requireContext()),
        )
    }

    val appearanceVariantController: com.example.chat.core.templates.AppearanceVariantController by lazy {
        val sp = requireContext().applicationContext
            .getSharedPreferences("chaty_local_preferences", android.content.Context.MODE_PRIVATE)
        com.example.chat.core.templates.AppearanceVariantController(
            object : com.example.chat.core.templates.AppearanceStore {
                override suspend fun getString(key: String): String? = sp.getString(key, null)
                override suspend fun setString(key: String, value: String): Boolean =
                    sp.edit().putString(key, value).commit()
            },
        )
    }
    val templateController: com.example.chat.core.templates.TemplateController by lazy {
        com.example.chat.core.templates.TemplateController(
            object : com.example.chat.core.templates.TemplateStateStore {
                override suspend fun load(): Map<String, Any?> = preferencesStorage.loadTemplateState()
                override suspend fun save(data: Map<String, Any?>): Boolean = preferencesStorage.saveTemplateState(data)
            },
        )
    }
    val templateRuntime: com.example.chat.core.templates.TemplateRuntime by lazy {
        val adapter = com.example.chat.core.templates.TemplateRuntimeAdapter(
            themeController = themeController,
            preferences = preferencesController,
            appearance = appearanceVariantController,
        )
        com.example.chat.core.templates.TemplateRuntime(theme = adapter, appearance = adapter, preferences = adapter)
    }

    /** locator.dart HiddenVaultService / HiddenVaultController singletons.
     *  Biometric unlock uses LocalLockService.authenticateBiometric. */
    val hiddenVaultService: com.example.chat.ui.vault.HiddenVaultService by lazy {
        com.example.chat.ui.vault.HiddenVaultService(
            lockService = localLockService,
            secureStorage = secureStore,
            authenticateBiometric = { reason -> localLockService.authenticateBiometric(reason) },
        )
    }
    val hiddenVaultController: com.example.chat.ui.vault.HiddenVaultController by lazy {
        com.example.chat.ui.vault.HiddenVaultController(hiddenVaultService)
    }

    /** M8 preferences controller (port of ChatyPreferencesController). Starts
     *  its load + remote reconcile on first reference; referenced from
     *  [init] so startup mirrors the Dart controller's constructor. */
    /** main.dart app-lock state machine (_appLockRequired/_backgroundedAt). */
    val appLock: com.example.chat.data.services.AppLockCoordinator by lazy {
        com.example.chat.data.services.AppLockCoordinator(
            isAuthenticated = { apiBackend.isAuthenticated },
            isLockEnabled = { preferencesController.security.isAppLockEnabled },
            autoLockTimeout = { preferencesController.security.autoLockTimeout },
            isMethodReady = {
                val sec = preferencesController.security
                sec.isAppLockEnabled &&
                    com.example.chat.data.services.ProtectedResourceGate.isMethodConfigured(localLockService, sec.lockMethod)
            },
        )
    }

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

    /** Dart purgeLocalDataForDeletedAccount (profile_actions.dart:217); see AccountDeletionPurge for the order. */
    suspend fun purgeLocalDataForDeletedAccount(userId: String) {
        com.example.chat.data.services.AccountDeletionPurge(
            clearOutbox = { pendingSends.clear(it) },
            purgeMlsIdentity = { mls.purgeLocalIdentityForUser(it) },
            purgeTemporaryFiles = {
                com.example.chat.data.services.AccountDeletionPurge.purgeChatyTemporaryFiles(requireContext().cacheDir)
            },
            deleteSnapshots = { snapshotCache.deleteUser(it) },
            deleteAllSecureStorage = { secureStore.deleteAll() },
        ).purge(userId)
    }

    private val pendingSends: PendingSecureSendStore by lazy { PendingSecureSendStore(snapshotCache) }

    /** Dart NotificationChannelManager (badge scope). Home's conversation source is ApiBackendService
     *  (api.conversations, Dart dataStore.conversations), so the badge reads the same list. The lambda is only
     *  evaluated inside syncUnreadBadgeCount, never at construction. */
    val notificationChannelManager: com.example.chat.data.services.NotificationChannelManager by lazy {
        com.example.chat.data.services.NotificationChannelManager(
            context = requireContext(),
            preferences = preferencesController,
            conversations = { apiBackend.conversations },
        )
    }

    @Volatile private var badgeSyncStarted = false

    /** Dart ctor `dataStore.addListener(syncUnreadBadgeCount)`: explicit post-construction start (AppRoot,
     *  once the locator is ready) - initialize, then subscribe to the api data store. No repository callback,
     *  so there is no lazy-initialisation cycle. */
    fun startBadgeSync() {
        if (badgeSyncStarted) return
        badgeSyncStarted = true
        val m = notificationChannelManager
        m.initialize()
        apiBackend.addListener { m.syncUnreadBadgeCount() }
    }

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
            // Dart backend_service _shouldSendReadReceipts: ghost mode, readReceipts and blue-ticks-after-reply.
            shouldSendReadReceipts = { conversationId ->
                val p = preferencesController
                com.example.chat.ui.core.system.ReadReceiptPolicy.shouldSend(
                    ghostMode = p.home.ghostMode, gbGhost = p.gbBool("yo_want_ghostmode"),
                    readReceipts = p.privacy.readReceipts, blueTicksAfterReply = p.privacy.showBlueTicksAfterReply,
                    myId = currentUserId(),
                    senderIds = conversationsRepository.messagesByChatId[conversationId]?.map { it.senderId },
                )
            },
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

    /** locator.dart:48 registerLazySingleton<DynamicIslandController>. Native island bridge: none (null). */
    val dynamicIslandController: com.example.chat.ui.island.DynamicIslandController by lazy {
        com.example.chat.ui.island.DynamicIslandController(
            nativeBridge = com.example.chat.ui.island.ChatyIslandNativeBridge(requireContext()),
            timerScheduler = com.example.chat.ui.island.IslandTimerScheduler.coroutine(appScope),
            scope = appScope,
        )
    }

    // ── core audio (locator.dart:147-176) ───────────────────────────────

    val audioPlatform: com.example.chat.core.audio.AndroidChatyAudioPlatform by lazy {
        com.example.chat.core.audio.AndroidChatyAudioPlatform(requireContext())
    }
    val audioCapabilityService: com.example.chat.core.audio.AudioCapabilityService by lazy {
        com.example.chat.core.audio.AudioCapabilityService(audioPlatform, appScope)
    }
    val audioPolicyCoordinator: com.example.chat.core.audio.AudioPolicyCoordinator by lazy {
        com.example.chat.core.audio.AudioPolicyCoordinator(audioCapabilityService)
    }
    val coreCallAudioController: com.example.chat.core.audio.CallAudioController by lazy {
        com.example.chat.core.audio.CallAudioController(audioPolicyCoordinator, audioCapabilityService, callSignaling)
    }
    val mediaAudioController: com.example.chat.core.audio.MediaAudioController by lazy {
        com.example.chat.core.audio.MediaAudioController(audioPolicyCoordinator)
    }
    val notificationAudioController: com.example.chat.core.audio.NotificationAudioController by lazy {
        com.example.chat.core.audio.NotificationAudioController(audioPolicyCoordinator)
    }
    val audioSettingsController: com.example.chat.core.audio.AudioSettingsController by lazy {
        com.example.chat.core.audio.AudioSettingsController(audioCapabilityService, audioPolicyCoordinator)
    }

    // ── calls: presentation prefs, signaling, presentation policy, lifecycle ──

    val callPresentationPrefs: com.example.chat.ui.calls.CallPresentationPreferencesStore by lazy {
        com.example.chat.ui.calls.CallPresentationPreferencesStore(
            com.example.chat.ui.calls.AndroidCallPresentationPrefsStorage(requireContext()), appScope)
    }
    val callSignaling: com.example.chat.data.services.CallSignalingService by lazy {
        com.example.chat.data.services.CallSignalingService(
            scope = appScope,
            currentUserId = { apiBackend.currentUser?.id },
            currentUserDisplayName = { apiBackend.currentUser?.displayName ?: "User" },
            currentUserAvatarInitials = { apiBackend.currentUser?.avatarInitials },
            currentUserAvatarColorHex = { apiBackend.currentUser?.avatarColorHex },
            signalSender = com.example.chat.data.services.CallSignalSender { targetUserId, data ->
                apiBackend.sendCallSignal(targetUserId, data)
            },
            mediaFactory = { com.example.chat.data.services.WebRtcCallMediaController(requireContext()) },
            // GAP (no invention): api branch persists terminal call records via
            // dataStore.addCallRecord; no custom-backend call-history endpoint is
            // verified, and CallHistoryService here is Postgrest-backed. Sink is a
            // no-op until the record plane is confirmed.
            callRecordSink = { },
            lowDataUsageEnabled = { callPresentationPrefs.value.lowDataUsageEnabled },
        ).also { svc ->
            apiBackend.addCallSignalListener { data ->
                appScope.launch { svc.handleRemoteSignal(data) }
            }
        }
    }
    val callPresentationController: com.example.chat.ui.calls.CallPresentationController by lazy {
        com.example.chat.ui.calls.CallPresentationController(callSignaling, callPresentationPrefs)
            .also { it.start() }
    }
    val callLifecycle: com.example.chat.data.services.CallLifecycleCoordinator by lazy {
        com.example.chat.data.services.CallLifecycleCoordinator(
            scope = appScope,
            callService = callSignaling,
            foregroundSync = { session -> com.example.chat.data.services.CallForegroundService.sync(requireContext(), session) },
            foregroundStop = { com.example.chat.data.services.CallForegroundService.stop(requireContext()) },
            // W1 realtime has its own reconnect while the socket is alive; no-op.
            reconnectSignaling = { },
        ).also { it.start() }
    }

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

    /** Chat media plane (chat_media_service.dart): custom-backend upload
     *  takes facade precedence inside the service, exactly like Dart's
     *  locator.isRegistered && api.isAuthenticated branch. */
    val chatMedia: com.example.chat.data.services.ChatMediaService by lazy {
        com.example.chat.data.services.ChatMediaService(
            storage = storageClient,
            currentUserId = currentUserId,
            sendImagesFullResolutionMb = {
                preferencesController.universal.sendImagesFullResolutionMb
            },
            gbSizeLimitMb = { key, fallback -> preferencesController.gbDouble(key, fallback) },
            apiAuthenticated = { apiBackend.isAuthenticated },
            apiUpload = { bytes, filename -> apiBackend.uploadMedia(filename, bytes) },
        )
    }

    /** Tasks UI controller over the same REST client as ApiBackendService.loadTasks;
     *  stage history persists under SharedPreferences chaty_task_history_v1. */
    val tasksController: com.example.chat.ui.tasks.TasksController by lazy {
        val prefs = requireContext().getSharedPreferences("chaty_prefs", Context.MODE_PRIVATE)
        com.example.chat.ui.tasks.TasksController(
            client = apiBackend.tasksClient,
            historyStore = object : com.example.chat.ui.tasks.TaskHistoryStore {
                override fun load() = com.example.chat.ui.tasks.TaskHistoryCodec.decode(
                    prefs.getString("chaty_task_history_v1", null))
                override fun save(history: Map<String, List<String>>) {
                    prefs.edit().putString("chaty_task_history_v1",
                        com.example.chat.ui.tasks.TaskHistoryCodec.encode(history)).apply()
                }
            },
        )
    }

    val callHistory: CallHistoryService by lazy {
        CallHistoryService(queryClient = queryClient, currentUserId = currentUserId)
    }

    val gbFeatures: GbFeatureBackendService by lazy {
        GbFeatureBackendService(queryClient = queryClient, rpcClient = rpcClient, currentUserId = currentUserId)
    }

    val scheduledMessageService: com.example.chat.ui.automation.ScheduledMessageService by lazy {
        com.example.chat.ui.automation.ScheduledMessageService(backend = gbFeatures, scope = appScope)
    }

    val autoReplyRuleEngine: com.example.chat.ui.automation.AutoReplyRuleEngine by lazy {
        com.example.chat.ui.automation.AutoReplyRuleEngine(prefs = preferencesController, backend = gbFeatures, scope = appScope,
            sendReply = { cid, text -> apiBackend.sendMessage(cid, text) })
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
                SupabaseRealtimeChannel(supabaseUrl, publishableKey, accessToken, topic, tables, onChange, onStatus).also { it.start() }
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
            preferencesReady = { preferencesController.preferencesReady },
            gbHideStatusView = { preferencesController.gbBool("yoHideStatViewV2") },
            gbAudioStatusEnabled = { preferencesController.gbBool("abu9aleh_status_audio") },
            gbSizeLimitMb = { key, fallback -> preferencesController.gbDouble(key, fallback) },
            statusChannelFactory = { topic, tables, onChange, onStatus ->
                SupabaseRealtimeChannel(supabaseUrl, publishableKey, accessToken, topic, tables, onChange, onStatus).also { it.start() }
            },
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
