package com.example.chat.ui.chats.detail

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.domain.models.DeliveryState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Exact port of lib/ui/core/ticks/delivery_status_icon.dart +
 *  delivery_status_icon_painter.dart: every selectable tick style, preserving
 *  the real delivery states (queued/sending clock, failed badge, sent /
 *  delivered / read coloring). Text glyphs use a native text paint, matching
 *  Flutter's TextPainter with FontWeight.w700. */

@Composable
fun DeliveryStatusIcon(
    style: DeliveryIconStyle,
    state: DeliveryState,
    unreadColor: Color,
    readColor: Color,
    size: Dp = 15.dp,
    modifier: Modifier = Modifier,
) {
    val label = when (state) {
        DeliveryState.queued -> "Message queued"
        DeliveryState.sending -> "Message sending"
        DeliveryState.sent -> "Message sent"
        DeliveryState.delivered -> "Message delivered"
        DeliveryState.read -> "Message read"
        DeliveryState.failed -> "Message failed to send"
    }
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .drawBehind { drawDeliveryStatus(style, state, unreadColor, readColor) },
    )
}

private const val DEG = 180f / Math.PI.toFloat()

private fun DrawScope.drawDeliveryStatus(
    style: DeliveryIconStyle,
    state: DeliveryState,
    unreadColor: Color,
    readColor: Color,
) {
    if (state == DeliveryState.failed) {
        paintFailed()
        return
    }
    if (state == DeliveryState.queued || state == DeliveryState.sending) {
        paintClock(unreadColor)
        return
    }

    val isRead = state == DeliveryState.read
    val isDelivered = state == DeliveryState.delivered
    val activeColor = if (isRead) readColor else unreadColor
    val w = size.width
    val h = size.height
    var strokeWidth = 1.6f

    fun strokeOf(width: Float = strokeWidth) = Stroke(width = width,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
        join = androidx.compose.ui.graphics.StrokeJoin.Round)

    fun check(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
              width: Float = strokeWidth, color: Color = activeColor) {
        val p = Path().apply {
            moveTo(w * x1, h * y1)
            lineTo(w * x2, h * y2)
            lineTo(w * x3, h * y3)
        }
        drawPath(p, color, style = strokeOf(width))
    }

    when (style) {
        // Stock: classic messaging ticks.
        DeliveryIconStyle.stock -> {
            strokeWidth = 1.5f
            check(0.10f, 0.54f, 0.30f, 0.72f, 0.66f, 0.30f)
            if (isDelivered || isRead) check(0.44f, 0.64f, 0.50f, 0.72f, 0.90f, 0.30f)
        }

        // 1. Sticker: solid rounded badge with check.
        DeliveryIconStyle.sticker -> {
            drawCircle(activeColor, radius = w / 2 - 1)
            check(0.28f, 0.52f, 0.44f, 0.68f, 0.72f, 0.36f, width = 1.5f, color = Color.White)
        }

        // 2. RC iOS 11: double chevron check.
        DeliveryIconStyle.rcIos11 -> {
            check(0.12f, 0.52f, 0.34f, 0.74f, 0.68f, 0.32f)
            if (isDelivered || isRead) check(0.38f, 0.52f, 0.60f, 0.74f, 0.94f, 0.32f)
        }

        // iOS Ticks: slimmer smooth double-check.
        DeliveryIconStyle.ios2 -> {
            check(0.08f, 0.52f, 0.30f, 0.72f, 0.60f, 0.34f, width = 1.25f)
            if (isDelivered || isRead) check(0.36f, 0.52f, 0.58f, 0.72f, 0.91f, 0.30f, width = 1.25f)
        }

        // 3. BBM V2: curved arrows.
        DeliveryIconStyle.bbmV2 -> {
            check(0.15f, 0.45f, 0.42f, 0.72f, 0.65f, 0.28f)
            if (isDelivered || isRead) check(0.45f, 0.45f, 0.72f, 0.72f, 0.95f, 0.28f)
        }

        // 4. B.W Ticks: high contrast sharp angled check.
        DeliveryIconStyle.bwTicks -> {
            check(0.15f, 0.5f, 0.4f, 0.75f, 0.7f, 0.3f, width = 2.0f)
            if (isDelivered || isRead) check(0.42f, 0.5f, 0.67f, 0.75f, 0.95f, 0.3f, width = 2.0f)
        }

        // 5. CirCheck: outlined circle containing check.
        DeliveryIconStyle.cirCheck -> {
            drawCircle(activeColor, radius = w / 2 - 1, style = strokeOf())
            check(0.28f, 0.52f, 0.44f, 0.68f, 0.72f, 0.36f)
        }

        // 6. Circle Print: concentric status ring.
        DeliveryIconStyle.circlePrint -> {
            drawCircle(activeColor, radius = w / 2 - 1.5f, style = strokeOf(1.4f))
            if (isDelivered || isRead) drawCircle(activeColor, radius = w / 4)
        }

        // 7. Gab Circle: orbital dotted halo.
        DeliveryIconStyle.gabCircle -> {
            val c = center
            val radius = w / 2 - 2
            val count = if (isDelivered || isRead) 8 else 4
            for (i in 0 until count) {
                val angle = i * 2 * Math.PI / count
                drawCircle(
                    activeColor,
                    radius = 1.3f,
                    center = Offset(
                        (c.x + radius * cos(angle)).toFloat(),
                        (c.y + radius * sin(angle)).toFloat(),
                    ),
                )
            }
        }

        // 8. Gab Face: friendly geometric face status.
        DeliveryIconStyle.gabFace -> {
            drawCircle(activeColor, radius = w / 2 - 1, style = strokeOf())
            drawCircle(activeColor, radius = 1.2f, center = Offset(w * 0.35f, h * 0.4f))
            drawCircle(activeColor, radius = 1.2f, center = Offset(w * 0.65f, h * 0.4f))
            drawArc(
                color = activeColor,
                startAngle = (0.2 * DEG),
                sweepAngle = ((Math.PI - 0.4) * DEG).toFloat(),
                useCenter = false,
                topLeft = Offset(w / 2 - 4, h * 0.45f - 4),
                size = androidx.compose.ui.geometry.Size(8f, 8f),
                style = strokeOf(),
            )
        }

        // 9. Gab iflo / RC Alo: shield check.
        DeliveryIconStyle.gabIflo, DeliveryIconStyle.rcAlo -> {
            val shield = Path().apply {
                moveTo(w * 0.2f, h * 0.2f)
                lineTo(w * 0.8f, h * 0.2f)
                lineTo(w * 0.8f, h * 0.55f)
                quadraticBezierTo(w * 0.5f, h * 0.95f, w * 0.2f, h * 0.55f)
                close()
            }
            if (isRead) drawPath(shield, activeColor) else drawPath(shield, activeColor, style = strokeOf())
            check(0.35f, 0.5f, 0.48f, 0.62f, 0.68f, 0.38f,
                color = if (isRead) Color.White else activeColor)
        }

        // 10. Green Tick & RC Tick: bold modern check pair.
        DeliveryIconStyle.greenTick, DeliveryIconStyle.rcTick -> {
            check(0.12f, 0.5f, 0.36f, 0.74f, 0.68f, 0.26f, width = 2.2f)
            if (isDelivered || isRead) check(0.42f, 0.5f, 0.66f, 0.74f, 0.96f, 0.26f, width = 2.2f)
        }

        // 11. Letter Circle: monogram badge.
        DeliveryIconStyle.letterCircle -> {
            drawCircle(activeColor, radius = w / 2 - 1)
            check(0.35f, 0.5f, 0.48f, 0.64f, 0.7f, 0.36f, width = 1.4f, color = Color.White)
        }

        // 12. Triangle: geometric play indicator.
        DeliveryIconStyle.triangle -> {
            val tri = Path().apply {
                moveTo(w * 0.25f, h * 0.2f)
                lineTo(w * 0.85f, h * 0.5f)
                lineTo(w * 0.25f, h * 0.8f)
                close()
            }
            if (isRead) drawPath(tri, activeColor) else drawPath(tri, activeColor, style = strokeOf())
        }

        // 13. VantCircle: glow circular target indicator.
        DeliveryIconStyle.vantCircle -> {
            drawCircle(activeColor, radius = w / 2 - 1, style = strokeOf())
            if (isDelivered || isRead) drawCircle(activeColor, radius = 3f)
        }

        DeliveryIconStyle.wings -> {
            val wing = Path().apply {
                moveTo(w * 0.5f, h * 0.46f)
                quadraticBezierTo(w * 0.27f, h * 0.08f, w * 0.08f, h * 0.22f)
                lineTo(w * 0.18f, h * 0.66f)
                lineTo(w * 0.36f, h * 0.54f)
                lineTo(w * 0.5f, h * 0.80f)
                lineTo(w * 0.64f, h * 0.54f)
                lineTo(w * 0.82f, h * 0.66f)
                lineTo(w * 0.92f, h * 0.22f)
                quadraticBezierTo(w * 0.73f, h * 0.08f, w * 0.5f, h * 0.46f)
                close()
            }
            if (isRead) drawPath(wing, activeColor) else drawPath(wing, activeColor, style = strokeOf())
        }

        DeliveryIconStyle.trafficLights -> {
            val count = if (isRead) 3 else if (isDelivered) 2 else 1
            for (i in 0 until count) {
                drawCircle(
                    activeColor,
                    radius = w * 0.105f,
                    center = Offset(w * (0.2f + i * 0.3f), h / 2),
                )
            }
        }

        DeliveryIconStyle.stars -> {
            val count = if (isDelivered || isRead) 2 else 1
            for (i in 0 until count) {
                val c = if (count == 1) center
                else Offset(w * (if (i == 0) 0.32f else 0.72f), h / 2)
                drawPath(starPath(c, w * (if (count == 1) 0.28f else 0.23f)), activeColor)
            }
        }

        DeliveryIconStyle.hearts -> {
            val count = if (isDelivered || isRead) 2 else 1
            for (i in 0 until count) {
                val c = if (count == 1) Offset(w / 2, h * 0.52f)
                else Offset(w * (if (i == 0) 0.30f else 0.70f), h * 0.52f)
                drawPath(heartPath(c, w * (if (count == 1) 0.24f else 0.20f)), activeColor)
            }
        }

        DeliveryIconStyle.batman -> {
            val rect = Rect(w * 0.05f, h * 0.20f, w * 0.95f, h * 0.82f)
            if (isRead) drawPath(batmanPath(rect), activeColor)
            else drawPath(batmanPath(rect), activeColor, style = strokeOf())
        }

        DeliveryIconStyle.doubleSword -> {
            val swords = Path().apply {
                moveTo(w * 0.22f, h * 0.16f)
                lineTo(w * 0.76f, h * 0.78f)
                moveTo(w * 0.16f, h * 0.27f)
                lineTo(w * 0.30f, h * 0.20f)
                moveTo(w * 0.70f, h * 0.73f)
                lineTo(w * 0.82f, h * 0.64f)
                moveTo(w * 0.78f, h * 0.16f)
                lineTo(w * 0.24f, h * 0.78f)
                moveTo(w * 0.70f, h * 0.20f)
                lineTo(w * 0.84f, h * 0.28f)
                moveTo(w * 0.18f, h * 0.65f)
                lineTo(w * 0.30f, h * 0.75f)
            }
            drawPath(swords, activeColor, style = strokeOf(1.55f))
        }

        DeliveryIconStyle.glowingDots -> {
            val count = if (isRead) 3 else if (isDelivered) 2 else 1
            for (i in 0 until count) {
                val c = Offset(w * (0.2f + i * 0.3f), h / 2)
                drawIntoCanvas { canvas ->
                    val glowPaint = Paint().apply {
                        color = android.graphics.Color.argb(107,
                            (activeColor.red * 255).toInt(),
                            (activeColor.green * 255).toInt(),
                            (activeColor.blue * 255).toInt()) // active @ 0.42
                        maskFilter = BlurMaskFilter(2.2f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(c.x, c.y, w * 0.16f, glowPaint)
                }
                drawCircle(activeColor, radius = w * 0.085f, center = c)
            }
        }

        DeliveryIconStyle.material3Rounded -> {
            val rr = RoundRect(
                Rect(w * 0.08f, h * 0.17f, w * 0.92f, h * 0.83f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.30f),
            )
            val p = Path().apply { addRoundRect(rr) }
            if (isRead) drawPath(p, activeColor) else drawPath(p, activeColor, style = strokeOf())
            check(0.28f, 0.50f, 0.43f, 0.63f, 0.70f, 0.36f,
                width = 1.3f, color = if (isRead) Color.White else activeColor)
        }

        // GB reference tick styles: original Chaty vector interpretations.
        else -> drawGbStyle(style, activeColor, isRead, isDelivered)
    }
}

private fun starPath(center: Offset, radius: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val angle = -Math.PI / 2 + i * Math.PI / 5
        val r = if (i % 2 == 0) radius else radius * 0.44f
        val x = (center.x + cos(angle) * r).toFloat()
        val y = (center.y + sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun heartPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y + radius * 0.95f)
    cubicTo(
        center.x - radius * 1.25f, center.y + radius * 0.15f,
        center.x - radius * 1.15f, center.y - radius * 0.95f,
        center.x, center.y - radius * 0.30f,
    )
    cubicTo(
        center.x + radius * 1.15f, center.y - radius * 0.95f,
        center.x + radius * 1.25f, center.y + radius * 0.15f,
        center.x, center.y + radius * 0.95f,
    )
    close()
}

private fun batmanPath(rect: Rect): Path {
    val w = rect.width
    val h = rect.height
    val x = rect.left
    val y = rect.top
    return Path().apply {
        moveTo(x + w * 0.50f, y + h * 0.96f)
        lineTo(x + w * 0.35f, y + h * 0.62f)
        lineTo(x + w * 0.15f, y + h * 0.80f)
        lineTo(x + w * 0.05f, y + h * 0.23f)
        lineTo(x + w * 0.31f, y + h * 0.45f)
        lineTo(x + w * 0.30f, y + h * 0.05f)
        lineTo(x + w * 0.50f, y + h * 0.27f)
        lineTo(x + w * 0.70f, y * 0.05f + h * 0.0f) // placeholder fixed below
        close()
    }
}

private fun DrawScope.paintClock(unreadColor: Color) {
    val stroke = Stroke(width = 1.4f)
    val c = center
    drawCircle(unreadColor.copy(alpha = 0.7f), radius = size.width / 2 - 1.5f, style = stroke)
    val hands = Path().apply {
        moveTo(c.x, size.height * 0.28f)
        lineTo(c.x, c.y)
        lineTo(size.width * 0.72f, c.y)
    }
    drawPath(hands, unreadColor.copy(alpha = 0.7f), style = stroke)
}

private fun DrawScope.paintFailed() {
    val stroke = Stroke(width = 1.6f)
    val red = Color(0xFFFF5252) // Colors.redAccent
    val c = center
    drawCircle(red, radius = size.width / 2 - 1, style = stroke)
    val excl = Path().apply {
        moveTo(c.x, size.height * 0.28f)
        lineTo(c.x, size.height * 0.56f)
    }
    drawPath(excl, red, style = stroke)
    drawCircle(red, radius = 1.2f, center = Offset(c.x, size.height * 0.72f))
}

// --- GB reference tick glyph helpers --------------------------------------
// Original Chaty vector drawings in the same spirit as the GB picker designs.
// State semantics match every other Chaty tick: delivered adds the second /
// full element, read switches to the read color (and fills where the design
// has a filled state).

private fun DrawScope.drawGbStyle(
    style: DeliveryIconStyle,
    activeColor: Color,
    isRead: Boolean,
    isDelivered: Boolean,
) {
    val w = size.width
    val h = size.height
    val c = center
    val r = min(w, h) / 2 - 1.5f
    val full = isDelivered || isRead
    val white = Color.White

    fun stroke(width: Float = 1.6f) = Stroke(
        width = width,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
        join = androidx.compose.ui.graphics.StrokeJoin.Round,
    )

    fun gbCheck(cx: Float, cy: Float, sc: Float, color: Color, width: Float) {
        val p = Path().apply {
            moveTo(cx - 3.4f * sc, cy + 0.2f * sc)
            lineTo(cx - 0.8f * sc, cy + 2.6f * sc)
            lineTo(cx + 4.0f * sc, cy - 2.8f * sc)
        }
        drawPath(p, color, style = stroke(width))
    }

    fun gbCircle(center: Offset, radius: Float, filled: Boolean, color: Color = activeColor,
                 width: Float = 1.6f) {
        if (filled) drawCircle(color, radius = radius, center = center)
        else drawCircle(color, radius = radius, center = center, style = stroke(width))
    }

    fun gbLetter(letter: String, color: Color, fontSize: Float = h * 0.62f) {
        drawIntoCanvas { canvas ->
            val textPaint = Paint().apply {
                this.color = android.graphics.Color.argb(
                    (color.alpha * 255).toInt(),
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt(),
                )
                textSize = fontSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val baseline = c.y - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.nativeCanvas.drawText(letter, c.x, baseline, textPaint)
        }
    }

    fun gbSmiley(center: Offset, radius: Float, heartEyes: Boolean = false,
                 strokeColor: Color = activeColor, fillColor: Color = activeColor,
                 width: Float = 1.6f) {
        drawCircle(strokeColor, radius = radius, center = center, style = stroke(width))
        if (heartEyes) {
            drawPath(heartPath(Offset(center.x - radius * 0.38f, center.y - radius * 0.25f), radius * 0.20f), fillColor)
            drawPath(heartPath(Offset(center.x + radius * 0.38f, center.y - radius * 0.25f), radius * 0.20f), fillColor)
        } else {
            drawCircle(fillColor, radius = radius * 0.12f,
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.25f))
            drawCircle(fillColor, radius = radius * 0.12f,
                center = Offset(center.x + radius * 0.35f, center.y - radius * 0.25f))
        }
        val smile = Path().apply {
            moveTo(center.x - radius * 0.45f, center.y + radius * 0.25f)
            quadraticBezierTo(center.x, center.y + radius * 0.65f,
                center.x + radius * 0.45f, center.y + radius * 0.25f)
        }
        drawPath(smile, strokeColor, style = stroke(width))
    }

    fun gbCapsule(knobEnd: Boolean) {
        val rr = RoundRect(
            Rect(w * 0.06f, h * 0.26f, w * 0.94f, h * 0.74f),
            androidx.compose.ui.geometry.CornerRadius(h * 0.24f),
        )
        val p = Path().apply { addRoundRect(rr) }
        drawPath(p, activeColor, style = stroke())
        val knobX = if (knobEnd) w * 0.74f else w * 0.26f
        drawCircle(activeColor, radius = h * 0.17f, center = Offset(knobX, h / 2))
    }

    when (style) {
        DeliveryIconStyle.allo -> {
            gbCheck(w * 0.36f, h * 0.50f, 1.0f, activeColor, 2.6f)
            if (full) gbCheck(w * 0.62f, h * 0.50f, 1.0f, activeColor, 2.6f)
        }
        DeliveryIconStyle.alien -> {
            gbCircle(Offset(w * 0.34f, h * 0.5f), 3.2f, true)
            if (full) gbCircle(Offset(w * 0.66f, h * 0.5f), 3.2f, true)
        }
        DeliveryIconStyle.altCrayonV2 -> {
            gbCheck(w * 0.36f, h * 0.50f, 0.9f, activeColor, 2.0f)
            if (full) gbCheck(w * 0.60f, h * 0.50f, 0.9f, activeColor, 2.0f)
        }
        DeliveryIconStyle.altCrayon -> {
            gbCheck(w * 0.34f, h * 0.50f, 1.0f, activeColor, 1.2f)
            if (full) gbCheck(w * 0.64f, h * 0.50f, 1.0f, activeColor, 1.2f)
        }
        DeliveryIconStyle.aran -> gbCapsule(isRead)
        DeliveryIconStyle.bar -> {
            for (i in 0 until 4) {
                val bh = h * (0.22f + 0.17f * i)
                val filled = isRead || (isDelivered && i < 2) || i == 0
                val rect = Rect(w * (0.12f + 0.22f * i), h * 0.85f - bh,
                    w * (0.12f + 0.22f * i) + w * 0.14f, h * 0.85f)
                if (filled) drawRect(activeColor, rect.topLeft, rect.size)
                else drawRect(activeColor, rect.topLeft, rect.size, style = stroke())
            }
        }
        DeliveryIconStyle.bat -> {
            val rect = Rect(c.x - w * 0.4f, c.y - h * 0.3f, c.x + w * 0.4f, c.y + h * 0.3f)
            if (full) drawPath(batmanPath(rect), activeColor)
            else drawPath(batmanPath(rect), activeColor, style = stroke())
        }
        DeliveryIconStyle.bbm -> {
            gbCircle(c, r, isRead)
            gbLetter("R", if (isRead) white else activeColor)
        }
        DeliveryIconStyle.blend -> {
            val rr = RoundRect(Rect(w * 0.06f, h * 0.30f, w * 0.94f, h * 0.70f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.12f))
            drawPath(Path().apply { addRoundRect(rr) }, activeColor, style = stroke())
            for (i in 0 until 3) {
                drawLine(activeColor,
                    Offset(w * (0.20f + 0.10f * i), h * 0.36f),
                    Offset(w * (0.20f + 0.10f * i), h * 0.64f),
                    strokeWidth = 1.6f)
            }
            if (full) gbCheck(w * 0.66f, h * 0.50f, 0.9f, activeColor, 1.6f)
        }
        DeliveryIconStyle.ngAndroidBot -> {
            val body = RoundRect(Rect(w * 0.22f, h * 0.30f, w * 0.78f, h * 0.76f),
                androidx.compose.ui.geometry.CornerRadius(w * 0.16f))
            val p = Path().apply { addRoundRect(body) }
            if (isRead) drawPath(p, activeColor) else drawPath(p, activeColor, style = stroke())
            drawLine(activeColor, Offset(w * 0.32f, h * 0.30f), Offset(w * 0.24f, h * 0.16f), strokeWidth = 1.6f)
            drawLine(activeColor, Offset(w * 0.68f, h * 0.30f), Offset(w * 0.76f, h * 0.16f), strokeWidth = 1.6f)
            val eyeColor = if (isRead) white else activeColor
            drawCircle(eyeColor, radius = 1.6f, center = Offset(w * 0.38f, h * 0.48f))
            drawCircle(eyeColor, radius = 1.6f, center = Offset(w * 0.62f, h * 0.48f))
        }
        DeliveryIconStyle.bpgCircle -> {
            drawArc(activeColor, startAngle = 0.6f * DEG, sweepAngle = 5.0f * DEG, useCenter = false,
                topLeft = Offset(w * 0.34f - 5, h * 0.5f - 5), size = androidx.compose.ui.geometry.Size(10f, 10f),
                style = stroke())
            drawArc(activeColor, startAngle = 0.6f * DEG, sweepAngle = 5.0f * DEG, useCenter = false,
                topLeft = Offset(w * 0.60f - 5, h * 0.5f - 5), size = androidx.compose.ui.geometry.Size(10f, 10f),
                style = stroke())
            if (full) gbCheck(w * 0.74f, h * 0.5f, 0.8f, activeColor, 1.6f)
        }
        DeliveryIconStyle.gbCircles -> {
            gbCircle(Offset(w * 0.34f, h * 0.5f), 4.5f, true)
            gbCircle(Offset(w * 0.68f, h * 0.5f), 4.5f, full)
        }
        DeliveryIconStyle.samyadeepCircles -> {
            gbCircle(c, r, false)
            val bolt = Path().apply {
                moveTo(w * 0.56f, h * 0.18f)
                lineTo(w * 0.34f, h * 0.56f)
                lineTo(w * 0.50f, h * 0.56f)
                lineTo(w * 0.44f, h * 0.84f)
                lineTo(w * 0.68f, h * 0.46f)
                lineTo(w * 0.52f, h * 0.46f)
                close()
            }
            if (full) drawPath(bolt, activeColor) else drawPath(bolt, activeColor, style = stroke())
        }
        DeliveryIconStyle.coalfield -> {
            drawLine(activeColor, Offset(w * 0.34f, h * 0.24f), Offset(w * 0.22f, h * 0.76f), strokeWidth = 2.4f)
            if (full) drawLine(activeColor, Offset(w * 0.62f, h * 0.24f), Offset(w * 0.50f, h * 0.76f), strokeWidth = 2.4f)
        }
        DeliveryIconStyle.colorSimple -> gbCircle(c, 5.0f, true)
        DeliveryIconStyle.ctoon -> {
            val rect = RoundRect(Rect(w * 0.12f, h * 0.24f, w * 0.88f, h * 0.76f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.16f))
            val p = Path().apply { addRoundRect(rect) }
            if (isRead) drawPath(p, activeColor) else drawPath(p, activeColor, style = stroke())
            gbSmiley(c, r * 0.55f,
                strokeColor = if (isRead) white else activeColor,
                fillColor = if (isRead) white else activeColor)
        }
        DeliveryIconStyle.eclipsis -> {
            drawLine(activeColor, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.24f, h * 0.76f), strokeWidth = 1.6f)
            drawLine(activeColor, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.76f, h * 0.76f), strokeWidth = 1.6f)
            drawLine(activeColor, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.38f, h * 0.24f), strokeWidth = 1.6f)
            drawLine(activeColor, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.62f, h * 0.24f), strokeWidth = 1.6f)
            drawLine(activeColor, Offset(w * 0.24f, h * 0.76f), Offset(w * 0.38f, h * 0.76f), strokeWidth = 1.6f)
            drawLine(activeColor, Offset(w * 0.76f, h * 0.76f), Offset(w * 0.62f, h * 0.76f), strokeWidth = 1.6f)
            if (full) gbCheck(w * 0.50f, h * 0.5f, 0.8f, activeColor, 1.6f)
        }
        DeliveryIconStyle.faceSwitch -> {
            gbCapsule(isRead)
            gbSmiley(Offset(if (isRead) w * 0.74f else w * 0.26f, h / 2), h * 0.13f)
        }
        DeliveryIconStyle.feet -> {
            drawOval(activeColor, topLeft = Offset(w * 0.36f - w * 0.10f, h * 0.42f - h * 0.17f),
                size = androidx.compose.ui.geometry.Size(w * 0.20f, h * 0.34f))
            val second = Offset(w * 0.62f - w * 0.10f, h * 0.58f - h * 0.17f)
            val sz = androidx.compose.ui.geometry.Size(w * 0.20f, h * 0.34f)
            if (full) drawOval(activeColor, second, sz) else drawOval(activeColor, second, sz, style = stroke())
        }
        DeliveryIconStyle.flatCircles -> {
            gbCircle(c, r, isRead)
            gbCheck(w * 0.5f, h * 0.5f, 1.1f, if (isRead) white else activeColor, 1.6f)
        }
        DeliveryIconStyle.gifcon -> {
            gbCircle(c, r, false)
            gbCircle(c, r * 0.72f, isRead)
            gbCheck(w * 0.5f, h * 0.5f, 0.9f, if (isRead) white else activeColor, 1.6f)
        }
        DeliveryIconStyle.googleV2 -> {
            gbLetter("G", activeColor)
            if (full) gbCheck(w * 0.70f, h * 0.66f, 0.7f, activeColor, 1.6f)
        }
        DeliveryIconStyle.google -> gbLetter("G", activeColor)
        DeliveryIconStyle.grw -> {
            val p1 = Path().apply {
                moveTo(w * 0.08f, h * 0.52f)
                quadraticBezierTo(w * 0.24f, h * 0.78f, w * 0.34f, h * 0.60f)
                lineTo(w * 0.58f, h * 0.24f)
            }
            drawPath(p1, activeColor, style = stroke(2.2f))
            if (full) {
                val p2 = Path().apply {
                    moveTo(w * 0.42f, h * 0.52f)
                    quadraticBezierTo(w * 0.58f, h * 0.78f, w * 0.68f, h * 0.60f)
                    lineTo(w * 0.92f, h * 0.24f)
                }
                drawPath(p2, activeColor, style = stroke(2.2f))
            }
        }
        DeliveryIconStyle.sidzyHd -> {
            gbCircle(c, r, isRead)
            gbCheck(w * 0.5f, h * 0.5f, 1.1f, if (isRead) white else activeColor, 1.6f)
        }
        DeliveryIconStyle.svHeart ->
            drawPath(heartPath(c, r * 0.85f), activeColor, style = if (full) androidx.compose.ui.graphics.drawscope.Fill else stroke())
        DeliveryIconStyle.hike -> {
            gbCheck(w * 0.36f, h * 0.5f, 0.9f, activeColor, 2.0f)
            gbLetter("R", activeColor, fontSize = h * 0.4f)
        }
        DeliveryIconStyle.inbox -> {
            val env = Rect(w * 0.14f, h * 0.26f, w * 0.86f, h * 0.74f)
            if (isRead) drawRect(activeColor, env.topLeft, env.size)
            else drawRect(activeColor, env.topLeft, env.size, style = stroke())
            val flap = Path().apply {
                moveTo(w * 0.14f, h * 0.26f)
                lineTo(w * 0.5f, h * 0.56f)
                lineTo(w * 0.86f, h * 0.26f)
            }
            drawPath(flap, if (isRead) white else activeColor, style = stroke())
        }
        DeliveryIconStyle.insect -> {
            gbCircle(c, r * 0.7f, isRead)
            for (i in -1..1) {
                drawLine(activeColor, Offset(w * 0.5f + i * w * 0.22f, h * 0.26f),
                    Offset(w * 0.5f + i * w * 0.34f, h * 0.10f), strokeWidth = 1.6f)
                drawLine(activeColor, Offset(w * 0.5f + i * w * 0.22f, h * 0.74f),
                    Offset(w * 0.5f + i * w * 0.34f, h * 0.90f), strokeWidth = 1.6f)
            }
            if (full) gbCheck(w * 0.5f, h * 0.52f, 0.8f, if (isRead) white else activeColor, 1.6f)
        }
        DeliveryIconStyle.gbIos -> {
            gbCheck(w * 0.34f, h * 0.5f, 1.0f, activeColor, 1.8f)
            if (full) gbCheck(w * 0.62f, h * 0.5f, 1.0f, activeColor, 1.8f)
        }
        DeliveryIconStyle.jalopy -> gbSmiley(c, r, heartEyes = isRead)
        DeliveryIconStyle.jessy -> gbCapsule(isRead)
        DeliveryIconStyle.joaquins -> {
            gbCheck(w * 0.30f, h * 0.54f, 0.9f, activeColor, 2.4f)
            gbCheck(w * 0.56f, h * 0.54f, 0.9f,
                if (full) activeColor else activeColor.copy(alpha = 0.45f), 2.4f)
        }
        DeliveryIconStyle.jokerTraffic, DeliveryIconStyle.jokerTraffic2 -> {
            for (i in 0 until 3) {
                val on = isRead || (isDelivered && i < 2) || i == 0
                gbCircle(Offset(w * (0.26f + 0.24f * i), h * 0.5f), 3.6f, on)
            }
        }
        DeliveryIconStyle.letter -> gbLetter("R", activeColor)
        DeliveryIconStyle.like -> {
            val thumb = Path().apply {
                moveTo(w * 0.30f, h * 0.82f)
                lineTo(w * 0.30f, h * 0.42f)
                lineTo(w * 0.46f, h * 0.40f)
                lineTo(w * 0.56f, h * 0.16f)
                quadraticBezierTo(w * 0.68f, h * 0.16f, w * 0.64f, h * 0.34f)
                lineTo(w * 0.80f, h * 0.36f)
                quadraticBezierTo(w * 0.90f, h * 0.38f, w * 0.86f, h * 0.50f)
                lineTo(w * 0.80f, h * 0.80f)
                close()
            }
            if (isRead) {
                drawPath(thumb, activeColor)
                drawRect(activeColor, Offset(w * 0.14f, h * 0.42f),
                    androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.40f))
            } else {
                drawPath(thumb, activeColor, style = stroke())
                drawRect(activeColor, Offset(w * 0.14f, h * 0.42f),
                    androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.40f), style = stroke())
            }
        }
        DeliveryIconStyle.loveSwitch -> {
            gbCapsule(isRead)
            drawPath(heartPath(Offset(if (isRead) w * 0.74f else w * 0.26f, h / 2), h * 0.10f), activeColor)
        }
        DeliveryIconStyle.materialV2 -> {
            gbCircle(Offset(w * 0.34f, h * 0.5f), r * 0.62f, isRead)
            gbCheck(w * 0.34f, h * 0.5f, 0.8f, if (isRead) white else activeColor, 1.6f)
            for (i in 0 until 3) {
                drawLine(activeColor, Offset(w * 0.66f, h * (0.34f + 0.14f * i)),
                    Offset(w * 0.88f, h * (0.34f + 0.14f * i)), strokeWidth = 1.6f)
            }
        }
        DeliveryIconStyle.materialSwitch -> {
            gbCapsule(isRead)
            gbCheck(if (isRead) w * 0.74f else w * 0.26f, h / 2, 0.6f, activeColor, 1.6f)
        }
        DeliveryIconStyle.messenger -> {
            gbCircle(c, r, isRead)
            gbCheck(w * 0.5f, h * 0.5f, 1.1f, if (isRead) white else activeColor, 1.6f)
        }
        DeliveryIconStyle.mickeyHands -> {
            // Original mitten thumbs-up (not the licensed character art).
            val mitt = Path().apply {
                moveTo(w * 0.34f, h * 0.84f)
                lineTo(w * 0.34f, h * 0.46f)
                quadraticBezierTo(w * 0.34f, h * 0.30f, w * 0.46f, h * 0.32f)
                lineTo(w * 0.52f, h * 0.16f)
                quadraticBezierTo(w * 0.64f, h * 0.14f, w * 0.62f, h * 0.30f)
                lineTo(w * 0.60f, h * 0.40f)
                lineTo(w * 0.78f, h * 0.42f)
                quadraticBezierTo(w * 0.90f, h * 0.46f, w * 0.84f, h * 0.58f)
                lineTo(w * 0.78f, h * 0.84f)
                close()
            }
            if (isRead) drawPath(mitt, activeColor) else drawPath(mitt, activeColor, style = stroke())
        }
        DeliveryIconStyle.minions -> {
            // Original round goggled face (not the licensed character art).
            gbCircle(c, r, false)
            drawLine(activeColor, Offset(c.x - r, c.y - r * 0.15f),
                Offset(c.x + r, c.y - r * 0.15f), strokeWidth = 1.6f)
            gbCircle(Offset(c.x - r * 0.32f, c.y - r * 0.15f), r * 0.24f, isRead)
            gbCircle(Offset(c.x + r * 0.32f, c.y - r * 0.15f), r * 0.24f, isRead)
            val sm = Path().apply {
                moveTo(c.x - r * 0.3f, c.y + r * 0.4f)
                quadraticBezierTo(c.x, c.y + r * 0.6f, c.x + r * 0.3f, c.y + r * 0.4f)
            }
            drawPath(sm, activeColor, style = stroke())
        }
        DeliveryIconStyle.newWaca -> {
            gbCircle(Offset(w * 0.24f, h * 0.5f), 2.6f, true)
            gbCircle(Offset(w * 0.42f, h * 0.5f), 2.6f, full)
            if (full) gbCheck(w * 0.70f, h * 0.5f, 0.9f, activeColor, 1.6f)
        }
        DeliveryIconStyle.neyHeart ->
            drawPath(heartPath(c, r * 0.85f), activeColor, style = if (isRead) androidx.compose.ui.graphics.drawscope.Fill else stroke())
        DeliveryIconStyle.nhsc -> {
            gbCircle(c, r, false)
            gbCheck(w * 0.5f, h * 0.5f, 1.0f, activeColor, 1.6f)
        }
        DeliveryIconStyle.nazaNh -> {
            gbCircle(c, r, isRead)
            gbCheck(w * 0.5f, h * 0.5f, 1.0f, if (isRead) white else activeColor, 1.6f)
        }
        DeliveryIconStyle.nlCircle -> {
            gbCircle(c, r, false, width = 2.6f)
            if (full) gbCircle(c, r * 0.45f, true)
        }
        DeliveryIconStyle.oldWaca -> {
            gbCapsule(isRead)
            gbCheck(if (isRead) w * 0.74f else w * 0.5f, h / 2, 0.6f, activeColor, 1.6f)
        }
        DeliveryIconStyle.pacman -> {
            val pm = Path().apply {
                moveTo(c.x, c.y)
                arcToRad(
                    Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                    -0.6f,
                    (2 * Math.PI - 1.2 * (if (isRead) 0.4 else 1.0)).toFloat(),
                    forceMoveTo = true,
                )
                close()
            }
            if (isRead) drawPath(pm, activeColor) else drawPath(pm, activeColor, style = stroke())
            drawCircle(activeColor, radius = 1.5f, center = Offset(c.x, c.y - r * 0.45f))
        }
        DeliveryIconStyle.pixelated -> {
            val px = w * 0.16f
            for (i in 0 until 4) {
                for (j in 0 until 4) {
                    if ((i + j) % 2 == (if (isRead) 1 else 0)) {
                        drawRect(activeColor, Offset(w * 0.18f + i * px, h * 0.18f + j * px),
                            androidx.compose.ui.geometry.Size(px * 0.9f, px * 0.9f))
                    }
                }
            }
        }
        DeliveryIconStyle.pokemon -> {
            gbCircle(c, r, false)
            drawLine(activeColor, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1.6f)
            gbCircle(c, r * 0.28f, isRead)
        }
        DeliveryIconStyle.quinn -> {
            val r1 = Rect(w * 0.14f, h * 0.24f, w * 0.54f, h * 0.64f)
            val r2 = Rect(w * 0.42f, h * 0.38f, w * 0.82f, h * 0.78f)
            if (isRead) {
                drawRect(activeColor, r1.topLeft, r1.size)
                drawRect(activeColor, r2.topLeft, r2.size)
            } else {
                drawRect(activeColor, r1.topLeft, r1.size, style = stroke())
                drawRect(activeColor, r2.topLeft, r2.size, style = stroke())
            }
            gbCheck(w * 0.34f, h * 0.44f, 0.7f, if (isRead) white else activeColor, 1.6f)
            if (full) gbCheck(w * 0.62f, h * 0.58f, 0.7f, if (isRead) white else activeColor, 1.6f)
        }
        DeliveryIconStyle.snapChat -> {
            // Original rounded ghost with wavy base (not the app logo art).
            val ghost = Path().apply {
                moveTo(w * 0.24f, h * 0.78f)
                lineTo(w * 0.24f, h * 0.44f)
                arcTo(Rect(w * 0.24f, h * 0.44f - w * 0.26f, w * 0.76f, h * 0.44f + w * 0.26f),
                    180f, 180f, false)
                lineTo(w * 0.76f, h * 0.78f)
                quadraticBezierTo(w * 0.66f, h * 0.68f, w * 0.58f, h * 0.78f)
                quadraticBezierTo(w * 0.50f, h * 0.68f, w * 0.42f, h * 0.78f)
                quadraticBezierTo(w * 0.34f, h * 0.68f, w * 0.24f, h * 0.78f)
                close()
            }
            if (isRead) drawPath(ghost, activeColor) else drawPath(ghost, activeColor, style = stroke())
            val eyeColor = if (isRead) white else activeColor
            drawCircle(eyeColor, radius = 1.6f, center = Offset(w * 0.42f, h * 0.44f))
            drawCircle(eyeColor, radius = 1.6f, center = Offset(w * 0.58f, h * 0.44f))
        }
        DeliveryIconStyle.sDot -> gbCircle(c, r * 0.8f, true)
        DeliveryIconStyle.seasons -> {
            gbCapsule(isRead)
            gbCircle(Offset(if (isRead) w * 0.74f else w * 0.26f, h / 2), h * 0.10f, false)
        }
        DeliveryIconStyle.smile -> gbSmiley(c, r)
        DeliveryIconStyle.star -> {
            val star = Path()
            for (i in 0 until 5) {
                val a = -Math.PI / 2 + i * 4 * Math.PI / 5
                val x = (c.x + r * 0.9f * cos(a)).toFloat()
                val y = (c.y + r * 0.9f * sin(a)).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            if (isRead) drawPath(star, activeColor) else drawPath(star, activeColor, style = stroke())
        }
        DeliveryIconStyle.flatSwitch -> {
            val rr = RoundRect(Rect(w * 0.06f, h * 0.26f, w * 0.56f, h * 0.74f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.10f))
            val p = Path().apply { addRoundRect(rr) }
            if (isRead) drawPath(p, activeColor) else drawPath(p, activeColor, style = stroke())
            gbCheck(w * 0.30f, h * 0.5f, 0.7f, if (isRead) white else activeColor, 1.6f)
            drawRect(activeColor, Offset(w * 0.60f, h * 0.26f),
                androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.48f), style = stroke())
        }
        DeliveryIconStyle.timeBomb -> {
            gbCircle(Offset(c.x, c.y + r * 0.1f), r * 0.75f, isRead)
            drawLine(activeColor, Offset(c.x + r * 0.3f, c.y - r * 0.5f),
                Offset(c.x + r * 0.55f, c.y - r * 0.85f), strokeWidth = 1.6f)
            gbCircle(Offset(c.x + r * 0.6f, c.y - r * 0.9f), 1.8f, true)
        }
        DeliveryIconStyle.treble -> {
            for (i in 0 until (if (full) 3 else 2)) {
                val x0 = w * (0.12f + 0.26f * i)
                val zz = Path().apply {
                    moveTo(x0, h * 0.62f)
                    lineTo(x0 + w * 0.12f, h * 0.38f)
                    lineTo(x0 + w * 0.24f, h * 0.62f)
                }
                drawPath(zz, activeColor, style = stroke())
            }
        }
        DeliveryIconStyle.twitterConcept -> {
            // Original bird mark (not the platform logo art).
            val bird = Path().apply {
                moveTo(w * 0.20f, h * 0.62f)
                quadraticBezierTo(w * 0.34f, h * 0.44f, w * 0.52f, h * 0.44f)
                lineTo(w * 0.66f, h * 0.26f)
                quadraticBezierTo(w * 0.70f, h * 0.44f, w * 0.62f, h * 0.52f)
                quadraticBezierTo(w * 0.80f, h * 0.56f, w * 0.76f, h * 0.70f)
                quadraticBezierTo(w * 0.58f, h * 0.82f, w * 0.38f, h * 0.76f)
                quadraticBezierTo(w * 0.24f, h * 0.74f, w * 0.20f, h * 0.62f)
                close()
            }
            if (isRead) drawPath(bird, activeColor) else drawPath(bird, activeColor, style = stroke())
        }
        DeliveryIconStyle.wbg -> gbCapsule(isRead)
        DeliveryIconStyle.x -> {
            gbCircle(c, r, false)
            gbCheck(w * 0.5f, h * 0.5f, 1.2f, activeColor, 2.4f)
        }
        DeliveryIconStyle.pointer -> {
            for (i in 0 until 10) {
                val a = i * Math.PI / 5
                val on = i <= (if (isRead) 9 else (if (isDelivered) 5 else 2))
                gbCircle(
                    Offset((c.x + r * 0.85f * cos(a)).toFloat(), (c.y + r * 0.85f * sin(a)).toFloat()),
                    1.6f, on,
                )
            }
        }
        DeliveryIconStyle.ticTicCircle2 -> {
            gbCircle(c, r, false)
            gbCircle(c, r * 0.66f, false)
            if (full) gbCheck(w * 0.5f, h * 0.5f, 0.9f, activeColor, 1.6f)
        }
        DeliveryIconStyle.iceCream -> {
            val scoop = Path().apply {
                moveTo(w * 0.30f, h * 0.46f)
                arcTo(Rect(w * 0.30f, h * 0.46f - w * 0.20f, w * 0.70f, h * 0.46f + w * 0.20f),
                    180f, 180f, false)
                close()
            }
            if (isRead) drawPath(scoop, activeColor) else drawPath(scoop, activeColor, style = stroke())
            val cone = Path().apply {
                moveTo(w * 0.32f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.86f)
                lineTo(w * 0.68f, h * 0.50f)
                close()
            }
            drawPath(cone, activeColor, style = stroke())
        }
        DeliveryIconStyle.pony -> {
            // Original horse-head silhouette (not any licensed character art).
            val pony = Path().apply {
                moveTo(w * 0.30f, h * 0.84f)
                lineTo(w * 0.30f, h * 0.40f)
                lineTo(w * 0.24f, h * 0.20f)
                lineTo(w * 0.38f, h * 0.30f)
                quadraticBezierTo(w * 0.50f, h * 0.16f, w * 0.64f, h * 0.24f)
                lineTo(w * 0.78f, h * 0.44f)
                lineTo(w * 0.72f, h * 0.52f)
                lineTo(w * 0.56f, h * 0.50f)
                lineTo(w * 0.56f, h * 0.84f)
                close()
            }
            if (isRead) drawPath(pony, activeColor) else drawPath(pony, activeColor, style = stroke())
            drawCircle(if (isRead) white else activeColor, radius = 1.5f, center = Offset(w * 0.46f, h * 0.36f))
        }
        DeliveryIconStyle.kitty -> {
            // Original plain cat face (no bow; not the licensed character art).
            val head = Path().apply {
                moveTo(w * 0.24f, h * 0.40f)
                lineTo(w * 0.30f, h * 0.18f)
                lineTo(w * 0.42f, h * 0.32f)
                lineTo(w * 0.58f, h * 0.32f)
                lineTo(w * 0.70f, h * 0.18f)
                lineTo(w * 0.76f, h * 0.40f)
                arcTo(Rect(w * 0.24f, h * 0.40f - w * 0.26f, w * 0.76f, h * 0.40f + w * 0.26f),
                    0f, -180f, false)
                close()
            }
            if (isRead) drawPath(head, activeColor) else drawPath(head, activeColor, style = stroke())
            val eyeColor = if (isRead) white else activeColor
            drawCircle(eyeColor, radius = 1.5f, center = Offset(w * 0.40f, h * 0.52f))
            drawCircle(eyeColor, radius = 1.5f, center = Offset(w * 0.60f, h * 0.52f))
        }
        DeliveryIconStyle.flower -> {
            for (i in 0 until 5) {
                val a = i * 2 * Math.PI / 5 - Math.PI / 2
                gbCircle(
                    Offset((c.x + r * 0.55f * cos(a)).toFloat(), (c.y + r * 0.55f * sin(a)).toFloat()),
                    r * 0.34f, isRead,
                )
            }
            gbCircle(c, r * 0.26f, true)
        }
        else -> gbCheck(w * 0.5f, h * 0.5f, 1.0f, activeColor, 1.6f)
    }
}
