package com.example.chat.data.media

import com.example.chat.data.local.AttachmentRef
import com.example.chat.domain.models.ChatMessage

/** Pure helpers that map message lists to cache identities, so deletes can invalidate local copies. */
object AttachmentRefs {
    fun refOf(conversationId: String, m: ChatMessage): AttachmentRef? {
        val a = m.attachment ?: return null
        if (conversationId.isBlank() || m.id.isBlank() || a.id.isBlank()) return null
        return AttachmentRef(conversationId, m.id, a.id)
    }

    fun refsOf(conversationId: String, messages: Collection<ChatMessage>): List<AttachmentRef> =
        messages.mapNotNull { refOf(conversationId, it) }

    /** Attachments held in [before] whose message is absent from [after], or present but deleted (for me/everyone). */
    fun refsGone(conversationId: String, before: Collection<ChatMessage>?, after: Collection<ChatMessage>): List<AttachmentRef> {
        if (before.isNullOrEmpty()) return emptyList()
        val live = after.filter { !it.isDeletedForEveryone && !it.isDeletedForMe }.map { it.id }.toHashSet()
        return before.filter { it.id !in live }.mapNotNull { refOf(conversationId, it) }
    }
}
