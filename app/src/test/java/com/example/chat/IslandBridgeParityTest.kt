package com.example.chat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import com.example.chat.data.realtime.EventNotificationIcon
import com.example.chat.ui.island.AndroidDynamicIslandBridge
import com.example.chat.ui.island.ChatyIslandNativeBridge
import com.example.chat.ui.island.IslandIconMapper
import com.example.chat.ui.island.IslandNativeEvent
import com.example.chat.ui.settings.notifications.DynamicIslandException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Behavior tests for the island native bridges, hand-computed from the Dart
 *  native side (MainActivity.kt channel handler +
 *  DynamicIslandNotificationListener companion) and the Dart controller's
 *  catch-and-default channel semantics. */
class IslandBridgeParityTest {

    private val context = RecordingContext()

    @After
    fun reset() {
        SharedPreferences.resetAll()
        Settings.Secure.values.clear()
        Settings.canDrawOverlaysResult = false
    }

    // ------------------------------------------------- ChatyIslandNativeBridge

    @Test
    fun `hide-system-notifications defaults false then roundtrips`() = runBlocking {
        val bridge = ChatyIslandNativeBridge(context)
        assertFalse(bridge.isHideSystemNotificationsEnabled())
        assertTrue(bridge.setHideSystemNotifications(true))
        assertTrue(bridge.isHideSystemNotificationsEnabled())
        assertFalse(bridge.setHideSystemNotifications(false))
        assertFalse(bridge.isHideSystemNotificationsEnabled())
    }

    @Test
    fun `header-enabled defaults false then roundtrips`() = runBlocking {
        val bridge = ChatyIslandNativeBridge(context)
        assertFalse(bridge.isHeaderEnabled())
        assertTrue(bridge.setHeaderEnabled(true))
        assertTrue(bridge.isHeaderEnabled())
    }

    @Test
    fun `clearAll never throws and stored handler receives dispatched events`() = runBlocking {
        val bridge = ChatyIslandNativeBridge(context)
        bridge.clearAll() // IslandOverlay unported: shipped-parity no-op
        var received: IslandNativeEvent? = null
        bridge.setNotificationEventHandler { received = it }
        bridge.dispatchNativeEvent(IslandNativeEvent.NotificationPosted("k1", false, "t", "b"))
        assertTrue(received is IslandNativeEvent.NotificationPosted)
        assertEquals("k1", (received as IslandNativeEvent.NotificationPosted).key)
    }

    // ---------------------------------------------- AndroidDynamicIslandBridge

    @Test
    fun `permission state matches shipped manifest - overlay and access false`() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(context)
        val state = bridge.getPermissionState()
        // Stub SDK is 34 (>= M) and no SYSTEM_ALERT_WINDOW: overlay false.
        assertEquals(false, state["overlay"])
        // No declared listener service: access false.
        assertEquals(false, state["notificationAccess"])
        assertEquals(false, state["enabled"])
        assertEquals(false, state["hideSystemNotifications"])
        assertEquals(false, state["headerEnabled"])
    }

    @Test
    fun `setEnabled writes the same prefs key the Dart native side reads`() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(context)
        bridge.setEnabled(true)
        assertEquals(true, bridge.getPermissionState()["enabled"])
        bridge.setEnabled(false)
        assertEquals(false, bridge.getPermissionState()["enabled"])
    }

    @Test
    fun `notification access check ports the listener companion exactly`() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(context)
        assertEquals(false, bridge.getPermissionState()["notificationAccess"])
        // Colon-joined list without the expected component -> false.
        Settings.Secure.values["enabled_notification_listeners"] =
            "com.other.app/com.other.app.OtherListener"
        assertEquals(false, bridge.getPermissionState()["notificationAccess"])
        // Expected component present among colon-joined entries -> true.
        Settings.Secure.values["enabled_notification_listeners"] =
            "com.other.app/com.other.app.OtherListener:" +
            "com.example.chat/com.example.chat.DynamicIslandNotificationListener"
        assertEquals(true, bridge.getPermissionState()["notificationAccess"])
    }

    @Test
    fun `openOverlaySettings only launches when permission missing`() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(context)
        Settings.canDrawOverlaysResult = false
        bridge.openOverlaySettings()
        assertEquals(1, context.started.size)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, context.started[0].action)
        assertEquals("package:com.example.chat", context.started[0].data?.value)
        // Already granted: Dart does nothing and succeeds.
        context.started.clear()
        Settings.canDrawOverlaysResult = true
        bridge.openOverlaySettings()
        assertTrue(context.started.isEmpty())
    }

    @Test
    fun `openNotificationAccessSettings launches the listener settings intent`() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(context)
        bridge.openNotificationAccessSettings()
        assertEquals(1, context.started.size)
        assertEquals("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", context.started[0].action)
    }

    @Test
    fun `channel settings uses the app notification settings intent on modern SDK`() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(context)
        bridge.openNotificationChannelSettings()
        assertEquals(1, context.started.size)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, context.started[0].action)
        assertEquals("com.example.chat", context.started[0].extras[Settings.EXTRA_APP_PACKAGE])
    }

    @Test
    fun `showPreview without overlay permission follows the Dart error path`() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(context)
        try {
            bridge.showPreview()
            fail("expected DynamicIslandException")
        } catch (e: DynamicIslandException) {
            assertEquals("Allow display over other apps first.", e.message)
        }
    }

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

/** Captures startActivity so the intent contract is assertable. */
private class RecordingContext : Context() {
    val started = mutableListOf<Intent>()
    override fun startActivity(intent: Intent) { started.add(intent) }
}
