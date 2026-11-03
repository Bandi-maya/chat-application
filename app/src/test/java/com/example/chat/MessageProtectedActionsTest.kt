package com.example.chat

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.SelectionAction
import com.example.chat.ui.chats.detail.SelectionActionPolicy
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MessageProtectedActionsTest {
    private val text = ChatMessage("m", "c", "u", text = "Hello", createdAt = Instant.EPOCH)
    @Test fun textAllowsCopyForwardAndStar() {
        assertTrue(SelectionActionPolicy.available(listOf(text)).containsAll(setOf(SelectionAction.copy, SelectionAction.forward, SelectionAction.star)))
    }
    @Test fun revokedCannotLeakThroughCopyForwardOrStar() { assertEquals(setOf(SelectionAction.delete), SelectionActionPolicy.available(listOf(text.copy(isRevokedBySender = true)))) }
    @Test fun viewOnceCannotLeakThroughCopyForwardOrStar() { assertEquals(setOf(SelectionAction.delete), SelectionActionPolicy.available(listOf(text.copy(metadata = mapOf("view_once" to true))))) }
    @Test fun deletedCannotLeakThroughCopyForwardOrStar() { assertEquals(setOf(SelectionAction.delete), SelectionActionPolicy.available(listOf(text.copy(isDeletedForEveryone = true)))) }
    @Test fun mixedSelectionUsesIntersection() { assertFalse(SelectionAction.copy in SelectionActionPolicy.available(listOf(text, text.copy(type = MessageType.image)))) }
}
