package com.example.chat.ui.island

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.chat.ui.settings.notifications.DynamicIslandBridge
import com.example.chat.ui.settings.notifications.DynamicIslandException

/** Real Android implementation of the settings-page [DynamicIslandBridge]:
 *  the native side of Dart MethodChannel('chaty/dynamic_island') as invoked by
 *  notification_settings_page.dart. Exact port of the MainActivity.kt handler
 *  (chaty-api android/.../MainActivity.kt:185-290) and the
 *  DynamicIslandNotificationListener companion accessors (:302-352).
 *
 *  Shipped-parity: neither the Dart nor the Kotlin manifest declares a
 *  NotificationListenerService or SYSTEM_ALERT_WINDOW, so
 *  notificationAccess/overlay read false exactly as in the shipped Dart app,
 *  and showPreview follows the Dart overlay_permission_required error path.
 *  The settings intents are real and work. */
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

    /** DynamicIslandNotificationListener.hasNotificationAccess (:338-346).
     *  The checked component matches the Dart listener's class name; it is
     *  undeclared in both manifests, so this is false in shipped parity. */
    private fun hasNotificationAccess(): Boolean {
        val expected = ComponentName(appContext.packageName, LISTENER_CLASS)
        val enabled = Settings.Secure.getString(
            appContext.contentResolver, "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** MainActivity.kt:219-228: prefs write; IslandOverlay.remove() unported. */
    override suspend fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(ChatyIslandPrefs.KEY_ENABLED, enabled).apply()
        // if (!enabled) IslandOverlay.remove(): system overlay island unported.
    }

    /** MainActivity.kt:210-218 (error "notification_access_settings_failed"). */
    override suspend fun openNotificationAccessSettings() {
        try {
            appContext.startActivity(
                Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    /** MainActivity.kt:246-254: requires the overlay permission, then shows
     *  IslandOverlay.preview. The overlay window is unported, so the granted
     *  branch (unreachable with the shipped manifest, which declares no
     *  SYSTEM_ALERT_WINDOW) reports the precise gap instead of pretending. */
    override suspend fun showPreview() {
        val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(appContext)
        if (!hasOverlay) {
            throw DynamicIslandException("Allow display over other apps first.")
        }
        throw DynamicIslandException(
            "System island preview unavailable: IslandOverlay window unported."
        )
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

    companion object {
        /** Dart: "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
         *  (Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS exists only API 22+
         *  as a constant; the literal matches all supported levels). */
        private const val ACTION_NOTIFICATION_LISTENER_SETTINGS =
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        /** Same fully-qualified class name the Dart listener uses, so the
         *  enabled_notification_listeners comparison is byte-identical. */
        private const val LISTENER_CLASS =
            "com.example.chat.DynamicIslandNotificationListener"
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
