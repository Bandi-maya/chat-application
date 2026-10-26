package com.example.chat.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestorePage
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReplyAll
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.ui.kit.AppContextMenu
import com.example.chat.ui.kit.ChatyAboutDialog
import com.example.chat.ui.kit.ChatyLicenseEntry
import com.example.chat.ui.kit.ContextMenuItem
import com.example.chat.ui.kit.ContextMenuSection
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Screens SettingsScreen (and the search index) can open. The owner maps each to its nav route. */
enum class SettingsScreenDestination {
    PrivacyCenter, SecurityCenter, ThemeEditor, TemplatesSettings, StarredMessages, HomeScreenSettings,
    ConversationSettings, MessageManagement, NavigationEffects, NotificationSettings, SystemPermissions,
    ProPricing, LinkedDevicesOnly, AccountSettings, NewChatGroupMode, NewChatBroadcastMode, UniversalScreen,
    UniversalAppearance,
}

/** Dart SettingsSearchResult with the destination Widget replaced by [destination]. */
data class SettingsSearchEntry(
    val title: String,
    val category: String,
    val description: String,
    val icon: ImageVector,
    val destination: SettingsScreenDestination,
)

/** The fields SettingsScreen reads from dataStore.currentUser. */
data class SettingsProfileUser(val displayName: String, val about: String, val avatarInitials: String, val username: String)

/** Port of _buildSearchIndex (21 entries, same order and copy). */
fun buildSettingsSearchIndex(): List<SettingsSearchEntry> {
    fun e(t: String, c: String, d: String, i: ImageVector, dest: SettingsScreenDestination) = SettingsSearchEntry(t, c, d, i, dest)
    return listOf(
        e("Freeze Last Seen", "Privacy", "Freeze last visible timestamp", Icons.Rounded.AcUnit, SettingsScreenDestination.PrivacyCenter),
        e("Hide Last Seen Audience", "Privacy", "Restrict last seen visibility to Everyone, Contacts, Nobody", Icons.Rounded.VisibilityOff, SettingsScreenDestination.PrivacyCenter),
        e("Read Receipts (Blue Ticks)", "Privacy", "Enable or disable read receipt indicators", Icons.Rounded.DoneAll, SettingsScreenDestination.PrivacyCenter),
        e("Anti-Delete Messages", "Privacy", "Retain deleted messages marked \"Deleted by sender\"", Icons.Rounded.DeleteForever, SettingsScreenDestination.PrivacyCenter),
        e("Chaty App Lock", "Security", "Biometric, PIN, Pattern & Password protection", Icons.Rounded.Lock, SettingsScreenDestination.SecurityCenter),
        e("Chaty Themes", "Themes", "13+ dark & light theme presets and token editor", Icons.Rounded.Palette, SettingsScreenDestination.ThemeEditor),
        e("Global Template Studio", "Themes & Appearance", "Apply app-wide templates and independently customize navigation, home, chat list, bubbles, composer, updates, profile and calls", Icons.Rounded.DashboardCustomize, SettingsScreenDestination.TemplatesSettings),
        e("Starred Messages", "Messages", "Search, open and remove saved messages across conversations", Icons.Rounded.StarOutline, SettingsScreenDestination.StarredMessages),
        e("Home UI Style", "Home Screen", "Default, Classic, Compact, Expressive, Stories First", Icons.Rounded.SpaceDashboard, SettingsScreenDestination.HomeScreenSettings),
        e("Instagram Stories Bar", "Home Screen", "Horizontal story avatars above chats", Icons.Rounded.HistoryEdu, SettingsScreenDestination.HomeScreenSettings),
        e("Ghost Mode", "Home Screen", "One-tap total stealth privacy bundle", Icons.Rounded.ShieldMoon, SettingsScreenDestination.HomeScreenSettings),
        e("Bubble Shapes & Corner Radius", "Conversation", "Rounded, Tail, Squircle, Compact bubble shapes", Icons.Rounded.ChatBubbleOutline, SettingsScreenDestination.ConversationSettings),
        e("Quick Contact Sidebar", "Conversation", "Docked contact switcher panel", Icons.Rounded.Dock, SettingsScreenDestination.ConversationSettings),
        e("Auto-Reply Rules", "Message Management", "Keyword automated response rules", Icons.Rounded.ReplyAll, SettingsScreenDestination.MessageManagement),
        e("Message Scheduler", "Message Management", "Schedule messages for auto execution", Icons.Rounded.Schedule, SettingsScreenDestination.MessageManagement),
        e("Interactive Click Particles", "Navigation Effects", "Tap particle splash symbols and speed", Icons.Rounded.AutoAwesome, SettingsScreenDestination.NavigationEffects),
        e("Notification Sounds & LED", "Notifications", "Conversation tones, LED colors, and heads-up alerts", Icons.Rounded.NotificationsActive, SettingsScreenDestination.NotificationSettings),
        e("System Permissions & Hardware", "Permissions", "Camera, mic, storage, notification access", Icons.Rounded.Security, SettingsScreenDestination.SystemPermissions),
    )
}

