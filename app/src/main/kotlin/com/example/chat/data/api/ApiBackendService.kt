package com.example.chat.data.api

import android.content.Context
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
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
    )

    private val session = ApiAuthSession(sessionStore)

    private val chat = ApiChatClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        token = { session.token },
        transport = OkHttpApiTransport(),
    )

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

        val confirmed = chat.sendMessage(conversationId, text) ?: return null
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
        // Dart initialize(): after session restore, fire the chat-plane loads.
        if (session.isAuthenticated) {
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
            } catch (_: Exception) {
            }
        }
        config.setTenantId(tenantId.trim())
        session.adopt(result)
        Triple(result.user, orgId, resolvedOrgName)
    }

    suspend fun checkOrganization(workspace: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        auth.checkOrganization(workspace)
    }

    fun signOut() = session.signOut()
}
