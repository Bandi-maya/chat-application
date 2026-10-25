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
import com.example.chat.ui.settings.conversation.*
import com.example.chat.ui.settings.home.*
import kotlinx.coroutines.launch
import com.example.chat.ui.settings.universal.*

private enum class ConvSub { ActionBar, Bubbles, Entry, More }
private enum class HomeSub { Rows, Fab, Status }
private enum class UniversalSub { Colors, Styles, Icons, HideMedia, Backup, General }

val hostedSettingsDestinations: Set<SettingsDestination> = setOf(
    SettingsDestination.Privacy, SettingsDestination.Effects, SettingsDestination.Notifications,
    SettingsDestination.BackupRestore, SettingsDestination.Universal,
    SettingsDestination.Conversation, SettingsDestination.HomeScreen, SettingsDestination.Calls,
    SettingsDestination.Permissions, SettingsDestination.StorageMedia,
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
        SettingsDestination.Conversation -> {
            var sub by remember { mutableStateOf<ConvSub?>(null) }
            BackHandler(enabled = sub != null) { sub = null }
            when (sub) {
                null -> ConversationSettingsPage(
                    preferences = prefs, onBack = onBack,
                    onOpenActionBar = { sub = ConvSub.ActionBar },
                    onOpenBubbleAndTicks = { sub = ConvSub.Bubbles },
                    onOpenEntryStyle = { sub = ConvSub.Entry },
                    onOpenMoreOptions = { sub = ConvSub.More },
                )
                ConvSub.ActionBar -> ConversationActionBarScreen(preferences = prefs, onBack = { sub = null })
                ConvSub.Bubbles -> ConversationBubbleAndTicksScreen(preferences = prefs, onBack = { sub = null })
                ConvSub.Entry -> ConversationEntryStyleScreen(preferences = prefs, onBack = { sub = null })
                ConvSub.More -> ConversationMoreOptionsScreen(preferences = prefs, onBack = { sub = null })
            }
        }
        SettingsDestination.HomeScreen -> {
            var sub by remember { mutableStateOf<HomeSub?>(null) }
            BackHandler(enabled = sub != null) { sub = null }
            when (sub) {
                null -> HomeScreenSettingsPage(
                    preferences = prefs, onBack = onBack,
                    // Header settings need the live header preview + overflow menu seams; not hosted yet.
                    onOpenHeader = { toast("Header settings are not hosted in this build yet.") },
                    onOpenRows = { sub = HomeSub.Rows },
                    onOpenFab = { sub = HomeSub.Fab },
                    onOpenStatus = { sub = HomeSub.Status },
                )
                HomeSub.Rows -> HomeRowsSettingsScreen(preferences = prefs, onBack = { sub = null })
                HomeSub.Fab -> HomeFabSettingsScreen(preferences = prefs, onBack = { sub = null })
                HomeSub.Status -> StatusSettingsScreen(preferences = prefs, onBack = { sub = null })
            }
        }
        SettingsDestination.Calls -> {
            val store = ServiceLocator.callPresentationPrefs
            var rev by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            androidx.compose.runtime.DisposableEffect(store) {
                val l: () -> Unit = { rev++ }
                store.addListener(l)
                onDispose { store.removeListener(l) }
            }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var audio by remember { mutableStateOf(false) }
            BackHandler(enabled = audio) { audio = false }
            @Suppress("UNUSED_EXPRESSION") rev
            if (audio) {
                com.example.chat.ui.settings.AudioSettingsScreen(
                    settings = ServiceLocator.audioSettingsController,
                    capability = ServiceLocator.audioCapabilityService,
                    coordinator = ServiceLocator.audioPolicyCoordinator,
                    scope = scope,
                    onBack = { audio = false },
                )
            } else {
                val v = store.value
                fun run(block: suspend () -> Unit) {
                    scope.launch { try { block() } catch (e: Exception) { toast(e.message ?: "Unable to save.") } }
                }
                CallSettingsScreen(
                    preferences = prefs,
                    call = CallPresentationState(
                        dynamicIslandEnabled = v.dynamicIslandEnabled,
                        pictureInPictureEnabled = v.pictureInPictureEnabled,
                        lowDataUsageEnabled = v.lowDataUsageEnabled,
                        incomingCallTemplate = v.incomingCallTemplate,
                        ongoingCallTemplate = v.ongoingCallTemplate,
                    ),
                    templates = com.example.chat.ui.calls.CallScreenTemplate.all.map { CallTemplateOption(it.wireName, it.displayName) },
                    contacts = ServiceLocator.apiBackend.users.map { CallContactOption(it.id, it.displayName) },
                    onBack = onBack,
                    onIncomingTemplate = { t -> run { store.setIncomingCallTemplate(t) } },
                    onOngoingTemplate = { t -> run { store.setOutgoingCallTemplate(t) } },
                    onDynamicIsland = { b -> run { store.setDynamicIslandEnabled(b) } },
                    onPictureInPicture = { b -> run { store.setPictureInPictureEnabled(b) } },
                    onLowData = { b -> run { store.setLowDataUsageEnabled(b) } },
                    onOpenAudioSettings = { audio = true },
                )
            }
        }
        SettingsDestination.Permissions -> com.example.chat.ui.settings.permissions.SystemPermissionsScreen(
            onBack = onBack,
            triggerTestNotification = {
                ServiceLocator.notifications.triggerEventNotification(
                    title = "Chaty", body = "Test notification",
                )
            },
            canUseBiometrics = { ServiceLocator.localLockService.canUseBiometrics() },
            authenticateBiometric = { reason -> ServiceLocator.localLockService.authenticateBiometric(reason) },
        )
        // Dart clear-cache action is not ported; the toggles on this page are static in the Kotlin port.
        SettingsDestination.StorageMedia -> StorageMediaScreen(
            onBack = onBack,
            onClearCache = { toast("Clearing the local media cache is not wired in this build yet.") },
        )
        else -> return false
    }
    return true
}
