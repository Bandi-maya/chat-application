package com.example.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.theme.ChatyAsyncImage
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Route keys for the root rows; AppRoot maps each to a destination. */
enum class SettingsDestination {
    LinkedDevices, Privacy, Security, ThemeEngine, TemplateStudio, HomeScreen,
    Conversation, Effects, Universal, HomeShortcuts, Notifications, Automation,
    Calls, StorageMedia, Permissions, BackupRestore, WasteCleaning, About,
    PrivacyPolicy, ShareChaty,
    EditProfile, SearchSettings, QrCode,
}

/** Dart ProfileMediaSource. */
enum class ProfileMediaSource { camera, gallery }

private data class RootRow(val dest: SettingsDestination, val icon: ImageVector, val tint: @Composable () -> Color, val title: String, val subtitle: String)
private data class RootGroup(val title: String, val rows: List<RootRow>)

/** Same 6 groups, order, icons, colors and copy as settings_root_screen.dart (L507-720). */
private val rootGroups: List<RootGroup> = listOf(
    RootGroup("Devices", listOf(
        RootRow(SettingsDestination.LinkedDevices, Icons.Rounded.Devices, { LocalAppColors.current.foregroundSecondary }, "Linked Devices", "Use Chaty on web and desktop, view active sessions"),
    )),
    RootGroup("Privacy and security", listOf(
        RootRow(SettingsDestination.Privacy, Icons.Rounded.Security, { LocalAppColors.current.info }, "Privacy Center", "Last seen, online presence, anti-delete, read receipts & stealth"),
        RootRow(SettingsDestination.Security, Icons.Outlined.Lock, { LocalAppColors.current.success }, "Security & App Lock", "Biometric, PIN, Pattern protection & session security"),
    )),
    RootGroup("Look and feel", listOf(
        RootRow(SettingsDestination.ThemeEngine, Icons.Outlined.ColorLens, { LocalAppColors.current.accent }, "Theme & Color Engine", "13+ dark & light theme presets, token editor & palette"),
        RootRow(SettingsDestination.TemplateStudio, Icons.Rounded.DashboardCustomize, { LocalAppColors.current.info }, "Global Template Studio", "App-wide templates and independent component styles"),
        RootRow(SettingsDestination.HomeScreen, Icons.Outlined.Home, { LocalAppColors.current.accent }, "Home Screen", "Header, chat rows, floating button & status"),
        RootRow(SettingsDestination.Conversation, Icons.Outlined.ChatBubbleOutline, { LocalAppColors.current.success }, "Conversation Customization", "Bubbles, ticks, contact sidebar & message appearance"),
        RootRow(SettingsDestination.Effects, Icons.Outlined.TouchApp, { LocalAppColors.current.warning }, "Navigation & Touch Effects", "Interactive click particles (Stars/Hearts/Bubbles), Sound FX"),
        RootRow(SettingsDestination.Universal, Icons.Outlined.Palette, { LocalAppColors.current.accent }, "Universal", "Colors, icons and fonts, language & other"),
        RootRow(SettingsDestination.HomeShortcuts, Icons.Outlined.Widgets, { LocalAppColors.current.accent }, "Home shortcuts", "Configure in-app home layout and shortcuts"),
    )),
    RootGroup("Messages and calls", listOf(
        RootRow(SettingsDestination.Notifications, Icons.Outlined.NotificationsNone, { LocalAppColors.current.warning }, "Notifications & Alerts", "Heads-up popups, notification sounds, LED & vibration"),
        RootRow(SettingsDestination.Automation, Icons.Outlined.SmartToy, { LocalAppColors.current.info }, "Message Automation & Scheduler", "Auto-reply rules, auto-responder bot & message scheduler"),
        RootRow(SettingsDestination.Calls, Icons.Outlined.Call, { LocalAppColors.current.warning }, "Call Settings", "Call privacy, incoming call controls & call appearance"),
    )),
    RootGroup("Storage and system", listOf(
        RootRow(SettingsDestination.StorageMedia, Icons.Rounded.Storage, { LocalAppColors.current.accent }, "Storage & Media", "Auto-download quality, media limits & cache retention"),
        RootRow(SettingsDestination.Permissions, Icons.Outlined.AdminPanelSettings, { LocalAppColors.current.primary }, "System Permissions & Hardware", "Camera, microphone, storage, battery & OS access"),
        RootRow(SettingsDestination.BackupRestore, Icons.Outlined.Backup, { LocalAppColors.current.info }, "Backup and restore", "Export and restore Chaty settings safely"),
        RootRow(SettingsDestination.WasteCleaning, Icons.Outlined.CleaningServices, { LocalAppColors.current.foregroundSecondary }, "Waste cleaning", "Remove expired generated image cache files"),
    )),
    RootGroup("About", listOf(
        RootRow(SettingsDestination.About, Icons.Outlined.Info, { LocalAppColors.current.foregroundSecondary }, "About Chaty", "App version, build information and licenses"),
        RootRow(SettingsDestination.PrivacyPolicy, Icons.Outlined.Shield, { LocalAppColors.current.foregroundSecondary }, "Privacy Policy", "Read our zero-telemetry security & privacy policy"),
        RootRow(SettingsDestination.ShareChaty, Icons.Rounded.Share, { LocalAppColors.current.foregroundSecondary }, "Share Chaty", "Invite friends and family to join Chaty"),
    )),
)

