package com.example.chat.ui.chats.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One revealable swipe action (chaty_kit.dart ChatySwipeAction). */
data class HomeSwipeAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onTriggered: () -> Unit,
)

/** Port of chaty_kit.dart ChatySwipeActions: the row slides away to reveal
 *  actions; swipe left reveals [actions] on the right, swipe right reveals
 *  [leadingActions] on the left. Settles open past half the max extent (or
 *  on a 420dp/s fling past 12dp), snaps with 220ms easeOutCubic, labels fade
 *  in once |offset| exceeds 72% of the max drag, and the row stops taking
 *  taps while |offset| > 4. */
@Composable
fun HomeSwipeActions(
    actions: List<HomeSwipeAction>,
    modifier: Modifier = Modifier,
    leadingActions: List<HomeSwipeAction> = emptyList(),
    actionExtent: Float = 74f,
    backgroundColor: Color,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxTrailingPx = with(density) { (actionExtent * actions.size).dp.toPx() }
    val maxLeadingPx = with(density) { (actionExtent * leadingActions.size).dp.toPx() }
    // Dart _dragOffset: positive reveals trailing actions (right side),
    // negative reveals leading actions (left side).
    val offset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val snap = remember { Animatable(0f) }
    val easeOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

    fun settle(target: Float) {
        scope.launch {
            snap.stop()
            snap.snapTo(offset.floatValue)
            snap.animateTo(target, tween(durationMillis = 220, easing = easeOutCubic)) {
                offset.floatValue = value
            }
        }
    }

    fun trigger(action: HomeSwipeAction) {
        action.onTriggered()
        settle(0f)
    }

    val dragState = rememberDraggableState { delta ->
        snap.stop()
        offset.floatValue = (offset.floatValue - delta).coerceIn(-maxLeadingPx, maxTrailingPx)
    }

    Box(
        modifier = modifier.draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity ->
                val v = -velocity
                val current = offset.floatValue
                if (current < 0) {
                    val openLeading = maxLeadingPx > 0 &&
                        (abs(current) > maxLeadingPx / 2 || (v < -420 && abs(current) > 12))
                    settle(if (openLeading) -maxLeadingPx else 0f)
                } else {
                    val openTrailing = maxTrailingPx > 0 &&
                        (current > maxTrailingPx / 2 || (v > 420 && current > 12))
                    settle(if (openTrailing) maxTrailingPx else 0f)
                }
            },
        ),
    ) {
        // Action layer under the row, on the side the swipe direction opens.
        Row(Modifier.fillMaxSize()) {
            if (offset.floatValue < 0) {
                leadingActions.forEach { action ->
                    SwipeActionCell(
                        action = action,
                        extentDp = actionExtent,
                        maxDragPx = maxLeadingPx,
                        dragOffset = offset.floatValue,
                        revealed = offset.floatValue < -6,
                        onTrigger = { trigger(action) },
                    )
                }
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
                actions.forEach { action ->
                    SwipeActionCell(
                        action = action,
                        extentDp = actionExtent,
                        maxDragPx = maxTrailingPx,
                        dragOffset = offset.floatValue,
                        revealed = offset.floatValue > 6,
                        onTrigger = { trigger(action) },
                    )
                }
            }
        }
        // The row itself slides away. An opaque backdrop prevents the action
        // layer bleeding through on transparent rows.
        Box(
            modifier = Modifier
                .offset { IntOffset((-offset.floatValue).roundToInt(), 0) }
                .background(backgroundColor),
        ) {
            if (abs(offset.floatValue) > 4) {
                // AbsorbPointer equivalent: an invisible overlay eats touches.
                Box { content() }
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(enabled = true, onClick = {}),
                )
            } else {
                content()
            }
        }
    }
}

@Composable
private fun SwipeActionCell(
    action: HomeSwipeAction,
    extentDp: Float,
    maxDragPx: Float,
    dragOffset: Float,
    revealed: Boolean,
    onTrigger: () -> Unit,
) {
    val showLabel = maxDragPx > 0 && abs(dragOffset) > maxDragPx * 0.72f
    Column(
        modifier = Modifier
            .width(extentDp.dp)
            .fillMaxHeight()
            .background(action.color)
            .clickable(enabled = revealed) { onTrigger() }
            .padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(action.icon, contentDescription = action.label, tint = Color.White, modifier = Modifier.width(21.dp).height(21.dp))
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                action.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.W600,
            )
        }
    }
}
