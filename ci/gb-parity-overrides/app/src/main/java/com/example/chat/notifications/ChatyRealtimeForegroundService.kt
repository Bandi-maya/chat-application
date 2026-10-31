package com.example.chat.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.chat.MainActivity
import com.example.chat.ServiceLocator

/**
 * Keeps Chaty's authenticated custom-backend WebSocket alive while the UI is backgrounded.
 * No Firebase/FCM dependency: ApiBackendService owns the socket, heartbeat and reconnect policy.
 * Android may still stop delivery after force-stop, user restriction, or OEM battery killing.
 */
class ChatyRealtimeForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val backend = runCatching { ServiceLocator.apiBackend }.getOrNull()
        if (backend == null || !backend.isAuthenticated || backend.isDemoSession) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Reuse the app's one authenticated socket; it already owns heartbeat and reconnect.
        backend.onAppResumed()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the task away is not equivalent to signing out.
        super.onTaskRemoved(rootIntent)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Chaty message connection", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Keeps real-time Chaty messages and incoming call alerts connected."
                    setShowBadge(false)
                },
            )
        }
    }

    private fun buildNotification(): Notification {
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, NOTIFICATION_ID, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Chaty is connected")
            .setContentText("Listening for messages and incoming calls")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "chaty_realtime_connection"
        const val NOTIFICATION_ID = 7402

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ChatyRealtimeForegroundService::class.java))
            } catch (e: Exception) {
                android.util.Log.w("ChatyRealtimeService", "Unable to start realtime service", e)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ChatyRealtimeForegroundService::class.java)) }
        }
    }
}
