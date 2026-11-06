package com.example.chat

import com.example.chat.data.demo.DemoMode
import com.example.chat.data.demo.DemoSampleData
import com.example.chat.domain.models.ConversationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline demo (NOT in Flutter) must stay synthetic and self-consistent:
 * demo ids only, the demo user in every conversation, and messages that match
 * their conversation's last-message fields.
 */
class DemoSampleDataTest {

    @Test
    fun demoIdsAreSynthetic() {
        assertTrue(DemoMode.isDemoUser(DemoMode.USER_ID))
        assertTrue(!DemoMode.isDemoUser("real-user-id"))
        assertTrue(!DemoMode.isDemoUser(null))
        assertTrue(DemoMode.USER_ID.startsWith(DemoMode.ID_PREFIX))
        assertTrue(DemoMode.TOKEN.startsWith(DemoMode.ID_PREFIX))
    }

    @Test
    fun sampleContentUsesOnlyDemoIds() {
        val ids = mutableSetOf(DemoMode.USER_ID)
        DemoSampleData.demoUsers().forEach { ids += it.id }
        assertTrue(ids.all { DemoMode.isDemoId(it) })
        DemoSampleData.demoConversations().forEach { conv ->
            assertTrue(DemoMode.isDemoId(conv.id))
            assertTrue(conv.participantIds.contains(DemoMode.USER_ID))
            assertTrue(conv.participantIds.all { it in ids })
            assertTrue(conv.title.contains("sample", ignoreCase = true))
        }
    }

    @Test
    fun sampleMessagesMatchConversations() {
        val conversations = DemoSampleData.demoConversations().associateBy { it.id }
        val messages = DemoSampleData.demoMessages()
        assertEquals(conversations.keys, messages.keys)
        messages.forEach { (roomId, list) ->
            val conv = conversations.getValue(roomId)
            assertTrue(list.isNotEmpty())
            assertTrue(list.all { it.conversationId == roomId })
            assertTrue(list.all { DemoMode.isDemoId(it.id) })
            assertTrue(list.all { it.senderId in conv.participantIds })
            val sorted = list.sortedBy { it.createdAt }
            assertEquals(sorted, list)
            val last = list.last()
            assertEquals(last.text, conv.lastMessageText)
            assertEquals(last.createdAt, conv.lastMessageTime)
            assertEquals(last.senderId, conv.lastMessageSenderId)
        }
    }

    @Test
    fun sampleHasDirectAndGroup() {
        val types = DemoSampleData.demoConversations().map { it.type }.toSet()
        assertTrue(types.contains(ConversationType.direct))
        assertTrue(types.contains(ConversationType.group))
    }
}
