package com.example.chat.ui.settings.notifications

import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.KeyboardAlt
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.OnlinePrediction
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSliderTile
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Android bridge for Dart's MethodChannel('chaty/dynamic_island'). The native side is NOT ported by this
 * screen: the integration owner must implement it (or pass null, which reproduces Dart's
 * MissingPluginException path). Every method may throw [DynamicIslandException] (= PlatformException).
 */
class DynamicIslandException(message: String?) : Exception(message)

interface DynamicIslandBridge {
    /** getPermissionState -> map with "overlay" and "notificationAccess". */
    suspend fun getPermissionState(): Map<String, Boolean>?
    suspend fun setEnabled(enabled: Boolean)
    suspend fun openNotificationAccessSettings()
    suspend fun openOverlaySettings()
    suspend fun showPreview()
    suspend fun openNotificationChannelSettings()
}

/** Dart ChatyNotificationService.triggerEventNotification fallback used by the island preview. */
typealias TriggerEventNotification = (title: String, body: String, accentColor: Long) -> Unit

/** Dart _buildToastPreview. UNCOMPILED (Compose). */
@Composable
private fun ToastPreview(text: String, hasAvatar: Boolean, hasElevation: Boolean, radius: Double, bgColor: Long?, textColor: Long?, defaultBg: Color) {
    val bg = bgColor?.let { Color(it) } ?: defaultBg
    val fg = textColor?.let { Color(it) } ?: Color.White
    val shape = RoundedCornerShape(radius.toFloat().dp)
    Row(
        Modifier.let { if (hasElevation) it.shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)) else it }
            .background(bg, shape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasAvatar) {
            Box(Modifier.size(24.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.W600, fontSize = 13.sp)
    }
}

/**
 * Port of NotificationSettingsPage (ChatySettingsPage layout). Dart aliases are mapped to Kotlin real field
 * names. Dynamic Island native calls go through [bridge] and the event-toast fallback through
 * [triggerEventNotification]; both are owner wiring. The permission dialog result is awaited like Dart's showDialog.
 * UNCOMPILED (Compose), no pixel check; Material TextButton/OutlinedButton styling is not yet compared to Flutter's.
 */
