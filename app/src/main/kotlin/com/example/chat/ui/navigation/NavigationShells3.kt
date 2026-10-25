package com.example.chat.ui.navigation

import com.example.chat.core.templates.NavigationTemplate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        isBasic -> 52f
        isIos -> 70f
        isBubbles -> 68f
        else -> 72f
    }
    val horizontalPadding = if (isIos) 10f else 0f
    val itemWidth = when {
        isInstagram -> 58f
        isBasic -> 76f
        isTelegram -> 82f
        isOneUi -> 88f
        isBubbles -> 76f
        else -> 80f
    }
    val dockBackground = when {
        isIos -> colors.surface.copy(alpha = if (isDark) 0.96f else 0.93f)
        isTelegram -> colors.surfaceElevated
        else -> colors.surface
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = horizontalPadding.dp)
                .fillMaxWidth()
                .height(barHeight.dp)
                .then(
                    if (isIos) Modifier.shadow(14.dp) else Modifier,
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
            Row(
                modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
            ) {
                navItemIds.forEachIndexed { index, id ->
                    val selected = index == selectedIndex
                    val useCircle = isIos || isBubbles
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(itemWidth.dp)
                            .height(barHeight.dp)
                            .clickable { onSelect(index) }
                            .padding(horizontal = if (isBasic) 2.dp else 4.dp, vertical = if (isInstagram) 0.dp else 4.dp),
                    ) {
                        when {
                            isInstagram -> Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 5.dp),
                            ) {
                                Box(
                                    Modifier
                                        .width(28.dp)
                                        .height(2.dp)
                                        .background(if (selected) accent else Color.Transparent),
                                )
                                Spacer(Modifier.height(4.dp))
                                NavDestinationIcon(id, size = 23, tint = if (selected) foreground else secondary)
                            }
                            isBasic -> {
                                NavDestinationIcon(id, size = 18, tint = if (selected) accent else secondary)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    NavigationCatalog.labelFor(id),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) foreground else secondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                )
                            }
                            else -> {
                                if (isOneUi) {
                                    Box(
                                        Modifier
                                            .width(if (selected) 22.dp else 0.dp)
                                            .height(2.dp)
                                            .background(if (selected) accent else Color.Transparent),
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(if (isBubbles) 34.dp else 32.dp)
                                        .then(
                                            when {
                                                useCircle -> Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (selected) accent.copy(alpha = if (isIos) 0.18f else 0.22f)
                                                        else Color.Transparent,
                                                    )
                                                    .border(
                                                        width = if (selected) 1.3.dp else 0.7.dp,
                                                        color = if (selected) accent else colors.borderSubtle,
                                                        shape = CircleShape,
                                                    )
                                                isOneUi || isTelegram -> Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (selected) accent.copy(alpha = 0.13f)
                                                        else Color.Transparent,
                                                    )
                                                else -> Modifier
                                            },
                                        ),
                                ) {
                                    NavDestinationIcon(
                                        id,
                                        size = if (isInstagram) 23 else if (isBasic) 19 else 21,
                                        tint = if (selected) accent else secondary,
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    NavigationCatalog.labelFor(id),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) foreground else secondary,
                                    fontSize = if (isBubbles) 9.sp else 10.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                )
                                if (isTelegram) {
                                    Box(
                                        Modifier
                                            .width(if (selected) 16.dp else 0.dp)
                                            .height(2.dp)
                                            .background(if (selected) accent else Color.Transparent),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = (barHeight + 16).dp, end = 16.dp)) { fab() }
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

    Box(Modifier.fillMaxSize().background(colors.background)) {
        content()
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
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
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
                if (isSelected) {
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                }
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
                NavDestinationIcon(id, size = 22, tint = if (isSelected) accent else unselectedFg)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(if (isSelected) 5.5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accent else Color.Transparent),
                )
            }
            "Circle Accent Pop" -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isSelected) 42.dp else 36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accent else Color.Transparent),
            ) {
                NavDestinationIcon(id, size = 20, tint = if (isSelected) colors.onPrimary else unselectedFg)
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
                if (isSelected) {
                    Spacer(Modifier.width(5.dp))
                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = colors.onPrimary)
                }
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
                NavDestinationIcon(id, size = 21, tint = if (isSelected) accent else unselectedFg)
            }
            "Minimal Icon Dock" -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayerScale(if (isSelected) 1.15f else 1f),
            ) {
                NavDestinationIcon(id, size = 22, tint = if (isSelected) accent else unselectedFg)
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
            // 'Floating Pill' default
            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) accent.copy(alpha = if (isDark) 0.2f else 0.12f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                NavDestinationIcon(id, size = 20, tint = if (isSelected) accent else unselectedFg)
                if (isSelected) {
                    Spacer(Modifier.width(5.dp))
                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = accent)
                }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.Transparent) {
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceElevated)
                .border(0.8.dp, colors.borderSubtle, RoundedCornerShape(24.dp)),
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
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.foregroundSecondary, modifier = Modifier.size(20.dp))
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
                    Icon(
                        Icons.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = colors.foregroundTertiary,
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
