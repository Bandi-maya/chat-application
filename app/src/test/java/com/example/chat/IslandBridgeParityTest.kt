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
            EventNotificationIcon.notifications to "Rounded.Notifications",
            EventNotificationIcon.image to "Rounded.Image",
            EventNotificationIcon.videocam to "Rounded.Videocam",
            EventNotificationIcon.mic to "Rounded.Mic",
            EventNotificationIcon.attachFile to "Rounded.AttachFile",
            EventNotificationIcon.locationOn to "Rounded.LocationOn",
            EventNotificationIcon.person to "Rounded.Person",
            EventNotificationIcon.checkCircleOutline to "Rounded.CheckCircleOutline",
            EventNotificationIcon.chatBubble to "Rounded.ChatBubble",
            EventNotificationIcon.onlinePrediction to "Rounded.OnlinePrediction",
            EventNotificationIcon.keyboardAlt to "Outlined.KeyboardAlt",
            EventNotificationIcon.micNone to "Rounded.MicNone",
            EventNotificationIcon.undo to "Rounded.Undo",
            EventNotificationIcon.personOutline to "Rounded.PersonOutline",
            EventNotificationIcon.autoAwesome to "Rounded.AutoAwesome",
            EventNotificationIcon.historyToggleOff to "Rounded.HistoryToggleOff",
        )
        assertEquals(EventNotificationIcon.values().size, expected.size)
        for ((icon, name) in expected) {
            // Compose material icon names carry the style prefix (e.g. "Rounded.Notifications"),
            // matching the Dart IconData styles in the mapper docs (keyboard_alt stays Outlined).
            assertEquals(name, IslandIconMapper.map(icon).name)
        }
    }
}
