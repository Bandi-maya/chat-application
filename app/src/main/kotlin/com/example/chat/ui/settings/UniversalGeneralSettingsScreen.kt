package com.example.chat.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbRadioSelectionModal
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSliderTile
import com.example.chat.ui.kit.GbSwitchTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val translateOptions = listOf("Server Translation (Google/Cloud)", "In-app Translation (Offline/Direct)")
private val translationLanguages = listOf(
    "Show All", "English (US)", "Spanish (Espa\u00F1ol)", "Arabic (\u0627\u0644\u0639\u0631\u0628\u064A\u0629)",
    "Hindi (\u0939\u093F\u0928\u094D\u0926\u0940)", "Portuguese (Portugu\u00EAs)", "French (Fran\u00E7ais)",
    "German (Deutsch)", "Russian (\u0420\u0443\u0441\u0441\u043A\u0438\u0439)", "Japanese (\u65E5\u672C\u8A9E)",
)

private fun isLogLike(f: File): Boolean {
    val n = f.name.lowercase()
    return n.endsWith(".log") || n.startsWith("chaty_") || n.contains("cache")
}

/**
 * Port of universal_general_settings_screen.dart on the GB kit: GbCardContainer sections (Translation / General
 * Mods / "Image/Video Mods"), GbSettingRow/GbSwitchTile/GbSliderTile with the Dart Material icons, GbRadioSelectionModal
 * pickers, and the Dart AppBar (surfaceColor, "Settings" 19*fontScale/w700, padded ChatyBackButton). Log size/clear
 * walk the app cache dir (Dart getTemporaryDirectory) with the same filename filter; snackbar text is Dart's.
 * Uncompiled; no pixel check.
 */
