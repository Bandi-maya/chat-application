package com.example.chat.data.services

import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.domain.models.PresenceState
import java.time.Instant

/**
 * Exact port of backend_service.dart setPresence + _effectivePublishedPresence
 * (+ _currentPrivacy / _presenceToDatabase). Privacy preferences are not yet
 * ported (M7/M8 ChatyPreferencesController); the [privacy] seam supplies a
 * snapshot. Dart behavior when the controller is unregistered: privacy == null
 * publishes presence unchanged and always bumps last_seen_at.
 */
class PresenceService(
    private val queryClient: PostgrestQueryClient,
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

    /** Exact port of setPresence's non-demo path. */
    suspend fun setPresence(presence: PresenceState) {
        val userId = currentUserId() ?: return
        val snap = privacy()
        val update = mutableMapOf<String, Any?>(
            "presence" to presenceToDatabase(effectivePublishedPresence(presence, snap)),
        )
        if (snap == null || !snap.freezeLastSeen) {
            update["last_seen_at"] = Instant.now().toString()
        }
        queryClient.from("profiles").eq("id", userId).update(update)
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
