package com.example.chat.ui.settings.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.outlined.Call as CallOutlined
import androidx.compose.material.icons.outlined.Circle as CircleOutlined
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.CurvedRadialDrawerShell
import com.example.chat.core.preferences.HomePresetNormalizer
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.chats.home.ConversationRow
import com.example.chat.ui.chats.home.HomeFilterChips
import com.example.chat.ui.chats.home.HomePresence
import com.example.chat.ui.chats.home.HomeStyleSpec
import com.example.chat.ui.chats.home.defaultHomeTemplates
import com.example.chat.ui.chats.home.homeFilters
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private fun col(v: Long) = Color(v)
private fun Color.a(alpha: Float) = copy(alpha = alpha)

/**
 * Port of gb_design_system.dart GbLiveHeaderPreview (all 12 header + bottom bar builders, search
 * placement previews, action icons, sample chat row). Colors come from the live global theme
 * ([t]). UNCOMPILED (Compose), no pixel check. Dart IconButton(VisualDensity.compact) is a 40dp
 * target here.
 */
@Composable
fun GbLiveHeaderPreview(a: HeaderPreviewArgs, t: ThemeConfig, modifier: Modifier = Modifier) {
    val title = if (a.setMyName && a.displayName.isNotEmpty()) a.displayName else "Chaty"
    val hs = a.homeStyle
    val isOneUi = hs == "ONE UI"
    val isIos = hs == "IOS STYLE"
    val isStock = hs == "WhatsApp UI Stock"
    val isBubbles = hs == "BUBBLES TAB STYLE"
    val isBasicTabs = hs == "BASIC TAB STYLE"
    val isInstagram = hs == "Instagram Style"
    val isTelegram = hs == "Telegram Style"
    val isFloatingRail = hs == "Floating Rail"
    val isPerspectiveDrawer = hs == "3D Perspective Drawer"
    val isModernDrawer = hs == "Modern Side Menu"
    val isCurvedRadial = hs == "Curved Radial Menu"
    val isOldUi = hs == "WhatsApp OLD UI"
    val sp = a.searchPlacement.trim().lowercase()
    val showHeaderSearch = a.showSearchBar && !a.disableSearchBar && sp == "header action"
    val showInlineSearch = a.showSearchBar && sp != "header action"
    val radius = t.cornerRadius.toFloat().coerceIn(0f, 32f).dp
    val shape = RoundedCornerShape(radius)
    val bg = col(t.backgroundColor)

    if (isCurvedRadial) {
        CurvedRadialActualPreview(a, t, title, modifier)
        return
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .shadow(8.dp, shape, ambientColor = bg.a(0.4f), spotColor = bg.a(0.4f))
            .clip(shape).background(bg).border(1.2.dp, col(t.cardColor), shape),
    ) {
        Row(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(col(t.accentColor), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                "LIVE PREVIEW \u2022 ${a.homeStyle}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = col(t.accentColor), fontSize = 11.sp, fontWeight = FontWeight.W800, letterSpacing = 0.6.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "3D: ${a.pagerTransition3d}", maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                color = col(t.secondaryTextColor), fontSize = 10.5.sp, fontWeight = FontWeight.W600,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        when {
            isOneUi -> OneUiHeader(t, a, title, showHeaderSearch)
            isIos -> IosHeader(t, a, title, showHeaderSearch)
            isStock -> StockHeader(t, a, title, showHeaderSearch)
            isBubbles -> BubblesHeader(t, a, title, showHeaderSearch)
            isOldUi -> OldUiHeader(t, a, title, showHeaderSearch)
            isInstagram -> InstagramHeader(t, a, title, showHeaderSearch)
            isTelegram -> TelegramHeader(t, a, title, showHeaderSearch)
            isCurvedRadial -> CurvedRadialHeader(t, a, title)
            isFloatingRail -> FloatingRailHeader(t, title)
            isPerspectiveDrawer || isModernDrawer -> DrawerHeader(t, a, title)
            else -> BasicHeader(t, a, title, showHeaderSearch)
        }
        if ((a.showCameraIcon && !isStock && !isOldUi) || a.showDesktopIcon || a.showIconAddAccount) {
            Row(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 4.dp), horizontalArrangement = Arrangement.End) {
                if (a.showCameraIcon && !isStock && !isOldUi) HeaderActionIcon(t, Icons.Outlined.PhotoCamera)
                if (a.showDesktopIcon) HeaderActionIcon(t, Icons.Rounded.QrCodeScanner)
                if (a.showIconAddAccount) HeaderActionIcon(t, Icons.Rounded.PersonAddAlt1)
            }
        }
        if (showInlineSearch && sp == "below header") SearchPlacementPreview(t)
        if (showInlineSearch && sp == "above chat filters") SearchAboveFiltersPreview(t)
        // Real home components: the chip row is the same HomeFilterChips the home screen
        // renders (so Tab Bubble Style is visible here) and the row is the real ConversationRow.
        var previewFilter by remember { mutableStateOf("All") }
        HomeFilterChips(
            theme = t,
            tabBubbleStyle = a.tabBubbleStyle,
            filters = homeFilters(null),
            selectedFilter = previewFilter,
            onSelect = { previewFilter = it },
        )
        SampleConversationRow(t, a)
        when {
            isOneUi -> OneUiBottomBar(t)
            isIos -> IosBottomBar(t)
            isStock -> StockBottomBar(t)
            isBubbles -> BubblesBottomBar(t)
            isOldUi -> OldUiBottomBar(t)
            isInstagram -> InstagramBottomBar(t)
            isTelegram -> TelegramBottomBar(t)
            isBasicTabs -> BasicBottomBar(t)
            isFloatingRail -> FloatingRailBottomBar(t)
            isCurvedRadial -> CurvedRadialBottomBar(t)
            isPerspectiveDrawer || isModernDrawer -> DrawerBottomBar(t)
            else -> BasicBottomBar(t)
        }
    }
}

