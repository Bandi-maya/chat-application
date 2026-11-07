package com.example.chat.ui.navigation

import androidx.compose.ui.graphics.graphicsLayer
import com.example.chat.core.templates.NavigationTemplate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.ui.core.theme.ChatyColors

val LocalReferenceDefaultPresentation = androidx.compose.runtime.staticCompositionLocalOf { false }

val LocalReferenceContentSurface = androidx.compose.runtime.staticCompositionLocalOf<Color?> { null }

/** Reusable preset content surface. Callers provide real content, never screen-specific copies.
 * A null override uses the caller's active theme surface in both light and dark mode. */
@Composable
fun ReferenceLayoutContentSurface(
    rounded: Boolean,
    isDark: Boolean,
    lightSurface: Color,
    modifier: Modifier = Modifier,
    surfaceOverride: Color? = null,
    content: @Composable () -> Unit,
) {
    val surface = surfaceOverride ?: androidx.compose.ui.graphics.lerp(lightSurface, com.example.chat.ui.theme.LocalAppColors.current.primary, if (isDark) 0.08f else 0.035f)
    Box(modifier.then(if (rounded) Modifier.clip(RoundedCornerShape(24.dp)).background(surface) else Modifier)) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalReferenceContentSurface provides if (rounded) surface else null,
        ) { content() }
    }
}

/** Port of _buildPresetNavigationShell: oneUi / iosStyle / instagramStyle /
 *  telegramStyle / basicTabStyle / gestureTabs bottom docks with the exact
 *  per-mode metrics tables. */
