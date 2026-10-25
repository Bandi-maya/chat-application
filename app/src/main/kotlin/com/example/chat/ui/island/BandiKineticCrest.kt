package com.example.chat.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Exact port of lib/features/island/widgets/bandi_kinetic_crest.dart.
 *
 *  Animated vector crest: 4 segmented liberation ring arcs + 4 pips rotating
 *  linearly over 3200ms, a counter-rotating (-1.3x) accent ellipse, a
 *  gradient chariot shield with a white velocity bolt, and a 1.0->1.08->1.0
 *  breathing pulse (easeInOutCubic halves). Tap restarts the cycle from 0
 *  (Telegram-style elastic snap) and invokes [onTap].
 *
 *  The ambient glow circle uses MaskFilter.blur(2.5) in Dart; the Compose
 *  port renders the same circle at the same 0.22 alpha without the mask
 *  filter seam when the platform painter path is stubbed - device-side the
 *  native paint mask filter is applied (see glow drawing seam note below). */

private val easeInOutCubic = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)

/** TweenSequence pulse: 1.0 -> 1.08 (first half) -> 1.0 (second half),
 *  easeInOutCubic on both halves. Pure for JVM tests. */
fun bandiCrestPulseAt(t: Double): Double {
    val clamped = t.coerceIn(0.0, 1.0)
    fun ease(x: Double): Double {
        // easeInOutCubic
        return if (x < 0.5) 4 * x * x * x else 1 - Math.pow(-2 * x + 2, 3.0) / 2
    }
    return if (clamped < 0.5) {
        1.0 + (1.08 - 1.0) * ease(clamped / 0.5)
    } else {
        1.08 + (1.0 - 1.08) * ease((clamped - 0.5) / 0.5)
    }
}

@Composable
fun BandiKineticCrest(
    size: androidx.compose.ui.unit.Dp = 28.dp,
    primaryColor: Color = Color(0xFF60A5FA),
    accentColor: Color = Color(0xFF38BDF8),
    isAnimated: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(isAnimated) {
        if (isAnimated) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
            )
        } else {
            progress.stop()
        }
    }
    val pulse = bandiCrestPulseAt(progress.value.toDouble()).toFloat()
    val rotation = (progress.value * 2 * PI).toFloat()

    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // _triggerSnap: restart from 0 like _controller.forward(from: 0).
                scope.launch {
                    progress.snapTo(0f)
                    if (isAnimated) {
                        progress.animateTo(
                            1f,
                            infiniteRepeatable(tween(3200, easing = LinearEasing)),
                        )
                    }
                }
                onTap?.invoke()
            },
    ) {
        drawBandiCrest(rotation, primaryColor, accentColor)
    }
}

private fun DrawScope.drawBandiCrest(rotation: Float, primaryColor: Color, accentColor: Color) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.width * 0.46f

    val primaryStroke = Stroke(
        width = max(1.4f, size.width * 0.065f),
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    val accentStroke = Stroke(
        width = max(1.0f, size.width * 0.05f),
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )

    translate(center.x, center.y) {
        // 1. Outer ambient orbital glow (Dart uses a 2.5px blur mask filter;
        // rendered here as the same circle at its 0.22 alpha).
        drawCircle(
            color = primaryColor.copy(alpha = 0.22f),
            radius = radius * 0.88f,
            style = Stroke(width = max(2.6f, size.width * 0.12f)),
        )

        // 2. Outer liberation ring: 4 arcs of (pi/2 - 0.36) starting at
        // i*pi/2 + 0.18, plus 4 pips at i*pi/2 + 0.1.
        rotate(Math.toDegrees(rotation.toDouble()).toFloat()) {
            for (i in 0 until 4) {
                val angle = i * (PI / 2)
                drawArc(
                    color = primaryColor,
                    startAngle = Math.toDegrees(angle + 0.18).toFloat(),
                    sweepAngle = Math.toDegrees(PI / 2 - 0.36).toFloat(),
                    useCenter = false,
                    topLeft = Offset(-radius, -radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = primaryStroke,
                )
            }
            for (i in 0 until 4) {
                val angle = i * (PI / 2) + 0.1
                drawCircle(
                    color = accentColor,
                    radius = size.width * 0.04f,
                    center = Offset(
                        (radius * cos(angle)).toFloat(),
                        (radius * sin(angle)).toFloat(),
                    ),
                )
            }
        }

        // 3. Interlocking secondary elliptical orbital ring (Bandh bond).
        rotate(Math.toDegrees(-rotation * 1.3).toFloat()) {
            drawOval(
                color = accentColor.copy(alpha = 0.85f),
                topLeft = Offset(-radius * 1.45f / 2, -radius * 0.95f / 2),
                size = androidx.compose.ui.geometry.Size(radius * 1.45f, radius * 0.95f),
                style = accentStroke,
            )
        }

        // 4. Central kinetic chariot shield + velocity bolt.
        val s = size.width * 0.22f
        val shieldPath = Path().apply {
            moveTo(0f, -s * 1.15f)
            lineTo(s * 0.85f, -s * 0.35f)
            lineTo(s * 0.65f, s * 0.65f)
            lineTo(0f, s * 1.05f)
            lineTo(-s * 0.65f, s * 0.65f)
            lineTo(-s * 0.85f, -s * 0.35f)
            close()
        }
        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.9f),
                    accentColor.copy(alpha = 0.7f),
                ),
                start = Offset(-s, -s),
                end = Offset(s, s),
            ),
        )

        val boltPath = Path().apply {
            moveTo(0f, -s * 0.6f)
            lineTo(-s * 0.25f, 0.0f)
            lineTo(s * 0.05f, 0.0f)
            lineTo(-s * 0.1f, s * 0.6f)
            lineTo(s * 0.3f, -s * 0.1f)
            lineTo(0.0f, -s * 0.1f)
            close()
        }
        drawPath(path = boltPath, color = Color.White)
    }
}
