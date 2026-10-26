package com.example.chat.ui.kit

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

/** Content-sized popup, bounded to the device width and 80% of its available height.
 * Intrinsic width prevents weighted menu labels from expanding to the full popup constraint.
 * Foundation handles scrolling and Popup handles Back/outside dismissal.
 */
@Composable
fun ChatyDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ChatyModalShape,
    containerColor: Color = LocalAppColors.current.surfaceElevated,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val colors = LocalAppColors.current
    val configuration = LocalConfiguration.current
    val maxMenuWidth = (configuration.screenWidthDp.dp - 16.dp).coerceAtLeast(1.dp)
    val maxMenuHeight = (configuration.screenHeightDp.dp * 0.8f).coerceAtLeast(1.dp)
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(margin) { ChatyMenuPositionProvider(margin) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier.shadow(12.dp, shape).clip(shape)
                .background(containerColor)
                .border(border ?: BorderStroke(1.dp, colors.border), shape)
                .widthIn(max = maxMenuWidth)
                .width(IntrinsicSize.Max)
                .heightIn(max = maxMenuHeight)
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { content() }
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
    CompositionLocalProvider(LocalContentColor provides if (enabled) colors.foreground else colors.disabledForeground) {
        Row(
            modifier.defaultMinSize(minHeight = 48.dp)
                .clip(ChatyModalShape)
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
internal class ChatyMenuPositionProvider(private val margin: Int) : PopupPositionProvider {
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
        return IntOffset(preferredX.coerceIn(margin, maxX), preferredY.coerceIn(margin, maxY))
    }
}
