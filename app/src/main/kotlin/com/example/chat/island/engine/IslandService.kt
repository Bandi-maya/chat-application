package com.example.chat.island.engine

import com.example.chat.island.ui.components.IslandIcon as Icon
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.chat.MainActivity
import com.example.chat.R
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.Perms
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IslandService : Service() {
    private val lifetime = MainScope()
    private var window: IslandOverlay? = null

    override fun onCreate() {
        super.onCreate()
        IslandSettings.init(applicationContext)
        IslandController.attach(applicationContext)
        val notice = notification()
        if (Build.VERSION.SDK_INT < 34) startForeground(2, notice)
        else startForeground(2, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        window = IslandOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        lifetime.launch {
            combine(IslandA11yService.connected, IslandSettings.enabled) { preferredHost, enabled ->
                enabled && !preferredHost && Perms.overlay(this@IslandService)
            }.collect { shouldShow ->
                if (shouldShow) window?.show() else {
                    window?.hide()
                    stopSelf() // Disabled, permission lost, or accessibility now owns the window.
                }
            }
        }
        running.value = true
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        for (obsolete in arrayOf("island", "island_status")) {
            try { manager.deleteNotificationChannel(obsolete) } catch (_: Exception) {}
        }
        try { manager.cancel(1) } catch (_: Exception) {}
        manager.createNotificationChannel(NotificationChannel(
            "island_status_v3", getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN
        ).also { it.setShowBadge(false) })

        val destination = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val tap = PendingIntent.getActivity(this, 0, destination,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "island_status_v3").apply {
            setContentIntent(tap)
            setSmallIcon(R.drawable.ic_stat_island)
            setContentText(getString(R.string.notif_text))
            setContentTitle(getString(R.string.notif_title))
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setColor(0xFF7B3DF5.toInt())
            setShowWhen(false)
            setSilent(true)
            setOngoing(true)
        }.build()
    }

    override fun onDestroy() {
        val previous = window
        window = null
        lifetime.cancel()
        previous?.hide()
        running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        val running = MutableStateFlow(false)
        val active: Flow<Boolean> = combine(running, IslandA11yService.connected, IslandSettings.enabled) {
            foreground, accessibility, requested -> requested && (foreground || accessibility)
        }

        fun isActive(): Boolean {
            if (!IslandSettings.enabled.value) return false
            return running.value || IslandA11yService.connected.value
        }
    }
}
