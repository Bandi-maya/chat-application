package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Web
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Port of ConversationActionBarScreen. The preview colours fall back to theme surface /
 * surfaceSecondary / secondaryText when the preference is null. UNCOMPILED (Compose).
 */
@Composable
fun ConversationActionBarScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    val pickers = rememberGbPickers()
    @Suppress("UNUSED_EXPRESSION") version
    val p = preferences.conversation
    val accent = Color(config.accentColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    val barColor = p.actionBarColor?.let { Color(it) } ?: Color(config.surfaceColor)
    val statusBg = p.contactStatusBgColor?.let { Color(it) } ?: colors.surfaceSecondary
    val statusText = p.contactStatusTextColor?.let { Color(it) } ?: secondaryText
    val fs = config.fontScale.toFloat()

    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Action Bar", color = primaryText, fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            GbLivePreviewCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(barColor, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = primaryText, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        if (!p.hideProfilePicture) {
                            Box(Modifier.size(36.dp).background(colors.surfaceSecondary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Person, null, tint = secondaryText, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            if (!p.hideContactName) Text("Tyler Durden", color = primaryText, fontWeight = FontWeight.W700, fontSize = 15.sp)
                            Text("Online", color = accent, fontSize = 12.sp)
                        }
                        if (!p.hideCallButton) {
                            Icon(Icons.Rounded.Call, null, tint = primaryText, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Icon(Icons.Rounded.Videocam, null, tint = primaryText, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Icon(Icons.Rounded.MoreVert, null, tint = primaryText, modifier = Modifier.size(20.dp))
                    }
                    if (!p.disableContactStatus) {
                        Text(
                            "Life is too short. Enjoy it and Support GBWA!",
                            Modifier.fillMaxWidth()
                                .background(statusBg, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = statusText, fontSize = 12.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                GbSettingRow(
                    title = "Action Bar", icon = Icons.Rounded.Web, subtitle = "Change Color of ActionBar on Chat Screen",
                    colorValue = p.actionBarColor,
                    onColorTap = { pickColor("Action Bar Color", p.actionBarColor) { preferences.updateConversation(p.copy(actionBarColor = it)) } },
                )
                GbSwitchTile(Icons.Outlined.AccountCircle, "Hide contact profile picture", p.hideProfilePicture, {
                    preferences.updateConversation(p.copy(hideProfilePicture = it))
                }, "Removes the contact profile picture from the header in conversation screen")
                GbSwitchTile(Icons.Outlined.PersonOff, "Hide Contact Name", p.hideContactName, {
                    preferences.updateConversation(p.copy(hideContactName = it))
                }, "Hides the name of the person you're talking to")
                GbSwitchTile(Icons.Rounded.PhoneDisabled, "Hide Call Button", p.hideCallButton, {
                    preferences.updateConversation(p.copy(hideCallButton = it))
                }, "Hide call button from conversation action bar")
                GbSwitchTile(Icons.Rounded.SpeakerNotesOff, "Disable Contact Status", p.disableContactStatus, {
                    preferences.updateConversation(p.copy(disableContactStatus = it))
                }, "Removes the Contact status line that's under the header in conversation")
                GbSettingRow(
                    title = "Contact Status Background", icon = Icons.Rounded.FormatPaint,
                    subtitle = "Change General Contact status background in Conversation screen",
                    colorValue = p.contactStatusBgColor,
                    onColorTap = { pickColor("Status Background Color", p.contactStatusBgColor) { preferences.updateConversation(p.copy(contactStatusBgColor = it)) } },
                )
                GbSettingRow(
                    title = "Contact Status Text Color", icon = Icons.Rounded.FormatColorText,
                    subtitle = "Change the text of the General contact status in conversation screen",
                    colorValue = p.contactStatusTextColor,
                    onColorTap = { pickColor("Status Text Color", p.contactStatusTextColor) { preferences.updateConversation(p.copy(contactStatusTextColor = it)) } },
                )
            }
        }
    }
    pickers.Host()
}
