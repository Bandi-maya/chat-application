package com.example.chat.data.messaging

import com.example.chat.data.services.ConnectionHealthService
import com.example.chat.domain.models.ConnectionHealth
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Exact port of outgoing_message_queue_engine.dart: offline persistence via
 * [PendingSecureSendStore], strict FIFO per conversation, client-generated
 * idempotency keys, a concurrency guard, bounded exponential backoff with
 * jitter, and manual single-message retry.
 *
 * Wiring seams replacing the Dart locator/listeners:
 * - [deliver] mirrors ChatyBackendService.retryPendingSecureMessage (it must
 *   rethrow MLS setup-pending failures as [SecureSendPendingException]).
 * - [currentUserId] mirrors _backend.currentUser?.id.
 * - Call [onHealthChanged] from the shared ConnectionHealthService change
 *   callback, and [onSession] from the auth layer; they mirror the Dart
 *   health listener and onAuthStateChange subscription.
 */
class OutgoingMessageQueueEngine(
    private val store: PendingSecureSendStore,
    private val healthService: ConnectionHealthService?,
    private val deliver: suspend (PendingSecureSend) -> Unit,
    private val currentUserId: () -> String?,
    private val notifyChanged: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMutex = Mutex()
    private var isProcessing = false
    private val retryCountByMessageId = HashMap<String, Int>()
    private val nextAllowedRetryByMessageId = HashMap<String, Instant>()

    init {
        // Dart _init only wires listeners and refreshes the count; the
        // listeners here are the onHealthChanged/onSession entry points.
        scope.launch { updateQueuedCount() }
    }

    /** Backoff with jitter: min(30s, 2^min(retries,5)) + 0..499ms. */
    fun calculateBackoff(retryCount: Int): Long {
        if (retryCount <= 0) return 0L
        val expSeconds = min(30.0, 2.0.pow(min(retryCount, 5))).toLong()
        val jitterMs = Random.nextInt(500).toLong()
        return expSeconds * 1000 + jitterMs
    }

    /** Mirrors the Dart health listener: drain when the connection recovers
     *  to excellent or weak. */
    fun onHealthChanged() {
        val health = healthService?.health
        if (health == ConnectionHealth.excellent || health == ConnectionHealth.weak) {
            scope.launch { processQueue() }
        }
    }

    /** Mirrors the Dart auth-state subscription: drain on sign-in. */
    fun onSession(userId: String?) {
        if (userId != null) {
            scope.launch { processQueue() }
        }
    }

    /** Refreshes the queued-message count into ConnectionHealthService. */
    suspend fun updateQueuedCount() {
        val userId = currentUserId() ?: return
        runCatching {
            val items = store.read(userId)
            healthService?.updateQueuedCount(items.size)
            notifyChanged()
        }
    }

    /** Enqueues a message for offline sending or retry. */
    suspend fun enqueue(send: PendingSecureSend) {
        val userId = currentUserId() ?: return
        store.put(userId, send)
        updateQueuedCount()
        if (healthService?.isOnline != false) {
            scope.launch { processQueue() }
        }
    }

    /** Manual retry by clientMessageId, bypassing the backoff timer. */
    suspend fun retryMessage(clientMessageId: String): Boolean {
        val userId = currentUserId() ?: return false
        nextAllowedRetryByMessageId.remove(clientMessageId)
        retryCountByMessageId[clientMessageId] = 0

        val target = store.read(userId)
            .firstOrNull { it.clientMessageId == clientMessageId } ?: return false
        return try {
            deliver(target)
            store.remove(userId, clientMessageId)
            updateQueuedCount()
            true
        } catch (e: Exception) {
            val retries = (retryCountByMessageId[clientMessageId] ?: 0) + 1
            retryCountByMessageId[clientMessageId] = retries
            nextAllowedRetryByMessageId[clientMessageId] =
                Instant.now().plusMillis(calculateBackoff(retries))
            false
        }
    }

    /** Processes all persisted queued messages in FIFO order, guarded
     *  against concurrent triggers exactly like the Dart _isProcessing flag. */
    suspend fun processQueue() {
        processingMutex.withLock {
            if (isProcessing) return
            isProcessing = true
        }
        try {
            val userId = currentUserId() ?: return
            val items = store.read(userId)
            if (items.isEmpty()) {
                healthService?.updateQueuedCount(0)
                return
            }

            // Group by conversation to enforce sequential FIFO per conversation.
            val byConversation = LinkedHashMap<String, MutableList<PendingSecureSend>>()
            for (item in items) {
                byConversation.getOrPut(item.conversationId) { mutableListOf() }.add(item)
            }

            val now = Instant.now()
            for ((_, list) in byConversation) {
                list.sortBy { it.createdAt }
                for (item in list) {
                    val nextAllowed = nextAllowedRetryByMessageId[item.clientMessageId]
                    if (nextAllowed != null && now.isBefore(nextAllowed)) {
                        // In backoff: stop this conversation to preserve FIFO.
                        break
                    }
                    try {
                        deliver(item)
                        store.remove(userId, item.clientMessageId)
                        retryCountByMessageId.remove(item.clientMessageId)
                        nextAllowedRetryByMessageId.remove(item.clientMessageId)
                    } catch (e: SecureSendPendingException) {
                        // Receiver MLS setup pending: preserve FIFO, stop
                        // hammering; a capability/key event re-triggers drain.
                        break
                    } catch (e: Exception) {
                        val retries = (retryCountByMessageId[item.clientMessageId] ?: 0) + 1
                        retryCountByMessageId[item.clientMessageId] = retries
                        nextAllowedRetryByMessageId[item.clientMessageId] =
                            Instant.now().plusMillis(calculateBackoff(retries))
                        // Earlier message failed: stop this conversation queue.
                        break
                    }
                }
            }
            updateQueuedCount()
        } finally {
            processingMutex.withLock { isProcessing = false }
        }
    }
}
