package com.example.chat.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.ChatyHapticsPort
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Exact port of lib/features/island/widgets/dynamic_island_overlay_host.dart.
 *
 *  Spring-morphing floating island host matching the Framer Motion reference
 *  (nithinpjohn/Dynamic-island-notifications):
 *  SPRING = { stiffness: 380, damping: 36, mass: 0.9 } -> Compose
 *  spring(stiffness = 380, dampingRatio = 36 / (2*sqrt(380*0.9)) = 0.97333),
 *  the identical second-order ODE.
 *
 *  Progress t: 0 idle, 1 compact, 2 split, 3 expanded; width/radius/opacity
 *  lerp segments are ported 1:1 into [islandDimensions] (JVM-tested).
 *
 *  Seams:
 *  - BackdropFilter(ImageFilter.blur 20) -> Modifier.blur(20.dp) on the
 *    capsule background (API 31+ real blur; below, the 0xF2 black capsule
 *    still renders - same degradation class as the camera preview filter).
 *  - MediaQuery cutout/viewPadding -> [CutoutAnchorInput] is supplied by the
 *    caller (device-side adapter); [CutoutAnchorResolver] does the math.
 *  - Velocity thresholds: Flutter logical px/s -> Compose px/ms (1000x). */

/** Dart SpringDescription(mass 0.9, stiffness 380, damping 36) as a Compose
 *  damping ratio: damping / (2 * sqrt(stiffness * mass)). */
val ISLAND_SPRING_DAMPING_RATIO: Float = (36.0 / (2.0 * sqrt(380.0 * 0.9))).toFloat()
val ISLAND_SPRING_STIFFNESS: Float = 380f

fun islandModeToTarget(mode: IslandMode): Float = when (mode) {
    IslandMode.idle -> 0f
    IslandMode.compact -> 1f
    IslandMode.split -> 2f
    IslandMode.expanded -> 3f
}

data class IslandDimensions(
    val width: Double,
    val radius: Double,
    val opacity: Double,
    val targetContentWidth: Double,
    val visible: Boolean,
)

/** The width/radius/opacity lerp segments from build(), pure for tests. */
fun islandDimensions(
    t: Double,
    mode: IslandMode,
    isPlayer: Boolean,
    isCall: Boolean,
    screenWidth: Double,
): IslandDimensions {
    val expandedWidth = if (isPlayer) 340.0 else 356.0
    val compactWidth = if (isPlayer) 92.0 else (if (isCall) 96.0 else 84.0)
    val clamped = t.coerceIn(0.0, 3.0)

    var width: Double
    var radius: Double
    var opacity: Double

    fun lerp(a: Double, b: Double, u: Double) = a + (b - a) * u

    if (clamped <= 1.0) {
        // idle -> compact
        width = lerp(32.0, compactWidth, clamped)
        radius = lerp(16.0, 999.0, clamped)
        opacity = lerp(0.0, 1.0, clamped)
    } else if (clamped <= 2.0) {
        // compact -> split
        val splitT = clamped - 1.0
        width = lerp(compactWidth, 104.0, splitT)
        radius = 999.0
        opacity = 1.0
    } else {
        // split / compact -> expanded
        val expandT = (clamped - 2.0).coerceIn(0.0, 1.0)
        val startW = if (mode == IslandMode.split) 104.0 else compactWidth
        width = lerp(startW, expandedWidth, expandT)
        radius = lerp(999.0, 28.0, expandT)
        opacity = 1.0
    }

    width = min(width, screenWidth - 24.0)

    val targetContentWidth = when (mode) {
        IslandMode.expanded -> expandedWidth
        IslandMode.idle -> 32.0
        IslandMode.split -> 104.0
        IslandMode.compact -> compactWidth
    }

    val visible = !(mode == IslandMode.idle && t <= 0.01)

    return IslandDimensions(width, radius, opacity, targetContentWidth, visible)
}

