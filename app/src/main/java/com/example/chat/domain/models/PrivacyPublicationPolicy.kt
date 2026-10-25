package com.example.chat.domain.models

/** Exact port of lib/domain/models/privacy_publication_policy.dart. */

/** Privacy-sensitive activity that could be published to another account. */
enum class PrivacyPublication { readReceipt, typing, recording, statusView, presence, lastSeen }

/** The PrivacyPreferences fields the policy reads (full preferences port
 *  lands with M7/M8; this snapshot keeps the policy exact meanwhile). */
data class PrivacyPreferencesSnapshot(
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val recordingIndicators: Boolean = true,
    val hideViewStatus: Boolean = false,
    val freezeLastSeen: Boolean = false,
    val hideLastSeenAudience: String = "Everyone",
    val hideOnlineAudience: String = "Everyone",
)

/**
 * Resolves publication before a network operation is created.
 *
 * This policy deliberately answers whether an event may leave the device. It
 * must not be used merely to hide an indicator after the event was sent.
 */
class PrivacyPublicationPolicy {
    fun allows(
        publication: PrivacyPublication,
        preferences: PrivacyPreferencesSnapshot,
    ): Boolean = when (publication) {
        PrivacyPublication.readReceipt -> preferences.readReceipts
        PrivacyPublication.typing -> preferences.typingIndicators
        PrivacyPublication.recording -> preferences.recordingIndicators
        PrivacyPublication.statusView -> !preferences.hideViewStatus
        PrivacyPublication.presence ->
            !(preferences.hideLastSeenAudience == "Nobody" &&
                preferences.hideOnlineAudience == "Same as Last Seen")
        PrivacyPublication.lastSeen ->
            !preferences.freezeLastSeen &&
                preferences.hideLastSeenAudience != "Nobody"
    }
}
