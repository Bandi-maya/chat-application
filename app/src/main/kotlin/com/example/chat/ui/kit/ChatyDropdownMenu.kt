package com.example.chat.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.chat.ui.theme.LocalAppColors

/** One presentation mapping for the root, navigation shell, and real previews. */
fun referenceLayoutStyleFor(mode: com.example.chat.core.theme.AppNavigationMode): ReferenceLayoutStyle = when (mode) {
    com.example.chat.core.theme.AppNavigationMode.oneUi -> ReferenceLayoutStyle.Default
    com.example.chat.core.theme.AppNavigationMode.topWhatsAppBar -> ReferenceLayoutStyle.Old
    com.example.chat.core.theme.AppNavigationMode.iosStyle -> ReferenceLayoutStyle.Ios
    else -> ReferenceLayoutStyle.Other
}

val LocalReferenceLayoutPresentation = androidx.compose.runtime.staticCompositionLocalOf { ReferenceLayoutPresentation() }

/** Content-sized popup, bounded to the device width and 80% of its available height.
 * Intrinsic width prevents weighted menu labels from expanding to the full popup constraint.
 * Foundation handles scrolling and Popup handles Back/outside dismissal.
 */
/** Session-scoped scroll memory for menus that opt in via rememberKey: reopening
 *  lands on the same option area instead of the top (user directive: reopen from
 *  the last used option, not from the start). */
private val chatyMenuScrollMemory = HashMap<String, Int>()

@Composable
fun ChatyDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    rememberKey: String? = null,
    shape: Shape = ChatyModalShape,
    containerColor: Color = LocalAppColors.current.surfaceElevated,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val presentation = LocalReferenceLayoutPresentation.current
    val menuVisibility = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    menuVisibility.targetState = expanded
    if (!menuVisibility.currentState && !menuVisibility.targetState) return
    // Existing callers using the shared default shape opt into the active layout.
    // An explicit custom shape remains the caller's choice.
    val effectiveShape = if (shape == ChatyModalShape)
        androidx.compose.foundation.shape.RoundedCornerShape(presentation.menuCornerDp.dp) else shape
    val configuration = LocalConfiguration.current
    val maxMenuWidth = (configuration.screenWidthDp.dp - 16.dp).coerceAtLeast(1.dp)
    val maxMenuHeight = (configuration.screenHeightDp.dp * 0.8f).coerceAtLeast(1.dp)
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    // User directive: the menu scales/fades out from the tapped anchor, not a
    // fixed corner. The position provider reports where the anchor's centre falls
    // inside the placed popup; that point becomes the transform origin.
    var menuTransformOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val position = remember(margin) {
        ChatyMenuPositionProvider(margin) { ox, oy -> menuTransformOrigin = TransformOrigin(ox, oy) }
    }
    val menuScrollState = remember(rememberKey) { ScrollState(rememberKey?.let { chatyMenuScrollMemory[it] } ?: 0) }
    if (rememberKey != null) {
        LaunchedEffect(menuScrollState.value) { chatyMenuScrollMemory[rememberKey] = menuScrollState.value }
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = menuVisibility,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(presentation.menuEnterMs)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(presentation.menuEnterMs), initialScale = 0.96f, transformOrigin = menuTransformOrigin),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(presentation.menuExitMs)) +
                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(presentation.menuExitMs), targetScale = 0.98f, transformOrigin = menuTransformOrigin),
        ) {
        Column(
            modifier.shadow(12.dp, effectiveShape).clip(effectiveShape)
                .background(containerColor)
                .border(border ?: BorderStroke(1.dp, colors.border), effectiveShape)
                .widthIn(max = maxMenuWidth)
                .width(IntrinsicSize.Max)
                .heightIn(max = maxMenuHeight)
                .verticalScroll(menuScrollState)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { content() }
        }
    }
}

/** A single 48dp touch target; caller-owned text/icon content and actions stay unchanged. */
@Composable
fun ChatyDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
) {
    val colors = LocalAppColors.current
    val rowShape = androidx.compose.foundation.shape.RoundedCornerShape(
        LocalReferenceLayoutPresentation.current.menuCornerDp.coerceAtMost(12).dp)
    CompositionLocalProvider(LocalContentColor provides if (enabled) colors.foreground else colors.disabledForeground) {
        Row(
            modifier.defaultMinSize(minHeight = 48.dp)
                .clip(rowShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let { it(); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) { text() }
            trailingIcon?.let { Spacer(Modifier.width(12.dp)); it() }
        }
    }
}

/** Keeps menus on screen and flips above the anchor when there is no room below. */
internal class ChatyMenuPositionProvider(
    private val margin: Int,
    private val onOrigin: ((Float, Float) -> Unit)? = null,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val below = anchorBounds.bottom + margin
        val preferredY = if (below + popupContentSize.height <= windowSize.height - margin) below
            else anchorBounds.top - popupContentSize.height - margin
        val placed = IntOffset(preferredX.coerceIn(margin, maxX), preferredY.coerceIn(margin, maxY))
        if (onOrigin != null && popupContentSize.width > 0 && popupContentSize.height > 0) {
            val anchorCenterX = (anchorBounds.left + anchorBounds.right) / 2f
            val originX = ((anchorCenterX - placed.x) / popupContentSize.width).coerceIn(0f, 1f)
            // Below the anchor the menu grows downward from it; above, upward.
            val originY = if (placed.y >= anchorBounds.bottom) 0f else 1f
            onOrigin(originX, originY)
        }
        return placed
    }
}
