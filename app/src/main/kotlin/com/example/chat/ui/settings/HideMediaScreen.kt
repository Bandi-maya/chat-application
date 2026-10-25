package com.example.chat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.theme.LocalAppColors
import java.io.File

/**
 * Port of hide_media_screen.dart. Writes/removes <files>/ChatyMedia/.nomedia
 * exactly as _syncNoMediaMarker does (Dart used the app documents dir; the
 * Android equivalent for app-private documents is filesDir).
 * [onResetDone] is the hook for the "Media gallery visibility restored to
 * default" snackbar.
 */
@Composable
fun HideMediaScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onResetDone: () -> Unit = {},
) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val prefs = preferences.universal

    fun syncNoMedia(shouldHide: Boolean) {
        try {
            val mediaDir = File(context.filesDir, "ChatyMedia")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            val marker = File(mediaDir, ".nomedia")
            if (shouldHide) {
                if (!marker.exists()) marker.writeText("")
            } else if (marker.exists()) {
                marker.delete()
            }
        } catch (e: Exception) {
            android.util.Log.d("HideMedia", "Syncing .nomedia marker failed: $e")
        }
    }

    SettingsPage("Hide Media from gallery", null, onBack) {
        Text(
            "Turn ON toggles to prevent media received in chats from being scanned by and visible in your device gallery.",
            color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        SettingsSection(title = null) {
            SettingsSwitchTile("\u25A4", "Photos", "Hide photos downloaded from chats in gallery", prefs.hidePhotosFromGallery) { v ->
                preferences.updateUniversal(prefs.copy(hidePhotosFromGallery = v))
                syncNoMedia(v || prefs.hideVideosFromGallery || prefs.hideGifsFromGallery)
            }
            SettingsDivider()
            SettingsSwitchTile("\u25B6", "Videos", "Hide received video clips in gallery", prefs.hideVideosFromGallery) { v ->
                preferences.updateUniversal(prefs.copy(hideVideosFromGallery = v))
                syncNoMedia(prefs.hidePhotosFromGallery || v || prefs.hideGifsFromGallery)
            }
            SettingsDivider()
            SettingsSwitchTile("GIF", "GIFs", "Hide downloaded GIFs and animated stickers in gallery", prefs.hideGifsFromGallery) { v ->
                preferences.updateUniversal(prefs.copy(hideGifsFromGallery = v))
                syncNoMedia(prefs.hidePhotosFromGallery || prefs.hideVideosFromGallery || v)
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                "\u21BA  Reset Gallery Settings",
                color = colors.accent, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    preferences.updateUniversal(
                        prefs.copy(hidePhotosFromGallery = false, hideVideosFromGallery = false, hideGifsFromGallery = false),
                    )
                    syncNoMedia(false)
                    onResetDone()
                }.padding(12.dp),
            )
        }
    }
}
