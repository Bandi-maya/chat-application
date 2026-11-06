package com.example.chat.core.messages

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import com.example.chat.data.local.AttachmentCachePolicy

/** Device-local pins only. No group-wide permission or server pin state is claimed. */
object LocalPinnedMessagePolicy {
    fun eligible(message: ChatMessage): Boolean = message.isPinned && message.type != MessageType.system &&
        !message.isDeletedForEveryone && !message.isDeletedForMe && !message.isRevokedBySender &&
        !AttachmentCachePolicy.viewOnceRestricted(message.metadata)
    fun latest(messages: List<ChatMessage>): ChatMessage? = messages.filter(::eligible)
        .maxWithOrNull(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
}
