package com.example.chat.ui.settings.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.CurvedRadialDrawerShell
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.core.theme.ThemeConfig

/** Port of _HomePreviewCurveClipper. */
private object HomePreviewCurveShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f)
            cubicTo(w * 1.02f, h * 0.45f, w * 0.72f, h * 0.98f, 0f, h * 0.78f)
            close()
        }
        return Outline.Generic(p)
    }
}

private val navIcons: List<ImageVector> get() = listOf(Icons.Rounded.ChatBubble, Icons.Rounded.AutoStories, Icons.Rounded.Call, Icons.Rounded.Settings)

/**
 * Port of HeaderSettingsScreen._buildHomeUiPreview(style, theme, large). Static mini-mock of each
 * home navigation style. UNCOMPILED (Compose), no pixel check. Dart Positioned() boxes are
 * reproduced with offset+size inside a BoxWithConstraints.
 */
@Composable
fun HomeUiPreview(style: String, theme: ThemeConfig, current: AppNavigationMode, modifier: Modifier = Modifier, large: Boolean = false) {
    val accent = Color(theme.accentColor)
    val fg = Color(theme.primaryTextColor)
    val muted = Color(theme.secondaryTextColor)
    val surface = Color(theme.surfaceColor)
    val bg = Color(theme.backgroundColor)
    val onAccent = Color(theme.onAccentColor)
    val mode = HomeUiStyles.navigationModeForHomeUiStyle(style, current)
    val scale = if (large) 1f else 0.55f
    val topTabs = mode == AppNavigationMode.topWhatsAppBar
    val rail = mode == AppNavigationMode.floatingIslandRail
    val isInstagram = mode == AppNavigationMode.instagramStyle
    val isTelegram = mode == AppNavigationMode.telegramStyle
    val isOneUi = mode == AppNavigationMode.oneUi
    val isIos = mode == AppNavigationMode.iosStyle
    val isBubbles = mode == AppNavigationMode.gestureTabs
    val isBasic = mode == AppNavigationMode.basicTabStyle
    val curved = mode == AppNavigationMode.curvedRadialDrawer
    val sideDrawer = mode == AppNavigationMode.perspective3DDrawer || mode == AppNavigationMode.modernSideMenu || curved
    val showBottomDock = !rail && !sideDrawer && !topTabs
    val previewHeight = if (large) (if (curved) 205f else 128f) else 62f
    val sd = (scale).toFloat()
    fun s(v: Float) = (v * sd).dp

    Box(
        (if (large) modifier.fillMaxWidth() else modifier.width(100.dp)).height(previewHeight.dp)
            .background(bg).border(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxW = maxWidth
            val maxH = maxHeight
            if (curved) {
                CurvedRadialThumb(theme, large, maxW)
                return@BoxWithConstraints
            }
            if (rail) {
                Column(
                    Modifier.offset(s(3f), s(3f)).width(s(21f)).height(maxH - s(6f))
                        .background(surface, RoundedCornerShape(s(8f)))
                        .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(s(8f))),
                    verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    navIcons.forEach { Icon(it, null, tint = accent, modifier = Modifier.size(s(11f))) }
                }
            }
            if (sideDrawer && !curved) {
                Column(
                    Modifier.width(s(if (large) 92f else 36f)).fillMaxHeight().background(surface).padding(s(5f)),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (i in 0 until 4) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(navIcons[i], null, tint = if (i == 0) accent else muted, modifier = Modifier.size(s(10f)))
                            if (large) {
                                Spacer(Modifier.width(4.dp))
                                Box(Modifier.weight(1f).height(3.dp).background(muted.copy(alpha = 0.55f), RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
            val left = s(if (rail) 29f else if (sideDrawer && !curved) (if (large) 100f else 40f) else 0f)
            val bottomInset = s(if (showBottomDock) (if (large) 21f else 12f) else 4f)
            Column(
                Modifier.offset(left, 0.dp).width(maxW - left).height(maxH - bottomInset).padding(s(7f)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (topTabs) {
                        Icon(Icons.Rounded.Menu, null, tint = accent, modifier = Modifier.size(s(12f)))
                        Spacer(Modifier.width(s(3f)))
                    }
                    Box(Modifier.weight(1f).height(s(4f)).background(if (topTabs) accent else fg.copy(alpha = 0.85f), RoundedCornerShape(4.dp)))
                    if (large) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Search, null, tint = fg, modifier = Modifier.size(13.dp))
                    }
                }
                Spacer(Modifier.height(s(5f)))
                if (topTabs) {
                    Row(
                        Modifier.fillMaxWidth().height(s(16f)).background(accent, RoundedCornerShape(s(3f))),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                    ) { navIcons.forEach { Icon(it, null, tint = onAccent, modifier = Modifier.size(s(9f))) } }
                    Spacer(Modifier.height(s(3f)))
                }
                Spacer(Modifier.height(s(7f)))
                if (style == "IOS STYLE") {
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 4) {
                            Box(Modifier.weight(1f).padding(end = s(3f))) {
                                Box(
                                    Modifier.fillMaxWidth().height(s(17f))
                                        .background(accent.copy(alpha = 0.16f + i * 0.08f), CircleShape)
                                        .border(1.dp, accent.copy(alpha = 0.65f), CircleShape),
                                )
                            }
                        }
                    }
                }
                for (i in 0 until 3) {
                    Spacer(Modifier.height(s(5f)))
                    Row {
                        Box(Modifier.size(s(13f)).background(accent.copy(alpha = 0.32f + i * 0.12f), CircleShape))
                        Spacer(Modifier.width(s(5f)))
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.height(s(3f)).width(s(if (large) 120f else 58f)).background(fg.copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
                            Spacer(Modifier.height(s(3f)))
                            Box(Modifier.height(s(2f)).width(s(if (large) 170f else 64f)).background(muted.copy(alpha = 0.7f), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
            if (showBottomDock) {
                val dockH = s(if (isInstagram) 18f else if (isBasic) 26f else 25f)
                Row(
                    Modifier.offset(0.dp, maxH - dockH).fillMaxWidth().height(dockH)
                        .background(surface),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (i in 0 until 4) {
                        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = s(1f)), contentAlignment = Alignment.Center) {
                            val on = i == 0
                            when {
                                isBubbles -> Box(
                                    Modifier.size(s(16f))
                                        .background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent, CircleShape)
                                        .border(0.65.dp, if (on) accent else muted.copy(alpha = 0.45f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(navIcons[i], null, tint = if (on) accent else muted, modifier = Modifier.size(s(8f))) }
                                isOneUi -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Box(Modifier.width(if (on) s(14f) else 0.dp).height(s(1.5f)).background(accent))
                                    Icon(navIcons[i], null, tint = if (on) accent else muted, modifier = Modifier.size(s(9f)))
                                    Box(Modifier.width(s(10f)).height(s(1f)).background(if (on) fg.copy(alpha = 0.65f) else muted.copy(alpha = 0.45f)))
                                }
                                isIos -> Box(
                                    Modifier.size(s(16f)).background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val ic = listOf(Icons.Rounded.ChatBubbleOutline, Icons.Rounded.Search, Icons.Outlined.AddBox, Icons.Rounded.PersonOutline)[i]
                                    Icon(ic, null, tint = if (on) accent else muted, modifier = Modifier.size(s(9f)))
                                }
                                isInstagram -> {
                                    val ic = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Outlined.AddBox, Icons.Rounded.PersonOutline)[i]
                                    Icon(ic, null, tint = if (on) fg else muted, modifier = Modifier.size(s(11f)))
                                }
                                isTelegram -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val ic = listOf(Icons.Rounded.ChatBubble, Icons.Rounded.Contacts, Icons.Rounded.Call, Icons.Rounded.Settings)[i]
                                    Icon(ic, null, tint = if (on) accent else muted, modifier = Modifier.size(s(9f)))
                                    Box(Modifier.padding(top = s(2f)).width(s(10f)).height(s(1f)).background(if (on) accent else muted.copy(alpha = 0.4f)))
                                }
                                isBasic -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(navIcons[i], null, tint = if (on) accent else muted, modifier = Modifier.size(s(9f)))
                                    Box(Modifier.padding(top = s(2f)).width(s(15f)).height(s(1.3f)).background(if (on) fg else muted.copy(alpha = 0.4f)))
                                }
                                else -> Icon(navIcons[i], null, tint = if (on) accent else muted, modifier = Modifier.size(s(10f)))
                            }
                        }
                    }
                }
                // Dart dock top border: 0.7 logical px, muted @ 0.28
                Box(Modifier.offset(0.dp, maxH - dockH).fillMaxWidth().height(0.7.dp).background(muted.copy(alpha = 0.28f)))
            }
        }
    }
}

/**
 * Curved Radial thumbnail: the ACTUAL CurvedRadialDrawerShell laid out at a phone-sized viewport,
 * scaled uniformly by width and cropped to the card, so it shows the same trigger/fan the applied
 * style draws (top-left of the phone). Source only, not compiled or run. Pointer input is
 * swallowed so the picker row, not the shell, receives taps.
 */
@Composable
private fun CurvedRadialThumb(theme: ThemeConfig, large: Boolean, cardWidth: Dp) {
    val phoneW = 392.dp
    val phoneH = 760.dp
    val scale = cardWidth.value / phoneW.value
    val ids = listOf("chats", "updates", "calls", "tasks", "settings")
    Box(Modifier.fillMaxSize().clipToBounds().consumeWindowInsets(WindowInsets.safeDrawing)) {
        Box(
            Modifier.requiredSize(phoneW, phoneH).graphicsLayer {
                scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            CurvedRadialDrawerShell(
                colors = ChatyColors(theme), navItemIds = ids, selectedIndex = 0,
                animationsDisabled = true, onSelect = {}, fab = {},
                initialOpen = large,
                content = {
                    Column(Modifier.fillMaxSize().background(Color(theme.backgroundColor)).padding(14.dp, 12.dp)) {
                        Box(Modifier.width(150.dp).height(12.dp).background(Color(theme.primaryTextColor).copy(alpha = 0.85f), RoundedCornerShape(6.dp)))
                        for (i in 0 until 5) {
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(Color(theme.accentColor).copy(alpha = 0.32f + i * 0.08f), CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Box(Modifier.width(170.dp).height(10.dp).background(Color(theme.primaryTextColor).copy(alpha = 0.8f), RoundedCornerShape(5.dp)))
                                    Spacer(Modifier.height(8.dp))
                                    Box(Modifier.width(220.dp).height(8.dp).background(Color(theme.secondaryTextColor).copy(alpha = 0.7f), RoundedCornerShape(4.dp)))
                                }
                            }
                        }
                    }
                },
            )
        }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
                }
            },
        )
    }
}