@Composable
internal fun PresetNavigationShell(
    mode: AppNavigationMode,
    colors: ChatyColors,
    navItemIds: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
    animationsDisabled: Boolean = false,
    rootHeaderActions: @Composable () -> Unit = {},
    referenceDefaultPresentation: Boolean = false,
    unreadCounts: Map<String, Int> = emptyMap(),
    /** OneUI center-button dock: the raised circle in the dock middle. */
    onCenterAction: () -> Unit = {},
) {
    val accent = colors.primary
    val foreground = colors.foreground
    val secondary = colors.foregroundSecondary
    val isDark = colors.isDark
    val isInstagram = mode == AppNavigationMode.instagramStyle
    val isIos = mode == AppNavigationMode.iosStyle
    val isOneUi = mode == AppNavigationMode.oneUi
    val isTelegram = mode == AppNavigationMode.telegramStyle
    val isBubbles = mode == AppNavigationMode.gestureTabs
    val isBasic = mode == AppNavigationMode.basicTabStyle
    val barHeight = when {
        isInstagram -> 54f
        isBasic -> 56f
        isIos -> 56f
        isBubbles -> 56f
        isOneUi -> 56f
        else -> 64f
    }
    val horizontalPadding = 0f
    val referenceDarkChrome = isDark && (isOneUi || isIos)
    val exterior = colors.background
    val dockBackground = when {
        referenceDarkChrome -> colors.background
        isIos -> colors.surface.copy(alpha = if (isDark) 0.96f else 0.93f)
        isTelegram -> colors.surfaceElevated
        else -> colors.surface
    }

    val activeId = navItemIds.getOrNull(selectedIndex)
    val collapseRangePx = with(LocalDensity.current) { ReferenceLayoutMetrics.compactIdentityHeightDp.dp.toPx() }
    var headerOffsetPx by remember(activeId, mode) { mutableFloatStateOf(0f) }
    val headerCollapse = if (isOneUi && collapseRangePx > 0f) -headerOffsetPx / collapseRangePx else 0f
    val headerScroll = remember(activeId, mode, collapseRangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isOneUi) return Offset.Zero
                val old = headerOffsetPx
                headerOffsetPx = referenceHeaderScrollOffset(old, available.y, collapseRangePx)
                return Offset(0f, headerOffsetPx - old)
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalReferenceHeaderCollapse provides headerCollapse, LocalReferenceDefaultPresentation provides (isOneUi && referenceDefaultPresentation)) {
    Box(Modifier.fillMaxSize().background(exterior).nestedScroll(headerScroll)) {
        // Only adapters that already honor the title hand-off may suppress their title.
        // Other enabled destinations retain their existing functional header.
        val shellOwnsHeading = (isOneUi || isIos) && activeId in setOf("calls", "updates", "settings", "tasks")
        Column(Modifier.fillMaxSize().padding(bottom = barHeight.dp)) {
            if (shellOwnsHeading) {
                Row(Modifier.fillMaxWidth().height(if (isOneUi) (ReferenceLayoutMetrics.compactIdentityHeightDp * (1f - headerCollapse)).dp else 56.dp)
                    .padding(top = 0.dp)
                    .then(if (isOneUi) Modifier.clipToBounds().alpha(1f - headerCollapse) else Modifier)
                    .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start) {
                    if (isOneUi) {
                        NavDestinationIcon(activeId!!, size = ReferenceLayoutMetrics.identityIconSizeDp, tint = foreground)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(NavigationCatalog.labelFor(activeId!!), color = foreground,
                        fontSize = if (isOneUi) ReferenceLayoutMetrics.compactIdentityTextSizeSp.sp else 20.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (isOneUi) Modifier else Modifier.weight(1f))
                    if (!isOneUi && activeId != "settings") rootHeaderActions() // Settings hosts its own edit/search/QR row; the avatar already opens Settings.
                }
            }
            if (shellOwnsHeading && isOneUi && activeId != "settings") Row(
                Modifier.fillMaxWidth().height(ReferenceLayoutMetrics.actionBarHeightDp.dp).padding(end = 4.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(NavigationCatalog.labelFor(activeId!!), color = foreground,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 16.dp).alpha(headerCollapse),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Settings hosts its own edit/search/QR row; the avatar already opens Settings.
                if (activeId != "settings") rootHeaderActions()
            }
            ReferenceLayoutContentSurface(rounded = isOneUi && activeId != null && activeId !in setOf("chats", "groups"), isDark = isDark,
                lightSurface = colors.surface, modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDestinationTitlePresentation provides if (shellOwnsHeading) activeId else null,
                ) { content() }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // User directive (Telegram dock reference): the Telegram dock floats
                // as a rounded pill above the system bar instead of a full-width bar.
                .then(if (isTelegram) Modifier.padding(horizontal = 12.dp) else Modifier.padding(horizontal = horizontalPadding.dp))
                .fillMaxWidth()
                .padding(bottom = if (isTelegram) 10.dp else 0.dp)
                .height(barHeight.dp)
                .then(
                    if (isTelegram) Modifier.shadow(6.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)) else Modifier,
                )
                .background(dockBackground),
        ) {
            // Dart Border(top: ...) plus the iOS bottom hairline.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (isInstagram) 0.7.dp else 1.dp)
                    .background(colors.borderSubtle)
                    .align(Alignment.TopCenter),
            )
            if (isIos) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.7.dp)
                        .background(colors.borderSubtle)
                        .align(Alignment.BottomCenter),
                )
            }
            // Shared by applied Home and read-only preview: preserve every configured destination.
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val renderDockItem: @Composable androidx.compose.foundation.layout.RowScope.(Int, String) -> Unit = { index, id ->
                    val selected = index == selectedIndex
                    val label = NavigationCatalog.labelFor(id)
                    val targetTint = if (selected) foreground else secondary
                    val tint by animateColorAsState(targetValue = targetTint,
                        animationSpec = tween(if (animationsDisabled) 0 else 140), label = "navigation-item-tint")
                    val selectedModifier = Modifier
                        .weight(if (isBubbles && selected) 1.6f else 1f)
                        .fillMaxSize()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                    when {
                        isOneUi -> Column(selectedModifier, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, color = tint, fontSize = 16.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val unread = unreadCounts[id] ?: 0
                                if (unread > 0) Box(Modifier.background(accent, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
                                    Text(if (unread > 99) "99+" else unread.toString(), color = colors.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            // Fixed geometry keeps selection from shifting the label.
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(5) { Box(Modifier.size(2.dp).background(
                                    if (selected) tint else Color.Transparent, CircleShape)) }
                            }
                        }
                        isBubbles -> Row(selectedModifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) colors.surfaceElevated else Color.Transparent),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            NavDestinationIcon(id, size = 23, tint = tint)
                            if (selected) {
                                Spacer(Modifier.width(7.dp))
                                Text(label, color = tint, fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        else -> Column(
                            // Telegram reference: selection is a rounded pill behind the
                            // icon+label; tint stays foreground for every item, no underline.
                            if (isTelegram) selectedModifier
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) colors.primary.copy(alpha = if (isDark) 0.22f else 0.12f) else Color.Transparent)
                            else selectedModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            if (isInstagram) Box(Modifier.width(26.dp).height(2.dp)
                                .background(if (selected) accent else Color.Transparent))
                            NavDestinationIcon(id, size = if (isBasic) 23 else 24,
                                selected = isIos && selected,
                                tint = if (isTelegram) foreground else if (isIos) tint else if (selected) accent else secondary)
                            if (!isInstagram) {
                                Spacer(Modifier.height(if (isIos) 4.dp else 3.dp))
                                Text(label, color = if (isTelegram) foreground else tint, fontSize = if (isIos) 11.sp else if (isBasic) 13.sp else 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (!isTelegram && selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                // OneUI center-button dock: left half / fixed center gap for the
                // raised circle / right half. Both halves are weight(1f), so the gap
                // sits exactly at the bar's center for odd and even item counts.
                // Explicit One UI pick only - the Chaty Default presentation, which
                // also renders through the oneUi mode, keeps the plain weight row.
                if (isOneUi && !referenceDefaultPresentation) {
                    val half = navItemIds.size / 2
                    Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        navItemIds.subList(0, half).forEachIndexed { i, id -> renderDockItem(i, id) }
                    }
                    Spacer(Modifier.width(56.dp))
                    Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        navItemIds.subList(half, navItemIds.size).forEachIndexed { i, id -> renderDockItem(half + i, id) }
                    }
                } else {
                    navItemIds.forEachIndexed { index, id -> renderDockItem(index, id) }
                }
            }
            // The OneUI dock carries a raised circular center action button
            // overlapping the bar top (explicit One UI pick only).
            if (isOneUi && !referenceDefaultPresentation) {
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = (-14).dp)
                        .size(52.dp).shadow(6.dp, CircleShape).clip(CircleShape)
                        .background(accent)
                        .clickable { onCenterAction() },
                    contentAlignment = Alignment.Center,
                ) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.camera, semanticLabel = "Quick action", color = colors.onPrimary,
                        modifier = Modifier.size(24.dp))
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = (barHeight + 16).dp, end = 16.dp)) { fab() }
    }
}
}

