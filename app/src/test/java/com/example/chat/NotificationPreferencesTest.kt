package com.example.chat

import com.example.chat.data.notifications.NotificationPreferences
import com.example.chat.data.notifications.NotificationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesTest {

    @Test fun `per-chat style overrides default`() {
        val p = NotificationPreferences(defaultStyle = NotificationStyle.FULL)
        p.setOverride("c1", NotificationPreferences.ChatOverride(style = NotificationStyle.NAME_ONLY))
        assertEquals(NotificationStyle.NAME_ONLY, p.styleFor("c1"))
        assertEquals(NotificationStyle.FULL, p.styleFor("c2"))
    }

    @Test fun `overnight quiet hours window`() {
        val q = NotificationPreferences.QuietHours(22 * 60, 7 * 60)
        assertTrue(q.contains(23 * 60))
        assertTrue(q.contains(6 * 60))
        assertFalse(q.contains(12 * 60))
    }

    @Test fun `calls break through quiet hours and mute, mentions break through mute`() {
        val p = NotificationPreferences()
        p.quietHours = NotificationPreferences.QuietHours(0, 24 * 60)   // always quiet
        p.setOverride("c1", NotificationPreferences.ChatOverride(muted = true))
        assertTrue(p.shouldAlert("c1", isCall = true, mentionsSelf = false, minuteOfDay = 12 * 60))
        assertFalse(p.shouldAlert("c1", isCall = false, mentionsSelf = false, minuteOfDay = 12 * 60))
        assertTrue(p.shouldAlert("c1", isCall = false, mentionsSelf = true, minuteOfDay = null))
    }

    @Test fun `badge excludes muted conversations`() {
        val p = NotificationPreferences()
        p.setOverride("muted", NotificationPreferences.ChatOverride(muted = true))
        assertEquals(3, p.badgeCount(mapOf("muted" to 5, "loud" to 3)))
    }
}
