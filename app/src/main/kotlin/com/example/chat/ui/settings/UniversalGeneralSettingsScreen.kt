package com.example.chat.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.theme.LocalAppColors
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
 * Port of universal_general_settings_screen.dart. Log size/clear walk the app
 * cache dir (Dart getTemporaryDirectory) with the same filename filter.
 * [onMessage] receives the snackbar text ("Chaty debug logs & temp files
 * cleared (X.X MB freed)").
 */
@Composable
fun UniversalGeneralSettingsScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    SettingsPage("General", null, onBack) {
        SettingsSection(title = null) {
            SettingsNavTile("\u25CE", "Translate Option Settings", "Choose how the translation icon works: ${prefs.translateOptionSettings}") { dialog = 1 }
            SettingsDivider()
            SettingsNavTile("\u25CE", "Default translation language", prefs.defaultTranslationLanguage) { dialog = 2 }
            SettingsDivider()
            SettingsSwitchTile("\u25AD", "Conversation Cards (multi Chats)", "Every chat you open will become a card, can switch from Recents easily", prefs.conversationCards) {
                preferences.updateUniversal(prefs.copy(conversationCards = it))
            }
            SettingsDivider()
            SettingsSwitchTile("\u2297", "Disable Heads-Up Notification", "Disable Heads-Up Popup in notifications", prefs.disableHeadsUpNotification) {
                preferences.updateUniversal(prefs.copy(disableHeadsUpNotification = it))
            }
            SettingsDivider()
            SettingsSwitchTile("\u2297", "Disable Badge counter", "Disables the messages counter on the icon in Home Screen/Launcher", prefs.disableBadgeCounter) {
                preferences.updateUniversal(prefs.copy(disableBadgeCounter = it))
            }
            SettingsDivider()
            SettingsSwitchTile("\u2297", "Disable Audio playing Notification", "Remove the notification when playing voice notes/audio", prefs.disableAudioPlayingNotification) {
                preferences.updateUniversal(prefs.copy(disableAudioPlayingNotification = it))
            }
            SettingsDivider()
            SettingsSwitchTile("\u2197", "Increase Forward limit", "Forward messages up to 250 chats!", prefs.increaseForwardLimit) {
                preferences.updateUniversal(prefs.copy(increaseForwardLimit = it))
            }
            SettingsDivider()
            SettingsSwitchTile("\u2297", "Disable Swipe to exit conversation", "Prevents closing the conversation by swiping from left to right", prefs.disableSwipeToExitConversation) {
                preferences.updateUniversal(prefs.copy(disableSwipeToExitConversation = it))
            }
            SettingsDivider()
            SettingsSwitchTile("\u25CF", "Enable Always Online", "Stay Always online, but don't close from Recents!", prefs.enableAlwaysOnline) {
                preferences.updateUniversal(prefs.copy(enableAlwaysOnline = it))
            }
            SettingsDivider()
            SettingsNavTile("GIF", "Tenor/Giphy GIF provider", prefs.tenorGiphyGifProvider) { dialog = 3 }
            SettingsDivider()
            SettingsNavTile(
                "\u2702", "Clear Chaty Logs",
                if (calculating) "Calculating\u2026" else String.format(Locale.US, "%.1f MB", logBytes / (1024.0 * 1024.0)),
            ) {
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
                    onMessage("Chaty debug logs & temp files cleared (" + String.format(Locale.US, "%.1f", freed / (1024.0 * 1024.0)) + " MB freed)")
                }
            }
        }
        SettingsSection(title = "Image/Video Mods") {
            SettingsSliderTile(
                "\u25A4", "Send Images in Full Resolution",
                prefs.sendImagesInFullResolutionMb.coerceIn(1.0, 6.0), 1.0, 6.0, 5,
                { "${Math.round(it)}MB" },
            ) { v -> preferences.updateUniversal(prefs.copy(sendImagesInFullResolutionMb = Math.round(v).toDouble())) }
            Text(
                "Images will be sent in Highest quality/resolution up to ${prefs.sendImagesFullResolutionMb}MB",
                color = colors.foregroundSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }

    when (dialog) {
        1 -> SettingsRadioDialog(
            "Translate Option Settings", translateOptions,
            if (prefs.translateOption == "In-app") translateOptions[1] else translateOptions[0],
            { v -> preferences.updateUniversal(prefs.copy(translateOptionSettings = if (v.startsWith("In-app")) "In-app" else "Server")) },
            { dialog = 0 },
        )
        2 -> SettingsRadioDialog(
            "Default Translation Language", translationLanguages, prefs.defaultTranslationLanguage,
            { v -> preferences.updateUniversal(prefs.copy(defaultTranslationLanguage = v)) }, { dialog = 0 },
        )
        3 -> SettingsRadioDialog(
            "GIF Provider", listOf("Tenor", "Giphy"), prefs.tenorGiphyGifProvider,
            { v -> preferences.updateUniversal(prefs.copy(tenorGiphyGifProvider = v)) }, { dialog = 0 },
        )
    }
}
