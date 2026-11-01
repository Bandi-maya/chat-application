package com.example.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.notifications.ChatyNotifications
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatyNotificationsSignOutTest {
    @Test
    fun signOutClearsPendingRoomAndCallActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ChatyNotifications.requestOpenRoom("account-a-room")
        ChatyNotifications.requestCallAction(
            ChatyNotifications.PendingCallAction("account-a-call", answer = true),
        )

        ChatyNotifications.clearForSignOut(context)

        assertNull(ChatyNotifications.pendingRoomId.value)
        assertNull(ChatyNotifications.pendingCallAction.value)
    }
}