/** Port of _buildBottomShell: the 12 named bottom-bar styles' container
 *  metrics (side margin, bottom margin, radius, background, border,
 *  shadow rule) plus the center-action slot. */
@Composable
internal fun BottomShell(
    colors: ChatyColors,
    template: NavigationTemplate,
    styleName: String,
    navItemIds: List<String>,
    selectedIndex: Int,
    primaryCount: Int,
    animationsDisabled: Boolean,
    onDestinationTap: (Int) -> Unit,
    onCenterAction: () -> Unit,
    /** Explicit One UI pick only: render the raised-centre horizontally
     *  scrolling dock instead of the configured named dock style. */
    raisedCenterScrollDock: Boolean = false,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val accent = colors.primary
    val sideMargin = when (styleName) {
        "Floating Pill" -> 16f
        "Active Pill Chip" -> 14f
        "Top Indicator Line", "Bottom Indicator Dot" -> 0f
        "Circle Accent Pop" -> 12f
        "Curved Notch Teardrop" -> 0f
        "Floating Dynamic Island" -> 20f
        "Raised Center Action" -> 0f
        "Segmented Glass Dock" -> 14f
        "Minimal Icon Dock" -> 24f
        "Classic Label Bar", "Soft Square Tile" -> 0f
        else -> 14f
    }
    val bottomMargin = when (styleName) {
        "Floating Pill" -> 12f
        "Active Pill Chip" -> 10f
        "Top Indicator Line", "Bottom Indicator Dot" -> 0f
        "Circle Accent Pop" -> 10f
        "Curved Notch Teardrop" -> 0f
        "Floating Dynamic Island" -> 14f
        "Raised Center Action" -> 0f
        "Segmented Glass Dock" -> 10f
        "Minimal Icon Dock" -> 12f
        "Classic Label Bar", "Soft Square Tile" -> 0f
        else -> 8f
    }
    val barHeight = template.height.coerceIn(48.0, 88.0)
    val barRadius = when (styleName) {
        "Floating Pill" -> 32f
        "Active Pill Chip" -> 24f
        "Top Indicator Line", "Bottom Indicator Dot" -> 0f
        "Circle Accent Pop" -> 26f
        "Curved Notch Teardrop" -> 0f
        "Floating Dynamic Island" -> 30f
        "Raised Center Action" -> 0f
        "Segmented Glass Dock" -> 22f
        "Minimal Icon Dock" -> 28f
        "Classic Label Bar", "Soft Square Tile" -> 0f
        else -> 20f
    }
    val barBg = when (styleName) {
        "Floating Dynamic Island" -> colors.surfaceElevated
        "Segmented Glass Dock" -> colors.surface.copy(alpha = if (colors.isDark) 0.85f else 0.92f)
        else -> colors.surface
    }
    val hasShadow = sideMargin > 0
    val topBorderOnly = styleName in setOf(
        "Top Indicator Line", "Bottom Indicator Dot", "Curved Notch Teardrop",
        "Classic Label Bar", "Soft Square Tile",
    )

    val dockVariant = HomePresetNormalizer.resolvedBottomDockVariant(styleName, raisedCenterScrollDock)

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // Reserve the complete dock footprint for every destination. Page-owned
        // Scaffold FABs (Calls) and last rows must not be drawn underneath it.
        Box(Modifier.fillMaxSize().padding(bottom = (barHeight + bottomMargin).dp)) { content() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = sideMargin.dp, end = sideMargin.dp, bottom = bottomMargin.dp)
                .fillMaxWidth()
                .height(barHeight.dp)
                .then(if (hasShadow) Modifier.shadow(18.dp, RoundedCornerShape(barRadius.dp)) else Modifier)
                .clip(RoundedCornerShape(barRadius.dp))
                .background(barBg)
                .then(
                    when {
                        styleName == "Segmented Glass Dock" ->
                            Modifier.border(1.2.dp, colors.border, RoundedCornerShape(barRadius.dp))
                        styleName in setOf("Floating Pill", "Active Pill Chip", "Floating Dynamic Island") ->
                            Modifier.border(0.8.dp, colors.borderSubtle, RoundedCornerShape(barRadius.dp))
                        topBorderOnly -> Modifier
                        else -> Modifier.border(0.8.dp, colors.borderSubtle, RoundedCornerShape(barRadius.dp))
                    },
                )
                .then(if (topBorderOnly) Modifier.border(0.dp, Color.Transparent) else Modifier),
        ) {
            if (topBorderOnly) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle).align(Alignment.TopCenter))
            }
            if (dockVariant == HomePresetNormalizer.RAISED_CENTER_SCROLL_DOCK) {
                // Fixed 64dp items scroll horizontally in two independent
                // halves; the 56dp centre gap is a FIXED sibling (never scrolled)
                // so it stays centred under the raised button. With few items the
                // halves hug the gap and need no scrolling.
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    val half = navItemIds.size / 2
                    val left = navItemIds.subList(0, half)
                    val right = navItemIds.subList(half, navItemIds.size)
                    Row(
                        Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                    ) {
                        left.forEachIndexed { i, id ->
                            Box(Modifier.width(64.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                CustomNavItem(
                                    id = id, isSelected = selectedIndex == i, isCenter = false,
                                    styleName = styleName, colors = colors, accent = accent,
                                    onTap = { onDestinationTap(i) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(56.dp))
                    Row(
                        Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.Start),
                    ) {
                        right.forEachIndexed { i, id ->
                            val index = half + i
                            Box(Modifier.width(64.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                CustomNavItem(
                                    id = id, isSelected = selectedIndex == index, isCenter = false,
                                    styleName = styleName, colors = colors, accent = accent,
                                    onTap = { onDestinationTap(index) },
                                )
                            }
                        }
                    }
                }
            } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hasCenterAction = template.hasCenterAction && navItemIds.size >= 3
                val centerSlot = if (hasCenterAction) navItemIds.size / 2 else -1
                navItemIds.forEachIndexed { index, id ->
                    if (hasCenterAction && index == centerSlot) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(accent, accent.copy(alpha = 0.82f)),
                                        ),
                                    )
                                    .clickable(onClick = onCenterAction),
                            ) {
                                Icon(
                                    // Dart: navigationTemplate.centerActionIcon
                                    // ?? Icons.bolt_rounded; the registry sets
                                    // 'camera_alt_rounded' only for cameraFirst.
                                    if (template.centerActionIcon != null) Icons.Rounded.CameraAlt else Icons.Rounded.Bolt,
                                    contentDescription = if (template.centerActionId == "camera") "Open camera effects" else "Quick action",
                                    tint = colors.onPrimary,
                                    modifier = Modifier.size(23.dp),
                                )
                            }
                        }
                    }
                    val isCenter = !hasCenterAction && navItemIds.size % 2 == 1 && index == navItemIds.size / 2
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CustomNavItem(
                            id = id,
                            isSelected = selectedIndex == index,
                            isCenter = isCenter,
                            styleName = styleName,
                            colors = colors,
                            accent = accent,
                            onTap = { onDestinationTap(index) },
                        )
                    }
                }
                if (template.hasCenterAction && navItemIds.size < 3) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(onClick = onCenterAction) {
                            Icon(Icons.Rounded.Bolt, contentDescription = "Quick action", tint = accent)
                        }
                    }
                }
            }
            }
        }
        if (dockVariant == HomePresetNormalizer.RAISED_CENTER_SCROLL_DOCK) {
            // Raised circular centre action overlapping the dock top. Sibling of
            // the (clipped) dock container in the outer unclipped Box so the
            // raised 14dp is not cut off: the circle's bottom sits 38dp below the
            // dock's top edge, i.e. (bottomMargin + barHeight - 38) above the
            // outer bottom, horizontally centred like the dock.
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = (bottomMargin + barHeight - 38).dp)
                    .size(52.dp).shadow(6.dp, CircleShape).clip(CircleShape)
                    .background(accent)
                    .clickable(onClick = onCenterAction),
                contentAlignment = Alignment.Center,
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.camera, semanticLabel = "Quick action", color = colors.onPrimary,
                    modifier = Modifier.size(24.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = (barHeight + bottomMargin + 16).dp, end = 16.dp)) { fab() }
    }
}

