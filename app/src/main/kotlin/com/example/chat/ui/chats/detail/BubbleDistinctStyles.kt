package com.example.chat.ui.chats.detail

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.example.chat.core.theme.BubbleStyleId
import kotlin.math.min

/**
 * Chaty vector variants for bubble style ids that used to share one silhouette with another id
 * (for example Fold vs Fold v2, Twitter vs Facebook Messenger). The FIRST id of each former
 * shared group keeps its original silhouette; the other ids get their own shape or accent here,
 * chosen from the style name. These are Chaty-drawn vectors, not replicas of the original
 * GBWhatsApp artwork. Style ids and their stored values are unchanged.
 *
 * Hooked from [bubblePath] (silhouette) and drawBubbleAccents (accents) in BubblePainter.kt.
 */

private fun rr(size: Size, tl: Float, tr: Float, bl: Float, br: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            Rect(0f, 0f, size.width, size.height),
            topLeft = CornerRadius(tl), topRight = CornerRadius(tr),
            bottomLeft = CornerRadius(bl), bottomRight = CornerRadius(br),
        ),
    )
}

/** Rounded rect with one flatter corner on the tail side (bottom, or top when [top]). */
private fun tailRect(size: Size, isMe: Boolean, r: Float, flat: Float, top: Boolean = false): Path = when {
    !top && isMe -> rr(size, r, r, r, flat)
    !top -> rr(size, r, r, flat, r)
    isMe -> rr(size, r, flat, r, r)
    else -> rr(size, flat, r, r, r)
}

/** Body plus a small swept tail hooking out of the tail-side corner, as a true union. */
private fun withTail(
    body: Path, size: Size, isMe: Boolean, top: Boolean = false,
    tip: Float, rise: Float, inset: Float, base: Float,
): Path {
    fun x(dx: Float) = if (isMe) size.width + dx else -dx
    fun y(dy: Float) = if (top) dy else size.height - dy
    val tail = Path().apply {
        moveTo(x(-inset), y(rise))
        quadraticBezierTo(x(0f), y(rise * 0.3f), x(tip), y(0f))
        lineTo(x(-base), y(0f))
        close()
    }
    return Path().apply { op(body, tail, PathOperation.Union) }
}

private fun dome(size: Size, domeR: Float, bottomR: Float, bevel: Boolean): Path = Path().apply {
    val r = min(size.width / 2, domeR)
    val cx = size.width / 2
    val b = min(bottomR, size.height / 3)
    moveTo(0f, size.height - b)
    lineTo(0f, r)
    lineTo(cx - r, r)
    arcTo(Rect(cx - r, 0f, cx + r, r * 2), 180f, 180f, false)
    lineTo(size.width, r)
    lineTo(size.width, size.height - b)
    if (bevel) {
        lineTo(size.width - b, size.height); lineTo(b, size.height)
    } else {
        quadraticBezierTo(size.width, size.height, size.width - b, size.height)
        lineTo(b, size.height)
        quadraticBezierTo(0f, size.height, 0f, size.height - b)
    }
    close()
}

private fun chamfered(size: Size, isMe: Boolean, c: Float, r: Float, top: Boolean): Path = Path().apply {
    val w = size.width; val h = size.height
    // Clockwise outline; the chamfer sits on the outer corner of the tail side.
    val chTR = top && isMe; val chTL = top && !isMe; val chBR = !top && isMe; val chBL = !top && !isMe
    moveTo(if (chTL) c else r, 0f)
    lineTo(if (chTR) w - c else w - r, 0f)
    if (chTR) lineTo(w, c) else arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
    lineTo(w, if (chBR) h - c else h - r)
    if (chBR) lineTo(w - c, h) else arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
    lineTo(if (chBL) c else r, h)
    if (chBL) lineTo(0f, h - c) else arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
    lineTo(0f, if (chTL) c else r)
    if (chTL) lineTo(c, 0f) else arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
    close()
}

private fun doodle(size: Size): Path = Path().apply {
    val w = size.width; val h = size.height
    moveTo(12f, 1f)
    quadraticBezierTo(w * 0.5f, -2.5f, w - 12f, 0f)
    quadraticBezierTo(w + 1f, 0f, w, 12f)
    quadraticBezierTo(w + 2.5f, h * 0.5f, w - 1f, h - 12f)
    quadraticBezierTo(w, h, w - 12f, h - 1f)
    quadraticBezierTo(w * 0.5f, h + 2.5f, 12f, h)
    quadraticBezierTo(0f, h, 1f, h - 12f)
    quadraticBezierTo(-2.5f, h * 0.5f, 0f, 12f)
    quadraticBezierTo(0f, 0f, 12f, 1f)
    close()
}

