package com.example.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.ChatyAsyncImage
import com.example.chat.ui.theme.LocalAppColors

/** Route keys for the root rows; AppRoot maps each to a destination. Mirrors
 *  the settings_root_screen.dart row order, groups and copy. */
enum class SettingsDestination {
    LinkedDevices, Privacy, Security, ThemeEngine, TemplateStudio, HomeScreen,
    Conversation, Effects, Universal, HomeShortcuts, Notifications, Automation,
    Calls, StorageMedia, Permissions, BackupRestore, WasteCleaning, About,
    PrivacyPolicy, ShareChaty,
    EditProfile, SearchSettings, QrCode, ChangeBanner, ChangeAvatar,
}

private data class RootRow(val dest: SettingsDestination, val glyph: String, val title: String, val subtitle: String)
private data class RootGroup(val title: String, val rows: List<RootRow>)

/** Same 5 groups, titles and subtitles as settings_root_screen.dart L519-726. */
private val rootGroups = listOf(
    RootGroup("Devices", listOf(
        RootRow(SettingsDestination.LinkedDevices, "\u25A3", "Linked Devices", "Use Chaty on web and desktop, view active sessions"),
    )),
    RootGroup("Privacy and security", listOf(
        RootRow(SettingsDestination.Privacy, "\u25C9", "Privacy Center", "Last seen, online presence, anti-delete, read receipts & stealth"),
        RootRow(SettingsDestination.Security, "\u25A0", "Security & App Lock", "Biometric, PIN, Pattern protection & session security"),
    )),
    RootGroup("Look and feel", listOf(
        RootRow(SettingsDestination.ThemeEngine, "\u25D0", "Theme & Color Engine", "13+ dark & light theme presets, token editor & palette"),
        RootRow(SettingsDestination.TemplateStudio, "\u25A6", "Global Template Studio", "App-wide templates and independent component styles"),
        RootRow(SettingsDestination.HomeScreen, "\u2302", "Home Screen", "Header, chat rows, floating button & status"),
        RootRow(SettingsDestination.Conversation, "\u25AD", "Conversation Customization", "Bubbles, ticks, contact sidebar & message appearance"),
        RootRow(SettingsDestination.Effects, "\u2726", "Navigation & Touch Effects", "Interactive click particles (Stars/Hearts/Bubbles), Sound FX"),
        RootRow(SettingsDestination.Universal, "\u25CE", "Universal", "Colors, icons and fonts, language & other"),
        RootRow(SettingsDestination.HomeShortcuts, "\u2605", "Home shortcuts", "Configure in-app home layout and shortcuts"),
    )),
    RootGroup("Messages and calls", listOf(
        RootRow(SettingsDestination.Notifications, "\u266A", "Notifications & Alerts", "Heads-up popups, notification sounds, LED & vibration"),
        RootRow(SettingsDestination.Automation, "\u21BB", "Message Automation & Scheduler", "Auto-reply rules, auto-responder bot & message scheduler"),
        RootRow(SettingsDestination.Calls, "\u260E", "Call Settings", "Call privacy, incoming call controls & call appearance"),
    )),
    RootGroup("Storage and system", listOf(
        RootRow(SettingsDestination.StorageMedia, "\u25A4", "Storage & Media", "Auto-download quality, media limits & cache retention"),
        RootRow(SettingsDestination.Permissions, "\u2699", "System Permissions & Hardware", "Camera, microphone, storage, battery & OS access"),
        RootRow(SettingsDestination.BackupRestore, "\u21C5", "Backup and restore", "Export and restore Chaty settings safely"),
        RootRow(SettingsDestination.WasteCleaning, "\u2702", "Waste cleaning", "Remove expired generated image cache files"),
    )),
    RootGroup("About", listOf(
        RootRow(SettingsDestination.About, "\u24D8", "About Chaty", "App version, build information and licenses"),
        RootRow(SettingsDestination.PrivacyPolicy, "\u2691", "Privacy Policy", "Read our zero-telemetry security & privacy policy"),
        RootRow(SettingsDestination.ShareChaty, "\u2197", "Share Chaty", "Invite friends and family to join Chaty"),
    )),
)

/**
 * Port of SettingsRootScreen. Display-name resolution replicates the Dart
 * rules (myNameOverride, placeholder names ignored, username fallback).
 * Banner/avatar load through the shared ChatyAsyncImage wrapper; a
 * gradient/initials fallback is drawn on blank URL or load error, like Dart.
 */
@Composable
fun SettingsRootScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    user: UserProfile,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val theme = themeController.globalTheme
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

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Text("\u2039", color = colors.foreground, fontSize = 30.sp)
                }
            } else Spacer(Modifier.size(12.dp))
            Text("Settings", color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            HeaderAction("\u270E") { onNavigate(SettingsDestination.EditProfile) }
            HeaderAction("\u2315") { onNavigate(SettingsDestination.SearchSettings) }
            HeaderAction("\u25A3") { onNavigate(SettingsDestination.QrCode) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp)),
                    ) {
                        val bannerFallback: @Composable () -> Unit = {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(Color(theme.accentColor).copy(alpha = 0.30f), colors.surface, colors.background)),
                                ),
                                contentAlignment = Alignment.Center,
                            ) { Text("\u25A8", color = Color(theme.accentColor).copy(alpha = 0.65f), fontSize = 34.sp) }
                        }
                        ChatyAsyncImage(
                            url = user.bannerUrl?.trim(),
                            contentDescription = "Account banner",
                            modifier = Modifier.fillMaxSize(),
                            placeholder = bannerFallback,
                            error = bannerFallback,
                        )
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.92f))
                            .clickable { onNavigate(SettingsDestination.ChangeBanner) },
                        contentAlignment = Alignment.Center,
                    ) { Text("\u25A8", color = colors.foreground, fontSize = 16.sp) }
                }
                // Avatar overlaps the banner bottom by 40dp (Dart: bottom -40, 84 + 4 padding).
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.offset(y = (-40).dp).size(92.dp).clip(CircleShape)
                        .background(colors.background)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                        .padding(4.dp).clip(CircleShape)
                        .clickable { onNavigate(SettingsDestination.ChangeAvatar) },
                    contentAlignment = Alignment.Center,
                ) {
                    val avatarFallback: @Composable () -> Unit = {
                        Box(
                            Modifier.fillMaxSize().background(runCatching { Color(android.graphics.Color.parseColor(user.avatarColorHex)) }.getOrDefault(colors.primary)),
                            contentAlignment = Alignment.Center,
                        ) { Text(user.avatarInitials, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                    }
                    ChatyAsyncImage(
                        url = user.avatarUrl,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize(),
                        placeholder = avatarFallback,
                        error = avatarFallback,
                    )
                }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { onNavigate(SettingsDestination.EditProfile) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(displayName, color = colors.foreground, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("\u2304", color = colors.foregroundSecondary, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(handle, color = colors.foregroundSecondary, fontSize = 13.sp)
                if (user.about.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(user.about, color = colors.foregroundSecondary, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.surfaceSecondary)
                    .padding(top = 12.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                rootGroups.forEach { group ->
                    SettingsSection(title = group.title) {
                        group.rows.forEachIndexed { i, row ->
                            SettingsNavTile(row.glyph, row.title, row.subtitle) { onNavigate(row.dest) }
                            if (i < group.rows.lastIndex) SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(glyph: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, color = colors.foreground, fontSize = 22.sp)
    }
}
