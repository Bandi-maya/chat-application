package com.example.chat.data.services

import com.example.chat.data.realtime.EventNotificationIcon
import java.time.Instant

/**
 * Exact port of lib/data/services/notification_service.dart
 * (ChatyNotificationService): the in-memory event-notification feed behind
 * GB-style toasts. Flutter IconData/Color map onto the existing
 * [EventNotificationIcon] enum and ARGB Longs.
 */
data class ChatyEventNotification(
    val id: String,
    val title: String,
    val body: String,
    val icon: EventNotificationIcon = EventNotificationIcon.notifications,
    val colorArgb: Long = 0xFF6366F1,
    /** Optional custom text tint for title/body (GB toast `_tc` keys). */
    val textColorArgb: Long? = null,
    val timestamp: Instant,
    val userId: String? = null,
    val avatarInitials: String? = null,
    val avatarColorHex: String? = null,
)

class ChatyNotificationService(
    private val notifyChanged: () -> Unit = {},
) {
    private val _notifications = mutableListOf<ChatyEventNotification>()

    val notifications: List<ChatyEventNotification>
        get() = _notifications.toList()

    val latest: ChatyEventNotification?
        get() = _notifications.firstOrNull()

    fun triggerEventNotification(
        title: String,
        body: String,
        icon: EventNotificationIcon = EventNotificationIcon.notifications,
        colorArgb: Long = 0xFF6366F1,
        textColorArgb: Long? = null,
        userId: String? = null,
        avatarInitials: String? = null,
        avatarColorHex: String? = null,
    ) {
        val notification = ChatyEventNotification(
            id = "notif_${System.nanoTime()}",
            title = title,
            body = body,
            icon = icon,
            colorArgb = colorArgb,
            textColorArgb = textColorArgb,
            timestamp = Instant.now(),
            userId = userId,
            avatarInitials = avatarInitials,
            avatarColorHex = avatarColorHex,
        )
        _notifications.add(0, notification)
        while (_notifications.size > 100) {
            _notifications.removeAt(_notifications.size - 1)
        }
        notifyChanged()
    }

    fun clearAll() {
        if (_notifications.isEmpty()) return
        _notifications.clear()
        notifyChanged()
    }
}
