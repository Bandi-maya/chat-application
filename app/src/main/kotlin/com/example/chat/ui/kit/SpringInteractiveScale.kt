package com.example.chat.ui.kit

import androidx.compose.ui.composed
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Flutter Curves.elasticOut (period 0.4). */
val ElasticOutEasing = Easing { t ->
    if (t <= 0f) 0f else if (t >= 1f) 1f else {
        val s = 0.4f / 4.0f
        (2.0.pow(-10.0 * t) * sin((t - s) * (PI * 2.0) / 0.4) + 1.0).toFloat()
    }
}
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/** Pointer feedback only: nested clickables keep ownership of action semantics.
 * Physical-time spring is interruptible; no per-frame movement or refresh-rate forcing.
 */
fun Modifier.springInteractiveScale(
    scaleDownFactor: Float = 0.95f,
    enableHaptic: Boolean = true,
): Modifier = composed {
    val view = androidx.compose.ui.platform.LocalView.current
    val reduceMotion = rememberChatyReduceMotion()
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val pressedScale = scaleDownFactor.coerceIn(0.8f, 1f)
    androidx.compose.runtime.LaunchedEffect(reduceMotion) {
        if (reduceMotion) scale.snapTo(1f)
    }
    this
        .pointerInput(reduceMotion, pressedScale, enableHaptic, view) {
            var animation: kotlinx.coroutines.Job? = null
            try {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (enableHaptic) ChatyHapticsPort.selection(view)
                    animation?.cancel()
                    if (!reduceMotion) animation = scope.launch {
                        scale.animateTo(pressedScale, spring(dampingRatio = 0.82f, stiffness = 380f))
                    }
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    animation?.cancel()
                    if (!reduceMotion) animation = scope.launch {
                        scale.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 380f))
                    }
                }
            } finally {
                animation?.cancel()
                scope.launch { scale.snapTo(1f) }
            }
        }
        .scale(if (reduceMotion) 1f else scale.value)
}
