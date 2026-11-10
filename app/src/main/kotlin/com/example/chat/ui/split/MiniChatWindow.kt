package com.example.chat.ui.split

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.theme.LocalAppColors
import kotlin.math.roundToInt

/**
 * Movable, resizable, boundary-clamped floating Mini Chat Window.
 * When minimized, collapses into a compact floating pill.
 */
@Composable
fun MiniChatWindow(
    paneItem: SplitPaneItem,
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    onExpandToFull: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidthPx = with(density) { maxWidth.toPx() }
        val windowHeightPx = with(density) { maxHeight.toPx() }

        val miniWidthDp = 320.dp.coerceAtMost(maxWidth - 32.dp)
        val miniHeightDp = 460.dp.coerceAtMost(maxHeight - 64.dp)
        val miniWidthPx = with(density) { miniWidthDp.toPx() }
        val miniHeightPx = with(density) { miniHeightDp.toPx() }

        var offsetX by remember { mutableFloatStateOf(windowWidthPx - miniWidthPx - with(density) { 16.dp.toPx() }) }
        var offsetY by remember { mutableFloatStateOf(with(density) { 64.dp.toPx() }) }

        // Clamping inside box boundaries
        val clampedOffset = SplitChatLayoutPolicy.clampOffset(
            currentX = offsetX,
            currentY = offsetY,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            miniWidthPx = if (isMinimized) with(density) { 180.dp.toPx() } else miniWidthPx,
            miniHeightPx = if (isMinimized) with(density) { 48.dp.toPx() } else miniHeightPx,
            marginPx = with(density) { 12.dp.toPx() },
        )

        val windowShape = RoundedCornerShape(16.dp)

        if (isMinimized) {
            // Minimized Floating Pill
            Row(
                Modifier
                    .offset { IntOffset(clampedOffset.first.roundToInt(), clampedOffset.second.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .clickable { onToggleMinimize() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatyIcon(ChatyIconId.chat, color = colors.accent, semanticLabel = null)
                Text(
                    paneItem.title,
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close mini chat")
                }
            }
        } else {
            // Expanded Floating Mini Chat Window
            Column(
                Modifier
                    .offset { IntOffset(clampedOffset.first.roundToInt(), clampedOffset.second.roundToInt()) }
                    .size(width = miniWidthDp, height = miniHeightDp)
                    .shadow(16.dp, windowShape)
                    .clip(windowShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, windowShape),
            ) {
                // Drag Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatyIcon(ChatyIconId.chat, color = colors.accent, semanticLabel = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            paneItem.title,
                            color = colors.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (paneItem.accountNamespace != null) {
                            Text(
                                "Account: ${paneItem.accountNamespace.take(8)}",
                                color = colors.foregroundSecondary,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    IconButton(onClick = onToggleMinimize, modifier = Modifier.size(28.dp)) {
                        ChatyIcon(ChatyIconId.chevronDown, color = colors.foregroundSecondary, semanticLabel = "Minimize")
                    }
                    IconButton(onClick = onExpandToFull, modifier = Modifier.size(28.dp)) {
                        ChatyIcon(ChatyIconId.forward, color = colors.foregroundSecondary, semanticLabel = "Expand")
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close")
                    }
                }

                // Window Pane Content
                Box(Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

