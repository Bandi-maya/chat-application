package com.example.chat.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/**
 * Port of storage_and_media_settings_screen.dart. In the Dart source the
 * switches are static (value fixed, onChanged no-op) and the cache tile only
 * shows a snackbar; that exact behavior is kept. [onClearCache] lets the
 * integration owner surface the snackbar equivalent.
 */
@Composable
fun StorageMediaScreen(onBack: () -> Unit, onClearCache: () -> Unit) {
    val colors = LocalAppColors.current
    SettingsPage("Storage & Data", "Media quality, upload limits & cache retention", onBack) {
        SettingsSection("Media Upload & Quality", "Configure resolution and maximum upload size thresholds.") {
            SettingsSwitchTile("HD", "Send Images in High Resolution", "Preserve photographic detail and avoid aggressive downscaling.", true, colors.primary) {}
            SettingsSwitchTile("\u21E7", "Increased Upload Limit (Up to 700MB)", "Allow sending large audio, video, and document payloads.", true, colors.primary) {}
            SettingsSwitchTile("\u25A4", "Share Up to 100 Images at Once", "Disable batch-selection limits in media picker.", true, colors.primary) {}
        }
        SettingsSection("Automatic Media Download") {
            SettingsSwitchTile("\u2248", "When Connected on Wi-Fi", "Photos, voice notes and documents", true, colors.success) {}
            SettingsSwitchTile("\u25B2", "When Using Mobile Data", "Voice notes only", false, colors.warning) {}
        }
        SettingsSection("Device Storage") {
            SettingsNavTile("\u2702", "Clear Local Media Cache", "Free up local disk space without deleting server chats", colors.primary, onClearCache)
        }
    }
}
