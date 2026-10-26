package com.example.chat

import com.example.chat.ui.navigation.HomeHeaderTabsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeaderTabsStateTest {
    private val ids = listOf("chats", "updates", "calls")

    @Test fun republishingSameDataWritesNothingMore() {
        val s = HomeHeaderTabsState()
        s.publish(ids, 0, 56f) {}
        val after = s.writeCount
        repeat(20) { s.publish(ids.toList(), 0, 56f) {} } // fresh list + fresh lambda each "composition"
        assertEquals(after, s.writeCount)
        assertTrue(s.active)
    }

    @Test fun changedSelectionWritesOnlySelection() {
        val s = HomeHeaderTabsState()
        s.publish(ids, 0, 56f) {}
        val before = s.writeCount
        s.publish(ids, 2, 56f) {}
        assertEquals(before + 1, s.writeCount)
        assertEquals(2, s.selectedIndex)
    }

    @Test fun clearDeactivatesAndDropsCallback() {
        val s = HomeHeaderTabsState()
        var picked = -1
        s.publish(ids, 0, 56f) { picked = it }
        s.onSelect(1); assertEquals(1, picked)
        s.clear()
        assertFalse(s.active)
        s.onSelect(2); assertEquals(1, picked) // old shell closure released
        s.clear() // idempotent, no extra write
        val w = s.writeCount
        s.clear(); assertEquals(w, s.writeCount)
    }

    @Test fun latestCallbackIsUsedWithoutStateWrite() {
        val s = HomeHeaderTabsState()
        var a = 0; var b = 0
        s.publish(ids, 0, 56f) { a++ }
        val w = s.writeCount
        s.publish(ids, 0, 56f) { b++ }
        s.onSelect(0)
        assertEquals(w, s.writeCount); assertEquals(0, a); assertEquals(1, b)
    }
}
