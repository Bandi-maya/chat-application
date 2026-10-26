package com.example.chat.ui.settings.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MenuOpen
import androidx.compose.material.icons.rounded.MoveDown
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.ChatyOptionGallery
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/** Args for the GbLiveHeaderPreview slot (gb_design_system.dart GbLiveHeaderPreview). */
data class HeaderPreviewArgs(
    val displayName: String, val statusText: String, val setMyName: Boolean,
    val disableStatusUnderName: Boolean, val disableSearchBar: Boolean, val showSearchBar: Boolean,
    val showCameraIcon: Boolean, val showDesktopIcon: Boolean, val showIconAddAccount: Boolean,
    val searchPlacement: String, val separateChatsAndGroups: Boolean, val homeStyle: String,
    val tabBubbleStyle: String, val pagerTransition3d: String, val onThreeDotsClick: () -> Unit,
)

private fun homeUiStyleIcon(style: String): ImageVector = when (style) {
    "ONE UI" -> Icons.Rounded.ViewQuilt
    "WhatsApp UI Stock" -> Icons.Rounded.Chat
    "IOS STYLE" -> Icons.Rounded.PhoneIphone
    "BUBBLES TAB STYLE" -> Icons.Rounded.BubbleChart
    "BASIC TAB STYLE" -> Icons.Rounded.Dashboard
    "WhatsApp OLD UI" -> Icons.Rounded.ViewWeek
    "Floating Rail" -> Icons.Rounded.ViewSidebar
    "3D Perspective Drawer" -> Icons.Rounded.ViewInAr
    "Modern Side Menu" -> Icons.Rounded.MenuOpen
    "Curved Radial Menu" -> Icons.Rounded.DonutLarge
    "Instagram Style" -> Icons.Rounded.CameraAlt
    "Telegram Style" -> Icons.Rounded.Send
    else -> Icons.Rounded.DashboardCustomize
}

