package com.example.chat.data.realtime

import com.example.chat.data.remote.PostgresChange
import com.example.chat.data.remote.PostgresChangeSubscription
import com.example.chat.data.remote.RealtimeChannel
import com.example.chat.data.remote.SupabaseRealtimeChannel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Exact port of backend_service.dart _subscribeRealtime /
 * _schedule{Message,Membership,MessageRelated,Task,KeyExchange}Reconciliation /
 * _armRealtimeReconciliation / _flushRealtimeReconciliation /
 * _removeRealtimeChannel / _conversationIdForLoadedMessage.
 *
 * One "chaty-user-<id>" channel with ten table-scoped postgres_changes
 * registrations; changes debounce 120ms into a targeted flush (conversation
 * list, tasks, per-conversation members/messages) with a full-hydration
 * fallback on failure, then a pending-secure-send retry kick.
 *
 * Dart's _scheduleKeyExchangeReconciliation also invalidates
 * MessageTransportCompatibilityService; that plaintext-compat service is
 * registered-but-unused on api and is intentionally not ported (MLS
 * fail-closed), so the invalidation is a no-op seam here.
 */
class RealtimeReconciliationService(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
    private val currentUserId: () -> String?,
    /** Mirrors reads of _conversationsById / _messagesByChatId. */
    private val hasConversation: (String) -> Boolean,
    private val hasLoadedMessages: (String) -> Boolean,
    private val conversationIdForLoadedMessage: (String) -> String?,
    /** Targeted reload seams (Dart _load* methods). */
    private val loadConversations: suspend (loadMembers: Boolean) -> Unit,
    private val loadTasks: suspend () -> Unit,
    private val loadConversationMembers: suspend (String) -> Unit,
    private val loadMessages: suspend (String) -> Unit,
    /** _hydrateAuthenticatedState fallback after a failed flush. */
    private val hydrateFallback: suspend () -> Unit,
    /** unawaited OutgoingMessageQueueEngine.processQueue / _retryPendingSecureSends. */
    private val kickPendingSends: () -> Unit,
    private val notifyChanged: () -> Unit = {},
    /** eventBus RealtimeEventType.conversationUpdated publish. */
    private val publishConversationUpdated: () -> Unit = {},
    private val onStatus: (com.example.chat.data.remote.RealtimeSubscribeStatus) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "chaty-reconcile").apply { isDaemon = true }
    }

    private val lock = Any()
    private var channel: RealtimeChannel? = null
    private var armFuture: ScheduledFuture<*>? = null

    private var pendingConversationListRefresh = false
    private var pendingTaskRefresh = false
    private val pendingMessageConversationIds = LinkedHashSet<String>()
    private val pendingMemberConversationIds = LinkedHashSet<String>()

    /** Exact port of _subscribeRealtime. */
    fun subscribeRealtime() {
        val userId = currentUserId() ?: return
        removeRealtimeChannel()

        val tables = listOf(
            "messages",
            "conversation_members",
            "message_reactions",
            "message_receipts",
            "tasks",
            "mls_devices",
            "mls_key_packages",
            "mls_conversation_groups",
            "mls_control_messages",
            "mls_welcomes",
        )
        val newChannel = SupabaseRealtimeChannel(
            supabaseUrl = supabaseUrl,
            publishableKey = publishableKey,
            accessToken = accessToken,
            topic = "chaty-user-$userId",
            subscriptions = tables.map { PostgresChangeSubscription(table = it) },
            onChange = { change -> handleChange(change) },
            onStatus = onStatus,
        )
        newChannel.start()
        synchronized(lock) { channel = newChannel }
    }

    private fun handleChange(change: PostgresChange) {
        // supabase_dart delete payloads carry the row in oldRecord.
        val row = if (change.eventType == "DELETE") change.oldRecord else change.record
        when (change.table) {
            "messages" -> scheduleMessageReconciliation(row)
            "conversation_members" -> scheduleMembershipReconciliation(row)
            "message_reactions", "message_receipts" ->
                scheduleMessageRelatedReconciliation(row)
            "tasks" -> scheduleTaskReconciliation()
            "mls_devices", "mls_key_packages", "mls_conversation_groups",
            "mls_control_messages", "mls_welcomes" -> scheduleKeyExchangeReconciliation()
        }
    }

    /** Exact port of _scheduleMessageReconciliation. */
    private fun scheduleMessageReconciliation(row: Map<String, Any?>) {
        val conversationId =
            row["conversation_id"]?.toString()
                ?: conversationIdForLoadedMessage(row["id"]?.toString() ?: "")
        synchronized(lock) {
            if (!conversationId.isNullOrEmpty()) {
                pendingMessageConversationIds.add(conversationId)
            }
            pendingConversationListRefresh = true
        }
        armRealtimeReconciliation()
    }

    /** Exact port of _scheduleMembershipReconciliation. */
    private fun scheduleMembershipReconciliation(row: Map<String, Any?>) {
        val conversationId = row["conversation_id"]?.toString() ?: ""
        synchronized(lock) {
            if (conversationId.isNotEmpty()) {
                pendingMemberConversationIds.add(conversationId)
                if (hasLoadedMessages(conversationId)) {
                    pendingMessageConversationIds.add(conversationId)
                }
            }
            pendingConversationListRefresh = true
        }
        armRealtimeReconciliation()
    }

    /** Exact port of _scheduleMessageRelatedReconciliation. */
    private fun scheduleMessageRelatedReconciliation(row: Map<String, Any?>) {
        val messageId = row["message_id"]?.toString() ?: ""
        val conversationId = conversationIdForLoadedMessage(messageId)
        if (conversationId != null) {
            synchronized(lock) { pendingMessageConversationIds.add(conversationId) }
            armRealtimeReconciliation()
        }
    }

    /** Exact port of _scheduleTaskReconciliation. */
    private fun scheduleTaskReconciliation() {
        synchronized(lock) { pendingTaskRefresh = true }
        armRealtimeReconciliation()
    }

    /** Exact port of _scheduleKeyExchangeReconciliation (compat-service
     *  invalidation intentionally omitted - service not ported). */
    private fun scheduleKeyExchangeReconciliation() {
        armRealtimeReconciliation()
    }

    /** Exact port of _armRealtimeReconciliation (120ms debounce). */
    private fun armRealtimeReconciliation() {
        synchronized(lock) {
            armFuture?.cancel(false)
            armFuture = scheduler.schedule(
                { scope.launch { flushRealtimeReconciliation() } },
                RECONCILE_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    /** Exact port of _flushRealtimeReconciliation. */
    private suspend fun flushRealtimeReconciliation() {
        val refreshConversationList: Boolean
        val refreshTasks: Boolean
        val messageConversationIds: Set<String>
        val memberConversationIds: Set<String>
        synchronized(lock) {
            refreshConversationList = pendingConversationListRefresh
            refreshTasks = pendingTaskRefresh
            messageConversationIds = LinkedHashSet(pendingMessageConversationIds)
            memberConversationIds = LinkedHashSet(pendingMemberConversationIds)

            pendingConversationListRefresh = false
            pendingTaskRefresh = false
            pendingMessageConversationIds.clear()
            pendingMemberConversationIds.clear()
        }

        try {
            if (refreshConversationList) {
                loadConversations(false)
            }
            if (refreshTasks) {
                loadTasks()
            }
            for (conversationId in memberConversationIds) {
                if (hasConversation(conversationId)) {
                    loadConversationMembers(conversationId)
                }
            }
            for (conversationId in messageConversationIds) {
                if (hasConversation(conversationId) && hasLoadedMessages(conversationId)) {
                    loadMessages(conversationId)
                }
            }
            if (currentUserId() != null) {
                kickPendingSends()
            }
            notifyChanged()
            publishConversationUpdated()
        } catch (error: Throwable) {
            // Dart logs then falls back to a full re-hydration.
            runCatching { hydrateFallback() }
        }
    }

    /** Exact port of _removeRealtimeChannel. */
    fun removeRealtimeChannel() {
        val old: RealtimeChannel?
        synchronized(lock) {
            armFuture?.cancel(false)
            armFuture = null
            pendingConversationListRefresh = false
            pendingTaskRefresh = false
            pendingMessageConversationIds.clear()
            pendingMemberConversationIds.clear()
            old = channel
            channel = null
        }
        old?.let { runCatching { it.close() } }
    }

    companion object {
        private const val RECONCILE_DEBOUNCE_MS = 120L
    }
}