@Composable
fun UniversalGeneralSettingsScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    onBack: () -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val theme = themeController.globalTheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = preferences.universal
    var logBytes by remember { mutableStateOf(0L) }
    var calculating by remember { mutableStateOf(false) }
    var dialog by remember { mutableIntStateOf(0) } // 1 translate, 2 language, 3 gif

    suspend fun refresh() {
        calculating = true
        logBytes = try {
            withContext(Dispatchers.IO) {
                context.cacheDir.walkTopDown().filter { it.isFile && isLogLike(it) }.sumOf { it.length() }
            }
        } catch (_: Exception) { logBytes }
        calculating = false
    }
    LaunchedEffect(Unit) { refresh() }

    Box(Modifier.fillMaxSize().background(Color(theme.backgroundColor))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(theme.surfaceColor))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
                Spacer(Modifier.width(8.dp))
                Text("Settings", color = Color(theme.primaryTextColor), fontSize = (19 * theme.fontScale).sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                GbCardContainer {
                    GbSettingRow(
                        icon = Icons.Rounded.Translate, title = "Translate Option Settings",
                        subtitle = "Choose how the translation icon works: ${prefs.translateOption}",
                        hasSubScreen = true, onTap = { dialog = 1 },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Language, title = "Default translation language",
                        subtitle = prefs.translationLanguage, hasSubScreen = true, onTap = { dialog = 2 },
                    )
                }
                Spacer(Modifier.height(14.dp))
                GbCardContainer {
                    GbSwitchTile(Icons.Rounded.ViewCarousel, "Conversation Cards (multi Chats)", prefs.conversationCards,
                        { preferences.updateUniversal(prefs.copy(conversationCards = it)) },
                        "Every chat you open will become a card, can switch from Recents easily")
                    GbSwitchTile(Icons.Rounded.NotificationsOff, "Disable Heads-Up Notification", prefs.disableHeadsUpNotification,
                        { preferences.updateUniversal(prefs.copy(disableHeadsUpNotification = it)) },
                        "Disable Heads-Up Popup in notifications")
                    GbSwitchTile(Icons.Rounded.MarkChatUnread, "Disable Badge counter", prefs.disableBadgeCounter,
                        { preferences.updateUniversal(prefs.copy(disableBadgeCounter = it)) },
                        "Disables the messages counter on the icon in Home Screen/Launcher")
                    GbSwitchTile(Icons.Rounded.VolumeOff, "Disable Audio playing Notification", prefs.disableAudioPlayingNotification,
                        { preferences.updateUniversal(prefs.copy(disableAudioPlayingNotification = it)) },
                        "Remove the notification when playing voice notes/audio")
                    GbSwitchTile(Icons.Rounded.Forward, "Increase Forward limit", prefs.increaseForwardLimit,
                        { preferences.updateUniversal(prefs.copy(increaseForwardLimit = it)) },
                        "Forward messages up to 250 chats!")
                    GbSwitchTile(Icons.Rounded.Swipe, "Disable Swipe to exit conversation", prefs.disableSwipeToExit,
                        { preferences.updateUniversal(prefs.copy(disableSwipeToExitConversation = it)) },
                        "Prevents closing the conversation by swiping from left to right")
                    GbSwitchTile(Icons.Rounded.AccessTimeFilled, "Enable Always Online", prefs.enableAlwaysOnline,
                        { preferences.updateUniversal(prefs.copy(enableAlwaysOnline = it)) },
                        "Stay Always online, but don't close from Recents!")
                    GbSettingRow(
                        icon = Icons.Rounded.GifBox, title = "Tenor/Giphy GIF provider",
                        subtitle = prefs.gifProvider, hasSubScreen = true, onTap = { dialog = 3 },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.CleaningServices, title = "Clear Chaty Logs",
                        subtitle = if (calculating) "Calculating\u2026" else String.format(Locale.US, "%.1f MB", logBytes / (1024.0 * 1024.0)),
                        hasSubScreen = false,
                        onTap = {
                            scope.launch {
                                val freed = withContext(Dispatchers.IO) {
                                    var total = 0L
                                    try {
                                        context.cacheDir.walkTopDown().filter { it.isFile && isLogLike(it) }.toList().forEach {
                                            val len = it.length()
                                            if (it.delete()) total += len
                                        }
                                    } catch (_: Exception) {}
                                    total
                                }
                                refresh()
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar("Chaty debug logs & temp files cleared (" + String.format(Locale.US, "%.1f", freed / (1024.0 * 1024.0)) + " MB freed)")
                            }
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                GbCardContainer(headerText = "Image/Video Mods") {
                    GbSliderTile(
                        title = "Send Images in Full Resolution",
                        subtitle = "Images will be sent in Highest quality/resolution up to ${prefs.sendImagesFullResolutionMb}MB",
                        value = prefs.sendImagesFullResolutionMb.toFloat(),
                        min = 1f, max = 6f, divisions = 5, unit = "MB",
                        onChanged = { v -> preferences.updateUniversal(prefs.copy(sendImagesInFullResolutionMb = Math.round(v).toDouble())) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (dialog) {
        1 -> GbRadioSelectionModal(
            title = "Translate Option Settings", options = translateOptions,
            selectedOption = if (prefs.translateOption == "In-app") translateOptions[1] else translateOptions[0],
            onSelected = { v -> preferences.updateUniversal(prefs.copy(translateOptionSettings = if (v.startsWith("In-app")) "In-app" else "Server")) },
            onDismiss = { dialog = 0 },
        )
        2 -> GbRadioSelectionModal(
            title = "Default Translation Language", options = translationLanguages, selectedOption = prefs.translationLanguage,
            onSelected = { v -> preferences.updateUniversal(prefs.copy(defaultTranslationLanguage = v)) }, onDismiss = { dialog = 0 },
        )
        3 -> GbRadioSelectionModal(
            title = "GIF Provider", options = listOf("Tenor", "Giphy"), selectedOption = prefs.gifProvider,
            onSelected = { v -> preferences.updateUniversal(prefs.copy(tenorGiphyGifProvider = v)) }, onDismiss = { dialog = 0 },
        )
    }
}
