package com.example.chat.island

import android.app.Notification
import android.service.notification.StatusBarNotification

enum class TakeoverMode {
    MIRROR, // Normal safe mirror, leaves original intact
    TAKEOVER, // Cancels eligible notification to prevent double heads-up
    QUIET_VISUALS, // Custom OEM quiet mode
}

/**
 * Manages notification takeover policies safely without breaking calls/media (fixing K-12).
 */
object TakeoverPolicy {
    fun shouldCancelOriginal(sbn: StatusBarNotification, mode: TakeoverMode): Boolean {
        if (mode != TakeoverMode.TAKEOVER) return false
        val n = sbn.notification ?: return false

        // Never cancel calls
        if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_MISSED_CALL) {
            return false
        }

        // Never cancel ongoing foreground service tasks
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 || n.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            return false
        }

        // Never cancel Chaty's own notifications
        if (sbn.packageName.contains("chat", ignoreCase = true)) {
            return false
        }

        return true
    }
}