@Composable private fun Ic(v: ImageVector, c: Color, size: Dp = 24.dp) = Icon(v, null, tint = c, modifier = Modifier.size(size))
@Composable private fun RowScope.Sp() = Spacer(Modifier.weight(1f))

@Composable
private fun TapIcon(v: ImageVector, c: Color, size: Dp, onClick: () -> Unit) =
    Box(Modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) { Ic(v, c, size) }

@Composable
private fun CompactIconButton(v: ImageVector, c: Color, size: Dp, onClick: () -> Unit) =
    Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Ic(v, c, size) }

@Composable
private fun HeaderActionIcon(t: ThemeConfig, v: ImageVector) =
    Box(Modifier.padding(start = 11.dp)) { Ic(v, col(t.primaryTextColor), 18.dp) }

@Composable
private fun SearchPlacementPreview(t: ThemeConfig) {
    val r = t.cornerRadius.toFloat().coerceIn(0f, 28f).dp
    Box(Modifier.padding(14.dp, 0.dp, 14.dp, 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(col(t.cardColor), RoundedCornerShape(r))
                .border(1.dp, col(t.secondaryTextColor).a(0.24f), RoundedCornerShape(r)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(Icons.Rounded.Search, col(t.secondaryTextColor), 15.dp)
            Spacer(Modifier.width(7.dp))
            Text("Search chats", color = col(t.secondaryTextColor), fontSize = 11.sp)
            Sp()
            Ic(Icons.Rounded.Tune, col(t.secondaryTextColor), 14.dp)
        }
    }
}

@Composable
private fun SearchAboveFiltersPreview(t: ThemeConfig) {
    Column(Modifier.padding(14.dp, 0.dp, 14.dp, 8.dp)) {
        // Dart nests _buildSearchPlacementPreview (with its own padding) inside this padding.
        SearchPlacementPreview(t)
        Row {
            for (tag in listOf("All", "Unread", "Groups")) {
                val on = tag == "All"
                Box(
                    Modifier.padding(end = 6.dp)
                        .background(if (on) col(t.accentColor).a(0.14f) else col(t.cardColor))
                        .border(1.dp, if (on) col(t.accentColor).a(0.45f) else col(t.secondaryTextColor).a(0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(tag, color = if (on) col(t.accentColor) else col(t.secondaryTextColor), fontSize = 9.sp, fontWeight = if (on) FontWeight.W700 else FontWeight.W500)
                }
            }
        }
    }
}

@Composable
private fun InstagramHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    val fg = col(t.primaryTextColor)
    Row(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(14.dp, 10.dp, 14.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.W800, modifier = Modifier.weight(1f))
        if (hdrSearch) Ic(Icons.Rounded.Search, fg, 21.dp)
        Spacer(Modifier.width(12.dp))
        Ic(Icons.Rounded.FavoriteBorder, fg, 21.dp)
        Spacer(Modifier.width(10.dp))
        TapIcon(Icons.Rounded.MoreHoriz, fg, 22.dp, a.onThreeDotsClick)
    }
}

@Composable
private fun TelegramHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    val sec = col(t.secondaryTextColor)
    Column(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(14.dp, 9.dp, 14.dp, 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(Icons.Rounded.Menu, col(t.accentColor), 21.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, color = col(t.primaryTextColor), fontSize = 19.sp, fontWeight = FontWeight.W700, modifier = Modifier.weight(1f))
            if (hdrSearch) Ic(Icons.Rounded.Search, sec, 21.dp)
            Spacer(Modifier.width(12.dp))
            TapIcon(Icons.Rounded.MoreVert, sec, 21.dp, a.onThreeDotsClick)
        }
        Spacer(Modifier.height(8.dp))
        if (hdrSearch) {
            Row(
                Modifier.fillMaxWidth().height(30.dp).background(col(t.cardColor)).border(1.dp, sec.a(0.28f)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(Icons.Rounded.Search, sec, 15.dp)
                Spacer(Modifier.width(6.dp))
                Text("Search chats", color = sec, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CurvedRadialHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String) {
    Row(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(14.dp, 10.dp, 14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(col(t.accentColor).a(0.16f), CircleShape).border(1.dp, col(t.accentColor), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Ic(Icons.Rounded.Menu, col(t.accentColor), 18.dp) }
        Spacer(Modifier.width(10.dp))
        Text(title, color = col(t.primaryTextColor), fontSize = 18.sp, fontWeight = FontWeight.W800, modifier = Modifier.weight(1f))
        TapIcon(Icons.Rounded.MoreVert, col(t.primaryTextColor), 22.dp, a.onThreeDotsClick)
    }
}

@Composable
private fun FloatingRailHeader(t: ThemeConfig, title: String) {
    Row(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.width(30.dp).height(54.dp).background(col(t.surfaceColor)).border(1.dp, col(t.cardColor)),
            verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ic(Icons.Rounded.ChatBubble, col(t.accentColor), 16.dp)
            Ic(Icons.Rounded.AutoStories, col(t.secondaryTextColor), 16.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = col(t.primaryTextColor), fontSize = 19.sp, fontWeight = FontWeight.W800, modifier = Modifier.weight(1f))
        Ic(Icons.Rounded.Search, col(t.secondaryTextColor))
    }
}

@Composable
private fun DrawerHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String) {
    Row(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(14.dp, 10.dp, 14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.width(82.dp).height(50.dp).background(col(t.surfaceColor)).border(1.dp, col(t.cardColor)).padding(5.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(col(t.accentColor)))
            Box(Modifier.fillMaxWidth().height(2.dp).background(col(t.secondaryTextColor)))
            Box(Modifier.fillMaxWidth().height(2.dp).background(col(t.secondaryTextColor).a(0.55f)))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = col(t.primaryTextColor), fontSize = 18.sp, fontWeight = FontWeight.W800, modifier = Modifier.weight(1f))
        TapIcon(Icons.Rounded.MoreVert, col(t.primaryTextColor), 24.dp, a.onThreeDotsClick)
    }
}

@Composable
private fun InstagramBottomBar(t: ThemeConfig) {
    val fg = col(t.primaryTextColor)
    Row(Modifier.fillMaxWidth().height(44.dp).background(col(t.backgroundColor)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Ic(Icons.Rounded.Home, fg, 23.dp); Ic(Icons.Rounded.Search, fg, 23.dp); Ic(Icons.Outlined.AddBox, fg, 23.dp)
        Ic(Icons.Rounded.PlayCircleOutline, fg, 23.dp); Ic(Icons.Rounded.PersonOutline, fg, 23.dp)
    }
}

@Composable
private fun RowScope.PreviewTab(t: ThemeConfig, icon: ImageVector, label: String, selected: Boolean = false) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Ic(icon, if (selected) col(t.accentColor) else col(t.secondaryTextColor), 15.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) col(t.primaryTextColor) else col(t.secondaryTextColor), fontSize = 8.5.sp,
            fontWeight = if (selected) FontWeight.W800 else FontWeight.W500,
        )
    }
}

@Composable
private fun TelegramBottomBar(t: ThemeConfig) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(col(t.cardColor)))
        Row(Modifier.fillMaxWidth().height(49.dp).background(col(t.surfaceColor)), horizontalArrangement = Arrangement.SpaceEvenly) {
            PreviewTab(t, Icons.Rounded.ChatBubble, "Chats", true); PreviewTab(t, Icons.Rounded.Contacts, "Contacts")
            PreviewTab(t, Icons.Rounded.Call, "Calls"); PreviewTab(t, Icons.Rounded.Settings, "Settings")
        }
    }
}

@Composable
private fun FloatingRailBottomBar(t: ThemeConfig) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(col(t.backgroundColor)), horizontalArrangement = Arrangement.SpaceEvenly) {
        PreviewTab(t, Icons.Rounded.ChatBubble, "Chats", true); PreviewTab(t, Icons.Rounded.AutoStories, "Updates"); PreviewTab(t, Icons.Rounded.Call, "Calls")
    }
}

