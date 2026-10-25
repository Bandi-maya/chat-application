package com.example.chat.ui.updates

import com.example.chat.data.services.StatusRecord
import java.time.Instant

/** Exact port of lib/features/updates/status_view_session.dart.
 *  Prevents repeated widget opens and rebuilds from publishing duplicate
 *  views. */
class StatusViewSession {
    private val recorded = HashSet<String>()

    private fun key(status: StatusRecord, viewerId: String) = "${status.id}:$viewerId"

    fun tryBegin(status: StatusRecord, viewerId: String, now: Instant = Instant.now()): Boolean {
        if (status.id.isEmpty() ||
            viewerId.isEmpty() ||
            viewerId == status.userId ||
            status.isDeleted ||
            !status.expiresAt.isAfter(now)
        ) {
            return false
        }
        return recorded.add(key(status, viewerId))
    }

    fun rollback(status: StatusRecord, viewerId: String) {
        recorded.remove(key(status, viewerId))
    }
}
