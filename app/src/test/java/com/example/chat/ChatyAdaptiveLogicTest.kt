package com.example.chat

import com.example.chat.ui.kit.ChatyAdaptiveLogic
import com.example.chat.ui.kit.ChatyWidthBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Responsive plan R2/R3/R7: buckets, content caps, bubble cap, panes. */
class ChatyAdaptiveLogicTest {

    @Test
    fun `width buckets match the Material breakpoints`() {
        assertEquals(ChatyWidthBucket.Compact, ChatyAdaptiveLogic.bucketOf(320))
        assertEquals(ChatyWidthBucket.Compact, ChatyAdaptiveLogic.bucketOf(599))
        assertEquals(ChatyWidthBucket.Medium, ChatyAdaptiveLogic.bucketOf(600))
        assertEquals(ChatyWidthBucket.Medium, ChatyAdaptiveLogic.bucketOf(839))
        assertEquals(ChatyWidthBucket.Expanded, ChatyAdaptiveLogic.bucketOf(840))
        assertEquals(ChatyWidthBucket.Expanded, ChatyAdaptiveLogic.bucketOf(1600))
    }

    @Test
    fun `auth column caps at 460 and never exceeds the window`() {
        assertEquals(320, ChatyAdaptiveLogic.authColumnCap(320))
        assertEquals(460, ChatyAdaptiveLogic.authColumnCap(840))
        assertEquals(460, ChatyAdaptiveLogic.authColumnCap(2000))
    }

    @Test
    fun `list column caps at 720`() {
        assertEquals(411, ChatyAdaptiveLogic.listColumnCap(411))
        assertEquals(720, ChatyAdaptiveLogic.listColumnCap(840))
        assertEquals(720, ChatyAdaptiveLogic.listColumnCap(3000))
    }

    @Test
    fun `bubbles take 78 percent of the pane hard-capped at 520`() {
        assertEquals(249, ChatyAdaptiveLogic.bubbleCap(320))
        assertEquals(320, ChatyAdaptiveLogic.bubbleCap(411))
        assertEquals(520, ChatyAdaptiveLogic.bubbleCap(840))
        assertEquals(520, ChatyAdaptiveLogic.bubbleCap(2000))
    }

    @Test
    fun `list-detail layout only at expanded width`() {
        assertFalse(ChatyAdaptiveLogic.isListDetail(839))
        assertTrue(ChatyAdaptiveLogic.isListDetail(840))
        assertEquals(360, ChatyAdaptiveLogic.homePaneWidth(320))
        assertEquals(400, ChatyAdaptiveLogic.homePaneWidth(840))
    }
}
