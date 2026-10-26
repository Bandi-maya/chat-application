package com.example.chat

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.island.AndroidDynamicIslandBridge
import com.example.chat.ui.island.ChatyIslandNativeBridge
import com.example.chat.ui.island.IslandNativeEvent
import com.example.chat.ui.settings.notifications.DynamicIslandException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Island native bridge contracts on a real Android runtime (Context, SharedPreferences, Settings).
 *  startActivity is captured by a ContextWrapper so no settings screen is opened. Original stored values
 *  are restored after each check. */
@RunWith(AndroidJUnit4::class)
class IslandBridgeInstrumentedTest {
    private class Recording(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) { started.add(intent) }
        override fun getApplicationContext(): Context = this
    }

    private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun hideSystemNotificationsAndHeaderRoundtrip() = runBlocking {
        val bridge = ChatyIslandNativeBridge(target)
        val h0 = bridge.isHideSystemNotificationsEnabled()
        val e0 = bridge.isHeaderEnabled()
        try {
            assertTrue(bridge.setHideSystemNotifications(true))
            assertTrue(bridge.isHideSystemNotificationsEnabled())
            assertFalse(bridge.setHideSystemNotifications(false))
            assertFalse(bridge.isHideSystemNotificationsEnabled())
            assertTrue(bridge.setHeaderEnabled(true))
            assertTrue(bridge.isHeaderEnabled())
        } finally {
            bridge.setHideSystemNotifications(h0)
            bridge.setHeaderEnabled(e0)
        }
    }

    @Test
    fun clearAllNeverThrowsAndHandlerReceivesEvents() = runBlocking {
        val bridge = ChatyIslandNativeBridge(target)
        bridge.clearAll()
        var received: IslandNativeEvent? = null
        bridge.setNotificationEventHandler { received = it }
        bridge.dispatchNativeEvent(IslandNativeEvent.NotificationPosted("k1", false, "t", "b"))
        assertTrue(received is IslandNativeEvent.NotificationPosted)
        assertEquals("k1", (received as IslandNativeEvent.NotificationPosted).key)
    }

    @Test
    fun permissionStateMatchesShippedManifest() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(target)
        val state = bridge.getPermissionState()
        // No SYSTEM_ALERT_WINDOW and no declared listener service in the manifest.
        assertEquals(false, state["overlay"])
        assertEquals(false, state["notificationAccess"])
    }

    @Test
    fun setEnabledRoundtripsThroughPermissionState() = runBlocking {
        val bridge = AndroidDynamicIslandBridge(target)
        val before = bridge.getPermissionState()["enabled"] == true
        try {
            bridge.setEnabled(true)
            assertEquals(true, bridge.getPermissionState()["enabled"])
            bridge.setEnabled(false)
            assertEquals(false, bridge.getPermissionState()["enabled"])
        } finally {
            bridge.setEnabled(before)
        }
    }

    @Test
    fun overlaySettingsLaunchesWhenPermissionMissing() = runBlocking {
        val ctx = Recording(target)
        AndroidDynamicIslandBridge(ctx).openOverlaySettings()
        assertEquals(1, ctx.started.size)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, ctx.started[0].action)
        assertEquals("package:" + target.packageName, ctx.started[0].data?.toString())
    }

    @Test
    fun notificationAccessSettingsIntent() = runBlocking {
        val ctx = Recording(target)
        AndroidDynamicIslandBridge(ctx).openNotificationAccessSettings()
        assertEquals(1, ctx.started.size)
        assertEquals("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", ctx.started[0].action)
    }

    @Test
    fun channelSettingsUsesAppNotificationSettingsIntent() = runBlocking {
        val ctx = Recording(target)
        AndroidDynamicIslandBridge(ctx).openNotificationChannelSettings()
        assertEquals(1, ctx.started.size)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, ctx.started[0].action)
        assertEquals(target.packageName, ctx.started[0].extras?.getString(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun showPreviewWithoutOverlayPermissionFollowsDartErrorPath() = runBlocking {
        try {
            AndroidDynamicIslandBridge(target).showPreview()
            fail("expected DynamicIslandException")
        } catch (e: DynamicIslandException) {
            assertEquals("Allow display over other apps first.", e.message)
        }
    }
}