/** Port of _buildCustomNavItem: the 12 nav-item styles. */
@Composable
private fun CustomNavItem(
    id: String,
    isSelected: Boolean,
    isCenter: Boolean,
    styleName: String,
    colors: ChatyColors,
    accent: Color,
    onTap: () -> Unit,
) {
    val unselectedFg = colors.foregroundSecondary
    val label = NavigationCatalog.labelFor(id)
    val isDark = colors.isDark

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onTap),
    ) {
        when (styleName) {
            "Active Pill Chip" -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (isSelected) accent.copy(alpha = if (isDark) 0.22f else 0.14f) else Color.Transparent)
                    .then(if (isSelected) Modifier.border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(30.dp)) else Modifier)
                    .padding(horizontal = if (isSelected) 14.dp else 8.dp, vertical = 7.dp),
            ) {
                NavDestinationIcon(id, size = 20, tint = if (isSelected) accent else unselectedFg)
                Spacer(Modifier.width(5.dp))
                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg)
            }
            "Top Indicator Line" -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .width(if (isSelected) 26.dp else 0.dp)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (isSelected) accent else Color.Transparent),
                )
                NavDestinationIcon(id, size = 22, tint = if (isSelected) accent else unselectedFg)
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
                Spacer(Modifier.height(2.dp))
            }
            "Bottom Indicator Dot" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NavDestinationIcon(id, size = 20, tint = if (isSelected) accent else unselectedFg)
                Spacer(Modifier.height(2.dp))
                Text(
                    label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(if (isSelected) 4.5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accent else Color.Transparent),
                )
            }
            "Circle Accent Pop" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (isSelected) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accent else Color.Transparent),
                ) {
                    NavDestinationIcon(id, size = 17, tint = if (isSelected) colors.onPrimary else unselectedFg)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
            }
            "Curved Notch Teardrop" -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .width(if (isSelected) 28.dp else 0.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(if (isSelected) accent else Color.Transparent),
                )
                NavDestinationIcon(id, size = 21, tint = if (isSelected) accent else unselectedFg)
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
                Spacer(Modifier.height(3.dp))
            }
            "Floating Dynamic Island" -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .padding(horizontal = if (isSelected) 12.dp else 8.dp, vertical = 6.dp),
            ) {
                NavDestinationIcon(id, size = 19, tint = if (isSelected) colors.onPrimary else unselectedFg)
                Spacer(Modifier.width(5.dp))
                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) colors.onPrimary else unselectedFg)
            }
            "Raised Center Action" -> if (isCenter) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.85f)))),
                ) {
                    NavDestinationIcon(id, size = 24, tint = colors.onPrimary)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NavDestinationIcon(id, size = 20, tint = if (isSelected) accent else unselectedFg)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accent else unselectedFg,
                    )
                }
            }
            "Segmented Glass Dock" -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) accent.copy(alpha = if (isDark) 0.25f else 0.15f) else Color.Transparent)
                    .then(if (isSelected) Modifier.border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NavDestinationIcon(id, size = 19, tint = if (isSelected) accent else unselectedFg)
                    Spacer(Modifier.height(1.dp))
                    Text(
                        label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accent else unselectedFg,
                    )
                }
            }
            "Minimal Icon Dock" -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayerScale(if (isSelected) 1.1f else 1f),
            ) {
                NavDestinationIcon(id, size = 20, tint = if (isSelected) accent else unselectedFg)
                Spacer(Modifier.height(2.dp))
                Text(
                    label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    fontSize = 9.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
            }
            "Classic Label Bar" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NavDestinationIcon(id, size = 21, tint = if (isSelected) accent else unselectedFg)
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
            }
            "Soft Square Tile" -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accent.copy(alpha = if (isDark) 0.2f else 0.12f) else Color.Transparent)
                    .padding(7.dp),
            ) {
                NavDestinationIcon(id, size = 19, tint = if (isSelected) accent else unselectedFg)
                Text(
                    label,
                    fontSize = 9.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
            }
            // 'Floating Pill' default: every destination shows its icon and name
            // (Apple-style tab bar), the selected one tinted and softly pill-backed.
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) accent.copy(alpha = if (isDark) 0.18f else 0.10f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                NavDestinationIcon(id, size = 20, tint = if (isSelected) accent else unselectedFg)
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accent else unselectedFg,
                )
            }
        }
    }
}

