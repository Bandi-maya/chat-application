package com.example.chat.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.example.chat.ui.theme.LocalAppColors

/**
 * Port of storage_and_media_settings_screen.dart on the Chaty settings primitives. In the Dart source the
 * switches are static (value fixed, onChanged no-op) and the cache tile only shows a snackbar; that exact
 * behavior is kept. [onClearCache] lets the integration owner show "Media cache cleared successfully."
 */
@Composable
fun StorageMediaScreen(onBack: () -> Unit, onClearCache: () -> Unit) {
    val colors = LocalAppColors.current
    ChatySettingsPage("Storage & Data", "Media quality, upload limits & cache retention", onBack = onBack) {
        ChatySettingsSection("Media Upload & Quality", "Configure resolution and maximum upload size thresholds.") {
            ChatySwitchTile("Send Images in High Resolution", true, {}, Icons.Rounded.Hd, colors.primary, "Preserve photographic detail and avoid aggressive downscaling.")
            ChatySettingsRowDivider()
            ChatySwitchTile("Increased Upload Limit (Up to 700MB)", true, {}, Icons.Rounded.UploadFile, colors.primary, "Allow sending large audio, video, and document payloads.")
            ChatySettingsRowDivider()
            ChatySwitchTile("Share Up to 100 Images at Once", true, {}, Icons.Outlined.PhotoLibrary, colors.primary, "Disable batch-selection limits in media picker.")
        }
        ChatySettingsSection("Automatic Media Download") {
            ChatySwitchTile("When Connected on Wi-Fi", true, {}, Icons.Rounded.Wifi, colors.success, "Photos, voice notes and documents")
            ChatySettingsRowDivider()
            ChatySwitchTile("When Using Mobile Data", false, {}, Icons.Rounded.SignalCellularAlt, colors.warning, "Voice notes only")
        }
        ChatySettingsSection("Device Storage") {
            ChatySettingsTile(
                title = "Clear Local Media Cache", icon = Icons.Rounded.CleaningServices, iconColor = colors.primary,
                subtitle = "Free up local disk space without deleting server chats",
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundSecondary) },
                onTap = onClearCache,
            )
        }
    }
}
