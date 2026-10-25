package com.example.chat.ui.core.notifications

/**
 * Exact port of lib/ui/core/notifications/notification_icon_preset.dart.
 * Pure JVM: colors are ARGB longs; icons are glyph keys mapped to Material
 * icons in the UI layer (iconKey strings are the Dart Icons.* getter names).
 *
 * Shared source of truth for the Universal Notification Icon picker and the
 * in-app notification toast: selecting an icon must change both preview and
 * runtime rendering rather than only saving a display label.
 */
data class ChatyNotificationIconPreset(
    val name: String,
    val colorArgb: Long,
    val iconKey: String,
)

object ChatyNotificationIconPresets {
    val values: List<ChatyNotificationIconPreset> = listOf(
        ChatyNotificationIconPreset(name = "White", colorArgb = 0xFFFFFFFF, iconKey = "chat_bubble_outline_rounded"),
        ChatyNotificationIconPreset(name = "Black", colorArgb = 0xDD000000, iconKey = "chat_bubble_rounded"),
        ChatyNotificationIconPreset(name = "Blue", colorArgb = 0xFF2196F3, iconKey = "forum_rounded"),
        ChatyNotificationIconPreset(name = "Red", colorArgb = 0xFFFF5252, iconKey = "mode_comment_rounded"),
        ChatyNotificationIconPreset(name = "Green", colorArgb = 0xFF4CAF50, iconKey = "sms_rounded"),
        ChatyNotificationIconPreset(name = "Yellow", colorArgb = 0xFFFFC107, iconKey = "chat_rounded"),
        ChatyNotificationIconPreset(name = "Orange", colorArgb = 0xFFFF5722, iconKey = "mark_chat_unread_rounded"),
        ChatyNotificationIconPreset(name = "Gold", colorArgb = 0xFFFFD700, iconKey = "message_rounded"),
        ChatyNotificationIconPreset(name = "Cyan", colorArgb = 0xFF00BCD4, iconKey = "bubble_chart_rounded"),
        ChatyNotificationIconPreset(name = "Pink", colorArgb = 0xFFFF4081, iconKey = "chat_bubble_rounded"),
        ChatyNotificationIconPreset(name = "Magenta", colorArgb = 0xFFE040FB, iconKey = "chat_bubble_outline_rounded"),
        ChatyNotificationIconPreset(name = "Purple", colorArgb = 0xFF673AB7, iconKey = "forum_outlined"),
        ChatyNotificationIconPreset(name = "Notifybar 12", colorArgb = 0xFF9E9E9E, iconKey = "mode_comment_outlined"),
        ChatyNotificationIconPreset(name = "Notifybar 13", colorArgb = 0xFFE91E63, iconKey = "chat_bubble_outline_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 14", colorArgb = 0xFF009688, iconKey = "forum_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 15", colorArgb = 0xFF7C4DFF, iconKey = "message_outlined"),
        ChatyNotificationIconPreset(name = "Notifybar 16", colorArgb = 0xFFFFAB40, iconKey = "chat_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 17", colorArgb = 0xFFFFD740, iconKey = "mark_chat_unread_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 18", colorArgb = 0xFF8BC34A, iconKey = "chat_bubble_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 19", colorArgb = 0xFFF44336, iconKey = "sms_outlined"),
        ChatyNotificationIconPreset(name = "Notifybar 20", colorArgb = 0xFFE91E63, iconKey = "mode_comment_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 21", colorArgb = 0xFF9E9E9E, iconKey = "forum_outlined"),
        ChatyNotificationIconPreset(name = "Notifybar 22", colorArgb = 0xFFFF5252, iconKey = "chat_bubble_outline_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 23", colorArgb = 0xFF69F0AE, iconKey = "mark_chat_unread_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 24", colorArgb = 0xFFFF4081, iconKey = "message_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 25", colorArgb = 0xFFFFC107, iconKey = "forum_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 26", colorArgb = 0xFF40C4FF, iconKey = "chat_bubble_rounded"),
        ChatyNotificationIconPreset(name = "Notifybar 27", colorArgb = 0xFF795548, iconKey = "chat_rounded"),
    )

    fun forName(name: String?): ChatyNotificationIconPreset {
        for (preset in values) {
            if (preset.name == name) return preset
        }
        return values.first()
    }
}
