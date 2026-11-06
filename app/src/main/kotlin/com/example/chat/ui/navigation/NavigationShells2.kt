package com.example.chat.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.ui.core.theme.ChatyColors

/** Port of _PerspectiveDrawerScaffold: left menu + foreground screen that
 *  slides 220dp, scales to 0.82 and rotates -0.12 rad in 3D while opening
 *  (280ms easeOutCubic). */
@Composable
internal fun PerspectiveDrawerShell(
    colors: ChatyColors,
    navItemIds: List<String>,
    selectedIndex: Int,
    animationsDisabled: Boolean,
    onSelect: (Int) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
    initialOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initialOpen) }
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(if (animationsDisabled) 0 else 280),
        label = "perspectiveDrawer",
    )

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(colors.surfaceElevated)) {
        val panelWidth = DrawerLayoutMetrics.perspectiveWidth(maxWidth.value).dp
        val slide = DrawerLayoutMetrics.perspectiveTranslation(maxWidth.value).dp
        androidx.activity.compose.BackHandler(enabled = open) { open = false }
        // Left menu
        Column(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 20.dp),
        ) {
            Text(
                "Side Menu",
                color = colors.foreground,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(24.dp))
            navItemIds.forEachIndexed { i, id ->
                val isSel = selectedIndex == i
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            onSelect(i)
                            open = false
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    NavDestinationIcon(
                        id,
                        size = 20,
                        tint = if (isSel) colors.primary else colors.foregroundSecondary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        NavigationCatalog.labelFor(id),
                        color = if (isSel) colors.primary else colors.foregroundSecondary,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Perspective-transformed foreground
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = slide.toPx() * progress
                    val scale = 1f - 0.18f * progress
                    scaleX = scale
                    scaleY = scale
                    cameraDistance = 16f
                    rotationY = -0.12f * progress * 180f / Math.PI.toFloat()
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .clip(RoundedCornerShape((progress * 28).dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = { open = !open }) {
                    Icon(
                        if (progress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Menu,
                        contentDescription = if (progress > 0.5f) "Close menu" else "Open menu",
                        tint = colors.foreground,
                    )
                }
                Text(
                    NavigationCatalog.labelFor(navItemIds.getOrElse(selectedIndex) { "" }),
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Box(Modifier.weight(1f)) { content() }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
    }
}

/** Fixed trigger with a true circular quarter-fan. A dedicated navigation strip
 * reserves space ABOVE page content, so the closed trigger cannot cover a page
 * title or connection banner. The common safe parent consumes remaining insets once. */
@Composable
internal fun CurvedRadialDrawerShell(
    colors: ChatyColors,
    navItemIds: List<String>,
    selectedIndex: Int,
    animationsDisabled: Boolean,
    onSelect: (Int) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
    initialOpen: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(initialOpen) }
    var page by remember { mutableStateOf(0) }
    // Read in composition so the semantics modifier receives a new immutable
    // label with the same state that selects the visible Menu/X glyph.
    val triggerLabel = if (menuOpen) "Close navigation menu" else "Open navigation menu"
    val progress by animateFloatAsState(
        targetValue = if (menuOpen) 1f else 0f,
        animationSpec = tween(if (animationsDisabled) 0 else 190),
        label = "radialFan",
    )
    androidx.activity.compose.BackHandler(enabled = menuOpen) { menuOpen = false }

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
            .clipToBounds(),
        // Coordinates below are physical-left offsets, including in RTL.
        // A relative TopStart parent would right-align the small controls first.
        contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft,
    ) {
            // Content and FAB share the same consumed inset parent as the trigger.
            // Padding changes measured content bounds, unlike a floating overlay.
            Box(Modifier.fillMaxSize().padding(top = RadialMenuGeometry.navigationStripHeight.dp)) {
                content()
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
            Box(Modifier.fillMaxWidth().height(RadialMenuGeometry.navigationStripHeight.dp)
                .background(colors.surface))

            val geometry = RadialMenuGeometry.layout(maxWidth.value, maxHeight.value, navItemIds.size)
            val pageCount = if (navItemIds.isEmpty()) 1 else
                (navItemIds.size + geometry.pageSize - 1) / geometry.pageSize
            val currentPage = page.coerceIn(0, pageCount - 1)
            val firstIndex = currentPage * geometry.pageSize
            val visibleIds = navItemIds.drop(firstIndex).take(geometry.pageSize)
            val visibleGeometry = RadialMenuGeometry.layout(maxWidth.value, maxHeight.value, visibleIds.size)
            val half = RadialMenuGeometry.targetSize / 2f
            // Keep the fan on ordinary phones; compact windows use a reachable list.
            val canOpen = geometry.radius >= 160f
            if ((menuOpen || progress > 0f) && canOpen) {
                Box(Modifier.fillMaxSize().clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClickLabel = "Close navigation menu",
                ) { menuOpen = false })
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = colors.primary.copy(alpha = progress),
                        radius = (geometry.radius + half + 12f).dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(geometry.anchor.x.dp.toPx(), geometry.anchor.y.dp.toPx()),
                    )
                }
                visibleIds.forEachIndexed { localIndex, id ->
                    val point = visibleGeometry.centers[localIndex]
                    val index = firstIndex + localIndex
                    val selected = index == selectedIndex
                    // A page replaces destination identities, not only the glyph.
                    // Fresh semantics nodes prevent a previous page's labels from
                    // surviving while the visible page/callbacks have changed.
                    androidx.compose.runtime.key(id) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.absoluteOffset(x = (point.x - half).dp, y = (point.y - half).dp)
                            .size(RadialMenuGeometry.targetSize.dp)
                            .graphicsLayer { alpha = progress; scaleX = progress; scaleY = progress }
                            .shadow(if (selected) 4.dp else 1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(colors.onPrimary)
                            .border(if (selected) 3.dp else 1.dp, if (selected) colors.foreground else colors.onPrimary.copy(alpha = 0.7f), CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = NavigationCatalog.labelFor(id)
                                this.selected = selected
                            }
                            .clickable(enabled = menuOpen, role = androidx.compose.ui.semantics.Role.Tab) {
                                onSelect(index)
                                menuOpen = false
                            },
                    ) {
                        NavDestinationIcon(id, size = 24, tint = colors.primary)
                    }
                    // User directive: the selected radial destination also shows its
                    // label, under the circle.
                    if (selected) {
                        Text(
                            NavigationCatalog.labelFor(id),
                            color = colors.foreground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.absoluteOffset(x = (point.x - half - 28).dp, y = (point.y + half + 3).dp)
                                .width((RadialMenuGeometry.targetSize + 56).dp)
                                .graphicsLayer { alpha = progress },
                        )
                    }
                    }
                }
                if (pageCount > 1) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.absoluteOffset(
                            x = (geometry.anchor.x + geometry.radius * 0.35f - half).dp,
                            y = (geometry.anchor.y + geometry.radius * 0.35f - half).dp,
                        ).size(56.dp).clip(CircleShape).background(colors.surface)
                            .clickable(enabled = menuOpen, onClickLabel = "Next navigation items") { page = (currentPage + 1) % pageCount },
                    ) {
                        androidx.compose.runtime.key(currentPage, pageCount) {
                            Text("${currentPage + 1}/$pageCount", color = colors.foreground, fontSize = 12.sp)
                        }
                    }
                }
            } else if (menuOpen) {
                // Compact landscape / split-screen: never shrink targets or push them
                // into system bars to force a circle. Keep every destination reachable.
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = RadialMenuGeometry.navigationStripHeight.dp)
                        .background(colors.surface)
                        .verticalScroll(rememberScrollState()),
                ) {
                    navItemIds.forEachIndexed { index, id ->
                        // In a compact window give text the whole width instead of
                        // reserving a permanent icon lane that can orphan letters.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .semantics(mergeDescendants = true) { this.selected = index == selectedIndex }
                                .background(if (index == selectedIndex) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(role = androidx.compose.ui.semantics.Role.Tab) {
                                    onSelect(index)
                                    menuOpen = false
                                }.padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            NavDestinationIcon(id, size = 24, tint = colors.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                NavigationCatalog.labelFor(id), color = colors.foreground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
            // One persistent node: Menu changes to X in place, outside the decorative clipping.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.absoluteOffset(x = (geometry.anchor.x - half).dp, y = (geometry.anchor.y - half).dp)
                    .size(RadialMenuGeometry.targetSize.dp)
                    .shadow(4.dp, CircleShape).clip(CircleShape).background(colors.surface)
                    // Label the actual 56dp interactive target, not the 24dp glyph.
                    .semantics(mergeDescendants = true) {
                        contentDescription = triggerLabel
                    }
                    .clickable(onClickLabel = triggerLabel) {
                        menuOpen = !menuOpen
                        page = 0
                    },
            ) {
                Icon(
                    if (menuOpen) Icons.Rounded.Close else Icons.Rounded.Menu,
                    contentDescription = null,
                    tint = colors.foreground,
                    modifier = Modifier.size(24.dp),
                )
            }
    }
}

/** Port of _buildRailShell: Material NavigationRail with the exact
 *  per-appearance-index tables (compact set, label set, 20 indicator radii,
 *  group alignment set, indicator alpha set, icon sizes). */
@Composable
internal fun RailShell(
    colors: ChatyColors,
    navItemIds: List<String>,
    selectedIndex: Int,
    appearanceNavigationIndex: Int,
    maxWidth: Dp,
    onSelect: (Int) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val index = appearanceNavigationIndex
    val compact = index in setOf(1, 2, 6, 7, 15, 19)
    val showAllLabels = index in setOf(0, 3, 4, 8, 9, 17, 18) && maxWidth >= 900.dp
    val indicatorRadius = listOf(18, 12, 8, 24, 10, 20, 8, 30, 14, 20, 16, 24, 12, 22, 14, 8, 24, 10, 16, 8)[index]
    val groupAlignment = if (index in setOf(5, 16)) 0f else -0.72f
    val indicatorAlpha = if (index in setOf(2, 7, 9, 19)) 0.08f else 0.16f

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(if (showAllLabels) 190.dp else if (compact) 58.dp else 72.dp)
                    .fillMaxHeight()
                    .background(colors.surface)
                    .offset(y = (groupAlignment * 80).dp),
            ) {
                navItemIds.forEachIndexed { i, id ->
                    val isSel = selectedIndex == i
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(indicatorRadius.dp))
                            .background(if (isSel) colors.primary.copy(alpha = indicatorAlpha) else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        NavDestinationIcon(
                            id,
                            size = if (compact) 20 else 23,
                            tint = if (isSel) colors.primary else colors.foregroundSecondary,
                        )
                        if (showAllLabels || isSel) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                NavigationCatalog.labelFor(id),
                                color = if (isSel) colors.foreground else colors.foregroundSecondary,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.surfaceElevated))
            Box(Modifier.weight(1f)) { content() }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
    }
}
