package com.example.chat

import com.example.chat.data.services.ChatyNotificationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatyNotificationServiceTest {
    @Test
    fun activityFeedIsNewestFirstBoundedAndClearable() {
        val service = ChatyNotificationService()
        repeat(105) { index ->
            service.triggerEventNotification("Event $index", "Body $index")
        }

        val entries = service.notifications
        assertEquals(100, entries.size)
        assertEquals("Event 104", entries.first().title)
        assertEquals("Event 5", entries.last().title)
        assertEquals(100, entries.map { it.id }.distinct().size)

        service.clearAll()
        assertTrue(service.notifications.isEmpty())
        assertEquals(null, service.latest)
    }

    @Test
    fun listenersReceiveChangesAndCanBeRemoved() {
        val service = ChatyNotificationService()
        var changes = 0
        val listener: () -> Unit = { changes++ }
        service.addListener(listener)
        service.triggerEventNotification("One", "First")
        assertEquals(1, changes)

        service.removeListener(listener)
        service.triggerEventNotification("Two", "Second")
        assertEquals(1, changes)
    }

    @Test
    fun eachEventGetsAUniqueIdentity() {
        val service = ChatyNotificationService()
        service.triggerEventNotification("One", "Same body")
        service.triggerEventNotification("One", "Same body")
        val ids = service.notifications.map { it.id }
        assertEquals(2, ids.distinct().size)
        assertNotEquals(ids[0], ids[1])
    }
}
