package com.example.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbSwitchTile
import kotlinx.coroutines.launch
import java.io.File

/**
 * Port of lib/features/settings/universal/hide_media_screen.dart on the GB kit (GbCardContainer/GbSwitchTile,
 * Dart Material icons). The Dart AppBar uses theme.surfaceColor with a 19*fontScale/w700 title in
 * theme.primaryTextColor and a padded ChatyBackButton, and the page background is theme.backgroundColor.
 * The .nomedia marker lives in ChatyMedia under the app files dir (Dart getApplicationDocumentsDirectory is
 * the app's files dir on Android). Uncompiled; no pixel check.
 */
@Composable
fun HideMediaScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    onBack: () -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val theme = themeController.globalTheme
    val context = LocalContext.current
    val prefs = preferences.universal
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val accent = Color(theme.accentColor)

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

    Box(Modifier.fillMaxSize().background(Color(theme.backgroundColor))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(theme.surfaceColor))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Hide Media from gallery",
                    color = Color(theme.primaryTextColor),
                    fontSize = (19 * theme.fontScale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "Turn ON toggles to prevent media received in chats from being scanned by and visible in your device gallery.",
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = Color(theme.secondaryTextColor), fontSize = 13.sp, lineHeight = 18.2.sp,
                )
                Spacer(Modifier.height(8.dp))
                GbCardContainer {
                    GbSwitchTile(
                        icon = Icons.Rounded.PhotoLibrary, title = "Photos",
                        subtitle = "Hide photos downloaded from chats in gallery",
                        value = prefs.hidePhotosFromGallery,
                        onChanged = { v ->
                            preferences.updateUniversal(prefs.copy(hidePhotosFromGallery = v))
                            syncNoMedia(v || prefs.hideVideosFromGallery || prefs.hideGifsFromGallery)
                        },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.VideoLibrary, title = "Videos",
                        subtitle = "Hide received video clips in gallery",
                        value = prefs.hideVideosFromGallery,
                        onChanged = { v ->
                            preferences.updateUniversal(prefs.copy(hideVideosFromGallery = v))
                            syncNoMedia(prefs.hidePhotosFromGallery || v || prefs.hideGifsFromGallery)
                        },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.GifBox, title = "GIFs",
                        subtitle = "Hide downloaded GIFs and animated stickers in gallery",
                        value = prefs.hideGifsFromGallery,
                        onChanged = { v ->
                            preferences.updateUniversal(prefs.copy(hideGifsFromGallery = v))
                            syncNoMedia(prefs.hidePhotosFromGallery || prefs.hideVideosFromGallery || v)
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = {
                            preferences.updateUniversal(
                                prefs.copy(hidePhotosFromGallery = false, hideVideosFromGallery = false, hideGifsFromGallery = false),
                            )
                            syncNoMedia(false)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar("Media gallery visibility restored to default")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp), tint = accent)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Gallery Settings", color = accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
