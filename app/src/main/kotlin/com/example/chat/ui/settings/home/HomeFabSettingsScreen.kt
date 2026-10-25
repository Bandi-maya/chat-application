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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Settings as RoundedSettings
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

@Composable
private fun FabDot(radius: Int, bg: Color, icon: ImageVector, tint: Color, iconSize: Int, bottomPad: Int) {
    Box(Modifier.padding(bottom = bottomPad.dp).size((radius * 2).dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

/**
 * Port of HomeFabSettingsScreen. Dart's hideCutterFab is a getter alias of hideCutEditFab; the
 * copyWith(hideCutterFab:) maps to hideCutEditFab. UNCOMPILED (Compose).
 */
@Composable
fun HomeFabSettingsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
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
    val normalColor = colorOr(p.fabNormalColor, accent)
    val pressedColor = colorOr(p.fabPressedColor, accent.copy(alpha = 0.8f))
    val iconColor = colorOr(p.fabIconsColor, Color.White)
    val fs = config.fontScale.toFloat()
    val redAccent = Color(0xFFFF5252)
    val colors = com.example.chat.ui.theme.LocalAppColors.current

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Floating Action Button", color = Color(config.primaryTextColor), fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            GbCardContainer {
                Column {
                    GbLivePreviewCard {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("FAB Interactive Preview", color = Color(config.primaryTextColor), fontWeight = FontWeight.W700, fontSize = 14.sp)
                                Box(
                                    Modifier.background(if (p.hideFab) redAccent.copy(alpha = 0.15f) else normalColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(if (p.hideFab) "Hidden" else "Visible", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (p.hideFab) redAccent else normalColor)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(
                                Modifier.height(130.dp).fillMaxWidth()
                                    .background(Color(config.backgroundColor), RoundedCornerShape(12.dp))
                                    .border(1.dp, colors.divider.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            ) {
                                Text(
                                    if (p.hideFab) "FAB is currently hidden" else "Speed Dial Actions",
                                    color = Color(config.secondaryTextColor), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center),
                                )
                                if (!p.hideFab) {
                                    Column(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.End) {
                                        if (!p.hideGbwaSettingsFab) FabDot(14, pressedColor, Icons.Filled.Settings, iconColor, 14, 6)
                                        if (!p.hideCutEditFab) FabDot(14, pressedColor, Icons.Filled.ContentCut, iconColor, 14, 6)
                                        if (!p.hideLastSeenFab) FabDot(14, pressedColor, Icons.Rounded.HistoryToggleOff, iconColor, 14, 6)
                                        FabDot(24, normalColor, if (p.showMetaAiIcon) Icons.Rounded.Psychology else Icons.Rounded.ChatBubbleOutline, iconColor, 24, 0)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                Column {
                    GbSwitchTile(
                        icon = Icons.Rounded.VisibilityOff, title = "Hide FAB", subtitle = "Remove the floating action button from the home screen", value = p.hideFab,
                        onChanged = { preferences.updateHome(preferences.home.copy(hideFab = it)) },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Lens, title = "Fab normal", subtitle = "Change Color of Fab Normal", colorValue = p.fabNormalColor,
                        onColorTap = { pickColor("Fab Normal Color", p.fabNormalColor) { c -> preferences.updateHome(preferences.home.copy(fabNormalColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.TouchApp, title = "Fab Pressed", subtitle = "Change Color of Fab pressed", colorValue = p.fabPressedColor,
                        onColorTap = { pickColor("Fab Pressed Color", p.fabPressedColor) { c -> preferences.updateHome(preferences.home.copy(fabPressedColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Outlined.Palette, title = "Fab Icons Color", subtitle = "Change Color of Fab icons", colorValue = p.fabIconsColor,
                        onColorTap = { pickColor("Fab Icons Color", p.fabIconsColor) { c -> preferences.updateHome(preferences.home.copy(fabIconsColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer {
                Column {
                    GbSwitchTile(
                        icon = Icons.Rounded.Psychology, title = "Show icon Meta AI", subtitle = "You can hide the Meta AI icon from your home screen", value = p.showMetaAiIcon,
                        onChanged = { preferences.updateHome(preferences.home.copy(showMetaAiIcon = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.MarkEmailUnread, title = "Hide New Message FAB", subtitle = "Hide new chat quick button from FAB speed dial", value = p.hideNewMessageFab,
                        onChanged = { preferences.updateHome(preferences.home.copy(hideNewMessageFab = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.HistoryToggleOff, title = "Hide LastSeen FAB", subtitle = "Hide quick freeze last seen toggle button", value = p.hideLastSeenFab,
                        onChanged = { preferences.updateHome(preferences.home.copy(hideLastSeenFab = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.ContentCut, title = "Hide ✂️ FAB", subtitle = "Hide quick restart/cutter tool button", value = p.hideCutEditFab,
                        onChanged = { preferences.updateHome(preferences.home.copy(hideCutEditFab = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.Extension, title = "Hide plugins list", subtitle = "Hide plugins drawer trigger from FAB", value = p.hidePluginsList,
                        onChanged = { preferences.updateHome(preferences.home.copy(hidePluginsList = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.RoundedSettings, title = "Hide Chaty Settings FAB", subtitle = "Hide quick Chaty settings gear button from FAB speed dial", value = p.hideGbwaSettingsFab,
                        onChanged = { preferences.updateHome(preferences.home.copy(hideGbwaSettingsFab = it)) },
                    )
                }
            }
        }
    }
    pickers.Host()
}