/** Null means this id keeps the silhouette already in bubblePath. */
internal fun distinctBubblePath(id: BubbleStyleId, size: Size, isMe: Boolean): Path? {
    val h = size.height
    return when (id) {
        // iMessage style curl tail
        BubbleStyleId.ios -> withTail(tailRect(size, isMe, min(18f, h / 2), 0f), size, isMe, false, 5f, 14f, 6f, 12f)
        // Small swept hook
        BubbleStyleId.cvIos -> withTail(tailRect(size, isMe, min(18f, h / 2), 0f), size, isMe, false, 3f, 8f, 4f, 8f)
        BubbleStyleId.gabiDot2 -> tailRect(size, isMe, 14f, 2f, top = true)
        BubbleStyleId.ilkhang -> tailRect(size, isMe, 18f, 2f)
        // Chamfered "boxy" frame
        BubbleStyleId.aranbor -> chamfered(size, isMe, 6f, 4f, top = false).let { p ->
            // chamfer both bottom corners by drawing the opposite one too
            Path().apply { op(p, chamfered(size, !isMe, 6f, 4f, top = false), PathOperation.Intersect) }
        }
        // Thought-bubble pill, circles drawn as accent
        BubbleStyleId.gabyRon -> rr(size, min(24f, h / 2), min(24f, h / 2), min(24f, h / 2), min(24f, h / 2))
        // WhatsApp-like tail on the top outer corner
        BubbleStyleId.whatsappLb -> withTail(tailRect(size, isMe, 12f, 0f, top = true), size, isMe, true, 6f, 10f, 4f, 8f)
        // Telegram-like hook at the bottom corner
        BubbleStyleId.telegram -> withTail(tailRect(size, isMe, 14f, 0f), size, isMe, false, 6f, 10f, 3f, 14f)
        // Samsung ChatOn: big triangular tail
        BubbleStyleId.waChatOn -> withTail(tailRect(size, isMe, 10f, 0f), size, isMe, false, 8f, 16f, 2f, 6f)
        // Twitter DM: one sharp corner
        BubbleStyleId.twitter -> tailRect(size, isMe, 18f, 0f)
        BubbleStyleId.plusMessengerEd -> tailRect(size, isMe, 18f, 4f, top = true)
        BubbleStyleId.newHangouts4 -> if (isMe) rr(size, 16f, 0f, 16f, 16f) else rr(size, 0f, 16f, 16f, 16f)
        BubbleStyleId.doodleHang -> doodle(size)
        BubbleStyleId.samyadeepCrayonAlt -> if (isMe) rr(size, 12f, 12f, 12f, 0f) else rr(size, 12f, 12f, 0f, 12f)
        BubbleStyleId.rounded -> tailRect(size, isMe, 22f, 8f)
        // Material card: generous top, tight bottom
        BubbleStyleId.md -> rr(size, 20f, 20f, 6f, 6f)
        // Fold: chamfer at the bottom outer corner. Fold v2: larger chamfer at the top.
        BubbleStyleId.fold -> chamfered(size, isMe, 12f, 12f, top = false)
        BubbleStyleId.foldV2 -> chamfered(size, isMe, 18f, 14f, top = true)
        // Leaf: diagonal corners round, the others tight
        BubbleStyleId.bryed -> rr(size, 14f, 4f, 4f, 14f)
        BubbleStyleId.hike -> withTail(tailRect(size, isMe, 10f, 0f), size, isMe, false, 5f, 9f, 3f, 9f)
        BubbleStyleId.win -> rr(size, 2f, 2f, 2f, 2f)
        BubbleStyleId.samyadeepDual -> tailRect(size, isMe, 20f, 6f)
        BubbleStyleId.popzup -> rr(size, h / 2, h / 2, h / 2, h / 2)
        BubbleStyleId.line -> tailRect(size, isMe, 20f, 0f)
        BubbleStyleId.mood -> tailRect(size, isMe, 24f, 8f)
        // Swept tail on the top outer corner instead of the bottom
        BubbleStyleId.rcGoSms -> withTail(tailRect(size, isMe, min(16f, h / 2), 4f, top = true), size, isMe, true, 12f, 14f, 10f, 2f)
        BubbleStyleId.rcImess -> dome(size, 26f, 10f, bevel = false)
        BubbleStyleId.rcTranLine -> dome(size, 24f, 10f, bevel = true)
        else -> null
    }
}

