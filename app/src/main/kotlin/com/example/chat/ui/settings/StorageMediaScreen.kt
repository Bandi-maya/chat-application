package com.example.chat.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.UploadFile
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.theme.LocalAppColors

/**
 * Storage controls backed by the same preference keys consumed by the media
 * pipeline. Unsupported automatic-download switches are deliberately not
 * rendered until a real background-download policy is available.
 */
@Composable
fun StorageMediaScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onClearCache: () -> Unit,
    cacheLimitMb: Int = 1024,
    onCacheLimitChange: (Int) -> Unit = {},
) {
    val colors = LocalAppColors.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { revision += 1 }
        preferences.addListener(listener)
        onDispose { preferences.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") revision

    val fullResolutionMb = preferences.universal.sendImagesFullResolutionMb.coerceIn(1, 6)
    val highResolutionEnabled = fullResolutionMb >= 4
    val configuredUploadMb = preferences.gbDouble("Up_size_limit", 700.0).toInt().coerceIn(1, 700)
    val largeUploadsEnabled = configuredUploadMb >= 700
    val imageShareLimit = preferences.gbInt("Img_share_limit", 30).coerceIn(1, 100)
    val batchShareEnabled = imageShareLimit >= 100

    ChatySettingsPage("Storage & Data", "Upload preferences and local media retention", onBack = onBack) {
        ChatySettingsSection(
            "Media Upload & Quality",
            "These controls update the limits consumed by Chaty's media upload pipeline.",
        ) {
            ChatySwitchTile(
                icon = Icons.Rounded.Hd,
                iconColor = colors.primary,
                title = "High-resolution images",
                subtitle = if (highResolutionEnabled) "Image upload allowance: up to 6 MB" else "Image upload allowance: up to 1 MB",
                value = highResolutionEnabled,
                onChanged = { enabled ->
                    preferences.updateUniversal(
                        preferences.universal.copy(sendImagesInFullResolutionMb = if (enabled) 6.0 else 1.0),
                        logTitle = "Image upload resolution",
                    )
                },
            )
            ChatySettingsRowDivider()
            ChatySwitchTile(
                icon = Icons.Rounded.UploadFile,
                iconColor = colors.primary,
                title = "Large uploads (up to 700 MB)",
                subtitle = if (largeUploadsEnabled) "Large-file upload limit enabled" else "Upload limit set to 100 MB",
                value = largeUploadsEnabled,
                onChanged = { enabled ->
                    preferences.updateGbFeature(
                        "Up_size_limit",
                        if (enabled) 700.0 else 100.0,
                        logTitle = "Maximum media upload size",
                    )
                },
            )
            ChatySettingsRowDivider()
            ChatySwitchTile(
                icon = Icons.Outlined.PhotoLibrary,
                iconColor = colors.primary,
                title = "Send up to 100 images at once",
                subtitle = if (batchShareEnabled) "Image selection limit: 100" else "Image selection limit: 30",
                value = batchShareEnabled,
                onChanged = { enabled ->
                    preferences.updateGbFeature(
                        "Img_share_limit",
                        if (enabled) 100 else 30,
                        logTitle = "Image batch selection limit",
                    )
                },
            )
        }
        ChatySettingsSection(
            "Automatic Media Download",
            "Automatic background-download controls are not available in this build. Media is downloaded only through an explicit user action.",
        ) { }
        ChatySettingsSection(
            "Offline Media Storage",
            "Media opened or downloaded is cached locally. Reducing the limit removes older local cache entries only; server copies are not deleted.",
        ) {
            listOf(500 to "500 MB", 1024 to "1 GB", 2048 to "2 GB").forEachIndexed { index, (mb, label) ->
                if (index > 0) ChatySettingsRowDivider()
                ChatySettingsTile(
                    title = label,
                    icon = Icons.Outlined.PhotoLibrary,
                    iconColor = colors.primary,
                    subtitle = if (mb == 1024) "Default" else null,
                    trailing = { if (cacheLimitMb == mb) com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check, semanticLabel = "Selected", color = colors.primary) },
                    onTap = { onCacheLimitChange(mb) },
                )
            }
        }
        ChatySettingsSection("Device Storage") {
            ChatySettingsTile(
                title = "Clear Local Media Cache",
                icon = Icons.Rounded.CleaningServices,
                iconColor = colors.primary,
                subtitle = "Free local disk space without deleting server messages",
                trailing = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundSecondary) },
                onTap = onClearCache,
            )
        }
    }
}
