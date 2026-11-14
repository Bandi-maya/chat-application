package com.example.chat.ui.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SplitChatReorderTest {
    private val a = SplitPaneItem("room-a", "A", "account-one")
    private val b = SplitPaneItem("room-b", "B", "account-two")
    @Test fun reorderPreservesAccountIdentityAndFocus() {
        val result = SplitChatSessionState(listOf(a, b), "room-b").reordered("room-b", -1)
        assertEquals(listOf(b, a), result.paneItems)
        assertEquals("room-b", result.focusedConversationId)
        assertEquals("account-two", result.paneItems.first().accountNamespace)
    }
    @Test fun boundariesAndMissingRoomAreNoOps() {
        val before = SplitChatSessionState(listOf(a, b), "room-a")
        assertSame(before, before.reordered("room-a", -1))
        assertSame(before, before.reordered("missing", 1))
        assertSame(before, before.reordered("room-b", 0))
    }
    @Test fun accountAndRoomDelimiterCannotAliasState() {
        val first = SplitPaneItem("c", "A", "a:b")
        val second = SplitPaneItem("b:c", "B", "a")
        org.junit.Assert.assertNotEquals(first.stateKey(), second.stateKey())
    }

    @Test fun restoredPanesNeverMountUnderDifferentAccount() {
        val before = SplitChatSessionState(listOf(a, b), "room-b", freeLayout = true)
        val filtered = before.forAccount("account-one")
        assertEquals(listOf(a), filtered.paneItems)
        assertEquals("room-a", filtered.focusedConversationId)
        assertEquals(true, filtered.freeLayout)
        assertEquals(emptyList<SplitPaneItem>(), before.forAccount(null).paneItems)
        assertEquals(emptyList<SplitPaneItem>(), before.forAccount("unknown").paneItems)
    }
    @Test fun legacyUnscopedPaneIsNotTrustedAfterRestore() {
        val legacy = SplitChatSessionState(listOf(SplitPaneItem("room-a", "Old")))
        assertEquals(emptyList<SplitPaneItem>(), legacy.forAccount("account-one").paneItems)
    }
}
