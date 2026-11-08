package com.example.chat.data.services

import com.example.chat.domain.models.PresenceState

/**
 * Presence publishing rides the Chaty backend: POST /presence/heartbeat is
 * the transport (the retired Supabase `profiles` write is gone). The server
 * only believes online for 90s (ONLINE_TTL), so the app heartbeats while
 * foregrounded; a clean offline is the same endpoint with online=false.
 * The privacy mapping of backend_service.dart's _effectivePublishedPresence
 * is preserved: a user who hides online from everyone publishes offline.
 */
class PresenceService(
    private val heartbeat: suspend (online: Boolean) -> Boolean,
    private val currentUserId: () -> String?,
    /** Snapshot of the privacy fields setPresence reads; null when the
     *  preferences controller is unavailable (matches Dart's null path). */
    private val privacy: () -> PrivacySnapshot?,
) {
    /** The three PrivacyPreferences fields setPresence can consult. */
    data class PrivacySnapshot(
        val freezeLastSeen: Boolean,
        val hideLastSeenAudience: String,
        val hideOnlineAudience: String,
    )

    /** Publish a presence state. away/typing count as active (the user is
     *  in the app); the privacy snapshot can still force offline. */
    suspend fun setPresence(presence: PresenceState) {
        // Offline demo: presence is never published from a demo session.
        if (com.example.chat.data.demo.DemoMode.active) return
        currentUserId() ?: return
        val snap = privacy()
        val effective = effectivePublishedPresence(presence, snap)
        // freezeLastSeen: the heartbeat endpoint always refreshes the
        // last-seen stamp, and the server has no frozen-stamp mode. Fail
        // closed: a frozen user publishes nothing and stays invisible
        // rather than leaking a fresh stamp they asked to freeze.
        if (snap != null && snap.freezeLastSeen) return
        heartbeat(effective != PresenceState.offline)
    }

    companion object {
        /** Exact port of _effectivePublishedPresence. */
        fun effectivePublishedPresence(
            presence: PresenceState,
            privacy: PrivacySnapshot?,
        ): PresenceState {
            if (privacy == null) return presence
            val onlineHiddenFromAll =
                privacy.hideLastSeenAudience == "Nobody" &&
                    privacy.hideOnlineAudience == "Same as Last Seen"
            if (onlineHiddenFromAll &&
                (presence == PresenceState.online || presence == PresenceState.typing)
            ) {
                return PresenceState.offline
            }
            return presence
        }

        /** Exact port of _presenceToDatabase. */
        fun presenceToDatabase(presence: PresenceState): String = when (presence) {
            PresenceState.online -> "online"
            PresenceState.away -> "away"
            PresenceState.typing -> "typing"
            PresenceState.offline -> "offline"
        }
    }
}
