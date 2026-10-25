package com.example.chat.domain.models

import com.example.chat.core.util.parseIsoOrNow
import com.example.chat.core.util.parseIsoToInstant
import java.time.Duration
import java.time.Instant

enum class ConversationType { direct, group }

enum class EncryptionStatus { encrypted, verificationNeeded, demoMode }

data class Conversation(
    val id: String,
    val type: ConversationType,
    val title: String,
    val participantIds: List<String>,
    val adminIds: List<String> = emptyList(),
    val avatarInitials: String? = null,
    val avatarColorHex: String? = null,
    val lastMessageText: String,
    val lastMessageTime: Instant,
    val lastMessageSenderId: String,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val wallpaperId: String? = null,
    val disappearingDuration: Duration? = null,
    val roomType: String? = null,
    val taskId: String? = null,
    // Chaty must not claim E2EE until audited device-to-device encryption is
    // actually implemented and verified. The option remains in the UI.
    val encryptionStatus: EncryptionStatus = EncryptionStatus.verificationNeeded,
    val draftText: String = "",
) {
    val isTask: Boolean get() = roomType == "task" || !taskId.isNullOrEmpty()
    val isGroup: Boolean get() = type == ConversationType.group
    val isDirect: Boolean get() = type == ConversationType.direct

    companion object {
        /** Exact port of Conversation.fromApi (lib/domain/models/conversation.dart). */
        @Suppress("UNCHECKED_CAST")
        fun fromApi(json: Map<String, Any?>): Conversation {
            var lastText = ""
            var lastTime = parseIsoOrNow(json["updated_at"]?.toString())
            var lastSender = ""

            val lastMsg = json["last_message"] as? Map<String, Any?>
            if (lastMsg != null) {
                val raw = lastMsg["content"]?.toString() ?: ""
                lastText = if (raw.startsWith("{") && raw.contains("\"image\"")) {
                    "📷 Photo"
                } else if (raw.startsWith("{") && raw.contains("\"type\"")) {
                    "📎 Attachment"
                } else if (raw.startsWith("http") && (raw.endsWith(".jpg") || raw.endsWith(".png") ||
                        raw.endsWith(".jpeg") || raw.endsWith(".webp"))
                ) {
                    "📷 Photo"
                } else {
                    raw
                }
                val created = parseIsoToInstant(lastMsg["created_at"]?.toString())
                if (created != null) lastTime = created
                lastSender = lastMsg["sender_name"]?.toString() ?: ""
            }

            val otherUser = json["other_user"] as? Map<*, *>
            val participantIds = mutableListOf<String>()
            json["created_by_id"]?.let { participantIds.add(it.toString()) }
            otherUser?.get("id")?.let {
                val otherId = it.toString()
                if (!participantIds.contains(otherId)) participantIds.add(otherId)
            }

            val rawTitle = json["display_name"]?.toString() ?: json["name"]?.toString() ?: "Chat"
            val effectiveTitle = if (rawTitle.trim().isNotEmpty()) rawTitle.trim() else "Chat"
            val compact = effectiveTitle.replace(Regex("[^A-Za-z0-9]"), "")
            val initials = if (compact.isEmpty()) "C"
            else compact.substring(0, compact.length.coerceIn(1, 2)).uppercase()

            val rType = json["room_type"]?.toString()
            val tId = json["task_id"]?.toString()

            return Conversation(
                id = json["uid"]?.toString() ?: json["id"]?.toString() ?: "",
                type = if (rType == "group") ConversationType.group else ConversationType.direct,
                title = effectiveTitle,
                participantIds = participantIds,
                avatarInitials = initials,
                avatarColorHex = "0xFF6366F1",
                lastMessageText = lastText,
                lastMessageTime = lastTime,
                lastMessageSenderId = lastSender,
                roomType = rType,
                taskId = tId,
            )
        }
    }
}