/**
 * Port of HeaderSettingsScreen. UNCOMPILED (Compose).
 * Inputs the Dart screen reads from locators: [navigationMode]/[onSetNavigationMode] =
 * ThemeController.navigationMode/setNavigationMode; [currentUserDisplayName]/[currentUserUsername]/
 * [currentUserAbout] = ChatyDataStore.currentUser; [headerPreview] renders GbLiveHeaderPreview and
 * [showOverflowMenu] GbHeaderOverflowMenu (ported separately; not in this file).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderSettingsScreen(
    preferences: ChatyPreferencesController,
    navigationMode: AppNavigationMode,
    onSetNavigationMode: (AppNavigationMode) -> Unit,
    currentUserDisplayName: String?,
    currentUserUsername: String?,
    currentUserAbout: String?,
    headerPreview: @Composable (HeaderPreviewArgs) -> Unit,
    showOverflowMenu: (onSettings: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val home = preferences.home
    val pickers = rememberGbPickers()
    var showStyleSheet by remember { mutableStateOf(false) }

    fun updateHome(p: HomePreferences, logTitle: String? = null) = preferences.updateHome(p, logTitle = logTitle)

    val override = home.myNameOverride.trim()
    val effectiveOverride = if (override.isNotEmpty() && override != "Alex Rivera" && override != "Bandi Maya") override else ""
    val rawName = currentUserDisplayName?.trim() ?: ""
    val displayName = when {
        effectiveOverride.isNotEmpty() -> effectiveOverride
        rawName.isNotEmpty() && rawName != "Bandi Maya" && rawName != "Chaty User" -> rawName
        !currentUserUsername.isNullOrEmpty() -> currentUserUsername
        else -> "User"
    }
    val statusText = if (!currentUserAbout.isNullOrEmpty()) currentUserAbout else "Available"
    val effectiveSearchPlacement =
        if (home.disableSearchBar && home.searchPlacement.trim().lowercase() == "header action") "Below header" else home.searchPlacement
    val selectedStyle = HomeUiStyles.selectedHomeUiStyle(home.homeStyle, navigationMode)

    fun rowIcon(icon: ImageVector, tint: Color = colors.primary): @Composable () -> Unit = {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
    }

    @Composable
    fun divider() = HorizontalDivider(
        Modifier.padding(start = 68.dp, end = 16.dp), thickness = 0.8.dp, color = Color(config.cardColor),
    )

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Header", color = colors.foreground, fontSize = 19.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp)
        }
        headerPreview(
            HeaderPreviewArgs(
                displayName, statusText, home.setMyName, home.disableStatusUnderName, home.disableSearchBar,
                home.showSearchBar, home.showCameraIcon, home.showDesktopIcon, home.showIconAddAccount,
                effectiveSearchPlacement, home.separateChatsAndGroups, selectedStyle,
                home.tabBubbleStyle, home.pagerTransition3d,
            ) { showOverflowMenu(onBack) },
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp, 4.dp, 14.dp, 24.dp)) {
            Column(
                Modifier.fillMaxWidth().background(colors.surface).border(1.1.dp, colors.borderSubtle),
            ) {
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.Layers), title = "Home UI Style", subtitle = selectedStyle,
                    showChevron = true, onTap = { showStyleSheet = true },
                )
                divider()
                ChatyOptionGallery(
                    title = "Tab Bubble Style",
                    subtitle = "Cards show a generic icon. Tap a style and the header preview above shows the one selected.",
                    values = HomeUiStyles.tabBubbleStyles,
                    selected = if (home.tabBubbleStyle.isNotEmpty()) home.tabBubbleStyle else "Capsule Pill",
                    labelOf = { it },
                    onSelected = { updateHome(home.copy(tabBubbleStyle = it), "Tab Bubble Style") },
                ) { Icon(Icons.Rounded.ChatBubbleOutline, null, tint = colors.primary, modifier = Modifier.size(26.dp)) }
                divider()
                ChatyOptionGallery(
                    title = "3D Pager / Slider Effect",
                    subtitle = "Cards show a generic icon. Tap an effect to use it when swiping between tabs.",
                    values = HomeUiStyles.pager3dStyles,
                    selected = if (home.pagerTransition3d.isNotEmpty()) home.pagerTransition3d else "Cube 3D",
                    labelOf = { it },
                    onSelected = { updateHome(home.copy(pagerTransition3d = it), "3D Slider Effect") },
                ) { Icon(Icons.Rounded.ViewInAr, null, tint = colors.primary, modifier = Modifier.size(26.dp)) }
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Rounded.DonutLarge), title = "Enable Instagram-like Stories",
                    switchValue = home.enableStoriesStrip,
                    onSwitchChanged = { updateHome(home.copy(enableStoriesStrip = it), "Stories Strip") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.BubbleChart), title = "Carousel View",
                    subtitle = "Enable Instagram-like Stories", switchValue = home.carouselView,
                    onSwitchChanged = { updateHome(home.copy(carouselView = it), "Carousel View") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Rounded.BubbleChart), title = "Stories Style",
                    subtitle = if (home.storiesStyle.isNotEmpty()) home.storiesStyle else "Instagram",
                    showChevron = true,
                    onTap = {
                        pickers.showRadio(
                            "Stories Style", HomeUiStyles.storiesStyles,
                            if (home.storiesStyle.isNotEmpty()) home.storiesStyle else "Instagram",
                        ) { chosen -> updateHome(preferences.home.copy(storiesStyle = chosen), "Stories Style") }
                    },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Rounded.AltRoute), title = "Separate Chats/Groups",
                    subtitle = SEPARATE_SUBTITLE, switchValue = home.separateChatsAndGroups,
                    onSwitchChanged = { updateHome(home.copy(separateChatsAndGroups = it), "Separate Chats/Groups") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.Badge), title = "Set My Name",
                    subtitle = "Set your name instead of Chaty in Home Screen", switchValue = home.setMyName,
                    onSwitchChanged = { updateHome(home.copy(setMyName = it), "Set My Name") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.EventBusy), title = "Disable Status under my name",
                    subtitle = "Removes your status line under your name in Main Screen", switchValue = home.disableStatusUnderName,
                    onSwitchChanged = { updateHome(home.copy(disableStatusUnderName = it), "Disable Status") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.Inbox), title = "Hide chat sort list",
                    subtitle = "Hide the chat sort list from the main screen.", switchValue = home.hideChatSortList,
                    onSwitchChanged = { updateHome(home.copy(hideChatSortList = it), "Hide Sort List") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Rounded.ManageSearch), title = "Search placement",
                    subtitle = effectiveSearchPlacement, showChevron = true,
                    onTap = {
                        pickers.showRadio(
                            "Search placement", HomeUiStyles.searchPlacementOptions,
                            if (effectiveSearchPlacement in HomeUiStyles.searchPlacementOptions) effectiveSearchPlacement else "Header action",
                        ) { chosen ->
                            updateHome(
                                preferences.home.copy(searchPlacement = chosen, disableSearchBar = chosen != "Header action"),
                                "Search placement",
                            )
                        }
                    },
                )
                divider()
                val airplane = home.airplaneModeSimulator || preferences.gbBool("yo_want_airplanemode")
                GbSettingRow(
                    iconContent = rowIcon(Icons.Rounded.AirplanemodeActive, if (home.airplaneModeSimulator) colors.primary else colors.foregroundSecondary),
                    title = "Airplane mode",
                    subtitle = if (airplane) "Enabled \u2014 Chaty presence is offline until you turn it off"
                    else "Simulate offline presence; you can turn this on or off anytime",
                    switchValue = airplane,
                    onSwitchChanged = {
                        updateHome(home.copy(airplaneModeSimulator = it), "Airplane Mode")
                        preferences.updateGbFeature("yo_want_airplanemode", it, logTitle = "Airplane Mode")
                    },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Rounded.MoveDown), title = "Move search out of header",
                    subtitle = if (home.searchPlacement == "Above chat filters") "Search appears above the chat filter tags."
                    else if (home.disableSearchBar) "Search appears below the header."
                    else "Move the header search action below the header or above chat filters.",
                    switchValue = home.disableSearchBar || home.searchPlacement != "Header action",
                    onSwitchChanged = { on ->
                        val target = if (on) (if (effectiveSearchPlacement == "Above chat filters") "Above chat filters" else "Below header") else "Header action"
                        updateHome(
                            home.copy(disableSearchBar = on, searchPlacement = target),
                            if (on) "Move Search Out of Header" else "Move Search Into Header",
                        )
                    },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Rounded.SearchOff), title = "Hide search everywhere",
                    subtitle = "Temporarily hides search from the header, below-header area, and chat filters.",
                    switchValue = !home.showSearchBar,
                    onSwitchChanged = { on ->
                        updateHome(home.copy(showSearchBar = !on), if (on) "Hide Search Everywhere" else "Show Search Everywhere")
                    },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.Palette), title = "Themes", switchValue = home.themesEnabled,
                    onSwitchChanged = { updateHome(home.copy(themesEnabled = it), "Themes Enabled") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.VisibilityOff), title = "Ghost mode",
                    subtitle = "Show the ghost mode pill in the header.", switchValue = home.showGhostPill,
                    onSwitchChanged = { updateHome(home.copy(showGhostPill = it), if (it) "Show Ghost Pill" else "Hide Ghost Pill") },
                )
                divider()
                GbSettingRow(
                    iconContent = rowIcon(Icons.Outlined.PersonAddAlt1), title = "Show icon Add Account",
                    switchValue = home.showIconAddAccount,
                    onSwitchChanged = { updateHome(home.copy(showIconAddAccount = it), "Show Add Account") },
                )
            }
        }
    }

    if (showStyleSheet) {
        HomeUiStyleSheet(
            selected = selectedStyle, navigationMode = navigationMode,
            onDismiss = { showStyleSheet = false },
            onApply = { chosen ->
                showStyleSheet = false
                onSetNavigationMode(HomeUiStyles.navigationModeForHomeUiStyle(chosen, navigationMode))
                updateHome(preferences.home.copy(homeStyle = chosen), "Home UI Style")
            },
        )
    }
    pickers.Host()
}

private const val SEPARATE_SUBTITLE = "The Status page will be replaced with Group chats, and \"Instagram-like stories\" will be forced."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeUiStyleSheet(
    selected: String,
    navigationMode: AppNavigationMode,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    var draft by remember { mutableStateOf(selected) }
    val screenH = LocalConfiguration.current.screenHeightDp
    ModalBottomSheet(tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = com.example.chat.ui.kit.ChatyModalShape, containerColor = Color(config.surfaceColor), dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().height((screenH * 0.84f).dp)) {
            Text("Home UI Style", color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.W800, modifier = Modifier.padding(20.dp, 2.dp, 20.dp, 0.dp))
            Text("Select a layout to see a live preview, then apply it.", color = colors.foregroundSecondary, fontSize = 12.sp, modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 10.dp))
            Box(Modifier.padding(horizontal = 18.dp)) {
                AnimatedContent(
                    targetState = draft,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) }, label = "homePreview",
                ) { d -> HomeUiPreview(d, config, navigationMode, large = true) }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 2.dp, 14.dp, 10.dp)) {
                itemsIndexed(HomeUiStyles.homeUiStyles) { index, option ->
                    val isSelected = draft == option
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    Row(
                        Modifier.fillMaxWidth().clickable { draft = option }.padding(horizontal = 7.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(28.dp).background((if (isSelected) colors.primary else colors.foregroundSecondary).copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(homeUiStyleIcon(option), null, tint = if (isSelected) colors.primary else colors.foregroundSecondary, modifier = Modifier.size(17.dp)) }
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.width(72.dp)) { HomeUiPreview(option, config, navigationMode, large = false) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option, color = if (isSelected) colors.primary else colors.foreground, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.W800 else FontWeight.W600)
                            Spacer(Modifier.height(2.dp))
                            Text(HomeUiStyles.description(option), color = colors.foregroundSecondary, fontSize = 10.5.sp, lineHeight = 12.6.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (isSelected) colors.primary else colors.foregroundTertiary, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(18.dp, 6.dp, 18.dp, 14.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("Apply style") }
            }
        }
    }
}
