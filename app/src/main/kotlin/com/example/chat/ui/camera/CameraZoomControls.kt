package com.example.chat.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Preset zoom pills shown above the arc (1x, 2x, 4x, 6x). */
@Composable
fun CameraZoomPresets(
    current: Float,
    min: Float,
    max: Float,
    enabled: Boolean,
    onZoom: (Float) -> Unit,
) {
    val presets = remember(min, max) {
        (listOf(min, 1f, 2f, 4f, 6f, max).filter { it in min..max }).distinct().sorted()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { ratio ->
            val active = abs(current - ratio) < 0.05f
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Color(0xFFFFD54F) else Color.Transparent,
                        CircleShape,
                    )
                    .clickable(enabled = enabled) {
                        onZoom(ratio)
                    }
                    .semantics {
                        contentDescription = String.format(java.util.Locale.ROOT, "Zoom %.0fx", ratio)
                        selected = active
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (ratio % 1f == 0f) "%.0fx".format(ratio) else "%.1fx".format(ratio),
                    color = if (active) Color.Black else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Live zoom value pill shown above the arc center. */
@Composable
fun CameraZoomValue(current: Float) {
    Text(
        if (current % 1f == 0f) "%.0fx".format(current) else "%.1fx".format(current),
        color = Color(0xFFFFD54F),
        fontSize = 13.sp,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
    )
}

/**
 * OEM-style circular zoom dial. The arc spans the zoom range log-mapped like real
 * cameras. A fixed top marker reads the live value; horizontal drags scroll the arc
 * to zoom continuously. Values 1x / 2x / 4x / 6x are always displayed around the arc.
 */
@Composable
fun CircularZoomDial(
    current: Float,
    min: Float,
    max: Float,
    enabled: Boolean,
    onZoomLive: (Float) -> Unit,
) {
    val rangeFraction: (Float) -> Float = { z ->
        ln(z / min) / ln(max / min)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6141414))
            .semantics {
                contentDescription = "Circular zoom dial, drag horizontally to zoom"
                role = Role.Button
            }
            .pointerInput(min, max, enabled) {
                if (!enabled) return@pointerInput
                var value = current
                detectHorizontalDragGestures(
                    onDragStart = { value = current },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val widthPx = size.width.coerceAtLeast(1)
                        val f0 = rangeFraction(value)
                        val f1 = (f0 + dragAmount / widthPx * 0.5f).coerceIn(0f, 1f)
                        val next = min * exp(f1 * ln(max / min))
                        if (abs(next - value) > 0.005f) {
                            value = next
                            onZoomLive(next)
                        }
                    },
                )
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h * 2.4f)
            val radius = h * 2.1f
            val fCurrent = rangeFraction(current)

            fun pointFor(f: Float, r: Float): Offset {
                val angleDeg = 270.0 + (f - fCurrent) * 140.0
                val rad = Math.toRadians(angleDeg)
                return Offset(
                    center.x + (r * cos(rad)).toFloat(),
                    center.y + (r * sin(rad)).toFloat(),
                )
            }

            var step = 0.5f
            if (max - min > 6f) step = 1f
            var zTick = min
            while (zTick <= max + 0.001f) {
                val f = rangeFraction(zTick)
                if (abs(f - fCurrent) < 0.38f) {
                    val major = (listOf(min, 1f, 2f, 4f, 6f, max).any { abs(zTick - it) < 0.05f })
                    val a = pointFor(f, radius)
                    val b = pointFor(f, radius - if (major) 16f else 9f)
                    drawLine(
                        color = if (major) Color.White else Color.White.copy(alpha = 0.55f),
                        start = a,
                        end = b,
                        strokeWidth = if (major) 3f else 2f,
                    )
                }
                zTick += step
            }
            // Fixed top-center marker showing the live value position.
            val tipY = 14f
            val path = android.graphics.Path().apply {
                moveTo(w / 2f - 8f, tipY)
                lineTo(w / 2f + 8f, tipY)
                lineTo(w / 2f, tipY + 12f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

/** Clamp a zoom ratio into [min, max]. */
fun clampCameraZoom(current: Float, min: Float, max: Float): Float {
    return current.coerceIn(min, max)
}