@Composable
fun NotificationSettingsPage(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    bridge: DynamicIslandBridge? = null,
    triggerEventNotification: TriggerEventNotification = { _, _, _ -> },
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val notif = preferences.notification
    val p = preferences.toasts
    val pickers = rememberGbPickers()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationAccessGranted by remember { mutableStateOf(false) }
    var dialogFor by remember { mutableStateOf<Pair<Boolean, CompletableDeferred<Boolean>>?>(null) }
    val recordingAlert = preferences.gbBool("notify_recording_started", false)
    val isIslandEnabled = preferences.gbBool("dynamic_island_notifications", false)

    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }
    fun showPositionModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
        "Position of Toast", listOf("Top of the screen", "Center of screen", "Bottom of screen"), current, onSelected = onSelected,
    )
    fun showRingtoneModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
        "Ringtone", listOf("Default Chaty Tone", "Soft Ping", "Pop Bubble", "Chime Bell", "Silent"), current, onSelected = onSelected,
    )

    suspend fun refreshPermissions() {
        try {
            val state = bridge?.getPermissionState() ?: return
            overlayGranted = state["overlay"] == true
            notificationAccessGranted = state["notificationAccess"] == true
        } catch (_: DynamicIslandException) {
        }
    }
    LaunchedEffect(bridge) { refreshPermissions() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, bridge) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) scope.launch { refreshPermissions() } }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Dart _openDynamicIslandPermission: await the dialog, then (if accepted) open system settings.
    suspend fun openPermission(notificationAccess: Boolean) {
        val result = CompletableDeferred<Boolean>()
        dialogFor = notificationAccess to result
        val accepted = result.await()
        dialogFor = null
        if (!accepted) return
        val b = bridge
        if (b == null) {
            snackbar.showSnackbar("System-wide Dynamic Island is available on Android only.")
            return
        }
        try {
            if (notificationAccess) b.openNotificationAccessSettings() else b.openOverlaySettings()
        } catch (error: DynamicIslandException) {
            snackbar.showSnackbar("Could not open Android settings: ${error.message ?: "Please open Settings manually."}")
        }
    }

    fun fallbackPreview() {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        triggerEventNotification("Dynamic Island preview", "New message \u00B7 Tap the pill to expand", config.accentColor)
    }
    // Dart _previewDynamicIsland, same order: permission dialog awaited, state re-read, then system preview or fallback.
    suspend fun previewDynamicIsland() {
        if (!overlayGranted) {
            openPermission(false)
            if (!overlayGranted) {
                fallbackPreview()
                return
            }
        }
        try {
            if (overlayGranted && bridge != null) {
                bridge.showPreview()
                return
            }
        } catch (e: DynamicIslandException) {
            snackbar.showSnackbar("Could not show system preview: ${e.message ?: "Unknown error"}")
        }
        fallbackPreview()
    }
    suspend fun setDynamicIslandEnabled(value: Boolean) {
        preferences.updateGbFeature("dynamic_island_notifications", value, "Dynamic Island Notifications")
        try {
            bridge?.setEnabled(value)
            if (value && !notificationAccessGranted) openPermission(true)
            else if (value && !overlayGranted) openPermission(false)
        } catch (_: DynamicIslandException) {
        }
    }

    val missing = !notificationAccessGranted || !overlayGranted
    val chipTone = when { !isIslandEnabled -> colors.foregroundSecondary; missing -> colors.warning; else -> colors.success }

    dialogFor?.let { (na, result) ->
        AlertDialog(
            onDismissRequest = { result.complete(false) },
            title = { Text(if (na) "Allow notification access" else "Allow display over other apps") },
            text = { Text(if (na) "Android lets Chaty read notification content only after you explicitly enable Chaty Dynamic Island in Notification access settings. This is required to show notifications from other apps." else "Android requires display-over-other-apps access so the island can float above your current app. Chaty only draws the temporary notification pill.") },
            dismissButton = { TextButton(onClick = { result.complete(false) }) { Text("Not now") } },
            confirmButton = { Button(onClick = { result.complete(true) }) { Text("Open Settings") } },
        )
    }

    Box(Modifier.fillMaxWidth()) {
        ChatySettingsPage(
            title = "Notifications",
            subtitle = "In-app alerts, realtime contact events & toast notification customizations",
            onBack = onBack,
            trailingHeaderWidget = {
                Row(
                    Modifier.clickable {
                        when {
                            !isIslandEnabled -> scope.launch { setDynamicIslandEnabled(true) }
                            !notificationAccessGranted -> scope.launch { openPermission(true) }
                            !overlayGranted -> scope.launch { openPermission(false) }
                            else -> scope.launch { previewDynamicIsland() }
                        }
                    }
                        .background(if (!isIslandEnabled) colors.surface else chipTone.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .border(1.dp, if (!isIslandEnabled) colors.borderSubtle else chipTone, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(chipTone, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            !isIslandEnabled -> "Island Off"
                            !notificationAccessGranted && !overlayGranted -> "Missing Perms"
                            !notificationAccessGranted -> "No Access"
                            !overlayGranted -> "No Overlay"
                            else -> "Island Ready"
                        },
                        fontSize = 11.sp, fontWeight = FontWeight.W700, color = chipTone,
                    )
                }
            },
        ) {
            Column {
            ChatySettingsSection(
                title = "In-app event notifications",
                description = "Display non-intrusive toasts for incoming live state transitions without interrupting chats.",
            ) {
                Column {
                ChatySwitchTile(
                    icon = Icons.Rounded.NotificationsActive, iconColor = colors.primary, title = "Enable in-app notifications", subtitle = "Allow Chaty to show realtime event banners while the app is open", value = notif.enableGlobalNotifications,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(enableGlobalNotifications = it), "In-App Notifications") },
                )
                ChatySwitchTile(
                    icon = Icons.Rounded.AccountCircle, title = "Show sender avatar", subtitle = "Display user avatar icon in event toast notifications", value = notif.showSenderAvatar,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(showSenderAvatar = it), "Notification Avatar") },
                )
                ChatySwitchTile(
                    icon = Icons.Rounded.Badge, title = "Show sender name", subtitle = "Include the contact name in the toast title", value = notif.showSenderName,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(showSenderName = it), "Notification Name") },
                )
                ChatySwitchTile(
                    icon = Icons.Rounded.Subtitles, title = "Show event detail", subtitle = "Include the online, typing or recording detail text", value = notif.showMessagePreview,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(showMessagePreview = it), "Show Message Preview") },
                )
                }
            }
            ChatySettingsSection(
                title = "Dynamic Island notifications",
                description = "Show notifications from across your phone in a floating island. Android requires two explicit permissions; you can revoke either at any time in system settings.",
            ) {
                Column {
                    ChatySwitchTile(
                        icon = Icons.Rounded.MotionPhotosOn, iconColor = colors.primary, title = "Enable Dynamic Island",
                        subtitle = "Turn on the island for supported notifications across apps", value = isIslandEnabled,
                        onChanged = { scope.launch { setDynamicIslandEnabled(it) } },
                    )
                    Column(
                        Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        PermissionRow(
                            granted = notificationAccessGranted, idleIcon = Icons.Rounded.NotificationsActive,
                            title = "Notification access",
                            subtitle = if (notificationAccessGranted) "Granted \u00B7 system notifications can be read" else "Required \u00B7 allow Chaty in Android Settings",
                            onClick = { scope.launch { openPermission(true) } },
                        )
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
                        Spacer(Modifier.height(10.dp))
                        PermissionRow(
                            granted = overlayGranted, idleIcon = Icons.Rounded.Layers,
                            title = "Display over other apps",
                            subtitle = if (overlayGranted) "Granted \u00B7 island can float above apps" else "Required \u00B7 permit the floating pill",
                            onClick = { scope.launch { openPermission(false) } },
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { scope.launch { refreshPermissions() } }) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh permission status")
                        }
                    }
                    Row(Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth()) {
                        OutlinedButton(onClick = { scope.launch { previewDynamicIsland() } }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PlayCircleOutline, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Preview island + tone")
                        }
                    }
                    Text(
                        "The island maintains a persistent multi-item notification stack. Tap the pill or swipe down to expand all messages. Swipe horizontally to dismiss an individual notification, or tap Clear All to clear the stack. Collapsing the island never deletes your messages.",
                        color = colors.foregroundSecondary, fontSize = 12.sp, lineHeight = 16.2.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(14.dp)).border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reduce duplicate heads-up banners", fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.foreground)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("To make Dynamic Island your preferred preview without losing notification history, Android lets you set notifications to \"Silent\" or disable \"Pop on screen\" in the source app channel settings. Tap below to manage settings for Chaty or other apps.", fontSize = 11.5.sp, color = colors.foregroundSecondary, lineHeight = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { scope.launch { try { bridge?.openNotificationChannelSettings() } catch (_: DynamicIslandException) {} } }) {
                                Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("App Notification Settings")
                            }
                        }
                    }
                }
            }
            ChatySettingsSection(
                title = "Realtime contact events",
                description = "These alerts are generated from Supabase Realtime state changes, not simulations.",
            ) {
                Column {
                ChatySwitchTile(
                    icon = Icons.Rounded.OnlinePrediction, iconColor = colors.success, title = "Contact online alert", subtitle = "Show a toast when a conversation contact changes from offline to online", value = notif.notifyContactOnline,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(notifyContactOnline = it), "Contact Online Alert") },
                )
                ChatySwitchTile(
                    icon = Icons.Outlined.KeyboardAlt, iconColor = colors.info, title = "Typing alert", subtitle = "Show a toast when a contact starts typing", value = notif.notifyTypingStarted,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(notifyTypingStarted = it), "Typing Alert") },
                )
                ChatySwitchTile(
                    icon = Icons.Rounded.MicNone, iconColor = colors.error, title = "Recording alert", subtitle = "Show a toast when a contact starts recording a voice message", value = recordingAlert,
                    onChanged = { preferences.updateGbFeature("notify_recording_started", it, "Recording Alert") },
                )
                ChatySwitchTile(
                    icon = Icons.Rounded.Visibility, iconColor = colors.accent, title = "Status / story viewed alert", subtitle = "Notify when the backend records a contact viewing your story", value = notif.notifyStatusViewed,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(notifyStatusViewed = it), "Status Viewed Alert") },
                )
                ChatySwitchTile(
                    icon = Icons.Rounded.DeleteSweep, iconColor = colors.error, title = "Message revoked alert", subtitle = "Notify when a contact deletes a message", value = notif.notifyMessageDeleted,
                    onChanged = { preferences.updateNotification(preferences.notification.copy(notifyMessageDeleted = it), "Message Revoked Alert") },
                )
                }
            }
            GbCardContainer(headerText = "Contact Online Toast Customization") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Online", p.onlineWithProfilePic, p.onlineElevation, p.onlineRadius, p.onlineBgColor, p.onlineTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show a notification on the phone's status bar", value = p.onlineInNotificationBar,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastInNotificationBar = it), "Online In Notification Bar") },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.ToggleOn, title = "Disable Contact Online Toast", subtitle = "Turn on to disable toast notifications", value = p.disableContactOnlineToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastEnabled = !it), "Disable Contact Online Toast") },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to toast message", value = p.onlineWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastWithProfilePicture = it), "Online With Profile Pic") },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.FilterNone, title = "Elevation", subtitle = if (p.onlineElevation) "Enabled" else "Disabled", value = p.onlineElevation,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastElevation = it), "Online Toast Elevation") },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Rounded corners radius", value = p.onlineRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastRadius = it.toDouble()), "Online Toast Radius") },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.MusicNote, title = "Ringtone", subtitle = p.onlineRingtone, hasSubScreen = true,
                        onTap = { showRingtoneModal(p.onlineRingtone) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastRingtone = v), "Online Toast Ringtone") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.onlinePosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.onlinePosition) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastPosition = v), "Online Toast Position") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.onlineBgColor,
                        onColorTap = { pickColor("Toast Background Color", p.onlineBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastBgColor = c), "Online Toast Bg Color") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.onlineTextColor,
                        onColorTap = { pickColor("Toast Text Color", p.onlineTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastTextColor = c), "Online Toast Text Color") } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer(headerText = "Viewed Story Toast Customization") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Status", p.storyWithProfilePic, p.storyElevation, p.storyRadius, p.storyBgColor, p.storyTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show in status bar", value = p.storyInNotificationBar,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastInNotificationBar = it), "Story In Notification Bar") },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.HistoryEdu, title = "Viewed Story Toast", subtitle = "Know immediately when someone views your status via Toast message", value = p.viewedStoryToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(viewedStoryToast = it), "Viewed Story Toast") },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to status toast", value = p.storyWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastWithProfilePicture = it), "Story With Profile Pic") },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Corner curvature", value = p.storyRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastRadius = it.toDouble()), "Story Toast Radius") },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.storyPosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.storyPosition) { v -> preferences.updateToasts(preferences.toasts.copy(storyToastPosition = v), "Story Toast Position") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.storyBgColor,
                        onColorTap = { pickColor("Story Toast Background Color", p.storyBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastBgColor = c), "Story Toast Bg Color") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.storyTextColor,
                        onColorTap = { pickColor("Story Toast Text Color", p.storyTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastTextColor = c), "Story Toast Text Color") } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer(headerText = "Profile Toast Customization") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Profile", p.profileWithProfilePic, p.profileElevation, p.profileRadius, p.profileBgColor, p.profileTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.AccountBox, title = "Profile Toast", subtitle = "Know immediately when anyone views your profile via a Toast message", value = p.profileToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastEnabled = it), "Profile Toast") },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar", value = p.profileWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastWithProfilePicture = it), "Profile With Profile Pic") },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Corner curvature", value = p.profileRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastRadius = it.toDouble()), "Profile Toast Radius") },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.profilePosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.profilePosition) { v -> preferences.updateToasts(preferences.toasts.copy(profileToastPosition = v), "Profile Toast Position") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.profileBgColor,
                        onColorTap = { pickColor("Profile Toast Background Color", p.profileBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastBgColor = c), "Profile Toast Bg Color") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.profileTextColor,
                        onColorTap = { pickColor("Profile Toast Text Color", p.profileTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastTextColor = c), "Profile Toast Text Color") } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer(headerText = "Typing Toast Customization") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Typing...", p.typingWithProfilePic, p.typingElevation, p.typingRadius, p.typingBgColor, p.typingTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.Keyboard, title = "Typing Toast", subtitle = "Know immediately when anyone starts typing to you via a floating toast message", value = p.typingToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastEnabled = it), "Typing Toast") },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to typing alert", value = p.typingWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastWithProfilePicture = it), "Typing With Profile Pic") },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Corner curvature", value = p.typingRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastRadius = it.toDouble()), "Typing Toast Radius") },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.typingPosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.typingPosition) { v -> preferences.updateToasts(preferences.toasts.copy(typingToastPosition = v), "Typing Toast Position") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.typingBgColor,
                        onColorTap = { pickColor("Typing Toast Background Color", p.typingBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastBgColor = c), "Typing Toast Bg Color") } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.typingTextColor,
                        onColorTap = { pickColor("Typing Toast Text Color", p.typingTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastTextColor = c), "Typing Toast Text Color") } },
                    )
                }
            }

            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    pickers.Host()
}

@Composable
private fun PermissionRow(granted: Boolean, idleIcon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else idleIcon, null,
            tint = if (granted) colors.success else colors.primary, modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.W700)
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
        TextButton(onClick = onClick) { Text(if (granted) "Review" else "Allow") }
    }
}
