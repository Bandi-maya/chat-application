package com.example.chat.ui.core.components.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Port of lib/ui/core/components/navigation/chaty_navigation_renderer.dart (api 3bee780d).
 *  Motion constants are app_tokens.dart's ChatyMotion (instant 100ms, fast 160ms, activeScale 0.97, easeOutCubic),
 *  which this file imports in Dart - NOT chaty_motion.dart's 80/140 used by ui/kit/ChatyKit.kt. */
private const val INSTANT_MS = 100
private const val FAST_MS = 160
private const val ACTIVE_SCALE = 0.97f
private val enter = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/** Semantic navigation item model, independent of visual variants. */
class ChatyNavItemData(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val isSelected: Boolean,
    val onTap: () -> Unit,
    val activeIcon: ImageVector? = null,
    val badgeCount: Int = 0,
)

private fun ChatyNavItemData.shownIcon(): ImageVector = if (isSelected && activeIcon != null) activeIcon else icon

/** Delegates visual presentation to the chosen variant while keeping identical interaction and semantics.
 *  [reducedMotion] = Dart MediaQuery.disableAnimationsOf (durations become 0). */
@Composable
fun ChatyNavigationRenderer(
    variantId: String,
    items: List<ChatyNavItemData>,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    activeColor: Color? = null,
    inactiveColor: Color? = null,
    reducedMotion: Boolean = false,
) {
    val c = LocalAppColors.current
    val bg = backgroundColor ?: c.surface
    val active = activeColor ?: c.primary
    val inactive = inactiveColor ?: c.foregroundSecondary
    val fastMs = if (reducedMotion) 0 else FAST_MS
    val safe = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))

    when (variantId) {
        "floating_dock" -> Box(modifier.then(safe).padding(16.dp, 0.dp, 16.dp, 12.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = c.shadow.copy(alpha = 0.12f), spotColor = c.shadow.copy(alpha = 0.12f))
                    .background(bg, RoundedCornerShape(32.dp))
                    .border(1.dp, c.border, RoundedCornerShape(32.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                items.forEach { item ->
                    NavItemWrapper(item.onTap, reducedMotion) {
                        val tint by animateColorAsState(if (item.isSelected) active.copy(alpha = 0.14f) else Color.Transparent, tween(fastMs, easing = enter), label = "fd")
                        Box(Modifier.background(tint, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                            BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(22.dp), tint = if (item.isSelected) active else inactive) }
                        }
                    }
                }
            }
        }
        "capsule_rail" -> Box(modifier.background(bg).padding(vertical = 6.dp).then(safe)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                items.forEach { item ->
                    NavItemWrapper(item.onTap, reducedMotion) {
                        val fill by animateColorAsState(if (item.isSelected) active else Color.Transparent, tween(fastMs, easing = enter), label = "cr")
                        Row(Modifier.background(fill, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(20.dp), tint = if (item.isSelected) Color.White else inactive) }
                            if (item.isSelected) {
                                Spacer(Modifier.width(6.dp))
                                Text(item.label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.W700)
                            }
                        }
                    }
                }
            }
        }
        "split_segments" -> Box(modifier.background(bg).padding(12.dp, 6.dp, 12.dp, 6.dp).then(safe)) {
            Row(Modifier.fillMaxWidth()) {
                items.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        NavItemWrapper(item.onTap, reducedMotion) {
                            val fill by animateColorAsState(if (item.isSelected) active.copy(alpha = 0.16f) else c.surfaceSecondary, tween(fastMs, easing = enter), label = "ss")
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    .background(fill, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (item.isSelected) active else c.border, RoundedCornerShape(10.dp))
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(20.dp), tint = if (item.isSelected) active else inactive) }
                                Spacer(Modifier.height(2.dp))
                                Text(item.label, maxLines = 1, color = if (item.isSelected) active else inactive, fontSize = 10.5.sp,
                                    fontWeight = if (item.isSelected) FontWeight.W700 else FontWeight.W500)
                            }
                        }
                    }
                }
            }
        }
        "glass_dock" -> Box(modifier.then(safe).padding(16.dp, 0.dp, 16.dp, 12.dp)) {
            // Dart: BackdropFilter blur(16) + surface @0.72 + white@0.15 1.2 border. Compose has no portable
            // backdrop blur: translucent surface + border kept; blur is a documented limitation.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(bg.copy(alpha = 0.72f), RoundedCornerShape(28.dp))
                    .border(1.2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                items.forEach { item ->
                    NavItemWrapper(item.onTap, reducedMotion) {
                        val s by animateFloatAsState(if (item.isSelected) 1.08f else 1f, tween(fastMs, easing = enter), label = "gd")
                        Box(Modifier.scale(s)) {
                            BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(24.dp), tint = if (item.isSelected) active else inactive) }
                        }
                    }
                }
            }
        }
        "compact_icon_dock" -> Box(modifier.background(bg).padding(vertical = 8.dp).then(safe)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                items.forEach { item ->
                    // Dart Tooltip(message: label): long-press tooltip is delegated to the platform content description.
                    NavItemWrapper(item.onTap, reducedMotion) {
                        Box(Modifier.padding(8.dp)) {
                            BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), item.label, Modifier.size(24.dp), tint = if (item.isSelected) active else inactive) }
                        }
                    }
                }
            }
        }
        "orbit" -> Box(modifier.background(bg).padding(vertical = 6.dp).then(safe)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                items.forEach { item ->
                    NavItemWrapper(item.onTap, reducedMotion) {
                        Box(Modifier.border(2.dp, if (item.isSelected) active else Color.Transparent, CircleShape).padding(6.dp)) {
                            BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(22.dp), tint = if (item.isSelected) active else inactive) }
                        }
                    }
                }
            }
        }
        "elevated_pill" -> Box(modifier.then(safe).padding(16.dp, 4.dp, 16.dp, 8.dp)) {
            Row(
                Modifier.fillMaxWidth().background(c.surfaceSecondary, RoundedCornerShape(24.dp))
                    .border(0.8.dp, c.border, RoundedCornerShape(24.dp)).padding(4.dp),
            ) {
                items.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        NavItemWrapper(item.onTap, reducedMotion) {
                            val fill by animateColorAsState(if (item.isSelected) bg else Color.Transparent, tween(fastMs, easing = enter), label = "ep")
                            Box(
                                Modifier.fillMaxWidth()
                                    .then(if (item.isSelected) Modifier.shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = c.shadow.copy(alpha = 0.08f), spotColor = c.shadow.copy(alpha = 0.08f)) else Modifier)
                                    .background(fill, RoundedCornerShape(20.dp)).padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(22.dp), tint = if (item.isSelected) active else inactive) }
                            }
                        }
                    }
                }
            }
        }
        "line_marker" -> Box(modifier.background(bg).then(safe)) {
            Row(Modifier.fillMaxWidth()) {
                items.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        NavItemWrapper(item.onTap, reducedMotion) {
                            val w by animateDpAsState(if (item.isSelected) 28.dp else 0.dp, tween(fastMs, easing = enter), label = "lm")
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.height(3.dp).width(w).background(active, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.height(6.dp))
                                BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(22.dp), tint = if (item.isSelected) active else inactive) }
                                Spacer(Modifier.height(3.dp))
                                Text(item.label, maxLines = 1, color = if (item.isSelected) active else inactive, fontSize = 11.sp,
                                    fontWeight = if (item.isSelected) FontWeight.W700 else FontWeight.W500)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
        // adaptive_rail: Dart falls back to the classic label bar horizontally.
        else -> Column(modifier.background(bg)) {
            // Dart: Border(top: BorderSide(borderSubtle, 0.8)) then padding top/bottom 6 + SafeArea(top:false).
            Box(Modifier.fillMaxWidth().height(0.8.dp).background(c.borderSubtle))
            Box(Modifier.padding(top = 6.dp, bottom = 6.dp).then(safe)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                items.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        NavItemWrapper(item.onTap, reducedMotion) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                BadgeAnchor(item.badgeCount) { Icon(item.shownIcon(), null, Modifier.size(24.dp), tint = if (item.isSelected) active else inactive) }
                                Spacer(Modifier.height(3.dp))
                                Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (item.isSelected) active else inactive,
                                    fontSize = 11.5.sp, fontWeight = if (item.isSelected) FontWeight.W700 else FontWeight.W500, letterSpacing = (-0.1).sp)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** _NavItemWrapper: opaque tap target, selection haptic, press scale 0.97 over 100ms. */
@Composable
private fun NavItemWrapper(onTap: () -> Unit, reducedMotion: Boolean, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(if (pressed) ACTIVE_SCALE else 1f, tween(if (reducedMotion) 0 else INSTANT_MS, easing = enter), label = "navPress")
    Box(
        Modifier.scale(s).pointerInput(onTap) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    val released = tryAwaitRelease()
                    pressed = false
                    if (released) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTap()
                    }
                },
            )
        },
    ) { content() }
}

/** _BadgeAnchor: error-colored pill, "99+" cap, top-right overhang (-8, -4). */
@Composable
private fun BadgeAnchor(count: Int, child: @Composable () -> Unit) {
    if (count <= 0) { child(); return }
    val display = if (count > 99) "99+" else count.toString()
    Box {
        child()
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp)
                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                .background(LocalAppColors.current.error, RoundedCornerShape(10.dp))
                .padding(horizontal = 4.5.dp, vertical = 1.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(display, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.W800, lineHeight = 9.5.sp)
        }
    }
}
