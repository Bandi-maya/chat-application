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
        transport = OkHttpApiTransport(),
        token = { session.token },
    )

    /** Scoped auth requests for a registered account without mutating global routing. */
    private fun scopedAuthClient(baseUrl: String, tenantId: String): ApiAuthClient = ApiAuthClient(
        baseUrl = { baseUrl },
        tenantId = { tenantId },
        transport = OkHttpApiTransport(),
        token = { session.token },
    )

    private val session = ApiAuthSession(sessionStore, authScopeLock, ::notifyAuthScopeTransition)

    // Contacts / presence / call-history planes (research 5.2-5.4). All
    // fail-closed: a pre-plane server throws PlaneUnavailable and the UI
    // shows nothing rather than guessing.
    private val contactsClient = ApiContactsClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(),
        token = { session.token },
    )
    private val presenceClient = ApiPresenceClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(),
        token = { session.token },
    )
    private val callHistoryClient = ApiCallHistoryClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(),
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
            if (blocked) contactsClient.blockUser(id) else contactsClient.unblock(id)
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
        var queued = false
        withAuthScope(expected) { queued = realtime.sendCallSignal(targetUserId, data) }
        callCoordinator.send(expected, targetUserId, data)
        return queued // Socket enqueue only, never delivery confirmation.
    }
    fun removeCallSignalListener(listener: (Map<String, Any?>) -> Unit) { callSignalListeners.remove(listener) }

    private val scopedCallListeners = java.util.concurrent.CopyOnWriteArrayList<(ApiAccountScope, Map<String, Any?>) -> Unit>()
    /** Push ring nudge: one-shot REST poll of queued call signals while the
     *  socket is down (app may be backgrounded). */
    fun pollCallSignalsNow() { callCoordinator.pollNow() }
    suspend fun pollCallSignalsAndAwait() { callCoordinator.pollNow()?.join() }
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
            clock = { android.os.SystemClock.elapsedRealtime() })
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
        return ApiRealtimeClient.resolveWebSocketUrl(
            userId = uid,
            customWebSocketUrl = ApiBackendConfig.DOTENV_WEBSOCKET_URL,
            envWebSocketUrl = ApiBackendConfig.WEBSOCKET_URL_OVERRIDE,
            restApiBaseUrl = config.restApiBaseUrl,
        )
    }

    private val realtimeListener: ApiRealtimeClient.Listener = object : ApiRealtimeClient.Listener {
        override fun onConnected() {
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
                            lastMessageText = if (normalized.text.isNotEmpty()) normalized.text
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
                            if (lastMsg.text.isNotEmpty()) lastMsg.text
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
            val expected = authSnapshot()
            val myId = session.currentUser?.id
            if (readerId.isNotEmpty() && readerId == myId) return
            // Live backend sends no message_ids when a reader marks the whole
            // room read ({room_id, user_id, read_at}); the client delivers an
            // empty list for that form, meaning "all of my messages here".
            val markAll = messageIds.isEmpty() && readerId.isNotEmpty()
            val ids = messageIds.toSet()
            var changed = false
            synchronized(chatLock) {
                val list = _messages[roomId] ?: return@synchronized
                for (i in list.indices) {
                    val m = list[i]
                    if ((markAll || m.id in ids) && m.senderId == myId && m.deliveryState != DeliveryState.read) {
                        list[i] = m.copy(deliveryState = DeliveryState.read)
                        changed = true
                    }
                }
            }
            if (changed) {
                schedulePersistRoomMessages(roomId, expected)
                notifyChatListeners()
            }
        }

        override fun onInitialPresence(onlineUserIds: List<String>) {
            onlineIds.clear()
            onlineIds.addAll(onlineUserIds)
            notifyChatListeners()
        }

        override fun onPresence(userId: String, isOnline: Boolean, lastSeen: String?) {
            if (isOnline) onlineIds.add(userId) else onlineIds.remove(userId)
            val seen = parseIsoOrNull(lastSeen)
            if (seen != null) lastSeenById[userId] = seen
            else if (!isOnline) lastSeenById.remove(userId)
            notifyChatListeners()
            for (l in contactEventListeners) runCatching { l.onContactOnlineChanged(userId, isOnline) }
        }

        override fun onPresenceChanged(userId: String, online: Boolean, lastSeenAt: String?, approx: String?) {
            // Server already privacy-filtered this view for us; store exactly
            // what it said and drop everything else (fail-closed).
            if (online) onlineIds.add(userId) else onlineIds.remove(userId)
            val seen = parseIsoOrNull(lastSeenAt)
            if (seen != null) lastSeenById[userId] = seen else lastSeenById.remove(userId)
            if (approx != null) approxPresenceById[userId] = approx else approxPresenceById.remove(userId)
            notifyChatListeners()
            for (l in contactEventListeners) runCatching { l.onContactOnlineChanged(userId, online) }
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
    suspend fun fetchPresence(userId: String): ApiPresenceClient.PresenceView =
        withContext(Dispatchers.IO) { presenceClient.presence(userId) }

    /** POST /presence/heartbeat (REST presence transport; server believes
     *  online for 90s only). Fail-closed false. */
    suspend fun presenceHeartbeat(online: Boolean): Boolean =
        withContext(Dispatchers.IO) { presenceClient.heartbeat(online) }

    fun presencePrivacy(): ApiPresenceClient.PresencePrivacy? = presenceClient.privacy()

    fun putPresencePrivacy(lastSeenPolicy: String, onlinePolicy: String, exceptions: List<String>?): ApiPresenceClient.PresencePrivacy? =
        presenceClient.putPrivacy(lastSeenPolicy, onlinePolicy, exceptions)

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
    fun sendTyping(roomId: String, isTyping: Boolean) {
        if (session.token == null) return
        realtime.sendTyping(roomId, isTyping)
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
    suspend fun remoteSettingsFetch(): Pair<Map<String, Any?>?, String?>? =
        withContext(Dispatchers.IO) { chat.getChatSettingsExtras() }

    suspend fun remoteSettingsPush(snapshot: Map<String, Any?>): Boolean =
        withContext(Dispatchers.IO) { chat.updateChatSettings(mapOf("extras" to snapshot)) }

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

    suspend fun syncFromPushNudge() {
        if (session.token == null || DemoMode.active) return
        pushSyncMutex.withLock {
            val expected = authSnapshot()
            if (expected.token == null || DemoMode.active) return@withLock
            if (synchronized(chatLock) { _conversations.isEmpty() }) {
                hydrateLocalStateForScope(expected)
            }
            val (conversationsBefore, cachedBeforeByRoom) = synchronized(chatLock) {
                _conversations.toList() to _messages.mapValues { (_, list) -> list.toList() }
            }
            ensureFreshToken()
            ensureOutboxLoaded()
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
                loadMessages(conv.id)
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
        val coord = snapshotCoordinator ?: return
        val ns = snapshotNamespaceFor(expected) ?: return
        if (roomId.isBlank()) return
        val snapshot = synchronized(chatLock) { _messages[roomId]?.toList() } ?: return
        chatScope.launch(Dispatchers.IO) {
            if (authSnapshot() !== expected) return@launch
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
        if (!applied) return
        schedulePersistRoomMessages(chatId, expected)
        notifyGone(gone)
        notifyChatListeners()
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
    suspend fun createOrGetDirectConversation(targetUserId: String): Conversation {
        val expected = authSnapshot()
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

    /** createGroup: scope-bound request and publication; never insert an old
     *  account's room into a newly switched account's list or snapshot. */
    suspend fun createGroupRoom(name: String, memberUserIds: List<String>): Conversation {
        val expected = authSnapshot()
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
    suspend fun deleteUser(userId: String) {
        val expected = authSnapshot()
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
                            if (lastMsg.text.isNotEmpty()) lastMsg.text
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
                                lastMessageText = if (lastMsg.text.isNotEmpty()) lastMsg.text
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
                                lastMessageText = if (confirmed.text.isNotEmpty()) confirmed.text
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
        enqueueOutbox(OutboxEntry(tempId, conversationId, effectiveContent, tempMsg))
        notifyChatListeners()

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
    )

    private val outboxLock = Any()
    private val outbox = mutableListOf<OutboxEntry>()
    @Volatile private var outboxLoadedFor: String? = null
    private val flushMutex = kotlinx.coroutines.sync.Mutex()
    // Outcome hand-off from the flush loop back to the sendMessage call that queued the message.
    private val confirmedSends = HashMap<String, ChatMessage>()
    private val rejectedSends = HashMap<String, String>()

    private fun legacyOutboxKey(userId: String) = "api_outbox_$userId"

    /** Loads the signed-in account's encrypted queued messages once and puts them back in their chats. */
    private fun ensureOutboxLoaded() {
        val snap = authSnapshot()
        val userId = snap.userId?.takeIf { it.isNotBlank() } ?: return
        val accountHash = currentAccountHash() ?: userId
        if (outboxLoadedFor == accountHash) return
        val restored = mutableListOf<OutboxEntry>()
        try {
            var rawJson = multiAccountStore.loadEncryptedOutboxJson(accountHash)
            if (rawJson.isNullOrBlank()) {
                val legacyRaw = prefs.getString(legacyOutboxKey(userId), null)
                if (!legacyRaw.isNullOrBlank() && legacyRaw != "[]") {
                    rawJson = legacyRaw
                    multiAccountStore.saveEncryptedOutboxJson(accountHash, legacyRaw)
                }
                prefs.edit().remove(legacyOutboxKey(userId)).apply()
            }
            val arr = org.json.JSONArray(rawJson ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
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
                    deliveryState = DeliveryState.sending,
                )
                restored += OutboxEntry(msg.id, msg.conversationId, o.getString("content"), msg)
            }
        } catch (_: Exception) {
        }
        synchronized(outboxLock) {
            outbox.clear()
            outbox.addAll(restored)
            outboxLoadedFor = accountHash
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

    private fun persistOutboxLocked() {
        val accountHash = outboxLoadedFor ?: return
        val arr = org.json.JSONArray()
        for (e in outbox) {
            val o = org.json.JSONObject()
                .put("temp_id", e.tempId)
                .put("room_id", e.conversationId)
                .put("content", e.content)
                .put("text", e.message.text)
                .put("type", e.message.type.name)
                .put("created_at", e.message.createdAt.toString())
            e.message.attachment?.let { a ->
                o.put("attachment", org.json.JSONObject()
                    .put("id", a.id).put("type", a.type).put("name", a.name).put("size", a.size)
                    .put("url", a.url ?: "").put("duration", a.durationSeconds))
            }
            arr.put(o)
        }
        try {
            multiAccountStore.saveEncryptedOutboxJson(accountHash, if (arr.length() == 0) null else arr.toString())
        } catch (_: Exception) {}
    }

    private fun enqueueOutbox(entry: OutboxEntry) {
        ensureOutboxLoaded()
        synchronized(outboxLock) {
            if (outbox.none { it.tempId == entry.tempId }) outbox.add(entry)
            persistOutboxLocked()
        }
    }

    private fun removeFromOutbox(tempId: String) {
        synchronized(outboxLock) {
            if (outbox.removeAll { it.tempId == tempId }) persistOutboxLocked()
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
                else chat.sendMessageResult(entry.conversationId, entry.content, clientId = entry.tempId)
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
                val confirmed = result.message
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
                    removeFromOutbox(entry.tempId)
                    synchronized(outboxLock) {
                        rejectedSends[entry.tempId] = result.detail ?: "Message could not be sent (${result.code})."
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

    /** Sends everything queued for the captured scope, oldest first. */
    suspend fun flushOutbox() {
        val expectedScope = authSnapshot()
        if (expectedScope.token.isNullOrBlank()) return
        ensureOutboxLoaded()
        if (!flushMutex.tryLock()) return
        try {
            while (authSnapshot() === expectedScope && session.token != null) {
                val next = synchronized(outboxLock) { outbox.firstOrNull() } ?: break
                val r = sendOutboxEntry(next, expectedScope)
                if (r == ApiChatClient.SendResult.Offline) break
            }
        } finally {
            flushMutex.unlock()
        }
    }

    /** Retry for a failed (red) message: queue it again and try now. */
    suspend fun retryMessage(messageId: String): Boolean {
        val msg = synchronized(chatLock) {
            _messages.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == messageId } }
        } ?: return false
        if (msg.deliveryState != DeliveryState.failed) return false
        val att = msg.attachment
        val content = if (att != null) {
            val o = org.json.JSONObject()
            o.put("type", msg.type.name)
            o.put("url", att.url ?: org.json.JSONObject.NULL)
            o.put("name", att.name)
            o.put("size", att.size)
            o.put("text", msg.text)
            o.put("duration", att.durationSeconds)
            o.toString()
        } else msg.text
        setLocalDeliveryState(msg.conversationId, msg.id, DeliveryState.sending)
        val entry = OutboxEntry(msg.id, msg.conversationId, content, msg.copy(deliveryState = DeliveryState.sending))
        enqueueOutbox(entry)
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
                lastMessageText = if (message.text.isNotEmpty()) message.text
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
                chatScope.launch { ensureOutboxLoaded(); flushOutbox() }
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

    suspend fun login(identifier: String, password: String, tenant: String?, isDefaultTenant: Boolean): ApiUserProfile =
        withContext(Dispatchers.IO) {
            val value = identifier.trim().replace(Regex("^@+"), "")
            if (isDefaultTenant) {
                config.setTenantId(null, useDefault = true)
            } else if (!tenant.isNullOrBlank()) {
                config.setTenantId(tenant.trim(), useDefault = false)
            }
            val result = auth.login(value, password, extraHeaders = deviceIdentity?.identityHeaders() ?: emptyMap())
            lastAdoptEpochMs = System.currentTimeMillis()
            session.adopt(result)
            saveRefreshToken(result.refreshToken)
            runCatching { multiAccount.registerAuthenticatedAccount(result, config.snapshot(), config.isUsingDefaultTenant) }
            afterAuthAdoptedAndSync()
            result.user
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

    suspend fun register(email: String, username: String, password: String): ApiUserProfile =
        withContext(Dispatchers.IO) {
            val result = auth.register(email, username, password, extraHeaders = deviceIdentity?.identityHeaders() ?: emptyMap())
            registrationVerificationMessage = result.verificationMessage
            lastAdoptEpochMs = System.currentTimeMillis()
            session.adopt(result)
            saveRefreshToken(result.refreshToken)
            runCatching { multiAccount.registerAuthenticatedAccount(result, config.snapshot(), config.isUsingDefaultTenant) }
            afterAuthAdoptedAndSync()
            result.user
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
    ): Triple<ApiUserProfile, String, String> = withContext(Dispatchers.IO) {
        val (result, orgMap) = auth.registerOrganization(
            tenantId = tenantId,
            organizationName = organizationName,
            adminUsername = adminUsername,
            adminEmail = adminEmail,
            adminPassword = adminPassword,
            adminFullName = adminFullName,
            allowUserRegistration = allowUserRegistration,
        )
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
        config.setTenantId(tenantId.trim())
        session.adopt(result)
        saveRefreshToken(result.refreshToken)
        runCatching { multiAccount.registerAuthenticatedAccount(result, config.snapshot(), config.isUsingDefaultTenant) }
        afterAuthAdoptedAndSync()
        Triple(result.user, orgId, resolvedOrgName)
    }

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
        }
        if (signingOutDemo && !storedStrictDemo) {
            session.clearInMemoryOnly()
        } else {
            session.signOut()
        }
        saveRefreshToken(null)
        SessionRefreshWorker.cancel(appContext)
        onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear()
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
            onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear()
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
        chatScope.launch { ensureOutboxLoaded(); flushOutbox() }
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
        val value = identifier.trim().replace(Regex("^@+"), "")
        val targetTenant = if (isDefaultTenant) config.defaultTenantId else (tenant?.trim().takeIf { !it.isNullOrEmpty() } ?: config.tenantId)
        val baseUrl = config.restApiBaseUrl
        val result = withContext(Dispatchers.IO) {
            scopedAuthClient(baseUrl, targetTenant).login(
                identifier = value,
                password = password,
                extraHeaders = deviceIdentity?.identityHeaders() ?: emptyMap(),
            )
        }
        val oldHash = currentAccountHash()
        persistAccountConversationSnapshot()
        val routingSnapshot = ApiRoutingSnapshot(
            tenantId = targetTenant,
            baseUrl = baseUrl,
            webSocketTemplate = config.snapshot().webSocketTemplate,
            generation = config.snapshot().generation,
        )
        val registered = multiAccount.registerAuthenticatedAccount(result, routingSnapshot, isDefaultTenant)
            ?: throw IllegalStateException("Maximum number of signed-in accounts (${MultiAccountRegistryCoordinator.DEFAULT_MAX_ACCOUNTS}) reached or demo session rejected.")

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
            onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear()
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
        registered
    }

    /** Independently signs out/removes [targetAccountKey]. If active and fallback accounts exist,
     *  atomically switches to fallback; if last account removed, clears to pre-auth cleanly. */
    suspend fun signOutAccount(targetAccountKey: String): AccountSwitchResult = accountSwitchMutex.withLock {
        val currentKey = currentAccountKey()
        val targetMeta = multiAccount.accountByKey(targetAccountKey)
            ?: return@withLock AccountSwitchResult.Failed("Account not found.")
        val remaining = multiAccount.removeAccount(targetAccountKey)
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
                    onlineIds.clear(); lastSeenById.clear(); approxPresenceById.clear(); typingUntil.clear()
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
