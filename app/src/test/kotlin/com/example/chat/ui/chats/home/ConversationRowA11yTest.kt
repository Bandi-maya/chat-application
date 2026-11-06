package com.example.chat.ui.chats.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRowA11yTest {
    @Test fun plainRowAnnouncesNameAndSubtitleOnly() {
        assertEquals(
            "Priya, On my way",
            conversationRowA11ySummary("Priya", "On my way", 0, false, false, false, false),
        )
    }
    @Test fun emptySubtitleIsOmitted() {
        assertEquals(
            "Priya",
            conversationRowA11ySummary("Priya", "", 0, false, false, false, false),
        )
    }
    @Test fun unreadPinnedMutedLockedSelectedStatesAreSpoken() {
        assertEquals(
            "Priya, typing…, 3 unread messages, pinned, muted, locked, selected",
            conversationRowA11ySummary("Priya", "typing…", 3, true, true, true, true),
        )
    }
    @Test fun draftSubtitleArrivesPrefixedFromTheCallSite() {
        // The call site builds "Draft: ..." from the same branch that renders
        // the visible draft subtitle, so the spoken text matches the screen.
        assertEquals(
            "Priya, Draft: see you at 7",
            conversationRowA11ySummary("Priya", "Draft: see you at 7", 0, false, false, false, false),
        )
    }
    @Test fun zeroUnreadAddsNothing() {
        val summary = conversationRowA11ySummary("Priya", "ok", 0, false, false, false, false)
        assertFalse(summary.contains("unread"))
    }
    @Test fun oneUnreadUsesSingularCopy() {
        val summary = conversationRowA11ySummary("Priya", "ok", 1, false, false, false, false)
        assertTrue(summary.contains("1 unread message"))
        assertFalse(summary.contains("1 unread messages"))
    }
}
