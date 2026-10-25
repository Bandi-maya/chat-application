package com.example.chat.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.chat.ServiceLocator
import com.example.chat.ui.settings.notifications.NotificationToastSettingsScreen
import com.example.chat.ui.settings.privacy.PrivacyCenterScreen
import com.example.chat.ui.settings.universal.*

private enum class UniversalSub { Colors, Styles, Icons, HideMedia, Backup, General }

val hostedSettingsDestinations: Set<SettingsDestination> = setOf(
    SettingsDestination.Privacy, SettingsDestination.Effects, SettingsDestination.Notifications,
    SettingsDestination.BackupRestore, SettingsDestination.Universal,
)

/**
 * Hosts the settings pages whose Kotlin screens take only the preference and
 * theme controllers. Returns false for destinations that still have no host
 * (the caller keeps its honest "not hosted" state for those).
 */
@Composable
fun SettingsSubHost(destination: SettingsDestination, onBack: () -> Unit): Boolean {
    val prefs = ServiceLocator.preferencesController
    val theme = ServiceLocator.themeController
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    when (destination) {
        SettingsDestination.Privacy -> PrivacyCenterScreen(preferences = prefs, onBack = onBack)
        SettingsDestination.Effects -> NavigationEffectsScreen(preferences = prefs, onBack = onBack)
        SettingsDestination.Notifications -> NotificationToastSettingsScreen(preferences = prefs, onBack = onBack)
        SettingsDestination.BackupRestore -> BackupAndRestoreScreen(
            preferences = prefs, onBack = onBack, onMessage = toast,
        )
        SettingsDestination.Universal -> {
            var sub by remember { mutableStateOf<UniversalSub?>(null) }
            val back = { if (sub == null) onBack() else sub = null }
            BackHandler { back() }
            when (sub) {
                null -> UniversalScreen(
                    preferences = prefs, onBack = onBack,
                    onOpenColors = { sub = UniversalSub.Colors },
                    onOpenStyles = { sub = UniversalSub.Styles },
                    onOpenChatyIcons = { sub = UniversalSub.Icons },
                    onOpenHideMedia = { sub = UniversalSub.HideMedia },
                    onOpenBackupRestore = { sub = UniversalSub.Backup },
                    onOpenGeneralSettings = { sub = UniversalSub.General },
                )
                UniversalSub.Colors -> UniversalColorsScreen(preferences = prefs, onBack = { sub = null })
                UniversalSub.Styles -> UniversalStylesScreen(
                    preferences = prefs, onBack = { sub = null }, themeController = theme, onMessage = toast,
                )
                UniversalSub.Icons -> UniversalChatyIconsScreen(preferences = prefs, onBack = { sub = null }, onMessage = toast)
                UniversalSub.HideMedia -> HideMediaScreen(preferences = prefs, themeController = theme, onBack = { sub = null })
                UniversalSub.Backup -> BackupAndRestoreScreen(preferences = prefs, onBack = { sub = null }, onMessage = toast)
                UniversalSub.General -> UniversalGeneralSettingsScreen(preferences = prefs, themeController = theme, onBack = { sub = null })
            }
        }
        else -> return false
    }
    return true
}
