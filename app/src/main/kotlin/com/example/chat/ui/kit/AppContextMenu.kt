package com.example.chat.ui.kit

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Port of ContextMenuItem / ContextMenuSection (lib/ui/core/menu/app_context_menu.dart). */
data class ContextMenuItem(
    val label: String,
    val onTap: () -> Unit,
    val icon: ImageVector? = null,
    val iconWidget: (@Composable () -> Unit)? = null,
    val subtitle: String? = null,
    val isDestructive: Boolean = false,
    val trailing: (@Composable () -> Unit)? = null,
    val isEnabled: Boolean = true,
)

data class ContextMenuSection(val items: List<ContextMenuItem>, val title: String? = null)

/**
 * Port of lib/ui/core/menu/app_context_menu.dart. With [anchorRect] (window px, e.g. boundsInWindow()) the menu is the
 * Dart anchored popover: [ContextSurfaceResolver] placement, scale 0.92->1 + fade over 170ms easeOutCubic from the
 * resolved transform origin, scrim 0x33 + 0.28 black, tap-outside dismiss, _AnchoredMenuLayout card (radius 16,
 * border 1.1 @0.18, two shadows approximated by one elevation shadow). Without an anchor it is the Dart bottom-sheet
 * fallback (_showFallbackModal). [reactionRail] reproduces showWithReactionRail (title/subtitle ignored there, like Dart).
 * Uncompiled; render differences (shadow blur, window insets inside Dialog) need the pixel pass.
 */
data class ReactionRail(
    val emojis: List<String>,
    val onReact: (String) -> Unit,
    val onAdd: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContextMenu(
    onDismiss: () -> Unit,
    sections: List<ContextMenuSection>,
    title: String? = null,
    subtitle: String? = null,
    backgroundColor: Color? = null,
    primaryTextColor: Color? = null,
    secondaryTextColor: Color? = null,
    destructiveColor: Color? = null,
    anchorRect: Rect? = null,
    anchorType: ContextAnchorType = ContextAnchorType.generic,
    minWidth: Float = 150f,
    maxWidth: Float = 240f,
    reactionRail: ReactionRail? = null,
) {
    val colors = LocalAppColors.current
    val bg = backgroundColor ?: colors.surfaceElevated
    val primary = primaryTextColor ?: colors.foreground
    val secondary = secondaryTextColor ?: colors.foregroundSecondary
    val danger = destructiveColor ?: colors.error
    if (anchorRect != null) {
        AnchoredContextSurface(
            anchorRect, anchorType, onDismiss, bg, primary, secondary, danger,
            sections, if (reactionRail != null) null else title, if (reactionRail != null) null else subtitle,
            minWidth, if (reactionRail != null && maxWidth == 240f) 260f else maxWidth, reactionRail,
        )
        return
    }
    ModalBottomSheet(tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        containerColor = bg,
        shape = com.example.chat.ui.kit.ChatyModalShape,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!title.isNullOrEmpty()) {
                Text(title, Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp), color = primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), color = secondary, fontSize = 12.sp)
                }
                HorizontalDivider(thickness = 0.8.dp, color = secondary.copy(alpha = 0.12f))
            }
            ContextSectionsList(sections, primary, secondary, danger, onDismiss)
        }
    }
}

/** Dart AppContextMenu._buildSectionsList (pop first, then item.onTap). */
@Composable
internal fun ContextSectionsList(
    sections: List<ContextMenuSection>,
    primary: Color,
    secondary: Color,
    danger: Color,
    onDismiss: () -> Unit,
) {
    sections.forEachIndexed { s, section ->
        if (s > 0) HorizontalDivider(thickness = 0.8.dp, color = secondary.copy(alpha = 0.12f))
        section.title?.let {
            Text(it, Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp), color = secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
        }
        section.items.forEach { item ->
            val color = if (!item.isEnabled) secondary.copy(alpha = 0.5f) else if (item.isDestructive) danger else primary
            Row(
                Modifier.fillMaxWidth()
                    .then(if (item.isEnabled) Modifier.clickable { onDismiss(); item.onTap() } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.iconWidget != null) {
                    item.iconWidget.invoke(); Spacer(Modifier.width(12.dp))
                } else if (item.icon != null) {
                    Icon(item.icon, null, Modifier.size(18.dp), tint = color); Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (item.subtitle != null) {
                        Spacer(Modifier.height(1.dp))
                        Text(item.subtitle, color = secondary, fontSize = 11.5.sp)
                    }
                }
                item.trailing?.invoke()
            }
        }
    }
}