@Composable
private fun CurvedRadialBottomBar(t: ThemeConfig) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(col(t.surfaceColor)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(Icons.Rounded.ChatBubble, Icons.Rounded.AutoStories, Icons.Rounded.Call, Icons.Rounded.Settings).forEach {
            Box(
                Modifier.size(28.dp).background(col(t.accentColor).a(0.14f), CircleShape).border(1.dp, col(t.accentColor).a(0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Ic(it, col(t.accentColor), 14.dp) }
        }
    }
}

@Composable
private fun DrawerBottomBar(t: ThemeConfig) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(col(t.surfaceColor)), horizontalArrangement = Arrangement.SpaceEvenly) {
        PreviewTab(t, Icons.Rounded.ChatBubble, "Chats", true); PreviewTab(t, Icons.Rounded.AutoStories, "Updates")
        PreviewTab(t, Icons.Rounded.Call, "Calls"); PreviewTab(t, Icons.Rounded.Settings, "Settings")
    }
}

@Composable
private fun OneUiHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    Column(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(16.dp, 12.dp, 16.dp, 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (hdrSearch) CompactIconButton(Icons.Rounded.Search, col(t.primaryTextColor), 21.dp) {}
            CompactIconButton(Icons.Rounded.MoreVert, col(t.primaryTextColor), 21.dp, a.onThreeDotsClick)
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(title, color = col(t.primaryTextColor), fontSize = 23.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.4).sp)
            if (!a.disableStatusUnderName && a.statusText.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(a.statusText, color = col(t.secondaryTextColor), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            PillTab(t, "Chats", true, "7"); Spacer(Modifier.width(8.dp))
            if (a.separateChatsAndGroups) { PillTab(t, "Groups"); Spacer(Modifier.width(8.dp)) }
            PillTab(t, "Status"); Spacer(Modifier.width(8.dp)); PillTab(t, "Calls")
        }
    }
}

