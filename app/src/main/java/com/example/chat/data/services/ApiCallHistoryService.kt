package com.example.chat.data.services

import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.api.ApiCallHistoryClient
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.domain.models.CallType
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Call history plane (research 5.4): the Calls tab reads GET /calls/history
 *  through this service instead of the dead Supabase call_sessions query.
 *  Entries are per-viewer; delete hides a row only for this user. */
class ApiCallHistoryService(
    private val client: () -> ApiCallHistoryClient,
    private val currentUserId: () -> String?,
    private val notifyChanged: () -> Unit = {},
) {
    var records: List<CallRecord> = emptyList()
        private set
    var lastError: Throwable? = null
        private set
    var nextCursor: String? = null
        private set

    suspend fun start() = refresh()
    suspend fun retry() = refresh()

    suspend fun refresh() {
        val uid = currentUserId()
        if (uid == null) {
            records = emptyList()
            lastError = null
            notifyChanged()
            return
        }
        try {
            val page = withContext(Dispatchers.IO) { client().history(limit = 50) }
            records = page.entries.mapNotNull { mapEntry(it, uid) }
            nextCursor = page.nextCursor
            lastError = null
        } catch (error: Throwable) {
            lastError = error
        }
        notifyChanged()
    }

    /** WhatsApp's "Remove from call log": per-viewer delete, optimistic with
     *  rollback. An already-gone row is tolerated by the client. */
    suspend fun deleteEntry(record: CallRecord) {
        val previous = records
        records = records.filterNot { it.id == record.id }
        notifyChanged()
        try {
            withContext(Dispatchers.IO) { client().deleteEntry(record.id) }
            lastError = null
        } catch (error: Throwable) {
            records = previous
            lastError = error
            notifyChanged()
            throw error
        }
    }

    /** "Clear call log": per-viewer clear, optimistic with rollback. */
    suspend fun clearAll() {
        val previous = records
        records = emptyList()
        nextCursor = null
        notifyChanged()
        try {
            withContext(Dispatchers.IO) { client().clear() }
            lastError = null
        } catch (error: Throwable) {
            records = previous
            lastError = error
            notifyChanged()
            throw error
        }
    }

    fun stop() {
        records = emptyList()
        lastError = null
        nextCursor = null
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
            )
        }
    }
}
