package com.example.chat

import com.example.chat.data.local.AttachmentRef
import com.example.chat.data.media.AttachmentRefs
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageAttachment
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class AttachmentRefsTest {
    private fun msg(id: String, att: String?, delMe: Boolean = false, delAll: Boolean = false) = ChatMessage(
        id = id, conversationId = "c1", senderId = "s", text = "", createdAt = Instant.EPOCH,
        attachment = att?.let { MessageAttachment(it, "image", "n", "1 KB") },
        isDeletedForEveryone = delAll, isDeletedForMe = delMe)

    @Test fun refOfRejectsMissingOrBlankIds() {
        assertNull(AttachmentRefs.refOf("c1", msg("m1", null)))
        assertNull(AttachmentRefs.refOf("c1", msg("m1", "")))
        assertNull(AttachmentRefs.refOf("c1", msg("", "a")))
        assertNull(AttachmentRefs.refOf("", msg("m1", "a")))
        assertEquals(AttachmentRef("c1", "m1", "a"), AttachmentRefs.refOf("c1", msg("m1", "a")))
    }

    @Test fun goneIsAbsentOrDeletedMessagesOnly() {
        val before = listOf(msg("m1", "a1"), msg("m2", "a2"), msg("m3", "a3"), msg("m4", "a4"), msg("m5", null))
        val after = listOf(msg("m1", "a1"), msg("m3", "a3", delMe = true), msg("m4", "a4", delAll = true), msg("m5", null))
        assertEquals(setOf(AttachmentRef("c1", "m2", "a2"), AttachmentRef("c1", "m3", "a3"), AttachmentRef("c1", "m4", "a4")),
            AttachmentRefs.refsGone("c1", before, after).toSet())
        assertTrue(AttachmentRefs.refsGone("c1", null, after).isEmpty())
        assertTrue(AttachmentRefs.refsGone("c1", emptyList(), after).isEmpty())
        assertTrue(AttachmentRefs.refsGone("c1", before, before).isEmpty())
    }

    @Test fun clearAndConversationDeleteListEveryAttachment() {
        assertEquals(2, AttachmentRefs.refsOf("c1", listOf(msg("m1", "a1"), msg("m2", null), msg("m3", "a3"))).size)
    }
}
