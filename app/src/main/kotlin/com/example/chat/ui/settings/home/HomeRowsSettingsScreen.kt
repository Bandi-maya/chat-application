package com.example.chat.ui.settings.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSliderTile
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalThemeConfig

private fun colorOr(v: Long?, fallback: Color) = v?.let { Color(it) } ?: fallback

/**
 * Port of HomeRowsSettingsScreen. Kotlin HomePreferences names the Dart textColor /
 * contactNameColor fields rowTextColor / rowContactNameColor. UNCOMPILED (Compose).
 */
@Composable
fun HomeRowsSettingsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val p = preferences.home
    val pickers = rememberGbPickers()
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }
    val accent = Color(config.accentColor)
    val fs = config.fontScale.toFloat()
    val textSize = p.screenTextSize.toFloat()

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Rows", color = Color(config.primaryTextColor), fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            GbCardContainer {
                Column {
                    GbLivePreviewCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Box(Modifier.size(48.dp).background(accent.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("JD", fontWeight = FontWeight.Bold, color = Color(config.primaryTextColor))
                                }
                                if (!p.disableOnlineDot) {
                                    Box(
                                        Modifier.align(Alignment.BottomEnd).size(13.dp)
                                            .background(colorOr(p.onlineDotColor, Color(config.successColor)), CircleShape)
                                            .border(2.dp, Color(config.surfaceColor), CircleShape),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                                    Text("John Doe", color = colorOr(p.rowContactNameColor, Color(config.primaryTextColor)), fontSize = textSize.sp, fontWeight = FontWeight.W700)
                                    Text(if (p.elapsedTime) "5m ago" else "10:42 AM", color = colorOr(p.contactOnlineColor, Color(config.secondaryTextColor)), fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Hey, are you free for a call?", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = colorOr(p.rowTextColor, Color(config.secondaryTextColor)),
                                        fontSize = (textSize - 2f).coerceIn(10f, 22f).sp, modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.background(colorOr(p.unreadCounterColor, accent), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("3", color = colorOr(p.unreadCounterTextColor, Color(config.onAccentColor)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(5.dp))
                                    Box(Modifier.size(18.dp).background(colorOr(p.mentionIndicatorBgColor, accent.copy(alpha = 0.14f)), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.AlternateEmail, null, tint = colorOr(p.mentionIconColor, accent), modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                        }
                    }
                    GbSliderTile(
                        title = "Main/Calls/Contacts Screen Text Size", subtitle = "Change the size of the text in the whole main screen",
                        value = p.screenTextSize.toFloat(), min = 10f, max = 24f, divisions = 14,
                        onChanged = { preferences.updateHome(preferences.home.copy(screenTextSize = it.toDouble())) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                Column {
                    GbSettingRow(
                        icon = Icons.Outlined.Palette, title = "Home screen text color", subtitle = "Change general message text color", colorValue = p.rowTextColor,
                        onColorTap = { pickColor("Home Screen Text Color", p.rowTextColor) { c -> preferences.updateHome(preferences.home.copy(rowTextColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Person, title = "Contact Name Color", subtitle = "Change contact name color on home screen", colorValue = p.rowContactNameColor,
                        onColorTap = { pickColor("Contact Name Color", p.rowContactNameColor) { c -> preferences.updateHome(preferences.home.copy(rowContactNameColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.MarkChatUnread, title = "Unread message counter color", subtitle = "Change the background color for the unread message counter indicator", colorValue = p.unreadCounterColor,
                        onColorTap = { pickColor("Unread Counter Background", p.unreadCounterColor) { c -> preferences.updateHome(preferences.home.copy(unreadCounterColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Unread counter text color", subtitle = "Change the text color of the unread message counter", colorValue = p.unreadCounterTextColor,
                        onColorTap = { pickColor("Unread Counter Text Color", p.unreadCounterTextColor) { c -> preferences.updateHome(preferences.home.copy(unreadCounterTextColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.WifiTethering, title = "Contact Online Color", subtitle = "Change Color of Online in Main Screen", colorValue = p.contactOnlineColor,
                        onColorTap = { pickColor("Contact Online Color", p.contactOnlineColor) { c -> preferences.updateHome(preferences.home.copy(contactOnlineColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.History, title = "Last Seen Color", subtitle = "Change Color of Last Seen in Main Screen", colorValue = p.lastSeenColor,
                        onColorTap = { pickColor("Last Seen Color", p.lastSeenColor) { c -> preferences.updateHome(preferences.home.copy(lastSeenColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.AlternateEmail, title = "Mention Indicator Background", subtitle = "Change background for @ mention indicator", colorValue = p.mentionIndicatorBgColor,
                        onColorTap = { pickColor("Mention Indicator Background", p.mentionIndicatorBgColor) { c -> preferences.updateHome(preferences.home.copy(mentionIndicatorBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Tag, title = "Mention Icon Color", subtitle = "Change icon color for @ mention indicator", colorValue = p.mentionIconColor,
                        onColorTap = { pickColor("Mention Icon Color", p.mentionIconColor) { c -> preferences.updateHome(preferences.home.copy(mentionIconColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                Column {
                    GbSwitchTile(
                        icon = Icons.Outlined.Archive, title = "Hide Archived Chats", subtitle = "Hide archived chats button from conversations screen (needs restart)", value = p.hideArchivedChats,
                        onChanged = { preferences.updateHome(preferences.home.copy(hideArchivedChats = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.Unarchive, title = "Archive Chats on top", subtitle = "Show Archive Chats on top of chats", value = p.archiveChatsOnTop,
                        onChanged = { preferences.updateHome(preferences.home.copy(archiveChatsOnTop = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.VisibilityOff, title = "Disable Contact Online/Last Seen status", subtitle = "Removes the Contact Online/Last seen Status from the Home Screen", value = p.disableContactOnlineLastSeen,
                        onChanged = { preferences.updateHome(preferences.home.copy(disableContactOnlineLastSeen = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.DoNotDisturbOn, title = "Disable Online Dot", subtitle = "Hide online indicator dot next to contact avatar", value = p.disableOnlineDot,
                        onChanged = { preferences.updateHome(preferences.home.copy(disableOnlineDot = it)) },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Circle, title = "Online Dot Color", subtitle = "Change the online indicator dot color", colorValue = p.onlineDotColor,
                        onColorTap = { pickColor("Online Dot Color", p.onlineDotColor) { c -> preferences.updateHome(preferences.home.copy(onlineDotColor = c)) } },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.AccessTime, title = "Elapsed time", subtitle = "Use Elapsed time (last sees 1hr and mins ago) in Chaty in Home and Conversation screen!", value = p.elapsedTime,
                        onChanged = { preferences.updateHome(preferences.home.copy(elapsedTime = it)) },
                    )
                }
            }
        }
    }
    pickers.Host()
}
