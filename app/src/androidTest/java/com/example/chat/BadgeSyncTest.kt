package com.example.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.data.services.NotificationChannelManager
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic count-policy and notify checks for the badge manager (Dart syncUnreadBadgeCount):
 *  sum of unread over non-muted conversations, zero when disableBadgeCounter or global notifications are off,
 *  listeners fire on sync, and the persisted record equals the count. Synthetic data only. */
@RunWith(AndroidJUnit4::class)
class BadgeSyncTest {
    private fun convo(id: String, unread: Int, muted: Boolean) = Conversation(
        id = id, type = ConversationType.direct, title = id, participantIds = listOf("a", "b"),
        lastMessageText = "", lastMessageTime = Instant.EPOCH, lastMessageSenderId = "a",
        unreadCount = unread, isMuted = muted,
    )

    @Test
    fun countPolicyAndNotify() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = ServiceLocator.preferencesController
        val savedUniversal = prefs.universal
        val savedNotification = prefs.notification
        try {
            prefs.updateUniversal(prefs.universal.copy(disableBadgeCounter = false))
            prefs.updateNotification(prefs.notification.copy(enableGlobalNotifications = true))
            var list = listOf(convo("c1", 3, false), convo("c2", 5, true), convo("c3", 2, false))
            val m = NotificationChannelManager(ctx, prefs) { list }
            var notified = 0
            m.addListener { notified++ }
            m.syncUnreadBadgeCount()
            assertEquals("muted excluded", 5, m.badgeCount)
            assertEquals("listener fired", 1, notified)
            val stored = ctx.getSharedPreferences("FlutterSharedPreferences", android.content.Context.MODE_PRIVATE)
                .getInt("flutter.chaty_unread_badge_count", -1)
            assertEquals("persisted record", 5, stored)
            list = listOf(convo("c1", 7, false))
            m.syncUnreadBadgeCount()
            assertEquals("recount after change", 7, m.badgeCount)
            prefs.updateUniversal(prefs.universal.copy(disableBadgeCounter = true))
            assertEquals("counter disabled -> 0 (preferences listener sync)", 0, m.badgeCount)
            prefs.updateUniversal(prefs.universal.copy(disableBadgeCounter = false))
            prefs.updateNotification(prefs.notification.copy(enableGlobalNotifications = false))
            m.syncUnreadBadgeCount()
            assertEquals("global notifications off -> 0", 0, m.badgeCount)
        } finally {
            prefs.updateUniversal(savedUniversal)
            prefs.updateNotification(savedNotification)
            ctx.getSharedPreferences("FlutterSharedPreferences", android.content.Context.MODE_PRIVATE)
                .edit().remove("flutter.chaty_unread_badge_count").apply()
        }
    }
}
