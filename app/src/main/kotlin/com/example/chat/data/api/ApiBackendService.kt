package com.example.chat.data.api

import android.content.Context
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

/** Android holder for the REST backend: ApiBackendConfig (tenant + base URL),
 *  ApiAuthClient (endpoints), ApiAuthSession (api_hub_token/api_hub_user
 *  persistence). Mirrors the api branch's ApiBackendService auth surface; the
 *  ChatyBackendService facade prefers this session, so splash/home routing
 *  reads [isAuthenticated]/[currentUser] from here first. */
class ApiBackendService(
    context: Context,
    chatTransport: ApiHttpTransport = OkHttpApiTransport(),
) {

    val config = ApiBackendConfig(context)

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

    private val session = ApiAuthSession(sessionStore)

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
    fun sendCallSignal(targetUserId: String, data: Map<String, Any?>): Boolean =
        realtime.sendCallSignal(targetUserId, data)
    fun removeCallSignalListener(listener: (Map<String, Any?>) -> Unit) { callSignalListeners.remove(listener) }

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
            synchronized(chatLock) {
                val roomId = message.conversationId
                val list = _messages.getOrPut(roomId) { mutableListOf() }
                // Dart: replace the optimistic sending/temp message matching
                // text + sender; otherwise append if the id is new.
                val optIdx = list.indexOfFirst { m ->
                    (m.deliveryState == DeliveryState.sending || m.id.startsWith("temp_")) &&
                        m.text == message.text &&
                        (m.senderId == message.senderId || m.senderId == session.currentUser?.id)
                }
                if (optIdx != -1) {
                    list[optIdx] = message
                } else if (list.none { it.id == message.id }) {
                    list.add(message)
                }
                list.sortBy { it.createdAt }
                val convIdx = _conversations.indexOfFirst { it.id == roomId }
                if (convIdx != -1) {
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
                } else {
                    unknownRoom = true
                }
            }
            if (unknownRoom) chatScope.launch { loadConversations() }
            notifyChatListeners()
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
            synchronized(chatLock) {
                val list = _messages[roomId] ?: return@synchronized
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
            notifyChatListeners()
        }

        override fun onMessagesCleared(roomId: String) {
            synchronized(chatLock) {
                _messages[roomId]?.clear()
                val convIdx = _conversations.indexOfFirst { it.id == roomId }
                if (convIdx != -1) {
                    _conversations[convIdx] = _conversations[convIdx].copy(lastMessageText = "")
                }
            }
            notifyChatListeners()
        }

        override fun onCallSignal(data: Map<String, Any?>) {
            callSignalListeners.forEach { it(data) }
        }
    }

    private val realtime = ApiRealtimeClient(
        webSocketUrl = { resolveWebSocketUrl() },
        token = { session.token },
    ).apply { listener = realtimeListener }

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
    @Volatile private var syncRunning = false

    val conversations: List<Conversation> get() = synchronized(chatLock) { _conversations.toList() }
    val users: List<UserProfile> get() = synchronized(chatLock) { _users.toList() }
    fun getMessages(chatId: String): List<ChatMessage> =
        synchronized(chatLock) { (_messages[chatId] ?: emptyList()).toList() }

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
        val remote = withContext(Dispatchers.IO) { chat.listMessages(chatId) } ?: return
        synchronized(chatLock) {
            val current = _messages[chatId] ?: mutableListOf()
            val pending = current.filter { m ->
                (m.deliveryState == DeliveryState.sending || m.id.startsWith("temp_")) &&
                    remote.none { it.text == m.text }
            }
            _messages[chatId] = (remote + pending).sortedBy { it.createdAt }.toMutableList()
        }
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
        synchronized(chatLock) {
            _conversations.removeAll { it.id == chatId }
            _messages.remove(chatId)
            if (activeConversationId == chatId) activeConversationId = null
        }
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.deleteRoom(chatId) }
    }

    suspend fun loadUsers(query: String? = null) {
        val fetched = withContext(Dispatchers.IO) { chat.listUsers(query) } ?: return
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

    /** Dart setActiveConversation: track the room, start sync, load messages. */
    fun setActiveConversation(conversationId: String?) {
        activeConversationId = conversationId
        if (!conversationId.isNullOrEmpty()) {
            realtime.connect()
            startBackgroundSync()
            chatScope.launch { loadMessages(conversationId) }
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
                ticks++
                // Flutter's sync loop also recovers an absent WebSocket. connect()
                // is idempotent while connecting/connected and retains the configured URL.
                if (!realtime.isConnected) realtime.connect()
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
        synchronized(chatLock) {
            val list = _messages[chatId]
            if (list != null) {
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
        notifyChatListeners()
        withContext(Dispatchers.IO) { chat.deleteMessage(chatId, messageId) }
    }

    /** Dart clearConversationMessages: local clear + preview, then DELETE all. */
    suspend fun clearConversationMessages(chatId: String) {
        synchronized(chatLock) {
            _messages[chatId] = mutableListOf()
            val convIdx = _conversations.indexOfFirst { it.id == chatId }
            if (convIdx != -1) {
                _conversations[convIdx] = _conversations[convIdx].copy(lastMessageText = "")
            }
        }
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
                    if (remote.none { it.text == p.text }) merged.add(p)
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

    /** sendMessage: optimistic temp message, POST, confirm replaces temp and
     *  bumps the conversation to the top; failure leaves the temp sending. */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        type: MessageType = MessageType.text,
        attachment: com.example.chat.domain.models.MessageAttachment? = null,
        extraMetadata: Map<String, Any?>? = null,
    ): ChatMessage? {
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
            senderId = session.currentUser?.id ?: "",
            type = type,
            text = text,
            attachment = attachment,
            createdAt = Instant.now(),
            deliveryState = DeliveryState.sending,
        )
        synchronized(chatLock) {
            _messages.getOrPut(conversationId) { mutableListOf() }.add(tempMsg)
        }
        notifyChatListeners()

        val confirmed = withContext(Dispatchers.IO) { chat.sendMessage(conversationId, effectiveContent) }
        if (confirmed == null) {
            // Dart: fall back to the WebSocket send when HTTP did not confirm;
            // the temp message stays in 'sending' either way.
            if (!realtime.sendMessageFallback(conversationId, effectiveContent)) {
                throw IllegalStateException("Message not confirmed. Check your connection; the pending message will remain visible.")
            }
            return null
        }
        synchronized(chatLock) {
            val list = _messages.getOrPut(conversationId) { mutableListOf() }
            val idx = list.indexOfFirst { it.id == tempId }
            if (idx != -1) list[idx] = confirmed else list.add(confirmed)
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
        notifyChatListeners()
        return confirmed
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

    /** Dart ApiBackendService.uploadMedia (api_backend_service.dart:1569):
     *  multipart POST /chat/upload with the /api/v1 404 fallback. */
    suspend fun uploadMedia(file: java.io.File): Map<String, Any> =
        withContext(Dispatchers.IO) { mediaUpload.uploadMedia(file) }

    /** Byte-array form used by ChatMediaService's post-compression payload;
     *  Dart uploads the (possibly compressed) File, whose filename is the
     *  attachment name - here the name travels alongside the bytes. */
    suspend fun uploadMedia(filename: String, bytes: ByteArray): Map<String, Any> =
        withContext(Dispatchers.IO) { mediaUpload.uploadMedia(filename, bytes) }

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
        config.init()
        session.restore()
        // Dart initialize(): restore _currentOrg from the persisted org payload.
        try {
            prefs.getString("api_hub_org", null)?.let { currentOrg = ApiChatClient.deepMap(org.json.JSONObject(it)) }
        } catch (_: Exception) {
        }
        // Dart initialize(): after session restore, fire the chat-plane loads.
        if (session.isAuthenticated) {
            realtime.connect()
            startBackgroundSync()
            chatScope.launch { loadConversations() }
            chatScope.launch { loadUsers() }
            chatScope.launch { loadTasks() }
            // Dart _syncFcmToken() (api_backend_service.dart:119).
            try { syncFcmTokenHook?.invoke() } catch (_: Exception) {
            }
        }
    }

    /** UsernameLoginService.login: strip leading @, apply tenant selection,
     *  then delegate to ApiBackendService.login. */
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
            afterAuthAdopted()
            result.user
        }

    /** Dart _parseAuthResponse tail (api_backend_service.dart:565-572), shared by login, register,
     *  registerOrganization and oauth: connect the socket, load conversations/users/tasks, sync FCM. */
    private fun afterAuthAdopted() {
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
    ): ApiUserProfile = withContext(Dispatchers.IO) {
        if (session.token == null || session.currentUser == null) throw Exception("Not authenticated")
        auth.updateProfile(displayName, about, username, avatarUrl)
        val updated = session.updateProfileFields(displayName, about, username, avatarUrl)
        notifyChatListeners()
        updated
    }

    /** Dart updateOrganizationRegistrationSupport (PATCH /organization/<id>/registration). */
    suspend fun updateOrganizationRegistrationSupport(organizationIdOrTenantId: String, allowUserRegistration: Boolean): Boolean =
        withContext(Dispatchers.IO) { accountClient.updateRegistrationSupport(organizationIdOrTenantId, allowUserRegistration) }

    /** Dart uploadProfilePhoto: multipart POST /app-users/upload-photo; on a non-empty URL the current user's
     *  avatarUrl is updated and persisted (api_hub_user), then listeners are notified. */
    suspend fun uploadProfilePhoto(filename: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val url = accountClient.uploadProfilePhoto(filename, bytes)
        if (url.isNotEmpty() && session.currentUser != null) {
            session.updateProfileFields(null, null, null, url)
            notifyChatListeners()
        }
        url
    }

    suspend fun checkOrganization(workspace: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        auth.checkOrganization(workspace)
    }

    /** Dart ApiBackendService.onAppResumed (api_backend_service.dart:914):
     *  reconnect the realtime socket if down, reload conversations, and
     *  refresh the active conversation's messages. */
    fun onAppResumed() {
        if (session.token == null) return
        realtime.connect()
        chatScope.launch { loadConversations() }
        activeConversationId?.takeIf { it.isNotEmpty() }?.let { id ->
            chatScope.launch { loadMessages(id) }
        }
    }

    fun signOut() {
        // Dart signOut: FirebaseNotificationService.unregisterOnLogout first.
        try { unregisterFcmHook?.invoke() } catch (_: Exception) {
        }
        realtime.disconnect()
        session.signOut()
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
}
