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
        appTrafficMonitor.start(appScope)
        com.example.chat.core.theme.ThemePresets.platformIsLightProvider = {
            (requireContext().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        // gotrue session restore, mirroring supabase_flutter's initializeAuth
        // which runs during Supabase.initialize before runApp.
        appScope.launch { sessionManager.restore() }
        // Decrypted media temps never survive a process restart.
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { mediaCache.purgeTransientTemps() } }
        preferencesController // start load + remote reconcile (Dart controller constructor)
        notificationCoordinator.start()
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
    /** One monitor for the process (created lazily with the single apiBackend). */
    val networkMonitor: com.example.chat.data.network.NetworkStatusMonitor by lazy {
        com.example.chat.data.network.NetworkStatusMonitor(requireContext())
    }

    /** Persistent byte-accurate upload/download totals and live transfer snapshots. */
    val transferMonitor: com.example.chat.data.network.TransferMonitor by lazy {
        com.example.chat.data.network.TransferMonitor(requireContext())
    }

    /** Android per-UID byte counters include realtime sockets and protocol overhead. */
    val appTrafficMonitor: com.example.chat.data.network.AppTrafficMonitor by lazy {
        com.example.chat.data.network.AppTrafficMonitor(requireContext())
    }

    /** Runs once, from the apiBackend lazy initializer, so there is exactly one collector. Reconnects the
     *  socket only on an offline -> online transition (validated internet), never disables anything while
     *  offline: a false 'validated' reading just skips the early reconnect and the backoff timer still runs. */
    private fun startNetworkRecovery(service: com.example.chat.data.api.ApiBackendService) {
        networkMonitor.start()
        appScope.launch {
            var wasOnline = networkMonitor.state.value.online
            networkMonitor.state.collect { status ->
                if (status.online && !wasOnline) service.onNetworkRegained()
                wasOnline = status.online
                if (status.online && callSignaling.hasActiveSession) {
                    runCatching {
                        callSignaling.onNetworkStatusChanged(
                            upstreamKbps = status.upstreamKbps,
                            downstreamKbps = status.downstreamKbps,
                        )
                    }.onFailure { android.util.Log.w("ChatyNetwork", "Call adaptation skipped", it) }
                }
            }
        }
    }

    val apiBackend: com.example.chat.data.api.ApiBackendService by lazy {
        com.example.chat.data.api.ApiBackendService(requireContext()).also { service ->
            service.transferMonitor = transferMonitor
            service.onAttachmentsGone = { refs -> invalidateMediaFor(refs) }
            // Offline demo: fail-closed entry probe; a real GoTrue identity blocks demo mode.
            service.demoGoTruePresentHook = { sessionManager.hasAnySession() }
            service.initialize()
            startNetworkRecovery(service)
        }
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
    val effectEngine: com.example.chat.ui.camera.EffectEngine by lazy {
        // Effect favorites survive process death, same SharedPreferences file
        // and LensStringListStore contract as the lens catalog (distinct key).
        val sp = requireContext().applicationContext
            .getSharedPreferences("chaty_camera_lens", android.content.Context.MODE_PRIVATE)
        com.example.chat.ui.camera.EffectEngine(
            prefs = object : com.example.chat.ui.camera.LensStringListStore {
                override fun getStringList(key: String): List<String>? =
                    sp.getString(key, null)?.split('|')?.filter { it.isNotBlank() }
                override fun setStringList(key: String, value: List<String>) {
                    sp.edit().putString(key, value.joinToString("|")).apply()
                }
            },
        )
    }
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
    /** System notifications (messages, incoming call, ongoing-call actions) for the custom-API account plane. */
    val notificationCoordinator: com.example.chat.notifications.ChatyNotificationCoordinator by lazy {
        com.example.chat.notifications.ChatyNotificationCoordinator(
            context = requireContext(), scope = appScope, api = apiBackend, calls = callSignaling,
            prefs = preferencesController, appLock = appLock, channelManager = notificationChannelManager,
            isChatVaultLocked = { id -> hiddenVaultService.isConversationLocked(id) },
        )
    }

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
            // Custom backend health endpoint. (The Supabase 'profiles' ping measured a backend this
            // build does not sign in to, so the banner reported the wrong server.)
            backendPing = { apiBackend.pingBackend() },
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
            purgeMediaCache = { mediaCache.deleteAccount(it) },
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
    @Volatile private var callNotificationsStarted = false

    /** App moved between foreground and background: the coordinator (single owner of call/message
     *  system alerts) re-evaluates the live call - withdraws the full-screen alert while the in-app
     *  call UI is visible, re-posts it when backgrounded. Message alerts need no re-evaluation
     *  (per-event gating). Called from ChatyApplication's lifecycle hook. */
    fun refreshCallAlert() { runCatching { notificationCoordinator.refreshCallState() } }

    /** Badge/channel sync: initialize the notification channel manager once the locator is ready,
     *  then keep the unread badge in step with the api data store (pinned behavior; AppRoot calls this). */
    fun startBadgeSync() {
        if (badgeSyncStarted) return
        badgeSyncStarted = true
        val m = notificationChannelManager
        m.initialize()
        apiBackend.addListener { m.syncUnreadBadgeCount() }
        startPrivacySync()
    }

    /**
     * System notifications while the app is in the background or closed.
     * Socket-driven call ringing and message alerts are owned by the privacy-aware
     * ChatyNotificationCoordinator (started once in [init]); this only ensures the
     * FCM path's channels exist when a push arrives in a cold process, keeps the
     * active-call foreground service in sync, and wires read-sync cancellation for
     * FCM-posted message alerts. Idempotent; called from ChatyApplication and the
     * FCM service.
     */
    fun startCallNotifications() {
        if (callNotificationsStarted) return
        callNotificationsStarted = true
        val ctx = requireContext()
        com.example.chat.notifications.ChatyNotifications.ensureChannels(ctx)
        callLifecycle // keeps the active-call foreground service in sync with the session
        apiBackend.onConversationRead = { roomId ->
            com.example.chat.notifications.ChatyNotifications.cancelMessages(ctx, roomId)
        }
    }

    /** Keeps the backend's chat privacy settings (who sees last seen/online, read receipts,
     *  typing) in line with the Privacy Center. The server enforces them for other users. */
    private fun startPrivacySync() {
        val sync: () -> Unit = {
            runCatching {
                val p = preferencesController
                val ghost = p.home.ghostMode || p.gbBool("yo_want_ghostmode")
                val privacy = p.privacy
                apiBackend.syncChatPrivacy(
                    mapOf(
                        "freeze_last_seen" to privacy.freezeLastSeen,
                        "who_can_see_last_seen" to when (privacy.hideLastSeenAudience) {
                            "Everyone" -> "everyone"
                            "Nobody" -> "nobody"
                            else -> "contacts"
                        },
                        "who_can_see_online" to
                            if (privacy.hideOnlineAudience == "Same as Last Seen") "same_as_last_seen" else "everyone",
                        "read_receipts_enabled" to (privacy.readReceipts && !ghost),
                        "blue_ticks_after_reply" to privacy.showBlueTicksAfterReply,
                        "typing_indicators_enabled" to (privacy.typingIndicators && !ghost),
                    ),
                )
            }
        }
        preferencesController.addListener(sync)
        apiBackend.addListener(sync)
        sync()
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
            scopeSnapshot = { apiBackend.authSnapshot() },
            withScope = { expected, action ->
                (expected as? com.example.chat.data.api.ApiAccountScope)
                    ?.let { apiBackend.withAuthScope(it, action) } ?: false
            },
            onAttachmentsGone = { refs -> invalidateMediaFor(refs) },
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
            mediaFactory = {
                val uploadId = transferMonitor.begin(
                    com.example.chat.data.network.TransferDirection.upload,
                    com.example.chat.data.network.TransferKind.call,
                    null,
                    "Live call",
                )
                val downloadId = transferMonitor.begin(
                    com.example.chat.data.network.TransferDirection.download,
                    com.example.chat.data.network.TransferKind.call,
                    null,
                    "Live call",
                )
                com.example.chat.data.services.WebRtcCallMediaController(requireContext()).also { media ->
                    media.onTrafficBytes = { uploaded, downloaded ->
                        transferMonitor.update(uploadId, uploaded)
                        transferMonitor.update(downloadId, downloaded)
                    }
                    media.onNetworkMetrics = { measuredUpKbps, lossPercent, rttMs ->
                        appScope.launch {
                            val link = networkMonitor.state.value
                            callSignaling.onNetworkStatusChanged(
                                upstreamKbps = if (measuredUpKbps > 0) measuredUpKbps else link.upstreamKbps,
                                downstreamKbps = link.downstreamKbps,
                                packetLossPercent = lossPercent,
                                roundTripTimeMs = rttMs,
                            )
                        }
                    }
                    media.onTrafficEnded = {
                        transferMonitor.finish(uploadId)
                        transferMonitor.finish(downloadId)
                    }
                }
            },
            // GAP (no invention): api branch persists terminal call records via
            // dataStore.addCallRecord; no custom-backend call-history endpoint is
            // verified, and CallHistoryService here is Postgrest-backed. Sink is a
            // no-op until the record plane is confirmed.
            callRecordSink = { },
            lowDataUsageEnabled = { callPresentationPrefs.value.lowDataUsageEnabled },
            captureAccountScope = { apiBackend.authSnapshot() },
            accountScopeValid = { expected -> apiBackend.authSnapshot() === expected },
            accountScopeGuard = { expected, action ->
                (expected as? com.example.chat.data.api.ApiAccountScope)?.let { apiBackend.withAuthScope(it, action) } ?: false
            },
        ).also { svc ->
            // Token refresh waits while a call is active (a refresh is an account-scope transition).
            apiBackend.isCallActive = { svc.hasActiveSession }
            apiBackend.addAuthScopeListener { next ->
                // Callback only detaches state under auth lock; disposal owns
                // the captured old peer and runs outside that lock.
                val cleanup = svc.detachForAccountTransition(next)
                if (cleanup != null) appScope.launch { cleanup() }
            }
            apiBackend.addScopedCallSignalListener { expected, data ->
                appScope.launch {
                    svc.handleRemoteSignal(data,
                        validScope = { apiBackend.authSnapshot() === expected },
                        guardScope = { action -> apiBackend.withAuthScope(expected, action) })
                }
            }
        }
    }
    /** Native direct file transfer over a dedicated WebRTC DataChannel. */
    val p2pFileTransfers: com.example.chat.data.transfer.P2pFileTransferService by lazy {
        com.example.chat.data.transfer.P2pFileTransferService(
            context = requireContext(),
            scope = appScope,
            currentUserId = { apiBackend.currentUser?.id },
            sendSignal = { targetUserId, data -> apiBackend.sendCallSignal(targetUserId, data); true },
            transferMonitor = transferMonitor,
        ).also { service ->
            apiBackend.addScopedCallSignalListener { expected, data ->
                if (data["signal_type"]?.toString()?.startsWith("file_transfer_") == true) {
                    appScope.launch {
                        if (apiBackend.authSnapshot() === expected) {
                            runCatching { service.handleRemoteSignal(data) }
                                .onFailure { android.util.Log.w("ChatyP2P", "Transfer signal rejected", it) }
                        }
                    }
                }
            }
            apiBackend.addAuthScopeListener { _ -> service.disposeAll() }
        }
    }

    val callPresentationController: com.example.chat.ui.calls.CallPresentationController by lazy {
        com.example.chat.ui.calls.CallPresentationController(callSignaling, callPresentationPrefs)
            .also { it.start() }
    }
    // Identity of THIS wiring owner for foreground-service start/stop
    // authority: stop couriers name the session this owner is tearing down,
    // never a process-global current value, so a stale teardown from an old
    // locator/coordinator generation cannot kill a newer call's service.
    private val callForegroundOwnerToken: String = "callfg-" + java.util.UUID.randomUUID().toString().substring(0, 8)
    val callLifecycle: com.example.chat.data.services.CallLifecycleCoordinator by lazy {
        com.example.chat.data.services.CallLifecycleCoordinator(
            scope = appScope,
            callService = callSignaling,
            foregroundSync = { session -> com.example.chat.data.services.CallForegroundService.sync(requireContext(), session, callForegroundOwnerToken) },
            foregroundStop = { com.example.chat.data.services.CallForegroundService.stop(requireContext(), callForegroundOwnerToken, null) },
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
            imageCompressor = { bytes, quality ->
                com.example.chat.ui.media.ImageSendCompressor.compress(bytes, quality)
            },
            adaptiveImageQuality = {
                when (networkMonitor.state.value.upstreamKbps) {
                    in 1..80 -> 45
                    in 81..250 -> 60
                    in 251..700 -> 78
                    in 701..Int.MAX_VALUE -> 85
                    else -> 65
                }
            },
            apiAuthenticated = { apiBackend.isAuthenticated },
            apiUpload = { bytes, filename, onProgress ->
                apiBackend.uploadMedia(
                    filename,
                    bytes,
                    expectedScope = kotlin.coroutines.coroutineContext[com.example.chat.data.api.ApiScopeElement]?.scope,
                    onProgress = onProgress,
                )
            },
            transferMonitor = transferMonitor,
        )
    }

    /** Tasks UI controller over the same REST client as ApiBackendService.loadTasks;
     *  stage history persists under SharedPreferences chaty_task_history_v1. */
    val tasksController: com.example.chat.ui.tasks.TasksController by lazy {
        val prefs = requireContext().getSharedPreferences("chaty_prefs", Context.MODE_PRIVATE)
        com.example.chat.ui.tasks.TasksController(
            client = apiBackend.tasksClient,
            accountKey = { apiBackend.currentUser?.id },
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

    /** Encrypted, account-scoped local store for media the user opened/downloaded. Limit = user setting
     *  (500 MB / 1 GB default / 2 GB); per-entry cap = the existing 50 MB media limit. Decrypted temps from a
     *  previous process are purged on first use. */
    val mediaCache: com.example.chat.data.local.AttachmentFileCache by lazy {
        com.example.chat.data.local.AttachmentFileCache(
            context = requireContext(),
            // Same immutable custom-API account/routing scope that owns these messages. No Supabase fallback.
            identity = {
                val account = apiBackend.authSnapshot()
                account.userId?.takeIf { it.isNotBlank() && !account.token.isNullOrBlank() }?.let {
                    com.example.chat.data.local.SessionEpoch.Identity(account.tenantId, it, account.generation)
                }
            },
            maxBytes = { com.example.chat.data.local.MediaCacheSettings.limitBytes(requireContext()) },
            maxEntryBytes = com.example.chat.data.services.ChatMediaService.STORAGE_LIMIT_MB * 1024L * 1024L,
            exportPolicy = { ref -> trustedAttachmentFor(ref)?.let { !it.viewOnce && !it.restricted } == true },
            editedOutputValidator = com.example.chat.data.media.AndroidEditedOutputValidator,
        ).also { it.purgeTransientTemps() }
    }

    /** Trusted state from the signed-in account's loaded message (never caller claims). Unknown/deleted => null. */
    private fun trustedAttachmentFor(ref: com.example.chat.data.local.AttachmentRef): com.example.chat.data.media.TrustedAttachment? =
        runCatching {
            // Custom-backend sessions keep their messages in ApiBackendService; other paths in the repository.
            val account = apiBackend.authSnapshot()
            var trusted: com.example.chat.data.media.TrustedAttachment? = null
            apiBackend.withAuthScope(account) {
                val m = apiBackend.getMessages(ref.conversationId).firstOrNull { it.id == ref.messageId }
                val attachment = m?.attachment
                if (m != null && attachment != null && attachment.id == ref.attachmentId &&
                    !m.isDeletedForEveryone && !m.isDeletedForMe && !m.isRevokedBySender) {
                    trusted = com.example.chat.data.media.TrustedAttachment(attachment.type,
                        com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(m.metadata), false)
                }
            }
            trusted
        }.getOrNull()

    /** Deleted/removed messages: drop their local media copies off the caller's thread. Never throws. */
    private fun invalidateMediaFor(refs: List<com.example.chat.data.local.AttachmentRef>) {
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { refs.forEach { mediaCache.onAttachmentGone(it) } } }
    }

    /** Resolver for opened/downloaded attachments; consumers decide how to render each ResolvedSource. */
    val attachmentResolver: com.example.chat.data.media.AttachmentSourceResolver by lazy {
        val downloader = com.example.chat.data.media.BoundedMediaDownloader(
            maxBytes = com.example.chat.data.services.ChatMediaService.STORAGE_LIMIT_MB * 1024L * 1024L,
        )
        com.example.chat.data.media.AttachmentSourceResolver(
            store = mediaCache,
            isOnline = { networkMonitor.state.value.online },
            lookup = { ref -> trustedAttachmentFor(ref) },
            download = { url ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val transferId = transferMonitor.begin(
                        com.example.chat.data.network.TransferDirection.download,
                        com.example.chat.data.network.TransferKind.other,
                        null,
                        "Attachment",
                    )
                    try {
                        val source = downloader.open(url) { sent, total ->
                            transferMonitor.update(transferId, sent, total)
                        }
                        object : java.io.FilterInputStream(source) {
                            private var reachedEof = false
                            override fun read(): Int = super.read().also { if (it < 0) reachedEof = true }
                            override fun read(b: ByteArray, off: Int, len: Int): Int =
                                super.read(b, off, len).also { if (it < 0) reachedEof = true }
                            override fun close() {
                                try { super.close() } finally {
                                    transferMonitor.finish(
                                        transferId,
                                        if (reachedEof) com.example.chat.data.network.TransferStatus.completed
                                        else com.example.chat.data.network.TransferStatus.failed,
                                    )
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        transferMonitor.finish(transferId, com.example.chat.data.network.TransferStatus.failed)
                        throw error
                    }
                }
            },
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
            onSignedOut = { mediaCache.purgeTransientTemps() },
            apiScopeIdentity = { apiBackend.authSnapshot() },
            onAccountSwitch = { apiBackend.clearChatCachesForAccountSwitch() },
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
            if (session != null) {
                // A real GoTrue adoption is a real-adopt boundary too: demo
                // residue must not survive it (mirrors REST afterAuthAdopted).
                apiBackend.purgeDemoResidueForRealAdopt()
            }
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