@Composable
fun DynamicIslandOverlayHost(
    controller: DynamicIslandController,
    anchorInput: CutoutAnchorInput,
    screenHeight: Double,
    enableBlur: Boolean = true,
    isHighContrast: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val view = LocalView.current
    val springT = remember { Animatable(islandModeToTarget(controller.mode)) }

    // Controller listener: mode changes animate to the new target with the
    // spring; any other change just recomposes.
    var controllerTick by remember { mutableIntStateOf(0) }
    var lastMode by remember { mutableStateOf(controller.mode) }
    DisposableEffect(controller) {
        val listener: () -> Unit = {
            controllerTick++
            val newMode = controller.mode
            if (newMode != lastMode) {
                lastMode = newMode
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    LaunchedEffect(lastMode) {
        springT.animateTo(
            islandModeToTarget(lastMode),
            spring(dampingRatio = ISLAND_SPRING_DAMPING_RATIO, stiffness = ISLAND_SPRING_STIFFNESS),
        )
    }

    @Suppress("UNUSED_EXPRESSION") controllerTick
    val mode = controller.mode
    val activePayload = controller.activePayload
    val secondaryPayload = controller.secondaryPayload

    val isPlayer = activePayload?.type == IslandPayloadType.media ||
        activePayload?.type == IslandPayloadType.voiceNotePlayback
    val isCall = activePayload?.type == IslandPayloadType.incomingCall ||
        activePayload?.type == IslandPayloadType.ongoingCall

    val anchor = CutoutAnchorResolver.resolve(anchorInput)
    val dims = islandDimensions(
        t = springT.value.toDouble(),
        mode = mode,
        isPlayer = isPlayer,
        isCall = isCall,
        screenWidth = anchorInput.screenWidth,
    )

    Box(Modifier.fillMaxSize()) {
        content()

        if (dims.visible) {
            // Full-screen outside-tap dismiss barrier when expanded.
            if (mode == IslandMode.expanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                        ) { controller.collapse() },
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = anchor.topOffset.dp)
                    .height(max(100.0, screenHeight - anchor.topOffset).dp),
                contentAlignment = when (anchor.alignment) {
                    CutoutAlignment.topLeft -> Alignment.TopStart
                    CutoutAlignment.topCenter -> Alignment.TopCenter
                    CutoutAlignment.topRight -> Alignment.TopEnd
                },
            ) {
                Row(
                    modifier = Modifier.graphicsLayer { alpha = dims.opacity.coerceIn(0.0, 1.0).toFloat() },
                ) {
                    // Primary capsule.
                    Box(
                        modifier = Modifier
                            .islandCapsuleGestures(controller, mode, view)
                            .clip(RoundedCornerShape(dims.radius.dp))
                            .then(if (enableBlur) Modifier.blurHostBackdrop() else Modifier)
                            .background(Color(0xF2000000))
                            .border(
                                width = if (isHighContrast || controller.isDragTargetActive) 2.dp else 1.dp,
                                color = when {
                                    isHighContrast -> Color.White
                                    controller.isDragTargetActive -> Color(0xFF00F0FF)
                                    else -> Color.White.copy(alpha = 0.08f)
                                },
                                shape = RoundedCornerShape(dims.radius.dp),
                            )
                            .width(dims.width.dp)
                            .heightIn(min = 36.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            Modifier
                                .clipToBounds()
                                .width(dims.targetContentWidth.dp),
                        ) {
                            // Dart keys each view: collapsed-view /
                            // expanded-<kind>-<payload.id> / notifications-view.
                            val contentKey = when {
                                mode == IslandMode.compact || mode == IslandMode.split -> "collapsed-view"
                                mode == IslandMode.expanded && controller.activeSubView == "notifications" ->
                                    "expanded-notifications-view"
                                mode == IslandMode.expanded && activePayload != null ->
                                    "expanded-${activePayload.type}-${activePayload.id}"
                                else -> "idle-dot"
                            }
                            BlurFadeSwitcher(
                                targetState = contentKey,
                                enableBlur = enableBlur,
                            ) {
                                IslandContent(mode, activePayload, controller)
                            }
                        }
                    }

                    // Secondary bubble in split mode.
                    if (mode == IslandMode.split && secondaryPayload != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .then(if (enableBlur) Modifier.blurHostBackdrop() else Modifier)
                                .background(Color(0xF2000000))
                                .border(
                                    width = if (isHighContrast) 2.dp else 1.dp,
                                    color = if (isHighContrast) Color.White
                                    else Color.White.copy(alpha = 0.08f),
                                    shape = CircleShape,
                                )
                                .clickable {
                                    ChatyHapticsPort.light(view)
                                    controller.expand(subView = "notifications")
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = secondaryPayload.iconData
                                    ?: androidx.compose.material.icons.Icons.Rounded.Notifications,
                                contentDescription = "Secondary activity: ${secondaryPayload.title}",
                                tint = secondaryPayload.accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Capsule gesture map:
 *  tap: compact/split -> expand; expanded -> collapse.
 *  long press: haptic + expand.
 *  vertical drag end velocity < -100 px/s (=-0.1 px/ms): collapse.
 *  horizontal drag end |v| > 140 px/s (=0.14 px/ms): expanded + queue>1 ->
 *  carousel next/prev (haptic light); otherwise dismissActive. */
private fun Modifier.islandCapsuleGestures(
    controller: DynamicIslandController,
    mode: IslandMode,
    view: android.view.View,
): Modifier {
    return this
        .pointerInput(mode) {
            detectTapGestures(
                onTap = {
                    if (mode == IslandMode.compact || mode == IslandMode.split) {
                        controller.expand()
                    } else if (mode == IslandMode.expanded) {
                        controller.collapse()
                    }
                },
                onLongPress = {
                    ChatyHapticsPort.threshold(view)
                    controller.expand()
                },
            )
        }
        .pointerInput(mode) {
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onVerticalDrag = { change, _ ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                },
                onDragEnd = {
                    val velocityPxPerMs = tracker.calculateVelocity().y
                    if (velocityPxPerMs < -0.1f) { // -100 logical px/s
                        controller.collapse()
                    }
                },
            )
        }
        .pointerInput(mode) {
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onHorizontalDrag = { change, _ ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                },
                onDragEnd = {
                    val v = tracker.calculateVelocity().x
                    if (kotlin.math.abs(v) > 0.14f) { // 140 logical px/s
                        if (mode == IslandMode.expanded && controller.queue.size > 1) {
                            ChatyHapticsPort.light(view)
                            if (v < 0) controller.nextCarouselItem() else controller.prevCarouselItem()
                        } else {
                            ChatyHapticsPort.light(view)
                            controller.dismissActive()
                        }
                    }
                },
            )
        }
}

/** Backdrop blur seam (documented above): the capsule's own blur modifier. */
private fun Modifier.blurHostBackdrop(): Modifier = this.blur(20.dp)

/** _buildIslandContent dispatch, exact to the Dart switch. */
@Composable
private fun IslandContent(
    mode: IslandMode,
    payload: DynamicIslandPayload?,
    controller: DynamicIslandController,
) {
    if (mode == IslandMode.compact || mode == IslandMode.split) {
        CollapsedIslandView(
            payload = payload,
            notifCount = controller.queue.size,
            onTap = { controller.expand() },
        )
        return
    }

    if (mode == IslandMode.expanded) {
        if (controller.activeSubView == "notifications") {
            ExpandedNotificationsListView(
                controller = controller,
                showBack = payload?.isOngoingActivity == true,
            )
            return
        }
        if (payload == null) {
            Box(Modifier.height(42.dp))
            return
        }
        when (payload.type) {
            IslandPayloadType.media, IslandPayloadType.voiceNotePlayback ->
                ExpandedMediaView(payload = payload, controller = controller)
            IslandPayloadType.incomingCall, IslandPayloadType.ongoingCall ->
                ExpandedCallView(payload = payload, controller = controller)
            IslandPayloadType.clipboardIntent ->
                ExpandedClipboardIntentView(payload = payload, controller = controller)
            IslandPayloadType.dragAndGoTarget ->
                ExpandedDragTargetView(payload = payload, controller = controller)
            IslandPayloadType.chatMessage, IslandPayloadType.systemAlert,
            IslandPayloadType.contactOnline, IslandPayloadType.ghostModeStatus,
            ->
                ExpandedChatMessageView(payload = payload, controller = controller)
        }
        return
    }

    // Idle dot fallback.
    Box(Modifier.size(width = 38.dp, height = 32.dp))
}
