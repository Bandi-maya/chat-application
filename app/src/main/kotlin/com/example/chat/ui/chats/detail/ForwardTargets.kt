package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType

/** Exact port of the forward-target selection in chat_detail_screen.dart
 *  (_openForwardSheet, :2337-2400): the conversation store order is preserved for
 *  the limit take(), sections are built from the limited pool, and the contact
 *  sections honor the Home hide switches while Groups always stays visible. */
object ForwardTargets {
    const val SECTION_FREQUENT = "Frequently contacted"
    const val SECTION_RECENT = "Recent chats"
    const val SECTION_OTHER_CONTACTS = "Other contacts"
    const val SECTION_GROUPS = "Groups"

    /** preferencesController.universal.increaseForwardLimit ? 250 : 5. */
    const val BASE_LIMIT = 5
    const val EXTENDED_LIMIT = 250

    fun maxChats(increaseForwardLimit: Boolean): Int =
        if (increaseForwardLimit) EXTENDED_LIMIT else BASE_LIMIT

    fun build(
        conversations: List<Conversation>,
        currentConversationId: String,
        messageCount: (String) -> Int,
        increaseForwardLimit: Boolean,
        hideFrequentlyContacted: Boolean,
        hideRecentChats: Boolean,
        hideOtherContacts: Boolean,
    ): List<ForwardTargetItem> {
        val maxForwardChats = maxChats(increaseForwardLimit)
        val allTargets = conversations.filter { it.id != currentConversationId }
        val targets = allTargets.take(maxForwardChats)
        val recentOrdered = targets.sortedByDescending { it.lastMessageTime }
        val directByFrequency = targets
            .filter { it.type == ConversationType.direct }
            .sortedByDescending { messageCount(it.id) }
        val frequentTargets = directByFrequency.take(5)
        val frequentIds = frequentTargets.map { it.id }.toSet()
        val recentPool = recentOrdered.filter { it.id !in frequentIds }
        val recentTargets = recentPool.take(5)
        val recentIds = recentTargets.map { it.id }.toSet()
        val otherContactTargets = recentOrdered.filter {
            it.type == ConversationType.direct && it.id !in frequentIds && it.id !in recentIds
        }
        val otherGroupTargets = recentOrdered.filter {
            it.type == ConversationType.group && it.id !in recentIds
        }
        val items = mutableListOf<ForwardTargetItem>()
        fun addSection(title: String, sectionItems: List<Conversation>) {
            for (item in sectionItems) items.add(ForwardTargetItem(title, item))
        }
        if (!hideFrequentlyContacted) addSection(SECTION_FREQUENT, frequentTargets)
        if (!hideRecentChats) addSection(SECTION_RECENT, recentTargets)
        if (!hideOtherContacts) addSection(SECTION_OTHER_CONTACTS, otherContactTargets)
        // The Home forwarding switches target contact sections. Keep groups visible
        // so the user always has a viable destination when the contact sections hide.
        addSection(SECTION_GROUPS, otherGroupTargets)
        return items
    }
}

data class ForwardTargetItem(val section: String, val conversation: Conversation)

/** Exact port of the forward send payload in _openForwardSheet
 *  (chat_detail_screen.dart:2501-2508): a system message forwards as an empty
 *  text message; every other type keeps its text, type and attachment (the
 *  attachment travels by reference - no re-upload, like Dart); extraMetadata is
 *  exactly {'forwarded': labelAllowed} where labelAllowed =
 *  !(privacy.disableForwardedLabel || gbBool('yoDisableFwd')). */
object ForwardSend {
    data class Payload(
        val text: String,
        val type: MessageType,
        val attachment: MessageAttachment?,
        val extraMetadata: Map<String, Any?>,
    )

    fun labelAllowed(disableForwardedLabel: Boolean, yoDisableFwd: Boolean): Boolean =
        !(disableForwardedLabel || yoDisableFwd)

    fun payload(message: ChatMessage, labelAllowed: Boolean): Payload {
        val system = message.type == MessageType.system
        return Payload(
            text = if (system) "" else message.text,
            type = if (system) MessageType.text else message.type,
            attachment = message.attachment,
            extraMetadata = mapOf("forwarded" to labelAllowed),
        )
    }
}
