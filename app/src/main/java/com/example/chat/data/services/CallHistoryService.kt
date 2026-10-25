package com.example.chat.data.services

import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.domain.models.CallType
import java.time.Instant

/**
 * Exact port of lib/data/services/call_history_service.dart. RLS-backed
 * source of truth for the recent-calls list: only terminal call_sessions
 * rows, newest first, capped at 100. The Dart side backs this with a
 * supabase .stream() live query; the Kotlin port re-runs the same bounded
 * query on each realtime change to call_sessions (wiring supplies the
 * trigger) plus [start]/[retry] refreshes - same visible behavior, same
 * mapping.
 */
class CallHistoryService(
    private val queryClient: PostgrestQueryClient,
    private val currentUserId: () -> String?,
    private val notifyChanged: () -> Unit = {},
) {
    var records: List<CallRecord> = emptyList()
        private set
    var lastError: Throwable? = null
        private set
    private var userId: String? = null

    /** Exact port of start()'s load semantics (the stream's first emission). */
    suspend fun start() {
        val uid = currentUserId()
        if (uid == null) {
            stop()
            return
        }
        userId = uid
        lastError = null
        refresh()
    }

    suspend fun retry() = start()

    /** Called by the realtime wiring whenever call_sessions changes. */
    suspend fun refresh() {
        val uid = userId ?: return
        try {
            val rows = queryClient.from("call_sessions")
                .select()
                .order("started_at", ascending = false)
                .limit(100)
                .execute()
            val next = mutableListOf<CallRecord>()
            for (row in rows) {
                val record = mapCallHistoryRow(row, uid)
                if (record != null) next.add(record)
            }
            records = next
            lastError = null
            notifyChanged()
        } catch (error: Throwable) {
            lastError = error
            notifyChanged()
        }
    }

    fun stop() {
        userId = null
        records = emptyList()
        lastError = null
        notifyChanged()
    }

    companion object {
        /** Exact port of mapCallHistoryRow (pure; shared with regression
         *  tests on the Dart side). */
        fun mapCallHistoryRow(
            row: Map<String, Any?>,
            currentUserId: String,
        ): CallRecord? {
            val callerId = row["caller_id"]?.toString() ?: ""
            val calleeId = row["callee_id"]?.toString() ?: ""
            if (callerId.isEmpty() || calleeId.isEmpty()) return null
            if (callerId != currentUserId && calleeId != currentUserId) return null

            val status = row["status"]?.toString() ?: ""
            val terminal = setOf("ended", "declined", "busy", "missed", "failed")
            if (!terminal.contains(status)) return null

            val startedAt = parseIsoToInstant(row["started_at"]?.toString()) ?: Instant.now()
            val connectedAt = parseIsoToInstant(row["connected_at"]?.toString())
            val endedAt = parseIsoToInstant(row["ended_at"]?.toString())
            val durationSeconds = if (connectedAt != null && endedAt != null) {
                java.time.Duration.between(connectedAt, endedAt).seconds.coerceIn(0, 86400)
            } else {
                0L
            }

            val outgoing = callerId == currentUserId
            // Any inbound call that never connected is missed, even if its
            // terminal server state is ended rather than declined, busy,
            // missed, or failed.
            val missedInbound = !outgoing && connectedAt == null

            return CallRecord(
                id = row["id"]?.toString() ?: "",
                callerId = callerId,
                participantIds = listOf(callerId, calleeId),
                type = if (row["kind"]?.toString() == "video") CallType.video else CallType.voice,
                direction = when {
                    outgoing -> CallDirection.outgoing
                    missedInbound -> CallDirection.missed
                    else -> CallDirection.incoming
                },
                timestamp = startedAt,
                durationSeconds = durationSeconds,
                isEncrypted = true,
            )
        }
    }
}
