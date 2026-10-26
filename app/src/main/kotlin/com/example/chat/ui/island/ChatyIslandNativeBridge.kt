package com.example.chat.ui.island

import android.content.Context

/** Real Android implementation of the controller's [IslandNativeBridge] seam:
 *  the native side of Dart MethodChannel('chaty/dynamic_island').
 *
 *  Exact ports of the MainActivity.kt channel handler +
 *  DynamicIslandNotificationListener companion accessors
 *  (chaty-api android/.../MainActivity.kt:230-245,
 *  DynamicIslandNotificationListener.kt:302-336): all four config values are
 *  SharedPreferences("chaty_dynamic_island") reads/writes, returning the
 *  passed/stored value exactly like result.success(...).
 *
 *  Shipped-parity notes:
 *  - clearAll(): Dart forwards to IslandOverlay.clearAllNotifications(); the
 *    system-overlay island window (IslandOverlay/IslandView) is unported AND
 *    unreachable in shipped Dart (neither manifest declares
 *    SYSTEM_ALERT_WINDOW), so this is intentionally a no-op.
 *  - setNotificationEventHandler: the inbound methods
 *    onNativeNotificationPosted/Removed/onClearAll are only invoked by the
 *    Dart app's NotificationListenerService, which is NOT declared in the
 *    shipped Dart manifest, so no events ever arrive there either. The
 *    handler is stored for a future listener; nothing else is synthesized. */
class ChatyIslandNativeBridge(context: Context) : IslandNativeBridge {

    private val appContext = context.applicationContext
    private var notificationEventHandler: ((IslandNativeEvent) -> Unit)? = null

    private fun prefs() =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun setNotificationEventHandler(handler: (IslandNativeEvent) -> Unit) {
        notificationEventHandler = handler
    }

    /** Test/native-side entry: delivers an inbound channel event. No shipped
     *  caller exists (see class doc); kept for the future listener binding. */
    fun dispatchNativeEvent(event: IslandNativeEvent) {
        notificationEventHandler?.invoke(event)
    }

    override fun clearAll() {
        // IslandOverlay.clearAllNotifications(): unported system overlay, no-op.
    }

    override suspend fun setHideSystemNotifications(hide: Boolean): Boolean {
        prefs().edit().putBoolean(KEY_HIDE_SYSTEM, hide).apply()
        return hide
    }

    override suspend fun isHideSystemNotificationsEnabled(): Boolean =
        prefs().getBoolean(KEY_HIDE_SYSTEM, false)

    override suspend fun setHeaderEnabled(enabled: Boolean): Boolean {
        prefs().edit().putBoolean(KEY_HEADER, enabled).apply()
        return enabled
    }

    override suspend fun isHeaderEnabled(): Boolean =
        prefs().getBoolean(KEY_HEADER, false)

    companion object {
        const val PREFS = "chaty_dynamic_island"
        const val KEY_ENABLED = "enabled"
        const val KEY_HIDE_SYSTEM = "hide_system_notifications"
        const val KEY_HEADER = "show_island_header"
    }
}
