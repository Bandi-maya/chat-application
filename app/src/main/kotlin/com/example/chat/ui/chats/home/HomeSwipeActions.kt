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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.draw.clipToBounds
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Job
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

/** Chat-row-sized reveal. The foreground alone measures height; the action
 * layer matches it without claiming the LazyColumn's unbounded height.
 * Physical left/right gestures work independently of text layout direction. */
@Composable
fun HomeSwipeActions(
    actions: List<HomeSwipeAction>,
    modifier: Modifier = Modifier,
    leadingActions: List<HomeSwipeAction> = emptyList(),
    actionExtent: Float = 74f,
    backgroundColor: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val animation = remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }
    val snap = remember { Animatable(0f) }
    val easeOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

    fun settle(target: Float) {
        animation.value?.cancel()
        animation.value = scope.launch {
            snap.snapTo(offset.floatValue)
            snap.animateTo(target, tween(durationMillis = 220, easing = easeOutCubic)) {
                offset.floatValue = value
            }
        }
    }

    // Selection mode must not keep a stale tray or a pointer-blocking overlay.
    // Mask its offset synchronously; cancel/reset the animation in this effect.
    LaunchedEffect(enabled) {
        if (!enabled) {
            animation.value?.cancel()
            offset.floatValue = 0f
            snap.snapTo(0f)
        }
    }

    BackHandler(enabled = enabled && abs(offset.floatValue) > 4f) { settle(0f) }

    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        // Never let an action tray remove the entire foreground on a narrow
        // window. Size cells together so the last action remains reachable.
        val trailingExtent = homeSwipeExtent(maxWidth.value, actions.size, actionExtent)
        val leadingExtent = homeSwipeExtent(maxWidth.value, leadingActions.size, actionExtent)
        val maxTrailingPx = with(density) { (trailingExtent * actions.size).dp.toPx() }
        val maxLeadingPx = with(density) { (leadingExtent * leadingActions.size).dp.toPx() }
        val current = if (enabled) offset.floatValue.coerceIn(-maxLeadingPx, maxTrailingPx) else 0f
        val dragState = rememberDraggableState { delta ->
            if (enabled) {
                animation.value?.cancel()
                offset.floatValue = (offset.floatValue - delta).coerceIn(-maxLeadingPx, maxTrailingPx)
            }
        }
        Box(Modifier.fillMaxWidth().draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            enabled = enabled,
            onDragStarted = { animation.value?.cancel(); offset.floatValue = current },
            onDragStopped = { velocity ->
                if (enabled) settle(homeSwipeSettleTarget(
                    offset = offset.floatValue, velocity = -velocity,
                    leading = maxLeadingPx, trailing = maxTrailingPx,
                    flingThreshold = with(density) { 420.dp.toPx() },
                    minimumFlingDistance = with(density) { 12.dp.toPx() },
                ))
            },
        )) {
            // matchParentSize is deliberate: unlike fillMaxSize this child
            // cannot determine the item height before the chat row is measured.
            if (abs(current) > 0.5f) Box(Modifier.matchParentSize()) {
                val leading = current < 0f
                Row(Modifier.fillMaxHeight().align(if (leading) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)) {
                    (if (leading) leadingActions else actions).forEach { action ->
                        SwipeActionCell(
                            action = action,
                            backgroundColor = backgroundColor,
                            extentDp = if (leading) leadingExtent else trailingExtent,
                            maxDragPx = if (leading) maxLeadingPx else maxTrailingPx,
                            dragOffset = current,
                            revealed = abs(current) > 6f,
                            onTrigger = { settle(0f); action.onTriggered() },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth()
                .absoluteOffset { IntOffset((-current).roundToInt(), 0) }
                .background(backgroundColor)) {
                // Keep the content at the same composition position throughout
                // a drag (avatars, remembered state, selection, custom styles).
                content()
                if (abs(current) > 4f) Box(Modifier.matchParentSize().clickable { settle(0f) })
            }
        }
    }
}

@Composable
private fun SwipeActionCell(
    action: HomeSwipeAction,
    backgroundColor: Color,
    extentDp: Float,
    maxDragPx: Float,
    dragOffset: Float,
    revealed: Boolean,
    onTrigger: () -> Unit,
) {
    // Resolve translucent action colors against the row backdrop before
    // choosing the foreground. Keep the stored action/background colors intact.
    val effectiveBackground = action.color.compositeOver(backgroundColor)
    val foreground = if (homeSwipeUsesDarkForeground(
        effectiveBackground.red, effectiveBackground.green, effectiveBackground.blue,
    )) Color.Black else Color.White
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
        Icon(action.icon, contentDescription = action.label, tint = foreground, modifier = Modifier.width(21.dp).height(21.dp))
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                action.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = foreground,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.W600,
            )
        }
    }
}
