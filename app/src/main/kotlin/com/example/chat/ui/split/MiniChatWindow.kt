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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
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
    onFocus: () -> Unit = {},
    initialXFraction: Float = 1f,
    initialYFraction: Float = 0.15f,
    initialWidthDp: Float = 320f,
    initialHeightDp: Float = 460f,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val focusCurrent by rememberUpdatedState(onFocus)
    val paneKey = paneItem.stateKey()

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val windowWidthPx = with(density) { maxWidth.toPx() }
        val windowHeightPx = with(density) { maxHeight.toPx() }

        var requestedWidth by rememberSaveable(paneKey) { mutableFloatStateOf(initialWidthDp) }
        var requestedHeight by rememberSaveable(paneKey) { mutableFloatStateOf(initialHeightDp) }
        val maxMiniWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
        val maxMiniHeight = (maxHeight - 24.dp).coerceAtLeast(1.dp)
        val minMiniWidth = 280.dp.coerceAtMost(maxMiniWidth)
        val minMiniHeight = 320.dp.coerceAtMost(maxMiniHeight)
        val miniWidthDp = requestedWidth.dp.coerceIn(minMiniWidth, maxMiniWidth)
        val miniHeightDp = requestedHeight.dp.coerceIn(minMiniHeight, maxMiniHeight)
        val miniWidthPx = with(density) { miniWidthDp.toPx() }
        val miniHeightPx = with(density) { miniHeightDp.toPx() }
        val pillWidthDp = 180.dp.coerceAtMost(maxMiniWidth)
        val pillHeightDp = 48.dp.coerceAtMost(maxMiniHeight)

        var offsetX by rememberSaveable(paneKey) {
            mutableFloatStateOf((windowWidthPx - miniWidthPx).coerceAtLeast(0f) * initialXFraction.coerceIn(0f, 1f))
        }
        var offsetY by rememberSaveable(paneKey) {
            mutableFloatStateOf((windowHeightPx - miniHeightPx).coerceAtLeast(0f) * initialYFraction.coerceIn(0f, 1f))
        }

        fun moveWindow(amount: Offset) {
            val next = SplitChatLayoutPolicy.clampOffset(
                currentX = offsetX + amount.x,
                currentY = offsetY + amount.y,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                miniWidthPx = if (isMinimized) with(density) { pillWidthDp.toPx() } else miniWidthPx,
                miniHeightPx = if (isMinimized) with(density) { pillHeightDp.toPx() } else miniHeightPx,
                marginPx = with(density) { 12.dp.toPx() },
            )
            offsetX = next.first
            offsetY = next.second
        }

        // Clamping inside box boundaries
        val clampedOffset = SplitChatLayoutPolicy.clampOffset(
            currentX = offsetX,
            currentY = offsetY,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            miniWidthPx = if (isMinimized) with(density) { pillWidthDp.toPx() } else miniWidthPx,
            miniHeightPx = if (isMinimized) with(density) { pillHeightDp.toPx() } else miniHeightPx,
            marginPx = with(density) { 12.dp.toPx() },
        )

        val windowShape = RoundedCornerShape(16.dp)

        if (isMinimized) {
            // Minimized Floating Pill
            Row(
                Modifier
                    .offset { IntOffset(clampedOffset.first.roundToInt(), clampedOffset.second.roundToInt()) }
                    .pointerInput(paneKey, windowWidthPx, windowHeightPx, miniWidthPx, miniHeightPx, isMinimized) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            moveWindow(dragAmount)
                        }
                    }
                    .size(pillWidthDp, pillHeightDp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .clickable { onToggleMinimize() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatyIcon(ChatyIconId.chats, color = colors.accent, semanticLabel = null)
                Text(
                    paneItem.title,
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
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
                        .clickable { focusCurrent() }
                        .pointerInput(paneKey, windowWidthPx, windowHeightPx, miniWidthPx, miniHeightPx, isMinimized) {
                            detectDragGestures(onDragStart = { focusCurrent() }) { change, dragAmount ->
                                change.consume()
                                moveWindow(dragAmount)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatyIcon(ChatyIconId.chats, color = colors.accent, semanticLabel = null)
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
                    IconButton(onClick = onToggleMinimize, modifier = Modifier.size(48.dp)) {
                        ChatyIcon(ChatyIconId.chevronDown, color = colors.foregroundSecondary, semanticLabel = "Minimize")
                    }
                    IconButton(onClick = onExpandToFull, modifier = Modifier.size(48.dp)) {
                        ChatyIcon(ChatyIconId.forward, color = colors.foregroundSecondary, semanticLabel = "Expand")
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close")
                    }
                }

                // Window Pane Content
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                // Dedicated resize lane, never covering the message Send/Mic button.
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier.size(48.dp)
                            .semantics {
                                contentDescription = "Resize chat window"
                                customActions = listOf(
                                    CustomAccessibilityAction("Make wider") { requestedWidth = (requestedWidth + 32f).coerceIn(minMiniWidth.value, maxMiniWidth.value); true },
                                    CustomAccessibilityAction("Make narrower") { requestedWidth = (requestedWidth - 32f).coerceIn(minMiniWidth.value, maxMiniWidth.value); true },
                                    CustomAccessibilityAction("Make taller") { requestedHeight = (requestedHeight + 32f).coerceIn(minMiniHeight.value, maxMiniHeight.value); true },
                                    CustomAccessibilityAction("Make shorter") { requestedHeight = (requestedHeight - 32f).coerceIn(minMiniHeight.value, maxMiniHeight.value); true },
                                )
                            }
                            .background(colors.surface.copy(alpha = 0.9f), RoundedCornerShape(topStart = 12.dp))
                            .pointerInput(density, maxMiniWidth, maxMiniHeight) {
                                detectDragGestures { change, amount ->
                                    change.consume()
                                    requestedWidth = (requestedWidth.coerceIn(minMiniWidth.value, maxMiniWidth.value) + with(density) { amount.x.toDp().value })
                                        .coerceIn(minMiniWidth.value, maxMiniWidth.value)
                                    requestedHeight = (requestedHeight.coerceIn(minMiniHeight.value, maxMiniHeight.value) + with(density) { amount.y.toDp().value })
                                        .coerceIn(minMiniHeight.value, maxMiniHeight.value)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("◢", color = colors.accent, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

