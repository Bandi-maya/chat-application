package com.example.chat.ui.kit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

/**
 * One globally hosted activity surface. [key] plays the role of Dart `activity.runtimeType` (AnimatedSwitcher child key):
 * a different key cross-fades, the same key just updates in place.
 */
class ChatyActivity(val key: Any, val content: @Composable () -> Unit)

/**
 * Port of ChatyGlobalActivityHost (lib/ui/core/design_system/components/global_activity_host.dart): routed app content
 * as the base, with primary (top), secondary (center) and bottom activity slots stacked above it. Slots animate with
 * fade + a small vertical slide (sign(verticalOffset) * 0.08 of the slot height) over ChatyMotion.base, zero when the
 * platform disables animations; slot content is a polite live region.
 *
 * Differences: Dart IgnorePointer lets touches fall through to the app; [ignoreActivityPointer] here CONSUMES touches
 * in the slot instead (Compose has no public hit-test opt-out). main.dart never sets it (default false).
 */
@Composable
fun ChatyGlobalActivityHost(
    modifier: Modifier = Modifier,
    primaryActivity: ChatyActivity? = null,
    secondaryActivity: ChatyActivity? = null,
    bottomActivity: ChatyActivity? = null,
    ignoreActivityPointer: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Backdrop for BackdropFilter-style blur in activities: only record routed content while an activity exists (API 31+).
    val backdropLayer = rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { ChatyBackdropState(backdropLayer) }
    val record = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (primaryActivity != null || secondaryActivity != null || bottomActivity != null)
    Box(modifier.fillMaxSize().onGloballyPositioned { backdrop.hostOrigin = it.positionInWindow() }) {
        Box(
            Modifier.fillMaxSize().then(
                if (record) Modifier.drawWithContent {
                    backdrop.layer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop.layer)
                } else Modifier,
            ),
        ) { content() }
        CompositionLocalProvider(LocalChatyBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                ActivitySlot(primaryActivity, ignoreActivityPointer, verticalOffset = -12)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ActivitySlot(secondaryActivity, ignoreActivityPointer, verticalOffset = 0)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ActivitySlot(bottomActivity, ignoreActivityPointer, verticalOffset = 12)
            }
        }
    }
}

@Composable
private fun ActivitySlot(activity: ChatyActivity?, ignorePointer: Boolean, verticalOffset: Int) {
    val ctx = LocalContext.current
    val ms = ChatyMotion.durationMs(ctx, ChatyMotion.baseMs)
    val fraction = if (verticalOffset == 0) 0f else Integer.signum(verticalOffset) * 0.08f
    // Keep the last content per key so the outgoing child still renders during its fade-out.
    val cache = androidx.compose.runtime.remember { HashMap<Any, @Composable () -> Unit>() }
    if (activity != null) cache[activity.key] = activity.content
    AnimatedContent(
        targetState = activity?.key,
        transitionSpec = {
            (fadeIn(tween(ms, easing = ChatyMotion.enter)) +
                slideInVertically(tween(ms, easing = ChatyMotion.enter)) { (it * fraction).roundToInt() }) togetherWith
                (fadeOut(tween(ms, easing = ChatyMotion.exit)) +
                    slideOutVertically(tween(ms, easing = ChatyMotion.exit)) { (it * fraction).roundToInt() }) using
                SizeTransform(clip = false)
        },
        label = "chaty-activity-slot",
    ) { k ->
        if (k != null) {
            val body = cache[k]
            if (body != null) {
                Box(
                    Modifier
                        .graphicsLayer() // RepaintBoundary
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .then(
                            if (ignorePointer) Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            } else Modifier,
                        ),
                ) { body() }
            }
        }
    }
}
