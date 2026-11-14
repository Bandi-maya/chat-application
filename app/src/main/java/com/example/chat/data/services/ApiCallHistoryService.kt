package com.example.chat.data.services

import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.api.ApiCallHistoryClient
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.domain.models.CallType
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

/** Call history plane (research 5.4): the Calls tab reads GET /calls/history
 *  through this service instead of the dead Supabase call_sessions query.
 *  Entries are per-viewer; delete hides a row only for this user. */
class ApiCallHistoryService(
    private val currentScope: () -> com.example.chat.data.api.ApiAccountScope,
    private val clientFor: (com.example.chat.data.api.ApiAccountScope) -> ApiCallHistoryClient,
    private val withScope: (com.example.chat.data.api.ApiAccountScope, () -> Unit) -> Boolean,
    private val notifyChanged: () -> Unit = {},
) {
    private data class Snapshot(val owner: com.example.chat.data.api.ApiAccountScope? = null,
        val records: List<CallRecord> = emptyList(), val error: Throwable? = null, val cursor: String? = null)
    @Volatile private var snapshot = Snapshot()
    private val stateLock = Any()
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    private val operations = kotlinx.coroutines.sync.Mutex()
    private fun visible(): Snapshot = snapshot.takeIf { it.owner === currentScope() } ?: Snapshot()
    val records: List<CallRecord> get() = visible().records
    val lastError: Throwable? get() = visible().error
    val nextCursor: String? get() = visible().cursor

    private fun publish(owner: com.example.chat.data.api.ApiAccountScope, version: Long, value: Snapshot): Boolean {
        var applied = false
        withScope(owner) { synchronized(stateLock) { if (epoch.get() == version) { snapshot = value; applied = true } } }
        if (applied) notifyChanged()
        return applied
    }
    suspend fun start() = refresh()
    suspend fun retry() = refresh()
    suspend fun refresh() = operations.withLock {
        val owner = currentScope()
        val uid = owner.userId
        if (uid.isNullOrBlank() || owner.token.isNullOrBlank()) { stop(); return@withLock }
        val version = epoch.get()
        val previous = snapshot.takeIf { it.owner === owner } ?: Snapshot(owner)
        try {
            val client = clientFor(owner)
            val page = withContext(Dispatchers.IO) { client.history(limit = 50) }
            publish(owner, version, Snapshot(owner, page.entries.mapNotNull { mapEntry(it, uid) }, cursor = page.nextCursor))
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            publish(owner, version, previous.copy(error = failure))
        }
        Unit
    }
    suspend fun deleteEntry(record: CallRecord) = operations.withLock {
        val owner = currentScope()
        val version = epoch.get()
        val previous = snapshot
        check(previous.owner === owner) { "Account or call history changed. Refresh first." }
        if (previous.records.none { it.id == record.id }) return@withLock
        check(publish(owner, version, previous.copy(records = previous.records.filterNot { it.id == record.id }, error = null))) { "Account changed." }
        try {
            val client = clientFor(owner)
            withContext(Dispatchers.IO) { client.deleteEntry(record.id) }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            publish(owner, version, previous); throw cancel
        } catch (error: Exception) {
            publish(owner, version, previous.copy(error = error)); throw error
        }
    }
    suspend fun clearAll() = operations.withLock {
        val owner = currentScope()
        val version = epoch.get()
        val previous = snapshot
        check(previous.owner === owner) { "Account changed. Refresh first." }
        check(publish(owner, version, Snapshot(owner))) { "Account changed." }
        try {
            val client = clientFor(owner)
            withContext(Dispatchers.IO) { client.clear() }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            publish(owner, version, previous); throw cancel
        } catch (error: Exception) {
            publish(owner, version, previous.copy(error = error)); throw error
        }
    }
    fun stop() {
        synchronized(stateLock) {
            epoch.incrementAndGet()
            snapshot = Snapshot()
        }
        notifyChanged()
    }

    companion object {
        /** Server entry -> the app's existing CallRecord row model. The
         *  server derives direction per viewer; an incoming call that was not
         *  answered shows as missed (WhatsApp-faithful, matches the old
         *  mapper's missedInbound rule). */
        fun mapEntry(entry: ApiCallHistoryClient.CallEntry, currentUserId: String): CallRecord? {
            if (entry.otherUserId.isEmpty()) return null
            val outgoing = entry.direction == "outgoing"
            return CallRecord(
                id = entry.id,
                callerId = if (outgoing) currentUserId else entry.otherUserId,
                participantIds = listOf(currentUserId, entry.otherUserId),
                type = if (entry.kind == "video") CallType.video else CallType.voice,
                direction = when {
                    outgoing -> CallDirection.outgoing
                    entry.status == "answered" -> CallDirection.incoming
                    else -> CallDirection.missed
                },
                timestamp = parseIsoToInstant(entry.startedAt) ?: Instant.now(),
                durationSeconds = entry.durationSec.toLong().coerceIn(0, 86400),
                isEncrypted = true,
                remoteDisplayName = entry.otherDisplayName?.trim()?.takeIf { it.isNotEmpty() },
                remoteUsername = entry.otherUsername?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
