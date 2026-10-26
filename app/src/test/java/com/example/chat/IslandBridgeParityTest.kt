package com.example.chat

import com.example.chat.data.realtime.EventNotificationIcon
import com.example.chat.ui.island.IslandIconMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM part of the island parity checks (icon mapping). The Context/SharedPreferences-bound
 *  bridge checks live in androidTest/IslandBridgeInstrumentedTest (they need a real Android runtime;
 *  the earlier unit-test form relied on android stubs that do not exist in this repo and never compiled). */
class IslandBridgeParityTest {

    // ------------------------------------------------------- IslandIconMapper

    @Test
    fun `all sixteen enum icons map to the exact Dart Material icons`() {
        val expected = mapOf(
            EventNotificationIcon.notifications to "Notifications",
            EventNotificationIcon.image to "Image",
            EventNotificationIcon.videocam to "Videocam",
            EventNotificationIcon.mic to "Mic",
            EventNotificationIcon.attachFile to "AttachFile",
            EventNotificationIcon.locationOn to "LocationOn",
            EventNotificationIcon.person to "Person",
            EventNotificationIcon.checkCircleOutline to "CheckCircleOutline",
            EventNotificationIcon.chatBubble to "ChatBubble",
            EventNotificationIcon.onlinePrediction to "OnlinePrediction",
            EventNotificationIcon.keyboardAlt to "KeyboardAlt",
            EventNotificationIcon.micNone to "MicNone",
            EventNotificationIcon.undo to "Undo",
            EventNotificationIcon.personOutline to "PersonOutline",
            EventNotificationIcon.autoAwesome to "AutoAwesome",
            EventNotificationIcon.historyToggleOff to "HistoryToggleOff",
        )
        assertEquals(EventNotificationIcon.values().size, expected.size)
        for ((icon, name) in expected) {
            assertEquals(name, IslandIconMapper.map(icon).name)
        }
    }
}