/** ContextSurfaceController.showSurface + _AnchoredMenuLayout. */
@Composable
private fun AnchoredContextSurface(
    anchorRect: Rect,
    anchorType: ContextAnchorType,
    onDismiss: () -> Unit,
    bg: Color,
    primary: Color,
    secondary: Color,
    danger: Color,
    sections: List<ContextMenuSection>,
    title: String?,
    subtitle: String?,
    minWidth: Float,
    maxWidth: Float,
    rail: ReactionRail?,
) {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val total = sections.sumOf { it.items.size }
    // Dart: show -> (title?48:0)+items*44+16 ; reaction rail -> 56+8+items*44
    val estimatedHeight = if (rail != null) 56f + 8f + total * 44f else (if (title != null) 48f else 0f) + total * 44f + 16f
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val safe = WindowInsets.safeDrawing
        val ime = WindowInsets.ime
        fun px(v: Int) = with(density) { v.toDp().value }
        val screen = SurfaceSize(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat())
        val insets = SurfaceInsets(px(safe.getLeft(density, dir)), px(safe.getTop(density)), px(safe.getRight(density, dir)), px(safe.getBottom(density)))
        val keyboard = SurfaceInsets(bottom = px(ime.getBottom(density)))
        val anchor = with(density) {
            SurfaceRect(anchorRect.left.toDp().value, anchorRect.top.toDp().value, anchorRect.width.toDp().value, anchorRect.height.toDp().value)
        }
        val res = ContextSurfaceResolver.resolve(
            ContextSurfaceRequest(anchor, SurfaceSize(maxWidth, estimatedHeight), insets, screen, keyboard, anchorType),
        )
        val transition = updateTransition(state, label = "contextSurface")
        val curved by transition.animateFloat(
            transitionSpec = { tween(170, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)) }, label = "t",
        ) { if (it) 1f else 0f }
        Box(
            Modifier.fillMaxSize().background(Color(0x33000000))
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val o = res.transformOrigin
            Column(
                Modifier
                    .offset(res.targetRect.left.dp, res.targetRect.top.dp)
                    .requiredWidth(res.targetRect.width.dp)
                    .heightIn(max = res.maxHeight.dp)
                    .graphicsLayer {
                        alpha = curved
                        val sc = 0.92f + 0.08f * curved
                        scaleX = sc; scaleY = sc
                        transformOrigin = TransformOrigin((o.x + 1f) / 2f, (o.y + 1f) / 2f)
                    }
                    .shadow(12.dp, ChatyModalShape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.16f))
                    .background(bg, ChatyModalShape)
                    .border(1.1.dp, secondary.copy(alpha = 0.18f), ChatyModalShape)
                    .clip(ChatyModalShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .verticalScroll(rememberScrollState()),
            ) {
                if (!title.isNullOrEmpty()) {
                    Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 8.dp)) {
                        Text(title, color = primary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!subtitle.isNullOrEmpty()) {
                            Spacer(Modifier.height(1.dp))
                            Text(subtitle, color = secondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(thickness = 0.8.dp, color = secondary.copy(alpha = 0.12f))
                }
                if (rail != null) {
                    ReactionRailRow(rail, onDismiss)
                    HorizontalDivider(thickness = 0.8.dp, color = secondary.copy(alpha = 0.12f))
                }
                Column(Modifier.padding(vertical = 4.dp)) {
                    ContextSectionsList(sections, primary, secondary, danger, onDismiss)
                }
            }
        }
    }
}

/** Dart _ReactionRailWidget: spaceAround, emoji 22sp padding 5, 32dp add circle (surfaceContainerHighest @0.6). */
@Composable
private fun ReactionRailRow(rail: ReactionRail, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rail.emojis.forEach { e ->
            Box(Modifier.clip(CircleShape).clickable { onDismiss(); rail.onReact(e) }.padding(5.dp)) { Text(e, fontSize = 22.sp) }
        }
        rail.onAdd?.let { add ->
            Box(
                Modifier.padding(horizontal = 2.dp).size(32.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .clickable { onDismiss(); add() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
