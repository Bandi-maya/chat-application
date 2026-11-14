package com.example.chat.data.api

import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock

import android.content.Context
import com.example.chat.data.demo.DemoMode
import com.example.chat.data.demo.DemoSampleData
import com.example.chat.data.messaging.SnapshotCacheStore
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Android holder for the REST backend: ApiBackendConfig (tenant + base URL),
 *  ApiAuthClient (endpoints), ApiAuthSession (api_hub_token/api_hub_user
 *  persistence). Mirrors the api branch's ApiBackendService auth surface; the
 *  ChatyBackendService facade prefers this session, so splash/home routing
 *  reads [isAuthenticated]/[currentUser] from here first. */
class ApiBackendService(
    context: Context,
    chatTransport: ApiHttpTransport = OkHttpApiTransport(),
) {
    private val appContext = context.applicationContext

    private val authScopeLock = Any()
    val config = ApiBackendConfig(context, authScopeLock, ::notifyAuthScopeTransition)

    private val sessionStore: ApiSessionStore = EncryptedApiSessionStore(context.applicationContext)
    val multiAccountStore: ApiMultiAccountStore = EncryptedMultiAccountStore(context.applicationContext)
    val multiAccount = MultiAccountRegistryCoordinator(multiAccountStore)
    // Non-secret account-local preferences only; credentials, outbox, and drafts live in encrypted storage.
    private val prefs = context.applicationContext.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)

    private val auth = ApiAuthClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(allowRedirects = false),
        token = { session.token },
    )

    /** Scoped auth requests for a registered account without mutating global routing. */
    private fun scopedAuthClient(baseUrl: String, tenantId: String): ApiAuthClient = ApiAuthClient(
        baseUrl = { baseUrl },
        tenantId = { tenantId },
        transport = OkHttpApiTransport(allowRedirects = false),
        token = { session.token },
    )

    private val session = ApiAuthSession(sessionStore, authScopeLock, ::notifyAuthScopeTransition)

    // Contacts / presence / call-history planes (research 5.2-5.4). All
    // fail-closed: a pre-plane server throws PlaneUnavailable and the UI
    // shows nothing rather than guessing.
    private val contactsClient = ApiContactsClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(allowRedirects = false),
        token = { session.token },
    )
    private fun scopedPresenceClient(expected: ApiAccountScope) = ApiPresenceClient(
        baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
        transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests),
    )
    private val callHistoryClient = ApiCallHistoryClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(allowRedirects = false),
        token = { session.token },
    )

    /** Phase 5: server-controlled configuration, revalidated with ETag on
     *  login and app start (never polled). Failures keep the last cache. */
    val serverConfig = ApiConfigClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
        transport = OkHttpApiTransport(),
        cache = ApiConfigStore(context.applicationContext),
    )
    private val authScopeOwner = ApiAccountScopeOwner(authScopeLock, session::snapshot, config::snapshot)
    fun authSnapshot(): ApiAccountScope = authScopeOwner.snapshot()
    fun withAuthScope(expected: ApiAccountScope, block: () -> Unit): Boolean = authScopeOwner.withScope(expected, block)
    private val authScopeListeners = java.util.concurrent.CopyOnWriteArraySet<(ApiAccountScope) -> Unit>()
    /** Listener fires synchronously under transition lock, no polling gap. Cancellation only: no IO,
     * blocking, UI, reentrant auth/config mutation, or unguarded deferred payload. */
    fun addAuthScopeListener(listener: (ApiAccountScope) -> Unit) = synchronized(authScopeLock) {
        authScopeListeners.add(listener)
        listener(authSnapshot())
    }
    fun removeAuthScopeListener(listener: (ApiAccountScope) -> Unit) { authScopeListeners.remove(listener) }
    private fun notifyAuthScopeTransition() {
        val next = authSnapshot()
        sendRequests.cancelAll() // in-flight scope-bound sends die with the account they were built for
        authScopeListeners.forEach { listener -> runCatching { listener(next) } }
    }


    private val chat = ApiChatClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
        transport = chatTransport,
    )

    private val mediaUpload = ApiMediaUploadClient(
        restApiBaseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
    )

    private val deviceTokens = ApiDeviceTokenClient(
        restApiBaseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
    )

    private val subscriptionClient = ApiSubscriptionClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
        transport = OkHttpApiTransport(),
    )

    private val accountClient = ApiAccountClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
        transport = OkHttpApiTransport(),
        binaryTransport = OkHttpApiBinaryTransport(),
    )

    private val moderationClient = ApiModerationClient(chatTransport)
    suspend fun reportMessage(expected: ApiAccountScope, messageId: String, reason: String, details: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            moderationClient.report(expected, { authSnapshot() === expected }, messageId, reason, details)
        }

    /** Chat-detail block toggle. Cross-check 2026-11-08: this used to hit the
     *  pending /moderation/blocks plane (always 404 on the live backend), so
     *  the toggle could never succeed. The contacts plane owns blocking
     *  (POST /block, DELETE /block/{id}), the same plane the privacy settings
     *  read, so writes and reads now agree. Same account-scope guards as the
     *  moderation path. */
    suspend fun setUserBlocked(expected: ApiAccountScope, userId: String, blocked: Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            check(authSnapshot() === expected) { "Account changed. Open this chat again." }
            val id = ApiPathSegments.safe(userId) ?: throw IllegalArgumentException("Invalid user identifier.")
            require(id != expected.userId) { "You cannot block yourself." }
            val client = contactsPlane(expected)
            if (blocked) client.blockUser(id) else client.unblock(id)
            check(authSnapshot() === expected) { "Account changed. Result was not applied." }
        }

    private val accountDeletion = ApiAccountDeletionClient(chatTransport)

    val tasksClient = ApiTasksClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
        transport = OkHttpApiTransport(),
    )

    /** ApiBackendService._currentOrg (Dart): current organization payload -
     *  restored from the persisted api_hub_org, refreshed on org register. */
    var currentOrg: Map<String, Any?>? = null
        private set

    /** Dart currentOrganization alias. */
    val currentOrganization: Map<String, Any?>? get() = currentOrg

    /** ApiBackendService.getAvailablePlans (Dart accepts but never sends orgId). */
    suspend fun getAvailablePlans(organizationId: String? = null): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) { subscriptionClient.getAvailablePlans(organizationId) }

    /** ApiBackendService.subscribeToPlan: POST create-order; throws the server
     *  detail/message on non-200. */
    /** Dart subscribeToPlan post-success: clear the subscription gate, update
     *  the in-memory org plan, notify, and reload conversations/users. */
    suspend fun subscribeToPlan(organizationId: String, planId: String, durationMonths: Int = 1): Boolean =
        withContext(Dispatchers.IO) {
            val ok = subscriptionClient.subscribeToPlan(organizationId, planId, durationMonths)
            if (ok) {
                requiresSubscription = false
                subscriptionErrorMessage = null
                (currentOrg as? MutableMap<String, Any?>)?.put("plan_type", planId.trim())
                notifyChatListeners()
                chatScope.launch { loadConversations() }
                chatScope.launch { loadUsers() }
            }
            ok
        }

    // ---- Realtime plane (W1 ApiRealtimeClient integration) ----

    private val callSignalListeners =
        java.util.concurrent.CopyOnWriteArrayList<(Map<String, Any?>) -> Unit>()

    /**
     * Loads short-lived ICE credentials from the Chaty custom API.
     * The authenticated user identity is taken from the current account scope;
     * credentials are never requested from Supabase and are never cached.
     */
    suspend fun loadIceServers(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val expected = authSnapshot()
        val bearer = expected.token?.takeIf { it.isNotBlank() }
            ?: throw SecurityException("Sign in to load WebRTC ICE credentials.")
        val base = expected.baseUrl.trimEnd('/')
        val uri = runCatching { java.net.URI(base) }.getOrElse {
            throw IllegalStateException("Custom API URL is invalid.")
        }
        val loopback = uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
        if (uri.host.isNullOrBlank() || (uri.scheme != "https" && !(loopback && uri.scheme == "http")) ||
            uri.userInfo != null || uri.query != null || uri.fragment != null
        ) throw SecurityException("ICE credentials require a secure custom API URL.")

        val headers = mapOf(
            "Authorization" to "Bearer $bearer",
            "x-tenant-id" to expected.tenantId,
            "Accept" to "application/json",
        )
        val transport = OkHttpApiTransport()
        val primary = transport.execute("GET", "$base/chat/call/ice-servers", headers, null)
        val response = if (primary.first == 404) {
            transport.execute("GET", "$base/api/v1/chat/call/ice-servers", headers, null)
        } else primary
        if (authSnapshot() !== expected) throw kotlinx.coroutines.CancellationException("Account changed while loading ICE credentials.")
        if (response.first == 401 || response.first == 403) throw SecurityException("Custom API rejected ICE credential authorization.")
        if (response.first !in 200..299) throw IllegalStateException("Custom ICE endpoint failed (${response.first}).")

        val json = org.json.JSONObject(response.second)
        val raw = json.optJSONArray("ice_servers")
            ?: throw IllegalStateException("Custom ICE endpoint returned no ice_servers array.")
        if (raw.length() > 16) throw SecurityException("Custom ICE endpoint returned too many ICE servers.")
        val servers = ArrayList<Map<String, Any?>>(raw.length())
        for (index in 0 until raw.length()) {
            val item = raw.optJSONObject(index) ?: continue
            val rawUrls = item.opt("urls")
            val urls = when (rawUrls) {
                is String -> listOf(rawUrls)
                is org.json.JSONArray -> (0 until rawUrls.length()).mapNotNull { i ->
                    rawUrls.optString(i).takeIf { it.isNotBlank() }
                }
                else -> emptyList()
            }.onEach { url ->
                if (!url.startsWith("stun:", true) && !url.startsWith("stuns:", true) &&
                    !url.startsWith("turn:", true) && !url.startsWith("turns:", true)
                ) throw SecurityException("Custom ICE endpoint returned an unsupported ICE URL.")
            }
            if (urls.isEmpty()) continue
            val entry = linkedMapOf<String, Any?>("urls" to if (rawUrls is String) urls.first() else urls)
            item.optString("username").takeIf { it.isNotBlank() }?.let { entry["username"] = it }
            item.optString("credential").takeIf { it.isNotBlank() }?.let { entry["credential"] = it }
            servers += entry
        }
        if (servers.isEmpty()) throw IllegalStateException("Custom ICE endpoint returned no usable ICE servers.")
        servers
    }

    fun addCallSignalListener(listener: (Map<String, Any?>) -> Unit) = callSignalListeners.add(listener)
    /** Dart ApiBackendService.sendCallSignal: fire-and-forget call signaling
     *  over the custom-backend WebSocket (payload type call_signal). */
    fun sendCallSignal(targetUserId: String, data: Map<String, Any?>): Boolean {
        val expected = authSnapshot()
        // One logical id shared by socket and REST retry. Never generate a different id in each transport.
        val eventId = (data["client_event_id"] as? String)?.takeIf { runCatching { java.util.UUID.fromString(it).toString() == it }.getOrDefault(false) }
            ?: java.util.UUID.randomUUID().toString()
        val event = data + ("client_event_id" to eventId)
        var queued = false
        withAuthScope(expected) { queued = realtime.sendCallSignal(targetUserId, event) }
        callCoordinator.send(expected, targetUserId, event)
        return queued // Enqueue only, not delivery/receipt.
    }
    fun removeCallSignalListener(listener: (Map<String, Any?>) -> Unit) { callSignalListeners.remove(listener) }

    private val scopedCallListeners = java.util.concurrent.CopyOnWriteArrayList<(ApiAccountScope, Map<String, Any?>) -> Unit>()
    /** Push ring nudge: one-shot REST poll of queued call signals while the
     *  socket is down (app may be backgrounded). */
    @Volatile private var callProtocolOwner: ApiAccountScope? = null
    @Volatile private var durableCallInbox = false
    private val callProtocolMutex = kotlinx.coroutines.sync.Mutex()
    private var lastInboxWakeMs = 0L
    @Volatile var onDurableCallInboxWake: (suspend (ApiAccountScope) -> Unit)? = null
    suspend fun usesDurableCallInbox(expected: ApiAccountScope): Boolean = callProtocolMutex.withLock {
        if (authSnapshot() !== expected) return@withLock false
        if (callProtocolOwner !== expected) {
            val supported = withContext(Dispatchers.IO) { ApiServerCapabilities.supports(expected, OkHttpApiTransport(allowRedirects = false), "call_signal_inbox_v1") }
            if (authSnapshot() !== expected) return@withLock false
            durableCallInbox = supported; callProtocolOwner = expected
            realtime.connect() // URL changes only after authenticated capability verification.
        }
        durableCallInbox
    }
    private fun wakeCallInbox(expected: ApiAccountScope) {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(callProtocolMutex) {
            if (now - lastInboxWakeMs < 1_000) return
            lastInboxWakeMs = now
        }
        chatScope.launch { pollCallSignalsAndAwait(expected) }
    }
    fun pollCallSignalsNow() { wakeCallInbox(authSnapshot()) }
    suspend fun pollCallSignalsAndAwait(expected: ApiAccountScope = authSnapshot()) {
        if (authSnapshot() !== expected) return
        if (usesDurableCallInbox(expected)) onDurableCallInboxWake?.invoke(expected)
        else if (callProtocolOwner === expected) callCoordinator.pollNow(expected)?.join()
    }
    fun addScopedCallSignalListener(listener: (ApiAccountScope, Map<String, Any?>) -> Unit) = scopedCallListeners.add(listener)
    private val callRequests = CallRequestRegistry()
    private val sendRequests = CallRequestRegistry()
    private val callCoordinator by lazy {
        ScopedCallCoordinator(chatScope, ::authSnapshot, ::withAuthScope,
            clientFor = { expected, wire ->
                ApiCallSignalClient({ if (authSnapshot() === expected) wire else null },
                    ScopedCallHttpTransport(expected, ::withAuthScope, callRequests))
            }, cancelRequests = callRequests::cancelAll,
            deliver = { expected, data -> scopedCallListeners.forEach { it(expected, data) } },
            clock = { android.os.SystemClock.elapsedRealtime() },
            legacyAllowed = { expected -> callProtocolOwner === expected && !durableCallInbox },
            inboxWake = ::wakeCallInbox)
    }
    private fun startCallScopeMonitoring() {
        addAuthScopeListener(callCoordinator::transition)
        // connect posts work; the callback itself must stay short under authScopeLock.
        // Offline demo: never connect from the transition listener either.
        addAuthScopeListener { if (!DemoMode.active) realtime.connect() }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val lifecycle = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle
            syncForeground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            callCoordinator.foreground(syncForeground)
            if (syncForeground) startBackgroundSync()
            lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                    syncForeground = true
                    callCoordinator.foreground(true)
                    if (!DemoMode.active) realtime.connect()
                    startBackgroundSync()
                }
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    syncForeground = false
                    realtime.disconnect()
                    activeSyncJob?.cancel()
                    activeSyncJob = null
                    syncRunning = false
                    callCoordinator.foreground(false)
                }
            })
        }
    }

    /** Dart getWebSocketUrl with configured override and REST-derived fallback.
     *  User id falls back to JWT uid/sub when the saved user id is empty. */
    private fun resolveWebSocketUrl(): String? {
        if (session.token == null) return null
        var uid = session.currentUser?.id?.trim() ?: ""
        if (uid.isEmpty()) {
            try {
                val jwt = ApiAuthClient({ "" }, { "" }, ApiHttpTransport { _, _, _, _ -> 0 to "" })
                    .decodeJwt(session.token!!)
                uid = (if (jwt.has("uid")) jwt.get("uid") else jwt.optString("sub")).toString()
            } catch (_: Exception) {
            }
        }
        if (uid.isEmpty()) return null
        val resolved = ApiRealtimeClient.resolveWebSocketUrl(
            userId = uid,
            customWebSocketUrl = ApiBackendConfig.DOTENV_WEBSOCKET_URL,
            envWebSocketUrl = ApiBackendConfig.WEBSOCKET_URL_OVERRIDE,
            restApiBaseUrl = config.restApiBaseUrl,
        )
        return if (resolved != null && callProtocolOwner === authSnapshot() && durableCallInbox) {
            android.net.Uri.parse(resolved).buildUpon().appendQueryParameter("call_signals_v2", "true").build().toString()
        } else resolved
    }

    private val realtimeListener: ApiRealtimeClient.Listener = object : ApiRealtimeClient.Listener {
        override fun onConnected() {
            val connectedOwner = authSnapshot()
            chatScope.launch { usesDurableCallInbox(connectedOwner) }
            chatScope.launch(Dispatchers.IO) {
                val key = com.example.chat.core.messages.LocalMessageOverridePolicy.accountKey(connectedOwner.tenantId, connectedOwner.baseUrl, connectedOwner.userId.orEmpty())
                    ?: return@launch
                com.example.chat.ui.chats.detail.ReadAlertCenter.init(appContext)
                val rooms = com.example.chat.ui.chats.detail.ReadAlertCenter.watchedConversationIds(key)
                val reader = ApiChatClient(baseUrl = { connectedOwner.baseUrl }, tenantId = { connectedOwner.tenantId }, token = { connectedOwner.token },
                    transport = ScopedCallHttpTransport(connectedOwner, ::withAuthScope, sendRequests))
                for (room in rooms) {
                    if (authSnapshot() !== connectedOwner) return@launch
                    val rows = reader.listMessages(room) ?: continue
                    applyLoadedMessages(room, rows, connectedOwner)
                }
            }
            val wasDown = socketWasDisconnected
            socketWasDisconnected = false
            if (wasDown) {
                conversationsDirty = true
                chatScope.launch {
                    loadConversationsIfNeeded(force = false)
                    val activeTargets = buildSet {
                        activeConversationId?.takeIf { it.isNotEmpty() }?.let { add(it) }
                        addAll(activeConversationIds)
                    }
                    activeTargets.forEach { loadMessagesIfNeeded(it, force = false) }
                }
            }
        }

        override fun onDisconnected() {
            socketWasDisconnected = true
            conversationsDirty = true
            synchronized(chatLock) {
                dirtyRooms.addAll(_messages.keys)
            }
            activeConversationId?.takeIf { it.isNotEmpty() }?.let { dirtyRooms.add(it) }
            dirtyRooms.addAll(activeConversationIds)
        }

        override fun onSecurityNewDevice(payload: org.json.JSONObject) {
            securityEvents.tryEmit(payload)
        }

        override fun onSecureMessage(data: Map<String, Any?>) {
            secureMessageIngest?.invoke(data)
        }

        override fun onSecureMessageEdited(data: Map<String, Any?>) {
            secureMessageEditIngest?.invoke(data)
        }

        override fun onConfigChanged() {
            // Telegram-style push invalidation: revalidate (ETag) on the hint;
            // the fresh document activates on the next app start.
            chatScope.launch(Dispatchers.IO) { runCatching { serverConfig.fetch() } }
        }

        override fun onReactionAggregate(data: Map<String, Any?>, capturedScope: Any?) {
            val owner = capturedScope as? ApiAccountScope ?: return
            val aggregate = runCatching { ReactionAggregate.parse(data) }.getOrNull() ?: return
            applyReactionAggregate(aggregate, owner)
        }

        override fun onMessageLifecycleChanged(roomId: String, messageId: String?, consumed: Boolean, capturedScope: Any?) {
            val owner = capturedScope as? ApiAccountScope ?: return
            if (consumed && messageId != null) {
                if (!withAuthScope(owner) {
                    synchronized(chatLock) {
                        val list = _messages[roomId]
                        val index = list?.indexOfFirst { it.id == messageId } ?: -1
                        if (list != null && index >= 0) list[index] = com.example.chat.core.messages.ViewOnceTextPolicy.redact(
                            list[index].copy(metadata = list[index].metadata + mapOf("view_once" to true, "view_once_state" to "consumed")))
                    }
                }) return
                onConversationRead(roomId) // Remove local notification text, never open or mark server read.
                schedulePersistRoomMessages(roomId, owner)
                notifyChatListeners()
            }
            chatScope.launch {
                if (authSnapshot() !== owner) return@launch
                val reader = ApiChatClient(baseUrl = { owner.baseUrl }, tenantId = { owner.tenantId }, token = { owner.token },
                    transport = ScopedCallHttpTransport(owner, ::withAuthScope, sendRequests))
                val rows = withContext(Dispatchers.IO) { reader.listMessages(roomId) } ?: return@launch
                applyLoadedMessages(roomId, rows, owner)
                if (authSnapshot() === owner) {
                    conversationsDirty = true
                    loadConversationsIfNeeded(force = true)
                }
            }
        }

        override fun onNewMessage(message: ChatMessage) {
            val expected = authSnapshot()
            val myId = session.currentUser?.id
            val normalized = ApiMessageDeliveryMerge.normalizeRealtimeMessage(message, myId)
            var unknownRoom = false
            var isNewToList = false
            var convoTitle: String? = null
            var convoMuted = false
            val roomId = normalized.conversationId
            synchronized(chatLock) {
                val hadRoomInMemory = _messages.containsKey(roomId)
                val list = _messages.getOrPut(roomId) { mutableListOf() }
                // Dart: replace the optimistic sending/temp message matching
                // text + sender; otherwise append if the id is new.
                // Already confirmed over HTTP: never let the echo claim another queued message.
                // Only our own messages can replace a pending one (an incoming "ok" must not
                // swallow the "ok" we are still sending).
                val optIdx = if (list.any { it.id == normalized.id } || normalized.senderId != myId) -1
                else list.indexOfFirst { m ->
                    m.deliveryState == DeliveryState.sending && m.id.startsWith("temp_") &&
                        m.text == normalized.text && m.senderId == myId
                }
                if (optIdx != -1) {
                    // Our own queued message came back from the server: it is sent (one tick).
                    removeFromOutbox(list[optIdx].id)
                    list[optIdx] = normalized
                    list.removeAll { it !== normalized && it.id == normalized.id }
                } else {
                    val existingIdx = list.indexOfFirst { it.id == normalized.id }
                    if (existingIdx != -1) {
                        list[existingIdx] = ApiMessageDeliveryMerge.preserveMonotonicDelivery(
                            list[existingIdx],
                            normalized,
                        )
                    } else {
                        list.add(normalized)
                        isNewToList = true
                    }
                }
                if (!hadRoomInMemory && !syncedRooms.containsKey(roomId)) {
                    dirtyRooms.add(roomId)
                }
                list.sortBy { it.createdAt }
                val convIdx = _conversations.indexOfFirst { it.id == roomId }
                if (convIdx != -1) {
                    val old = _conversations.removeAt(convIdx)
                    convoTitle = old.title
                    convoMuted = old.isMuted
                    _conversations.add(
                        0,
                        old.copy(
                            lastMessageText = if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(normalized.metadata)) com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
                            else if (normalized.text.isNotEmpty()) normalized.text
                            else if (normalized.type == MessageType.image) "📷 Photo" else "📎 Attachment",
                            lastMessageTime = normalized.createdAt,
                            lastMessageSenderId = normalized.senderId,
                            unreadCount = ApiUnreadPolicy.afterRealtime(
                                previous = old.unreadCount,
                                isNewMessage = isNewToList,
                                fromSelf = normalized.senderId == myId,
                                conversationVisible = roomId == activeConversationId || roomId in activeConversationIds,
                                isContentMessage = normalized.type != MessageType.system &&
                                    !normalized.isDeletedForEveryone && !normalized.isDeletedForMe && !normalized.isRevokedBySender,
                            ),
                        ),
                    )
                } else {
                    unknownRoom = true
                }
            }
            if (unknownRoom) {
                conversationsDirty = true
                chatScope.launch { loadConversationsIfNeeded(force = false) }
            }
            // Tell the sender it reached this phone (their second grey tick).
            if (normalized.senderId.isNotEmpty() && normalized.senderId != session.currentUser?.id) {
                realtime.sendDelivered(normalized.conversationId)
            }
            typingUntil.remove("${normalized.conversationId}|${normalized.senderId}")
            if ((normalized.conversationId == activeConversationId || normalized.conversationId in activeConversationIds) &&
                normalized.senderId != session.currentUser?.id
            ) {
                markConversationRead(normalized.conversationId)
            }
            schedulePersistRoomMessages(roomId, expected)
            schedulePersistConversations(expected)
            notifyChatListeners()
            if (isNewToList) runCatching { onIncomingMessage?.invoke(normalized, convoTitle, convoMuted) }
        }

        override fun onMessageEdited(roomId: String, messageId: String, newContent: String, editedAt: Instant) {
            val expected = authSnapshot()
            synchronized(chatLock) {
                val list = _messages[roomId] ?: return@synchronized
                val idx = list.indexOfFirst { it.id == messageId }
                if (idx != -1) {
                    list[idx] = list[idx].copy(text = newContent, editedAt = editedAt)
                    if (idx == list.size - 1) {
                        val convIdx = _conversations.indexOfFirst { it.id == roomId }
                        if (convIdx != -1) {
                            _conversations[convIdx] = _conversations[convIdx].copy(lastMessageText = newContent)
                        }
                    }
                }
            }
            schedulePersistRoomMessages(roomId, expected)
            schedulePersistConversations(expected)
            notifyChatListeners()
        }

        override fun onMessageDeleted(roomId: String, messageId: String) {
            val expected = authSnapshot()
            secureMessageDeleteIngest?.invoke(mapOf("room_id" to roomId, "id" to messageId, "for_everyone" to true))
            var gone: List<com.example.chat.data.local.AttachmentRef> = emptyList()
            synchronized(chatLock) {
                val list = _messages[roomId] ?: return@synchronized
                gone = com.example.chat.data.media.AttachmentRefs.refsOf(roomId, list.filter { it.id == messageId })
                list.removeAll { it.id == messageId }
                val convIdx = _conversations.indexOfFirst { it.id == roomId }
                if (convIdx != -1) {
                    val lastMsg = list.lastOrNull()
                    _conversations[convIdx] = _conversations[convIdx].copy(
                        lastMessageText = if (lastMsg != null) {
                            if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(lastMsg.metadata)) com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
                            else if (lastMsg.text.isNotEmpty()) lastMsg.text
                            else if (lastMsg.type == MessageType.image) "📷 Photo" else "📎 Attachment"
                        } else "",
                        lastMessageTime = lastMsg?.createdAt ?: _conversations[convIdx].lastMessageTime,
                    )
                }
            }
            notifyGone(gone)
            schedulePersistRoomMessages(roomId, expected)
            schedulePersistConversations(expected)
            notifyChatListeners()
        }

        override fun onMessagesCleared(roomId: String) {
            val expected = authSnapshot()
            var gone: List<com.example.chat.data.local.AttachmentRef> = emptyList()
            synchronized(chatLock) {
                gone = com.example.chat.data.media.AttachmentRefs.refsOf(roomId, _messages[roomId] ?: emptyList())
                _messages[roomId]?.clear()
                val convIdx = _conversations.indexOfFirst { it.id == roomId }
                if (convIdx != -1) {
                    _conversations[convIdx] = _conversations[convIdx].copy(lastMessageText = "")
                }
            }
            notifyGone(gone)
            schedulePersistRoomMessages(roomId, expected)
            schedulePersistConversations(expected)
            notifyChatListeners()
        }

        override fun onScopedCallSignal(data: Map<String, Any?>, capturedScope: Any?) {
            val expected = capturedScope as? ApiAccountScope ?: return
            callCoordinator.socketSignal(expected, data)
        }

        override fun onMessagesRead(roomId: String, messageIds: List<String>, readerId: String) {
            // Legacy room-wide read is an unread cursor, not per-message or all-group proof.
            // Exact receipt state is repaired by history/Info and message_receipt_updated.
        }

        override fun onScopedMessageReceiptUpdated(event: MessageReceiptEvent, capturedScope: Any?) {
            val expected = capturedScope as? ApiAccountScope ?: return
            if (authSnapshot() !== expected) return
            val myId = expected.userId ?: return
            var changed = false
            withAuthScope(expected) { synchronized(chatLock) {
                val list = _messages[event.roomId] ?: return@synchronized
                val index = list.indexOfFirst { it.id == event.messageId && it.senderId == myId }
                if (index < 0) return@synchronized
                val current = list[index]
                list[index] = current.copy(
                    deliveryState = when { event.allRead -> DeliveryState.read; event.allDelivered -> DeliveryState.delivered; else -> DeliveryState.sent },
                    readAt = if (event.allRead) current.readAt else null, // A member's timestamp is not the aggregate group-read time.
                    recipientReceipts = current.recipientReceipts + (event.userId to event.times),
                )
                changed = true
            } }
            if (changed) { schedulePersistRoomMessages(event.roomId, expected); notifyChatListeners() }
        }

        override fun onScopedInitialPresence(ids: List<String>, capturedScope: Any?) {
            val owner = capturedScope as? ApiAccountScope ?: return
            withAuthScope(owner) { onInitialPresence(ids) }
        }
        override fun onScopedPresence(userId: String, online: Boolean, lastSeen: String?, capturedScope: Any?) {
            val owner = capturedScope as? ApiAccountScope ?: return
            withAuthScope(owner) { onPresence(userId, online, lastSeen) }
        }
        override fun onScopedPresenceChanged(userId: String, online: Boolean, lastSeen: String?, approx: String?, capturedScope: Any?) {
            val owner = capturedScope as? ApiAccountScope ?: return
            withAuthScope(owner) { onPresenceChanged(userId, online, lastSeen, approx) }
        }
        override fun onScopedTyping(roomId: String, userId: String, typing: Boolean, capturedScope: Any?) {
            val owner = capturedScope as? ApiAccountScope ?: return
            withAuthScope(owner) { onTyping(roomId, userId, typing) }
        }

        override fun onInitialPresence(onlineUserIds: List<String>) {
            (onlineIds.toSet() + onlineUserIds).forEach(::bumpPresenceRevision)
            onlineIds.clear()
            onlineIds.addAll(onlineUserIds)
            notifyChatListeners()
        }

        override fun onPresence(userId: String, isOnline: Boolean, lastSeen: String?) {
            bumpPresenceRevision(userId)
            if (isOnline) onlineIds.add(userId) else onlineIds.remove(userId)
            val seen = parseIsoOrNull(lastSeen)
            if (seen != null) lastSeenById[userId] = seen
            else lastSeenById.remove(userId)
            approxPresenceById.remove(userId)
            notifyChatListeners()
            for (l in contactEventListeners) runCatching { l.onContactOnlineChanged(userId, isOnline) }
        }

        override fun onPresenceChanged(userId: String, online: Boolean, lastSeenAt: String?, approx: String?) {
            bumpPresenceRevision(userId)
            // Server already privacy-filtered this view for us; store exactly
            // what it said and drop everything else (fail-closed).
            if (online) onlineIds.add(userId) else onlineIds.remove(userId)
            val seen = parseIsoOrNull(lastSeenAt)
            if (seen != null) lastSeenById[userId] = seen else lastSeenById.remove(userId)
            if (approx != null) approxPresenceById[userId] = approx else approxPresenceById.remove(userId)
            notifyChatListeners()
            for (l in contactEventListeners) runCatching { l.onContactOnlineChanged(userId, online) }
        }

        override fun onScopedMessagesDelivered(roomId: String, messageIds: List<String>, receiverId: String, capturedScope: Any?) {
            if (!com.example.chat.ReleaseFeatures.SCOPED_DELIVERY_RECEIPTS) return
            val expected = capturedScope as? ApiAccountScope ?: return
            val myId = expected.userId ?: return
            val ids = messageIds.toSet()
            var changed = false
            if (!withAuthScope(expected) {
                synchronized(chatLock) {
                    val conv = _conversations.firstOrNull { it.id == roomId }
                    if (conv?.isDirect == true && receiverId != myId && receiverId in conv.participantIds) {
                        val list = _messages[roomId]
                        if (list != null) for (i in list.indices) {
                            val m = list[i]
                            if (m.id in ids && m.senderId == myId && m.deliveryState == DeliveryState.sent) {
                                list[i] = m.copy(deliveryState = DeliveryState.delivered)
                                changed = true
                            }
                        }
                    }
                }
            }) return
            if (changed) { schedulePersistRoomMessages(roomId, expected); notifyChatListeners() }
        }

        override fun onMessagesDelivered(roomId: String, deliveredUpTo: String) {
            val expected = authSnapshot()
            val upTo = parseIsoOrNull(deliveredUpTo) ?: return
            val myId = session.currentUser?.id ?: return
            var changed = false
            synchronized(chatLock) {
                val list = _messages[roomId] ?: return@synchronized
                for (i in list.indices) {
                    val m = list[i]
                    if (m.senderId == myId && m.deliveryState == DeliveryState.sent && !m.createdAt.isAfter(upTo)) {
                        list[i] = m.copy(deliveryState = DeliveryState.delivered)
                        changed = true
                    }
                }
            }
            if (changed) {
                schedulePersistRoomMessages(roomId, expected)
                notifyChatListeners()
            }
        }

        override fun onTyping(roomId: String, userId: String, isTyping: Boolean) {
            if (userId == session.currentUser?.id) return
            val key = "$roomId|$userId"
            if (isTyping) typingUntil[key] = System.currentTimeMillis() + TYPING_TTL_MS else typingUntil.remove(key)
            notifyChatListeners()
            for (l in contactEventListeners) runCatching { l.onContactTypingChanged(roomId, userId, isTyping) }
        }
    }

    // ---- Presence / typing / read state from the custom backend socket ----

    /** Additive fan-out of live socket presence/typing events for the in-app
     *  event toasts (RichChatRealtimeService gating decides whether a toast
     *  actually shows). Invoked synchronously on the socket scheduler thread
     *  after internal state updates; listener failures are swallowed so the
     *  socket handler stays healthy. */
    interface ContactEventListener {
        fun onContactOnlineChanged(userId: String, online: Boolean)
        fun onContactTypingChanged(roomId: String, userId: String, isTyping: Boolean)
    }

    private val contactEventListeners =
        java.util.concurrent.CopyOnWriteArrayList<ContactEventListener>()

    fun addContactEventListener(listener: ContactEventListener) {
        contactEventListeners.addIfAbsent(listener)
    }

    fun removeContactEventListener(listener: ContactEventListener) {
        contactEventListeners.remove(listener)
    }

    private val onlineIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val presenceRevisions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun bumpPresenceRevision(userId: String) { presenceRevisions.merge(userId, 1L) { old, step -> old + step } }
    fun presenceRevisionOf(userId: String): Long = presenceRevisions[userId] ?: 0L
    private val lastSeenById = java.util.concurrent.ConcurrentHashMap<String, Instant>()
    private val approxPresenceById = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val typingUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Online per the backend (respects the other user's "who can see me online" setting). */
    fun isUserOnline(userId: String): Boolean = userId in onlineIds

    /** Last-seen time the backend published, or null when unknown/hidden. */
    fun lastSeenOf(userId: String): Instant? = lastSeenById[userId]

    /** Telegram-style approximation bucket when the server hides exact
     *  presence (recently | within_week | within_month | long_ago). */
    fun approxPresenceOf(userId: String): String? = approxPresenceById[userId]

    /** One-shot privacy-filtered presence for the chat header (research 5.2:
     *  header shows online / last seen / bucket; nothing when the server
     *  says nothing). */
    suspend fun fetchPresence(userId: String, expected: ApiAccountScope = authSnapshot()): ApiPresenceClient.PresenceView {
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        val result = withContext(Dispatchers.IO) { scopedPresenceClient(expected).presence(userId) }
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        return result
    }

    /** POST /presence/heartbeat (REST presence transport; server believes
     *  online for 90s only). Fail-closed false. */
    suspend fun presenceHeartbeat(online: Boolean, expected: ApiAccountScope = authSnapshot()): Boolean {
        if (authSnapshot() !== expected || (online && !syncForeground)) return false
        val result = withContext(Dispatchers.IO) { scopedPresenceClient(expected).heartbeat(online) }
        return authSnapshot() === expected && result
    }

    fun presencePrivacy(expected: ApiAccountScope = authSnapshot()): ApiPresenceClient.PresencePrivacy? {
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        val result = scopedPresenceClient(expected).privacy()
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        return result
    }

    fun putPresencePrivacy(lastSeenPolicy: String, onlinePolicy: String, exceptions: List<String>?, expected: ApiAccountScope = authSnapshot()): ApiPresenceClient.PresencePrivacy? {
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        val result = scopedPresenceClient(expected).putPrivacy(lastSeenPolicy, onlinePolicy, exceptions)
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        return result
    }

    // --- Contacts plane (research 5.3) ------------------------------------

    fun contactsPlane(): ApiContactsClient = contactsClient
    fun contactsPlane(expected: ApiAccountScope): ApiContactsClient = ApiContactsClient(
        baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
        transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests),
    )

    /** Exact public profile lookup, bound to the account that opened contact info. */
    suspend fun peerProfile(username: String, userId: String, expected: ApiAccountScope): UserProfile {
        val reader = ApiAuthClient(
            baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
            transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests),
        )
        val profile = withContext(Dispatchers.IO) { ApiChatClient.userFromApi(reader.profileByUsername(username)) }
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        check(profile.id == userId) { "Contact profile identity changed." }
        if (!withAuthScope(expected) {
            synchronized(chatLock) {
                val index = _users.indexOfFirst { it.id == userId }
                if (index >= 0) _users[index] = _users[index].copy(
                    displayName = profile.displayName, username = profile.username, about = profile.about,
                    avatarUrl = profile.avatarUrl, bannerUrl = profile.bannerUrl,
                )
                _conversations.replaceAll { room ->
                    if (room.isDirect && userId in room.participantIds) room.copy(avatarUrl = profile.avatarUrl) else room
                }
            }
        }) throw ApiScopeChangedException()
        notifyChatListeners()
        return profile
    }


    // --- Call history plane (research 5.4) ---------------------------------

    fun callHistoryPlane(): ApiCallHistoryClient = callHistoryClient

    /** True while [userId] is typing in [roomId] (expires after a few seconds without updates). */
    fun isTyping(roomId: String, userId: String): Boolean {
        val until = typingUntil["$roomId|$userId"] ?: return false
        if (until >= System.currentTimeMillis()) return true
        typingUntil.remove("$roomId|$userId")
        return false
    }

    /** Any other participant typing in [roomId]. */
    fun anyoneTyping(roomId: String): Boolean =
        typingUntil.keys.any { it.startsWith("$roomId|") && isTyping(roomId, it.substringAfter('|')) }

    /** Publishes our typing state to the room (fire-and-forget). */
    fun sendTyping(roomId: String, isTyping: Boolean, expected: ApiAccountScope = authSnapshot()) {
        if (authSnapshot() !== expected || expected.token == null) return
        if (isTyping && (!syncForeground || (activeConversationId != roomId && roomId !in activeConversationIds))) return
        withAuthScope(expected) { realtime.sendTyping(roomId, isTyping) }
    }

    /** Publishes voice-note recording state through the custom backend socket. */
    fun sendRecording(roomId: String, isRecording: Boolean): Boolean {
        val expected = authSnapshot()
        if (expected.token.isNullOrBlank() || roomId.isBlank()) return false
        var queued = false
        withAuthScope(expected) { queued = realtime.sendRecording(roomId, isRecording) }
        return queued
    }

    /** Marks the room read on the server and clears the local unread badge. */
    fun markConversationRead(chatId: String) {
        if (session.token == null || chatId.isEmpty()) return
        try { onConversationRead(chatId) } catch (_: Exception) {}
        var hadUnread = false
        synchronized(chatLock) {
            val idx = _conversations.indexOfFirst { it.id == chatId }
            if (idx != -1 && _conversations[idx].unreadCount > 0) {
                _conversations[idx] = _conversations[idx].copy(unreadCount = 0)
                hadUnread = true
            }
        }
        if (hadUnread) notifyChatListeners()
        chatScope.launch { chat.markRoomRead(chatId) }
    }

    // ---- Push / background notification hooks (set by the locator) ----

    /** A chat was opened / read here: its notification can go. */
    @Volatile var onConversationRead: (String) -> Unit = {}
    @Volatile private var lastAdoptEpochMs: Long = 0L

    fun activeConversationIdOrNull(): String? = activeConversationId

    fun isConversationMuted(roomId: String): Boolean = conversations.firstOrNull { it.id == roomId }?.isMuted == true

    fun conversationTitle(roomId: String): String? = conversations.firstOrNull { it.id == roomId }?.title

    /** A push arrived (app in background or just started by it): reconnect the socket, tell the
     *  server the message reached this phone, and resync so the chat is current when opened. */
    fun onPushMessage(roomId: String?) {
        if (session.token == null) return
        realtime.connect()
        startBackgroundSync()
        chatScope.launch {
            if (!roomId.isNullOrEmpty()) {
                chat.markRoomDelivered(roomId)
                dirtyRooms.add(roomId)
            }
            conversationsDirty = true
            loadConversationsIfNeeded(force = true)
            if (!roomId.isNullOrEmpty() && roomId == activeConversationId) loadMessagesIfNeeded(roomId, force = true)
        }
    }

    @Volatile private var lastPrivacySync: String? = null

    /** Pushes the server-enforced privacy settings (PATCH /chat/settings) when they changed
     *  for the signed-in user. Safe to call often: identical payloads are not re-sent. */
    fun syncChatPrivacy(settings: Map<String, Any?>) {
        val userId = session.currentUser?.id ?: return
        if (session.token == null) return
        val key = userId + ":" + settings.toSortedMap().toString()
        if (key == lastPrivacySync) return
        lastPrivacySync = key
        chatScope.launch {
            val ok = chat.updateChatSettings(settings)
            if (!ok && lastPrivacySync == key) lastPrivacySync = null // retry on the next change/tick
        }
    }

    /** Rich-realtime migration seam: delivery receipt for a conversation via
     *  the REST chat plane (replaces the mark_conversation_delivered rpc). */
    suspend fun markConversationDeliveredBackend(conversationId: String) =
        withContext(Dispatchers.IO) { chat.markRoomDelivered(conversationId) }

    /** Remote settings-blob sync seam for the preferences controller
     *  (RemoteSettingsStore over /chat/settings extras): fetch returns
     *  (blob, updatedAt), null on transport failure. Push replaces the blob. */
    private fun scopedSettingsClient(expected: ApiAccountScope): ApiChatClient = ApiChatClient(
        baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
        transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests),
    )
    suspend fun remoteSettingsFetch(expected: ApiAccountScope = authSnapshot()): Pair<Map<String, Any?>?, String?>? =
        withContext(Dispatchers.IO) {
            if (authSnapshot() !== expected) throw ApiScopeChangedException()
            val result = scopedSettingsClient(expected).getChatSettingsExtras()
            if (authSnapshot() !== expected) throw ApiScopeChangedException()
            result
        }

    suspend fun remoteSettingsPush(snapshot: Map<String, Any?>, expected: ApiAccountScope = authSnapshot()): Boolean =
        withContext(Dispatchers.IO) {
            if (authSnapshot() !== expected) throw ApiScopeChangedException()
            val result = scopedSettingsClient(expected).updateChatSettings(mapOf("extras" to snapshot))
            if (authSnapshot() !== expected) throw ApiScopeChangedException()
            result
        }

    /** Hook set by the locator: true while a call is in progress (token refresh waits until it ends). */
    @Volatile var isCallActive: () -> Boolean = { false }

    /** Reachability probe for the connection banner: GET <base>/health. Throws on failure. */
    suspend fun pingBackend() = withContext(Dispatchers.IO) {
        val base = config.restApiBaseUrl.trimEnd('/').replace(Regex("/api/v[0-9]+$"), "")
        val response = healthTransport.execute("GET", "$base/health", emptyMap(), null)
        if (response.first !in 200..299) throw Exception("Backend health check failed (${response.first})")
    }
    private val healthTransport = OkHttpApiTransport()

    private val realtime: ApiRealtimeClient = ApiRealtimeClient(
        webSocketUrl = { resolveWebSocketUrl() },
        // Every connect/reconnect and heartbeat re-checks this gate, including
        // auth/network/push callbacks. Background nudges use bounded REST only.
        token = { if (syncForeground) session.token else null },
        // Reconnect-storm-safe full jitter (500ms base, 30s cap): every retry is
        // uniformly spread, so a server restart never sees a thundering herd.
        backoff = com.example.chat.data.network.BackoffPolicy(
            baseMs = 500L, maxMs = 30_000L, fullJitter = true,
        ),
        connectionScopeIdentity = { authSnapshot() },
        connectionScopeGuard = { expected, action -> (expected as? ApiAccountScope)?.let { withAuthScope(it, action) } ?: false },
    ).apply { listener = realtimeListener }

    /** Content-free push nudge (FCM or any wake transport): the message
     *  itself told us nothing, so do a bounded sync over the authenticated
     *  channel and emit incoming-message events for notification posting. */
    private val pushSyncMutex = kotlinx.coroutines.sync.Mutex()
    private val notifiedPushMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun onPushNudge() {
        if (session.token == null || DemoMode.active) return
        chatScope.launch(Dispatchers.IO) { syncFromPushNudge() }
    }

    suspend fun syncFromPushNudge(expected: ApiAccountScope = authSnapshot()) {
        if (session.token == null || DemoMode.active || authSnapshot() !== expected) return
        pushSyncMutex.withLock {
            if (expected.token == null || DemoMode.active || authSnapshot() !== expected) return@withLock
            if (synchronized(chatLock) { _conversations.isEmpty() }) {
                hydrateLocalStateForScope(expected)
            }
            val (conversationsBefore, cachedBeforeByRoom) = synchronized(chatLock) {
                _conversations.toList() to _messages.mapValues { (_, list) -> list.toList() }
            }
            ensureFreshToken()
            ensureOutboxLoaded(expected)
            flushOutbox()
            conversationsDirty = true
            loadConversationsIfNeeded(force = true)
            if (authSnapshot() !== expected) return@withLock
            val (conversationsAfter, dirtySnapshot, syncedSnapshot) = synchronized(chatLock) {
                Triple(_conversations.toList(), dirtyRooms.toSet(), syncedRooms.keys.toSet())
            }
            val targetRooms = ApiPushSyncHelper.selectRoomsToSyncForPush(
                conversationsBefore = conversationsBefore,
                conversationsAfter = conversationsAfter,
                dirtyRoomIds = dirtySnapshot,
                syncedRoomIds = syncedSnapshot,
                cachedMessagesByRoom = cachedBeforeByRoom,
            )
            val myUserId = expected.userId
            for (conv in targetRooms) {
                if (authSnapshot() !== expected) return@withLock
                hydrateRoomMessagesFromCacheIfNeeded(conv.id)
                val msgsBefore = synchronized(chatLock) { _messages[conv.id]?.toList().orEmpty() }
                if (com.example.chat.ReleaseFeatures.SCOPED_DELIVERY_RECEIPTS && conv.isDirect) {
                    val scopedChat = ApiChatClient(
                        baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
                        transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests),
                    )
                    val fetched = withContext(Dispatchers.IO) { scopedChat.listMessages(conv.id) }
                    if (authSnapshot() !== expected) return@withLock
                    if (fetched == null || !applyLoadedMessages(conv.id, fetched, expected)) continue
                    val incomingIds = fetched.filter {
                        it.senderId != myUserId && !it.isDeletedForEveryone && !it.isDeletedForMe && !it.isRevokedBySender
                    }.map { it.id }.distinct().take(100)
                    if (incomingIds.isNotEmpty()) withContext(Dispatchers.IO) {
                        scopedChat.markFetchedMessagesDelivered(conv.id, incomingIds)
                    }
                } else loadMessages(conv.id)
                if (authSnapshot() !== expected) return@withLock
                val msgsAfter = synchronized(chatLock) { _messages[conv.id]?.toList().orEmpty() }
                val freshIncoming = ApiPushSyncHelper.findNewIncomingMessagesForPush(
                    myUserId = myUserId,
                    conversation = conv,
                    messagesBefore = msgsBefore,
                    messagesAfter = msgsAfter,
                    alreadyNotifiedIds = notifiedPushMessageIds,
                )
                for (msg in freshIncoming) {
                    if (notifiedPushMessageIds.add(msg.id)) {
                        runCatching { onIncomingMessage?.invoke(msg, conv.title, conv.isMuted) }
                    }
                }
            }
        }
    }

    /** Network regained after loss: reconnect the socket now instead of waiting out the backoff.
     *  No-op while connected, connecting or signed out. */
    fun onNetworkRegained() {
        realtime.reconnectNow()
        chatScope.launch { flushOutbox() } // send what was typed while offline
        try { retryFcmTokenHook?.invoke() } catch (_: Exception) {}
    }

    /** Invalidates local media copies for deleted/removed messages. Set by the locator BEFORE initialize(). */
    @Volatile var onAttachmentsGone: (List<com.example.chat.data.local.AttachmentRef>) -> Unit = {}

    /** Wired by ServiceLocator: secure-plane realtime envelopes (MLS) go to
     *  the conversations repository for decrypt+merge with gap healing. */
    @Volatile var secureMessageIngest: ((Map<String, Any?>) -> Unit)? = null

    /** MLS edit fan-out (chat UI matrix): re-decrypt + update in place. */
    @Volatile var secureMessageEditIngest: ((Map<String, Any?>) -> Unit)? = null

    /** Delete-for-everyone fan-out: tombstone the local copy. */
    @Volatile var secureMessageDeleteIngest: ((Map<String, Any?>) -> Unit)? = null

    /** Wired by ServiceLocator. Binds sessions to this device's Ed25519 key:
     *  login/register send x-device-id + x-device-public-key; every refresh
     *  signs the presented refresh token as x-device-proof. Null = legacy
     *  unbound sessions (still accepted by the server). */
    @Volatile var deviceIdentity: DeviceIdentityManager? = null

    /** Optional quarantine hooks wired by ServiceLocator for non-API stores (ConversationsRepository, MLS, SQLCipher, Vault). */
    @Volatile var onBeforeAccountSwitch: (suspend (oldHash: String?, newHash: String?) -> Unit)? = null
    @Volatile var onAfterAccountSwitch: (suspend (oldHash: String?, newHash: String?) -> Unit)? = null
    @Volatile var isConversationProtectedHook: (String) -> Boolean = { false }

    fun currentAccountKey(): String? {
        val snap = authSnapshot()
        val uid = snap.userId?.takeIf { it.isNotBlank() } ?: return null
        if (DemoMode.isDemoUser(uid)) return null
        return ApiAccountRoutingCanonicalizer.accountKey(snap.tenantId, snap.baseUrl, uid)
    }

    fun currentAccountHash(): String? = currentAccountKey()?.let { ApiAccountRoutingCanonicalizer.accountHash(it) }

    /** Server security notifications (currently: new device signed in and
     *  awaiting confirmation). Collected by the UI layer to prompt the user
     *  to confirm or kill the session (48h auto-confirm server-side). */
    val securityEvents = kotlinx.coroutines.flow.MutableSharedFlow<org.json.JSONObject>(
        replay = 1, extraBufferCapacity = 8,
    )
    private fun notifyGone(refs: List<com.example.chat.data.local.AttachmentRef>) {
        if (refs.isNotEmpty()) runCatching { onAttachmentsGone(refs) }
    }

    private val chatCoroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }

    @Volatile private var _chatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + chatCoroutineExceptionHandler)
    private val chatScope: CoroutineScope get() = _chatScope

    private fun resetSessionCoroutineScope() {
        val prev = _chatScope
        _chatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + chatCoroutineExceptionHandler)
        runCatching { prev.cancel() }
    }
    private val chatLock = Any()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun notifyChatListeners() {
        // Socket and polling callbacks run on worker threads. Compose observers must
        // be notified on the UI thread, just like Flutter's single event loop.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            listeners.forEach { it() }
        } else uiHandler.post { listeners.forEach { it() } }
    }

    private val _conversations = mutableListOf<Conversation>()
    private val _users = mutableListOf<UserProfile>()
    private val _messages = mutableMapOf<String, MutableList<ChatMessage>>()

    @Volatile private var snapshotCoordinator: ApiSnapshotCacheCoordinator? = null
    private val conversationsSyncMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var conversationsSyncedFromServer = false
    @Volatile private var conversationsDirty = true
    @Volatile private var lastConversationsSyncMs = 0L
    @Volatile private var socketWasDisconnected = false
    private val syncedRooms = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val dirtyRooms = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val hydratedRoomSnapshots = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun configureSnapshotCache(store: SnapshotCacheStore) {
        snapshotCoordinator = ApiSnapshotCacheCoordinator(store)
    }

    private fun resetSyncTracking() {
        conversationsSyncedFromServer = false
        conversationsDirty = true
        lastConversationsSyncMs = 0L
        socketWasDisconnected = false
        syncedRooms.clear()
        dirtyRooms.clear()
        hydratedRoomSnapshots.clear()
        notifiedPushMessageIds.clear()
    }

    private fun snapshotNamespaceFor(expected: ApiAccountScope): String? {
        val uid = expected.userId?.takeIf { it.isNotBlank() } ?: return null
        if (DemoMode.isDemoUser(uid)) return null
        val key = ApiAccountRoutingCanonicalizer.accountKey(expected.tenantId, expected.baseUrl, uid) ?: return null
        return ApiAccountRoutingCanonicalizer.accountHash(key)
    }

    private fun schedulePersistConversations(expected: ApiAccountScope = authSnapshot()) {
        val coord = snapshotCoordinator ?: return
        val ns = snapshotNamespaceFor(expected) ?: return
        val snapshot = synchronized(chatLock) { _conversations.toList() }
        chatScope.launch(Dispatchers.IO) {
            if (authSnapshot() !== expected) return@launch
            coord.persistConversations(ns, snapshot)
        }
    }

    private fun schedulePersistUsers(expected: ApiAccountScope = authSnapshot()) {
        val coord = snapshotCoordinator ?: return
        val ns = snapshotNamespaceFor(expected) ?: return
        val snapshot = synchronized(chatLock) { _users.toList() }
        chatScope.launch(Dispatchers.IO) {
            if (authSnapshot() !== expected) return@launch
            coord.persistUsers(ns, snapshot)
        }
    }

    private fun schedulePersistRoomMessages(roomId: String, expected: ApiAccountScope = authSnapshot()) {
        if (roomId.isBlank()) return
        val snapshot = synchronized(chatLock) { _messages[roomId]?.toList() } ?: return
        chatScope.launch(Dispatchers.IO) {
            if (authSnapshot() !== expected) return@launch
            val key = com.example.chat.core.messages.LocalMessageOverridePolicy.accountKey(expected.tenantId, expected.baseUrl, expected.userId.orEmpty())
            if (key != null) {
                com.example.chat.ui.chats.detail.ReadAlertCenter.init(appContext)
                com.example.chat.ui.chats.detail.ReadAlertCenter.ensureWatching(key)
                com.example.chat.ui.chats.detail.ReadAlertCenter.onMessages(key, roomId, snapshot)
            }
            if (authSnapshot() !== expected) return@launch
            val coord = snapshotCoordinator ?: return@launch
            val ns = snapshotNamespaceFor(expected) ?: return@launch
            coord.persistMessages(ns, roomId, snapshot)
        }
    }

    private fun mergeCachedMessagesIntoRoomLocked(roomId: String, cached: List<ChatMessage>): Boolean {
        if (cached.isEmpty()) return false
        val dedupedCached = ApiMessageDeliveryMerge.deduplicateById(cached)
        val existing = _messages[roomId]
        if (existing == null || existing.isEmpty()) {
            _messages[roomId] = dedupedCached.toMutableList()
            return true
        }
        val byId = existing.associateBy { it.id }
        val merged = dedupedCached.map { c ->
            ApiMessageDeliveryMerge.preserveMonotonicDelivery(byId[c.id], c)
        }
        val mergedIds = merged.asSequence().map { it.id }.filter { it.isNotBlank() }.toHashSet()
        val extra = existing.filter { e -> e.id.isBlank() || e.id !in mergedIds }
        val combined = ApiMessageDeliveryMerge.deduplicateById(merged + extra).sortedBy { it.createdAt }
        if (combined == existing) return false
        _messages[roomId] = combined.toMutableList()
        return true
    }

    private suspend fun hydrateLocalStateForScope(expected: ApiAccountScope) {
        val coord = snapshotCoordinator ?: return
        val ns = snapshotNamespaceFor(expected) ?: return
        if (authSnapshot() !== expected) return
        val cachedConversations = withContext(Dispatchers.IO) { coord.readConversations(ns) }
        val cachedUsers = withContext(Dispatchers.IO) { coord.readUsers(ns) }
        if (authSnapshot() !== expected) return

        var hydratedHeader = false
        withAuthScope(expected) {
            synchronized(chatLock) {
                if (_conversations.isEmpty() && !cachedConversations.isNullOrEmpty()) {
                    _conversations.addAll(cachedConversations.filter { it.id.isNotBlank() }.distinctBy { it.id })
                    hydratedHeader = true
                }
                if (_users.isEmpty() && !cachedUsers.isNullOrEmpty()) {
                    _users.addAll(cachedUsers.filter { it.id.isNotBlank() }.distinctBy { it.id })
                    hydratedHeader = true
                }
            }
        }
        if (hydratedHeader) notifyChatListeners()

        val roomIds = synchronized(chatLock) { _conversations.map { it.id } }
        var hydratedMessages = false
        for (roomId in roomIds) {
            if (authSnapshot() !== expected) return
            if (hydratedRoomSnapshots.contains(roomId)) continue
            val cachedMsgs = withContext(Dispatchers.IO) { coord.readMessages(ns, roomId) }
            if (authSnapshot() !== expected) return
            withAuthScope(expected) {
                if (!cachedMsgs.isNullOrEmpty()) {
                    synchronized(chatLock) {
                        if (mergeCachedMessagesIntoRoomLocked(roomId, cachedMsgs)) hydratedMessages = true
                    }
                }
                hydratedRoomSnapshots.add(roomId)
            }
        }
        if (hydratedMessages) notifyChatListeners()
    }

    private suspend fun hydrateRoomMessagesFromCacheIfNeeded(roomId: String) {
        if (roomId.isBlank() || hydratedRoomSnapshots.contains(roomId)) return
        val expected = authSnapshot()
        val coord = snapshotCoordinator ?: run {
            hydratedRoomSnapshots.add(roomId)
            return
        }
        val ns = snapshotNamespaceFor(expected) ?: return
        val cached = withContext(Dispatchers.IO) { coord.readMessages(ns, roomId) }
        if (authSnapshot() !== expected) return
        var changed = false
        withAuthScope(expected) {
            if (!cached.isNullOrEmpty()) {
                synchronized(chatLock) {
                    if (mergeCachedMessagesIntoRoomLocked(roomId, cached)) changed = true
                }
            }
            hydratedRoomSnapshots.add(roomId)
        }
        if (changed) notifyChatListeners()
    }

    /** Dart _tasks / tasks getter. */
    private val _tasks = mutableListOf<ChatTask>()
    val tasks: List<ChatTask>
        get() = synchronized(chatLock) { _tasks.toList() }

    /** Active-conversation id(s) drive the 4s poll and auto-read across single or split panes. */
    @Volatile private var activeConversationId: String? = null
    private val activeConversationIds = java.util.concurrent.CopyOnWriteArraySet<String>()
    val activeConversation: String? get() = activeConversationId
    fun setSplitActiveConversations(ids: Set<String>) {
        val cleaned = ids.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        activeConversationIds.clear()
        activeConversationIds.addAll(cleaned)
        if (cleaned.isNotEmpty()) {
            realtime.connect()
            startBackgroundSync()
            cleaned.forEach { roomId ->
                chatScope.launch {
                    try {
                        loadMessagesIfNeeded(roomId)
                        markConversationRead(roomId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
    /** Incoming (not own) realtime message: (message, conversation title, muted). Invoked outside chatLock. */
    @Volatile var onIncomingMessage: ((ChatMessage, String?, Boolean) -> Unit)? = null
    // Process lifecycle owns this UI catch-up loop, never the sticky socket service.
    @Volatile private var syncForeground = false
    @Volatile private var syncRunning = false
    @Volatile private var activeSyncJob: kotlinx.coroutines.Job? = null

    val conversations: List<Conversation> get() {
        val list = synchronized(chatLock) { _conversations.toList() }
        val state = localState("conv")
        val drafts = currentAccountKey()?.let { multiAccount.allDraftsFor(it) }.orEmpty()
        if (state.length() == 0 && drafts.isEmpty()) return list
        return list.map { c ->
            val o = state.optJSONObject(c.id)
            val draft = drafts[c.id] ?: c.draftText
            if (o == null && draft == c.draftText) c
            else c.copy(
                isPinned = o?.optBoolean("pinned", c.isPinned) ?: c.isPinned,
                isMuted = o?.optBoolean("muted", c.isMuted) ?: c.isMuted,
                isArchived = o?.optBoolean("archived", c.isArchived) ?: c.isArchived,
                draftText = draft,
            )
        }
    }

    fun getDraft(conversationId: String): String =
        currentAccountKey()?.let { multiAccount.draftFor(it, conversationId) } ?: ""

    fun setDraft(conversationId: String, draft: String) {
        val key = currentAccountKey() ?: return
        multiAccount.saveDraft(key, conversationId, draft)
        synchronized(chatLock) {
            val idx = _conversations.indexOfFirst { it.id == conversationId }
            if (idx != -1) _conversations[idx] = _conversations[idx].copy(draftText = draft)
        }
        persistAccountConversationSnapshot()
        notifyChatListeners()
    }
    val users: List<UserProfile> get() = synchronized(chatLock) { _users.toList() }
    fun getMessages(chatId: String): List<ChatMessage> {
        val list = synchronized(chatLock) { (_messages[chatId] ?: emptyList()).toList() }
        val state = localState("msg").optJSONObject(chatId) ?: return list
        return list.map { m ->
            val o = state.optJSONObject(m.id) ?: return@map m
            m.copy(isStarred = o.optBoolean("starred", m.isStarred), isPinned = o.optBoolean("pinned", m.isPinned))
        }
    }

    /** Cheap home-row tick accessor: the last message's delivery state without
     *  copying the whole list. getMessages above copies every element (plus a
     *  JSON pass) per call, which the home list triggered per row per
     *  recomposition. Local starred/pinned overlays never affect the delivery
     *  state, so it can be read straight from the cached message. */
    fun lastMessageDeliveryState(chatId: String): DeliveryState? =
        synchronized(chatLock) { _messages[chatId]?.lastOrNull()?.deliveryState }

    // ---- Device-local chat state ----
    // The custom backend has no per-user pin/mute/archive or star/pin-message API yet, so these
    // are kept on this device per account (they do not sync to the user's other devices).

    private val localStateLock = Any()
    private val localStateCache = HashMap<String, org.json.JSONObject>()

    private fun localStateKey(kind: String): String {
        val ns = currentAccountHash() ?: session.currentUser?.id ?: "anon"
        return "api_local_${kind}_$ns"
    }

    private fun localState(kind: String): org.json.JSONObject = synchronized(localStateLock) {
        val key = localStateKey(kind)
        localStateCache.getOrPut(key) {
            val stored = prefs.getString(key, null)
            if (stored != null) {
                try { org.json.JSONObject(stored) } catch (_: Exception) { org.json.JSONObject() }
            } else {
                // One-time read migration from legacy userId-keyed state when single-account upgraded.
                val legacyUid = session.currentUser?.id
                val legacyKey = if (!legacyUid.isNullOrBlank()) "api_local_${kind}_$legacyUid" else null
                val legacyRaw = if (legacyKey != null && legacyKey != key && multiAccount.accounts().size <= 1) {
                    prefs.getString(legacyKey, null)
                } else null
                if (legacyRaw != null) {
                    try { org.json.JSONObject(legacyRaw).also { prefs.edit().putString(key, legacyRaw).apply() } }
                    catch (_: Exception) { org.json.JSONObject() }
                } else {
                    org.json.JSONObject()
                }
            }
        }
    }

    private fun saveLocalState(kind: String, value: org.json.JSONObject) = synchronized(localStateLock) {
        val key = localStateKey(kind)
        localStateCache[key] = value
        try { prefs.edit().putString(key, value.toString()).apply() } catch (_: Exception) {}
    }

    /** field: pinned | muted | archived. */
    fun setConversationLocalState(conversationId: String, field: String, value: Boolean) {
        synchronized(localStateLock) {
            val state = org.json.JSONObject(localState("conv").toString())
            val entry = state.optJSONObject(conversationId) ?: org.json.JSONObject()
            entry.put(field, value)
            state.put(conversationId, entry)
            saveLocalState("conv", state)
        }
        notifyChatListeners()
    }

    /** field: starred | pinned. */
    fun setMessageLocalState(conversationId: String, messageId: String, field: String, value: Boolean) {
        synchronized(localStateLock) {
            val state = org.json.JSONObject(localState("msg").toString())
            val room = state.optJSONObject(conversationId) ?: org.json.JSONObject()
            val entry = room.optJSONObject(messageId) ?: org.json.JSONObject()
            entry.put(field, value)
            room.put(messageId, entry)
            state.put(conversationId, room)
            saveLocalState("msg", state)
        }
        notifyChatListeners()
    }


    /** True while the adopted session is the offline demo ([DemoMode]). */
    /** STRICT: a session is demo only when BOTH the token and the user are
     *  the synthetic demo identity. A mixed/corrupt restore (real token +
     *  demo user JSON) is treated as a real session: never demo-armed and
     *  never eligible for demo cleanup (peer mixed-store blocker). */
    val isDemoSession: Boolean get() =
        DemoMode.isDemoUser(session.currentUser?.id) && session.token == DemoMode.TOKEN

    /**
     * Offline demo login (NOT in Flutter): adopts a synthetic session with
     * sample data while the server is unreachable. No network calls, no
     * realtime, no FCM registration and no backend identity; real login is
     * unchanged. Refused while any session is active.
     */
    suspend fun loginDemo(): ApiUserProfile = withContext(Dispatchers.IO) {
        // Fail closed on RAW persisted credentials, not just the composed
        // isAuthenticated: a real token with an unloadable user must still
        // block demo entry (never overwritten). A STRICTLY-demo persisted
        // pair is the one exception: re-entering the demo replaces it.
        run {
            val storedToken = runCatching { sessionStore.loadToken() }.getOrNull()
            val storedUser = runCatching { sessionStore.loadUserJson() }.getOrNull()
            val anyRaw = storedToken != null || storedUser != null ||
                session.token != null || session.currentUser != null
            val strictDemo = DemoMode.isDemoRestSession(storedToken, storedUser) &&
                (session.currentUser == null || DemoMode.isDemoUser(session.currentUser?.id))
            check(!anyRaw || strictDemo) { "Sign out before starting the demo." }
        }
        // Fail closed: any live or persisted real GoTrue identity blocks demo
        // entry (never cleared as prep); hook absence also refuses.
        val goTruePresent = demoGoTruePresentHook?.invoke() ?: true
        check(!goTruePresent) { "A saved sign-in exists on this device. Sign out fully before starting the demo." }
        val user = DemoSampleData.demoUser()
        try {
            // Arm the offline guard BEFORE adopt: the adopt transition notifies
            // listeners, and no request may pass once demo entry has begun.
            DemoMode.active = true
            session.adopt(ApiAuthResult(DemoMode.TOKEN, user))
            // Seed AFTER adopt: an adopt failure leaves no demo residue in memory.
            seedDemoState()
        } catch (e: Exception) {
            // Rollback: no demo session, no demo state, no armed flag.
            synchronized(chatLock) {
                _conversations.clear()
                _messages.clear()
                _users.clear()
            }
            DemoMode.active = false
            try { session.signOut() } catch (_: Exception) {
            }
            throw e
        }
        notifyChatListeners()
        user
    }

    private fun seedDemoState() {
        synchronized(chatLock) {
            _conversations.clear()
            _conversations.addAll(DemoSampleData.demoConversations())
            _messages.clear()
            DemoSampleData.demoMessages().forEach { (id, list) -> _messages[id] = list.toMutableList() }
            _users.clear()
            _users.addAll(DemoSampleData.demoUsers())
        }
    }

    var requiresSubscription: Boolean = false
        private set
    var subscriptionErrorMessage: String? = null
        private set

    /** loadConversations: keep prior state on failure, filter tasks, and
     *  set/clear the 402 subscription state. */
    /** ApiBackendService.loadTasks: GET /tasks into _tasks, keeping prior
     *  state on failure or when unauthenticated (Dart early return/catch). */
    suspend fun loadTasks() {
        val expected = authSnapshot()
        var changed = false
        val applied = com.example.chat.data.api.publishScopedApiResult(expected, ::withAuthScope,
            fetch = { withContext(Dispatchers.IO) { tasksClient.listTasks() } },
            publish = { result ->
                if (result != null) {
                    synchronized(chatLock) {
                        _tasks.clear()
                        _tasks.addAll(result)
                    }
                    changed = true
                }
            })
        if (applied && changed) notifyChatListeners()
    }

    suspend fun loadConversations() {
        val expected = authSnapshot()
        var changed = false
        val applied = com.example.chat.data.api.publishScopedApiResult(expected, ::withAuthScope,
            fetch = { withContext(Dispatchers.IO) { chat.listRooms() } },
            publish = { result ->
                when (result) {
                    is ApiChatClient.RoomsResult.Ok -> {
                        val filtered = result.conversations.filter { !it.isTask }
                        synchronized(chatLock) {
                            if (filtered.isNotEmpty()) {
                                val serverIds = filtered.map { it.id }.toSet()
                                val pendingConversations = _conversations.filter { it.id !in serverIds && (_messages[it.id].orEmpty().any { message -> message.deliveryState == DeliveryState.sending || message.id.startsWith("temp_") } || it.draftText.isNotEmpty()) }
                                _conversations.clear()
                                _conversations.addAll(filtered)
                                _conversations.addAll(pendingConversations)
                            } else if (_conversations.isEmpty()) {
                                _conversations.clear()
                            }
                            for (c in _conversations) {
                                val cachedMsgs = _messages[c.id]
                                val latestCached = cachedMsgs?.lastOrNull()?.createdAt ?: Instant.EPOCH
                                if (c.unreadCount > 0 ||
                                    (cachedMsgs.isNullOrEmpty() && c.lastMessageText.isNotEmpty()) ||
                                    ( !cachedMsgs.isNullOrEmpty() && c.lastMessageTime.isAfter(latestCached) )
                                ) {
                                    dirtyRooms.add(c.id)
                                }
                            }
                        }
                        conversationsSyncedFromServer = true
                        conversationsDirty = false
                        lastConversationsSyncMs = System.currentTimeMillis()
                        requiresSubscription = false
                        subscriptionErrorMessage = null
                        persistAccountConversationSnapshot()
                        schedulePersistConversations(expected)
                        changed = true
                    }
                    ApiChatClient.RoomsResult.SubscriptionRequired -> {
                        requiresSubscription = true
                        subscriptionErrorMessage = "Workspace subscription required. Please select a plan."
                        changed = true
                    }
                    ApiChatClient.RoomsResult.Failed -> Unit
                }
            })
        if (applied && changed) notifyChatListeners()
    }

    /** loadMessages with Dart merge semantics: pending (sending/temp) messages
     *  whose text is absent from the remote list survive; merged + sorted. */
    /** Loads the conversation list from the backend only when dirty or not yet synced in this session. */
    suspend fun loadConversationsIfNeeded(force: Boolean = false) {
        if (session.token == null || DemoMode.active) return
        if (!force && conversationsSyncedFromServer && !conversationsDirty) return
        conversationsSyncMutex.withLock {
            if (session.token == null || DemoMode.active) return
            if (!force && conversationsSyncedFromServer && !conversationsDirty) return
            loadConversations()
        }
    }

    /**
     * Ensures cached chats are hydrated immediately and triggers one catch-up fetch
     * (with one quick retry if initial login/restore fetch failed transiently).
     */
    suspend fun ensureInitialSyncIfNeeded() {
        if (!isAuthenticated || DemoMode.active) return
        val expected = authSnapshot()
        val needsHydrate = synchronized(chatLock) { _conversations.isEmpty() }
        if (needsHydrate) hydrateLocalStateForScope(expected)
        if (!conversationsSyncedFromServer || conversationsDirty) {
            loadConversationsIfNeeded(force = false)
            if (!conversationsSyncedFromServer && session.token != null && !DemoMode.active) {
                delay(500)
                loadConversationsIfNeeded(force = false)
            }
        }
    }

    /** loadMessages with monotonic delivery ticks and pending-message merge. */
    suspend fun loadMessages(chatId: String) {
        val expected = authSnapshot()
        val remote = withContext(Dispatchers.IO) { chat.listMessages(chatId) } ?: return
        applyLoadedMessages(chatId, remote, expected)
    }

    private fun applyLoadedMessages(chatId: String, remote: List<ChatMessage>, expected: ApiAccountScope): Boolean {
        var gone: List<com.example.chat.data.local.AttachmentRef> = emptyList()
        val applied = withAuthScope(expected) {
            synchronized(chatLock) {
                val current = _messages[chatId] ?: mutableListOf()
                val merged = ApiMessageDeliveryMerge.mergeRemoteMessages(current, remote) { m ->
                    (m.deliveryState == DeliveryState.sending || m.id.startsWith("temp_")) &&
                        (isQueued(m.id) || remote.none { it.text == m.text })
                }.toMutableList()
                gone = com.example.chat.data.media.AttachmentRefs.refsGone(chatId, current.toList(), merged)
                _messages[chatId] = merged
            }
            syncedRooms[chatId] = System.currentTimeMillis()
            dirtyRooms.remove(chatId)
            hydratedRoomSnapshots.add(chatId)
        }
        if (!applied) return false
        schedulePersistRoomMessages(chatId, expected)
        notifyGone(gone)
        notifyChatListeners()
        return true
    }

    /**
     * Serves messages from local storage + live WebSocket updates continuously,
     * fetching from the backend only when the room has not been synced yet,
     * is marked dirty/stale, the socket is disconnected, or server preview leads cache.
     */
    suspend fun loadMessagesIfNeeded(chatId: String, force: Boolean = false) {
        if (chatId.isBlank() || session.token == null || DemoMode.active) return
        hydrateRoomMessagesFromCacheIfNeeded(chatId)
        val needsFetch = if (force) {
            true
        } else {
            val conv = synchronized(chatLock) { _conversations.firstOrNull { it.id == chatId } }
            val msgs = synchronized(chatLock) { _messages[chatId].orEmpty() }
            ApiMessageDeliveryMerge.shouldFetchRoomMessages(
                hasSyncedThisSession = syncedRooms.containsKey(chatId),
                isDirty = chatId in dirtyRooms,
                socketConnected = realtime.isConnected,
                conversation = conv,
                currentMessages = msgs,
            )
        }
        if (!needsFetch) return
        loadMessages(chatId)
    }

    /** startDirectChat: POST /chat/rooms/direct (no fallback), upsert at
     *  index 0 (replace if the id already exists), notify. 402 sets the
     *  subscription state with the exact Dart message, notifies, rethrows. */
    suspend fun createOrGetDirectConversation(targetUserId: String, expectedScope: ApiAccountScope? = null): Conversation {
        val expected = expectedScope ?: authSnapshot()
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        // Bind the request to the original credentials and cancel it on
        // auth/routing transition, just like scope-bound message sends.
        val scopedChat = ApiChatClient(
            baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
            transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests),
        )
        try {
            val conversation = withContext(Dispatchers.IO) { scopedChat.createDirectRoom(targetUserId) }
            if (!withAuthScope(expected) {
                synchronized(chatLock) {
                    val idx = _conversations.indexOfFirst { it.id == conversation.id }
                    if (idx != -1) _conversations.removeAt(idx)
                    _conversations.add(0, conversation)
                }
                schedulePersistConversations(expected)
            }) throw ApiScopeChangedException()
            notifyChatListeners()
            if (authSnapshot() !== expected) throw ApiScopeChangedException()
            return conversation
        } catch (e: ApiChatClient.SubscriptionRequiredException) {
            if (!withAuthScope(expected) {
                requiresSubscription = true
                subscriptionErrorMessage =
                    "Workspace subscription required. Please choose a plan to chat."
            }) throw ApiScopeChangedException()
            notifyChatListeners()
            throw e
        }
    }

    /** Read-only snapshot for GroupInfo. Not global membership authority on network failure. */
    suspend fun loadGroupMembers(roomId: String, expected: ApiAccountScope = authSnapshot()): ApiGroupMembers? {
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        val scoped = ApiChatClient(baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId },
            token = { expected.token }, transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests))
        val result = withContext(Dispatchers.IO) { scoped.listGroupMembers(roomId) }
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        return result
    }

    /** createGroup: scope-bound request and publication; never insert an old
     *  account's room into a newly switched account's list or snapshot. */
    suspend fun createGroupRoom(name: String, memberUserIds: List<String>, expectedScope: ApiAccountScope? = null): Conversation {
        val expected = expectedScope ?: authSnapshot()
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        require(name.trim().isNotEmpty() && memberUserIds.isNotEmpty() && memberUserIds.all { it.isNotBlank() }) { "Choose a group name and members." }
        val scopedChat = ApiChatClient(
            baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
            transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests),
        )
        val conversation = withContext(Dispatchers.IO) { scopedChat.createGroupRoom(name, memberUserIds) }
        if (!withAuthScope(expected) {
            synchronized(chatLock) {
                val idx = _conversations.indexOfFirst { it.id == conversation.id }
                if (idx != -1) _conversations.removeAt(idx)
                _conversations.add(0, conversation)
            }
            schedulePersistConversations(expected)
        }) throw ApiScopeChangedException()
        notifyChatListeners()
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        return conversation
    }

    /** deleteUser: local-first removal (user + their direct conversations),
     *  notify, then the backend DELETE with errors swallowed. Deleting
     *  oneself signs out (Dart). */
    suspend fun deleteUser(userId: String, expectedScope: ApiAccountScope? = null) {
        val expected = expectedScope ?: authSnapshot()
        if (authSnapshot() !== expected) throw ApiScopeChangedException()
        withContext(Dispatchers.IO) { accountDeletion.delete(expected, { authSnapshot() === expected }, userId) }
        check(withAuthScope(expected) {
            synchronized(chatLock) {
                _users.removeAll { it.id == userId }
                _conversations.removeAll { it.type == ConversationType.direct && it.participantIds.contains(userId) }
            }
        }) { "Account changed. Local deletion was not applied." }
        schedulePersistUsers(expected)
        schedulePersistConversations(expected)
        notifyChatListeners()
        // UI owns purge and logout after this verified completion; do not sign out
        // before its account-local purge or destroy the scope it needs.
    }

    /** deleteConversation: local-first removal (conversation + its messages
     *  + active id), notify, then the backend DELETE with errors
     *  swallowed. */
    suspend fun deleteConversation(chatId: String) {
        var gone: List<com.example.chat.data.local.AttachmentRef> = emptyList()
        synchronized(chatLock) {
            gone = com.example.chat.data.media.AttachmentRefs.refsOf(chatId, _messages[chatId] ?: emptyList())
            _conversations.removeAll { it.id == chatId }
            _messages.remove(chatId)
            if (activeConversationId == chatId) activeConversationId = null
        }
        notifyGone(gone)
        schedulePersistConversations()
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.deleteRoom(chatId) }
    }

    suspend fun loadUsers(query: String? = null) {
        val expected = authSnapshot()
        var changed = false
        val applied = com.example.chat.data.api.publishScopedApiResult(expected, ::withAuthScope,
            fetch = { withContext(Dispatchers.IO) { chat.listUsers(query) } },
            publish = { fetched ->
                if (fetched != null) {
                    synchronized(chatLock) {
                        if (query.isNullOrBlank()) {
                            _users.clear()
                            val seenUserIds = HashSet<String>(fetched.size)
                            for (u in fetched) {
                                if (u.id.isNotBlank() && seenUserIds.add(u.id)) {
                                    _users.add(u)
                                }
                            }
                        } else {
                            for (u in fetched) {
                                if (u.id.isBlank()) continue
                                val idx = _users.indexOfFirst { it.id == u.id }
                                if (idx == -1) _users.add(u) else _users[idx] = u
                            }
                        }
                    }
                    schedulePersistUsers(expected)
                    changed = true
                }
            })
        if (applied && changed) notifyChatListeners()
    }

    /** Dart ApiBackendService.searchUsers: loadUsers(query) over GET /chat/users?search=
     *  (with the same fallbacks), then the in-memory directory filtered by lowercase contains on
     *  displayName/username/email; self excluded unless includeSelf. */
    suspend fun searchUsers(query: String, includeSelf: Boolean = false): List<UserProfile> {
        val q = query.lowercase().trim()
        loadUsers(q)
        return com.example.chat.ui.chats.newchat.NewChatRules.filterUsers(users, q, session.currentUser?.id, includeSelf)
    }

    private val reactionLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    private fun applyReactionAggregate(aggregate: ReactionAggregate, owner: ApiAccountScope, acknowledgedToken: String? = null) {
        if (!withAuthScope(owner) {
            synchronized(chatLock) {
                val list = _messages[aggregate.roomId] ?: return@synchronized
                val index = list.indexOfFirst { it.id == aggregate.messageId }
                if (index < 0) return@synchronized
                val message = list[index]
                val oldAt = message.metadata["reaction_updated_at"]?.toString()?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
                if (oldAt != null && (oldAt > aggregate.updatedAt ||
                    (oldAt == aggregate.updatedAt && message.metadata["reaction_pending_token"] != null &&
                        message.metadata["reaction_pending_token"] != acknowledgedToken))) return@synchronized
                list[index] = message.copy(reactions = aggregate.reactions,
                    metadata = (message.metadata - "reaction_pending_token" - "reaction_before") + ("reaction_updated_at" to aggregate.updatedAt.toString()))
            }
        }) return
        schedulePersistRoomMessages(aggregate.roomId, owner)
        notifyChatListeners()
    }

    suspend fun toggleServerReaction(roomId: String, messageId: String, emoji: String, owner: ApiAccountScope) {
        require(emoji.isNotBlank() && emoji.length <= 64) { "Invalid reaction" }
        val key = "${owner.generation}:$roomId:$messageId"
        reactionLocks.getOrPut(key) { kotlinx.coroutines.sync.Mutex() }.withLock {
            val client = ApiReactionsClient(owner, ScopedCallHttpTransport(owner, ::withAuthScope, sendRequests), { authSnapshot() === owner })
            check(withContext(Dispatchers.IO) { client.supported() }) { "Reactions are unavailable on this server." }
            val token = java.util.UUID.randomUUID().toString()
            var before: ChatMessage? = null
            var remove = false
            check(withAuthScope(owner) {
                synchronized(chatLock) {
                    val list = _messages[roomId] ?: error("Open this chat again.")
                    val index = list.indexOfFirst { it.id == messageId }
                    check(index >= 0) { "Message is no longer available." }
                    val message = list[index]
                    check(!message.isDeletedForEveryone && !message.isDeletedForMe && !message.isRevokedBySender &&
                        !com.example.chat.core.messages.ViewOnceTextPolicy.restricted(message.metadata)) { "This message cannot receive reactions." }
                    before = message
                    remove = message.reactions.any { it.emoji == emoji && owner.userId.orEmpty() in it.userIds }
                    list[index] = message.copy(reactions = com.example.chat.core.messages.ReactionUpdatePolicy.toggle(message.reactions, owner.userId.orEmpty(), emoji),
                        metadata = message.metadata + mapOf("reaction_pending_token" to token, "reaction_before" to message.reactions))
                }
            }) { "Account changed." }
            notifyChatListeners()
            try {
                val aggregate = withContext(Dispatchers.IO) { client.set(messageId, if (remove) null else emoji) }
                check(aggregate.messageId == messageId && aggregate.roomId == roomId) { "Unexpected reaction response." }
                applyReactionAggregate(aggregate, owner, acknowledgedToken = token)
            } catch (error: Exception) {
                withAuthScope(owner) {
                    synchronized(chatLock) {
                        val list = _messages[roomId]
                        val index = list?.indexOfFirst { it.id == messageId } ?: -1
                        if (list != null && index >= 0 && list[index].metadata["reaction_pending_token"] == token) {
                            val old = before!!
                            list[index] = list[index].copy(reactions = old.reactions, metadata = list[index].metadata - "reaction_pending_token" - "reaction_before")
                        }
                    }
                }
                notifyChatListeners()
                if (error is kotlinx.coroutines.CancellationException) throw error
                throw IllegalStateException("Reaction was not confirmed. Refresh the chat to check its server state.", error)
            }
        }
    }

    private fun viewOnceTextClient(expected: ApiAccountScope) = ApiViewOnceTextClient(expected,
        ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests), { authSnapshot() === expected })

    suspend fun supportsViewOnceText(expected: ApiAccountScope): Boolean = withContext(Dispatchers.IO) {
        viewOnceTextClient(expected).supported()
    }

    suspend fun sendViewOnceText(roomId: String, text: String, expected: ApiAccountScope): ChatMessage {
        check(authSnapshot() === expected) { "Account changed." }
        val room = conversations.firstOrNull { it.id == roomId } ?: error("Open this chat again.")
        check(room.isDirect) { "View-once text is available in direct chats only." }
        check(!com.example.chat.ServiceLocator.conversationsRepository.isSecureChannel(roomId)) { "Encrypted view-once text is not available in this build." }
        val endSend = com.example.chat.data.messaging.AttachmentUploadTracker.begin(
            com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(expected.userId, expected.tenantId, expected.baseUrl), roomId)
        val result = try {
            check(authSnapshot() === expected) { "Account changed. Nothing sent." }
            withContext(Dispatchers.IO) { viewOnceTextClient(expected).send(roomId, text, java.util.UUID.randomUUID().toString()) }
        } finally { endSend() }
        check(withAuthScope(expected) {
            synchronized(chatLock) {
                val list = _messages.getOrPut(roomId) { mutableListOf() }
                if (list.none { it.id == result.id }) list.add(result)
                touchConversationPreview(roomId, result)
            }
        }) { "Account changed. Check the original chat for send status." }
        schedulePersistRoomMessages(roomId, expected)
        schedulePersistConversations(expected)
        notifyChatListeners()
        return result
    }

    suspend fun openViewOnceText(message: ChatMessage, expected: ApiAccountScope): String {
        check(authSnapshot() === expected && message.senderId != expected.userId) { "No longer available" }
        check(com.example.chat.core.messages.ViewOnceTextPolicy.text(message) && !com.example.chat.core.messages.ViewOnceTextPolicy.unavailable(message)) { "No longer available" }
        check(message.metadata["encrypted"] != true) { "Encrypted view-once text is not supported in this build." }
        return withContext(Dispatchers.IO) { viewOnceTextClient(expected).openPlain(message.id) }
    }

    /** Captured scheduling session. Retry IDs contain no body, credentials or raw account ID. */
    fun scheduledMessagesClient(expected: ApiAccountScope = authSnapshot()): com.example.chat.ui.automation.ScheduledMessagesPort =
        ApiScheduledMessagesClient(expected,
            ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests), ::withAuthScope,
            retryId = { fingerprint ->
                val key = "schedule_retry_$fingerprint"
                synchronized(prefs) {
                    prefs.getString(key, null) ?: java.util.UUID.randomUUID().toString().also { id ->
                        check(prefs.edit().putString(key, id).commit()) { "Could not save the schedule retry ID. Nothing was sent." }
                    }
                }
            })

    /** Fresh, account-bound new-message lookup. No merged directory/cache fallback. */
    suspend fun findExactUsername(query: String, expected: ApiAccountScope): List<UserProfile> {
        val q = com.example.chat.ui.chats.newchat.NewChatRules.normalizedUsername(query)
        if (q.isEmpty()) return emptyList()
        check(authSnapshot() === expected) { "Account changed. Open New message again." }
        val reader = ApiChatClient(
            baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { expected.token },
            transport = ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests))
        val fresh = withContext(Dispatchers.IO) { reader.listUsers(q) }
            ?: throw IllegalStateException("Could not check this username. Try again when connected.")
        check(authSnapshot() === expected) { "Account changed. Open New message again." }
        return com.example.chat.ui.chats.newchat.NewChatRules.exactUsernameMatch(fresh, q, expected.userId)
    }

    /** Dart setActiveConversation: track the room, start sync, hydrate/load messages only when needed. */
    fun setActiveConversation(conversationId: String?) {
        activeConversationId = conversationId
        if (!conversationId.isNullOrEmpty()) {
            realtime.connect()
            startBackgroundSync()
            chatScope.launch {
                try {
                    loadMessagesIfNeeded(conversationId)
                    markConversationRead(conversationId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Background catch-up loop: maintains token freshness, reconnects dropped sockets,
     * flushes the offline outbox, and fetches rooms/messages only when dirty, uncached,
     * or while WebSocket realtime is disconnected.
     */
    fun startBackgroundSync() {
        if (!syncForeground || session.token == null || DemoMode.active) return
        if (syncRunning && activeSyncJob?.isActive == true) return
        syncRunning = true
        val scope = chatScope
        lateinit var job: kotlinx.coroutines.Job
        job = scope.launch {
            try {
                var ticks = 0
                while (true) {
                    delay(4000)
                    if (!syncForeground || session.token == null || DemoMode.active) break
                    try {
                        ensureFreshToken()
                        if (session.token == null) break
                        ticks++
                        callCoordinator.tick()
                        if (!realtime.isConnected) realtime.connect()
                        flushOutbox() // messages typed while offline
                        val targets = buildSet {
                            activeConversationId?.takeIf { it.isNotEmpty() }?.let { add(it) }
                            addAll(activeConversationIds)
                        }
                        targets.forEach { target ->
                            if (!realtime.isConnected || target in dirtyRooms || !syncedRooms.containsKey(target)) {
                                syncActiveRoomMessages(target)
                            }
                        }
                        if (!conversationsSyncedFromServer || conversationsDirty || (!realtime.isConnected && ticks % 3 == 0)) {
                            loadConversationsIfNeeded(force = conversationsDirty)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            } finally {
                if (activeSyncJob === job) syncRunning = false
            }
        }
        activeSyncJob = job
    }

    /** Dart editMessage: local update first (preview if latest), then PUT. */
    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        synchronized(chatLock) {
            val list = _messages[chatId]
            if (list != null) {
                val idx = list.indexOfFirst { it.id == messageId }
                if (idx != -1) {
                    list[idx] = list[idx].copy(text = newText, editedAt = Instant.now())
                    if (idx == list.size - 1) {
                        val convIdx = _conversations.indexOfFirst { it.id == chatId }
                        if (convIdx != -1) {
                            _conversations[convIdx] = _conversations[convIdx].copy(lastMessageText = newText)
                        }
                    }
                }
            }
        }
        schedulePersistRoomMessages(chatId)
        schedulePersistConversations()
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.editMessage(chatId, messageId, newText) }
    }

    /** Dart deleteMessage: local removal + preview recompute, then DELETE. */
    suspend fun deleteMessage(chatId: String, messageId: String) {
        var gone: List<com.example.chat.data.local.AttachmentRef> = emptyList()
        synchronized(chatLock) {
            val list = _messages[chatId]
            if (list != null) {
                gone = com.example.chat.data.media.AttachmentRefs.refsOf(chatId, list.filter { it.id == messageId })
                list.removeAll { it.id == messageId }
                val convIdx = _conversations.indexOfFirst { it.id == chatId }
                if (convIdx != -1) {
                    val lastMsg = list.lastOrNull()
                    _conversations[convIdx] = _conversations[convIdx].copy(
                        lastMessageText = if (lastMsg != null) {
                            if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(lastMsg.metadata)) com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
                            else if (lastMsg.text.isNotEmpty()) lastMsg.text
                            else if (lastMsg.type == MessageType.image) "📷 Photo" else "📎 Attachment"
                        } else "",
                        lastMessageTime = lastMsg?.createdAt ?: _conversations[convIdx].lastMessageTime,
                    )
                }
            }
        }
        notifyGone(gone)
        schedulePersistRoomMessages(chatId)
        schedulePersistConversations()
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.deleteMessage(chatId, messageId) }
    }

    /** Dart clearConversationMessages: local clear + preview, then DELETE all. */
    suspend fun clearConversationMessages(chatId: String) {
        var gone: List<com.example.chat.data.local.AttachmentRef> = emptyList()
        synchronized(chatLock) {
            gone = com.example.chat.data.media.AttachmentRefs.refsOf(chatId, _messages[chatId] ?: emptyList())
            _messages[chatId] = mutableListOf()
            val convIdx = _conversations.indexOfFirst { it.id == chatId }
            if (convIdx != -1) {
                _conversations[convIdx] = _conversations[convIdx].copy(lastMessageText = "")
            }
        }
        notifyGone(gone)
        schedulePersistRoomMessages(chatId)
        schedulePersistConversations()
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.clearMessages(chatId) }
    }

    private suspend fun syncActiveRoomMessages(chatId: String) {
        val expected = authSnapshot()
        val remote = withContext(Dispatchers.IO) { chat.listMessages(chatId) } ?: return
        var changed = false
        val applied = withAuthScope(expected) {
            synchronized(chatLock) {
                val current = _messages[chatId] ?: mutableListOf()
                val merged = ApiMessageDeliveryMerge.mergeRemoteMessages(current, remote) {
                    (it.deliveryState == DeliveryState.sending || it.id.startsWith("temp_")) &&
                        (isQueued(it.id) || remote.none { r -> r.text == it.text })
                }.toMutableList()
                val hasChanges = merged != current
                if (hasChanges) {
                    _messages[chatId] = merged
                    remote.lastOrNull()?.let { lastMsg ->
                        val idx = _conversations.indexOfFirst { it.id == chatId }
                        if (idx != -1) {
                            _conversations[idx] = _conversations[idx].copy(
                                lastMessageText = if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(lastMsg.metadata)) com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
                            else if (lastMsg.text.isNotEmpty()) lastMsg.text
                                else if (lastMsg.type == MessageType.image) "📷 Photo" else "📎 Attachment",
                                lastMessageTime = lastMsg.createdAt,
                                lastMessageSenderId = lastMsg.senderId,
                            )
                        }
                    }
                    changed = true
                }
            }
            syncedRooms[chatId] = System.currentTimeMillis()
            dirtyRooms.remove(chatId)
            hydratedRoomSnapshots.add(chatId)
        }
        if (applied && changed) {
            schedulePersistRoomMessages(chatId, expected)
            schedulePersistConversations(expected)
            notifyChatListeners()
        }
    }

    /**
     * Notification Reply seam. Sends [text] as the account captured in [expected] and nothing else: base URL, tenant
     * and token come from that scope object (never re-read from the current session), the HTTP call is registered
     * under the auth guard before it starts and cancelled on any auth transition, and no redirect/retry happens.
     * Returns true only when the server confirmed the message AND [expected] is still the live scope. No temp message
     * is created; the confirmed message is merged into the local list only if the scope still matches.
     */
    suspend fun sendMessageForScope(expected: ApiAccountScope, conversationId: String, text: String): Boolean {
        // Quarantined conversation: the notification quick-reply path sends
        // directly (no outbox), so it needs its own refusal AND the dispatch
        // lock - without the lock it could race the toggle mid-send.
        // Strict, owner-scoped outbox load FIRST: a quick reply right after
        // login must not beat the persisted holds loading (a load failure
        // throws = the reply is rejected, fail closed).
        ensureOutboxLoaded(expected)
        if (authSnapshot() !== expected) return false
        if (isHeld(conversationId)) return false
        val dispatchLocks = conversationDispatchLocks
        if (dispatchLocks != null) {
            val accountKey = com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                expected.userId, expected.tenantId, expected.baseUrl)
            return dispatchLocks.withLock(accountKey, conversationId) {
                if (isHeld(conversationId)) return@withLock false
                sendMessageForScopeLocked(expected, conversationId, text)
            }
        }
        return sendMessageForScopeLocked(expected, conversationId, text)
    }

    private suspend fun sendMessageForScopeLocked(expected: ApiAccountScope, conversationId: String, text: String): Boolean {
        var merged = false
        val sender = ScopedMessageSender(
            current = ::authSnapshot, guarded = ::withAuthScope,
            transportFor = { ScopedCallHttpTransport(it, ::withAuthScope, sendRequests) },
            merge = { confirmed ->
                merged = true
                synchronized(chatLock) {
                    val list = _messages.getOrPut(conversationId) { mutableListOf() }
                    val existingIdx = list.indexOfFirst { it.id == confirmed.id }
                    if (existingIdx != -1) {
                        list[existingIdx] = ApiMessageDeliveryMerge.preserveMonotonicDelivery(
                            list[existingIdx],
                            confirmed,
                        )
                    } else {
                        list.add(confirmed)
                    }
                    list.sortBy { it.createdAt }
                    touchConversationPreview(conversationId, confirmed)
                }
            },
        )
        val ok = withContext(Dispatchers.IO) { sender.send(expected, conversationId, text) }
        if (merged) {
            schedulePersistRoomMessages(conversationId, expected)
            schedulePersistConversations(expected)
            notifyChatListeners()
        }
        return ok && merged
    }

    /** sendMessage: optimistic temp message, POST, confirm replaces temp and
     *  bumps the conversation to the top; failure leaves the temp sending. */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        type: MessageType = MessageType.text,
        attachment: com.example.chat.domain.models.MessageAttachment? = null,
        extraMetadata: Map<String, Any?>? = null,
        /** Scope the user action started under (picker/review flows pass theirs). Default: captured now. */
        expectedScope: ApiAccountScope? = null,
    ): ChatMessage? {
        val expected = expectedScope ?: authSnapshot()
        if (expected.userId.isNullOrBlank() || expected.token.isNullOrBlank() || authSnapshot() !== expected) {
            throw IllegalStateException("Account changed or signed out; message not sent.")
        }
        // Quarantined conversation (moved to the encrypted plane): refuse
        // BEFORE the optimistic row so nothing plaintext is queued or shown
        // as sending. Catches unrouted callers during the toggle window.
        // Strict load first: persisted holds must beat a send right after login.
        ensureOutboxLoaded(expected)
        if (authSnapshot() !== expected) {
            throw IllegalStateException("Account changed or signed out; message not sent.")
        }
        if (isHeld(conversationId)) {
            throw IllegalStateException("This chat is being moved to encryption; the message was not sent.")
        }
        if (DemoMode.isDemoUser(expected.userId)) {
            // Offline demo: record locally as 'queued'; the message never leaves
            // the device, so it must not claim a server-confirmed state.
            val confirmed = ChatMessage(
                id = "demo_" + java.util.UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = expected.userId!!,
                type = type,
                text = text,
                attachment = attachment,
                metadata = extraMetadata?.toMap().orEmpty(),
                createdAt = Instant.now(),
                deliveryState = DeliveryState.queued,
            )
            withAuthScope(expected) {
                synchronized(chatLock) {
                    val list = _messages.getOrPut(conversationId) { mutableListOf() }
                    list.add(confirmed)
                    list.sortBy { it.createdAt }
                    val convIdx = _conversations.indexOfFirst { it.id == conversationId }
                    if (convIdx != -1) {
                        val old = _conversations.removeAt(convIdx)
                        _conversations.add(
                            0,
                            old.copy(
                                lastMessageText = if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(confirmed.metadata)) com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
                            else if (confirmed.text.isNotEmpty()) confirmed.text
                                else if (confirmed.type == MessageType.image) "📷 Photo" else "📎 Attachment",
                                lastMessageTime = confirmed.createdAt,
                                lastMessageSenderId = confirmed.senderId,
                            ),
                        )
                    }
                }
            }
            notifyChatListeners()
            return confirmed
        }
        // Dart api_backend_service.dart sendMessage: attachments travel as a JSON envelope in `content`.
        val effectiveContent = if (attachment != null) {
            val o = org.json.JSONObject()
            o.put("type", type.name)
            o.put("id", attachment.id)
            o.put("url", attachment.url ?: org.json.JSONObject.NULL)
            o.put("name", attachment.name)
            o.put("size", attachment.size)
            o.put("text", text)
            o.put("duration", attachment.durationSeconds)
            extraMetadata?.forEach { (k, v) -> o.put(k, v ?: org.json.JSONObject.NULL) }
            o.toString()
        } else text
        val tempId = "temp_" + java.util.UUID.randomUUID().toString()
        val tempMsg = ChatMessage(
            id = tempId,
            conversationId = conversationId,
            senderId = expected.userId,
            type = type,
            text = text,
            attachment = attachment,
            // Keep protection/forward hints on the optimistic row too. Metadata is not authority.
            metadata = extraMetadata?.toMap().orEmpty(),
            createdAt = Instant.now(),
            deliveryState = DeliveryState.sending, // clock until the server has it
        )
        // Lock order: authScopeLock (withAuthScope) then chatLock; nothing takes chatLock before authSnapshot().
        val added = withAuthScope(expected) {
            synchronized(chatLock) {
                _messages.getOrPut(conversationId) { mutableListOf() }.add(tempMsg)
                touchConversationPreview(conversationId, tempMsg)
            }
        }
        if (!added) throw IllegalStateException("Account changed or signed out; message not sent.")
        // Queue first, so the message survives no internet / app restarts until the server has it.
        val queued = try { enqueueOutbox(OutboxEntry(tempId, conversationId, effectiveContent, tempMsg), expected) }
        catch (error: Exception) {
            withAuthScope(expected) { synchronized(chatLock) { _messages[conversationId]?.removeAll { it.id == tempId } } }
            notifyChatListeners()
            throw error
        }
        if (!queued) {
            // A quarantine hold landed while this send was being prepared:
            // remove the optimistic row and fail the send - never plaintext.
            withAuthScope(expected) {
                synchronized(chatLock) {
                    _messages[conversationId]?.removeAll { it.id == tempId }
                }
            }
            notifyChatListeners()
            throw IllegalStateException("This chat is being moved to encryption; the message was not sent.")
        }
        notifyChatListeners()

        scheduleOutboxRecovery(expected)
        // Sends the queue in order (older offline messages first). If it is still queued afterwards
        // there is no internet: it keeps the clock and goes out automatically when back online.
        flushOutbox()
        synchronized(outboxLock) { rejectedSends.remove(tempId) }?.let { detail ->
            throw IllegalStateException(detail)
        }
        return synchronized(outboxLock) { confirmedSends.remove(tempId) }
    }

    // ---- Outbox: messages typed without internet (clock icon), sent when the connection returns ----

    private data class OutboxEntry(
        val tempId: String,
        val conversationId: String,
        val content: String,
        val message: ChatMessage,
        /** Quarantined: preserved in the store but skipped by the flush loop
         *  (the conversation moved to the encrypted plane). */
        val held: Boolean = false,
        val requiresReconciliation: Boolean = false,
        val failed: Boolean = false,
        val reconciledMessageId: String? = null,
        val reconciledState: String? = null,
    )

    private val outboxLock = Any()
    private val outbox = mutableListOf<OutboxEntry>()
    /** Conversations whose REST sends are quarantined. Guarded by
     *  [outboxLock]; persisted as `held_conversations` in the outbox store
     *  (an EMPTY chat's hold must survive restart too - entry flags alone
     *  cannot carry it). [enqueueOutbox] consults it under the same lock, so
     *  a send and a hold can never interleave into a new plaintext entry. */
    private val heldConversations = HashSet<String>()
    /** All heldConversations reads go through here: the set is mutated under
     *  outboxLock (hold/release/load), so bare reads outside that lock race
     *  the mutations. */
    private fun isHeld(conversationId: String): Boolean =
        synchronized(outboxLock) { heldConversations.contains(conversationId) }
    @Volatile private var outboxLoadedFor: String? = null
    private val flushMutex = kotlinx.coroutines.sync.Mutex()
    // Outcome hand-off from the flush loop back to the sendMessage call that queued the message.
    private val confirmedSends = HashMap<String, ChatMessage>()
    private val rejectedSends = HashMap<String, String>()

    private fun legacyOutboxKey(userId: String) = "api_outbox_$userId"

    /** Loads the signed-in account's encrypted queued messages once and puts them back in their chats. */
    /** Account hash derived from a CAPTURED identity (never live auth): a
     *  slow load for account A must not be able to target account B's store
     *  after a switch. */
    private fun accountHashFor(scope: ApiAccountScope): String? {
        val uid = scope.userId?.takeIf { it.isNotBlank() } ?: return null
        if (DemoMode.isDemoUser(uid)) return null
        return ApiAccountRoutingCanonicalizer.accountHash(
            ApiAccountRoutingCanonicalizer.accountKey(scope.tenantId, scope.baseUrl, uid))
    }

    /** Loads the outbox for [expected]'s identity. The slow I/O touches only
     *  locals; the global outbox/heldConversations/outboxLoadedFor swap
     *  happens inside withAuthScope(expected), so a load that finishes after
     *  an account switch throws instead of overwriting the new account's
     *  state. Callers must revalidate authSnapshot() against [expected] after
     *  this returns. */
    private fun ensureOutboxLoaded(expected: ApiAccountScope) {
        val snap = expected
        val userId = snap.userId?.takeIf { it.isNotBlank() } ?: return
        val accountHash = accountHashFor(expected) ?: userId
        if (outboxLoadedFor == accountHash) return
        val restored = mutableListOf<OutboxEntry>()
        var heldFromStore = listOf<String>()
        // Strict read, BEFORE the lenient parse below: null means no outbox
        // was ever saved; a present-but-unreadable store throws, because
        // treating a corrupt store as empty would silently drop quarantined
        // conversations on restart (fail closed instead).
        var rawJson = try {
            multiAccountStore.loadEncryptedOutboxJsonStrict(accountHash)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "The encrypted outbox could not be read; refusing to treat a possibly-quarantined queue as empty.", t)
        }
        if (rawJson.isNullOrBlank()) {
            val legacyRaw = prefs.getString(legacyOutboxKey(userId), null)
            if (!legacyRaw.isNullOrBlank() && legacyRaw != "[]") {
                rawJson = legacyRaw
                // Migration copy is strict too: the legacy entries may carry
                // held flags, and a lost copy must surface, not vanish.
                multiAccountStore.saveEncryptedOutboxJsonStrict(accountHash, legacyRaw)
            }
            prefs.edit().remove(legacyOutboxKey(userId)).apply()
        }
        // Strict parse (fail closed): structural corruption THROWS instead of
        // loading as an empty queue - an optJSONArray-style "missing = empty"
        // read would silently drop the persisted holds. Only INDIVIDUALLY
        // malformed entries are skipped, and their conversation's hold still
        // stands via held_conversations.
        val corrupt = IllegalStateException(
            "The encrypted outbox is corrupt; refusing to load a possibly-quarantined queue as empty.")
        // Stable local: a var cannot smartcast inside the lambdas below.
        val stableRaw: String? = rawJson
        val rootObj = if (stableRaw.isNullOrBlank()) null else runCatching {
            org.json.JSONObject(stableRaw)
        }.getOrNull()
        val arr: org.json.JSONArray = when {
            stableRaw.isNullOrBlank() -> org.json.JSONArray()
            rootObj != null -> {
                val h = rootObj.optJSONArray("held_conversations") ?: throw corrupt
                heldFromStore = (0 until h.length()).map {
                    h.getString(it).takeIf(String::isNotEmpty) ?: throw corrupt
                }
                rootObj.optJSONArray("entries") ?: throw corrupt
            }
            // Legacy layout: a bare array of entries (entry-level held flags).
            else -> runCatching { org.json.JSONArray(stableRaw) }.getOrNull() ?: throw corrupt
        }
        for (i in 0 until arr.length()) {
            val parsed = runCatching {
                val o = arr.getJSONObject(i)
                val att = o.optJSONObject("attachment")?.let { a ->
                    com.example.chat.domain.models.MessageAttachment(
                        id = a.optString("id"), type = a.optString("type"), name = a.optString("name"),
                        size = a.optString("size"), url = a.optString("url").ifEmpty { null },
                        durationSeconds = a.optInt("duration", 0),
                    )
                }
                val msg = ChatMessage(
                    id = o.getString("temp_id"),
                    conversationId = o.getString("room_id"),
                    senderId = userId,
                    type = runCatching { MessageType.valueOf(o.optString("type", "text")) }.getOrDefault(MessageType.text),
                    text = o.optString("text"),
                    attachment = att,
                    metadata = runCatching {
                        val content = org.json.JSONObject(o.getString("content"))
                        com.example.chat.core.stickers.StickerMessagePolicy.parse(content.optString("type"),
                            listOf("chaty_sticker_version", "chaty_sticker_pack", "chaty_sticker_id")
                                .associateWith { key -> if (content.has(key)) content.opt(key) else null })
                    }.getOrDefault(emptyMap()),
                    createdAt = runCatching { Instant.parse(o.optString("created_at")) }.getOrDefault(Instant.now()),
                    deliveryState = if (o.optBoolean("requires_reconciliation", false) || o.optBoolean("failed", false)) DeliveryState.failed else DeliveryState.sending,
                )
                OutboxEntry(msg.id, msg.conversationId, o.getString("content"), msg,
                    held = o.optBoolean("held", false), requiresReconciliation = o.optBoolean("requires_reconciliation", false), failed = o.optBoolean("failed", false),
                    reconciledMessageId = (o.opt("reconciled_message_id") as? String)?.takeIf { it.isNotBlank() },
                    reconciledState = o.optString("reconciled_state").takeIf { it in setOf("sent", "edited", "deleted") })
            }.getOrNull() ?: throw corrupt
            restored += parsed
        }
        // Swap ONLY under the auth guard for the captured identity: if the
        // account changed during the I/O above, nothing is applied and this
        // throws (fail closed) instead of clobbering the new account's queue.
        var swapped = false
        withAuthScope(expected) {
            synchronized(outboxLock) {
                // A racing load for THIS same account may have landed while
                // we were doing I/O; keep the first one.
                if (outboxLoadedFor != accountHash) {
                    outbox.clear()
                    outbox.addAll(restored)
                    heldConversations.clear()
                    heldConversations.addAll(heldFromStore)
                    heldConversations.addAll(restored.filter { it.held }.map { it.conversationId })
                    outboxLoadedFor = accountHash
                }
            }
            swapped = true
        }
        if (!swapped) {
            throw IllegalStateException("Account changed while the outbox was loading; nothing was applied.")
        }
        if (restored.isNotEmpty()) {
            val shouldPublish = withAuthScope(snap) {
                synchronized(chatLock) {
                    for (e in restored) {
                        val list = _messages.getOrPut(e.conversationId) { mutableListOf() }
                        if (list.none { it.id == e.tempId }) list.add(e.message)
                        list.sortBy { it.createdAt }
                    }
                }
            }
            if (shouldPublish) notifyChatListeners()
        }
    }

    /** False when the encrypted outbox store could not be written. Most
     *  callers keep the historical best-effort behavior; the quarantine
     *  hold/release treat false as fatal (a memory-only hold fails open on
     *  restart). */
    private fun persistOutboxLocked(): Boolean {
        val accountHash = outboxLoadedFor ?: return false
        val arr = org.json.JSONArray()
        for (e in outbox) {
            val o = org.json.JSONObject()
                .put("temp_id", e.tempId)
                .put("room_id", e.conversationId)
                .put("content", e.content)
                .put("text", e.message.text)
                .put("type", e.message.type.name)
                .put("created_at", e.message.createdAt.toString())
                .put("held", e.held)
                .put("requires_reconciliation", e.requiresReconciliation)
                .put("failed", e.failed)
                .put("reconciled_message_id", e.reconciledMessageId ?: org.json.JSONObject.NULL)
                .put("reconciled_state", e.reconciledState ?: org.json.JSONObject.NULL)
            e.message.attachment?.let { a ->
                o.put("attachment", org.json.JSONObject()
                    .put("id", a.id).put("type", a.type).put("name", a.name).put("size", a.size)
                    .put("url", a.url ?: "").put("duration", a.durationSeconds))
            }
            arr.put(o)
        }
        val root = org.json.JSONObject()
            .put("entries", arr)
            .put("held_conversations", org.json.JSONArray(heldConversations.toList()))
        return try {
            multiAccountStore.saveEncryptedOutboxJsonStrict(accountHash,
                if (arr.length() == 0 && heldConversations.isEmpty()) null else root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Holds (quarantines) queued REST texts for one conversation: they stay
     *  in the encrypted outbox store - preserved, never deleted - but
     *  [flushOutbox] skips them while held. Used when the conversation moves
     *  to the end-to-end encrypted plane: a message typed while it was a
     *  plaintext chat must not flush to the server afterwards, and it must not
     *  be silently deleted either. The optimistic rows stay visible (clock
     *  icon); the user can resend the text over the encrypted plane or delete
     *  it themselves. The held flag persists via the STRICT outbox write
     *  (checked commit), so a restart cannot fail open. Returns how many
     *  queued entries the conversation currently has; callers should surface
     *  that count so the user knows those messages were not sent.
     *
     *  This is the ONLY entry point - there is deliberately no public
     *  self-locking variant: holds are placed by
     *  ConversationsRepository.setSecureChannel as part of the atomic
     *  toggle, never by hosts directly (a host calling a suspend hold from
     *  its non-suspend scope block was a review blocker). The caller MUST
     *  already hold this conversation's dispatch lock (the toggle does, for
     *  the whole toggle); the Mutex is not reentrant. flushMutex is
     *  deliberately NOT taken here: the lock order is flushMutex -> dispatch
     *  lock (flush per entry), so taking it under the dispatch lock would
     *  invert and deadlock. Exclusion of in-flight delivery comes from the
     *  dispatch lock plus the flush loop re-checking `held` under it before
     *  each delivery (see flushOutbox). */
    fun holdQueuedMessagesForConversationLocked(expected: ApiAccountScope, conversationId: String): Int {
        // The repo toggle calls this while holding the OLD account's
        // dispatch lock: act on the CAPTURED identity, never live auth.
        if (authSnapshot() !== expected) {
            throw IllegalStateException("Account changed; the quarantine hold was not applied.")
        }
        ensureOutboxLoaded(expected)
        if (authSnapshot() !== expected) {
            throw IllegalStateException("Account changed while the outbox was loading; the quarantine hold was not applied.")
        }
        var count = 0
        // Mutation AND persist inside the auth guard (same auth -> outbox
        // ordering as the load swap): an account switch cannot slip between
        // an adjacent identity check and the lock acquisition and make this
        // edit/persist another account's queue under the old account's
        // dispatch lock.
        var guarded = false
        withAuthScope(expected) {
            synchronized(outboxLock) {
                val flipped = mutableListOf<Int>()
                // Exact-state rollback: the chat may ALREADY be held (idempotent
                // re-hold); on persist failure restore what was there, not empty.
                val wasAlreadyHeld = heldConversations.contains(conversationId)
                for (i in outbox.indices) {
                    val e = outbox[i]
                    if (e.conversationId == conversationId) {
                        count++
                        if (!e.held) {
                            outbox[i] = e.copy(held = true)
                            flipped += i
                        }
                    }
                }
                // Durable or nothing: the held flag persists in the outbox
                // store, so a restart cannot fail open; if the persist fails
                // the in-memory change is rolled back and this throws.
                heldConversations.add(conversationId)
                if (!persistOutboxLocked()) {
                    if (!wasAlreadyHeld) heldConversations.remove(conversationId)
                    for (i in flipped) outbox[i] = outbox[i].copy(held = false)
                    throw IllegalStateException("Could not durably save the hold; the queued messages were left untouched and nothing was quarantined.")
                }
            }
            guarded = true
        }
        if (!guarded) {
            throw IllegalStateException("Account changed; the quarantine hold was not applied.")
        }
        return count
    }

    /** Releases a hold (the conversation moved back to the REST plane):
     *  queued texts become flushable again. Same caller-holds-dispatch-lock
     *  contract as [holdQueuedMessagesForConversationLocked]. A persist
     *  failure ALWAYS throws (even when no entry flags flipped - removing
     *  the conversation from held_conversations must be durable too) and
     *  the in-memory change is rolled back, so a failure fails closed:
     *  the queue stays quarantined. */
    fun releaseQueuedMessagesForConversationLocked(expected: ApiAccountScope, conversationId: String) {
        if (authSnapshot() !== expected) {
            throw IllegalStateException("Account changed; the quarantine release was not applied (the queue stays held).")
        }
        ensureOutboxLoaded(expected)
        if (authSnapshot() !== expected) {
            throw IllegalStateException("Account changed while the outbox was loading; the queue stays held.")
        }
        var guarded = false
        withAuthScope(expected) {
            synchronized(outboxLock) {
                val flipped = mutableListOf<Int>()
                // Exact-state rollback: only restore a hold that actually existed.
                val wasHeld = heldConversations.contains(conversationId)
                for (i in outbox.indices) {
                    val e = outbox[i]
                    if (e.conversationId == conversationId && e.held) {
                        outbox[i] = e.copy(held = false)
                        flipped += i
                    }
                }
                heldConversations.remove(conversationId)
                if (!persistOutboxLocked()) {
                    if (wasHeld) heldConversations.add(conversationId)
                    for (i in flipped) outbox[i] = outbox[i].copy(held = true)
                    throw IllegalStateException("Could not durably save the release; the queued messages are still held.")
                }
            }
            guarded = true
        }
        if (!guarded) {
            throw IllegalStateException("Account changed; the quarantine release was not applied (the queue stays held).")
        }
    }

    /** True while the conversation has REST sends pending or in flight: every
     *  REST send passes through the outbox (queued first, removed only after
     *  the server confirms), so a non-held entry for the conversation means a
     *  plaintext send is queued or being delivered right now. Used to refuse
     *  enabling the encrypted plane mid-send. Attachment uploads are not
     *  covered here; the chat bindings re-check the flag at every send port,
     *  so an upload that finishes after enabling fails closed instead of
     *  sending plaintext. */
    fun hasPendingRestSends(conversationId: String): Boolean {
        val expected = authSnapshot()
        ensureOutboxLoaded(expected)
        // The read itself runs inside the auth guard; a guard failure fails
        // closed as "still pending" so the toggle refuses.
        var pending = true
        var guarded = false
        withAuthScope(expected) {
            pending = synchronized(outboxLock) {
                outbox.any { it.conversationId == conversationId && !it.held }
            }
            guarded = true
        }
        return !guarded || pending
    }

    /** Queues a message for the REST plane. Returns false - nothing queued -
     *  when the conversation is quarantined: a held conversation must never
     *  gain new plaintext sends. */
    private fun enqueueOutbox(entry: OutboxEntry, expected: ApiAccountScope): Boolean {
        ensureOutboxLoaded(expected)
        // Enqueue + persist inside the auth guard (same auth -> outbox
        // ordering as hold/release): no check-then-act gap where an account
        // switch lands between the identity check and the mutation.
        var enqueued = false
        var guarded = false
        withAuthScope(expected) {
            synchronized(outboxLock) {
                if (!heldConversations.contains(entry.conversationId)) {
                    val existing = outbox.firstOrNull { it.tempId == entry.tempId }
                    check(existing == null || (existing.conversationId == entry.conversationId && existing.content == entry.content)) { "A queued message identity cannot be reused for different content." }
                    val added = existing == null
                    if (added) outbox.add(entry)
                    if (!persistOutboxLocked()) {
                        if (added) outbox.removeAll { it.tempId == entry.tempId }
                        throw java.io.IOException("Message was not saved to the encrypted outbox. Nothing was sent; try again.")
                    }
                    enqueued = true
                }
            }
            guarded = true
        }
        return guarded && enqueued
    }

    private fun removeFromOutbox(tempId: String) {
        synchronized(outboxLock) {
            val before = outbox.toList()
            if (outbox.removeAll { it.tempId == tempId } && !persistOutboxLocked()) {
                outbox.clear(); outbox.addAll(before)
                throw java.io.IOException("The server may have committed this message, but local confirmation could not be saved. Its original retry key was retained.")
            }
        }
    }

    /** True while [tempId] is still waiting in the outbox. */
    private fun isQueued(tempId: String) = synchronized(outboxLock) { outbox.any { it.tempId == tempId } }

    /** One HTTP attempt for a queued message; binds tightly to [expectedScope] to eliminate cross-account sends. */
    private suspend fun sendOutboxEntry(
        entry: OutboxEntry,
        expectedScope: ApiAccountScope = authSnapshot(),
    ): ApiChatClient.SendResult {
        if (authSnapshot() !== expectedScope) return ApiChatClient.SendResult.Offline
        if (entry.requiresReconciliation) return ApiChatClient.SendResult.Offline
        val supported = withContext(Dispatchers.IO) {
            ApiServerCapabilities.supports(expectedScope, ScopedCallHttpTransport(expectedScope, ::withAuthScope, sendRequests), "message_send_idempotency_v1")
        }
        // Legacy deployments retain their existing best-effort dedup behavior. Same persisted key always.
        if (authSnapshot() !== expectedScope) return ApiChatClient.SendResult.Offline
        val payloadBytes = entry.content.toByteArray(Charsets.UTF_8).size.toLong()
        val ledger = transferMonitor
        val transferId = ledger?.begin(
            com.example.chat.data.network.TransferDirection.upload,
            com.example.chat.data.network.TransferKind.message,
            payloadBytes,
            "Message",
        )
        val result = try {
            withContext(Dispatchers.IO) {
                if (authSnapshot() !== expectedScope) ApiChatClient.SendResult.Offline
                else ApiChatClient(
                    baseUrl = { expectedScope.baseUrl }, tenantId = { expectedScope.tenantId }, token = { expectedScope.token },
                    transport = ScopedCallHttpTransport(expectedScope, ::withAuthScope, sendRequests),
                ).sendMessageResult(entry.conversationId, entry.content, clientId = entry.tempId,
                    messageType = if (entry.message.type == MessageType.audio) "audio" else "text")
            }
        } catch (error: Throwable) {
            if (transferId != null) ledger?.finish(transferId, com.example.chat.data.network.TransferStatus.failed)
            throw error
        }
        if (authSnapshot() !== expectedScope) {
            if (transferId != null) ledger?.finish(transferId, com.example.chat.data.network.TransferStatus.paused)
            return ApiChatClient.SendResult.Offline
        }
        if (transferId != null) {
            when (result) {
                is ApiChatClient.SendResult.Ok -> {
                    ledger?.update(transferId, payloadBytes, payloadBytes)
                    ledger?.finish(transferId)
                }
                is ApiChatClient.SendResult.Rejected ->
                    ledger?.finish(transferId, com.example.chat.data.network.TransferStatus.failed)
                ApiChatClient.SendResult.Offline -> {
                    ledger?.setStatus(transferId, com.example.chat.data.network.TransferStatus.paused)
                    ledger?.finish(transferId, com.example.chat.data.network.TransferStatus.paused)
                }
            }
        }
        when (result) {
            is ApiChatClient.SendResult.Ok -> {
                // A reduced dedup hit proves committed, not delivered. Preserve submitted display content.
                val response = result.message
                val restoreLegacyProjection = !supported && !response.isDeletedForEveryone && !response.isRevokedBySender &&
                    entry.message.metadata["view_once"] != true && response.metadata["view_once"] != true
                val confirmed = response.copy(
                    conversationId = entry.conversationId,
                    senderId = response.senderId.ifBlank { entry.message.senderId },
                    text = if (restoreLegacyProjection && response.text.isEmpty() && entry.message.text.isNotEmpty()) entry.message.text else response.text,
                    attachment = response.attachment ?: entry.message.attachment.takeIf { restoreLegacyProjection },
                    type = if (restoreLegacyProjection && response.attachment == null && entry.message.attachment != null) entry.message.type else response.type,
                    metadata = entry.message.metadata + response.metadata,
                )
                val updated = withAuthScope(expectedScope) {
                    removeFromOutbox(entry.tempId)
                    synchronized(outboxLock) {
                        confirmedSends[entry.tempId] = confirmed
                        if (confirmedSends.size > 50) confirmedSends.keys.firstOrNull()?.let { confirmedSends.remove(it) }
                    }
                    synchronized(chatLock) {
                        val list = _messages.getOrPut(entry.conversationId) { mutableListOf() }
                        list.removeAll { it.id == entry.tempId }
                        val idx = list.indexOfFirst { it.id == confirmed.id }
                        if (idx != -1) {
                            list[idx] = ApiMessageDeliveryMerge.preserveMonotonicDelivery(list[idx], confirmed)
                        } else {
                            list.add(confirmed)
                        }
                        list.sortBy { it.createdAt }
                        touchConversationPreview(entry.conversationId, confirmed)
                    }
                }
                if (updated) {
                    schedulePersistRoomMessages(entry.conversationId, expectedScope)
                    schedulePersistConversations(expectedScope)
                    notifyChatListeners()
                }
            }
            is ApiChatClient.SendResult.Rejected -> {
                val updated = withAuthScope(expectedScope) {
                    if (supported && result.code == 409) {
                        synchronized(outboxLock) {
                            val index = outbox.indexOfFirst { it.tempId == entry.tempId }
                            if (index >= 0) outbox[index] = outbox[index].copy(requiresReconciliation = true)
                            check(persistOutboxLocked()) { "Could not save message conflict state." }
                        }
                    } else synchronized(outboxLock) {
                        // Retain the exact ORIGINAL wire payload for a deliberate retry, including attachment metadata.
                        val index = outbox.indexOfFirst { it.tempId == entry.tempId }
                        if (index >= 0) outbox[index] = outbox[index].copy(failed = true)
                        check(persistOutboxLocked()) { "Could not save message failure state." }
                    }
                    synchronized(outboxLock) {
                        rejectedSends[entry.tempId] = if (supported && result.code == 409) "This message needs reconciliation with its original server record. Automatic retry is stopped; no new send key was created." else result.detail ?: "Message could not be sent (${result.code})."
                        if (rejectedSends.size > 50) rejectedSends.keys.firstOrNull()?.let { rejectedSends.remove(it) }
                    }
                    setLocalDeliveryState(entry.conversationId, entry.tempId, DeliveryState.failed)
                }
                if (updated) notifyChatListeners()
            }
            ApiChatClient.SendResult.Offline -> Unit // stays queued (clock)
        }
        return result
    }

    /** A failed/conflicted row blocks later messages in its thread, not unrelated conversations. outboxLock required. */
    private fun nextOutboxEntryLocked(): OutboxEntry? {
        return OutboxOrderPolicy.next(outbox, { it.conversationId }, { !it.held && !it.requiresReconciliation && !it.failed })
    }

    /** Inactive queues remain encrypted until their account is selected. Background retry NEVER changes UI ownership. */
    suspend fun retryActiveOutboxFromWorker(accountHash: String): Boolean {
        val expected = authSnapshot()
        if (accountHashFor(expected) != accountHash || expected.token.isNullOrBlank() || isDemoSession) return false
        flushOutbox()
        if (authSnapshot() !== expected) return false
        return synchronized(outboxLock) { outboxLoadedFor == accountHash && nextOutboxEntryLocked() != null }
    }
    private suspend fun scheduleOutboxRecovery(expected: ApiAccountScope) {
        val hash = accountHashFor(expected) ?: return
        // Queue is already durable. Scheduler failure must not erase it or claim the message was rejected.
        try { MessageOutboxRetryWorker.schedule(appContext, hash) }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { /* Existing foreground reconnect/switch paths still retry the saved queue. */ }
    }

    /** Sends everything queued for the captured scope, oldest first. */
    suspend fun flushOutbox() {
        val expectedScope = authSnapshot()
        if (expectedScope.token.isNullOrBlank()) return
        ensureOutboxLoaded(expectedScope)
        if (authSnapshot() !== expectedScope) return
        if (!flushMutex.tryLock()) return
        try {
            while (authSnapshot() === expectedScope && session.token != null) {
                // Held (quarantined) entries are preserved but never flushed:
                // their conversation moved to the encrypted plane.
                val next = synchronized(outboxLock) { nextOutboxEntryLocked() } ?: break
                // Per-entry cross-plane lock (flushMutex is already held and
                // stays outermost): a toggle for this conversation waits for
                // an in-flight delivery instead of racing it.
                val dispatchLocks = conversationDispatchLocks
                val r = if (dispatchLocks != null) {
                    val accountKey = com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                        expectedScope.userId, expectedScope.tenantId, expectedScope.baseUrl)
                    dispatchLocks.withLock(accountKey, next.conversationId) {
                        // The entry was selected under outboxLock but a hold
                        // for its conversation may have landed before this
                        // lock was acquired: re-check INSIDE the dispatch
                        // lock (holds run under the same lock), or this
                        // entry would deliver plaintext after the toggle.
                        val heldNow = synchronized(outboxLock) {
                            outbox.any { it.tempId == next.tempId && it.held }
                        }
                        if (heldNow) null else sendOutboxEntry(next, expectedScope)
                    }
                } else {
                    sendOutboxEntry(next, expectedScope)
                }
                if (r == null) continue
                if (r == ApiChatClient.SendResult.Offline) break
            }
        } finally {
            flushMutex.unlock()
        }
    }

    /** Retry for a failed (red) message: queue it again and try now. */
    suspend fun retryMessage(messageId: String): Boolean {
        val owner = authSnapshot()
        val msg = synchronized(chatLock) {
            _messages.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == messageId } }
        } ?: return false
        if (msg.deliveryState != DeliveryState.failed) return false
        if (authSnapshot() !== owner) throw ApiScopeChangedException()
        ensureOutboxLoaded(owner)
        val conflicting = synchronized(outboxLock) { outbox.firstOrNull { it.tempId == messageId && it.requiresReconciliation } }
        if (conflicting != null) {
            val identity = withContext(Dispatchers.IO) {
                ApiMessageKeyLookupClient(owner, ScopedCallHttpTransport(owner, ::withAuthScope, sendRequests)) {
                    authSnapshot() === owner
                }.lookup(conflicting.conversationId, conflicting.tempId)
            }
            check(withAuthScope(owner) {
                synchronized(outboxLock) {
                    val index = outbox.indexOfFirst { it.tempId == messageId && it.requiresReconciliation }
                    check(index >= 0)
                    val previous = outbox[index]
                    outbox[index] = previous.copy(reconciledMessageId = identity.messageId, reconciledState = identity.state)
                    if (!persistOutboxLocked()) { outbox[index] = previous; throw java.io.IOException("Reconciliation metadata could not be saved.") }
                }
            }) { "Account changed. Reconciliation was not saved." }
            // Metadata does not prove payload equality. Never publish the pending body as the stored message.
            throw IllegalStateException("Original server message found (${identity.state}, ${identity.messageId}). No new send was made. Refresh this chat to inspect its current server content; this pending item remains stopped.")
        }
        val expected = owner
        val original = synchronized(outboxLock) { outbox.firstOrNull { it.tempId == messageId } }
            ?: throw IllegalStateException("The original send payload is unavailable. Reconcile the server record before retrying this message.")
        val entry = original.copy(failed = false, message = original.message.copy(deliveryState = DeliveryState.sending))
        check(withAuthScope(expected) {
            synchronized(outboxLock) {
                val index = outbox.indexOfFirst { it.tempId == messageId }
                check(index >= 0 && !outbox[index].requiresReconciliation)
                check(!outbox[index].held && !heldConversations.contains(original.conversationId)) { "This chat's queued messages are held and cannot be retried." }
                val previous = outbox[index]; outbox[index] = entry
                if (!persistOutboxLocked()) { outbox[index] = previous; throw java.io.IOException("Retry was not saved. Nothing was sent.") }
            }
            setLocalDeliveryState(msg.conversationId, msg.id, DeliveryState.sending)
        }) { "Account changed. Message was not retried." }
        scheduleOutboxRecovery(expected)
        flushOutbox()
        synchronized(outboxLock) { rejectedSends.remove(msg.id); confirmedSends.remove(msg.id) }
        // Still queued = no internet yet; it will go out by itself, so the retry itself worked.
        return synchronized(chatLock) {
            _messages[msg.conversationId]?.none { it.id == msg.id && it.deliveryState == DeliveryState.failed } ?: true
        }
    }

    private fun setLocalDeliveryState(conversationId: String, messageId: String, state: DeliveryState) {
        synchronized(chatLock) {
            val list = _messages[conversationId] ?: return@synchronized
            val i = list.indexOfFirst { it.id == messageId }
            if (i != -1) list[i] = list[i].copy(deliveryState = state)
        }
        schedulePersistRoomMessages(conversationId)
        notifyChatListeners()
    }

    /** Call with chatLock held: moves the room to the top with [message] as its preview. */
    private fun touchConversationPreview(conversationId: String, message: ChatMessage) {
        val convIdx = _conversations.indexOfFirst { it.id == conversationId }
        if (convIdx == -1) return
        val old = _conversations.removeAt(convIdx)
        _conversations.add(
            0,
            old.copy(
                lastMessageText = if (com.example.chat.core.messages.ViewOnceTextPolicy.restricted(message.metadata)) com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
                            else if (message.text.isNotEmpty()) message.text
                else if (message.type == MessageType.image) "📷 Photo" else "📎 Attachment",
                lastMessageTime = message.createdAt,
                lastMessageSenderId = message.senderId,
            ),
        )
    }

    val isAuthenticated: Boolean get() = session.isAuthenticated
    val currentUser: ApiUserProfile? get() = session.currentUser
    val token: String? get() = session.token

    private var initialized = false

    /** The device-token plane on this service's session, exposed for the
     *  ServiceLocator's FcmTokenRegistrar (Dart has the registrar call the
     *  same _token-backed methods through the locator). */
    val deviceTokenClient: ApiDeviceTokenClient get() = deviceTokens

    /** Dart _syncFcmToken / signOut's unregisterOnLogout go through the
     *  lazy locator, which Kotlin object-lazy graphs cannot do re-entrantly;
     *  ServiceLocator wires these hooks instead (same effect, no cycle). */
    var syncFcmTokenHook: (() -> Unit)? = null
    var retryFcmTokenHook: (() -> Unit)? = null
    var unregisterFcmHook: (() -> Unit)? = null
    /** Optional process-wide ledger for visible message-transfer progress and payload usage. */
    @Volatile var transferMonitor: com.example.chat.data.network.TransferMonitor? = null
    /** Cross-plane per-conversation dispatch lock, shared with
     *  ConversationsRepository (see ConversationDispatchLocks for the
     *  invariants and the lock-order rules). Wired by ServiceLocator; null in
     *  tests = unlocked (old behavior). */
    @Volatile var conversationDispatchLocks: com.example.chat.data.messaging.ConversationDispatchLocks? = null

    /** Offline demo: returns true when any real GoTrue identity is live or
     *  persisted (wired by ServiceLocator). Demo entry FAILS CLOSED in that
     *  case; a real stored session is never cleared as demo prep. */
    var demoGoTruePresentHook: (suspend () -> Boolean)? = null

    /** Dart ApiBackendService.uploadMedia (api_backend_service.dart:1569):
     *  multipart POST /chat/upload with the /api/v1 404 fallback. */
    suspend fun uploadMedia(file: java.io.File): Map<String, Any> =
        uploadMedia(file) { _, _ -> }

    /** Stream a file through the custom backend without reading it into a ByteArray. */
    suspend fun uploadMedia(
        file: java.io.File,
        onProgress: (transferred: Long, total: Long) -> Unit,
    ): Map<String, Any> {
        val expected = authSnapshot()
        return withContext(Dispatchers.IO) {
            mediaUpload.uploadMedia(expected, { authSnapshot() === expected }, file, onProgress)
        }
    }

    /** Scope-bound File upload: callers that registered an upload with
     *  AttachmentUploadTracker under a captured identity must upload under
     *  THAT SAME identity - a fresh authSnapshot() here could silently ride
     *  the next account after a mid-start switch. */
    suspend fun uploadMediaForScope(
        expected: ApiAccountScope,
        file: java.io.File,
        onProgress: (transferred: Long, total: Long) -> Unit,
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        mediaUpload.uploadMedia(expected, { authSnapshot() === expected }, file, onProgress)
    }

    /** Byte-array form used by ChatMediaService's post-compression payload;
     *  Dart uploads the (possibly compressed) File, whose filename is the
     *  attachment name - here the name travels alongside the bytes. */
    suspend fun uploadMedia(
        filename: String,
        bytes: ByteArray,
        expectedScope: ApiAccountScope? = null,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): Map<String, Any> {
        val expected = expectedScope ?: authSnapshot()      // captured ONCE: primary, 404 retry and result check share it
        return withContext(Dispatchers.IO) {
            mediaUpload.uploadMedia(expected, { authSnapshot() === expected }, filename, bytes, onProgress)
        }
    }

    /** Dart ApiBackendService.registerDeviceToken
     *  (api_backend_service.dart:1975): POST /me/devices, /app-users
     *  fallback, failures swallowed after logging like Dart. */
    suspend fun registerDeviceToken(fcmToken: String, deviceType: String = "android") =
        withContext(Dispatchers.IO) { deviceTokens.registerDeviceToken(fcmToken, deviceType) }

    /** Dart ApiBackendService.unregisterDeviceToken
     *  (api_backend_service.dart:2013). */
    suspend fun unregisterDeviceToken(fcmToken: String) =
        withContext(Dispatchers.IO) { deviceTokens.unregisterDeviceToken(fcmToken) }

    /** Dart initialize(): config restore + session restore (once). */
    fun initialize() {
        if (initialized) return
        initialized = true
        startCallScopeMonitoring()
        config.init()
        // Offline demo: if the persisted session is the demo, arm the offline
        // guard BEFORE restore() fires its transition listeners.
        try {
            if (DemoMode.isDemoRestSession(
                    sessionStore.loadToken(),
                    sessionStore.loadUserJson(),
                )
            ) {
                DemoMode.active = true
            }
        } catch (_: Exception) {
        }
        session.restore()
        // Losslessly migrate legacy single-account installs into the encrypted multi-account registry
        // without clearing the legacy store or logging the user out.
        if (session.isAuthenticated && !isDemoSession) {
            runCatching {
                multiAccount.migrateLegacyIfNeeded(sessionStore, config.snapshot(), config.isUsingDefaultTenant)
            }
        }
        // If multi-account registry has an active session while legacy sessionStore didn't hydrate (or mismatch on cold start), restore from registry.
        if (!session.isAuthenticated && !DemoMode.active && !sessionStore.wasExplicitlyCleared()) {
            val activeMeta = multiAccount.activeAccountMeta()
            val creds = activeMeta?.let { multiAccount.credentialsFor(it.accountKey) }
            if (activeMeta != null && creds != null && !activeMeta.sessionExpired) {
                val userObj = runCatching { apiUserFromJson(creds.userJson) }.getOrNull()
                if (userObj != null) {
                    config.setRouting(
                        tenant = activeMeta.tenantId,
                        useDefault = activeMeta.isDefaultTenant,
                        restBaseUrl = activeMeta.baseUrl,
                        webSocketTemplate = activeMeta.webSocketTemplate,
                        notify = false,
                    )
                    session.adoptForSwitch(
                        ApiAuthResult(creds.accessToken, userObj, refreshToken = creds.refreshToken),
                        suppressTransitionCallback = true,
                    )
                    lastAdoptEpochMs = System.currentTimeMillis()
                    notifyAuthScopeTransition()
                }
            }
        }
        // Dart initialize(): restore _currentOrg from the persisted org payload.
        try {
            prefs.getString("api_hub_org", null)?.let { currentOrg = ApiChatClient.deepMap(org.json.JSONObject(it)) }
        } catch (_: Exception) {
        }
        // Dart initialize(): after session restore, fire the chat-plane loads.
        if (session.isAuthenticated) {
            if (isDemoSession) {
                // Offline demo session restored from disk: re-seed the sample
                // data locally; no realtime, background sync or FCM. (Demo entry
                // already refused while any real GoTrue identity existed.)
                seedDemoState()
                DemoMode.active = true
            } else {
                resetSyncTracking()
                val expected = authSnapshot()
                SessionRefreshWorker.schedule(appContext) // keep the session alive between app opens
                chatScope.launch(Dispatchers.IO) { runCatching { serverConfig.fetch() } }
                chatScope.launch { ensureFreshToken() }
                realtime.connect()
                startBackgroundSync()
                chatScope.launch { val scopeNow = authSnapshot(); ensureOutboxLoaded(scopeNow); flushOutbox() }
                chatScope.launch {
                    hydrateLocalStateForScope(expected)
                    ensureInitialSyncIfNeeded()
                }
                chatScope.launch { loadUsers() }
                chatScope.launch { loadTasks() }
                // Dart _syncFcmToken() (api_backend_service.dart:119).
                try { syncFcmTokenHook?.invoke() } catch (_: Exception) {
                }
            }
        }
    }

    /** UsernameLoginService.login: strip leading @, apply tenant selection,
     *  then delegate to ApiBackendService.login. */
    /** Any REAL auth adoption (REST or GoTrue) must never inherit demo
     *  residue: demo echoes are local-only and no flush path may ever see
     *  them. Idempotent; touches only demo:-prefixed in-memory state. */
    internal fun purgeDemoResidueForRealAdopt() {
        // Peer review Edge A: if the REST session itself is the demo identity
        // (in-memory or persisted), sign it out FIRST - a real adoption must
        // not leave a persisted demo session behind with the flag-only guards
        // disarmed (isDemoSession would stay true while DemoMode.active=false).
        // Only the demo identity is cleared, never real credentials; the
        // transition fires while the guard is still armed, so gated listeners
        // stay offline.
        // STRICT proof before any erasure (peer mixed-store blocker): a pair
        // is demo only when BOTH sides match. A mixed/corrupt store keeps its
        // raw bytes - the in-memory demo session is dropped without touching
        // the store, and demo disk residue is erased only when provably demo.
        val storedToken = runCatching { sessionStore.loadToken() }.getOrNull()
        val storedUser = runCatching { sessionStore.loadUserJson() }.getOrNull()
        val storedStrictDemo = DemoMode.isDemoRestSession(storedToken, storedUser)
        val memoryIsDemo = DemoMode.isDemoUser(session.currentUser?.id) || session.token == DemoMode.TOKEN
        when {
            memoryIsDemo && storedStrictDemo -> try { session.signOut() } catch (_: Exception) {
            }
            memoryIsDemo -> try { session.clearInMemoryOnly() } catch (_: Exception) {
            }
            storedStrictDemo -> try { sessionStore.clear() } catch (_: Exception) {
            }
        }
        DemoMode.active = false
        synchronized(chatLock) {
            _conversations.removeAll { DemoMode.isDemoId(it.id) }
            _messages.keys.filter { DemoMode.isDemoId(it) }.forEach { _messages.remove(it) }
            _users.removeAll { DemoMode.isDemoId(it.id) }
        }
    }

    data class EmailCodeAttempt internal constructor(
        val challenge: ApiEmailCodeClient.Challenge,
        internal val expectedScope: ApiAccountScope,
        internal val targetTenant: String,
        internal val baseUrl: String,
        internal val webSocketTemplate: String,
        internal val routingGeneration: Long,
        internal val isDefaultTenant: Boolean,
    )

    suspend fun requestEmailLoginCode(email: String, tenant: String?, isDefaultTenant: Boolean): EmailCodeAttempt =
        withContext(Dispatchers.IO) {
            val expected = authSnapshot()
            val routing = config.snapshot()
            val target = if (isDefaultTenant) config.defaultTenantId else tenant?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Enter a workspace ID.")
            val challenge = ApiEmailCodeClient(expected.baseUrl, target, OkHttpApiTransport(allowRedirects = false)) {
                authSnapshot() === expected
            }.requestCode(email)
            EmailCodeAttempt(challenge, expected, target, expected.baseUrl, routing.webSocketTemplate,
                routing.generation, isDefaultTenant)
        }

    suspend fun verifyEmailLoginCode(attempt: EmailCodeAttempt, code: String): ApiUserProfile = accountSwitchMutex.withLock {
        check(authSnapshot() === attempt.expectedScope) { "Account or workspace changed. Start sign-in again." }
        val result = withContext(Dispatchers.IO) {
            ApiEmailCodeClient(attempt.baseUrl, attempt.targetTenant, OkHttpApiTransport(allowRedirects = false)) {
                authSnapshot() === attempt.expectedScope
            }.verifyCode(attempt.challenge, code, requireNotNull(deviceIdentity) { "Installation identity is not ready." }
                .challengeHeaders(attempt.baseUrl, attempt.targetTenant, OkHttpApiTransport(allowRedirects = false)) { authSnapshot() === attempt.expectedScope })
        }
        check(authSnapshot() === attempt.expectedScope) { "Account changed. Start sign-in again." }
        adoptCodeOrAddedAccount(result, attempt.targetTenant, attempt.baseUrl, attempt.webSocketTemplate,
            attempt.routingGeneration, attempt.isDefaultTenant)
        result.user
    }

    suspend fun login(identifier: String, password: String, tenant: String?, isDefaultTenant: Boolean): ApiUserProfile {
        // Use the same register-before-adopt path as Add account; never sign in an unregistered third account.
        val registered = loginForAddAccount(identifier, password, tenant, isDefaultTenant)
        return session.currentUser?.takeIf { it.id == registered.userId }
            ?: throw ApiScopeChangedException()
    }

    /** Dart _parseAuthResponse tail (api_backend_service.dart:565-572), shared by login, register,
     *  registerOrganization and oauth: connect the socket, load conversations/users/tasks, sync FCM. */
    private fun afterAuthAdopted(launchInitialSync: Boolean = true) {
        resetSyncTracking()
        val expected = authSnapshot()
        chatScope.launch(Dispatchers.IO) { runCatching { serverConfig.fetch() } }
        SessionRefreshWorker.schedule(appContext) // 30-day background renewal; survives app not being opened
        purgeDemoResidueForRealAdopt()
        realtime.connect()
        startBackgroundSync()
        if (launchInitialSync) {
            chatScope.launch {
                hydrateLocalStateForScope(expected)
                ensureInitialSyncIfNeeded()
            }
        }
        chatScope.launch { loadUsers() }
        chatScope.launch { loadTasks() }
        try { syncFcmTokenHook?.invoke() } catch (_: Exception) {
        }
    }

    /**
     * Hydrates any encrypted local cache for the newly adopted account immediately and awaits the
     * initial conversation sync up to [INITIAL_LOGIN_SYNC_TIMEOUT_MS] so Home opens populated on login.
     */
    private suspend fun afterAuthAdoptedAndSync() {
        val expected = authSnapshot()
        afterAuthAdopted(launchInitialSync = false)
        hydrateLocalStateForScope(expected)
        val syncJob = chatScope.launch { ensureInitialSyncIfNeeded() }
        withTimeoutOrNull(INITIAL_LOGIN_SYNC_TIMEOUT_MS) { syncJob.join() }
    }

    @Volatile var registrationVerificationMessage: String? = null
        private set

    suspend fun register(email: String, username: String, password: String, phoneProof: PhoneVerificationProof? = null): ApiUserProfile =
        accountSwitchMutex.withLock { withContext(Dispatchers.IO) {
            check(!isCallActive()) { "Finish the current call before registering another account." }
            check(multiAccount.accounts().size < MultiAccountRegistryCoordinator.DEFAULT_MAX_ACCOUNTS) { "Remove an account before registering another on this app installation." }
            val owner = authSnapshot()
            val routingAtStart = config.snapshot()
            val proof = requireNotNull(deviceIdentity) { "Installation identity is not ready." }.challengeHeaders(
                routingAtStart.baseUrl, routingAtStart.tenantId, OkHttpApiTransport(allowRedirects = false)) { authSnapshot() === owner }
            val result = scopedAuthClient(routingAtStart.baseUrl, routingAtStart.tenantId).register(email, username, password, extraHeaders = proof, phoneProof = phoneProof)
            if (authSnapshot() !== owner) throw ApiScopeChangedException()
            registrationVerificationMessage = result.verificationMessage
            val routing = config.snapshot()
            adoptCodeOrAddedAccount(result, routing.tenantId, routing.baseUrl, routing.webSocketTemplate,
                routing.generation, config.isUsingDefaultTenant)
            result.user
        } }

    suspend fun acknowledgeAudioPlayed(messageId: String, conversationId: String, expected: ApiAccountScope): Boolean = withContext(Dispatchers.IO) {
        if (authSnapshot() !== expected) return@withContext false
        val message = synchronized(chatLock) { _messages[conversationId]?.firstOrNull { it.id == messageId } } ?: return@withContext false
        if (message.type != MessageType.audio || message.senderId == expected.userId || message.isDeletedForEveryone || message.isRevokedBySender) return@withContext false
        try { ApiMessageReceiptsClient(expected, OkHttpApiTransport(allowRedirects = false)) { authSnapshot() === expected }.acknowledge(messageId, "played") }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { false }
    }

    suspend fun requestPhoneVerification(number: String, tenant: String?, isDefaultTenant: Boolean, expected: ApiAccountScope = authSnapshot()): PhoneVerificationChallenge = withContext(Dispatchers.IO) {
        check(authSnapshot() === expected) { "Account changed. Start phone verification again." }
        val target = if (isDefaultTenant) config.defaultTenantId else tenant?.trim()?.takeIf { it.isNotBlank() } ?: error("Enter a workspace ID.")
        ApiPhoneVerificationClient(expected.baseUrl, target, OkHttpApiTransport(allowRedirects = false), isCurrent = { authSnapshot() === expected }).challenge(number)
    }

    suspend fun verifyPhoneNumber(challenge: PhoneVerificationChallenge, code: String, expected: ApiAccountScope): PhoneVerificationProof = withContext(Dispatchers.IO) {
        check(authSnapshot() === expected) { "Account changed. Start phone verification again." }
        ApiPhoneVerificationClient(challenge.baseUrl, challenge.tenantId, OkHttpApiTransport(allowRedirects = false), isCurrent = { authSnapshot() === expected }).verify(challenge, code)
    }

    suspend fun groupUiDefaults(expected: ApiAccountScope = authSnapshot()): com.example.chat.core.config.GroupUiConfig = withContext(Dispatchers.IO) {
        val hash = accountHashFor(expected)
        val cache = hash?.let { com.example.chat.core.config.EncryptedGroupUiValidatedCache(appContext, it) }
        ApiGroupUiConfigClient(expected, OkHttpApiTransport(allowRedirects = false), { authSnapshot() === expected }, cache).fetch()
    }

    suspend fun messageInfo(messageId: String, afterUserId: String? = null, expected: ApiAccountScope = authSnapshot()): MessageInfoPage =
        withContext(Dispatchers.IO) {
            ApiMessageReceiptsClient(expected, OkHttpApiTransport(allowRedirects = false)) { authSnapshot() === expected }.info(messageId, afterUserId)
        }

    suspend fun forgotPassword(email: String): String = withContext(Dispatchers.IO) {
        auth.forgotPassword(email)
    }

    suspend fun sendEmailVerification(email: String): String = withContext(Dispatchers.IO) {
        auth.sendEmailVerification(email)
    }

    suspend fun confirmEmailVerification(email: String, code: String): String = withContext(Dispatchers.IO) {
        auth.confirmEmailVerification(email, code)
    }

    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): String =
        withContext(Dispatchers.IO) {
            auth.resetPasswordWithCode(email, code, newPassword)
        }

    /** After a successful verification confirm: flip the session flag so the
     *  unverified-email banner disappears without a fresh login. */
    fun markEmailVerified() = session.markEmailVerified()

    /** Prepared public registration preflight. Captures target workspace without
     *  changing active account routing. Missing deployment throws, never Available. */
    suspend fun registrationUsernameAvailability(username: String, tenant: String?, isDefaultTenant: Boolean): ApiAuthClient.UsernameAvailability =
        withContext(Dispatchers.IO) {
            val expected = authSnapshot()
            val target = if (isDefaultTenant) config.defaultTenantId else tenant?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Enter a workspace ID.")
            ApiRegistrationUsernameClient(expected.baseUrl, target, OkHttpApiTransport()) {
                authSnapshot() === expected
            }.check(username)
        }

    /** Live username availability for the edit-profile field (report 5.1). */
    suspend fun usernameAvailability(username: String): ApiAuthClient.UsernameAvailability =
        withContext(Dispatchers.IO) { auth.usernameAvailability(username) }

    /** Exact-match public profile by username (contacts plane). */
    suspend fun profileByUsername(username: String): Map<String, Any?> =
        withContext(Dispatchers.IO) { auth.profileByUsername(username) }

    /** ApiBackendService.registerOrganization: on success applies the new
     *  tenant (Dart setTenantId) and adopts the returned admin session. */
    suspend fun registerOrganization(
        tenantId: String,
        organizationName: String,
        adminUsername: String,
        adminEmail: String,
        adminPassword: String,
        adminFullName: String,
        allowUserRegistration: Boolean = true,
    ): Triple<ApiUserProfile, String, String> = accountSwitchMutex.withLock { withContext(Dispatchers.IO) {
        check(!isCallActive()) { "Finish the current call before registering another organization." }
        check(multiAccount.accounts().size < MultiAccountRegistryCoordinator.DEFAULT_MAX_ACCOUNTS) { "Remove an account before registering another organization account." }
        val owner = authSnapshot()
        val (result, orgMap) = auth.registerOrganization(
            tenantId = tenantId,
            organizationName = organizationName,
            adminUsername = adminUsername,
            adminEmail = adminEmail,
            adminPassword = adminPassword,
            adminFullName = adminFullName,
            allowUserRegistration = allowUserRegistration,
            extraHeaders = requireNotNull(deviceIdentity) { "Installation identity is not ready." }
                .challengeHeaders(owner.baseUrl, "", OkHttpApiTransport(allowRedirects = false)) { authSnapshot() === owner },
        )
        if (authSnapshot() !== owner) throw ApiScopeChangedException()
        val registeredRouting = config.snapshot()
        adoptCodeOrAddedAccount(result, tenantId.trim(), registeredRouting.baseUrl, registeredRouting.webSocketTemplate,
            registeredRouting.generation, false)
        var orgId = tenantId.trim()
        var resolvedOrgName = organizationName.trim()
        if (orgMap != null) {
            orgId = (orgMap["uid"] ?: orgMap["tenantId"] ?: orgId).toString()
            resolvedOrgName = (orgMap["name"] ?: resolvedOrgName).toString()
            try {
                val orgJson = org.json.JSONObject()
                for ((k, v) in orgMap) orgJson.put(k, v)
                prefs.edit().putString("api_hub_org", orgJson.toString()).apply()
                currentOrg = orgMap
            } catch (_: Exception) {
            }
        }
        Triple(result.user, orgId, resolvedOrgName)
    } }

    /** ApiBackendService.updateProfile: PUT profile, then apply the local
     *  user update (effective name + first-char initials) and persist. */
    suspend fun updateProfile(
        displayName: String? = null,
        about: String? = null,
        username: String? = null,
        avatarUrl: String? = null,
        phone: String? = null,
        avatarColorHex: String? = null,
        avatarInitials: String? = null,
        bannerUrl: String? = null,
        expectedScope: ApiAccountScope? = null,
    ): ApiUserProfile {
        val expected = expectedScope ?: authSnapshot()
        return withContext(Dispatchers.IO) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            auth.updateProfile(expected, { authSnapshot() === expected }, displayName, about, username, avatarUrl,
                phone, avatarColorHex, avatarInitials, bannerUrl)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            var updated: ApiUserProfile? = null
            if (!withAuthScope(expected) {
                updated = session.updateProfileFields(displayName, about, username, avatarUrl,
                    phone, avatarColorHex, avatarInitials, bannerUrl)
            }) throw ApiScopeChangedException()
            notifyChatListeners()
            updated ?: throw ApiScopeChangedException()
        }
    }

    /** Dart updateOrganizationRegistrationSupport (PATCH /organization/<id>/registration). */
    suspend fun updateOrganizationRegistrationSupport(organizationIdOrTenantId: String, allowUserRegistration: Boolean): Boolean =
        withContext(Dispatchers.IO) { accountClient.updateRegistrationSupport(organizationIdOrTenantId, allowUserRegistration) }

    /** Dart uploadProfilePhoto: multipart POST /app-users/upload-photo; on a non-empty URL the current user's
     *  avatarUrl is updated and persisted (api_hub_user), then listeners are notified. */
    suspend fun uploadProfilePhoto(filename: String, bytes: ByteArray, expectedScope: ApiAccountScope? = null): String {
        val expected = expectedScope ?: authSnapshot()
        return withContext(Dispatchers.IO) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val url = accountClient.uploadProfilePhoto(filename, bytes)
            // Scope guard: if the account changed mid-upload, the URL belongs
            // to the old account - drop it instead of persisting it.
            if (authSnapshot() !== expected) throw ApiScopeChangedException()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            var changed = false
            if (!withAuthScope(expected) {
                if (url.isNotEmpty()) {
                    session.updateProfileFields(null, null, null, url)
                    changed = true
                }
            }) throw ApiScopeChangedException()
            if (changed) notifyChatListeners()
            url
        }
    }

    suspend fun checkOrganization(workspace: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        auth.checkOrganization(workspace)
    }

    /**
     * Reconnects realtime socket when returning from background and fetches
     * conversations/messages only if dirty, stale, or while WebSocket was offline.
     */
    fun onAppResumed() {
        if (session.token == null) return
        chatScope.launch { ensureFreshToken() }
        realtime.connect()
        chatScope.launch { flushOutbox() }
        val now = System.currentTimeMillis()
        val stale = !conversationsSyncedFromServer || conversationsDirty ||
            !realtime.isConnected || (now - lastConversationsSyncMs >= RESUME_STALE_MS)
        if (stale) {
            conversationsDirty = true
            chatScope.launch { loadConversationsIfNeeded(force = false) }
        }
        activeConversationId?.takeIf { it.isNotEmpty() }?.let { id ->
            if (!realtime.isConnected || id in dirtyRooms || !syncedRooms.containsKey(id)) {
                chatScope.launch { loadMessagesIfNeeded(id, force = true) }
            }
        }
    }

    /** Wipes published chat caches after a non-null account switch. Runs inside
     *  the scope-owner lock (withAuthScope), so it is atomic with guarded async
     *  writes: an old-account completion either applied before this clear (and
     *  is wiped) or fails its identity check after. Lock order everywhere:
     *  scope-owner lock -> chatLock. */
    fun clearChatCachesForAccountSwitch() {
        resetSyncTracking()
        val applied = withAuthScope(authSnapshot()) {
            synchronized(chatLock) {
                _messages.clear()
                _conversations.clear()
                requiresSubscription = false
                subscriptionErrorMessage = null
            }
        }
        if (applied) notifyChatListeners()
    }

    private fun retireRemovedAccountRecovery(hash: String) {
        // Cancellation is scoped and best effort. Workers independently revalidate stored credentials.
        runCatching { CallInboxRetryWorker.cancel(appContext, hash) }
        runCatching { MessageOutboxRetryWorker.cancel(appContext, hash) }
        runCatching { com.example.chat.notifications.ChatyNotifications.cancelRemovedAccountAlerts(appContext, hash) }
    }

    fun signOut() {
        resetSyncTracking()
        val activeKey = currentAccountKey()
        val oldHash = currentAccountHash()
        // Dart signOut: FirebaseNotificationService.unregisterOnLogout first.
        try { unregisterFcmHook?.invoke() } catch (_: Exception) {
        }
        realtime.disconnect()
        resetSessionCoroutineScope()
        syncRunning = false
        DemoMode.active = false
        // Preservation (peer mixed-store blocker): signing out a demo session
        // may only erase a STRICTLY-demo persisted pair; a mixed/corrupt store
        // keeps its raw bytes and only the in-memory demo session is dropped.
        val signingOutDemo = isDemoSession
        val storedStrictDemo = try {
            DemoMode.isDemoRestSession(
                sessionStore.loadToken(),
                sessionStore.loadUserJson(),
            )
        } catch (_: Exception) { false }
        if (!signingOutDemo && activeKey != null) {
            runCatching { multiAccount.removeAccount(activeKey) }
            oldHash?.let(::retireRemovedAccountRecovery)
        }
        if (signingOutDemo && !storedStrictDemo) {
            session.clearInMemoryOnly()
        } else {
            session.signOut()
        }
        saveRefreshToken(null)
        SessionRefreshWorker.cancel(appContext)
        onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear(); presenceRevisions.clear()
        // Queued messages stay saved for this account and are sent after its next login.
        synchronized(outboxLock) { outbox.clear(); outboxLoadedFor = null; confirmedSends.clear(); rejectedSends.clear() }
        synchronized(localStateLock) { localStateCache.clear() }
        activeConversationId = null
        activeConversationIds.clear()
        requiresSubscription = false
        subscriptionErrorMessage = null
        synchronized(chatLock) {
            _conversations.clear()
            _users.clear()
            _messages.clear()
            _tasks.clear()
        }
        if (oldHash != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { onBeforeAccountSwitch?.invoke(oldHash, null) }
            }
        }
        notifyChatListeners()
    }

    // ---- Access-token refresh (/auth/refresh) ----
    // Access tokens live ACCESS_TOKEN_EXPIRE_MINUTES (15 by default on the secure
    // backend). Without a refresh every request starts failing with 401 shortly
    // after login. Refresh tokens are single-use and rotated on every refresh:
    // the rotated token returned by the server is persisted immediately, because
    // replaying a consumed token revokes the whole session (reuse detection).

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    private val accountSwitchMutex = kotlinx.coroutines.sync.Mutex()

    sealed interface AccountSwitchResult {
        data class Switched(val meta: ApiRegisteredAccountMeta, val selectedConversationId: String?) : AccountSwitchResult
        data class AlreadyActive(val meta: ApiRegisteredAccountMeta) : AccountSwitchResult
        data class SessionExpired(val meta: ApiRegisteredAccountMeta, val message: String) : AccountSwitchResult
        data class Failed(val message: String) : AccountSwitchResult
    }

    fun persistAccountConversationSnapshot() {
        val key = currentAccountKey() ?: return
        val list = conversations
        val drafts = multiAccount.allDraftsFor(key)
        val summaries = MultiAccountRegistryCoordinator.summarizeConversations(
            conversations = list,
            drafts = drafts,
            isHiddenOrLocked = isConversationProtectedHook,
        )
        multiAccount.saveRecentConversations(key, summaries)
        val unreadChats = list.count { !it.isArchived && it.unreadCount > 0 && !isConversationProtectedHook(it.id) }
        val unreadMsgs = list.filter { !it.isArchived && !isConversationProtectedHook(it.id) }.sumOf { it.unreadCount.coerceAtLeast(0) }
        multiAccount.updateAccountProfileAndUnread(
            accountKey = key,
            user = session.currentUser,
            unreadChatsCount = unreadChats,
            unreadMessagesCount = unreadMsgs,
        )
    }

    /** Atomically switches the active session to [targetAccountKey], quarantining in-flight
     *  network/coroutines/realtime/DB/vault state before publishing the new identity. */
    suspend fun switchToAccount(targetAccountKey: String): AccountSwitchResult = accountSwitchMutex.withLock {
        val currentKey = currentAccountKey()
        val targetMeta = multiAccount.accountByKey(targetAccountKey)
            ?: return@withLock AccountSwitchResult.Failed("Account no longer available.")
        if (currentKey == targetAccountKey && !targetMeta.sessionExpired && session.isAuthenticated) {
            return@withLock AccountSwitchResult.AlreadyActive(targetMeta)
        }

        if (isCallActive()) return@withLock AccountSwitchResult.Failed("The current call is still active. End it before switching accounts; a call-retaining switch is not ready.")

        // Snapshot outgoing account state cleanly before teardown
        persistAccountConversationSnapshot()
        val oldHash = currentAccountHash()
        val creds = multiAccount.credentialsFor(targetAccountKey)
            ?: run {
                multiAccount.markSessionExpired(targetAccountKey, true)
                return@withLock AccountSwitchResult.SessionExpired(targetMeta.copy(sessionExpired = true), "Please sign in again.")
            }
        val targetUser = runCatching { apiUserFromJson(creds.userJson) }.getOrNull()
            ?: run {
                multiAccount.markSessionExpired(targetAccountKey, true)
                return@withLock AccountSwitchResult.SessionExpired(targetMeta.copy(sessionExpired = true), "Stored profile is invalid. Please sign in again.")
            }

        var effectiveToken = creds.accessToken
        var effectiveRefresh = creds.refreshToken
        val expMs = creds.expiresAtEpochMs ?: JwtExpiryDecoder.decodeExpiryEpochMs(effectiveToken)
        if (targetMeta.sessionExpired) {
            return@withLock AccountSwitchResult.SessionExpired(targetMeta, "Session expired. Please sign in again.")
        }
        if (expMs != null && (expMs - System.currentTimeMillis()) < REFRESH_MARGIN_SECONDS * 1000L && !effectiveRefresh.isNullOrBlank()) {
            val proof = deviceIdentity?.proofHeaders(effectiveRefresh) ?: emptyMap()
            when (val r = withContext(Dispatchers.IO) {
                scopedAuthClient(targetMeta.baseUrl, targetMeta.tenantId).refresh(effectiveRefresh, proof)
            }) {
                is ApiAuthClient.RefreshResult.Ok -> {
                    effectiveToken = r.accessToken
                    effectiveRefresh = r.refreshToken ?: effectiveRefresh
                    val newExp = JwtExpiryDecoder.decodeExpiryEpochMs(effectiveToken)
                    multiAccountStore.saveCredentials(
                        targetMeta.accountHash,
                        creds.copy(accessToken = effectiveToken, refreshToken = effectiveRefresh, expiresAtEpochMs = newExp),
                    )
                    multiAccount.markSessionExpired(targetAccountKey, false)
                }
                ApiAuthClient.RefreshResult.Rejected -> {
                    multiAccount.markSessionExpired(targetAccountKey, true)
                    notifyChatListeners()
                    return@withLock AccountSwitchResult.SessionExpired(targetMeta.copy(sessionExpired = true), "Session expired. Please sign in again.")
                }
                ApiAuthClient.RefreshResult.Unavailable -> Unit // allow local cached entry offline
            }
        }

        // Quarantine outgoing coroutines, realtime socket, and non-API stores outside/before atomic flip
        realtime.disconnect()
        resetSessionCoroutineScope()
        syncRunning = false
        DemoMode.active = false
        runCatching { onBeforeAccountSwitch?.invoke(oldHash, targetMeta.accountHash) }

        // Atomic routing + credential adoption under authScopeLock with a single transition signal
        synchronized(authScopeLock) {
            config.setRouting(
                tenant = targetMeta.tenantId,
                useDefault = targetMeta.isDefaultTenant,
                restBaseUrl = targetMeta.baseUrl,
                webSocketTemplate = targetMeta.webSocketTemplate,
                notify = false,
            )
            session.adoptForSwitch(
                ApiAuthResult(effectiveToken, targetUser, refreshToken = effectiveRefresh),
                suppressTransitionCallback = true,
            )
            lastAdoptEpochMs = System.currentTimeMillis()
            onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear(); presenceRevisions.clear()
            synchronized(outboxLock) { outbox.clear(); outboxLoadedFor = null; confirmedSends.clear(); rejectedSends.clear() }
            synchronized(localStateLock) { localStateCache.clear() }
            activeConversationId = null
            activeConversationIds.clear()
            requiresSubscription = false
            subscriptionErrorMessage = null
            synchronized(chatLock) {
                _conversations.clear()
                _users.clear()
                _messages.clear()
                _tasks.clear()
            }
            notifyAuthScopeTransition()
        }

        multiAccount.markActive(targetAccountKey)
        runCatching { onAfterAccountSwitch?.invoke(oldHash, targetMeta.accountHash) }

        // Restart account-scoped realtime & background sync cleanly
        resetSyncTracking()
        val nextExpected = authSnapshot()
        realtime.connect()
        startBackgroundSync()
        chatScope.launch(Dispatchers.IO) { runCatching { serverConfig.fetch() } }
        chatScope.launch { val scopeNow = authSnapshot(); ensureOutboxLoaded(scopeNow); flushOutbox() }
        chatScope.launch {
            hydrateLocalStateForScope(nextExpected)
            loadConversationsIfNeeded(force = true)
        }
        chatScope.launch { loadUsers() }
        chatScope.launch { loadTasks() }
        try { syncFcmTokenHook?.invoke() } catch (_: Exception) {}
        notifyChatListeners()

        AccountSwitchResult.Switched(
            meta = targetMeta.copy(sessionExpired = false, lastActiveEpochMs = System.currentTimeMillis()),
            selectedConversationId = multiAccount.selectedConversationFor(targetAccountKey),
        )
    }

    /** Real Add-Account login: authenticates without mutating active routing if cancelled/failed,
     *  then registers the new account and atomically switches to it. */
    suspend fun loginForAddAccount(
        identifier: String,
        password: String,
        tenant: String?,
        isDefaultTenant: Boolean,
    ): ApiRegisteredAccountMeta = accountSwitchMutex.withLock {
        check(!isCallActive()) { "Finish the current call before adding or re-authenticating an account." }
        val owner = authSnapshot()
        val routing = config.snapshot()
        val value = identifier.trim().replace(Regex("^@+"), "")
        val targetTenant = if (isDefaultTenant) config.defaultTenantId else (tenant?.trim().takeIf { !it.isNullOrEmpty() } ?: config.tenantId)
        val baseUrl = config.restApiBaseUrl
        val result = withContext(Dispatchers.IO) {
            scopedAuthClient(baseUrl, targetTenant).login(
                identifier = value,
                password = password,
                extraHeaders = requireNotNull(deviceIdentity) { "Installation identity is not ready." }
                    .challengeHeaders(baseUrl, targetTenant, OkHttpApiTransport(allowRedirects = false)) { authSnapshot() === owner },
            )
        }
        if (authSnapshot() !== owner) throw ApiScopeChangedException()
        adoptCodeOrAddedAccount(result, targetTenant, baseUrl, routing.webSocketTemplate,
            routing.generation, isDefaultTenant)
    }

    /** Common adoption path preserves existing multi-account snapshot, socket and
     *  namespace isolation for both password-added accounts and real code login. */
    private suspend fun adoptCodeOrAddedAccount(
        result: ApiAuthResult, targetTenant: String, baseUrl: String,
        webSocketTemplate: String, routingGeneration: Long, isDefaultTenant: Boolean,
    ): ApiRegisteredAccountMeta {
        val oldHash = currentAccountHash()
        persistAccountConversationSnapshot()
        val routingSnapshot = ApiRoutingSnapshot(
            tenantId = targetTenant,
            baseUrl = baseUrl,
            webSocketTemplate = webSocketTemplate,
            generation = routingGeneration,
        )
        val registered = multiAccount.registerAuthenticatedAccount(result, routingSnapshot, isDefaultTenant)
            ?: throw IllegalStateException("This app installation supports two signed-in accounts. Remove an account before adding another.")

        realtime.disconnect()
        resetSessionCoroutineScope()
        syncRunning = false
        DemoMode.active = false
        runCatching { onBeforeAccountSwitch?.invoke(oldHash, registered.accountHash) }

        synchronized(authScopeLock) {
            config.setRouting(
                tenant = registered.tenantId,
                useDefault = registered.isDefaultTenant,
                restBaseUrl = registered.baseUrl,
                webSocketTemplate = registered.webSocketTemplate,
                notify = false,
            )
            session.adoptForSwitch(result, suppressTransitionCallback = true)
            lastAdoptEpochMs = System.currentTimeMillis()
            onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear(); presenceRevisions.clear()
            synchronized(outboxLock) { outbox.clear(); outboxLoadedFor = null; confirmedSends.clear(); rejectedSends.clear() }
            synchronized(localStateLock) { localStateCache.clear() }
            activeConversationId = null
            activeConversationIds.clear()
            requiresSubscription = false
            subscriptionErrorMessage = null
            synchronized(chatLock) {
                _conversations.clear()
                _users.clear()
                _messages.clear()
                _tasks.clear()
            }
            notifyAuthScopeTransition()
        }

        runCatching { onAfterAccountSwitch?.invoke(oldHash, registered.accountHash) }
        afterAuthAdoptedAndSync()
        notifyChatListeners()
        return registered
    }

    /** Independently signs out/removes [targetAccountKey]. If active and fallback accounts exist,
     *  atomically switches to fallback; if last account removed, clears to pre-auth cleanly. */
    suspend fun signOutAccount(targetAccountKey: String): AccountSwitchResult = accountSwitchMutex.withLock {
        val currentKey = currentAccountKey()
        val targetMeta = multiAccount.accountByKey(targetAccountKey)
            ?: return@withLock AccountSwitchResult.Failed("Account not found.")
        val remaining = multiAccount.removeAccount(targetAccountKey)
        retireRemovedAccountRecovery(targetMeta.accountHash)
        if (currentKey != targetAccountKey) {
            notifyChatListeners()
            return@withLock remaining?.let { AccountSwitchResult.AlreadyActive(it) }
                ?: AccountSwitchResult.Failed("Removed inactive account.")
        }
        // Signing out the currently active account:
        if (remaining != null && !remaining.sessionExpired) {
            // Release lock before calling switch logic OR execute switch inline:
            val creds = multiAccount.credentialsFor(remaining.accountKey)
            val fallbackUser = creds?.let { runCatching { apiUserFromJson(it.userJson) }.getOrNull() }
            if (creds != null && fallbackUser != null) {
                realtime.disconnect()
                resetSessionCoroutineScope()
                syncRunning = false
                runCatching { onBeforeAccountSwitch?.invoke(targetMeta.accountHash, remaining.accountHash) }
                synchronized(authScopeLock) {
                    config.setRouting(
                        tenant = remaining.tenantId,
                        useDefault = remaining.isDefaultTenant,
                        restBaseUrl = remaining.baseUrl,
                        webSocketTemplate = remaining.webSocketTemplate,
                        notify = false,
                    )
                    session.adoptForSwitch(
                        ApiAuthResult(creds.accessToken, fallbackUser, refreshToken = creds.refreshToken),
                        suppressTransitionCallback = true,
                    )
                    lastAdoptEpochMs = System.currentTimeMillis()
                    onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear(); presenceRevisions.clear()
                    synchronized(outboxLock) { outbox.clear(); outboxLoadedFor = null; confirmedSends.clear(); rejectedSends.clear() }
                    synchronized(localStateLock) { localStateCache.clear() }
                    activeConversationId = null
                    activeConversationIds.clear()
                    synchronized(chatLock) {
                        _conversations.clear(); _users.clear(); _messages.clear(); _tasks.clear()
                    }
                    notifyAuthScopeTransition()
                }
                multiAccount.markActive(remaining.accountKey)
                runCatching { onAfterAccountSwitch?.invoke(targetMeta.accountHash, remaining.accountHash) }
                afterAuthAdoptedAndSync()
                notifyChatListeners()
                return@withLock AccountSwitchResult.Switched(remaining, multiAccount.selectedConversationFor(remaining.accountKey))
            }
        }
        withContext(Dispatchers.Main) { signOut() }
        AccountSwitchResult.Failed("Signed out.")
    }

    /** Refreshes all stored non-expired accounts in background without mutating active session routing. */
    suspend fun refreshAllRegisteredAccountsInBackground(): SessionRefreshOutcome {
        val activeOutcome = refreshSessionInBackground()
        val accounts = multiAccount.accounts()
        val activeKey = currentAccountKey()
        for (meta in accounts) {
            if (meta.accountKey == activeKey || meta.sessionExpired) continue
            val creds = multiAccount.credentialsFor(meta.accountKey) ?: continue
            val rt = creds.refreshToken ?: continue
            multiAccount.refreshAccountIfNeeded(meta.accountKey, force = true) { base, tenant, token ->
                val proof = deviceIdentity?.proofHeaders(token) ?: emptyMap()
                withContext(Dispatchers.IO) { scopedAuthClient(base, tenant).refresh(token, proof) }
            }
        }
        return activeOutcome
    }

    private fun saveRefreshToken(token: String?) {
        try {
            sessionStore.saveRefreshToken(token)
        } catch (_: Exception) {
        }
    }

    private fun loadRefreshToken(): String? =
        try { sessionStore.loadRefreshToken() } catch (_: Exception) { null }

    /** Renews the access token when it expires within [REFRESH_MARGIN_SECONDS]. Waits while a
     *  call is active (a new token is a new account scope, which would end the call). Signs the
     *  user out when the token is already expired and cannot be renewed. */
    suspend fun ensureFreshToken() {
        val current = session.token ?: return
        // Give newly adopted sessions at least a 60-second stabilization window before attempting
        // proactive background refresh to prevent initial sync race conditions / false clock skew.
        if (System.currentTimeMillis() - lastAdoptEpochMs < 60_000L) return
        val remaining = auth.secondsUntilExpiry(current) ?: return
        if (remaining > REFRESH_MARGIN_SECONDS) return
        if (remaining > 0 && isCallActive()) return
        performRefresh(force = false)
    }

    /** Outcome of a refresh attempt, for the background worker's retry policy. */
    enum class SessionRefreshOutcome { REFRESHED, NOT_NEEDED, NO_SESSION, UNAVAILABLE }

    /** Bandi (2026-11-07): a user who never opens the app must still stay
     *  signed in. SessionRefreshWorker calls this on a 30-day cadence; every
     *  successful call slides the server's 180-day idle window forward, so
     *  the session outlives any realistic gap between app opens. Network
     *  failures return UNAVAILABLE (the worker retries with backoff) and
     *  NEVER sign the user out; only a definitive server rejection does. */
    suspend fun refreshSessionInBackground(): SessionRefreshOutcome {
        if (session.token == null) session.restore()
        if (session.token == null) return SessionRefreshOutcome.NO_SESSION
        return performRefresh(force = true)
    }

    private suspend fun performRefresh(force: Boolean): SessionRefreshOutcome {
        val current = session.token ?: return SessionRefreshOutcome.NO_SESSION
        refreshMutex.lock()
        try {
            if (session.token != current) return SessionRefreshOutcome.REFRESHED // someone else refreshed meanwhile
            val user = session.currentUser ?: return SessionRefreshOutcome.NO_SESSION
            val refreshToken = loadRefreshToken() ?: return SessionRefreshOutcome.NO_SESSION
            val proofHeaders = deviceIdentity?.proofHeaders(refreshToken) ?: emptyMap()
            val result = withContext(Dispatchers.IO) { auth.refresh(refreshToken, extraHeaders = proofHeaders) }
            return when (result) {
                is ApiAuthClient.RefreshResult.Ok -> {
                    val rotated = result.refreshToken ?: refreshToken
                    saveRefreshToken(rotated) // rotation: the old token is dead from here on
                    val nextAuth = ApiAuthResult(token = result.accessToken, user = user, refreshToken = rotated)
                    session.adopt(nextAuth)
                    runCatching {
                        multiAccount.registerAuthenticatedAccount(nextAuth, config.snapshot(), config.isUsingDefaultTenant)
                    }
                    realtime.connect()
                    SessionRefreshOutcome.REFRESHED
                }
                ApiAuthClient.RefreshResult.Rejected -> {
                    val key = currentAccountKey()
                    if (key != null) {
                        multiAccount.markSessionExpired(key, true)
                    }
                    if (multiAccount.accounts().size > 1) {
                        notifyChatListeners()
                        SessionRefreshOutcome.NO_SESSION
                    } else {
                        withContext(Dispatchers.Main) { signOut() }
                        SessionRefreshOutcome.NO_SESSION
                    }
                }
                ApiAuthClient.RefreshResult.Unavailable -> SessionRefreshOutcome.UNAVAILABLE // retry later
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    private fun parseIsoOrNull(value: String?): Instant? = try {
        if (value.isNullOrBlank()) null
        else java.time.OffsetDateTime.parse(value).toInstant()
    } catch (_: Exception) {
        try { Instant.parse(value) } catch (_: Exception) {
            // backend_python may send naive UTC timestamps ("2026-10-29T10:00:00.123456").
            try { java.time.LocalDateTime.parse(value).toInstant(java.time.ZoneOffset.UTC) } catch (_: Exception) { null }
        }
    }

    private companion object {
        // 5-minute margin against the 15-minute access-token lifetime; the
        // refresh is skipped while a call is active (unless already expired).
        const val REFRESH_MARGIN_SECONDS = 5 * 60L
        const val TYPING_TTL_MS = 6_000L
        const val RESUME_STALE_MS = 60_000L
        const val INITIAL_LOGIN_SYNC_TIMEOUT_MS = 2_500L
    }
}
