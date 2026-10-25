package com.example.chat.data.messaging

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Storage seam for the AES-256-GCM snapshot cache (LocalSnapshotCacheService
 *  port lands with the local-cache slice). */
interface SnapshotCacheStore {
    suspend fun readJson(userId: String, scope: String): Any?
    suspend fun writeJson(userId: String, scope: String, value: Any?)
}

/**
 * Exact port of PendingSecureSendStore semantics: dedupe by client_message_id
 * (idempotency key), sorted by created_at, max 128 pending, serialized
 * mutations.
 */
class PendingSecureSendStore(private val cache: SnapshotCacheStore) {
    companion object {
        const val SCOPE = "pending_secure_sends"
        const val MAX_PENDING = 128
    }

    private val mutationBarrier = Mutex()

    @Suppress("UNCHECKED_CAST")
    suspend fun read(userId: String): List<PendingSecureSend> {
        val value = cache.readJson(userId = userId, scope = SCOPE)
        if (value !is List<*>) return emptyList()
        val byClientMessageId = LinkedHashMap<String, PendingSecureSend>()
        for (item in value) {
            if (item !is Map<*, *>) continue
            val pending = PendingSecureSend.fromJson(
                item.entries.associate { (k, v) -> k.toString() to v }
            )
            if (pending.clientMessageId.isEmpty() || pending.conversationId.isEmpty()) continue
            // A client message ID is the idempotency key. Keep only one entry
            // even if an older/corrupt snapshot contains duplicates.
            byClientMessageId[pending.clientMessageId] = pending
        }
        return byClientMessageId.values.sortedBy { it.createdAt }
    }

    suspend fun put(userId: String, item: PendingSecureSend) = mutationBarrier.withLock {
        val items = read(userId).toMutableList()
        val index = items.indexOfFirst { it.clientMessageId == item.clientMessageId }
        if (index >= 0) {
            items[index] = item
        } else {
            check(items.size < MAX_PENDING) { "Secure pending-send queue is full." }
            items.add(item)
        }
        cache.writeJson(userId = userId, scope = SCOPE, value = items.map { it.toJson() })
    }

    suspend fun remove(userId: String, clientMessageId: String) = mutationBarrier.withLock {
        val items = read(userId).filterNot { it.clientMessageId == clientMessageId }
        cache.writeJson(userId = userId, scope = SCOPE, value = items.map { it.toJson() })
    }

    /** Dart PendingSecureSendStore.clear: serialized write of an empty queue (EncryptedMessageOutbox.clear). */
    suspend fun clear(userId: String) = mutationBarrier.withLock {
        cache.writeJson(userId = userId, scope = SCOPE, value = emptyList<Any?>())
    }
}
