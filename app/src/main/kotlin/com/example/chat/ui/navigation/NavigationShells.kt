package com.example.chat.ui.navigation

import com.example.chat.core.templates.NavigationTemplate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.tasks.TaskAvatar

/** Port of _buildTopWhatsAppShell + _buildTopWhatsAppTabs: accent tab bar
 *  with onPrimary indicator (weight 3.5), bold 14sp labels, scrollable
 *  when more than 4 destinations. On Chats/Groups the tab bar is handed to
 *  the home header slot instead of rendering at the top. */
@Composable
internal fun TopWhatsAppShell(
    colors: ChatyColors,
    template: NavigationTemplate,
    navItemIds: List<String>,
    selectedIndex: Int,
    activeDestinationId: String,
    onSelect: (Int) -> Unit,
    homeHeaderTabsSlot: ((@Composable () -> Unit)?) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val tabsBelongUnderHomeHeader =
        activeDestinationId == ChatyNavigationDestinationIds.chats ||
            activeDestinationId == ChatyNavigationDestinationIds.groups

    val tabs: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(template.height.coerceIn(48.0, 88.0).dp)
                .background(colors.primary)
                .horizontalScroll(rememberScrollState(), enabled = navItemIds.size > 4),
        ) {
            navItemIds.forEachIndexed { index, id ->
                val selected = index == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .then(if (navItemIds.size > 4) Modifier.width(96.dp) else Modifier.weight(1f))
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            NavigationCatalog.labelFor(id),
                            color = if (selected) colors.onPrimary else colors.onPrimary.copy(alpha = 0.72f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = 0.2.sp,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(if (selected) colors.onPrimary else Color.Transparent),
                    )
                }
            }
        }
    }
    if (tabsBelongUnderHomeHeader) homeHeaderTabsSlot(tabs) else homeHeaderTabsSlot(null)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!tabsBelongUnderHomeHeader) tabs()
            Box(Modifier.weight(1f)) { content() }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
    }
}

/** Port of _buildFloatingIslandRailShell: 68-wide floating rail with
 *  traffic dots, bolt logo mark, destination icons, signed-in user avatar. */
@Composable
internal fun FloatingIslandRailShell(
    colors: ChatyColors,
    navItemIds: List<String>,
    selectedIndex: Int,
    userInitials: String,
    onSelect: (Int) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(start = 10.dp, top = 12.dp, bottom = 12.dp)
                    .width(68.dp)
                    .fillMaxHeight()
                    .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.surface),
            ) {
                Spacer(Modifier.height(14.dp))
                Row {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(colors.error))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(colors.warning))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(colors.success))
                }
                Spacer(Modifier.height(16.dp))
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(colors.divider))
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Top,
                ) {
                    navItemIds.forEachIndexed { i, id ->
                        val isSel = selectedIndex == i
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) colors.primary else Color.Transparent)
                                .clickable { onSelect(i) }
                                .padding(10.dp),
                        ) {
                            NavDestinationIcon(
                                id,
                                size = 20,
                                tint = if (isSel) colors.onPrimary else colors.foregroundSecondary,
                            )
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(colors.divider))
                Spacer(Modifier.height(12.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceSecondary),
                ) {
                    Text(userInitials, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
            }
            Box(Modifier.weight(1f)) { content() }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
    }
}

/** Port of _buildModernSideMenuShell: modal-style side drawer with the
 *  identity header (display-name resolution with the Dart override
 *  exclusions) and rounded selection tiles, plus a header row with the
 *  menu button. */
@Composable
internal fun ModernSideMenuShell(
    colors: ChatyColors,
    navItemIds: List<String>,
    selectedIndex: Int,
    currentUser: UserProfile?,
    myNameOverride: () -> String,
    onSelect: (Int) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var drawerOpen by remember { mutableStateOf(false) }

    // Dart display-name resolution.
    val override = myNameOverride().trim()
    val effectiveOverride =
        if (override.isNotEmpty() && override != "Alex Rivera" && override != "Bandi Maya") override else ""
    val rawName = currentUser?.displayName?.trim() ?: ""
    val displayName = if (effectiveOverride.isNotEmpty()) effectiveOverride
        else if (rawName.isNotEmpty() && rawName != "Chaty User" && rawName != "Bandi Maya") rawName
        else currentUser?.username?.ifEmpty { "User" } ?: "User"

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            // TemplateShellHeader equivalent.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = { drawerOpen = true }) {
                    Icon(Icons.Rounded.Menu, contentDescription = "Open menu", tint = colors.foreground)
                }
                Text(
                    NavigationCatalog.labelFor(navItemIds.getOrElse(selectedIndex) { "" }),
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Box(Modifier.weight(1f)) { content() }
        }

        if (drawerOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { drawerOpen = false },
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(colors.surfaceSecondary)
                    .clickable(enabled = false) {},
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp),
                ) {
                    TaskAvatar(
                        currentUser?.avatarInitials?.ifEmpty { "CU" } ?: "CU",
                        currentUser?.avatarColorHex,
                        48,
                        colors,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(displayName, color = colors.foreground, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(
                            "@${currentUser?.username?.ifEmpty { "chaty" } ?: "chaty"}",
                            color = colors.foregroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp)) {
                    navItemIds.forEachIndexed { i, id ->
                        val isSel = selectedIndex == i
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSel) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable {
                                    drawerOpen = false
                                    onSelect(i)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            NavDestinationIcon(
                                id,
                                size = 24,
                                tint = if (isSel) colors.primary else colors.foregroundSecondary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                NavigationCatalog.labelFor(id),
                                color = if (isSel) colors.foreground else colors.foregroundSecondary,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
    }
}