/** True when this id's accents were drawn here (the base accent for that id is then skipped). */
internal fun DrawScope.drawDistinctAccents(
    id: BubbleStyleId, isMe: Boolean, fillColor: Color, strokeColor: Color, accentColor: Color,
): Boolean {
    val w = size.width; val h = size.height
    val outerX = if (isMe) w else 0f
    val inward = if (isMe) -1f else 1f
    when (id) {
        // 3D: raised lip along the bottom and a highlight along the top
        BubbleStyleId.threeD -> {
            drawLine(Color.Black.copy(alpha = 0.28f), Offset(10f, h - 1.5f), Offset(w - 10f, h - 1.5f), 3f)
            drawLine(Color.White.copy(alpha = 0.30f), Offset(10f, 1.5f), Offset(w - 10f, 1.5f), 2f)
        }
        // Outline: second, inner hairline
        BubbleStyleId.gabiOutline -> drawRoundRect(
            strokeColor.copy(alpha = 0.6f), Offset(3f, 3f), Size(w - 6f, h - 6f), CornerRadius(13f), style = Stroke(1f),
        )
        // Dot 2: hollow ring at the bottom outer corner
        BubbleStyleId.gabiDot2 -> drawCircle(accentColor, 4f, Offset(outerX - inward * 2f, h - 4f), style = Stroke(1.5f))
        // Ilkhang: two satellite dots
        BubbleStyleId.ilkhang -> {
            drawCircle(accentColor, 2.5f, Offset(outerX - inward * 2f, 4f))
            drawCircle(accentColor, 1.8f, Offset(outerX - inward * 2f, 11f))
        }
        // Gaby Ron: thought-bubble circles trailing from the corner
        BubbleStyleId.gabyRon -> {
            drawCircle(fillColor, 3f, Offset(outerX - inward * 2f, h + 1f))
            drawCircle(fillColor, 1.8f, Offset(outerX - inward * 8f, h + 6f))
        }
        // Crayon: second offset stroke like a doubled pencil line
        BubbleStyleId.samyadeepCrayonAlt -> {
            val p = Path().apply { addPath(bubblePath(id, size, isMe), Offset(1.6f, 1.6f)) }
            drawPath(p, accentColor.copy(alpha = 0.55f), style = Stroke(1.2f))
        }
        // Bubble in bubble
        BubbleStyleId.inBubble -> drawRoundRect(
            accentColor.copy(alpha = 0.14f), Offset(4f, 4f), Size(w - 8f, h - 8f), CornerRadius(10f),
        )
        BubbleStyleId.fold -> {
            val c = 12f
            val tri = Path().apply {
                if (isMe) { moveTo(w - c, h); lineTo(w, h - c); lineTo(w - c, h - c) }
                else { moveTo(c, h); lineTo(0f, h - c); lineTo(c, h - c) }
                close()
            }
            drawPath(tri, Color.Black.copy(alpha = 0.15f))
        }
        BubbleStyleId.foldV2 -> {
            val c = 18f
            val tri = Path().apply {
                if (isMe) { moveTo(w - c, 0f); lineTo(w, c); lineTo(w - c, c) }
                else { moveTo(c, 0f); lineTo(0f, c); lineTo(c, c) }
                close()
            }
            drawPath(tri, Color.White.copy(alpha = 0.22f))
        }
        // Dual: two-tone lower band
        BubbleStyleId.samyadeepDual -> clipPath(bubblePath(id, size, isMe)) {
            drawRect(Color.Black.copy(alpha = 0.10f), Offset(0f, h * 0.55f), Size(w, h * 0.45f))
        }
        // Popzup: pop bubbles trailing from the corner
        BubbleStyleId.popzup -> {
            drawCircle(fillColor, 2.5f, Offset(outerX - inward * 3f, h - 2f))
            drawCircle(fillColor, 1.5f, Offset(outerX - inward * 9f, h + 3f))
        }
        // Line: accent bar on the tail side
        BubbleStyleId.line -> {
            val x = if (isMe) w - 6f else 6f
            drawLine(accentColor, Offset(x, 6f), Offset(x, h - 6f), 2.5f)
        }
        // Mood: tiny smile near the top outer corner
        BubbleStyleId.mood -> drawArc(
            accentColor.copy(alpha = 0.8f), 20f, 140f, false,
            Offset(outerX + inward * 14f - 4f, 4f), Size(8f, 6f), style = Stroke(1.4f),
        )
        else -> return false
    }
    return true
}
