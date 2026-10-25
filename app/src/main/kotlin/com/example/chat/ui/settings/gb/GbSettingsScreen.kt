@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.chat.ui.settings.gb
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.TemplateComponentType
import com.example.chat.core.templates.TemplateController
import com.example.chat.core.templates.TemplateRuntime
import com.example.chat.core.templates.UserTemplateConfiguration
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemeController
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.HomePreferences
import com.example.chat.domain.models.NavigationEffectPreferences
import com.example.chat.domain.models.UniversalPreferences
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class GbSheet { DownloadApps, Backup, Waste, Widgets }
enum class GbDialog { Updates, About }

/** Current user fields the Dart header avatar reads (user.avatarInitials/avatarColorHex/avatarUrl). */
data class GbSettingsUser(val initials: String, val colorHex: String?, val url: String?)

private fun toJson(v: Any?): Any? = when (v) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
    is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
    else -> v
}

private fun fromJson(v: Any?): Any? = when (v) {
    JSONObject.NULL -> null
    is JSONObject -> v.keys().asSequence().associateWith { fromJson(v.get(it)) }
    is JSONArray -> (0 until v.length()).map { fromJson(v.get(it)) }
    else -> v
}

/**
 * Port of GbSettingsScreen ("Chaty Settings"). Navigation to the other settings pages is through the
 * onOpen* lambdas (owner wiring in AppRoot); backup/restore use the real ThemeController, TemplateController
 * and [templateRuntime] (TemplateRuntimeAdapter, owner supplied). [avatar] renders Dart's ChatyNetworkAvatar.
 * Dart round-trips JSON through toMap/fromMap; ints that Dart keeps as int may come back as Long/Double here.
 * UNCOMPILED (Compose), no pixel check; ModalBottomSheet/AlertDialog/FilledTonalButton are Material3 look, not yet
 * compared to Flutter's.
 */
