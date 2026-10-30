package com.example.chat.data.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
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

    // Dart ForegroundTaskOptions allowWakeLock/allowWifiLock = true: held only while the service
    // (i.e. an active call per CallForegroundPolicy) runs, released in onDestroy.
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

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
        Log.i(TAG, "FGS-DIAG onDestroy")
        releaseLocks()
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "FGS-DIAG onCreate")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "FGS-DIAG onStartCommand entered startId=$startId")
        if (intent?.action == ACTION_STOP) {
            // Couriered stop (see stop()): satisfy any pending foreground
            // requirement on this record, then remove the notification and
            // stop. The platform kills the process when a record comes down
            // while still waiting for start foreground (system_server:
            // "Bringing down service while still waiting for start
            // foreground"), so a stop must never race context.stopService
            // against an in-flight startForegroundService.
            Log.i(TAG, "FGS-DIAG ACTION_STOP courier startId=$startId")
            try {
                ensureChannel()
                val stopNotification = buildNotification("Chaty call", "")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(SERVICE_ID, stopNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(SERVICE_ID, stopNotification)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                android.util.Log.w("CallForegroundService", "stop courier foreground failed: $e")
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Chaty call"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) == true
        // Foreground FIRST: on API 31+ the system kills the service with
        // ForegroundServiceDidNotStartInTimeException if startForeground is not
        // reached within a few seconds of startForegroundService(). Lock
        // acquisition (wifi/wake) can block, so it must happen after the service
        // is foreground, never before it.
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
        Log.i(TAG, "FGS-DIAG startForeground posted startId=$startId")
        acquireLocks()
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
        // Tapping returns to the in-app call UI: MainActivity routes ACTION_SHOW_CALL
        // to the call presentation controller, only while a session is actually live.
        val showCall = android.app.PendingIntent.getActivity(
            this, SERVICE_ID,
            Intent(this, com.example.chat.MainActivity::class.java).apply {
                action = com.example.chat.notifications.ChatyNotifications.ACTION_SHOW_CALL
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        // The connected call previously exposed no notification action at all, so a
        // backgrounded call had no system-level hangup. CallStyle gives a standard
        // system-rendered hang-up button (no custom styling is promised).
        val hangUp = android.app.PendingIntent.getBroadcast(
            this, SERVICE_ID + 1,
            Intent(this, com.example.chat.notifications.CallActionReceiver::class.java).apply {
                action = com.example.chat.notifications.ChatyNotifications.ACTION_END_CALL
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val caller = androidx.core.app.Person.Builder().setName(title).build()
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .setStyle(androidx.core.app.NotificationCompat.CallStyle.forOngoingCall(caller, hangUp))
            .setContentIntent(showCall)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "chaty_active_call"
        const val SERVICE_ID = 7401
        private const val TAG = "CallForegroundService"
        const val EXTRA_TITLE = "chaty.call.title"
        const val EXTRA_TEXT = "chaty.call.text"
        const val EXTRA_IS_VIDEO = "chaty.call.is_video"
        private const val ACTION_STOP = "chaty.call.STOP"

        private val syncLock = Any()
        private var syncHandler: android.os.Handler? = null
        private var pendingSync: Runnable? = null

        /** Sync entry point consumed by CallLifecycleCoordinator's
         *  foregroundSync seam.
         *
         *  Start/stop decisions are serialized on the main thread and rapid
         *  session flaps collapse to the latest state: every
         *  startForegroundService() opens a "did not start in time" window,
         *  and a start racing a later stop (the hang-up path) crashes the
         *  process with ForegroundServiceDidNotStartInTimeException when the
         *  window expires unserviced. Coalescing guarantees at most one
         *  pending decision and that a terminal stop discards any queued
         *  start. The pending slot always clears when its runnable runs, so
         *  later sessions can start the service again. */
        fun sync(context: Context, session: ChatyCallSession?) {
            val appContext = context.applicationContext
            val handler = syncHandler
                ?: android.os.Handler(android.os.Looper.getMainLooper()).also { syncHandler = it }
            synchronized(syncLock) {
                pendingSync?.let { handler.removeCallbacks(it) }
                val task = Runnable {
                    synchronized(syncLock) { pendingSync = null }
                    applySync(appContext, session)
                }
                pendingSync = task
                handler.post(task)
            }
        }

        private fun applySync(context: Context, session: ChatyCallSession?) {
            if (!CallForegroundPolicy.shouldRunFor(session)) {
                Log.i(TAG, "FGS-DIAG applySync -> stop (session=${session?.state})")
                stop(context)
                return
            }
            Log.i(TAG, "FGS-DIAG applySync -> startForegroundService (session=${session?.state})")
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
            } catch (e: Exception) {
                // WebRTC remains the media source of truth; a foreground-service
                // failure must never present as a successful background-call
                // guarantee.
                android.util.Log.w("CallForegroundService", "foreground start failed: $e")
            }
        }

        fun stop(context: Context) {
            // A terminal stop also discards any queued start decision so a
            // hang-up cannot leave a start window open behind the stop.
            val handler = syncHandler
            if (handler != null) {
                synchronized(syncLock) {
                    pendingSync?.let { handler.removeCallbacks(it) }
                    pendingSync = null
                }
            }
            Log.i(TAG, "FGS-DIAG stop requested (courier)")
            // Never race context.stopService against an in-flight
            // startForegroundService: if the platform processes the stop
            // first it brings the record down with the foreground
            // requirement still pending and kills the process with
            // ForegroundServiceDidNotStartInTimeException. Couriering the
            // stop as a start command on the same record keeps platform
            // ordering: the service satisfies the requirement first, then
            // removes its notification and stops itself.
            try {
                context.startService(
                    Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP),
                )
            } catch (e: Exception) {
                // Background-start rejection means no foreground service is
                // running or pending, so nothing needs the courier; the
                // legacy direct stop is then a harmless no-op.
                android.util.Log.w("CallForegroundService", "stop courier start failed: $e")
                try {
                    context.stopService(Intent(context, CallForegroundService::class.java))
                } catch (_: Exception) {
                }
            }
        }
    }
}

