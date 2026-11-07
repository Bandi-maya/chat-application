package com.example.chat

import com.example.chat.data.chatui.MentionParser
import com.example.chat.data.chatui.MessageFormatter
import com.example.chat.data.chatui.QuickReactionPolicy
import com.example.chat.data.chatui.ScheduledSendQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiFeaturesTest {

    @Test fun `mentions resolve against member handles`() {
        val m = MentionParser.parse("hi @bandi and @ghost", mapOf("bandi" to "u1"))
        assertEquals(1, m.size)
        assertEquals("u1", m[0].userId)
    }

    @Test fun `muted group notifies only on mention`() {
        assertTrue(MentionParser.shouldNotify(groupMuted = true, mentionsSelf = true))
        assertFalse(MentionParser.shouldNotify(groupMuted = true, mentionsSelf = false))
        assertTrue(MentionParser.shouldNotify(groupMuted = false, mentionsSelf = false))
    }

    @Test fun `formatter finds bold italic mono spoiler and quote`() {
        val spans = MessageFormatter.parse("*b* _i_ `m` ||s||\n> quoted")
        assertTrue(spans.any { it.style == MessageFormatter.Style.BOLD && it.content == "b" })
        assertTrue(spans.any { it.style == MessageFormatter.Style.ITALIC && it.content == "i" })
        assertTrue(spans.any { it.style == MessageFormatter.Style.MONO && it.content == "m" })
        assertTrue(spans.any { it.style == MessageFormatter.Style.SPOILER && it.content == "s" })
        assertTrue(spans.any { it.style == MessageFormatter.Style.QUOTE && it.content == "quoted" })
    }

    @Test fun `unclosed marker produces no span`() {
        assertTrue(MessageFormatter.parse("just *bold").isEmpty())
    }

    @Test fun `scheduled sends fire at time or when online`() {
        val q = ScheduledSendQueue()
        q.schedule(ScheduledSendQueue.Entry("a", "c", "enc", fireAtMillis = 1000, createdAtMillis = 0))
        q.schedule(ScheduledSendQueue.Entry("b", "c", "enc", fireAtMillis = null, createdAtMillis = 0))
        assertEquals(listOf("b"), q.due(nowMillis = 500, online = true).map { it.id })
        assertEquals(emptyList<String>(), q.due(nowMillis = 500, online = false).map { it.id })
        assertEquals(setOf("a", "b"), q.due(nowMillis = 2000, online = false).map { it.id }.toSet())
        q.markSent(setOf("a"))
        assertEquals(listOf("b"), q.pending().map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `past schedule rejected`() {
        ScheduledSendQueue().schedule(ScheduledSendQueue.Entry("x", "c", "enc", fireAtMillis = 5, createdAtMillis = 10))
    }

    @Test fun `quick reaction uses configured emoji on double tap`() {
        assertEquals("❤️", QuickReactionPolicy().reactionFor(doubleTap = true, configured = "❤️"))
        assertEquals("👍", QuickReactionPolicy().reactionFor(doubleTap = true))
        assertNull(QuickReactionPolicy().reactionFor(doubleTap = false))
    }
}