private fun Modifier.graphicsLayerScale(value: Float): Modifier =
    this.then(Modifier.graphicsLayer { scaleX = value; scaleY = value })

/** Port of _showMoreMenu: 'More Destinations' sheet with the grid header
 *  and the overflow destination rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreDestinationsSheet(
    colors: ChatyColors,
    overflowIds: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val presentation = com.example.chat.ui.kit.LocalReferenceLayoutPresentation.current
    val layoutShape = RoundedCornerShape(presentation.menuCornerDp.dp)
    ModalBottomSheet(tonalElevation = 0.dp, dragHandle = null, shape = layoutShape, onDismissRequest = onDismiss, containerColor = Color.Transparent) {
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clip(layoutShape)
                .background(colors.surfaceElevated)
                .border(0.8.dp, colors.borderSubtle, layoutShape),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.foregroundTertiary.copy(alpha = 0.3f)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(8.dp),
                ) {
                    Icon(Icons.Rounded.GridView, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("More Destinations", color = colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Quick access to extended views & settings", color = colors.foregroundSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close", color = colors.foregroundSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            overflowIds.forEachIndexed { i, id ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSecondary),
                    ) {
                        NavDestinationIcon(id, size = 20, tint = colors.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        NavigationCatalog.labelFor(id),
                        color = colors.foreground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
                        semanticLabel = null,
                        color = colors.foregroundTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (i < overflowIds.size - 1) {
                    Box(Modifier.padding(start = 48.dp).fillMaxWidth().height(1.dp).background(colors.divider.copy(alpha = 0.5f)))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
