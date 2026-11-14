package com.example.chat.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/** Migration tombstone for builds that previously kept a sticky idle socket.
 * Kept declared so Android can deliver an old restart and we can stop it.
 * Foreground UI owns realtime; background incoming events use FCM + REST. */
class ChatyRealtimeForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Never recreate the persistent connection notification on restart.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        // Retained as source-compatible stop behavior for stale callers.
        fun start(context: Context) { stop(context) }
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ChatyRealtimeForegroundService::class.java)) }
        }
    }
}