@Composable
fun GbSettingsScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    templateController: TemplateController,
    templateRuntime: TemplateRuntime,
    user: GbSettingsUser,
    avatar: @Composable (GbSettingsUser, Dp) -> Unit,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenThemeEditor: () -> Unit,
    onOpenUniversal: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenConversation: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMessageManagement: () -> Unit,
    onOpenStorageMedia: () -> Unit,
    onOpenNavigationEffects: () -> Unit,
) {
    val theme = themeController.globalTheme
    val colors = LocalAppColors.current
    val accent = Color(theme.accentColor)
    val isDark = theme.isDark
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<GbSheet?>(null) }
    var dialog by remember { mutableStateOf<GbDialog?>(null) }
    var restoreOpen by remember { mutableStateOf(false) }
    val fs = theme.fontScale.toFloat()

    fun toast(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    fun copyText(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
    }
    fun launchUrl(url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }
    fun shareApp() {
        ChatyHapticsPort.light(view)
        copyText("Download GBWhatsApp Pro: Enjoy privacy, themes, anti-delete and more! https://chaty.app/download")
        toast("GBWhatsApp invite link copied to clipboard! Share it with your friends! \uD83D\uDE0D\u2764\uFE0F")
    }
    fun openSheet(s: GbSheet) { ChatyHapticsPort.light(view); sheet = s }

    // Dart _buildSettingsRow: selectionClick before onTap; sheets additionally lightImpact (Download/Backup/Waste).
    @Composable
    fun GbGroupCard(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth()
                .shadow(10.dp, shape, ambientColor = colors.shadow.copy(alpha = if (isDark) 0.25f else 0.04f), spotColor = colors.shadow.copy(alpha = if (isDark) 0.25f else 0.04f))
                .background(if (isDark) Color(0xFF1E2124) else colors.surface, shape)
                .border(0.9.dp, colors.borderSubtle, shape).clip(shape),
        ) { content() }
    }
    @Composable
    fun GbDivider() = HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), thickness = 0.8.dp, color = colors.borderSubtle.copy(alpha = 0.4f))
    @Composable
    fun GbRow(icon: ImageVector, iconBg: Color, iconTint: Color, title: String, subtitle: String, onTap: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable { ChatyHapticsPort.selection(view); onTap() }.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(iconBg, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(theme.primaryTextColor), fontSize = (15.5f * fs).sp, fontWeight = FontWeight.W600, letterSpacing = (-0.2).sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(theme.secondaryTextColor), fontSize = (12.5f * fs).sp, fontWeight = FontWeight.W400)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }

    Box(Modifier.fillMaxSize().background(Color(theme.backgroundColor))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
                Text(
                    "Chaty Settings", modifier = Modifier.weight(1f), color = Color(theme.primaryTextColor),
                    fontSize = (20f * fs).sp, fontWeight = FontWeight.W700, letterSpacing = (-0.3).sp,
                )
                Box(Modifier.padding(end = 16.dp).border(2.dp, accent, CircleShape).padding(2.dp)) { avatar(user, 32.dp) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            GbGroupCard {
                GbRow(Icons.Rounded.Apps, colors.primary.copy(alpha = 0.12f), accent, "Download Apps New", "Open official Google Play listings") { openSheet(GbSheet.DownloadApps) }
            }
            Spacer(Modifier.height(12.dp))
            GbGroupCard {
                GbRow(Icons.Rounded.Security, accent.copy(alpha = 0.14f), accent, "Privacy and Security", "Privacy settings • Lock WhatsApp") { onOpenPrivacy() }
                GbDivider()
                GbRow(Icons.Rounded.FormatPaint, colors.warning.copy(alpha = 0.14f), colors.warning, "GBThemes", "23 Presets • Photo Palette • Import/Export • Reset") { onOpenThemeEditor() }
                GbDivider()
                GbRow(Icons.Rounded.SettingsSuggest, colors.info.copy(alpha = 0.14f), colors.info, "Universal", "Colors • Icons and Fonts • Language • Other") { onOpenUniversal() }
            }
            Spacer(Modifier.height(12.dp))
            GbGroupCard {
                GbRow(Icons.Rounded.Home, colors.primary.copy(alpha = 0.14f), accent, "Home Screen", "top bar • lines • floating button • status") { onOpenHome() }
                GbDivider()
                GbRow(Icons.Rounded.ChatBubbleOutline, colors.primary.copy(alpha = 0.14f), accent, "Conversation Screen", "Bubbles and Health • Images • More") { onOpenConversation() }
                GbDivider()
                GbRow(Icons.Rounded.NotificationsActive, colors.warning.copy(alpha = 0.14f), colors.warning, "Notifications", "Notify from a caller • Notify who viewed your status") { onOpenNotifications() }
                GbDivider()
                GbRow(Icons.Rounded.MarkChatUnread, colors.info.copy(alpha = 0.14f), colors.info, "Message management", "Auto Reply • Scheduling • Encrypt") { onOpenMessageManagement() }
                GbDivider()
                GbRow(Icons.Rounded.PermMedia, Color(0xFFFF4081).copy(alpha = 0.14f), Color(0xFFFF4081), "Setting Media", "media management • photos • video") { onOpenStorageMedia() }
                GbDivider()
                GbRow(Icons.Rounded.AutoAwesome, Color(0xFFE040FB).copy(alpha = 0.14f), Color(0xFFE040FB), "Navigation Effects", "Scroll up and down effects") { onOpenNavigationEffects() }
            }
            Spacer(Modifier.height(12.dp))
            GbGroupCard {
                GbRow(Icons.Rounded.CloudSync, colors.primary.copy(alpha = 0.14f), accent, "Backup and restore", "Export and restore Chaty settings safely") { openSheet(GbSheet.Backup) }
                GbDivider()
                GbRow(Icons.Rounded.CleaningServices, colors.warning.copy(alpha = 0.14f), colors.warning, "Waste cleaning", "Remove expired generated image cache files") { openSheet(GbSheet.Waste) }
                GbDivider()
                GbRow(Icons.Rounded.Widgets, colors.info.copy(alpha = 0.14f), colors.info, "Home shortcuts", "Configure in-app home layout and shortcuts") { sheet = GbSheet.Widgets }
            }
            Spacer(Modifier.height(12.dp))
            GbGroupCard {
                GbRow(Icons.Rounded.SystemUpdate, colors.primary.copy(alpha = 0.14f), accent, "Updates", "Open Chaty release notes and versions") { dialog = GbDialog.Updates }
                GbDivider()
                GbRow(Icons.Outlined.Info, colors.info.copy(alpha = 0.14f), colors.info, "About", "Follow the developer for the latest news") { dialog = GbDialog.About }
                GbDivider()
                GbRow(Icons.Rounded.Share, Color(0xFF7C4DFF).copy(alpha = 0.14f), Color(0xFF7C4DFF), "Share GBWhatsApp with friends!", "Share GBWhatsApp with your friends! 😍❤️") { shareApp() }
            }
            Spacer(Modifier.height(32.dp))

            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    // ---- sheets ----
    sheet?.let { s ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = Color(theme.surfaceColor),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                when (s) {
                    GbSheet.DownloadApps -> {
                        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(colors.borderSubtle, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.height(16.dp))
                        Text("Get Official Apps", color = Color(theme.primaryTextColor), fontSize = (18f * fs).sp, fontWeight = FontWeight.W700)
                        Spacer(Modifier.height(8.dp))
                        Text("Open the official app listings in Google Play. Chaty does not distribute modified third-party apps.", color = Color(theme.secondaryTextColor), fontSize = (13f * fs).sp)
                        Spacer(Modifier.height(18.dp))
                        data class AppTile(val name: String, val desc: String, val url: String, val icon: ImageVector, val color: Color)
                        val apps = listOf(
                            AppTile("Instagram", "Photos, Reels and Direct messages", "https://play.google.com/store/apps/details?id=com.instagram.android", Icons.Rounded.CameraAlt, colors.primary),
                            AppTile("TikTok", "Short-form videos and creators", "https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically", Icons.Rounded.MusicNote, colors.info),
                            AppTile("X", "Posts, conversations and media", "https://play.google.com/store/apps/details?id=com.twitter.android", Icons.Rounded.AlternateEmail, colors.secondary),
                            AppTile("Telegram", "Messaging, channels and cloud chats", "https://play.google.com/store/apps/details?id=org.telegram.messenger", Icons.Rounded.Send, Color(0xFF03A9F4)),
                        )
                        for (app in apps) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background(app.color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Icon(app.icon, null, tint = app.color, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, fontWeight = FontWeight.W600, fontSize = 14.sp, color = Color(theme.primaryTextColor))
                                    Text(app.desc, fontSize = 12.sp, color = Color(theme.secondaryTextColor))
                                }
                                FilledTonalButton(onClick = {
                                    sheet = null
                                    if (!launchUrl(app.url)) toast("Could not open the official ${app.name} listing.")
                                }) { Text("Open") }
                            }
                        }
                    }
                    GbSheet.Backup -> {
                        Text("Chaty Settings Backup & Restore", color = Color(theme.primaryTextColor), fontSize = (18f * fs).sp, fontWeight = FontWeight.W700)
                        Spacer(Modifier.height(10.dp))
                        Text("Export or restore themes, templates, home layout, conversation appearance and supported visual preferences. Chats, media, account information and security secrets are not included.", color = Color(theme.secondaryTextColor), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        SheetTile(Icons.Rounded.ContentCopy, accent, "Copy settings backup", "Copy a portable JSON settings profile") {
                            val gb = GbSettingsLogic.portableGb(preferences.gbFeatures)
                            val payload = (toJson(mapOf(
                                "kind" to "chaty_settings_profile",
                                "schemaVersion" to 1,
                                "home" to GbSettingsLogic.safeSettingsMap(preferences.home.toMap()),
                                "conversation" to GbSettingsLogic.safeSettingsMap(preferences.conversation.toMap()),
                                "universal" to GbSettingsLogic.safeSettingsMap(preferences.universal.toMap()),
                                "effects" to GbSettingsLogic.safeSettingsMap(preferences.effects.toMap()),
                                "gbAppearance" to gb,
                                "theme" to themeController.globalTheme.toMap(),
                                "template" to templateController.config.toMap(),
                            )) as JSONObject)
                            copyText(payload.toString())
                            sheet = null
                            toast("Settings backup copied. Chats, messages, account data and security secrets are not included.")
                        }
                        SheetTile(Icons.Rounded.Restore, colors.warning, "Restore settings", "Paste a previously copied settings profile") {
                            sheet = null
                            restoreOpen = true
                        }
                    }
                    GbSheet.Waste -> {
                        Text("Expired Cache Cleanup", color = Color(theme.primaryTextColor), fontSize = (18f * fs).sp, fontWeight = FontWeight.W700)
                        Spacer(Modifier.height(10.dp))
                        Text("Only Chaty-generated compressed image copies older than 24 hours are eligible. Voice notes, recent uploads, unsent staged media, chat attachments and profile images are preserved.", color = Color(theme.secondaryTextColor), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        val stats by produceState<Result<GbSettingsLogic.CacheStats>?>(null) {
                            value = withContext(Dispatchers.IO) { runCatching { GbSettingsLogic.scanExpiredCompressedImages(context.cacheDir) } }
                        }
                        val r = stats
                        when {
                            r == null -> Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            r.isFailure -> Text("Cache information is unavailable on this device.", color = Color(theme.secondaryTextColor))
                            else -> {
                                val st = r.getOrThrow()
                                Row(
                                    Modifier.fillMaxWidth().background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.CleaningServices, null, tint = accent, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Expired image cache", fontWeight = FontWeight.Bold, color = Color(theme.primaryTextColor))
                                        Text("${st.count} file(s) \u2022 ${GbSettingsLogic.formatCacheSize(st.bytes)}", fontSize = 12.sp, color = Color(theme.secondaryTextColor))
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.onPrimary),
                                    enabled = st.count != 0,
                                    onClick = {
                                        sheet = null
                                        scope.launch {
                                            val removed = withContext(Dispatchers.IO) { GbSettingsLogic.cleanExpiredCompressedImages(context.cacheDir) }
                                            toast("Removed ${removed.count} expired file(s), ${GbSettingsLogic.formatCacheSize(removed.bytes)}.")
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.DeleteSweep, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (st.count == 0) "No expired cache to clean" else "Clean ${st.count} expired file(s)")
                                }
                            }
                        }
                    }
                    GbSheet.Widgets -> {
                        Text("Home shortcuts", color = Color(theme.primaryTextColor), fontSize = (18f * fs).sp, fontWeight = FontWeight.W700)
                        Spacer(Modifier.height(10.dp))
                        Text("Chaty applies home layout and shortcut settings inside the app. Native Android/iOS launcher widgets are not registered in this build, so this screen does not show switches that cannot take effect.", color = Color(theme.secondaryTextColor), fontSize = 13.sp)
                        Spacer(Modifier.height(18.dp))
                        Button(modifier = Modifier.fillMaxWidth(), onClick = { sheet = null; onOpenHome() }) {
                            Icon(Icons.Rounded.DashboardCustomize, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Customize in-app home")
                        }
                    }
                }
            }
        }
    }

    // ---- dialogs ----
    when (dialog) {
        GbDialog.Updates -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = Color(theme.surfaceColor),
            title = { Text("Chaty Updates") },
            text = {
                Column {
                    Text("Installed version: 1.0.0+1", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("\u2022 Global templates and component-specific appearance")
                    Text("\u2022 Settings backup and restore for visual preferences")
                    Text("\u2022 Runtime fixes and compatibility improvements")
                    Spacer(Modifier.height(8.dp))
                    Text("Check the project release page for published release notes and newer builds.")
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = accent), onClick = {
                    dialog = null
                    if (!launchUrl("https://github.com/Bandi-maya/chat-application/releases")) toast("Could not open Chaty release notes.")
                }) { Text("View Release Notes") }
            },
        )
        GbDialog.About -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = Color(theme.surfaceColor),
            title = { Text("About GBWhatsApp") },
            text = {
                Column {
                    Text("GBWhatsApp Pro Edition", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Version: 19.50.0 (Global Edition)")
                    Spacer(Modifier.height(12.dp))
                    Text("Follow the official channels for the latest mods, themes, and security releases.")
                }
            },
            confirmButton = { Button(colors = ButtonDefaults.buttonColors(containerColor = accent), onClick = { dialog = null }) { Text("OK") } },
        )
        null -> Unit
    }

    // ---- restore dialog (Dart _restoreSettingsBackup) ----
    if (restoreOpen) {
        val clipboard = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        var input by remember { mutableStateOf(clipboard?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { restoreOpen = false },
            title = { Text("Restore settings") },
            text = {
                Column {
                    Text("Paste a Chaty settings profile JSON. It restores appearance and layout preferences only; chats, accounts, security secrets, and encryption keys are never imported.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, minLines = 4, maxLines = 8,
                        placeholder = { Text("Paste settings backup JSON") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            val c = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                            c?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let { input = it }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Paste clipboard")
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { restoreOpen = false }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    val raw = input
                    restoreOpen = false
                    if (raw.isBlank()) return@Button
                    scope.launch {
                        try {
                            val decoded = fromJson(JSONObject(raw))
                            val p = GbSettingsLogic.validateProfile(
                                decoded, ChatyTemplateId.values().map { it.key }.toSet(), TemplateComponentType.values().map { it.name }.toSet(),
                            )
                            val nextHome = HomePreferences.fromMap(preferences.home.toMap() + p.home)
                            val nextConversation = ConversationPreferences.fromMap(preferences.conversation.toMap() + p.conversation)
                            val nextUniversal = UniversalPreferences.fromMap(preferences.universal.toMap() + p.universal)
                            val nextEffects = NavigationEffectPreferences.fromMap(preferences.effects.toMap() + p.effects)
                            val nextTheme = if (p.theme.isEmpty()) null else ThemeConfig.fromMap(p.theme)
                            val nextTemplate = if (p.template.isEmpty()) null else UserTemplateConfiguration.fromMap(p.template)
                            if (nextTemplate != null) templateController.applyConfiguration(nextTemplate, templateRuntime)
                            if (p.home.isNotEmpty()) preferences.updateHome(nextHome, "Settings restore")
                            if (p.conversation.isNotEmpty()) preferences.updateConversation(nextConversation, "Settings restore")
                            if (p.universal.isNotEmpty()) preferences.updateUniversal(nextUniversal, "Settings restore")
                            if (p.effects.isNotEmpty()) preferences.updateEffects(nextEffects, "Settings restore")
                            if (p.gb.isNotEmpty()) preferences.updateGbFeatures(p.gb, "Settings restore")
                            if (nextTheme != null) themeController.updateThemeConfig(nextTheme)
                            toast("Settings profile restored.")
                        } catch (_: Exception) {
                            toast("Restore failed. The profile is invalid or from an unsupported version.")
                        }
                    }
                }) { Text("Validate & Restore") }
            },
        )
    }
}

@Composable
private fun SheetTile(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    val config = com.example.chat.ui.theme.LocalThemeConfig.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 0.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color(config.primaryTextColor), fontSize = 16.sp)
            Text(subtitle, color = Color(config.secondaryTextColor), fontSize = 14.sp)
        }
    }
}