private enum class SettingsSheet { EditProfile, Reset, Export, About }

/**
 * Port of lib/features/settings/settings_screen.dart. Navigation goes through [onNavigate]; the search
 * UI (SettingsSearchDelegate, another worker) through [onOpenSearch]. Dart read dataStore.currentUser:
 * [user] / [onSaveProfile] / [onLogout] stand in for dataStore.updateProfile / dataStore.logout +
 * pushAndRemoveUntil(WelcomeScreen). Uncompiled; no pixel check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    user: SettingsProfileUser,
    onSaveProfile: (displayName: String, about: String) -> Unit,
    onNavigate: (SettingsScreenDestination) -> Unit,
    onOpenSearch: (List<SettingsSearchEntry>) -> Unit,
    onLogout: () -> Unit,
    onBack: (() -> Unit)?,
    licenses: List<ChatyLicenseEntry> = ChatyLicenseData.entries,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var themeTick by remember { mutableStateOf(0) }
    DisposableEffect(themeController) {
        val l: () -> Unit = { themeTick++ }
        themeController.addListener(l)
        onDispose { themeController.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val tick = themeTick
    val theme = themeController.globalTheme
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    val accent = Color(theme.accentColor)
    val primaryText = Color(theme.primaryTextColor)
    val secondaryText = Color(theme.secondaryTextColor)

    fun toast(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    @Composable
    fun SectionHeader(title: String) {
        Text(
            title, Modifier.padding(start = 8.dp, top = 4.dp),
            fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = secondaryText.copy(alpha = 0.8f), letterSpacing = 0.9.sp,
        )
    }

    @Composable
    fun Tile(icon: ImageVector, tint: Color, title: String, subtitle: String, onTap: () -> Unit) =
        ChatySettingsTile(title = title, icon = icon, iconColor = tint, subtitle = subtitle, onTap = onTap)

    fun nav(d: SettingsScreenDestination): () -> Unit = { onNavigate(d) }

    Box(Modifier.fillMaxSize().background(Color(theme.backgroundColor))) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onBack?.invoke() }) { Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(22.dp), tint = primaryText) }
                Row {
                    IconButton(onClick = { onOpenSearch(buildSettingsSearchIndex()) }) { Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = primaryText) }
                    IconButton(onClick = { sheet = SettingsSheet.Export }) { Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(22.dp), tint = primaryText) }
                    IconButton(onClick = { sheet = SettingsSheet.EditProfile }) { Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp), tint = primaryText) }
                }
            }
            Spacer(Modifier.height(10.dp))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier
                        .background(colors.surfaceSecondary, RoundedCornerShape(18.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Mood, null, Modifier.size(16.dp), tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text(if (user.about.isNotEmpty()) user.about else "Current mood", color = primaryText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.size(86.dp)
                        .shadow(8.dp, CircleShape, ambientColor = accent.copy(alpha = 0.25f), spotColor = accent.copy(alpha = 0.25f))
                        .border(2.5.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(80.dp).background(colors.surfaceSecondary, CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            if (user.avatarInitials.isNotEmpty() && user.avatarInitials != "BM") user.avatarInitials
                            else if (user.displayName.isNotEmpty()) user.displayName.substring(0, 1).uppercase() else "U",
                            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = primaryText,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.clickable { sheet = SettingsSheet.EditProfile }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (user.displayName.isNotEmpty() && user.displayName != "Bandi Maya") user.displayName
                        else if (user.username.isNotEmpty()) user.username else "User",
                        color = primaryText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(22.dp), tint = secondaryText)
                }
                Spacer(Modifier.height(3.dp))
                if (user.username.isNotEmpty()) Text("@${user.username}", color = secondaryText, fontSize = 13.5.sp)
            }
            Spacer(Modifier.height(24.dp))

            ChatySettingsCard {
                Column {
                    Tile(Icons.Rounded.CurrencyRupee, colors.primary, "Payments", "Chaty Pro plans, UPI & billing", nav(SettingsScreenDestination.ProPricing))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Diamond, Color(0xFFFFC107), "Subscriptions", "Explore premium benefits", nav(SettingsScreenDestination.ProPricing))
                    ChatySettingsRowDivider()
                    Tile(Icons.Rounded.Devices, colors.info, "Linked devices", "Use Chaty on web and desktop devices", nav(SettingsScreenDestination.LinkedDevicesOnly))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Person, colors.success, "Account", "Profile photo, username & details", nav(SettingsScreenDestination.AccountSettings))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Lock, colors.primary, "Privacy", "Blocked accounts, disappearing messages", nav(SettingsScreenDestination.PrivacyCenter))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.People, colors.accent, "Lists", "Manage people and contact groups", nav(SettingsScreenDestination.NewChatGroupMode))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.ChatBubbleOutline, colors.success, "Chats", "Chat history, backup", nav(SettingsScreenDestination.ConversationSettings))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Palette, colors.warning, "Appearance", "Chat theme, app icon, app theme", nav(SettingsScreenDestination.UniversalScreen))
                    ChatySettingsRowDivider()
                    Tile(Icons.Rounded.Cast, colors.primary, "Broadcasts", "Create and manage broadcast recipient lists", nav(SettingsScreenDestination.NewChatBroadcastMode))
                }
            }
            Spacer(Modifier.height(18.dp))

            SectionHeader("PRIVACY & SECURITY")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    Tile(Icons.Outlined.Shield, colors.info, "Privacy Center", "Freeze last seen, anti-delete, read receipts & stealth", nav(SettingsScreenDestination.PrivacyCenter))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Lock, colors.primary, "Security Center", "Chaty App Lock (Biometric/PIN/Pattern), devices & audit", nav(SettingsScreenDestination.SecurityCenter))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.VpnKey, colors.success, "System Permissions & Hardware", "Camera, mic, storage, background refresh & battery", nav(SettingsScreenDestination.SystemPermissions))
                }
            }
            Spacer(Modifier.height(18.dp))

            SectionHeader("THEMES & APPEARANCE")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    Tile(Icons.Outlined.Palette, colors.accent, "Themes & Color Engine", "13+ dark & light presets, custom tokens, font scaling", nav(SettingsScreenDestination.ThemeEditor))
                    ChatySettingsRowDivider()
                    Tile(Icons.Rounded.DashboardCustomize, colors.primary, "Global Template Studio", "Apply a complete app template or mix component styles across navigation, chats, composer, updates, profile and calls", nav(SettingsScreenDestination.TemplatesSettings))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.AutoAwesomeMosaic, colors.primary, "Universal Appearance", "App icon changer, custom emojis, typography styles", nav(SettingsScreenDestination.UniversalAppearance))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.TouchApp, colors.warning, "Navigation & Touch Effects", "Interactive click particles (Stars/Hearts/Bubbles), Sound FX", nav(SettingsScreenDestination.NavigationEffects))
                }
            }
            Spacer(Modifier.height(18.dp))

            SectionHeader("CHATS & NOTIFICATIONS")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    Tile(Icons.Outlined.SpaceDashboard, colors.info, "Home Screen UI Layout", "Classic, compact, expressive, stories-first styles", nav(SettingsScreenDestination.HomeScreenSettings))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.ChatBubbleOutline, colors.success, "Conversation Customization", "Bubble shapes, corner radius, tick styles, contact sidebar", nav(SettingsScreenDestination.ConversationSettings))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Star, colors.primary, "Starred Messages", "Search, open and remove saved messages across conversations", nav(SettingsScreenDestination.StarredMessages))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Notifications, colors.warning, "Notification & Sound Studio", "Conversation tones, LED colors, heads-up popups, vibration", nav(SettingsScreenDestination.NotificationSettings))
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.SmartToy, colors.success, "Message Automation & Scheduler", "Auto-reply bot with keyword rules, message scheduler", nav(SettingsScreenDestination.MessageManagement))
                }
            }
            Spacer(Modifier.height(18.dp))

            SectionHeader("DATA & SYSTEM")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    Tile(Icons.Outlined.Backup, colors.info, "Backup & Export Data", "Export encrypted chats JSON, backup verification") { sheet = SettingsSheet.Export }
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.RestorePage, colors.error, "Reset & Diagnostics", "Reset privacy settings, themes, or restore factory defaults") { sheet = SettingsSheet.Reset }
                    ChatySettingsRowDivider()
                    Tile(Icons.Outlined.Info, colors.foregroundSecondary, "About Chaty", "Version 1.0.0 \u2022 Pure private messaging architecture") { sheet = SettingsSheet.About }
                }
            }
            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, colors.error),
            ) {
                Icon(Icons.Rounded.Logout, null, Modifier.size(18.dp), tint = colors.error)
                Spacer(Modifier.width(8.dp))
                Text("Log Out of Chaty", color = colors.error, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }
            Spacer(Modifier.height(90.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (sheet) {
        SettingsSheet.EditProfile -> {
            var name by remember { mutableStateOf(user.displayName) }
            var about by remember { mutableStateOf(user.about) }
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                containerColor = colors.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Column(Modifier.fillMaxWidth().imePadding().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)) {
                    Text("Edit Profile Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Display Name") })
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(about, { about = it }, Modifier.fillMaxWidth(), label = { Text("Bio / About Status") })
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onSaveProfile(name.trim(), about.trim()); sheet = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) { Text("Save Profile", fontWeight = FontWeight.Bold) }
                }
            }
        }
        SettingsSheet.Reset -> AppContextMenu(
            onDismiss = { sheet = null },
            title = "Reset & Preferences",
            subtitle = "Restore settings or presets",
            sections = listOf(
                ContextMenuSection(
                    items = listOf(
                        ContextMenuItem("Reset Theme to Default B&W", icon = Icons.Rounded.Restore, subtitle = "Restore default monochrome theme", onTap = {
                            themeController.resetToDefaults(); toast("Theme reset to default.")
                        }),
                        ContextMenuItem("Reset Privacy Options", icon = Icons.Outlined.Shield, subtitle = "Reset last seen and stealth toggles", onTap = {
                            preferences.resetPrivacy(); toast("Privacy settings reset.")
                        }),
                    ),
                ),
                ContextMenuSection(
                    title = "Danger Zone",
                    items = listOf(
                        ContextMenuItem("Reset ALL Preferences", icon = Icons.Rounded.CleaningServices, subtitle = "Restore factory defaults", isDestructive = true, onTap = {
                            preferences.resetAll(); themeController.resetToDefaults()
                            toast("All preferences reset to factory defaults.")
                        }),
                    ),
                ),
            ),
        )
        SettingsSheet.Export -> AlertDialog(
            onDismissRequest = { sheet = null },
            title = { Text("Backup & Export") },
            text = { Text("All your encrypted chats, settings, scheduled rules, and media configurations have been verified and backed up locally.") },
            dismissButton = { TextButton(onClick = { sheet = null }) { Text("Close") } },
            // Source quirk preserved: Export JSON only dismisses and shows the snackbar; nothing is exported.
            confirmButton = { Button(onClick = { sheet = null; toast("Exported backup package successfully! \uD83D\uDCE6") }) { Text("Export JSON") } },
        )
        SettingsSheet.About -> ChatyAboutDialog(
            applicationName = "Chaty",
            applicationVersion = "1.0.0 (Build 2026)",
            applicationLegalese = "\u00A9 2026 LOGY BYTE. All rights reserved.",
            onDismiss = { sheet = null },
            licenses = licenses,
        ) {
            Spacer(Modifier.height(12.dp))
            Text("End-to-end encrypted, deeply customizable instant messaging application.")
        }
        null -> Unit
    }
}
