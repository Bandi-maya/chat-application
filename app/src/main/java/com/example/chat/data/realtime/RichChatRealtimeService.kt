package com.example.chat.data.realtime

import com.example.chat.data.messaging.SupabaseRpc
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.data.remote.PostgresChange
import com.example.chat.data.remote.PostgresChangeSubscription
import com.example.chat.data.remote.RealtimeChannel
import com.example.chat.data.remote.RealtimeSubscribeStatus
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.RealtimeSocketStatus
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Contact typing/recording state, as the Dart ContactActivityState. */
data class ContactActivityState(
    val isTyping: Boolean = false,
    val isRecording: Boolean = false,
    val updatedAt: Instant,
)

/** Reports Realtime socket transitions to the connection-health slice
 *  (Dart ConnectionHealthService.notifyRealtimeStatus). */
fun interface ConnectionHealthSink {
    fun notifyRealtimeStatus(status: RealtimeSocketStatus)
}

/** Material icon names used by triggerEventNotification in the Dart
 *  notification service; the notification slice resolves them to icons. */
enum class EventNotificationIcon {
    notifications, image, videocam, mic, attachFile, locationOn, person,
    checkCircleOutline, chatBubble, onlinePrediction, keyboardAlt, micNone,
    undo, personOutline, autoAwesome, historyToggleOff,
}

/** Seam over ChatyNotificationService.triggerEventNotification. */
fun interface EventNotificationSink {
    // fun-interface abstract methods cannot declare default values; the
    // service always passes every argument anyway (Dart defaults applied at
    // the call site).
    fun triggerEventNotification(
        title: String,
        body: String,
        icon: EventNotificationIcon,
        colorArgb: Long,
        textColorArgb: Long?,
        userId: String?,
        avatarInitials: String?,
        avatarColorHex: String?,
    )
}

/** Seam over the ChatyPreferencesController reads this service performs.
 *  Property names match the Dart preference fields exactly; gbBool/gbColor
 *  are the GB-settings key lookups (gbColor as ARGB long, null when unset). */
interface RealtimePreferences {
    val enableGlobalNotifications: Boolean
    val showMessagePreview: Boolean
    val notifyContactOnline: Boolean
    val notifyTypingStarted: Boolean
    val notifyMessageDeleted: Boolean
    val messageRevokeAlert: Boolean
    val freezeLastSeen: Boolean
    val hideLastSeenAudience: String
    val hideOnlineAudience: String
    val recordingIndicators: Boolean
    val ghostMode: Boolean
    fun gbBool(key: String, fallback: Boolean = false): Boolean
    fun gbColorArgb(key: String): Long?
}

/** Seam over ChatyBackendService.getUserById / conversations lookup. */
interface RealtimeBackend {
    fun getUserById(userId: String): UserProfile?
    fun isConversationMuted(conversationId: String): Boolean
}

/**
 * Exact port of rich_chat_realtime_service.dart: presence projection,
 * typing/recording activity, message metadata + delivery receipts, revoke
 * alerts, and the incoming-message / contact-online / typing / recording /
 * profile-change toast gating, over one postgres_changes channel.
 *
 * The Dart auth listener maps to [onSessionChanged]: the auth layer calls it
 * with the new user id (null on sign-out), mirroring onAuthStateChange.
 * Demo mode does not exist on api; nothing here special-cases it.
 */