/**
 * Port of lib/features/settings/settings_root_screen.dart. About and Privacy Policy are in-screen dialogs
 * (About -> [SettingsDestination.About] and PrivacyPolicy keys are handled here, never forwarded). Avatar and
 * banner changes: [onPickAvatar]/[onPickBanner] must do ProfileMediaService.upload* + dataStore.updateUser and
 * throw on failure (a message containing "cancelled" is swallowed, like Dart). Logout: [onLogout]
 * (confirmChatyLogout). Uncompiled; no pixel check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRootScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    user: UserProfile,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onPickAvatar: suspend (ProfileMediaSource) -> Unit,
    onPickBanner: suspend (ProfileMediaSource) -> Unit,
    onLogout: () -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val theme = themeController.globalTheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf("") }          // "", "about", "policy"
    var pickTarget by remember { mutableStateOf("") }      // "", "avatar", "banner"
    val override = preferences.home.myNameOverride.trim()
    val effectiveOverride = if (override.isNotEmpty() && override != "Alex Rivera" && override != "Bandi Maya") override else ""
    val rawName = user.displayName.trim()
    val displayName = when {
        effectiveOverride.isNotEmpty() -> effectiveOverride
        rawName.isNotEmpty() && rawName != "Bandi Maya" && rawName != "Chaty User" -> rawName
        user.username.isNotEmpty() -> user.username
        else -> "User"
    }
    val handle = if (user.username.isNotEmpty()) "@${user.username}" else ""
    val accent = Color(theme.accentColor)

    fun toast(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    fun runPick(avatar: Boolean, source: ProfileMediaSource) {
        scope.launch {
            try {
                if (avatar) onPickAvatar(source) else onPickBanner(source)
                toast(if (avatar) "Profile photo updated." else "Banner updated.")
            } catch (e: Exception) {
                if (e.toString().contains("cancelled")) return@launch
                toast(if (avatar) "Could not update profile photo: $e" else "Could not update banner: $e")
            }
        }
    }
    fun open(d: SettingsDestination) {
        when (d) {
            SettingsDestination.About -> dialog = "about"
            SettingsDestination.PrivacyPolicy -> dialog = "policy"
            SettingsDestination.ShareChaty -> ChatyShareService.shareApp(context) { toast(it) }
            else -> onNavigate(d)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBack) Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) } else Spacer(Modifier.width(16.dp))
                Text("Settings", Modifier.weight(1f), color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = { onNavigate(SettingsDestination.EditProfile) }) { Icon(Icons.Outlined.Edit, "Edit profile", Modifier.size(24.dp), tint = colors.foreground) }
                IconButton(onClick = { onNavigate(SettingsDestination.SearchSettings) }) { Icon(Icons.Rounded.Search, "Search settings", Modifier.size(24.dp), tint = colors.foreground) }
                IconButton(onClick = { onNavigate(SettingsDestination.QrCode) }) { Icon(Icons.Rounded.QrCode2, "QR code", Modifier.size(25.dp), tint = colors.foreground) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val bannerHeight = ((LocalConfiguration.current.screenWidthDp - 32) * 9f / 16f).dp
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().height(bannerHeight)
                                .background(colors.surface, RoundedCornerShape(20.dp))
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(19.dp)),
                        ) {
                            val banner = user.bannerUrl?.trim().orEmpty()
                            if (banner.isEmpty()) {
                                Box(
                                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), colors.surface, colors.background))),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Outlined.Wallpaper, null, Modifier.size(34.dp), tint = accent.copy(alpha = 0.65f)) }
                            } else {
                                val model = if (banner.startsWith("http://") || banner.startsWith("https://")) banner else "file://" + banner.removePrefix("file://")
                                val fallback: @Composable () -> Unit = {
                                    Box(Modifier.fillMaxSize().background(colors.surfaceElevated), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Wallpaper, null, Modifier.size(30.dp), tint = colors.foregroundSecondary)
                                    }
                                }
                                ChatyAsyncImage(model, "Account banner", Modifier.fillMaxSize(), placeholder = fallback, error = fallback)
                            }
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(10.dp).size(40.dp).clip(CircleShape)
                                .background(colors.surface.copy(alpha = 0.92f)).clickable { pickTarget = "banner" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Wallpaper, "Change account banner", Modifier.size(19.dp), tint = colors.foreground) }
                        // Dart Positioned(bottom: -40): 4dp padding ring around an 84dp avatar.
                        Box(
                            Modifier.offset(y = 40.dp).size(92.dp)
                                .background(colors.background, CircleShape)
                                .border(1.dp, colors.borderSubtle, CircleShape)
                                .padding(4.dp).clip(CircleShape)
                                .clickable { pickTarget = "avatar" },
                            contentAlignment = Alignment.Center,
                        ) {
                            val fallback: @Composable () -> Unit = {
                                Box(Modifier.fillMaxSize().background(colors.surfaceElevated), contentAlignment = Alignment.Center) {
                                    Text(if (user.avatarInitials.isNotEmpty()) user.avatarInitials else "BM", color = colors.foreground, fontSize = (84 * 0.46f).sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!user.avatarUrl.isNullOrEmpty()) ChatyAsyncImage(user.avatarUrl, "Change profile photo", Modifier.fillMaxSize(), error = fallback)
                            else fallback()
                        }
                    }
                    Spacer(Modifier.height(50.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onNavigate(SettingsDestination.EditProfile) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(displayName, Modifier.weight(1f, fill = false), color = colors.foreground, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.35).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(21.dp), tint = colors.foregroundSecondary)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(handle, color = colors.foregroundSecondary, fontSize = 13.sp)
                    if (user.about.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(user.about, Modifier.padding(horizontal = 20.dp), color = colors.foregroundSecondary, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = colors.divider)
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .background(colors.surfaceSecondary, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(top = 12.dp, bottom = 36.dp),
                ) {
                    rootGroups.forEach { group ->
                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp)) {
                            Text(group.title, Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp), color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Column(
                                Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(24.dp))
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)),
                            ) {
                                group.rows.forEachIndexed { i, row ->
                                    RootItemTile(row.icon, row.tint(), row.title, row.subtitle) { open(row.dest) }
                                    if (i < group.rows.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), thickness = 1.dp, color = colors.divider)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, colors.error.copy(alpha = 0.8f)),
                    ) {
                        Icon(Icons.Rounded.Logout, null, Modifier.size(20.dp), tint = colors.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Log Out of Chaty", color = colors.error, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (pickTarget.isNotEmpty()) {
        val avatar = pickTarget == "avatar"
        ModalBottomSheet(onDismissRequest = { pickTarget = "" }) {
            Column(Modifier.padding(bottom = 16.dp)) {
                for ((icon, label, src) in listOf(
                    Triple(Icons.Rounded.PhotoCamera, "Take photo", ProfileMediaSource.camera),
                    Triple(Icons.Rounded.PhotoLibrary, "Choose from gallery", ProfileMediaSource.gallery),
                )) {
                    Row(
                        Modifier.fillMaxWidth().clickable { pickTarget = ""; runPick(avatar, src) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, tint = colors.foreground)
                        Spacer(Modifier.width(32.dp))
                        Text(label, color = colors.foreground, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    if (dialog == "policy") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = colors.surfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, null, tint = colors.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy Policy", color = colors.foreground, fontSize = 18.sp)
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Your Privacy is Our Priority", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\u2022 Local End-to-End Encryption:\nYour messages and calls are encrypted and stored safely on your device.\n\n" +
                            "\u2022 No Remote Telemetry:\nChaty never tracks your browsing, locations, or personal data.\n\n" +
                            "\u2022 Security & App Lock:\nLocal PIN, Pattern, and Biometric lock protect your messages completely offline.\n\n" +
                            "\u2022 Full Data Ownership:\nYou can clear chat histories, media caches, and revoke device links at any time.",
                        color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.2.sp,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { dialog = "" }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) {
                    Text("I Understand", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
    if (dialog == "about") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = colors.surfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = colors.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("About Chaty", color = colors.foreground, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text("Chaty Messenger Pro", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Version 2.4.0 (Build 2026.10)", color = colors.foregroundSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "A modern, privacy-first messaging platform with advanced personalization, rich customization, and offline security.",
                        color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 16.9.sp,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { dialog = "" }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

/** Dart _SettingsItemTile: 44dp icon box, title 16/w700, subtitle 13 (height 1.25), chevron 20. */
@Composable
private fun RootItemTile(icon: ImageVector, tint: Color, title: String, subtitle: String, onTap: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = tint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 16.25.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = colors.foregroundSecondary)
    }
}
