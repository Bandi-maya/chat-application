package com.example.chat.ui.island

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.Instant

/** Exact port of the IslandMode enum from
 *  lib/features/island/models/dynamic_island_payload.dart.
 *
 *  Visual display modes for the Dynamic Island matching the 4-state machine.
 */
enum class IslandMode {
    idle,
    compact,
    split,
    expanded,
}

/** Exact port of the IslandPayloadType enum from
 *  lib/features/island/models/dynamic_island_payload.dart.
 *
 *  Payload categorization determining visual priority and layout variants.
 */
enum class IslandPayloadType {
    incomingCall,
    ongoingCall,
    chatMessage,
    voiceNotePlayback,
    media,
    systemAlert,
    contactOnline,
    ghostModeStatus,
    clipboardIntent,
    dragAndGoTarget,
}

/** Exact port of DynamicIslandAction from
 *  lib/features/island/models/dynamic_island_payload.dart.
 *
 *  Action item displayed within the expanded Island view.
 *  Dart VoidCallback onTap -> () -> Unit; IconData -> ImageVector.
 */
data class DynamicIslandAction(
    val id: String,
    val title: String,
    val icon: ImageVector? = null,
    val color: Color? = null,
    val isDestructive: Boolean = false,
    val onTap: () -> Unit,
)

/** Exact port of DynamicIslandPayload from
 *  lib/features/island/models/dynamic_island_payload.dart, including the
 *  priority and isOngoingActivity derived properties.
 *
 *  Data model representing a Dynamic Island notification or activity.
 *  Dart DateTime -> Instant; IconData -> ImageVector.
 */
data class DynamicIslandPayload(
    val id: String,
    val type: IslandPayloadType,
    val title: String,
    val message: String = "",
    val timestamp: Instant,
    val iconEmoji: String? = null,
    val iconData: ImageVector? = null,
    val avatarUrl: String? = null,
    val senderInitials: String? = null,
    val accentColor: Color = Color(0xFF3B82F6),
    val isVerified: Boolean = false,
    val isE2ee: Boolean = true,

    // Media & Call properties
    val isPlaying: Boolean = false,
    val durationSeconds: Int = 0,
    val progress: Double = 0.0, // 0.0 to 1.0
    val isVideo: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val mediaArtist: String? = null,
    val mediaAlbum: String? = null,
    val audioSpeed: String? = "1.0x",

    // OriginOS / Hybrid capabilities
    val clipboardContent: String? = null,
    val clipboardActionLabel: String? = null,
    val onClipboardAction: (() -> Unit)? = null,
    val isBandiIcon: Boolean = false,

    // Interactive capabilities
    val canQuickReply: Boolean = false,
    val actions: List<DynamicIslandAction> = emptyList(),
    val onQuickReply: ((replyText: String) -> Unit)? = null,
    val onMarkAsRead: (() -> Unit)? = null,
    val onAcceptCall: (() -> Unit)? = null,
    val onDeclineCall: (() -> Unit)? = null,
    val onTogglePlay: (() -> Unit)? = null,
    val onSeek: ((progress: Double) -> Unit)? = null,
    val onTap: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
) {
    /** Numeric priority resolution (lower value = higher priority).
     *
     *  Priority hierarchy:
     *  0: Calls (incomingCall) & Drag target (dragAndGoTarget)
     *  1: Ongoing call
     *  2: Active Media / Voice playback / Clipboard Intent
     *  3: Chat messages
     *  4: System alerts, ghost mode, presence
     */
    val priority: Int
        get() = when (type) {
            IslandPayloadType.incomingCall, IslandPayloadType.dragAndGoTarget -> 0
            IslandPayloadType.ongoingCall -> 1
            IslandPayloadType.voiceNotePlayback,
            IslandPayloadType.media,
            IslandPayloadType.clipboardIntent -> 2
            IslandPayloadType.chatMessage -> 3
            IslandPayloadType.ghostModeStatus,
            IslandPayloadType.systemAlert,
            IslandPayloadType.contactOnline -> 4
        }

    /** Whether this payload represents an ongoing activity that should remain
     *  active in compact mode rather than automatically dismissing completely. */
    val isOngoingActivity: Boolean
        get() = type == IslandPayloadType.ongoingCall ||
            type == IslandPayloadType.incomingCall ||
            type == IslandPayloadType.dragAndGoTarget ||
            (type == IslandPayloadType.media && isPlaying) ||
            (type == IslandPayloadType.voiceNotePlayback && isPlaying)
}
