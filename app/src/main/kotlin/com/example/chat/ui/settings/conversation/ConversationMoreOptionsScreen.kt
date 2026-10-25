package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of ConversationMoreOptionsScreen. UNCOMPILED (Compose); uses the ported ChatyIcon for the play glyph. */
@Composable
fun ConversationMoreOptionsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
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
            Text("More options", color = primaryText, fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            GbCardContainer {
                GbLivePreviewCard {
                    Column {
                        Text("Live Visual Options Preview", color = primaryText, fontWeight = FontWeight.W700, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .background(p.voiceNotePlayingBarColor?.let { Color(it) } ?: Color(config.surfaceColor), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp).background(p.voiceNotePlayButtonColor?.let { Color(it) } ?: accent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { ChatyIcon(ChatyIconId.playArrow, size = 20.dp, color = Color.White) }
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f).height(4.dp).background(accent.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text("0:42", color = secondaryText, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                "Messages and calls are end-to-end encrypted",
                                Modifier.background(
                                    p.infoBalloonsBgColor?.let { Color(it) } ?: if (config.isDark) Color(0xFF1E2630) else Color(0xFFE2E8F0),
                                    RoundedCornerShape(12.dp),
                                ).padding(horizontal = 14.dp, vertical = 6.dp),
                                color = p.infoBalloonsTextColor?.let { Color(it) } ?: secondaryText,
                                fontSize = 11.sp, fontWeight = FontWeight.W500,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                GbSettingRow(
                    title = "Emoji header color", icon = Icons.Outlined.EmojiEmotions, subtitle = "Change Emoji popup header background color",
                    colorHex = p.emojiHeaderColor,
                    onColorTap = { pickColor("Emoji Header Color", p.emojiHeaderColor) { preferences.updateConversation(p.copy(emojiHeaderColor = it)) } },
                )
                GbSettingRow(
                    title = "Emoji header icons color", icon = Icons.Rounded.SentimentSatisfiedAlt, subtitle = "Change Emoji popup icons color",
                    colorHex = p.emojiHeaderIconsColor,
                    onColorTap = { pickColor("Emoji Header Icons Color", p.emojiHeaderIconsColor) { preferences.updateConversation(p.copy(emojiHeaderIconsColor = it)) } },
                )
                GbSettingRow(
                    title = "Emoji picker background color", icon = Icons.Outlined.Palette, subtitle = "Change Emoji popup background color",
                    colorHex = p.emojiPickerBgColor,
                    onColorTap = { pickColor("Emoji Picker Background Color", p.emojiPickerBgColor) { preferences.updateConversation(p.copy(emojiPickerBgColor = it)) } },
                )
                GbSettingRow(
                    title = "Hyperlinks colors", icon = Icons.Rounded.Link, subtitle = "Change Hyperlinks color in Chat Bubbles",
                    colorHex = p.hyperlinksColor,
                    onColorTap = { pickColor("Hyperlinks Color", p.hyperlinksColor) { preferences.updateConversation(p.copy(hyperlinksColor = it)) } },
                )
                GbSettingRow(
                    title = "Info balloons Text colour", icon = Icons.Rounded.Info, subtitle = "Date and Missed call Text colour bubbles",
                    colorHex = p.infoBalloonsTextColor,
                    onColorTap = { pickColor("Info Balloons Text Color", p.infoBalloonsTextColor) { preferences.updateConversation(p.copy(infoBalloonsTextColor = it)) } },
                )
                GbSettingRow(
                    title = "Info balloons background colour", icon = Icons.Outlined.MarkChatUnread, subtitle = "Date and Missed call bubbles background colour",
                    colorHex = p.infoBalloonsBgColor,
                    onColorTap = { pickColor("Info Balloons Background Color", p.infoBalloonsBgColor) { preferences.updateConversation(p.copy(infoBalloonsBgColor = it)) } },
                )
                GbSettingRow(
                    title = "Group participant name color", icon = Icons.Outlined.Group, subtitle = "Change color of group member/participant name on message",
                    colorHex = p.groupParticipantNameColor,
                    onColorTap = { pickColor("Participant Name Color", p.groupParticipantNameColor) { preferences.updateConversation(p.copy(groupParticipantNameColor = it)) } },
                )
                GbSettingRow(
                    title = "Voice note playing bar", icon = Icons.Rounded.GraphicEq, subtitle = "Change color of voice message playing bar",
                    colorHex = p.voiceNotePlayingBarColor,
                    onColorTap = { pickColor("Voice Note Playing Bar Color", p.voiceNotePlayingBarColor) { preferences.updateConversation(p.copy(voiceNotePlayingBarColor = it)) } },
                )
                GbSettingRow(
                    title = "Voice note Play button", icon = Icons.Rounded.PlayCircleFilled, subtitle = "Change color of voice message Play button",
                    colorHex = p.voiceNotePlayButtonColor,
                    onColorTap = { pickColor("Voice Note Play Button Color", p.voiceNotePlayButtonColor) { preferences.updateConversation(p.copy(voiceNotePlayButtonColor = it)) } },
                )
            }
        }
    }
    pickers.Host()
}
