package com.example.chat

import com.example.chat.core.messages.ReactionUpdatePolicy
import com.example.chat.domain.models.MessageReaction
import org.junit.Assert.*
import org.junit.Test

class ReactionUpdatePolicyTest {
    @Test fun oneChoicePerUserAndToggleRemovesSame() {
        val original = listOf(MessageReaction("a", listOf("me", "peer")), MessageReaction("b", listOf("me")))
        val next = ReactionUpdatePolicy.toggle(original, "me", "c")
        assertEquals(1, next.count { "me" in it.userIds })
        assertEquals(listOf("peer"), next.first { it.emoji == "a" }.userIds)
        assertTrue(ReactionUpdatePolicy.toggle(next, "me", "c").none { "me" in it.userIds })
    }
}
