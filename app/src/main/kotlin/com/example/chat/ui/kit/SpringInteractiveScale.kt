package com.example.chat.ui.kit

import androidx.compose.ui.composed
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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

/**
 * Port of lib/core/design_system/spring_interactive_scale.dart: on pointer down scales to
 * [scaleDownFactor] over 140ms easeOutCubic (+ selection haptic), on release/cancel reverses with
 * elasticOut. The pointer is only observed (not consumed) so inner clickables still receive taps.
 * UNCOMPILED (Compose).
 */
fun Modifier.springInteractiveScale(
    scaleDownFactor: Float = 0.95f,
    enableHaptic: Boolean = true,
): Modifier = composed {
    val view = androidx.compose.ui.platform.LocalView.current
    val progress = remember { Animatable(0f) } // controller value 0..1
    val scope = rememberCoroutineScope()
    val reversing = remember { androidx.compose.runtime.mutableStateOf(false) }
    val scaleValue = progress.value.let { c ->
        // forward uses easeOutCubic, reverse uses elasticOut over controller value
        1f + (scaleDownFactor - 1f) * (if (reversing.value) ElasticOutEasing.transform(c) else EaseOutCubic.transform(c))
    }
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (enableHaptic) ChatyHapticsPort.selection(view)
                scope.launch { reversing.value = false; progress.animateTo(1f, tween(140, easing = LinearEasing)) }
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                scope.launch { reversing.value = true; progress.animateTo(0f, tween(140, easing = LinearEasing)) }
            }
        }
        .scale(scaleValue)
}
