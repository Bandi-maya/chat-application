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
private enum class HomeSub { Header, Rows, Fab, Status }
private enum class UniversalSub { Colors, Styles, Icons, HideMedia, Backup, General }

val hostedSettingsDestinations: Set<SettingsDestination> = setOf(
    SettingsDestination.Privacy, SettingsDestination.Effects, SettingsDestination.Notifications,
    SettingsDestination.BackupRestore, SettingsDestination.Universal,
    SettingsDestination.Conversation, SettingsDestination.HomeScreen, SettingsDestination.Calls,
    SettingsDestination.Permissions, SettingsDestination.StorageMedia,
    SettingsDestination.TemplateStudio, SettingsDestination.ThemeEngine,
    SettingsDestination.HomeShortcuts, SettingsDestination.WasteCleaning,
)

/**
 * Hosts the settings pages whose Kotlin screens take only the preference and
 * theme controllers. Returns false for destinations that still have no host
 * (the caller keeps its honest "not hosted" state for those).
 */
@Composable
fun SettingsSubHost(
    destination: SettingsDestination,
    onBack: () -> Unit,
    onSwitch: (SettingsDestination) -> Unit = {},
): Boolean {
    val prefs = ServiceLocator.preferencesController
    val theme = ServiceLocator.themeController
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    when (destination) {
        SettingsDestination.Privacy -> PrivacyCenterScreen(preferences = prefs, onBack = onBack)
        SettingsDestination.Effects -> NavigationEffectsScreen(preferences = prefs, onBack = onBack)
        // Dart settings_root_screen '/settings/notifications' => NotificationSettingsPage (the toast-only screen is not
        // instantiated anywhere in Dart). Dynamic Island native bridge has no Kotlin implementation: bridge = null.
        SettingsDestination.Notifications -> com.example.chat.ui.settings.notifications.NotificationSettingsPage(
            preferences = prefs, onBack = onBack,
            bridge = com.example.chat.ui.island.AndroidDynamicIslandBridge(androidx.compose.ui.platform.LocalContext.current),
        )
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
                    appIconController = ServiceLocator.appIconController,
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
                    onOpenHeader = { sub = HomeSub.Header },
                    onOpenRows = { sub = HomeSub.Rows },
                    onOpenFab = { sub = HomeSub.Fab },
                    onOpenStatus = { sub = HomeSub.Status },
                )
                HomeSub.Header -> {
                    var overflow by remember { mutableStateOf<(() -> Unit)?>(null) }
                    val me = ServiceLocator.apiBackend.currentUser
                    HeaderSettingsScreen(
                        preferences = prefs,
                        navigationMode = theme.navigationMode,
                        onSetNavigationMode = { theme.setNavigationMode(it) },
                        currentUserDisplayName = me?.displayName,
                        currentUserUsername = me?.username,
                        currentUserAbout = me?.about,
                        headerPreview = { args -> GbLiveHeaderPreview(args, theme.globalTheme) },
                        showOverflowMenu = { onSettings -> overflow = onSettings },
                        onBack = { sub = null },
                    )
                    overflow?.let { onSettings ->
                        GbHeaderOverflowMenuDialog(
                            t = theme.globalTheme,
                            actions = GbHeaderOverflowActions(onSettings = onSettings),
                            onDismiss = { overflow = null },
                        )
                    }
                }
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
        SettingsDestination.ThemeEngine -> {
            // Dart file_picker/image_picker seams -> Activity Result launchers.
            var jsonWaiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<String?>?>(null) }
            var imageWaiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<java.io.File?>?>(null) }
            val jsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            ) { uri ->
                val text = try {
                    uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }
                } catch (_: Exception) { null }
                jsonWaiter?.complete(text); jsonWaiter = null
            }
            val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            ) { uri ->
                val file = try {
                    uri?.let {
                        val out = java.io.File(context.cacheDir, "theme_image_${System.currentTimeMillis()}")
                        context.contentResolver.openInputStream(it)?.use { input -> out.outputStream().use { o -> input.copyTo(o) } }
                        out
                    }
                } catch (_: Exception) { null }
                imageWaiter?.complete(file); imageWaiter = null
            }
            com.example.chat.ui.settings.theme.ThemeEditorScreen(
                themeController = theme,
                preferences = prefs,
                templateController = ServiceLocator.templateController,
                appearance = ServiceLocator.appearanceVariantController,
                exportDirectory = context.getExternalFilesDir(null) ?: context.filesDir,
                pickJsonText = {
                    val d = kotlinx.coroutines.CompletableDeferred<String?>()
                    jsonWaiter = d
                    jsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    d.await()
                },
                pickImageFile = {
                    val d = kotlinx.coroutines.CompletableDeferred<java.io.File?>()
                    imageWaiter = d
                    imageLauncher.launch("image/*")
                    d.await()
                },
                onBack = onBack,
            )
        }
        SettingsDestination.TemplateStudio -> {
            var component by remember { mutableStateOf<com.example.chat.core.templates.TemplateComponentType?>(null) }
            BackHandler(enabled = component != null) { component = null }
            val c = component
            if (c == null) {
                com.example.chat.ui.settings.templates.TemplatesSettingsScreen(
                    templateController = ServiceLocator.templateController,
                    preferences = prefs,
                    appearance = ServiceLocator.appearanceVariantController,
                    palette = theme.globalTheme,
                    templateRuntime = ServiceLocator.templateRuntime,
                    onBack = onBack,
                    onOpenComponent = { component = it },
                    onNotify = toast,
                )
            } else {
                com.example.chat.ui.settings.templates.ComponentOverrideScreen(
                    component = c,
                    templateController = ServiceLocator.templateController,
                    onBack = { component = null },
                    runtime = ServiceLocator.templateRuntime,
                    onMessage = toast,
                )
            }
        }
        // Dart openWasteCleaning / openHomeShortcuts: modal sheets over the settings root.
        SettingsDestination.WasteCleaning -> com.example.chat.ui.settings.gb.GbStandaloneSheet(onDismiss = onBack) {
            com.example.chat.ui.settings.gb.GbWasteCleaningSheetContent(onDismissSheet = onBack, toast = toast)
        }
        SettingsDestination.HomeShortcuts -> com.example.chat.ui.settings.gb.GbStandaloneSheet(onDismiss = onBack) {
            com.example.chat.ui.settings.gb.GbHomeShortcutsSheetContent(
                onDismissSheet = onBack,
                onOpenHome = { onSwitch(SettingsDestination.HomeScreen) },
            )
        }
        else -> return false
    }
    return true
}
