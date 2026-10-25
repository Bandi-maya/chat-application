package com.example.chat.data.api

import android.content.Context
import com.example.chat.domain.models.ChatMessage
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
class ApiBackendService(context: Context) {

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
        transport = OkHttpApiTransport(),
    )

    private val subscriptionClient = ApiSubscriptionClient(
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
    suspend fun subscribeToPlan(organizationId: String, planId: String, durationMonths: Int = 1): Boolean =
        withContext(Dispatchers.IO) { subscriptionClient.subscribeToPlan(organizationId, planId, durationMonths) }

    // ---- Realtime plane (W1 ApiRealtimeClient integration) ----

    private val callSignalListeners =
        java.util.concurrent.CopyOnWriteArrayList<(Map<String, Any?>) -> Unit>()

    fun addCallSignalListener(listener: (Map<String, Any?>) -> Unit) = callSignalListeners.add(listener)
    /** Dart ApiBackendService.sendCallSignal: fire-and-forget call signaling
     *  over the custom-backend WebSocket (payload type call_signal). */
    fun sendCallSignal(targetUserId: String, data: Map<String, Any?>): Boolean =
        realtime.sendCallSignal(targetUserId, data)
    fun removeCallSignalListener(listener: (Map<String, Any?>) -> Unit) { callSignalListeners.remove(listener) }

    /** getWebSocketUrl (backend_config.dart:80): custom/.env seams are absent
     *  in this repo, so this is the default branch: rest base with ws scheme,
     *  /api/vN and trailing slashes stripped, /chat/ws/<uid>. The uid falls
     *  back to the JWT uid/sub claim when the stored user id is empty. */
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
        val base = config.restApiBaseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .replace(Regex("/api/v[0-9]+/?$"), "")
            .replace(Regex("/+$"), "")
        return "$base/chat/ws/$uid"
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
    private fun notifyChatListeners() = listeners.forEach { it() }

    private val _conversations = mutableListOf<Conversation>()
    private val _users = mutableListOf<UserProfile>()
    private val _messages = mutableMapOf<String, MutableList<ChatMessage>>()

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
    suspend fun loadConversations() {
        when (val result = chat.listRooms()) {
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
        val remote = chat.listMessages(chatId) ?: return
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
            val conversation = chat.createDirectRoom(targetUserId)
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
        val conversation = chat.createGroupRoom(name, memberUserIds)
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
        chat.deleteUser(userId)
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
        chat.deleteRoom(chatId)
    }

    suspend fun loadUsers(query: String? = null) {
        val fetched = chat.listUsers(query) ?: return
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
        chat.editMessage(chatId, messageId, newText)
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
        chat.deleteMessage(chatId, messageId)
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
        chat.clearMessages(chatId)
    }

    private suspend fun syncActiveRoomMessages(chatId: String) {
        val remote = chat.listMessages(chatId) ?: return
        var changed = false
        synchronized(chatLock) {
            val current = _messages[chatId] ?: mutableListOf()
            val pending = current.filter {
                it.deliveryState == DeliveryState.sending || it.id.startsWith("temp_")
            }
            var hasChanges = remote.size != current.size
            if (!hasChanges && remote.isNotEmpty() && current.isNotEmpty()) {
                hasChanges = remote.last().id != current.last().id ||
                    remote.first().id != current.first().id
            }
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
    suspend fun sendMessage(conversationId: String, text: String): ChatMessage? {
        val tempId = "temp_" + System.currentTimeMillis()
        val tempMsg = ChatMessage(
            id = tempId,
            conversationId = conversationId,
            senderId = session.currentUser?.id ?: "",
            type = MessageType.text,
            text = text,
            createdAt = Instant.now(),
            deliveryState = DeliveryState.sending,
        )
        synchronized(chatLock) {
            _messages.getOrPut(conversationId) { mutableListOf() }.add(tempMsg)
        }
        notifyChatListeners()

        val confirmed = chat.sendMessage(conversationId, text)
        if (confirmed == null) {
            // Dart: fall back to the WebSocket send when HTTP did not confirm;
            // the temp message stays in 'sending' either way.
            realtime.sendMessageFallback(conversationId, text)
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
            realtime.connect()
            startBackgroundSync()
            chatScope.launch { loadConversations() }
            chatScope.launch { loadUsers() }
            result.user
        }

    suspend fun register(email: String, username: String, password: String): ApiUserProfile =
        withContext(Dispatchers.IO) {
            val result = auth.register(email, username, password)
            session.adopt(result)
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
        realtime.connect()
        startBackgroundSync()
        chatScope.launch { loadConversations() }
        chatScope.launch { loadUsers() }
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

    suspend fun checkOrganization(workspace: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        auth.checkOrganization(workspace)
    }

    fun signOut() {
        realtime.disconnect()
        session.signOut()
    }
}
