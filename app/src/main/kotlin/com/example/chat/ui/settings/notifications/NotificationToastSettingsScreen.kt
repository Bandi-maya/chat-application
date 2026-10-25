package com.example.chat.ui.settings.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VerticalAlignTop
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
 * Port of NotificationToastSettingsScreen. Dart field aliases map to the Kotlin real names
 * (e.g. onlineWithProfilePic -> onlineToastWithProfilePicture); the Disable Contact Online Toast switch
 * writes onlineToastEnabled = !value as Dart's copyWith does. Slider/GbLivePreviewCard(height) assume
 * the kit parameters exist. UNCOMPILED (Compose).
 */
@Composable
fun NotificationToastSettingsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val p = preferences.toasts
    val pickers = rememberGbPickers()
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }
    fun showPositionModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
        "Position of Toast", listOf("Top of the screen", "Center of screen", "Bottom of screen"), current, onSelected = onSelected,
    )
    fun showRingtoneModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
        "Ringtone", listOf("Default Chaty Tone", "Soft Ping", "Pop Bubble", "Chime Bell", "Silent"), current, onSelected = onSelected,
    )
    val fs = config.fontScale.toFloat()

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Notifications", color = Color(config.primaryTextColor), fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            GbCardContainer(headerText = "Contact Online Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Online", p.onlineWithProfilePic, p.onlineElevation, p.onlineRadius, p.onlineBgColor, p.onlineTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show a notification on the phone's status bar", value = p.onlineInNotificationBar,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastInNotificationBar = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.ToggleOn, title = "Disable Contact Online Toast", subtitle = "Turn on to disable toast notifications", value = p.disableContactOnlineToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastEnabled = !it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to toast message", value = p.onlineWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastWithProfilePicture = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.FilterNone, title = "Elevation", subtitle = if (p.onlineElevation) "Enabled" else "Disabled", value = p.onlineElevation,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastElevation = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Rounded corners radius", value = p.onlineRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastRadius = it.toDouble())) },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.MusicNote, title = "Ringtone", subtitle = p.onlineRingtone, hasSubScreen = true,
                        onTap = { showRingtoneModal(p.onlineRingtone) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastRingtone = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.onlinePosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.onlinePosition) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.onlineBgColor,
                        onColorTap = { pickColor("Toast Background Color", p.onlineBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.onlineTextColor,
                        onColorTap = { pickColor("Toast Text Color", p.onlineTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastTextColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer(headerText = "Viewed Story Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Status", p.storyWithProfilePic, p.storyElevation, p.storyRadius, p.storyBgColor, p.storyTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show in status bar", value = p.storyInNotificationBar,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastInNotificationBar = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.HistoryEdu, title = "Viewed Story Toast", subtitle = "Know immediately when someone views your status via Toast message", value = p.viewedStoryToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastEnabled = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to status toast", value = p.storyWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastWithProfilePicture = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Corner curvature", value = p.storyRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastRadius = it.toDouble())) },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.storyPosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.storyPosition) { v -> preferences.updateToasts(preferences.toasts.copy(storyToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.storyBgColor,
                        onColorTap = { pickColor("Story Toast Background Color", p.storyBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.storyTextColor,
                        onColorTap = { pickColor("Story Toast Text Color", p.storyTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastTextColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer(headerText = "Profile Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Profile", p.profileWithProfilePic, p.profileElevation, p.profileRadius, p.profileBgColor, p.profileTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.AccountBox, title = "Profile Toast", subtitle = "Know immediately when anyone views your profile via a Toast message", value = p.profileToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastEnabled = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar", value = p.profileWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastWithProfilePicture = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Corner curvature", value = p.profileRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastRadius = it.toDouble())) },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.profilePosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.profilePosition) { v -> preferences.updateToasts(preferences.toasts.copy(profileToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.profileBgColor,
                        onColorTap = { pickColor("Profile Toast Background Color", p.profileBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.profileTextColor,
                        onColorTap = { pickColor("Profile Toast Text Color", p.profileTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastTextColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer(headerText = "Typing Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ToastPreview("Bandi Maya Typing...", p.typingWithProfilePic, p.typingElevation, p.typingRadius, p.typingBgColor, p.typingTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.Keyboard, title = "Typing Toast", subtitle = "Know immediately when anyone starts typing to you via a floating toast message", value = p.typingToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastEnabled = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to typing alert", value = p.typingWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastWithProfilePicture = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Corner curvature", value = p.typingRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastRadius = it.toDouble())) },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.typingPosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.typingPosition) { v -> preferences.updateToasts(preferences.toasts.copy(typingToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = "Custom background tint", colorValue = p.typingBgColor,
                        onColorTap = { pickColor("Typing Toast Background Color", p.typingBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = "Custom text tint", colorValue = p.typingTextColor,
                        onColorTap = { pickColor("Typing Toast Text Color", p.typingTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastTextColor = c)) } },
                    )
                }
            }

        }
    }
    pickers.Host()
}
