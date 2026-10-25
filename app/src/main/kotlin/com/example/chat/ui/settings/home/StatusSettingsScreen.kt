package com.example.chat.ui.settings.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PersonPin
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Timelapse
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
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Port of StatusSettingsScreen. Kotlin StatusPreferences names: statusUnSeenColor (Dart statusUnseenColor)
 * and startStoriesDirectlyWithSound (Dart startStoriesWithSound). UNCOMPILED (Compose).
 */
@Composable
fun StatusSettingsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val p = preferences.status
    val pickers = rememberGbPickers()
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }
    fun showStoriesStyleModal() = pickers.showRadio(
        "Stories Style", listOf("Instagram", "Facebook", "RC-Notification", "Stock", "Card View"), preferences.status.storiesStyle,
    ) { preferences.updateStatus(preferences.status.copy(storiesStyle = it)) }
    fun showStatusStyleModal() = pickers.showRadio(
        "Activate the new status style",
        listOf("Old Status with Thumbnail", "Modern Grid View", "Minimalist Card", "Full Bleed Carousel"),
        preferences.status.activateNewStatusStyle,
    ) { preferences.updateStatus(preferences.status.copy(activateNewStatusStyle = it)) }
    fun showReactionEmojiModal() = pickers.showRadio(
        "Status Reaction Emoji", listOf("\uD83D\uDC9A", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDC4F", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83C\uDF89"),
        preferences.status.statusReactionEmoji,
    ) { preferences.updateStatus(preferences.status.copy(statusReactionEmoji = it)) }
    val accent = Color(config.accentColor)
    val fs = config.fontScale.toFloat()

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Status", color = Color(config.primaryTextColor), fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            GbCardContainer {
                Column {
                    GbLivePreviewCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.size(56.dp).border(
                                        2.5.dp,
                                        if (p.statusAroundProfile) colorOr(p.statusSeenColor, accent) else Color.Transparent,
                                        CircleShape,
                                    ),
                                )
                                Box(Modifier.size(46.dp).background(accent.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("TN", fontWeight = FontWeight.Bold, color = Color(config.primaryTextColor))
                                }
                                Box(Modifier.align(Alignment.BottomEnd).background(accent, CircleShape).padding(2.dp)) {
                                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Testing Name", color = colorOr(p.contactNameColor, Color(config.primaryTextColor)), fontSize = 16.sp, fontWeight = FontWeight.W700)
                                Spacer(Modifier.height(3.dp))
                                Text("Tap to add status update", color = Color(config.secondaryTextColor), fontSize = 13.sp)
                            }
                            Text(p.statusReactionEmoji, fontSize = 22.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                Column {
                    GbSwitchTile(
                        icon = Icons.Rounded.AutoStories, title = "Enable Instagram-like Stories", subtitle = "Enable Instagram Stories for Chaty (needs restart)", value = p.enableInstagramStories,
                        onChanged = { preferences.updateStatus(preferences.status.copy(enableInstagramStories = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.ViewCarousel, title = "Carousel view", subtitle = "Status displayed in horizontal carousel flow", value = p.carouselView,
                        onChanged = { preferences.updateStatus(preferences.status.copy(carouselView = it)) },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Style, title = "Stories Style", subtitle = p.storiesStyle, hasSubScreen = true,
                        onTap = { showStoriesStyleModal() },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.DashboardCustomize, title = "Activate the new status style", subtitle = p.activateNewStatusStyle, hasSubScreen = true,
                        onTap = { showStatusStyleModal() },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.AddReaction, title = "Status Reaction Emoji", subtitle = "Change status reaction button: ${p.statusReactionEmoji}", hasSubScreen = true,
                        onTap = { showReactionEmojiModal() },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                Column {
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignBottom, title = "Recent Updates bar", subtitle = "Change color of background recent/viewed updates bar in status page", colorValue = p.recentUpdatesBarColor,
                        onColorTap = { pickColor("Recent Updates Bar Color", p.recentUpdatesBarColor) { c -> preferences.updateStatus(preferences.status.copy(recentUpdatesBarColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.TextFields, title = "Recent Updates text", subtitle = "Change color of text recent/viewed updates bar in status page", colorValue = p.recentUpdatesTextColor,
                        onColorTap = { pickColor("Recent Updates Text Color", p.recentUpdatesTextColor) { c -> preferences.updateStatus(preferences.status.copy(recentUpdatesTextColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.PersonPin, title = "Contact Name", subtitle = "Change Color Contact Name in Stories", colorValue = p.contactNameColor,
                        onColorTap = { pickColor("Contact Name Color", p.contactNameColor) { c -> preferences.updateStatus(preferences.status.copy(contactNameColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Visibility, title = "Status Seen", subtitle = "Select color for circle seen Status", colorValue = p.statusSeenColor,
                        onColorTap = { pickColor("Status Seen Color", p.statusSeenColor) { c -> preferences.updateStatus(preferences.status.copy(statusSeenColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.RadioButtonChecked, title = "Status UnSeen", subtitle = "Select color for circle unseen Status", colorValue = p.statusUnseenColor,
                        onColorTap = { pickColor("Status UnSeen Color", p.statusUnseenColor) { c -> preferences.updateStatus(preferences.status.copy(statusUnSeenColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Layers, title = "Counter Background", subtitle = "Change Color background counter Stories", colorValue = p.counterBackgroundColor,
                        onColorTap = { pickColor("Counter Background Color", p.counterBackgroundColor) { c -> preferences.updateStatus(preferences.status.copy(counterBackgroundColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                Column {
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Counter Text", subtitle = "Change Color text of counter", colorValue = p.counterTextColor,
                        onColorTap = { pickColor("Counter Text Color", p.counterTextColor) { c -> preferences.updateStatus(preferences.status.copy(counterTextColor = c)) } },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.RadioButtonChecked, title = "Status around profile", subtitle = "Turn on to access status updates by clicking profile picture", value = p.statusAroundProfile,
                        onChanged = { preferences.updateStatus(preferences.status.copy(statusAroundProfile = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.SaveAlt, title = "Save and Mark Seen options", subtitle = "Save button and Mark Seen button to be displayed when you open any status", value = p.saveAndMarkSeenOptions,
                        onChanged = { preferences.updateStatus(preferences.status.copy(saveAndMarkSeenOptions = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.CameraFront, title = "Change Photo Profile / Status Preview", subtitle = "Change display in Stories from Profile picture to Status preview", value = p.changePhotoProfileStatusPreview,
                        onChanged = { preferences.updateStatus(preferences.status.copy(changePhotoProfileStatusPreview = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.HeadsetMic, title = "Start stories directly with sound", subtitle = "You can play the sound in the status or story when the phone is in silent mode or in vibrate mode", value = p.startStoriesWithSound,
                        onChanged = { preferences.updateStatus(preferences.status.copy(startStoriesDirectlyWithSound = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.CheckCircleOutline, title = "Confirm before sending a Status", subtitle = "Confirm dialog will popup before your status is uploaded", value = p.confirmBeforeSendingStatus,
                        onChanged = { preferences.updateStatus(preferences.status.copy(confirmBeforeSendingStatus = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.Timelapse, title = "5-minute status", subtitle = "Allows you to put a 50 minute video instead of 30 seconds as a Status. Note: Only Chaty users can see the full status! Other users will see 30sec only.", value = p.fiveMinuteStatus,
                        onChanged = { preferences.updateStatus(preferences.status.copy(fiveMinuteStatus = it)) },
                    )
                }
            }
        }
    }
    pickers.Host()
}
