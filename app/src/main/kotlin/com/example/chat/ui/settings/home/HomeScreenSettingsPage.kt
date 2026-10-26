package com.example.chat.ui.settings.home

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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Port of HomeScreenSettingsPage. Sub-screens are opened by the owner through the four lambdas
 * (Dart MaterialPageRoute pushes of Header/Rows/Floating Action Button/Status). UNCOMPILED.
 * Deviation: Flutter Switch(activeColor) paints the thumb accent and track accent@0.5; mapped via
 * SwitchDefaults.colors.
 */
@Composable
fun HomeScreenSettingsPage(
    preferences: ChatyPreferencesController,
    onOpenHeader: () -> Unit,
    onOpenRows: () -> Unit,
    onOpenFab: () -> Unit,
    onOpenStatus: () -> Unit,
    onBack: () -> Unit,
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val view = LocalView.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val home = preferences.home
    val accent = Color(config.accentColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    val fs = config.fontScale.toFloat()
    val isDark = config.isDark

    @Composable
    fun card(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth()
                .shadow(5.dp, shape, ambientColor = colors.shadow.copy(alpha = if (isDark) 0.25f else 0.04f), spotColor = colors.shadow.copy(alpha = if (isDark) 0.25f else 0.04f))
                .clip(shape)
                .background(if (isDark) Color(0xFF1E2124) else colors.surface)
                .border(0.9.dp, colors.borderSubtle, shape),
        ) { content() }
    }

    @Composable
    fun divider() = HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), thickness = 0.8.dp, color = colors.borderSubtle.copy(alpha = 0.4f))

    @Composable
    fun sectionDivider(label: String) {
        Column(Modifier.padding(16.dp, 16.dp, 16.dp, 6.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = accent, fontWeight = FontWeight.W700, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(1.2.dp)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.7f), accent.copy(alpha = 0f)))),
            )
        }
    }

    @Composable
    fun subpageRow(icon: ImageVector, title: String, onTap: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable { ChatyHapticsPort.selection(view); onTap() }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(title, color = primaryText, fontSize = (15.5f * fs).sp, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }

    @Composable
    fun switchRow(icon: ImageVector, title: String, subtitle: String, value: Boolean, onChanged: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = primaryText, fontSize = (14.5f * fs).sp, fontWeight = FontWeight.W600)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = secondaryText, fontSize = (11.5f * fs).sp)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = value,
                onCheckedChange = { ChatyHapticsPort.selection(view); onChanged(it) },
                colors = com.example.chat.ui.kit.chatySwitchColors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f)),
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Home Screen", color = primaryText, fontSize = (20f * fs).sp, fontWeight = FontWeight.W700, letterSpacing = (-0.3).sp)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            GbLivePreviewCard(label = "BOTTOM NAVIGATION PREVIEW") {
                val items = buildList {
                    add(Icons.Outlined.ChatBubbleOutline to "Chats")
                    if (!preferences.gbBool("nav_hide_updates")) add(Icons.Rounded.Update to "Updates")
                    if (!preferences.gbBool("nav_hide_tasks")) add(Icons.Rounded.TaskAlt to "Tasks")
                    add(Icons.Outlined.Call to "Calls")
                }
                Row(Modifier.fillMaxWidth()) {
                    items.forEach { (ic, label) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(ic, null, tint = colors.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.foregroundSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            card {
                sectionDivider("Bottom navigation")
                switchRow(Icons.Rounded.TaskAlt, "Hide Tasks tab", "Remove Tasks from the bottom navigation", preferences.gbBool("nav_hide_tasks")) {
                    preferences.updateGbFeatures(mapOf("nav_hide_tasks" to it), logTitle = "Hide Tasks tab")
                }
                switchRow(Icons.Rounded.Update, "Hide Updates tab", "Remove Updates from the bottom navigation", preferences.gbBool("nav_hide_updates")) {
                    preferences.updateGbFeatures(mapOf("nav_hide_updates" to it), logTitle = "Hide Updates tab")
                }
            }
            Spacer(Modifier.height(16.dp))
            card {
                subpageRow(Icons.Rounded.ViewHeadline, "Header", onOpenHeader)
                divider()
                subpageRow(Icons.Outlined.ViewAgenda, "Rows", onOpenRows)
                divider()
                subpageRow(Icons.Outlined.AddCircleOutline, "Floating Action Button", onOpenFab)
                divider()
                subpageRow(Icons.Outlined.AccountCircle, "Status", onOpenStatus)
            }
            Spacer(Modifier.height(16.dp))
            card {
                sectionDivider("Mods")
                switchRow(Icons.Rounded.HorizontalRule, "Hide Chats Divider", "Removes grey line between chats in Main Screen", home.hideChatsDivider) {
                    preferences.updateHome(preferences.home.copy(hideChatsDivider = it), logTitle = "Hide Chats Divider")
                }
                switchRow(Icons.Outlined.PersonOff, "Hide unsaved numbers", "If contact number not saved then it ll set default contact name", home.hideUnsavedNumbers) {
                    preferences.updateHome(preferences.home.copy(hideUnsavedNumbers = it), logTitle = "Hide unsaved numbers")
                }
                sectionDivider("Forward Settings")
                switchRow(Icons.Outlined.PersonSearch, "Frequently contacted", "Hide Frequently contacted from Forward section", home.hideFrequentlyContacted) {
                    preferences.updateHome(preferences.home.copy(hideFrequentlyContacted = it), logTitle = "Hide Frequently contacted")
                }
                switchRow(Icons.Outlined.Group, "Other contacts", "Hide Other contacts from Forward section", home.hideOtherContacts) {
                    preferences.updateHome(preferences.home.copy(hideOtherContacts = it), logTitle = "Hide Other contacts")
                }
                switchRow(Icons.Rounded.ChatBubbleOutline, "Recent chats", "Hide Recent chats from Forward section", home.hideRecentChats) {
                    preferences.updateHome(preferences.home.copy(hideRecentChats = it), logTitle = "Hide Recent chats")
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
