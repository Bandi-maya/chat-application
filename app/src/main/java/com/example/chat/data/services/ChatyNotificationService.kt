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
    val colorArgb: Long = 0xFF2563EB,
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
    private val notificationsLock = Any()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /** Dart ChangeNotifier.addListener/removeListener. */
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun fireListeners() { notifyChanged(); for (l in listeners) l() }

    val notifications: List<ChatyEventNotification>
        get() = synchronized(notificationsLock) { _notifications.toList() }

    val latest: ChatyEventNotification?
        get() = synchronized(notificationsLock) { _notifications.firstOrNull() }

    fun triggerEventNotification(
        title: String,
        body: String,
        icon: EventNotificationIcon = EventNotificationIcon.notifications,
        colorArgb: Long = 0xFF2563EB,
        textColorArgb: Long? = null,
        userId: String? = null,
        avatarInitials: String? = null,
        avatarColorHex: String? = null,
    ) {
        val notification = ChatyEventNotification(
            id = "notif_${java.util.UUID.randomUUID()}",
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
        synchronized(notificationsLock) {
            _notifications.add(0, notification)
            while (_notifications.size > 100) {
                _notifications.removeAt(_notifications.size - 1)
            }
        }
        fireListeners()
    }

    fun clearAll() {
        val changed = synchronized(notificationsLock) {
            if (_notifications.isEmpty()) false else {
                _notifications.clear()
                true
            }
        }
        if (changed) fireListeners()
    }
}
