package com.example.chat

import com.example.chat.data.chatlist.MessageDraftStore
import com.example.chat.data.chatlist.MultiAccountPolicy
import com.example.chat.data.chatlist.PinnedConversationsPolicy
import com.example.chat.data.chatlist.SwipeAction
import com.example.chat.data.chatlist.SwipeActionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListPoliciesTest {

    @Test fun `pins float to top with cap 3`() {
        val p = PinnedConversationsPolicy()
        assertTrue(p.pin("a")); assertTrue(p.pin("b")); assertTrue(p.pin("c"))
        assertFalse(p.pin("d"))
        assertEquals(listOf("c", "b", "a", "d", "e"), p.ordered(listOf("a", "b", "c", "d", "e")))
        p.unpin("b")
        assertEquals(listOf("c", "a", "b", "d"), p.ordered(listOf("a", "b", "c", "d")))
    }

    @Test fun `drafts save blank clears and send clears`() {
        val d = MessageDraftStore()
        d.save("c1", "hello")
        assertEquals("hello", d.draft("c1"))
        d.save("c1", "   ")
        assertNull(d.draft("c1"))
        d.save("c2", "later")
        d.clear("c2")
        assertTrue(d.conversationsWithDrafts().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `swipe config needs one action`() {
        SwipeActionConfig(SwipeAction.NONE, SwipeAction.NONE)
    }

    @Test fun `multi account switch keeps sessions and namespaces differ`() {
        val m = MultiAccountPolicy()
        assertTrue(m.add("u1")); assertTrue(m.add("u2"))
        assertEquals("u2", m.active())
        assertTrue(m.switchTo("u1"))
        assertEquals("u1", m.active())
        assertTrue(m.storageNamespace("u1") != m.storageNamespace("u2"))
    }

    @Test fun `multi account cap`() {
        val m = MultiAccountPolicy(maxAccounts = 2)
        assertTrue(m.add("u1")); assertTrue(m.add("u2")); assertFalse(m.add("u3"))
    }
}
