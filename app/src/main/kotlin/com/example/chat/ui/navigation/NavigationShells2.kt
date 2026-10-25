package com.example.chat.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.rotate
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
import kotlin.math.pow
import kotlin.math.sin

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
) {
    var open by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(if (animationsDisabled) 0 else 280),
        label = "perspectiveDrawer",
    )

    Box(Modifier.fillMaxSize().background(colors.surfaceElevated)) {
        // Left menu
        Column(
            modifier = Modifier
                .width(210.dp)
                .fillMaxHeight()
                .padding(horizontal = 18.dp, vertical = 20.dp),
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
                    translationX = 220.dp.toPx() * progress
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

/** Port of _CurvedRadialDrawerScaffold + _CurvedRadialMenuClipper: accent
 *  quarter-circle panel clipped by the same cubic path, radial icon/label
 *  placement formulas preserved exactly. */
@Composable
internal fun CurvedRadialDrawerShell(
    colors: ChatyColors,
    navItemIds: List<String>,
    selectedIndex: Int,
    animationsDisabled: Boolean,
    onSelect: (Int) -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = colors.primary
    val onAccent = colors.onPrimary

    Box(Modifier.fillMaxSize().background(colors.background)) {
        content()
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelWidth = maxWidth.coerceAtMost(420.dp)
            val panelHeight = if (maxHeight < 250.dp) maxHeight
                else (maxHeight * 0.60f).coerceIn(250.dp, 360.dp)

            if (menuOpen) {
                val panelAlpha by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(if (animationsDisabled) 0 else 190),
                    label = "radialPanel",
                )
                Box(
                    modifier = Modifier
                        .padding(start = 0.dp, top = 3.dp)
                        .width(panelWidth)
                        .height(panelHeight)
                        .graphicsLayer { alpha = panelAlpha }
                        .clip(CurvedRadialMenuShape)
                        .background(accent),
                ) {
                    // Close button
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp, top = 8.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(onAccent.copy(alpha = 0.98f)),
                    ) {
                        IconButton(onClick = { menuOpen = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close navigation menu", tint = accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    // Radial items (Dart formulas preserved).
                    val count = navItemIds.size
                    if (count > 0) {
                        val iconSize = if (count > 7) 23f else 26f
                        val activeIndex = selectedIndex.coerceIn(0, count - 1)
                        navItemIds.forEachIndexed { index, id ->
                            val progress = if (count <= 1) 0f else index.toFloat() / (count - 1)
                            val leftFraction = 0.20f -
                                0.09f * sin(Math.PI.toFloat() * progress) +
                                0.44f * progress * progress
                            val topFraction = 0.04f + 0.50f * progress.toDouble().pow(0.95).toFloat()
                            val selected = id == navItemIds[activeIndex]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .offset(x = panelWidth * leftFraction, y = panelHeight * topFraction)
                                    .height((iconSize + 2).dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(iconSize.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) onAccent else onAccent.copy(alpha = 0.86f))
                                        .border(
                                            width = if (selected) 1.4.dp else 0.7.dp,
                                            color = if (selected) onAccent else onAccent.copy(alpha = 0.7f),
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            onSelect(index)
                                            menuOpen = false
                                        },
                                ) {
                                    NavDestinationIcon(id, size = (iconSize * 0.52f).toInt(), tint = accent)
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    NavigationCatalog.labelFor(id).uppercase(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = onAccent,
                                    fontSize = if (selected) 10.5.sp else 9.5.sp,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                                    letterSpacing = 0.25.sp,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .rotate(-0.04f * 180f / Math.PI.toFloat() - progress * 0.30f * 180f / Math.PI.toFloat())
                                        .clickable {
                                            onSelect(index)
                                            menuOpen = false
                                        },
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, top = 8.dp)
                        .shadow(5.dp, CircleShape)
                        .clip(CircleShape)
                        .background(colors.surface),
                ) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Open navigation menu", tint = colors.foreground)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
    }
}

/** Port of _CurvedRadialMenuClipper's path as a Shape. */
private val CurvedRadialMenuShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.16f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.62f)
            cubicTo(w * 0.78f, h * 0.74f, w * 0.52f, h * 0.72f, w * 0.30f, h * 0.62f)
            cubicTo(w * 0.08f, h * 0.51f, -w * 0.05f, h * 0.31f, w * 0.055f, h * 0.12f)
            cubicTo(w * 0.075f, h * 0.055f, w * 0.11f, 0f, w * 0.16f, 0f)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
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
