package com.example.chat.ui.island

import android.content.Context
import com.example.chat.IslandOverlay
import java.lang.ref.WeakReference

/** Local-only bridge between the Android listener and the in-app island.
 * Notification contents are neither persisted nor sent over a network. */
class ChatyIslandNativeBridge(context: Context) : IslandNativeBridge {

    private val appContext = context.applicationContext
    private var notificationEventHandler: ((IslandNativeEvent) -> Unit)? = null

    private fun prefs() =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun setNotificationEventHandler(handler: (IslandNativeEvent) -> Unit) {
        notificationEventHandler = handler
        activeBridge = WeakReference(this)
    }

    /** Delivery is on Android main thread, matching controller/UI state. */
    fun dispatchNativeEvent(event: IslandNativeEvent) {
        notificationEventHandler?.invoke(event)
    }

    override fun clearAll() {
        IslandOverlay.clearAllNotifications()
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
        private var activeBridge: WeakReference<ChatyIslandNativeBridge>? = null
        fun deliverNativeEvent(event: IslandNativeEvent) {
            activeBridge?.get()?.dispatchNativeEvent(event)
        }

        const val PREFS = "chaty_dynamic_island"
        const val KEY_ENABLED = "enabled"
        const val KEY_HIDE_SYSTEM = "hide_system_notifications"
        const val KEY_HEADER = "show_island_header"
    }
}
