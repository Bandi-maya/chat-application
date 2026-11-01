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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

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
 *       android:foregroundServiceType="microphone|camera|mediaProjection" android:stopWithTask="true"/>
 */
class CallForegroundService : Service() {

    // Dart ForegroundTaskOptions allowWakeLock/allowWifiLock = true: held only while the service
    // (i.e. an active call per CallForegroundPolicy) runs, released in onDestroy.
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    @Volatile private var foregroundStarted = false

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "chaty:call").apply {
                    setReferenceCounted(false); acquire()
                }
            }
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "chaty:call").apply {
                    setReferenceCounted(false); acquire()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("CallForegroundService", "call lock acquire failed: $e")
        }
    }

    private fun releaseLocks() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Chaty call"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) == true
        val isScreenProjection = intent?.getBooleanExtra(EXTRA_SCREEN_PROJECTION, false) == true
        acquireLocks()
        ensureChannel()
        val notification = buildNotification(title, text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var types = if (isVideo) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (isScreenProjection) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(SERVICE_ID, notification, types)
            } else {
                startForeground(SERVICE_ID, notification)
            }
            foregroundStarted = true
            if (isScreenProjection) projectionForegroundReady?.complete(true)
        } catch (error: Exception) {
            if (isScreenProjection) projectionForegroundReady?.complete(false)
            android.util.Log.e("CallForegroundService", "Unable to enter requested foreground-service mode", error)
            // Preserve an already-running voice/video call if only the optional
            // screen-projection upgrade was rejected by Android.
            if (!foregroundStarted) stopSelf(startId)
        }
        // Dart allowAutoRestart=false: never recreated by the system without a live call.
        return START_NOT_STICKY
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
        // Dart notificationInitialRoute '/': tapping the notification brings the app to its launch screen.
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (launch != null) {
            builder.setContentIntent(
                android.app.PendingIntent.getActivity(
                    this, SERVICE_ID, launch,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
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
        const val EXTRA_SCREEN_PROJECTION = "chaty.call.screen_projection"

        private val projectionReadyLock = Any()
        private val ownerLock = Any()
        @Volatile private var activeOwnerToken: String? = null
        @Volatile private var projectionForegroundReady: CompletableDeferred<Boolean>? = null

        /**
         * Wait until Android confirms the running call service has entered the
         * mediaProjection foreground-service type. The Activity obtains
         * consent first; this handshake prevents ScreenCapturerAndroid from
         * creating a MediaProjection too early.
         */
        suspend fun ensureScreenProjectionForeground(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
            val ready = CompletableDeferred<Boolean>()
            synchronized(projectionReadyLock) {
                projectionForegroundReady?.cancel()
                projectionForegroundReady = ready
            }
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, "Chaty screen sharing")
                putExtra(EXTRA_TEXT, "Sharing your screen in an active video call")
                putExtra(EXTRA_IS_VIDEO, true)
                putExtra(EXTRA_SCREEN_PROJECTION, true)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                withTimeout(5_000L) { ready.await() }
            } catch (error: Exception) {
                android.util.Log.e("CallForegroundService", "Screen-share foreground setup failed", error)
                false
            } finally {
                synchronized(projectionReadyLock) {
                    if (projectionForegroundReady === ready) projectionForegroundReady = null
                }
            }
        }

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
                putExtra(EXTRA_SCREEN_PROJECTION, active.isSharingScreen)
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

        /** Owner-aware entry point used by CallLifecycleCoordinator. */
        fun sync(context: Context, session: ChatyCallSession?, ownerToken: String) {
            synchronized(ownerLock) { activeOwnerToken = ownerToken }
            sync(context, session)
        }

        /** A stale coordinator may stop only the service instance it owns. */
        fun stop(context: Context, ownerToken: String, nextOwnerToken: String?) {
            synchronized(ownerLock) {
                if (activeOwnerToken != ownerToken) return
                activeOwnerToken = nextOwnerToken
            }
            stop(context)
        }

        fun stop(context: Context) {
            synchronized(ownerLock) { activeOwnerToken = null }
            try {
                context.stopService(Intent(context, CallForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
