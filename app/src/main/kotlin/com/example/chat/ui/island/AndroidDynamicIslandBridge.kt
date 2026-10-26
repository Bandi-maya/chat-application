package com.example.chat.ui.island

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.chat.IslandOverlay
import com.example.chat.DynamicIslandNotificationListener
import com.example.chat.ui.settings.notifications.DynamicIslandBridge
import com.example.chat.ui.settings.notifications.DynamicIslandException

/** Android permission entry and native overlay control. Permission is read
 * from Android; declaring the service does not grant notification access. */
class AndroidDynamicIslandBridge(context: Context) : DynamicIslandBridge {

    private val appContext = context.applicationContext

    private fun prefs() =
        appContext.getSharedPreferences(ChatyIslandPrefs.PREFS, Context.MODE_PRIVATE)

    /** MainActivity.kt:189-195. */
    override suspend fun getPermissionState(): Map<String, Boolean> = mapOf(
        "overlay" to (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(appContext)),
        "notificationAccess" to hasNotificationAccess(),
        "enabled" to prefs().getBoolean(ChatyIslandPrefs.KEY_ENABLED, false),
        "hideSystemNotifications" to prefs().getBoolean(ChatyIslandPrefs.KEY_HIDE_SYSTEM, false),
        "headerEnabled" to prefs().getBoolean(ChatyIslandPrefs.KEY_HEADER, false),
    )

    /** Check the exact declared component, including applicationId variants. */
    private fun hasNotificationAccess(): Boolean {
        val expected = ComponentName(appContext, DynamicIslandNotificationListener::class.java)
        val enabled = Settings.Secure.getString(
            appContext.contentResolver, "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** Disable immediately removes the window and clears cached content. */
    override suspend fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(ChatyIslandPrefs.KEY_ENABLED, enabled).apply()
        if (!enabled) {
            IslandOverlay.remove()
            ChatyIslandNativeBridge.deliverNativeEvent(IslandNativeEvent.ClearAll)
        } else if (hasNotificationAccess()) {
            android.service.notification.NotificationListenerService.requestRebind(
                ComponentName(appContext, DynamicIslandNotificationListener::class.java),
            )
        }
    }

    /** MainActivity.kt:210-218 (error "notification_access_settings_failed"). */
    override suspend fun openNotificationAccessSettings() {
        try {
            val component = ComponentName(appContext, DynamicIslandNotificationListener::class.java)
            val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                }
            } else null
            if (detail != null && detail.resolveActivity(appContext.packageManager) != null) {
                try {
                    appContext.startActivity(detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: android.content.ActivityNotFoundException) {
                    // Some OEMs advertise the detail activity but omit it.
                }
            }
            appContext.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (error: Exception) {
            throw DynamicIslandException(error.message)
        }
    }

    /** MainActivity.kt:196-209 (error "overlay_settings_failed"): only opens
     *  when the permission is actually missing. */
    override suspend fun openOverlaySettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(appContext)
            ) {
                appContext.startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${appContext.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (error: Exception) {
            throw DynamicIslandException(error.message)
        }
    }

    /** Preview uses the same native overlay as incoming notifications. */
    override suspend fun showPreview() {
        val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(appContext)
        if (!hasOverlay) {
            throw DynamicIslandException("Allow display over other apps first.")
        }
        if (!prefs().getBoolean(ChatyIslandPrefs.KEY_ENABLED, false)) {
            throw DynamicIslandException("Enable Dynamic Island first.")
        }
        IslandOverlay.preview(appContext)
    }

    /** MainActivity.kt:262-284 (error "channel_settings_failed"). */
    override suspend fun openNotificationChannelSettings() {
        try {
            val pkg = appContext.packageName
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                }
            }
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            throw DynamicIslandException(error.message)
        }
    }

}

/** Shared name/keys so both bridges hit the same store
 *  (DynamicIslandNotificationListener.kt:302-336). */
private object ChatyIslandPrefs {
    const val PREFS = "chaty_dynamic_island"
    const val KEY_ENABLED = "enabled"
    const val KEY_HIDE_SYSTEM = "hide_system_notifications"
    const val KEY_HEADER = "show_island_header"
}