@Composable
private fun IosHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    Column(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(16.dp, 10.dp, 16.dp, 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Edit", color = col(t.accentColor), fontSize = 14.sp, fontWeight = FontWeight.W600)
            Sp()
            CompactIconButton(Icons.Rounded.MoreVert, col(t.accentColor), 20.dp, a.onThreeDotsClick)
        }
        Text(title, color = col(t.primaryTextColor), fontSize = 22.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.5).sp)
        if (hdrSearch) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(32.dp).background(col(t.cardColor), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(Icons.Rounded.Search, col(t.secondaryTextColor), 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Search", color = col(t.secondaryTextColor), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StockHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    val fg = col(t.primaryTextColor)
    Row(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(16.dp, 10.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = col(t.accentColor), fontSize = 19.sp, fontWeight = FontWeight.W800)
        Sp()
        if (a.showCameraIcon) { Ic(Icons.Outlined.CameraAlt, fg, 21.dp); Spacer(Modifier.width(14.dp)) }
        if (hdrSearch) { Ic(Icons.Rounded.Search, fg, 21.dp); Spacer(Modifier.width(14.dp)) }
        TapIcon(Icons.Rounded.MoreVert, fg, 21.dp, a.onThreeDotsClick)
    }
}

@Composable
private fun BubblesHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    val fg = col(t.primaryTextColor)
    Column(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(14.dp, 10.dp, 14.dp, 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.W700)
            Sp()
            if (hdrSearch) Ic(Icons.Rounded.Search, fg, 20.dp)
            Spacer(Modifier.width(12.dp))
            TapIcon(Icons.Rounded.MoreVert, fg, 20.dp, a.onThreeDotsClick)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            BubbleTabItem(t, "Chats", true, "7"); Spacer(Modifier.width(6.dp))
            if (a.separateChatsAndGroups) { BubbleTabItem(t, "Groups"); Spacer(Modifier.width(6.dp)) }
            BubbleTabItem(t, "Status"); Spacer(Modifier.width(6.dp)); BubbleTabItem(t, "Calls")
        }
    }
}

