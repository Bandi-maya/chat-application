package com.example.chat.island.core

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.chat.island.engine.IslandA11yService
import com.example.chat.island.engine.IslandService

object Perms {
    private enum class Destination(val action: String, val scoped: Boolean = false) {
        Overlay(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true),
        Accessibility(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Notifications(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        BatteryRequest(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true),
        BatteryList(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        App(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
    }

    private fun open(context: Context, destination: Destination): Boolean {
        val request = Intent(destination.action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (destination.scoped) data = Uri.fromParts("package", context.packageName, null)
        }
        return try {
            context.startActivity(request)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun grantedSince(context: Context, minimumApi: Int, permission: String): Boolean =
        if (Build.VERSION.SDK_INT < minimumApi) true
        else ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun overlay(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun listener(c: Context): Boolean = c.packageName in NotificationManagerCompat.getEnabledListenerPackages(c)

    fun accessibility(c: Context): Boolean {
        val expected = ComponentName(c, IslandA11yService::class.java)
        return try {
            val manager = c.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty().any { service ->
                    val info = service.resolveInfo.serviceInfo
                    ComponentName(info.packageName, info.name) == expected
                }
        } catch (_: Exception) {
            false
        }
    }

    fun post(c: Context): Boolean = grantedSince(c, 33, Manifest.permission.POST_NOTIFICATIONS)
    fun bluetooth(c: Context): Boolean = grantedSince(c, 31, Manifest.permission.BLUETOOTH_CONNECT)

    fun battery(c: Context): Boolean = try {
        (c.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(c.packageName) == true
    } catch (_: Exception) {
        false
    }

    fun coreOk(c: Context): Boolean {
        if (!listener(c) || !post(c)) return false
        return overlay(c) || accessibility(c)
    }

    fun openOverlaySettings(c: Context) { open(c, Destination.Overlay) }
    fun openAccessibilitySettings(c: Context) { open(c, Destination.Accessibility) }
    fun openListenerSettings(c: Context) { open(c, Destination.Notifications) }
    fun openAppInfo(c: Context) { open(c, Destination.App) }
    fun openBatterySettings(c: Context) {
        if (!open(c, Destination.BatteryRequest)) open(c, Destination.BatteryList)
    }
}

object IslandControl {
    private fun record(context: Context, enabled: Boolean) {
        IslandSettings.enabled.value = enabled
        IslandSettings.save()
        context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE).edit().run {
            putBoolean("enabled", enabled)
            apply()
        }
    }

    fun start(c: Context) {
        record(c, true)
        if (!Perms.overlay(c) && !Perms.accessibility(c)) {
            Perms.openAccessibilitySettings(c)
            return
        }
        try {
            ContextCompat.startForegroundService(c, Intent(c, IslandService::class.java))
        } catch (_: Exception) {
            // The user can retry after Android lifts a background-start restriction.
        }
    }

    fun stop(c: Context) {
        record(c, false)
        c.stopService(Intent(c, IslandService::class.java))
    }
}
