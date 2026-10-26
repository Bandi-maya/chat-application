package com.example.chat.ui.settings.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Resolves the Dart `Icons.<name>` identifiers stored in SettingsRegistry (SettingsDefinition.iconName). */
object SettingsIcons {
    fun resolve(name: String?): ImageVector? = when (name) {
        "ac_unit_rounded" -> Icons.Rounded.AcUnit
        "admin_panel_settings_outlined" -> Icons.Outlined.AdminPanelSettings
        "alternate_email_rounded" -> Icons.Rounded.AlternateEmail
        "apps_rounded" -> Icons.Rounded.Apps
        "auto_fix_high_rounded" -> Icons.Rounded.AutoFixHigh
        "auto_stories_outlined" -> Icons.Outlined.AutoStories
        "badge_outlined" -> Icons.Outlined.Badge
        "chat_bubble_outline_rounded" -> Icons.Rounded.ChatBubbleOutline
        "dashboard_customize_rounded" -> Icons.Rounded.DashboardCustomize
        "dashboard_outlined" -> Icons.Outlined.Dashboard
        "delete_forever_rounded" -> Icons.Rounded.DeleteForever
        "done_all_rounded" -> Icons.Rounded.DoneAll
        "edit_note_rounded" -> Icons.Rounded.EditNote
        "emoji_emotions_outlined" -> Icons.Outlined.EmojiEmotions
        "favorite_outline_rounded" -> Icons.Rounded.FavoriteBorder
        "fingerprint_rounded" -> Icons.Rounded.Fingerprint
        "groups_outlined" -> Icons.Outlined.Groups
        "hd_rounded" -> Icons.Rounded.Hd
        "history_toggle_off_rounded" -> Icons.Rounded.HistoryToggleOff
        "info_outline_rounded" -> Icons.Outlined.Info
        "lock_outline_rounded" -> Icons.Outlined.Lock
        "login_rounded" -> Icons.Rounded.Login
        "mark_chat_read_rounded" -> Icons.Rounded.MarkChatRead
        "notifications_active_outlined" -> Icons.Outlined.NotificationsActive
        "online_prediction_rounded" -> Icons.Rounded.OnlinePrediction
        "palette_outlined" -> Icons.Outlined.Palette
        "preview_outlined" -> Icons.Outlined.Preview
        "qr_code_scanner_rounded" -> Icons.Rounded.QrCodeScanner
        "reply_rounded" -> Icons.Rounded.Reply
        "ring_volume_rounded" -> Icons.Rounded.RingVolume
        "schedule_send_rounded" -> Icons.Rounded.ScheduleSend
        "screenshot_outlined" -> Icons.Outlined.Screenshot
        "text_fields_rounded" -> Icons.Rounded.TextFields
        "timer_outlined" -> Icons.Outlined.Timer
        "view_sidebar_outlined" -> Icons.Outlined.ViewSidebar
        "visibility_off_rounded" -> Icons.Rounded.VisibilityOff
        "visibility_outlined" -> Icons.Outlined.Visibility
        "wallpaper_rounded" -> Icons.Rounded.Wallpaper
        else -> null
    }
}
