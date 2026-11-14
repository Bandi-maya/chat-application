package com.example.chat.domain.notifications

import com.example.chat.domain.models.NotificationToastPreferences

/** Presentation only. Event permissions, contact overrides and protected-chat guards stay upstream. */
data class ContactToastStyle(
    val showAvatar: Boolean,
    val elevated: Boolean,
    val radius: Double,
    val position: String,
    val background: Long?,
    val text: Long?,
) {
    companion object {
        /** Only live event kinds verified by the realtime service are routed here.
         * Profile updates are NOT profile views; no story-view event is available. */
        fun resolve(kind: String, prefs: NotificationToastPreferences): ContactToastStyle? = when (kind) {
            "online" -> ContactToastStyle(prefs.onlineWithProfilePic, prefs.onlineElevation,
                radius(prefs.onlineRadius), position(prefs.onlinePosition), prefs.onlineBgColor, prefs.onlineTextColor)
            "typing" -> ContactToastStyle(prefs.typingWithProfilePic, prefs.typingElevation,
                radius(prefs.typingRadius), position(prefs.typingPosition), prefs.typingBgColor, prefs.typingTextColor)
            else -> null
        }
        private fun radius(value: Double): Double = if (value.isFinite()) value.coerceIn(0.0, 64.0) else 8.0
        private fun position(value: String): String = when {
            value.contains("bottom", ignoreCase = true) -> "bottom"
            value.contains("center", ignoreCase = true) || value.contains("middle", ignoreCase = true) -> "center"
            else -> "top"
        }
    }
}