@Composable
private fun OldUiHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    val fg = col(t.primaryTextColor)
    val sec = col(t.secondaryTextColor)
    Column(Modifier.fillMaxWidth().background(col(t.surfaceColor))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Sp()
            if (hdrSearch) { Ic(Icons.Rounded.Search, fg, 21.dp); Spacer(Modifier.width(14.dp)) }
            TapIcon(Icons.Rounded.MoreVert, fg, 21.dp, a.onThreeDotsClick)
        }
        Row(Modifier.fillMaxWidth()) {
            if (a.showCameraIcon) Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { Ic(Icons.Rounded.CameraAlt, sec, 18.dp) }
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text("CHATS", color = col(t.accentColor), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().height(2.5.dp).background(col(t.accentColor)))
            }
            if (a.separateChatsAndGroups) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("GROUPS", color = sec, fontSize = 13.sp) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("STATUS", color = sec, fontSize = 13.sp) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("CALLS", color = sec, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun BasicHeader(t: ThemeConfig, a: HeaderPreviewArgs, title: String, hdrSearch: Boolean) {
    val fg = col(t.primaryTextColor)
    val sec = col(t.secondaryTextColor)
    Column(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(16.dp, 10.dp, 16.dp, 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Sp()
            if (hdrSearch) { Ic(Icons.Rounded.Search, fg, 20.dp); Spacer(Modifier.width(12.dp)) }
            TapIcon(Icons.Rounded.MoreVert, fg, 20.dp, a.onThreeDotsClick)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Text("Chats (7)", color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (a.separateChatsAndGroups) Text("Groups", color = sec, fontSize = 13.sp)
            Text("Status", color = sec, fontSize = 13.sp)
            Text("Calls", color = sec, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SampleConversationRow(t: ThemeConfig, a: HeaderPreviewArgs) {
    val spec = HomeStyleSpec.resolve(HomePresetNormalizer.homeStyle(a.homeStyle), a.homeStyle.trim().lowercase())
    val convo = remember {
        Conversation(
            id = "preview", type = ConversationType.direct, title = "Tyler Durden",
            participantIds = listOf("me", "tyler"), avatarInitials = "TD",
            lastMessageText = "The things you own end up owning you.",
            lastMessageTime = java.time.Instant.now(), lastMessageSenderId = "tyler", unreadCount = 7,
        )
    }
    ConversationRow(
        conversation = convo, theme = t, homePrefs = HomePreferences(homeStyle = a.homeStyle, tabBubbleStyle = a.tabBubbleStyle),
        chatList = defaultHomeTemplates.chatList, density = spec.tileDensity, isDark = t.isDark,
        cardRows = spec.cardRows, isTelegramRows = a.homeStyle.trim().lowercase() == "telegram style",
        displayTitle = convo.title, presence = HomePresence(isOnline = true), currentUserId = "me",
        lastOwnMessageState = DeliveryState.read, selected = false, locked = false, elapsedTime = false,
        gbOnlineChat = true, gbOnlineDotChat = true, gbModOnlineColor = null, gbModLastSeenColor = null,
        gbOnlineDotColor = null, gbHomeCounterBk = null, gbHomeCounterText = null,
        onTap = {}, onLongPress = {},
    )
}

@Composable
private fun SampleChatRow(t: ThemeConfig) {
    Row(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).background(col(t.cardColor), CircleShape).border(1.5.dp, col(t.accentColor).a(0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("TD", color = col(t.primaryTextColor), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tyler Durden", color = col(t.primaryTextColor), fontWeight = FontWeight.W600, fontSize = 14.5.sp)
                Text("10:42 PM", color = col(t.accentColor), fontSize = 11.sp, fontWeight = FontWeight.W500)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(Icons.Rounded.DoneAll, col(t.accentColor), 15.dp)
                Spacer(Modifier.width(4.dp))
                Text("The things you own end up owning you.", color = col(t.secondaryTextColor), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(18.dp).background(col(t.accentColor), CircleShape), contentAlignment = Alignment.Center) {
                    Text("7", color = col(t.onAccentColor), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OneUiBottomBar(t: ThemeConfig) {
    Row(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.background(col(t.accentColor).a(0.18f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(Icons.Rounded.ChatBubble, col(t.accentColor), 18.dp)
            Spacer(Modifier.width(6.dp))
            Text("Chats", color = col(t.accentColor), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val s = col(t.secondaryTextColor)
        Ic(Icons.Rounded.Update, s, 20.dp); Ic(Icons.Rounded.Checklist, s, 20.dp); Ic(Icons.Outlined.CallOutlined, s, 20.dp)
    }
}

@Composable
private fun IosBottomBar(t: ThemeConfig) {
    Column {
        Box(Modifier.fillMaxWidth().height(0.8.dp).background(col(t.cardColor)))
        Row(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IosTabItem(t, Icons.Rounded.ChatBubble, "Chats", true, "7"); IosTabItem(t, Icons.Outlined.CircleOutlined, "Updates")
            IosTabItem(t, Icons.Rounded.Checklist, "Tasks"); IosTabItem(t, Icons.Rounded.Phone, "Calls"); IosTabItem(t, Icons.Rounded.Settings, "Settings")
        }
    }
}

@Composable
private fun StockBottomBar(t: ThemeConfig) {
    Column {
        Box(Modifier.fillMaxWidth().height(0.8.dp).background(col(t.cardColor)))
        Row(Modifier.fillMaxWidth().background(col(t.backgroundColor)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            StockTabItem(t, Icons.Rounded.ChatBubble, "Chats", true, "7"); StockTabItem(t, Icons.Rounded.Update, "Updates")
            StockTabItem(t, Icons.Rounded.Groups, "Communities"); StockTabItem(t, Icons.Rounded.Call, "Calls")
        }
    }
}

@Composable
private fun BubblesBottomBar(t: ThemeConfig) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(12.dp, 4.dp, 12.dp, 8.dp).background(col(t.surfaceColor), shape).border(1.dp, col(t.cardColor), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.background(col(t.accentColor), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(Icons.Rounded.ChatBubble, col(t.onAccentColor), 16.dp)
            Spacer(Modifier.width(4.dp))
            Text("Chats", color = col(t.onAccentColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        val s = col(t.secondaryTextColor)
        Ic(Icons.Rounded.Update, s, 18.dp); Ic(Icons.Rounded.Checklist, s, 18.dp); Ic(Icons.Rounded.Call, s, 18.dp)
    }
}

@Composable
private fun OldUiBottomBar(t: ThemeConfig) {
    Box(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text("Classic Full-Screen Tab View (Old UI)", color = col(t.secondaryTextColor), fontSize = 10.5.sp)
    }
}

@Composable
private fun BasicBottomBar(t: ThemeConfig) {
    Row(Modifier.fillMaxWidth().background(col(t.surfaceColor)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        val s = col(t.secondaryTextColor)
        Ic(Icons.Rounded.ChatBubble, col(t.accentColor), 20.dp); Ic(Icons.Rounded.Update, s, 20.dp); Ic(Icons.Rounded.Checklist, s, 20.dp); Ic(Icons.Rounded.Call, s, 20.dp)
    }
}

@Composable
private fun PillTab(t: ThemeConfig, label: String, selected: Boolean = false, count: String? = null) {
    Row(
        Modifier.background(if (selected) col(t.accentColor) else col(t.cardColor), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) col(t.onAccentColor) else col(t.secondaryTextColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.background(if (selected) col(t.onAccentColor) else col(t.accentColor), CircleShape).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text(count, color = if (selected) col(t.primaryTextColor) else col(t.onAccentColor), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RowScope.BubbleTabItem(t: ThemeConfig, label: String, selected: Boolean = false, count: String? = null) {
    val shape = RoundedCornerShape(14.dp)
    var m = Modifier.weight(1f).background(if (selected) col(t.accentColor).a(0.2f) else Color.Transparent, shape)
    if (selected) m = m.border(1.2.dp, col(t.accentColor), shape)
    Box(m.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(
            if (count != null) "$label ($count)" else label,
            color = if (selected) col(t.accentColor) else col(t.secondaryTextColor), fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.W500,
        )
    }
}

@Composable
private fun IosTabItem(t: ThemeConfig, icon: ImageVector, label: String, selected: Boolean = false, badge: String? = null) {
    val color = if (selected) col(t.accentColor) else col(t.secondaryTextColor)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Ic(icon, color, 18.dp)
            if (badge != null) {
                Box(Modifier.offset(12.dp, (-3).dp).background(col(t.dangerColor), CircleShape).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(badge, color = col(t.primaryTextColor), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun StockTabItem(t: ThemeConfig, icon: ImageVector, label: String, selected: Boolean = false, badge: String? = null) {
    val color = if (selected) col(t.accentColor) else col(t.secondaryTextColor)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Ic(icon, color, 19.dp)
            if (badge != null) {
                Box(Modifier.offset(12.dp, (-3).dp).background(col(t.accentColor), CircleShape).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(badge, color = col(t.onAccentColor), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.W600)
    }
}


/**
 * Curved Radial Menu preview: the ACTUAL CurvedRadialDrawerShell (trigger, nav strip, fan geometry)
 * laid out in a full phone-sized viewport, then scaled uniformly into the card. It is never laid
 * out at card size, so the shell does not take its compact fallback. Window insets are consumed
 * so the device status bar does not shift the scaled copy. Page content is synthetic.
 */
@Composable
private fun CurvedRadialActualPreview(a: HeaderPreviewArgs, t: ThemeConfig, title: String, modifier: Modifier) {
    val phoneW = 392.dp
    val phoneH = 760.dp
    val ids = listOf("chats", "updates", "calls", "tasks", "settings")
    var filter by remember { mutableStateOf("All") }
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        val scale = maxWidth.value / phoneW.value
        Box(
            Modifier.width(maxWidth).height(phoneH * scale).clipToBounds()
                .consumeWindowInsets(WindowInsets.safeDrawing),
        ) {
            Box(
                Modifier.requiredSize(phoneW, phoneH).graphicsLayer {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                CurvedRadialDrawerShell(
                    colors = ChatyColors(t), navItemIds = ids, selectedIndex = 0,
                    animationsDisabled = true, onSelect = {}, fab = {},
                    content = {
                        Column(Modifier.fillMaxSize().background(col(t.backgroundColor))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, color = col(t.primaryTextColor), fontSize = 18.sp, fontWeight = FontWeight.W800, modifier = Modifier.weight(1f))
                                Ic(Icons.Rounded.MoreVert, col(t.primaryTextColor), 22.dp)
                            }
                            HomeFilterChips(t, a.tabBubbleStyle, homeFilters(null), filter) { filter = it }
                            SampleConversationRow(t, a)
                        }
                    },
                )
            }
        }
    }
}
