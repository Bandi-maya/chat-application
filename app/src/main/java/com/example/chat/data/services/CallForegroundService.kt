package com.example.chat.data.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Android foreground-execution wrapper for an already-authorized WebRTC call.
 * Port of lib/data/services/call_foreground_service.dart.
 *
 * Started only after local media has been opened: outgoing calls reach
 * `ringing` after capture succeeds; incoming calls reach `connecting` after
 * the user accepts and capture succeeds. That ordering is required by
 * Android 14+ while-in-use camera/microphone rules. stopWithTask equivalent:
 * [stopSelf] runs on task removal via [onTaskRemoved].
 *
 * Manifest (integration owner):
 *   <service android:name=".data.services.CallForegroundService"
 *       android:foregroundServiceType="microphone|camera" android:stopWithTask="true"/>
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Chaty call"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) == true
        ensureChannel()
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val types = if (isVideo) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(SERVICE_ID, notification, types)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = if (isVideo) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(SERVICE_ID, notification, types)
        } else {
            startForeground(SERVICE_ID, notification)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // stopWithTask=true parity: task removal cannot masquerade as a
        // surviving call.
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active calls",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    "Keeps an active Chaty voice or video call running while the app is in the background."
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "chaty_active_call"
        const val SERVICE_ID = 7401
        const val EXTRA_TITLE = "chaty.call.title"
        const val EXTRA_TEXT = "chaty.call.text"
        const val EXTRA_IS_VIDEO = "chaty.call.is_video"

        /** Sync entry point consumed by CallLifecycleCoordinator's
         *  foregroundSync seam. */
        fun sync(context: Context, session: ChatyCallSession?) {
            if (!CallForegroundPolicy.shouldRunFor(session)) {
                stop(context)
                return
            }
            val active = session!!
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, CallForegroundPolicy.notificationTitle(active))
                putExtra(EXTRA_TEXT, CallForegroundPolicy.notificationText(active))
                putExtra(EXTRA_IS_VIDEO, active.isVideo)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // WebRTC remains the media source of truth; a foreground-service
                // failure must never present as a successful background-call
                // guarantee.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
