package com.example.chat

import android.content.Context
import com.example.chat.core.crypto.uniffi.UniffiMlsEngine
import com.example.chat.data.auth.AuthSessionManager
import com.example.chat.data.auth.EncryptedGoTrueSessionStorage
import com.example.chat.data.auth.SessionStore
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

    /** R5 in-app chat font size (approved responsive plan 8 Nov 2026):
     *  Small 0.85 / Default 1.0 / Large 1.15, applied to chat message text
     *  and timestamps only. Stored locally in chaty_prefs; instant. */
    val chatFontScale = androidx.compose.runtime.mutableStateOf(1.0f)

    fun loadChatFontScale() {
        val v = requireContext().getSharedPreferences("chaty_prefs", Context.MODE_PRIVATE)
            .getFloat("chat_font_scale", 1.0f)
        chatFontScale.value = if (v in 0.8f..1.2f) v else 1.0f
    }

    fun setChatFontScale(scale: Float) {
        chatFontScale.value = scale
        requireContext().getSharedPreferences("chaty_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("chat_font_scale", scale).apply()
    }

    /** Mirrors main.dart's pre-runApp wiring: config init + setupLocator().
     *  Called once from ChatyApplication.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
        loadChatFontScale()
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) { appTrafficMonitor.start(appScope) }
        com.example.chat.core.theme.ThemePresets.platformIsLightProvider = {
            (requireContext().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        // gotrue session restore, mirroring supabase_flutter's initializeAuth
        // which runs during Supabase.initialize before runApp.
        apiBackend.initialize()
        appScope.launch { sessionManager.restore() }
        // Decrypted media temps never survive a process restart.
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { mediaCache.purgeTransientTemps() } }
        preferencesController // start load + remote reconcile (Dart controller constructor)
        notificationCoordinator.start()
        com.example.chat.ui.chats.detail.ReadAlertCenter.init(requireContext())
        com.example.chat.ui.chats.detail.ReadAlertCenter.notifier = { key, room, _ -> notificationCoordinator.showReadAlert(key, room) }
        com.example.chat.bubbles.ChatBubbleController.install(context)
        // Always wire FcmRegistrarHolder + PushWakeHolder at process init so early
        // onNewToken / onMessageReceived callbacks never drop before UI launches.
        fcmRegistrar
    }

    /** Defensive bootstrap invoked by background FCM entry points before accessing holders. */
    fun ensurePushReady(context: Context) {
        if (appContext == null) {
            init(context.applicationContext)
        }
        fcmRegistrar
        startCallNotifications()
    }

    private fun requireContext(): Context =
        appContext ?: throw IllegalStateException("ServiceLocator.init() must run from ChatyApplication first.")

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── config (dart-define equivalents; defaults match main.dart) ────────


    // ── auth + session (M3) ───────────────────────────────────────────────

    private val sessionStorage: com.example.chat.data.auth.SessionStorage by lazy { EncryptedGoTrueSessionStorage(requireContext()) }

    val sessionManager: AuthSessionManager by lazy {
        AuthSessionManager(storage = sessionStorage)
    }

    private val currentUserId: () -> String? =
        { apiBackend.currentUser?.id ?: sessionManager.currentUserId() }

    // ── remote clients ────────────────────────────────────────────────────

    /** Fail-closed realtime channel for planes the backend has no
     *  postgres-changes equivalent for: reports `closed` once (honest
     *  disconnected health) and never delivers changes. Catch-up rides
     *  ApiBackendService's websocket + background sync. */
    private fun inertRealtimeChannel(
        onStatus: (com.example.chat.data.remote.RealtimeSubscribeStatus) -> Unit,
    ): com.example.chat.data.remote.RealtimeChannel {
        onStatus(com.example.chat.data.remote.RealtimeSubscribeStatus.closed)
        return object : com.example.chat.data.remote.RealtimeChannel {
            override fun close() {}
        }
    }

    /** Media uploads go to the chat backend (POST /chat/upload); the server
     *  returns absolute public URLs. */
    val storageClient: com.example.chat.data.remote.MediaStorageBackend by lazy {
        com.example.chat.data.api.ApiMediaStorageClient(
            baseUrl = { apiBackend.config.restApiBaseUrl.trimEnd('/').replace(Regex("/api/v[0-9]+$"), "") },
            tenantId = { apiBackend.config.tenantId },
            token = { apiBackend.authSnapshot().token },
        )
    }

    // ── local storage ─────────────────────────────────────────────────────

    private val secureStore: SecureKeyValueStoreImpl by lazy { SecureKeyValueStoreImpl(requireContext()) }

    /** REST backend (ApiBackendService on the api branch): the service the
     *  auth screens actually call; its session takes facade precedence. */
    /** The ApiBackendService under construction. initialize() runs the FCM sync hook synchronously when a session is
     *  stored; fcmRegistrar must reach the service through this field, not through the apiBackend lazy (re-entering
     *  it recursed until StackOverflowError at launch, CI run 40267033954 job 121395203962). */
    @Volatile private var apiBackendUnderConstruction: com.example.chat.data.api.ApiBackendService? = null

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
                // Re-tune active video calls whenever Android reports a network handover
                // or a changed link-bandwidth estimate. Voice remains the priority.
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

    /** Cross-plane per-conversation dispatch lock, shared by the REST outbox
     *  flush, the MLS delivery path, and the secure-channel toggle (see
     *  ConversationDispatchLocks for invariants and lock-order rules). */
    val conversationDispatchLocks = com.example.chat.data.messaging.ConversationDispatchLocks()
        .also { locks ->
            com.example.chat.data.messaging.AttachmentUploadTracker.dispatchLocks = locks
            // Uploads re-read the AUTHORITATIVE flag (persisted-first repo
            // state), never a UI lambda: warm read before the dispatch lock,
            // non-suspending loaded read inside it (see AttachmentUploadTracker).
            com.example.chat.data.messaging.AttachmentUploadTracker.secureFlagProbe =
                com.example.chat.data.messaging.AttachmentUploadTracker.SecureFlagProbe(
                    warmAndRead = { cid -> conversationsRepository.isSecureChannel(cid) },
                    readLoaded = { cid -> conversationsRepository.isSecureChannelLoaded(cid) },
                )
        }

    val apiBackend: com.example.chat.data.api.ApiBackendService by lazy {
        com.example.chat.data.api.ApiBackendService(requireContext()).also { service ->
            apiBackendUnderConstruction = service
            service.deviceIdentity = com.example.chat.data.api.DeviceIdentityManager(secureStore)
            service.configureSnapshotCache(snapshotCache)
            service.transferMonitor = transferMonitor
            service.conversationDispatchLocks = conversationDispatchLocks
            // Dart locator wiring for _syncFcmToken / unregisterOnLogout:
            // deferred lambdas so the registrar's lazy init cannot cycle
            // back into this lazy block during construction.
            service.onAttachmentsGone = { refs -> invalidateMediaFor(refs) }
            service.secureMessageIngest = { data ->
                appScope.launch {
                    runCatching { conversationsRepository.ingestSecureEnvelope(data) }
                }
            }
            service.secureMessageEditIngest = { data ->
                appScope.launch {
                    runCatching { conversationsRepository.applySecureEdit(data) }
                }
            }
            service.secureMessageDeleteIngest = { data ->
                appScope.launch {
                    runCatching { conversationsRepository.applyRemoteDeleteForEveryone(data) }
                }
            }
            service.onDurableCallInboxWake = { owner ->
                if (service.authSnapshot() === owner) service.multiAccount.activeAccountMeta()?.let { meta ->
                    owner.token?.let { token -> consumeAccountCallInbox(meta, token) }
                }
            }
            service.syncFcmTokenHook = { fcmRegistrar.syncTokenOnLogin() }
            service.retryFcmTokenHook = { fcmRegistrar.retryIfTokenMissing() }
            service.unregisterFcmHook = { fcmRegistrar.unregisterOnLogout() }
            // Offline demo: fail-closed entry probe; a real GoTrue identity blocks demo mode.
            service.demoGoTruePresentHook = { sessionManager.hasAnySession() }
            service.isConversationProtectedHook = { convId ->
                preferencesController.isConversationHidden(convId) ||
                    preferencesController.isConversationLocked(convId) ||
                    hiddenVaultService.isConversationLocked(convId)
            }
            service.onBeforeAccountSwitch = { oldHash, newHash ->
                conversationsRepository.resetForAccountSwitch()
                tasksRepository.clearLocalForSwitch()
                runCatching { mls.close() }
                runCatching { mediaCache.purgeTransientTemps() }
                localLockService.setAccountNamespace(newHash.orEmpty())
                hiddenVaultService.resetForAccountSwitchSync(newHash.orEmpty())
                if (!oldHash.isNullOrBlank()) {
                    appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { secureMessageStore.closeNamespace(oldHash) }
                    }
                }
            }
            service.onAfterAccountSwitch = { _, newHash ->
                val isFirstOrSingle = service.multiAccount.accounts().size <= 1
                if (!newHash.isNullOrBlank()) {
                    runCatching { hiddenVaultService.switchAccountNamespace(newHash, migrateLegacyIfEmpty = isFirstOrSingle) }
                }
            }
            service.initialize()
            service.currentAccountHash()?.takeIf { it.isNotBlank() }?.let { activeHash ->
                localLockService.setAccountNamespace(activeHash)
                hiddenVaultService.resetForAccountSwitchSync(activeHash)
                appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        hiddenVaultService.switchAccountNamespace(
                            activeHash,
                            migrateLegacyIfEmpty = service.multiAccount.accounts().size <= 1,
                        )
                    }
                }
            }
            startNetworkRecovery(service)
        }
    }

    /** FCM token lifecycle (firebase_notification_service.dart port),
     *  installed into FcmRegistrarHolder so the framework-created
     *  ChatyFirebaseMessagingService reaches it. Runtime delivery stays
     *  inert until a real google-services.json lands. */
    val fcmRegistrar: com.example.chat.data.api.FcmTokenRegistrar by lazy {
        com.example.chat.data.api.FcmTokenRegistrar(
            apiDevices = (apiBackendUnderConstruction ?: apiBackend).deviceTokenClient,
            log = { android.util.Log.d("FcmTokenRegistrar", it) },
            runAsync = { block -> Thread(block, "fcm-register").start() },
            registerRetainedAccounts = { token -> appScope.launch(kotlinx.coroutines.Dispatchers.IO) { accountPushBindings.registerRetained(token) } },
            // Dart FirebaseMessaging.getToken(); throws IllegalStateException while FirebaseApp is
            // not initialised (no google-services.json), which the registrar logs.
            tokenFetcher = { onToken, onError ->
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token -> onToken(token) }
                        .addOnFailureListener { error -> onError(error) }
                } catch (error: Throwable) {
                    // No google-services.json / FirebaseApp not initialised.
                    onError(error)
                }
            },
        ).also {
            com.example.chat.notifications.FcmRegistrarHolder.registrar = it
            installPushWakeHandler()
        }
    }

    /** Push-agnostic wake wiring: a content-free nudge (any transport) runs a
     *  bounded sync over the authenticated channel. */
    private val inactiveAccountPush by lazy {
        com.example.chat.data.api.InactiveAccountPushBridge(requireContext(), apiBackend, secureStore)
    }
    private val callInboxStage by lazy { com.example.chat.data.api.EncryptedCallInboxStage(secureStore) }
    private val callInboxConsumer by lazy { com.example.chat.data.api.DurableCallInboxConsumer(callInboxStage) }
    private val handledCallInboxEvents = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val callInboxRunning = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val callInboxRetries = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val callInboxRecoveryScheduledAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** A push may be the only wake. Retry only this accepted inbox task, bounded to its 60s event lifetime. */
    private fun retryAccountCallInbox(meta: com.example.chat.data.api.ApiRegisteredAccountMeta, bearer: String) {
        synchronized(callInboxRetries) {
            if (callInboxRetries[meta.accountHash]?.isActive == true) return
            callInboxRetries[meta.accountHash] = appScope.launch {
                try {
                    for (delayMillis in listOf(2_100L, 4_100L, 8_100L, 16_100L, 30_100L)) {
                        kotlinx.coroutines.delay(delayMillis)
                        val api = apiBackendUnderConstruction ?: apiBackend
                        if (api.multiAccount.accountByKey(meta.accountKey)?.sessionExpired != false ||
                            api.multiAccount.credentialsFor(meta.accountKey)?.accessToken != bearer) break
                        var retryNeeded = false
                        consumeAccountCallInbox(meta, bearer, scheduleRetry = false, retryResult = { retryNeeded = it })
                        if (!retryNeeded) break
                    }
                } finally { synchronized(callInboxRetries) { callInboxRetries.remove(meta.accountHash) } }
            }
        }
    }
    private val accountPushBindings by lazy {
        com.example.chat.data.api.AccountPushBindingRouter(requireContext(), apiBackendUnderConstruction ?: apiBackend,
            inactive = { key -> inactiveAccountPush.run(key) }, durableCalls = { meta, token -> consumeAccountCallInbox(meta, token) })
    }
    private suspend fun consumeAccountCallInbox(meta: com.example.chat.data.api.ApiRegisteredAccountMeta, bearer: String, scheduleRetry: Boolean = true, retryResult: (Boolean) -> Unit = {}): Boolean {
        val api = apiBackendUnderConstruction ?: apiBackend
        fun valid() = !com.example.chat.data.demo.DemoMode.active && api.multiAccount.accountByKey(meta.accountKey)?.sessionExpired == false &&
            api.multiAccount.credentialsFor(meta.accountKey)?.accessToken == bearer
        val owner = com.example.chat.data.api.ApiAccountScope(meta.userId, bearer, meta.tenantId, meta.baseUrl, meta.webSocketTemplate, 0)
        val client = com.example.chat.data.api.DurableCallInboxClient(owner, com.example.chat.data.api.OkHttpApiTransport(allowRedirects = false), ::valid)
        if (!valid() || !kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { client.supports() }) return false
        if (!callInboxRunning.add(meta.accountHash)) { retryResult(true); return true }
        try {
            val outcome = callInboxConsumer.consume(meta.accountHash, client, ::valid, prepareRecovery = {
                val now = android.os.SystemClock.elapsedRealtime()
                val prior = callInboxRecoveryScheduledAt[meta.accountHash]
                if (scheduleRetry && (prior == null || now - prior >= 10_000L)) {
                    com.example.chat.data.api.CallInboxRetryWorker.schedule(requireContext(), meta.accountHash)
                    callInboxRecoveryScheduledAt[meta.accountHash] = now
                }
            }) { events ->
                val active = api.authSnapshot()
                val ownsActive = valid() && api.currentAccountKey() == meta.accountKey && active.token == bearer
                val svc = callSignaling
                val firstOffer = events.firstOrNull { it.kind == "offer" && it.data.opt("ice_restart") != true }
                if (!ownsActive) {
                    events.filter { it.kind in setOf("end", "hangup", "decline", "reject", "busy", "cancel") }.forEach { event ->
                        if (valid()) com.example.chat.notifications.ChatyNotifications.cancelAccountIncomingCall(requireContext(), meta.accountHash, event.callId)
                    }
                    if (firstOffer != null && valid()) com.example.chat.notifications.ChatyNotifications.showAccountIncomingCall(requireContext(), meta.accountHash, firstOffer.callId, java.time.Duration.between(java.time.Instant.now(), firstOffer.expiresAt).toMillis())
                } else {
                    val delivered = mutableSetOf<String>()
                    val deferredCalls = mutableSetOf<String>()
                    for (event in events) {
                        if (!valid() || api.authSnapshot() !== active) break
                        // ICE renegotiation cannot create a fresh incoming session after process death.
                        if (event.kind == "offer" && event.data.opt("ice_restart") == true &&
                            svc.currentSession?.let { it.callId == event.callId && !it.state.isTerminal } != true) {
                            delivered += event.eventId
                            continue
                        }
                        if (event.kind == "offer" && svc.hasActiveSession && svc.currentSession?.callId != event.callId) {
                            deferredCalls += event.callId
                            com.example.chat.notifications.ChatyNotifications.showAccountIncomingCall(requireContext(), meta.accountHash, event.callId, java.time.Duration.between(java.time.Instant.now(), event.expiresAt).toMillis())
                            continue
                        }
                        if (event.callId in deferredCalls && event.kind !in setOf("end", "hangup", "decline", "reject", "busy", "cancel")) continue
                        if (event.kind in setOf("end", "hangup", "decline", "reject", "busy", "cancel"))
                            com.example.chat.notifications.ChatyNotifications.cancelAccountIncomingCall(requireContext(), meta.accountHash, event.callId)
                        val key = "${meta.accountHash}:${event.eventId}"
                        if (key !in handledCallInboxEvents) {
                            svc.handleRemoteSignal(com.example.chat.data.api.ApiChatClient.deepMap(event.data),
                                validScope = { valid() && api.authSnapshot() === active },
                                guardScope = { action -> api.withAuthScope(active, action) })
                            if (!valid() || api.authSnapshot() !== active) break
                            handledCallInboxEvents.add(key)
                        }
                        // Keep the unanswered offer and early ICE durable while the in-memory service rings.
                        // Explicit answer/decline writes a resolved tombstone before media or signaling.
                        val ringing = svc.currentSession?.let { it.callId == event.callId && it.state == com.example.chat.data.services.CallSessionState.incoming } == true
                        if (!ringing) delivered += event.eventId
                    }
                    if (delivered.isNotEmpty()) callInboxStage.forget(meta.accountHash, delivered)
                    if (handledCallInboxEvents.size > 1024) handledCallInboxEvents.clear() // Service also dedups call/session identity.
                }
            }
            val needsRetry = valid() && outcome == com.example.chat.data.api.DurableCallInboxConsumer.Outcome.RETRY_REQUIRED
            retryResult(needsRetry)
            // A server-caught-up inbox can still contain a locally staged unanswered call.
            // Keep its durable wake until disposition/expiry so process death cannot orphan that offer.
            val awaitingAnswer = callInboxStage.pending(meta.accountHash).any { it.kind == "offer" && it.data.opt("ice_restart") != true }
            if (scheduleRetry && !needsRetry && !awaitingAnswer && callInboxRecoveryScheduledAt.remove(meta.accountHash) != null)
                com.example.chat.data.api.CallInboxRetryWorker.cancel(requireContext(), meta.accountHash)
            if (scheduleRetry && needsRetry)
                retryAccountCallInbox(meta, bearer)
        } finally { callInboxRunning.remove(meta.accountHash) }
        return true // Capability exists, even if this wake needs retry. NEVER switch to destructive legacy drain.
    }
    /** Return true only while this exact retained account's bounded inbox task still needs recovery. */
    suspend fun retryDurableCallInboxFromWorker(accountHash: String): Boolean {
        val api = apiBackendUnderConstruction ?: apiBackend
        val meta = api.multiAccount.accounts().firstOrNull { it.accountHash == accountHash && !it.sessionExpired } ?: return false
        val bearer = api.multiAccount.credentialsFor(meta.accountKey)?.accessToken ?: return false
        var needsRetry = true
        val supported = consumeAccountCallInbox(meta, bearer, scheduleRetry = false, retryResult = { needsRetry = it })
        // A failed capability lookup is not proof that an inbox previously verified as v2 vanished.
        val awaitingAnswer = callInboxStage.pending(accountHash).any { it.kind == "offer" && it.data.opt("ice_restart") != true }
        return !supported || needsRetry || awaitingAnswer
    }
    suspend fun replayAccountCallInbox(accountHash: String): Boolean {
        val meta = apiBackend.multiAccount.accounts().firstOrNull { it.accountHash == accountHash } ?: return false
        val token = apiBackend.multiAccount.credentialsFor(meta.accountKey)?.accessToken ?: return false
        return consumeAccountCallInbox(meta, token)
    }
    suspend fun hasPendingAccountCall(accountHash: String, callId: String): Boolean =
        callInboxStage.pending(accountHash).any { it.callId == callId && it.kind == "offer" }

    suspend fun declinePendingAccountCall(accountHash: String, callId: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val meta = apiBackend.multiAccount.accounts().firstOrNull { it.accountHash == accountHash && !it.sessionExpired } ?: return@withContext false
        val credentials = apiBackend.multiAccount.credentialsFor(meta.accountKey) ?: return@withContext false
        val offer = callInboxStage.pending(accountHash).firstOrNull { it.callId == callId && it.kind == "offer" } ?: return@withContext false
        val wire = com.example.chat.data.api.CallSignalScope(meta.userId, meta.baseUrl, credentials.accessToken, 0, meta.tenantId)
        val client = com.example.chat.data.api.ApiCallSignalClient({ wire.takeIf { apiBackend.multiAccount.credentialsFor(meta.accountKey)?.accessToken == wire.token } },
            com.example.chat.data.api.OkHttpApiTransport(allowRedirects = false))
        val event = mapOf("signal_type" to "decline", "call_id" to callId, "client_event_id" to java.util.UUID.randomUUID().toString())
        if (client.send(wire, offer.senderId, event) != com.example.chat.data.api.ApiCallSignalClient.SendResult.ACCEPTED) return@withContext false
        callInboxStage.resolveCall(accountHash, callId)
        com.example.chat.notifications.ChatyNotifications.cancelAccountIncomingCall(requireContext(), accountHash, callId)
        true
    }
    private suspend fun catchUpInactiveAccounts() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try { inactiveAccountPush.run() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { /* Inactive catch-up cannot fail the active wake. */ }
    }

    private fun installPushWakeHandler() {
        com.example.chat.notifications.PushWakeHolder.handler =
            object : com.example.chat.notifications.PushWakeHandler {
                override suspend fun onAccountSyncNudgeAndWait(bindingId: String) { accountPushBindings.handle(bindingId) }
                override suspend fun onAccountIncomingCallNudgeAndWait(bindingId: String, callId: String) { accountPushBindings.handle(bindingId, callId) }
                override fun onSyncNudge() {
                    appScope.launch { apiBackend.syncFromPushNudge(); catchUpInactiveAccounts() }
                }

                override suspend fun onSyncNudgeAndWait() {
                    apiBackend.syncFromPushNudge()
                    catchUpInactiveAccounts()
                }

                override fun onIncomingCallNudge(callId: String) {
                    // Fetch the queued offer over the authenticated channel;
                    // the delivered signal surfaces the existing ring UI.
                    appScope.launch { apiBackend.pollCallSignalsAndAwait(); catchUpInactiveAccounts() }
                }

                override suspend fun onIncomingCallNudgeAndWait(callId: String) {
                    apiBackend.pollCallSignalsAndAwait()
                    catchUpInactiveAccounts()
                }
            }
    }

    /** Matrices 11/18: Room + SQLCipher encrypted local message DB, isolated per account namespace. */
    val secureMessageStore: com.example.chat.data.local.SecureMessageStore by lazy {
        com.example.chat.data.local.SecureMessageStore(
            context = requireContext(),
            secureStore = secureStore,
            activeNamespaceProvider = { apiBackend.currentAccountHash() },
        )
    }

    /** Media matrix: per-network auto-download rules + per-chat overrides. */
    val mediaAutoDownloadGate: com.example.chat.data.media.MediaAutoDownloadGate by lazy {
        com.example.chat.data.media.MediaAutoDownloadGate(
            networkMonitor,
            requireContext().getSharedPreferences("chaty_media_rules", android.content.Context.MODE_PRIVATE),
        )
    }

    /** Tap-to-download for media from other people: resumable, account-scoped parts, progress state. */
    val mediaDownloads: com.example.chat.data.media.MediaDownloadManager by lazy {
        val online = kotlinx.coroutines.flow.MutableStateFlow(networkMonitor.state.value.online)
        appScope.launch { networkMonitor.state.collect { online.value = it.online } }
        com.example.chat.data.media.MediaDownloadManager(
            scope = appScope,
            resolver = attachmentResolver,
            downloader = com.example.chat.data.media.ResumableMediaDownloader(
                client = okhttp3.OkHttpClient(),
                maxBytes = com.example.chat.data.services.ChatMediaService.STORAGE_LIMIT_MB * 1024L * 1024L,
            ),
            partsRoot = java.io.File(requireContext().noBackupFilesDir, "chaty_dl_parts"),
            online = online,
        )
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
            remote = ApiRemoteSettingsStore(apiBackend),
            lockStore = localLockService,
            auth = SessionAndApiAuthSource(GoTrueAuthPlane(sessionManager), ApiBackendAuthPlane(apiBackend)),
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
        ).apply {
            // Wire the foreground/pause hooks (previously never attached, so
            // the 30s probe also ran in the background).
            attachLifecycle(androidx.lifecycle.ProcessLifecycleOwner.get())
        }
    }

    /** D1 (Supabase retirement): the MLS plane talks to the Chaty backend's
     *  /e2ee routes; the shared Supabase rpcClient is no longer on the
     *  encrypted path. Account-scoped: base URL, tenant and token always come
     *  from the current api session snapshot. */
    private val mlsRpcClient: com.example.chat.data.api.ApiMlsRpcClient by lazy {
        com.example.chat.data.api.ApiMlsRpcClient(
            baseUrl = { apiBackend.config.restApiBaseUrl },
            tenantId = { apiBackend.config.tenantId },
            token = { apiBackend.authSnapshot().token },
            transport = com.example.chat.data.api.OkHttpApiTransport(),
        )
    }

    /** D2=C encrypted attachment queue (upload/download/confirm of opaque
     *  ciphertext blobs; keys travel only inside MLS messages). The
     *  file-transfer flow wires in with the media section. */
    val encryptedAttachments: com.example.chat.data.api.ApiEncryptedAttachmentClient by lazy {
        com.example.chat.data.api.ApiEncryptedAttachmentClient(
            baseUrl = { apiBackend.config.restApiBaseUrl },
            tenantId = { apiBackend.config.tenantId },
            token = { apiBackend.authSnapshot().token },
        )
    }

    val mls: MlsE2eeService by lazy {
        MlsE2eeService(
            rpc = mlsRpcClient,
            secureStorage = secureStore,
            currentAuthUserId = currentUserId,
            dbPathForUser = { safeNs -> requireContext().filesDir.absolutePath + "/mls/" + safeNs },
            accountNamespaceProvider = { apiBackend.currentAccountHash() },
        )
    }

    /** Dart purgeLocalDataForDeletedAccount (profile_actions.dart:217); deletes ONLY the target account's
     *  secrets when other signed-in accounts exist in the multi-account registry. */
    suspend fun purgeLocalDataForDeletedAccount(userId: String) {
        val currentHash = apiBackend.currentAccountHash()
        val matchingAccounts = apiBackend.multiAccount.accounts().filter { it.userId == userId }
        val targetNamespaces = buildSet {
            add(userId)
            currentHash?.let { add(it) }
            matchingAccounts.forEach { add(it.accountHash) }
        }
        val otherAccountsRemain = apiBackend.multiAccount.accounts().any { it.userId != userId }
        com.example.chat.data.services.AccountDeletionPurge(
            clearOutbox = { pendingSends.clear(it) },
            purgeMlsIdentity = { uid ->
                targetNamespaces.forEach { ns -> mls.purgeLocalIdentityForNamespace(ns) }
            },
            purgeTemporaryFiles = {
                com.example.chat.data.services.AccountDeletionPurge.purgeChatyTemporaryFiles(requireContext().cacheDir)
            },
            deleteSnapshots = { uid ->
                snapshotCache.deleteUser(uid)
                targetNamespaces.forEach { ns -> runCatching { snapshotCache.deleteUser(ns) } }
            },
            deleteAllSecureStorage = {
                if (!otherAccountsRemain) {
                    secureStore.deleteAll()
                } else {
                    targetNamespaces.forEach { ns ->
                        secureStore.deletePrefix("chaty.mls.$ns.")
                        secureStore.deletePrefix("chaty.hidden_vault.$ns.")
                        secureStore.deletePrefix("chaty.local_lock.v2.$ns.")
                    }
                }
            },
            purgeMediaCache = { mediaCache.deleteAccount(it) },
        ).purge(userId)
        targetNamespaces.forEach { ns ->
            runCatching { secureMessageStore.purgeNamespace(ns) }
        }
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
            secureRpc = mlsRpcClient,
            // Central routing: routeTextSend sends flagged chats over the MLS
            // plane and everything else through the REST sender below. The
            // flag read inside routeTextSend throws when it is unreadable, so
            // a caller can never silently fall back to plaintext.
            restTextSend = { cid, text -> apiBackend.sendMessage(cid, text) },
            // Enabling encryption is refused while a REST send for that chat
            // is queued or delivering, or an attachment/voice upload for it
            // is in flight. The upload count is registered under the same
            // dispatch lock the toggle holds, so check-then-register cannot
            // slip through.
            restSendsInFlight = { cid ->
                if (apiBackend.hasPendingRestSends(cid)) {
                    true
                } else {
                    val s = apiBackend.authSnapshot()
                    com.example.chat.data.messaging.AttachmentUploadTracker.hasInFlight(
                        com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                            s.userId, s.tenantId, s.baseUrl),
                        cid,
                    )
                }
            },
            dispatchLocks = conversationDispatchLocks,
            lockAccountKey = {
                val s = apiBackend.authSnapshot()
                com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                    s.userId, s.tenantId, s.baseUrl)
            },
            // The toggle holds the dispatch lock already; these are the
            // non-locking bodies (the public variants would self-deadlock).
            // The scope comes from the toggle's captured identity - the
            // service re-checks it against live auth before acting.
            // Storage keys for secure_channels/sync_cursors are namespaced by
            // the account hash (tenant+backend+user), never the raw uid: the
            // same uid on another backend cannot load this backend's flags or
            // cursors. Demo/unsigned falls back to the raw uid.
            storageNamespace = {
                apiBackend.currentAccountHash() ?: throw IllegalStateException("Account storage is unavailable until sign-in completes.")
            },
            holdRestSends = { scope, cid ->
                val s = scope as? com.example.chat.data.api.ApiAccountScope
                    ?: throw IllegalStateException("Missing account scope for the quarantine hold.")
                apiBackend.holdQueuedMessagesForConversationLocked(s, cid)
            },
            releaseRestSends = { scope, cid ->
                val s = scope as? com.example.chat.data.api.ApiAccountScope
                    ?: throw IllegalStateException("Missing account scope for the quarantine release.")
                apiBackend.releaseQueuedMessagesForConversationLocked(s, cid)
            },
            // D1: the repository's reads (conversations list, message list,
            // profile search) ride the Chaty backend too - the Supabase
            // PostgREST client is retired from the messaging path.
            rpc = mlsRpcClient,
            mls = { mls },
            currentUserId = currentUserId,
            pendingStore = pendingSends,
            onQueuedCountChanged = { count -> connectionHealth.updateQueuedCount(count) },
            onPendingEnqueued = { appScope.launch { outgoingQueue.processQueue() } },
            snapshotCache = snapshotCache,
            messageSnapshotNamespace = { captured ->
                val owner = captured as? com.example.chat.data.api.ApiAccountScope
                owner?.let { com.example.chat.data.api.ApiAccountRoutingCanonicalizer.accountKey(it.tenantId, it.baseUrl, it.userId.orEmpty()) }
                    ?.let(com.example.chat.data.api.ApiAccountRoutingCanonicalizer::accountHash)
            },
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
        ).also { repo ->
            // Matrices 11/18: mirror decrypted, user-visible messages into the
            // SQLCipher DB so global search works offline.
            repo.localMessageMirror = mirror@{ captured, conversationId, messages ->
                val owner = captured as? com.example.chat.data.api.ApiAccountScope ?: return@mirror
                if (apiBackend.authSnapshot() !== owner) return@mirror
                val key = com.example.chat.data.api.ApiAccountRoutingCanonicalizer.accountKey(owner.tenantId, owner.baseUrl, owner.userId.orEmpty()) ?: return@mirror
                val activeHash = com.example.chat.data.api.ApiAccountRoutingCanonicalizer.accountHash(key)
                val dao = secureMessageStore.database(activeHash).messages()
                dao.upsertAll(messages.map { m ->
                    com.example.chat.data.local.LocalMessageEntity(
                        id = m.id,
                        conversationId = conversationId,
                        senderId = m.senderId,
                        type = m.type.name,
                        text = if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(m.metadata)) "" else m.text,
                        caption = if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(m.metadata)) null else m.metadata["caption"]?.toString(),
                        createdAtMillis = m.createdAt.toEpochMilli(),
                        editedAtMillis = m.editedAt?.toEpochMilli(),
                        isDeletedForEveryone = m.isDeletedForEveryone || com.example.chat.core.messages.ViewOnceTextPolicy.restricted(m.metadata),
                    )
                })
            }
        }
    }

    val tasksRepository: TasksRepository by lazy {
        TasksRepository(
            // Backend tasks plane; the retired Supabase task RPCs are gone.
            tasksPlane = { apiBackend.tasksClient },
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
            beforeIncomingDisposition = { captured, callId ->
                val owner = captured as? com.example.chat.data.api.ApiAccountScope ?: error("Call account is unavailable.")
                check(apiBackend.authSnapshot() === owner) { "Call account changed." }
                val key = com.example.chat.data.api.ApiAccountRoutingCanonicalizer.accountKey(owner.tenantId, owner.baseUrl, owner.userId.orEmpty())
                    ?: error("Call account is unavailable.")
                val hash = com.example.chat.data.api.ApiAccountRoutingCanonicalizer.accountHash(key)
                if (callInboxStage.pending(hash).any { it.callId == callId && it.kind == "offer" })
                    callInboxStage.resolveCall(hash, callId)
                check(apiBackend.authSnapshot() === owner) { "Call account changed." }
            },
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
            iceServersProvider = { apiBackend.loadIceServers() },
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
            // Calls matrix "survive network switch mid-call": feed transport
            // changes (Wi-Fi <-> cellular) into ICE restart.
            appScope.launch {
                networkMonitor.state.collect { status ->
                    svc.onNetworkTransportChanged(status.transport)
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
            sendSignal = { targetUserId, data -> apiBackend.sendCallSignal(targetUserId, data) },
            transferMonitor = transferMonitor,
            iceServers = { com.example.chat.data.transfer.P2pFileTransferService.parseIceServers(callSignaling.iceServersForTransfer()) },
            networkStatus = { networkMonitor.state.value },
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
            // Backend REST heartbeat (POST /presence/heartbeat); the retired
            // Supabase profiles write is gone. Server TTL is 90s: the
            // foreground loop in ChatyPresenceEffect keeps it fresh.
            heartbeat = { online -> apiBackend.presenceHeartbeat(online) },
            currentUserId = currentUserId,
            privacy = { preferencesController.privacy.toPresenceSnapshot() },
        )
    }

    val profileService: ProfileService by lazy {
        ProfileService(
            apiBackend = apiBackend,
            conversationsRepository = conversationsRepository,
            tasksRepository = tasksRepository,
            presenceService = presenceService,
            currentAuthUser = {
                apiBackend.currentUser?.let {
                    com.example.chat.data.services.AuthUserSnapshot(id = it.id, email = it.email, phone = it.phone)
                } ?: sessionManager.currentAuthUser()
            },
            scope = appScope,
            mls = mls,
        )
    }

    val contactRelationships: ContactRelationshipService by lazy {
        ContactRelationshipService(
            // Backend contacts plane; the retired Supabase tables are gone.
            contacts = { apiBackend.contactsPlane() },
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

    /** Call history plane (research 5.4): server-backed per-viewer log. */
    val apiCallHistory: com.example.chat.data.services.ApiCallHistoryService by lazy {
        com.example.chat.data.services.ApiCallHistoryService(
            client = { apiBackend.callHistoryPlane() },
            currentUserId = currentUserId,
        )
    }

    val gbFeatures: GbFeatureBackendService by lazy {
        // Backend migration: no server-side automation plane exists yet, so
        // the service runs fail-closed (null clients = local-only no-ops)
        // until the backend grows one.
        GbFeatureBackendService(queryClient = null, rpcClient = null, currentUserId = currentUserId)
    }

    val scheduledMessageService: com.example.chat.ui.automation.ScheduledMessageService by lazy {
        com.example.chat.ui.automation.ScheduledMessageService(backend = gbFeatures, scope = appScope,
            session = { apiBackend.scheduledMessagesClient() },
            sendGuard = { cid ->
                // Scheduled sends dispatch server-side later, without this
                // device - always plaintext. Refuse encrypted chats; an
                // unreadable flag throws from isSecureChannel, which fails
                // closed the same way.
                if (conversationsRepository.isSecureChannel(cid)) {
                    throw IllegalStateException(
                        "Scheduled messages are not available in an encrypted chat: the server would send them as plaintext.")
                }
            })
    }

    val autoReplyRuleEngine: com.example.chat.ui.automation.AutoReplyRuleEngine by lazy {
        com.example.chat.ui.automation.AutoReplyRuleEngine(prefs = preferencesController, backend = gbFeatures, scope = appScope,
            sendReply = { cid, text ->
                // Fail closed by default: when the chat is flagged for
                // end-to-end encryption, or its flag cannot be read, the
                // auto-reply is skipped entirely instead of leaking plaintext.
                // (Sending the canned reply over MLS is a deliberate product
                // choice for later - automation inside encrypted chats should
                // be opt-in, not silent.)
                val secure = try {
                    conversationsRepository.isSecureChannel(cid)
                } catch (e: Exception) {
                    android.util.Log.w("AutoReply", "encryption flag unreadable; auto-reply skipped: ${e.message}")
                    true
                }
                if (!secure) apiBackend.sendMessage(cid, text)
            })
    }

    val messageAutomation: MessageAutomationService by lazy {
        MessageAutomationService(
            automation = { preferencesController.automation },
            currentUserId = currentUserId,
            backend = gbFeatures,
            scope = appScope,
            addListener = { l -> preferencesController.addListener(l) },
            removeListener = { l -> preferencesController.removeListener(l) },
        ).apply {
            // Foreground-gate the 30s retry pass (same pattern as
            // ConnectionHealthService above).
            attachLifecycle(androidx.lifecycle.ProcessLifecycleOwner.get())
        }
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
                override fun directConversationIdForUser(userId: String) =
                    conversationsRepository.conversationsById.values
                        .firstOrNull { it.isDirect && userId in it.participantIds }?.id
                override fun isConversationProtected(conversationId: String) =
                    preferencesController.isConversationProtected(conversationId)
            },
            readPlane = com.example.chat.data.realtime.BackendRichChatReadPlane(apiBackend),
            markDeliveredRpc = { id -> apiBackend.markConversationDeliveredBackend(id) },
            // No server-side recording-state plane yet: fail closed (no-op).
            setRecordingRpc = { _, _ -> },
            channelFactory = { _, _, _, onStatus ->
                inertRealtimeChannel(onStatus)
            },
            currentUserId = currentUserId,
            connectionHealth = ConnectionHealthSinkAdapter(connectionHealth),
        )
    }

    val realtimeReconciliation: RealtimeReconciliationService by lazy {
        RealtimeReconciliationService(
            channelFactory = { _, _, _, onStatus -> inertRealtimeChannel(onStatus) },
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
            authSignOut = {
                apiBackend.signOut()
                sessionManager.clearLocal()
            },
            kickPendingSends = { appScope.launch { outgoingQueue.processQueue() } },
            onSignedOut = { mediaCache.purgeTransientTemps(); mediaDownloads.purgeAll() },
            apiScopeIdentity = { apiBackend.authSnapshot() },
            onAccountSwitch = { apiBackend.clearChatCachesForAccountSwitch() },
        )
    }

    private val statusClient: com.example.chat.data.api.ApiStatusClient by lazy {
        com.example.chat.data.api.ApiStatusClient(
            baseUrl = { apiBackend.config.restApiBaseUrl },
            tenantId = { apiBackend.config.tenantId },
            token = { apiBackend.authSnapshot().token },
            transport = com.example.chat.data.api.OkHttpApiTransport(),
        )
    }

    val statusService: StatusService by lazy {
        StatusService(
            api = statusClient,
            mediaUpload = { name, bytes, _ ->
                val result = apiBackend.uploadMedia(name, bytes)
                (result["url"] ?: result["public_url"] ?: result["media_url"])?.toString()
                    ?: throw IllegalStateException("Media upload returned no URL.")
            },
            currentUserId = currentUserId,
            privacy = { preferencesController.privacy.toPublicationSnapshot() },
            preferencesReady = { preferencesController.preferencesReady },
            gbHideStatusView = { preferencesController.gbBool("yoHideStatViewV2") },
            gbAudioStatusEnabled = { preferencesController.gbBool("abu9aleh_status_audio") },
            gbSizeLimitMb = { key, fallback -> preferencesController.gbDouble(key, fallback) },
            // Status now lives on the custom backend: a live API session (user
            // + token) is the identity binding. Still fail-closed when signed out.
            identityBound = {
                val scope = apiBackend.authSnapshot()
                !scope.userId.isNullOrBlank() && !scope.token.isNullOrBlank()
            },
        )
    }

    /** Mirrors backend_service.dart's onAuthStateChange wiring: the auth
     *  event stream feeds SessionLifecycleService.initialize as a Flow of
     *  GoTrue session snapshots (its Dart-faithful API), and the rich
     *  realtime service observes the same transitions. Call once when the UI
     *  layer is up (Dart does this in ChatyDataStore.start()). */
    suspend fun startSessionLifecycle() {
        // Wire live socket presence/typing into the event-toast path once
        // (addIfAbsent dedupes repeat calls). The toast gating itself lives
        // in RichChatRealtimeService + ContactEventAlertPolicy.
        apiBackend.addContactEventListener(object : com.example.chat.data.api.ApiBackendService.ContactEventListener {
            override fun onContactOnlineChanged(userId: String, online: Boolean) =
                richRealtime.onLivePresenceChanged(userId, online)
            override fun onContactTypingChanged(roomId: String, userId: String, isTyping: Boolean) =
                richRealtime.onLiveTypingChanged(roomId, userId, isTyping)
        })
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
