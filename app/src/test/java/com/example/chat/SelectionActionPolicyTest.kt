package com.example.chat
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.SelectionAction
import com.example.chat.ui.chats.detail.SelectionActionPolicy
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
class SelectionActionPolicyTest {
    private fun msg(id: String, type: MessageType = MessageType.text) = ChatMessage(
        id = id, conversationId = "synthetic", senderId = "synthetic", type = type,
        text = "synthetic text", createdAt = Instant.EPOCH)
    @Test fun emptySelectionHasNoActions() { assertTrue(SelectionActionPolicy.available(emptyList()).isEmpty()) }
    @Test fun textBatchHasOnlyImplementedActions() {
        assertEquals(setOf(SelectionAction.forward, SelectionAction.copy, SelectionAction.delete, SelectionAction.star),
            SelectionActionPolicy.available(listOf(msg("1"), msg("2"))))
    }
    @Test fun mixedDocumentBatchCannotCopyTextOnlySubset() {
        assertEquals(setOf(SelectionAction.forward, SelectionAction.delete, SelectionAction.star),
            SelectionActionPolicy.available(listOf(msg("1"), msg("2", MessageType.document))))
    }
    @Test fun restrictedContentLimitsEntireSelectionToDelete() {
        val restricted = listOf(msg("s", MessageType.system), msg("v").copy(metadata = mapOf("view_once" to true)),
            msg("d").copy(isDeletedForEveryone = true), msg("m").copy(isDeletedForMe = true))
        for (message in restricted) assertEquals(setOf(SelectionAction.delete),
            SelectionActionPolicy.available(listOf(msg("1"), message)))
    }
}
