package com.example.chat.data.api

import kotlinx.coroutines.ensureActive

import android.content.Context
import com.example.chat.data.demo.DemoMode
import com.example.chat.data.demo.DemoSampleData
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android holder for the REST backend: ApiBackendConfig (tenant + base URL),
 *  ApiAuthClient (endpoints), ApiAuthSession (api_hub_token/api_hub_user
 *  persistence). Mirrors the api branch's ApiBackendService auth surface; the
 *  ChatyBackendService facade prefers this session, so splash/home routing
 *  reads [isAuthenticated]/[currentUser] from here first. */
class ApiBackendService(
    context: Context,
    chatTransport: ApiHttpTransport = OkHttpApiTransport(),
) {

    private val authScopeLock = Any()
    val config = ApiBackendConfig(context, authScopeLock, ::notifyAuthScopeTransition)

    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)

    private val sessionStore = object : ApiSessionStore {
        override fun loadToken(): String? =
            try { prefs.getString("api_hub_token", null) } catch (_: Exception) { null }
        override fun loadUserJson(): String? =
            try { prefs.getString("api_hub_user", null) } catch (_: Exception) { null }
        override fun save(token: String, userJson: String) {
            try {
                prefs.edit()
                    .putString("api_hub_token", token)
                    .putString("api_hub_user", userJson)
                    .apply()
            } catch (_: Exception) {
            }
        }
        override fun clear() {
            try { prefs.edit().remove("api_hub_token").remove("api_hub_user").apply() } catch (_: Exception) {}
        }
    }

    private val auth = ApiAuthClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(),
        token = { session.token },
    )

    private val session = ApiAuthSession(sessionStore, authScopeLock, ::notifyAuthScopeTransition)
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
            callCoordinator.foreground(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
            lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_START) callCoordinator.foreground(true)
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) callCoordinator.foreground(false)
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

    private val realtimeListener = object : ApiRealtimeClient.Listener {
        override fun onNewMessage(message: ChatMessage) {
            var unknownRoom = false
            var isNewToList = false
            var convoTitle: String? = null
            var convoMuted = false
            synchronized(chatLock) {
                val roomId = message.conversationId
                val list = _messages.getOrPut(roomId) { mutableListOf() }
                // Dart: replace the optimistic sending/temp message matching
                // text + sender; otherwise append if the id is new.
                // Already confirmed over HTTP: never let the echo claim another queued message.
                // Only our own messages can replace a pending one (an incoming "ok" must not
                // swallow the "ok" we are still sending).
                val myId = session.currentUser?.id
                val optIdx = if (list.any { it.id == message.id } || message.senderId != myId) -1
                else list.indexOfFirst { m ->
                    m.deliveryState == DeliveryState.sending && m.id.startsWith("temp_") &&
                        m.text == message.text && m.senderId == myId
                }
                if (optIdx != -1) {
                    // Our own queued message came back from the server: it is sent (one tick).
                    removeFromOutbox(list[optIdx].id)
                    list[optIdx] = message
                    list.removeAll { it !== message && it.id == message.id }
                } else if (list.none { it.id == message.id }) {
                    list.add(message)
                    isNewToList = true
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
                            lastMessageText = if (message.text.isNotEmpty()) message.text
                            else if (message.type == MessageType.image) "📷 Photo" else "📎 Attachment",
                            lastMessageTime = message.createdAt,
                            lastMessageSenderId = message.senderId,
                        ),
                    )
                } else {
                    unknownRoom = true
                }
            }
            if (unknownRoom) chatScope.launch { loadConversations() }
            // Tell the sender it reached this phone (their second grey tick).
            if (message.senderId.isNotEmpty() && message.senderId != session.currentUser?.id) {
                realtime.sendDelivered(message.conversationId)
            }
            typingUntil.remove("${message.conversationId}|${message.senderId}")
            if (message.conversationId == activeConversationId && message.senderId != session.currentUser?.id) {
                markConversationRead(message.conversationId)
            }
            notifyChatListeners()
            if (isNewToList) runCatching { onIncomingMessage?.invoke(message, convoTitle, convoMuted) }
        }

        override fun onMessageEdited(roomId: String, messageId: String, newContent: String, editedAt: Instant) {
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
            notifyChatListeners()
        }

        override fun onMessageDeleted(roomId: String, messageId: String) {
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
            notifyChatListeners()
        }

        override fun onMessagesCleared(roomId: String) {
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
            notifyChatListeners()
        }

        override fun onScopedCallSignal(data: Map<String, Any?>, capturedScope: Any?) {
            val expected = capturedScope as? ApiAccountScope ?: return
            callCoordinator.socketSignal(expected, data)
        }

        override fun onMessagesRead(roomId: String, messageIds: List<String>, readerId: String) {
            val myId = session.currentUser?.id
            if (readerId.isNotEmpty() && readerId == myId) return
            val ids = messageIds.toSet()
            var changed = false
            synchronized(chatLock) {
                val list = _messages[roomId] ?: return@synchronized
                for (i in list.indices) {
                    val m = list[i]
                    if (m.id in ids && m.senderId == myId && m.deliveryState != DeliveryState.read) {
                        list[i] = m.copy(deliveryState = DeliveryState.read)
                        changed = true
                    }
                }
            }
            if (changed) notifyChatListeners()
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
        }

        override fun onMessagesDelivered(roomId: String, deliveredUpTo: String) {
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
            if (changed) notifyChatListeners()
        }

        override fun onTyping(roomId: String, userId: String, isTyping: Boolean) {
            if (userId == session.currentUser?.id) return
            val key = "$roomId|$userId"
            if (isTyping) typingUntil[key] = System.currentTimeMillis() + TYPING_TTL_MS else typingUntil.remove(key)
            notifyChatListeners()
        }
    }

    // ---- Presence / typing / read state from the custom backend socket ----

    private val onlineIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val lastSeenById = java.util.concurrent.ConcurrentHashMap<String, Instant>()
    private val typingUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Online per the backend (respects the other user's "who can see me online" setting). */
    fun isUserOnline(userId: String): Boolean = userId in onlineIds

    /** Last-seen time the backend published, or null when unknown/hidden. */
    fun lastSeenOf(userId: String): Instant? = lastSeenById[userId]

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
            if (!roomId.isNullOrEmpty()) chat.markRoomDelivered(roomId)
            loadConversations()
            if (!roomId.isNullOrEmpty() && roomId == activeConversationId) loadMessages(roomId)
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

    /** Hook set by the locator: true while a call is in progress (token refresh waits until it ends). */
    @Volatile var isCallActive: () -> Boolean = { false }

    /** Reachability probe for the connection banner: GET <base>/health. Throws on failure. */
    suspend fun pingBackend() = withContext(Dispatchers.IO) {
        val base = config.restApiBaseUrl.trimEnd('/').replace(Regex("/api/v[0-9]+$"), "")
        val response = healthTransport.execute("GET", "$base/health", emptyMap(), null)
        if (response.first !in 200..299) throw Exception("Backend health check failed (${response.first})")
    }
    private val healthTransport = OkHttpApiTransport()

    private val realtime = ApiRealtimeClient(
        webSocketUrl = { resolveWebSocketUrl() },
        token = { session.token },
        backoff = com.example.chat.data.network.BackoffPolicy(),
        connectionScopeIdentity = { authSnapshot() },
        connectionScopeGuard = { expected, action -> (expected as? ApiAccountScope)?.let { withAuthScope(it, action) } ?: false },
    ).apply { listener = realtimeListener }

    /** Network regained after loss: reconnect the socket now instead of waiting out the backoff.
     *  No-op while connected, connecting or signed out. */
    fun onNetworkRegained() {
        realtime.reconnectNow()
        chatScope.launch { flushOutbox() } // send what was typed while offline
    }

    /** Invalidates local media copies for deleted/removed messages. Set by the locator BEFORE initialize(). */
    @Volatile var onAttachmentsGone: (List<com.example.chat.data.local.AttachmentRef>) -> Unit = {}
    private fun notifyGone(refs: List<com.example.chat.data.local.AttachmentRef>) {
        if (refs.isNotEmpty()) runCatching { onAttachmentsGone(refs) }
    }

    private val chatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    /** Dart _tasks / tasks getter. */
    private val _tasks = mutableListOf<ChatTask>()
    val tasks: List<ChatTask>
        get() = synchronized(chatLock) { _tasks.toList() }

    /** Active-conversation id drives the 4s poll (Dart setActiveConversation). */
    @Volatile private var activeConversationId: String? = null
    val activeConversation: String? get() = activeConversationId
    /** Incoming (not own) realtime message: (message, conversation title, muted). Invoked outside chatLock. */
    @Volatile var onIncomingMessage: ((ChatMessage, String?, Boolean) -> Unit)? = null
    @Volatile private var syncRunning = false

    val conversations: List<Conversation> get() {
        val list = synchronized(chatLock) { _conversations.toList() }
        val state = localState("conv")
        if (state.length() == 0) return list
        return list.map { c ->
            val o = state.optJSONObject(c.id) ?: return@map c
            c.copy(
                isPinned = o.optBoolean("pinned", c.isPinned),
                isMuted = o.optBoolean("muted", c.isMuted),
                isArchived = o.optBoolean("archived", c.isArchived),
            )
        }
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

    // ---- Device-local chat state ----
    // The custom backend has no per-user pin/mute/archive or star/pin-message API yet, so these
    // are kept on this device per account (they do not sync to the user's other devices).

    private val localStateLock = Any()
    private val localStateCache = HashMap<String, org.json.JSONObject>()

    private fun localStateKey(kind: String) = "api_local_${kind}_" + (session.currentUser?.id ?: "anon")

    private fun localState(kind: String): org.json.JSONObject = synchronized(localStateLock) {
        val key = localStateKey(kind)
        localStateCache.getOrPut(key) {
            try { org.json.JSONObject(prefs.getString(key, "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
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
            val storedToken = try { prefs.getString("api_hub_token", null) } catch (_: Exception) { null }
            val storedUser = try { prefs.getString("api_hub_user", null) } catch (_: Exception) { null }
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
        val result = withContext(Dispatchers.IO) { tasksClient.listTasks() } ?: return
        synchronized(chatLock) {
            _tasks.clear()
            _tasks.addAll(result)
        }
        notifyChatListeners()
    }

    suspend fun loadConversations() {
        when (val result = withContext(Dispatchers.IO) { chat.listRooms() }) {
            is ApiChatClient.RoomsResult.Ok -> {
                synchronized(chatLock) {
                    _conversations.clear()
                    _conversations.addAll(result.conversations.filter { !it.isTask })
                }
                requiresSubscription = false
                subscriptionErrorMessage = null
                notifyChatListeners()
            }
            ApiChatClient.RoomsResult.SubscriptionRequired -> {
                requiresSubscription = true
                subscriptionErrorMessage = "Workspace subscription required. Please select a plan."
                notifyChatListeners()
            }
            ApiChatClient.RoomsResult.Failed -> Unit
        }
    }

    /** loadMessages with Dart merge semantics: pending (sending/temp) messages
     *  whose text is absent from the remote list survive; merged + sorted. */
    suspend fun loadMessages(chatId: String) {
        val expected = authSnapshot()
        val remote = withContext(Dispatchers.IO) { chat.listMessages(chatId) } ?: return
        var gone: List<com.example.chat.data.local.AttachmentRef> = emptyList()
        val applied = withAuthScope(expected) {
            synchronized(chatLock) {
                val current = _messages[chatId] ?: mutableListOf()
                val pending = current.filter { m ->
                    (m.deliveryState == DeliveryState.sending || m.id.startsWith("temp_")) &&
                        (isQueued(m.id) || remote.none { it.text == m.text })
                }
                val merged = (remote + pending).sortedBy { it.createdAt }.toMutableList()
                gone = com.example.chat.data.media.AttachmentRefs.refsGone(chatId, current.toList(), merged)
                _messages[chatId] = merged
            }
        }
        if (!applied) return
        notifyGone(gone)
        notifyChatListeners()
    }

    /** startDirectChat: POST /chat/rooms/direct (no fallback), upsert at
     *  index 0 (replace if the id already exists), notify. 402 sets the
     *  subscription state with the exact Dart message, notifies, rethrows. */
    suspend fun createOrGetDirectConversation(targetUserId: String): Conversation {
        try {
            val conversation = withContext(Dispatchers.IO) { chat.createDirectRoom(targetUserId) }
            synchronized(chatLock) {
                val idx = _conversations.indexOfFirst { it.id == conversation.id }
                if (idx != -1) _conversations.removeAt(idx)
                _conversations.add(0, conversation)
            }
            notifyChatListeners()
            return conversation
        } catch (e: ApiChatClient.SubscriptionRequiredException) {
            requiresSubscription = true
            subscriptionErrorMessage =
                "Workspace subscription required. Please choose a plan to chat."
            notifyChatListeners()
            throw e
        }
    }

    /** createGroup: upsert at index 0 (replace if the id exists), notify. */
    suspend fun createGroupRoom(name: String, memberUserIds: List<String>): Conversation {
        val conversation = withContext(Dispatchers.IO) { chat.createGroupRoom(name, memberUserIds) }
        synchronized(chatLock) {
            val idx = _conversations.indexOfFirst { it.id == conversation.id }
            if (idx != -1) _conversations.removeAt(idx)
            _conversations.add(0, conversation)
        }
        notifyChatListeners()
        return conversation
    }

    /** deleteUser: local-first removal (user + their direct conversations),
     *  notify, then the backend DELETE with errors swallowed. Deleting
     *  oneself signs out (Dart). */
    suspend fun deleteUser(userId: String) {
        synchronized(chatLock) {
            _users.removeAll { it.id == userId }
            _conversations.removeAll { conversation ->
                conversation.type == ConversationType.direct &&
                    conversation.participantIds.contains(userId)
            }
        }
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.deleteUser(userId) }
        if (userId == session.currentUser?.id) signOut()
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
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.deleteRoom(chatId) }
    }

    suspend fun loadUsers(query: String? = null) {
        val fetched = withContext(Dispatchers.IO) { chat.listUsers(query) } ?: return
        // Seed "last seen" from the directory (last_seen) for offline users. Online state itself
        // comes from the WebSocket (initial_presence / user_presence), which is always fresher.
        for (u in fetched) {
            if (u.id.isEmpty() || u.id == session.currentUser?.id) continue
            if (u.presence == PresenceState.offline) lastSeenById.putIfAbsent(u.id, u.lastSeenAt)
        }
        synchronized(chatLock) {
            if (query.isNullOrBlank()) {
                _users.clear()
                _users.addAll(fetched)
            } else {
                for (u in fetched) {
                    val idx = _users.indexOfFirst { it.id == u.id }
                    if (idx == -1) _users.add(u) else _users[idx] = u
                }
            }
        }
        notifyChatListeners()
    }

    /** Dart ApiBackendService.searchUsers: loadUsers(query) over GET /chat/users?search=
     *  (with the same fallbacks), then the in-memory directory filtered by lowercase contains on
     *  displayName/username/email; self excluded unless includeSelf. */
    suspend fun searchUsers(query: String, includeSelf: Boolean = false): List<UserProfile> {
        val q = query.lowercase().trim()
        loadUsers(q)
        return com.example.chat.ui.chats.newchat.NewChatRules.filterUsers(users, q, session.currentUser?.id, includeSelf)
    }

    /** Dart setActiveConversation: track the room, start sync, load messages. */
    fun setActiveConversation(conversationId: String?) {
        activeConversationId = conversationId
        if (!conversationId.isNullOrEmpty()) {
            realtime.connect()
            startBackgroundSync()
            chatScope.launch {
                loadMessages(conversationId)
                markConversationRead(conversationId)
            }
        }
    }

    /** _startBackgroundSync: every 4s poll the active room; every 3rd tick
     *  refresh the conversation list; stops when the session ends. */
    fun startBackgroundSync() {
        if (syncRunning) return
        syncRunning = true
        chatScope.launch {
            var ticks = 0
            while (true) {
                delay(4000)
                if (session.token == null) break
                ensureFreshToken()
                if (session.token == null) break
                ticks++
                callCoordinator.tick()
                // Flutter's sync loop also recovers an absent WebSocket. connect()
                // is idempotent while connecting/connected and retains the configured URL.
                if (!realtime.isConnected) realtime.connect()
                flushOutbox() // messages typed while offline
                activeConversationId?.takeIf { it.isNotEmpty() }?.let { syncActiveRoomMessages(it) }
                if (ticks % 3 == 0) loadConversations()
            }
            syncRunning = false
        }
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
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.clearMessages(chatId) }
    }

    private suspend fun syncActiveRoomMessages(chatId: String) {
        val remote = withContext(Dispatchers.IO) { chat.listMessages(chatId) } ?: return
        var changed = false
        synchronized(chatLock) {
            val current = _messages[chatId] ?: mutableListOf()
            val pending = current.filter {
                it.deliveryState == DeliveryState.sending || it.id.startsWith("temp_")
            }
            val hasChanges = remote != current.filterNot { it in pending }
            if (hasChanges) {
                val merged = remote.toMutableList()
                for (p in pending) {
                    if (isQueued(p.id) || remote.none { it.text == p.text }) merged.add(p)
                }
                merged.sortBy { it.createdAt }
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
        if (changed) notifyChatListeners()
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
                    if (list.none { it.id == confirmed.id }) { list.add(confirmed); list.sortBy { it.createdAt } }
                }
            },
        )
        val ok = withContext(Dispatchers.IO) { sender.send(expected, conversationId, text) }
        if (merged) notifyChatListeners()
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

    private fun outboxKey(userId: String) = "api_outbox_$userId"

    /** Loads the signed-in user's queued messages once and puts them back in their chats. */
    private fun ensureOutboxLoaded() {
        val userId = session.currentUser?.id ?: return
        if (outboxLoadedFor == userId) return
        val restored = mutableListOf<OutboxEntry>()
        try {
            val arr = org.json.JSONArray(prefs.getString(outboxKey(userId), "[]") ?: "[]")
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
            outboxLoadedFor = userId
        }
        if (restored.isNotEmpty()) {
            synchronized(chatLock) {
                for (e in restored) {
                    val list = _messages.getOrPut(e.conversationId) { mutableListOf() }
                    if (list.none { it.id == e.tempId }) list.add(e.message)
                    list.sortBy { it.createdAt }
                }
            }
            notifyChatListeners()
        }
    }

    private fun persistOutboxLocked() {
        val userId = outboxLoadedFor ?: return
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
        try { prefs.edit().putString(outboxKey(userId), arr.toString()).apply() } catch (_: Exception) {}
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

    /** One HTTP attempt for a queued message; updates the chat and the outbox from the result. */
    private suspend fun sendOutboxEntry(entry: OutboxEntry): ApiChatClient.SendResult {
        val result = withContext(Dispatchers.IO) {
            chat.sendMessageResult(entry.conversationId, entry.content, clientId = entry.tempId)
        }
        when (result) {
            is ApiChatClient.SendResult.Ok -> {
                removeFromOutbox(entry.tempId)
                val confirmed = result.message
                synchronized(outboxLock) {
                    confirmedSends[entry.tempId] = confirmed
                    if (confirmedSends.size > 50) confirmedSends.keys.firstOrNull()?.let { confirmedSends.remove(it) }
                }
                synchronized(chatLock) {
                    val list = _messages.getOrPut(entry.conversationId) { mutableListOf() }
                    // The WebSocket echo may already have added the confirmed message: never keep two.
                    list.removeAll { it.id == entry.tempId }
                    val idx = list.indexOfFirst { it.id == confirmed.id }
                    if (idx != -1) {
                        // Keep whichever tick is further along (the echo/receipts may have arrived first).
                        if (list[idx].deliveryState.ordinal < confirmed.deliveryState.ordinal) list[idx] = confirmed
                    } else {
                        list.add(confirmed)
                    }
                    list.sortBy { it.createdAt }
                    touchConversationPreview(entry.conversationId, confirmed)
                }
                notifyChatListeners()
            }
            is ApiChatClient.SendResult.Rejected -> {
                removeFromOutbox(entry.tempId)
                synchronized(outboxLock) {
                    rejectedSends[entry.tempId] = result.detail ?: "Message could not be sent (${result.code})."
                    if (rejectedSends.size > 50) rejectedSends.keys.firstOrNull()?.let { rejectedSends.remove(it) }
                }
                setLocalDeliveryState(entry.conversationId, entry.tempId, DeliveryState.failed)
            }
            ApiChatClient.SendResult.Offline -> Unit // stays queued (clock)
        }
        return result
    }

    /** Sends everything queued, oldest first. Stops at the first "still offline" so order is kept. */
    suspend fun flushOutbox() {
        if (session.token == null) return
        ensureOutboxLoaded()
        if (!flushMutex.tryLock()) return
        try {
            while (session.token != null) {
                val next = synchronized(outboxLock) { outbox.firstOrNull() } ?: break
                val r = sendOutboxEntry(next)
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
    var unregisterFcmHook: (() -> Unit)? = null
    /** Offline demo: returns true when any real GoTrue identity is live or
     *  persisted (wired by ServiceLocator). Demo entry FAILS CLOSED in that
     *  case; a real stored session is never cleared as demo prep. */
    var demoGoTruePresentHook: (suspend () -> Boolean)? = null

    /** Dart ApiBackendService.uploadMedia (api_backend_service.dart:1569):
     *  multipart POST /chat/upload with the /api/v1 404 fallback. */
    suspend fun uploadMedia(file: java.io.File): Map<String, Any> =
        withContext(Dispatchers.IO) { mediaUpload.uploadMedia(file) }

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
                    prefs.getString("api_hub_token", null),
                    prefs.getString("api_hub_user", null),
                )
            ) {
                DemoMode.active = true
            }
        } catch (_: Exception) {
        }
        session.restore()
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
                chatScope.launch { ensureFreshToken() }
                realtime.connect()
                startBackgroundSync()
                chatScope.launch { ensureOutboxLoaded(); flushOutbox() }
                chatScope.launch { loadConversations() }
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
        val storedToken = try { prefs.getString("api_hub_token", null) } catch (_: Exception) { null }
        val storedUser = try { prefs.getString("api_hub_user", null) } catch (_: Exception) { null }
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
            val result = auth.login(value, password)
            session.adopt(result)
            saveRefreshToken(result.refreshToken)
            afterAuthAdopted()
            result.user
        }

    /** Dart _parseAuthResponse tail (api_backend_service.dart:565-572), shared by login, register,
     *  registerOrganization and oauth: connect the socket, load conversations/users/tasks, sync FCM. */
    private fun afterAuthAdopted() {
        purgeDemoResidueForRealAdopt()
        realtime.connect()
        startBackgroundSync()
        chatScope.launch { loadConversations() }
        chatScope.launch { loadUsers() }
        chatScope.launch { loadTasks() }
        try { syncFcmTokenHook?.invoke() } catch (_: Exception) {
        }
    }

    suspend fun register(email: String, username: String, password: String): ApiUserProfile =
        withContext(Dispatchers.IO) {
            val result = auth.register(email, username, password)
            session.adopt(result)
            saveRefreshToken(result.refreshToken)
            afterAuthAdopted()
            result.user
        }

    suspend fun forgotPassword(email: String): String = withContext(Dispatchers.IO) {
        auth.forgotPassword(email)
    }

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
        afterAuthAdopted()
        Triple(result.user, orgId, resolvedOrgName)
    }

    /** ApiBackendService.updateProfile: PUT profile, then apply the local
     *  user update (effective name + first-char initials) and persist. */
    suspend fun updateProfile(
        displayName: String? = null,
        about: String? = null,
        username: String? = null,
        avatarUrl: String? = null,
        expectedScope: ApiAccountScope? = null,
    ): ApiUserProfile {
        val expected = expectedScope ?: authSnapshot()
        return withContext(Dispatchers.IO) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            auth.updateProfile(expected, { authSnapshot() === expected }, displayName, about, username, avatarUrl)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            var updated: ApiUserProfile? = null
            if (!withAuthScope(expected) {
                updated = session.updateProfileFields(displayName, about, username, avatarUrl)
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
            val url = accountClient.uploadProfilePhoto(expected, { authSnapshot() === expected }, filename, bytes)
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

    /** Dart ApiBackendService.onAppResumed (api_backend_service.dart:914):
     *  reconnect the realtime socket if down, reload conversations, and
     *  refresh the active conversation's messages. */
    fun onAppResumed() {
        if (session.token == null) return
        chatScope.launch { ensureFreshToken() }
        realtime.connect()
        chatScope.launch { flushOutbox() }
        chatScope.launch { loadConversations() }
        activeConversationId?.takeIf { it.isNotEmpty() }?.let { id ->
            chatScope.launch { loadMessages(id) }
        }
    }

    /** Wipes published chat caches after a non-null account switch. Runs inside
     *  the scope-owner lock (withAuthScope), so it is atomic with guarded async
     *  writes: an old-account completion either applied before this clear (and
     *  is wiped) or fails its identity check after. Lock order everywhere:
     *  scope-owner lock -> chatLock. */
    fun clearChatCachesForAccountSwitch() {
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
        // Dart signOut: FirebaseNotificationService.unregisterOnLogout first.
        try { unregisterFcmHook?.invoke() } catch (_: Exception) {
        }
        realtime.disconnect()
        DemoMode.active = false
        // Preservation (peer mixed-store blocker): signing out a demo session
        // may only erase a STRICTLY-demo persisted pair; a mixed/corrupt store
        // keeps its raw bytes and only the in-memory demo session is dropped.
        val signingOutDemo = isDemoSession
        val storedStrictDemo = try {
            DemoMode.isDemoRestSession(
                prefs.getString("api_hub_token", null),
                prefs.getString("api_hub_user", null),
            )
        } catch (_: Exception) { false }
        if (signingOutDemo && !storedStrictDemo) {
            session.clearInMemoryOnly()
        } else {
            session.signOut()
        }
        saveRefreshToken(null)
        onlineIds.clear(); lastSeenById.clear(); typingUntil.clear()
        // Queued messages stay saved for this account and are sent after its next login.
        synchronized(outboxLock) { outbox.clear(); outboxLoadedFor = null; confirmedSends.clear(); rejectedSends.clear() }
        // Dart signOut: drop the active room and all in-memory chat state
        // (tasks are intentionally untouched, as in Dart), then notify.
        activeConversationId = null
        synchronized(chatLock) {
            _conversations.clear()
            _users.clear()
            _messages.clear()
        }
        notifyChatListeners()
    }

    // ---- Access-token refresh (/auth/refresh) ----
    // Access tokens live JWT_EXPIRE_MINUTES (60 by default). Without a refresh every
    // request starts failing with 401 an hour after login.

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    private fun saveRefreshToken(token: String?) {
        try {
            if (token.isNullOrBlank()) prefs.edit().remove("api_hub_refresh_token").apply()
            else prefs.edit().putString("api_hub_refresh_token", token).apply()
        } catch (_: Exception) {
        }
    }

    private fun loadRefreshToken(): String? =
        try { prefs.getString("api_hub_refresh_token", null) } catch (_: Exception) { null }

    /** Renews the access token when it expires within [REFRESH_MARGIN_SECONDS]. Waits while a
     *  call is active (a new token is a new account scope, which would end the call). Signs the
     *  user out when the token is already expired and cannot be renewed. */
    suspend fun ensureFreshToken() {
        val current = session.token ?: return
        val remaining = auth.secondsUntilExpiry(current) ?: return
        if (remaining > REFRESH_MARGIN_SECONDS) return
        if (remaining > 0 && isCallActive()) return
        refreshMutex.lock()
        try {
            if (session.token != current) return // someone else refreshed meanwhile
            val user = session.currentUser ?: return
            val refreshToken = loadRefreshToken()
            if (refreshToken == null) {
                if (remaining <= 0) withContext(Dispatchers.Main) { signOut() }
                return
            }
            when (val result = withContext(Dispatchers.IO) { auth.refresh(refreshToken) }) {
                is ApiAuthClient.RefreshResult.Ok -> {
                    session.adopt(ApiAuthResult(token = result.accessToken, user = user, refreshToken = refreshToken))
                    realtime.connect()
                }
                ApiAuthClient.RefreshResult.Rejected -> withContext(Dispatchers.Main) { signOut() }
                ApiAuthClient.RefreshResult.Unavailable -> Unit // retry on the next tick
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
        const val REFRESH_MARGIN_SECONDS = 10 * 60L
        const val TYPING_TTL_MS = 6_000L
    }
}
