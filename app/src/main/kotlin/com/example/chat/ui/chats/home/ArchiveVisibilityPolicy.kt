package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation

/** Archive is an ordinary list, not the authenticated hidden-chat vault.
 * Recheck visibility when handling a bulk action too: a selection can outlive
 * an archive update, removal or privacy change while a dialog is open. */
object ArchiveVisibilityPolicy {
    fun visible(
        conversations: List<Conversation>,
        isHidden: (String) -> Boolean,
    ): List<Conversation> = conversations.filter { it.isArchived && !isHidden(it.id) }

    fun actionableIds(
        selectedIds: Set<String>,
        conversations: List<Conversation>,
        isHidden: (String) -> Boolean,
    ): List<String> = visible(conversations, isHidden)
        .asSequence()
        .map { it.id }
        .filter { it in selectedIds }
        .distinct()
        .toList()
}