class RichChatRealtimeService(
    private val preferences: RealtimePreferences,
    private val notifications: EventNotificationSink,
    private val backend: RealtimeBackend,
    private val query: PostgrestQueryClient,
    private val rpc: SupabaseRpc,
    private val channelFactory: (
        topic: String,
        tables: List<PostgresChangeSubscription>,
        onChange: (PostgresChange) -> Unit,
        onStatus: (RealtimeSubscribeStatus) -> Unit,
    ) -> RealtimeChannel,
    private val currentUserId: () -> String?,
    private val connectionHealth: ConnectionHealthSink? = null,
    private val notifyChanged: () -> Unit = {},
) {

    private val lock = Any()

    private var channel: RealtimeChannel? = null
    private val presenceByUserId = HashMap<String, PresenceState>()
    private val lastSeenByUserId = HashMap<String, Instant?>()
    private val activityByConversationAndUser = HashMap<String, ContactActivityState>()
    private val metadataByMessageId = HashMap<String, Map<String, Any?>>()
    private val deliveryStateByMessageId = HashMap<String, DeliveryState>()
    private val senderByMessageId = HashMap<String, String>()
    private val trackedConversationIds = LinkedHashSet<String>()
    private val revokeAlerted = LinkedHashSet<String>()
    private val profileFingerprints = HashMap<String, String>()

    /** Message IDs already toast-notified; dedupes reconnect stream replay. */
    private val notifiedMessageIds = LinkedHashSet<String>()

    /** Conversation the user is actively viewing; suppresses its toasts. */
    @Volatile
    var activeConversationId: String? = null

    private var disposed = false
    private var initializing = false

    val isConnected: Boolean
        get() = synchronized(lock) { channel != null }

    /** Mirrors the Dart auth subscription + initial-session kickoff. */
    fun onSessionChanged(userId: String?) {
        if (userId == null) {
            launch { reset() }
        } else {
            launch { initializeForSession() }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { block() }
        }
    }

    fun presenceFor(userId: String): PresenceState =
        synchronized(lock) { presenceByUserId[userId] } ?: PresenceState.offline

    fun isOnline(userId: String): Boolean {
        if (isMyOnlineHidden) return false
        val state = presenceFor(userId)
        return state == PresenceState.online || state == PresenceState.typing
    }

    fun lastSeenFor(userId: String): Instant? {
        if (isMyLastSeenHidden) return null
        return synchronized(lock) { lastSeenByUserId[userId] }
    }

    private val isMyLastSeenHidden: Boolean
        get() = preferences.freezeLastSeen || preferences.hideLastSeenAudience == "Nobody"

    private val isMyOnlineHidden: Boolean
        get() = preferences.hideLastSeenAudience == "Nobody" &&
            preferences.hideOnlineAudience == "Same as Last Seen"

    fun activityFor(conversationId: String, userId: String): ContactActivityState =
        synchronized(lock) { activityByConversationAndUser["$conversationId:$userId"] }
            ?: ContactActivityState(updatedAt = Instant.ofEpochMilli(0))

    fun metadataFor(messageId: String): Map<String, Any?> =
        synchronized(lock) { metadataByMessageId[messageId] } ?: emptyMap()

    fun deliveryStateFor(messageId: String): DeliveryState? =
        synchronized(lock) { deliveryStateByMessageId[messageId] }

    /** Applies live metadata/delivery overrides, as the Dart hydrateMessage. */
    fun hydrateMessage(message: ChatMessage): ChatMessage {
        val (metadata, delivery) = synchronized(lock) {
            metadataByMessageId[message.id] to deliveryStateByMessageId[message.id]
        }
        if (metadata == null && delivery == null) return message
        return message.copy(
            metadata = metadata ?: message.metadata,
            deliveryState = delivery ?: message.deliveryState,
        )
    }

    suspend fun initializeForSession() {
        if (initializing || disposed || currentUserId() == null) return
        initializing = true
        try {
            loadPresenceProjection(emitNotifications = false)
            subscribeRealtime()
            for (conversationId in synchronized(lock) { trackedConversationIds.toList() }) {
                loadConversationRuntime(conversationId)
            }
            if (!disposed) notifyChanged()
        } finally {
            initializing = false
        }
    }

    suspend fun trackConversation(conversationId: String) {
        if (conversationId.isEmpty()) return
        synchronized(lock) { trackedConversationIds.add(conversationId) }
        if (currentUserId() == null) return
        loadConversationRuntime(conversationId)
        if (!disposed) notifyChanged()
    }

    private suspend fun loadConversationRuntime(conversationId: String) {
        coroutineScope {
            val a = async { loadMessageRuntime(conversationId) }
            val b = async { loadActivity(conversationId, emitNotifications = false) }
            a.await(); b.await()
        }
    }

    private suspend fun loadPresenceProjection(emitNotifications: Boolean) {
        if (currentUserId() == null) return
        val rows = query.from("contact_presence_visibility").select().execute()
        for (row in rows) applyPresenceRow(row, emitNotification = emitNotifications)
    }

    private fun applyPresenceRow(row: Map<String, Any?>, emitNotification: Boolean) {
        val userId = row["owner_user_id"]?.toString() ?: ""
        if (userId.isEmpty()) return
        val previous = synchronized(lock) { presenceByUserId[userId] } ?: PresenceState.offline
        val next = presenceFromDatabase(row["presence"]?.toString())
        synchronized(lock) {
            presenceByUserId[userId] = next
            lastSeenByUserId[userId] = dateOf(row["last_seen_at"])
        }
        if (emitNotification &&
            previous == PresenceState.offline &&
            next == PresenceState.online &&
            (preferences.notifyContactOnline || preferences.gbBool("abu_saleh_toast_online"))
        ) {
            val profile = backend.getUserById(userId)
            notifications.triggerEventNotification(
                title = "${profile?.displayName ?: "Contact"} is online",
                body = "Now active in Chaty",
                icon = EventNotificationIcon.onlinePrediction,
                colorArgb = preferences.gbColorArgb("abu_saleh_toast_online_bc") ?: 0xFF69F0AE,
                textColorArgb = preferences.gbColorArgb("abu_saleh_toast_online_tc"),
                userId = userId,
                avatarInitials = profile?.avatarInitials,
                avatarColorHex = profile?.avatarColorHex,
            )
        }
    }

    private suspend fun loadActivity(conversationId: String, emitNotifications: Boolean) {
        val rows = query.from("typing_states").select()
            .eq("conversation_id", conversationId).execute()
        val activeKeys = HashSet<String>()
        for (row in rows) {
            val key = applyActivityRow(row, emitNotification = emitNotifications)
            if (key != null) activeKeys.add(key)
        }
        val prefix = "$conversationId:"
        synchronized(lock) {
            val stale = activityByConversationAndUser.keys
                .filter { it.startsWith(prefix) && !activeKeys.contains(it) }
            for (key in stale) activityByConversationAndUser.remove(key)
        }
    }

    private fun applyActivityRow(row: Map<String, Any?>, emitNotification: Boolean): String? {
        val conversationId = row["conversation_id"]?.toString() ?: ""
        val userId = row["user_id"]?.toString() ?: ""
        if (conversationId.isEmpty() || userId.isEmpty() || userId == currentUserId()) return null
        val key = "$conversationId:$userId"
        val previous = synchronized(lock) { activityByConversationAndUser[key] }
        val next = ContactActivityState(
            isTyping = row["is_typing"] == true,
            isRecording = row["is_recording"] == true,
            updatedAt = dateOf(row["updated_at"]) ?: Instant.now(),
        )
        if (!next.isTyping && !next.isRecording) {
            synchronized(lock) { activityByConversationAndUser.remove(key) }
            return key
        }
        synchronized(lock) { activityByConversationAndUser[key] = next }

        val profile = backend.getUserById(userId)
        if (emitNotification &&
            next.isTyping &&
            previous?.isTyping != true &&
            (preferences.notifyTypingStarted || preferences.gbBool("abu_saleh_toast_typing"))
        ) {
            notifications.triggerEventNotification(
                title = "${profile?.displayName ?: "Contact"} is typing",
                body = "Typing in a conversation",
                icon = EventNotificationIcon.keyboardAlt,
                colorArgb = preferences.gbColorArgb("abu_saleh_toast_typing_bc") ?: 0xFF448AFF,
                textColorArgb = preferences.gbColorArgb("abu_saleh_toast_typing_tc"),
                userId = userId,
                avatarInitials = profile?.avatarInitials,
                avatarColorHex = profile?.avatarColorHex,
            )
        }
        if (emitNotification &&
            next.isRecording &&
            previous?.isRecording != true &&
            preferences.gbBool("notify_recording_started", fallback = true)
        ) {
            notifications.triggerEventNotification(
                title = "${profile?.displayName ?: "Contact"} is recording",
                body = "Recording a voice message",
                icon = EventNotificationIcon.micNone,
                colorArgb = 0xFFFF5252,
                userId = userId,
                avatarInitials = profile?.avatarInitials,
                avatarColorHex = profile?.avatarColorHex,
            )
        }
        return key
    }

    private suspend fun loadMessageRuntime(conversationId: String) {
        val rows = query.from("messages").select("id,sender_id,metadata")
            .eq("conversation_id", conversationId)
            .order("created_at", ascending = false)
            .limit(200)
            .execute()
        val messageIds = mutableListOf<String>()
        for (row in rows) {
            val messageId = row["id"]?.toString() ?: ""
            if (messageId.isEmpty()) continue
            messageIds.add(messageId)
            synchronized(lock) {
                senderByMessageId[messageId] = row["sender_id"]?.toString() ?: ""
                metadataByMessageId[messageId] = mapOf(row["metadata"])
                deliveryStateByMessageId[messageId] =
                    if (senderByMessageId[messageId] == currentUserId()) DeliveryState.sent
                    else DeliveryState.delivered
            }
        }
        if (messageIds.isEmpty()) return
        val receipts = query.from("message_receipts")
            .select("message_id,user_id,delivered_at,read_at")
            .inFilter("message_id", messageIds)
            .execute()
        for (row in receipts) applyReceiptRow(row)
    }

    private fun applyReceiptRow(row: Map<String, Any?>) {
        val messageId = row["message_id"]?.toString() ?: ""
        if (messageId.isEmpty()) return
        synchronized(lock) {
            if (senderByMessageId[messageId] != currentUserId()) return
            if (row["read_at"] != null) {
                deliveryStateByMessageId[messageId] = DeliveryState.read
            } else if (row["delivered_at"] != null &&
                deliveryStateByMessageId[messageId] != DeliveryState.read
            ) {
                deliveryStateByMessageId[messageId] = DeliveryState.delivered
            }
        }
    }

    private fun maybeAlertRevokedMessage(id: String, row: Map<String, Any?>) {
        if (id.isEmpty() || row["deleted_at"] == null) return
        val senderId = synchronized(lock) { senderByMessageId[id] } ?: ""
        if (senderId.isEmpty() || senderId == currentUserId()) return
        if (!preferences.messageRevokeAlert || !preferences.notifyMessageDeleted) return
        val added = synchronized(lock) { revokeAlerted.add(id) }
        if (!added) return
        val profile = backend.getUserById(senderId)
        notifications.triggerEventNotification(
            title = "${profile?.displayName ?: "A contact"} revoked a message",
            body = "A message was deleted for everyone",
            icon = EventNotificationIcon.undo,
            colorArgb = 0xFFFFAB40,
            userId = senderId,
            avatarInitials = profile?.avatarInitials,
            avatarColorHex = profile?.avatarColorHex,
        )
    }

    suspend fun markConversationDelivered(conversationId: String) {
        if (currentUserId() == null) return
        runCatching {
            rpc.rpc(
                "mark_conversation_delivered",
                mapOf("p_conversation_id" to conversationId),
            )
        }
    }

    suspend fun setRecording(conversationId: String, isRecording: Boolean) {
        if (currentUserId() == null) return
        if (isRecording &&
            (!preferences.recordingIndicators ||
                preferences.ghostMode ||
                preferences.gbBool("yo_want_ghostmode"))
        ) {
            return
        }
        runCatching {
            rpc.rpc(
                "set_recording_state",
                mapOf(
                    "p_conversation_id" to conversationId,
                    "p_is_recording" to isRecording,
                ),
            )
        }
    }

    private fun subscribeRealtime() {
        val userId = currentUserId() ?: return
        val old = synchronized(lock) {
            val o = channel
            channel = null
            o
        }
        old?.close()

        val newChannel = channelFactory(
            "chaty-rich-runtime-$userId",
            listOf(
                PostgresChangeSubscription(table = "contact_presence_visibility"),
                PostgresChangeSubscription(table = "typing_states"),
                PostgresChangeSubscription(table = "message_receipts"),
                PostgresChangeSubscription(table = "messages"),
                PostgresChangeSubscription(table = "profiles"),
            ),
            { change -> handleChange(change) },
            { status ->
                connectionHealth?.notifyRealtimeStatus(
                    when (status) {
                        RealtimeSubscribeStatus.subscribed -> RealtimeSocketStatus.connected
                        RealtimeSubscribeStatus.timedOut -> RealtimeSocketStatus.reconnecting
                        RealtimeSubscribeStatus.closed -> RealtimeSocketStatus.disconnected
                        RealtimeSubscribeStatus.channelError -> RealtimeSocketStatus.disconnected
                    },
                )
            },
        )
        synchronized(lock) { channel = newChannel }
    }

    private fun handleChange(change: PostgresChange) {
        val isDelete = change.eventType == "DELETE"
        val row = if (isDelete) change.oldRecord else change.record
        when (change.table) {
            "contact_presence_visibility" -> {
                val owner = row["owner_user_id"]?.toString() ?: ""
                if (isDelete) {
                    synchronized(lock) {
                        presenceByUserId.remove(owner)
                        lastSeenByUserId.remove(owner)
                    }
                } else {
                    applyPresenceRow(row, emitNotification = true)
                }
                if (!disposed) notifyChanged()
            }
            "typing_states" -> {
                val conversationId = row["conversation_id"]?.toString() ?: ""
                val tracked = synchronized(lock) { trackedConversationIds.contains(conversationId) }
                if (!tracked) return
                if (isDelete) {
                    val key = "$conversationId:${row["user_id"]}"
                    synchronized(lock) { activityByConversationAndUser.remove(key) }
                } else {
                    applyActivityRow(row, emitNotification = true)
                }
                if (!disposed) notifyChanged()
            }
            "message_receipts" -> {
                applyReceiptRow(row)
                if (!disposed) notifyChanged()
            }
            "messages" -> {
                val conversationId = row["conversation_id"]?.toString() ?: ""
                val senderId = row["sender_id"]?.toString() ?: ""
                val id = row["id"]?.toString() ?: ""

                if (change.eventType == "INSERT" &&
                    senderId.isNotEmpty() &&
                    senderId != currentUserId() &&
                    conversationId.isNotEmpty()
                ) {
                    launch { markConversationDelivered(conversationId) }
                    triggerIncomingMessageNotification(
                        messageId = id,
                        conversationId = conversationId,
                        senderId = senderId,
                        row = row,
                    )
                }

                val tracked = synchronized(lock) { trackedConversationIds.contains(conversationId) }
                if (!tracked) return
                if (isDelete) {
                    synchronized(lock) {
                        metadataByMessageId.remove(id)
                        senderByMessageId.remove(id)
                        deliveryStateByMessageId.remove(id)
                    }
                } else {
                    synchronized(lock) {
                        metadataByMessageId[id] = mapOf(row["metadata"])
                        senderByMessageId[id] =
                            row["sender_id"]?.toString() ?: senderByMessageId[id] ?: ""
                        if (senderByMessageId[id] == currentUserId()) {
                            deliveryStateByMessageId.putIfAbsent(id, DeliveryState.sent)
                        }
                    }
                    maybeAlertRevokedMessage(id, row)
                }
                if (!disposed) notifyChanged()
            }
            "profiles" -> handleProfileChange(row)
        }
    }

    private fun handleProfileChange(row: Map<String, Any?>) {
        val userId = row["id"]?.toString() ?: ""
        if (userId.isEmpty() || userId == currentUserId()) return
        if (backend.getUserById(userId) == null) return
        val name = row["display_name"]?.toString() ?: ""
        val about = row["about"]?.toString() ?: row["bio"]?.toString() ?: ""
        val avatar = row["avatar_url"]?.toString() ?: ""
        val banner = row["banner_url"]?.toString() ?: ""
        val fingerprint = "$name|$about|$avatar|$banner"
        val previous = synchronized(lock) {
            val p = profileFingerprints[userId]
            profileFingerprints[userId] = fingerprint
            p
        }
        if (previous == null || previous == fingerprint) return
        if (disposed) return
        if (!preferences.enableGlobalNotifications) return
        if (!preferences.gbBool("abu_saleh_toast_profile")) return
        val profile = backend.getUserById(userId)
        notifications.triggerEventNotification(
            title = "${if (name.isNotEmpty()) name else profile?.displayName ?: "Contact"} updated their profile",
            body = if (about.isNotEmpty()) about else "Profile details changed",
            icon = EventNotificationIcon.personOutline,
            colorArgb = preferences.gbColorArgb("abu_saleh_toast_profile_bc") ?: 0xFF6366F1,
            textColorArgb = preferences.gbColorArgb("abu_saleh_toast_profile_tc"),
            userId = userId,
            avatarInitials = profile?.avatarInitials,
            avatarColorHex = profile?.avatarColorHex,
        )
    }

    private fun triggerIncomingMessageNotification(
        messageId: String,
        conversationId: String,
        senderId: String,
        row: Map<String, Any?>,
    ) {
        // Guard: never notify for own messages.
        if (senderId == currentUserId()) return
        // Guard: global notifications disabled.
        if (!preferences.enableGlobalNotifications) return
        // Guard: user is actively viewing this conversation.
        if (activeConversationId == conversationId) return
        // Guard: conversation is muted.
        if (backend.isConversationMuted(conversationId)) return
        // Guard: dedupe, with the same 512-id eviction.
        val shouldNotify = synchronized(lock) {
            if (notifiedMessageIds.contains(messageId)) {
                false
            } else {
                notifiedMessageIds.add(messageId)
                if (notifiedMessageIds.size > MAX_NOTIFIED_IDS) {
                    notifiedMessageIds.remove(notifiedMessageIds.first())
                }
                true
            }
        }
        if (!shouldNotify) return

        val profile = backend.getUserById(senderId)
        val senderName = profile?.displayName ?: "New message"
        val messageType = row["message_type"]?.toString() ?: row["type"]?.toString() ?: "text"
        val previewBody = if (!preferences.showMessagePreview) {
            "New message"
        } else {
            val textContent = row["content"]?.toString() ?: row["text"]?.toString() ?: ""
            buildMessagePreview(messageType, textContent)
        }
        notifications.triggerEventNotification(
            title = senderName,
            body = previewBody,
            icon = iconForMessageType(messageType),
            colorArgb = preferences.gbColorArgb("incoming_message_toast_color") ?: 0xFF6366F1,
            userId = senderId,
            avatarInitials = profile?.avatarInitials,
            avatarColorHex = profile?.avatarColorHex,
        )
    }

    private suspend fun reset() {
        val old = synchronized(lock) {
            val o = channel
            channel = null
            o
        }
        runCatching { old?.close() }
        synchronized(lock) {
            presenceByUserId.clear()
            lastSeenByUserId.clear()
            profileFingerprints.clear()
            activityByConversationAndUser.clear()
            metadataByMessageId.clear()
            deliveryStateByMessageId.clear()
            senderByMessageId.clear()
            revokeAlerted.clear()
        }
        if (!disposed) notifyChanged()
    }

    fun dispose() {
        disposed = true
        val old = synchronized(lock) {
            val o = channel
            channel = null
            o
        }
        old?.close()
    }

    companion object {
        private const val MAX_NOTIFIED_IDS = 512

        fun buildMessagePreview(messageType: String, text: String): String = when (messageType) {
            "image" -> "\uD83D\uDCF7 Photo"
            "video" -> "\uD83C\uDFA5 Video"
            "audio" -> "\uD83C\uDFA4 Voice message"
            "document" -> "\uD83D\uDCC4 Document"
            "location" -> "\uD83D\uDCCD Location"
            "contact" -> "\uD83D\uDC64 Contact"
            "task" -> "✅ Task"
            else -> if (text.isNotEmpty()) {
                if (text.length > 80) text.substring(0, 80) + "…" else text
            } else "New message"
        }

        fun iconForMessageType(messageType: String): EventNotificationIcon = when (messageType) {
            "image" -> EventNotificationIcon.image
            "video" -> EventNotificationIcon.videocam
            "audio" -> EventNotificationIcon.mic
            "document" -> EventNotificationIcon.attachFile
            "location" -> EventNotificationIcon.locationOn
            "contact" -> EventNotificationIcon.person
            "task" -> EventNotificationIcon.checkCircleOutline
            else -> EventNotificationIcon.chatBubble
        }

        fun presenceFromDatabase(value: String?): PresenceState = when (value) {
            "online" -> PresenceState.online
            "away" -> PresenceState.away
            "typing" -> PresenceState.typing
            else -> PresenceState.offline
        }

        @Suppress("UNCHECKED_CAST")
        fun mapOf(value: Any?): Map<String, Any?> =
            value as? Map<String, Any?> ?: emptyMap()

        fun dateOf(value: Any?): Instant? = when (value) {
            null -> null
            is Instant -> value
            else -> com.example.chat.core.util.parseIsoToInstant(value.toString())
        }
    }
}
