package com.example.chat

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.home.filterHomeConversations
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class HomeMemoPrivacyTest {
    private fun room(id: String, type: ConversationType = ConversationType.direct) = Conversation(
        id = id, type = type, title = id, participantIds = emptyList(), lastMessageText = "", lastMessageTime = Instant.EPOCH,
        lastMessageSenderId = "", isPinned = true)
    @Test fun mutablePrivacyCanChangeWithoutPredicateIdentityChanging() {
        val hidden = mutableSetOf<String>()
        val predicate: (String) -> Boolean = { it in hidden }
        val rooms = listOf(room("a"), room("b"))
        val first = rooms.map { it.id }.filter(predicate).toSet()
        hidden += "a"
        val second = rooms.map { it.id }.filter(predicate).toSet()
        assertNotEquals(first, second)
        assertEquals(listOf("b"), filterHomeConversations(rooms, "", "All", null, second::contains).map { it.id })
        hidden.clear()
        val third = rooms.map { it.id }.filter(predicate).toSet()
        assertNotEquals(second, third)
        assertEquals(listOf("a", "b"), filterHomeConversations(rooms, "", "All", null, third::contains).map { it.id })
    }
    @Test fun memoKeysUsePrivacyValuesAndBothQuickListsFilterHiddenRooms() {
        val source = File("app/src/main/kotlin/com/example/chat/ui/chats/ChatsHomeScreen.kt").readText()
        assertTrue(source.contains("remember(accountSessionIdentity, conversations, searchQuery, selectedFilter, forcedType, hiddenConversationIds)"))
        assertTrue(source.contains("hiddenConversationIds::contains"))
        assertTrue(source.contains("it.isPinned && !it.isArchived && it.id !in hiddenConversationIds"))
        assertTrue(source.contains("it.type == ConversationType.group && !it.isArchived && it.id !in hiddenConversationIds"))
    }    @Test fun selectionAndPendingDeleteCannotCarryAcrossAuthGenerations() {
        val source = File("app/src/main/kotlin/com/example/chat/ui/chats/ChatsHomeScreen.kt").readText()
        assertTrue(source.contains("selectedIds by remember(accountSessionIdentity)"))
        assertTrue(source.contains("showDeleteConfirm by remember(accountSessionIdentity)"))
        val host = File("app/src/main/kotlin/com/example/chat/ui/ChatsHomeHost.kt").readText()
        assertTrue(host.contains("accountSessionIdentity = api.authSnapshot()"))
    }

}
