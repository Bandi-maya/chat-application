package com.example.chat.ui.chats.detail

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.chat.core.theme.BubbleStyleId
import kotlin.math.min
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/** Exact port of lib/ui/core/bubbles/bubble_geometry.dart's getBubblePath and
 *  bubble_painter.dart (fill, outline, drop shadow, decorative accents) to a
 *  Compose DrawScope. Flutter arcToPoint quarter-corners map to arcTo with
 *  axis-aligned rects; coordinates are y-down in both engines. */

private fun rrect(
    rect: Rect,
    tl: CornerRadius,
    tr: CornerRadius,
    bl: CornerRadius,
    br: CornerRadius,
): Path = Path().apply {
    addRoundRect(RoundRect(rect, topLeft = tl, topRight = tr, bottomLeft = bl, bottomRight = br))
}

private fun uniform(rect: Rect, r: Float): Path =
    rrect(rect, CornerRadius(r), CornerRadius(r), CornerRadius(r), CornerRadius(r))

/** Quarter-circle arc from one axis-aligned corner to the next, matching
 *  Flutter arcToPoint(Offset, radius) for the corner patterns below. */
private fun Path.cornerArc(rect: Rect, startDeg: Float) {
    arcTo(rect, startDeg, 90f, forceMoveTo = false)
}

fun bubblePath(styleId: BubbleStyleId, size: Size, isMe: Boolean): Path {
    val left = 0f
    val top = 0f
    val right = size.width
    val bottom = size.height
    val height = size.height
    val full = Rect(left, top, right, bottom)

    return when (styleId) {
        // 1. Cr Messenger: super-ellipse pill with subtle pinched tail
        BubbleStyleId.crMessenger -> {
            val r = CornerRadius(min(18f, height / 2))
            val tail = CornerRadius(4f)
            if (isMe) rrect(full, r, r, r, tail) else rrect(full, r, r, tail, r)
        }

        // 2. RC BURBUJA 2: balloon speech bubble with teardrop corner
        BubbleStyleId.rcBurbuja2 -> Path().apply {
            val r = CornerRadius(min(16f, height / 2))
            if (isMe) {
                moveTo(left + 16, top)
                lineTo(right - 16, top)
                cornerArc(Rect(right - 32, top, right, top + 32), -90f)
                lineTo(right, bottom - 10)
                quadraticBezierTo(right + 6, bottom + 4, right - 12, bottom)
                lineTo(left + 16, bottom)
                cornerArc(Rect(left, bottom - 32, left + 32, bottom), 90f)
                lineTo(left, top + 16)
                cornerArc(Rect(left, top, left + 32, top + 32), 180f)
            } else {
                moveTo(left + 16, top)
                lineTo(right - 16, top)
                cornerArc(Rect(right - 32, top, right, top + 32), -90f)
                lineTo(right, bottom - 16)
                cornerArc(Rect(right - 32, bottom - 32, right, bottom), 0f)
                lineTo(left + 12, bottom)
                quadraticBezierTo(left - 6, bottom + 4, left, bottom - 10)
                lineTo(left, top + 16)
                cornerArc(Rect(left, top, left + 32, top + 32), 180f)
            }
            close()
        }

        // 3. RC BURBUJA 5: leaf / pebble organic asymmetrical curve
        BubbleStyleId.rcBurbuja5 -> {
            val big = CornerRadius(22f)
            val small = CornerRadius(4f)
            if (isMe) rrect(full, big, small, small, big) else rrect(full, small, big, big, small)
        }

        // 4. 3D V2: chiseled layered bezel
        BubbleStyleId.threeDV2, BubbleStyleId.threeD -> uniform(full, 12f)

        // 5. Kitty: whimsical crown arch
        BubbleStyleId.kitty -> {
            val r = CornerRadius(18f)
            val flat = CornerRadius(4f)
            if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
        }

        // 6. Amor: heart crest accent
        BubbleStyleId.amor -> {
            val r = CornerRadius(16f)
            val flat = CornerRadius(2f)
            if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
        }

        // 7. RC SAMS BORD: capsule outline
        BubbleStyleId.rcSamsBord, BubbleStyleId.gabiOutline -> uniform(full, 16f)

        // 8. RC IOS 11: sleek rounded iOS tail
        BubbleStyleId.rcIos11, BubbleStyleId.ios -> {
            val r = CornerRadius(min(18f, height / 2))
            val tail = CornerRadius(3f)
            if (isMe) rrect(full, r, r, r, tail) else rrect(full, r, r, tail, r)
        }

        // 9. RC GOOGLE ASSISTAN: pill with 4-corner balanced curve
        BubbleStyleId.rcGoogleAssistan -> uniform(full, 20f)

        // 10. Gabi Squa: geometric rounded square
        BubbleStyleId.gabiSqua -> uniform(full, 8f)

        // 11. Gabi Dot & Gabi Dot 2 & Ilkhang
        BubbleStyleId.gabiDot, BubbleStyleId.gabiDot2, BubbleStyleId.ilkhang -> {
            val r = CornerRadius(14f)
            val flat = CornerRadius(2f)
            if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
        }

        // 12. Rc line: hairline frame
        BubbleStyleId.rcLine, BubbleStyleId.aranbor -> uniform(full, 4f)

        // 13. Roundle: organic circle pill
        BubbleStyleId.roundle, BubbleStyleId.gabyRon ->
            uniform(full, min(24f, height / 2))

        // 14. Stock / Whatsapp LB / Telegram: classic messaging tail
        BubbleStyleId.stock, BubbleStyleId.whatsappLb,
        BubbleStyleId.telegram, BubbleStyleId.waChatOn -> Path().apply {
            if (isMe) {
                moveTo(left + 14, top)
                lineTo(right - 14, top)
                cornerArc(Rect(right - 28, top, right, top + 28), -90f)
                lineTo(right, bottom - 10)
                lineTo(right + 4, bottom)
                lineTo(right - 10, bottom)
                lineTo(left + 14, bottom)
                cornerArc(Rect(left, bottom - 28, left + 28, bottom), 90f)
                lineTo(left, top + 14)
                cornerArc(Rect(left, top, left + 28, top + 28), 180f)
            } else {
                moveTo(left + 14, top)
                lineTo(right - 14, top)
                cornerArc(Rect(right - 28, top, right, top + 28), -90f)
                lineTo(right, bottom - 14)
                cornerArc(Rect(right - 28, bottom - 28, right, bottom), 0f)
                lineTo(left + 10, bottom)
                lineTo(left - 4, bottom)
                lineTo(left, bottom - 10)
                lineTo(left, top + 14)
                cornerArc(Rect(left, top, left + 28, top + 28), 180f)
            }
            close()
        }

        // 15. Facebook Messenger / Twitter / Plus Messenger ED
        BubbleStyleId.facebookMessenger, BubbleStyleId.twitter,
        BubbleStyleId.plusMessengerEd -> uniform(full, 18f)

        // 16. Old Hangouts / MD / Samyadeep Crayon Alt
        BubbleStyleId.oldHangouts, BubbleStyleId.newHangouts4,
        BubbleStyleId.doodleHang, BubbleStyleId.samyadeepCrayonAlt -> {
            val r = CornerRadius(12f)
            if (isMe) rrect(full, r, CornerRadius.Zero, r, r)
            else rrect(full, CornerRadius.Zero, r, r, r)
        }

        // 17. Materialized / Rounded / MD
        BubbleStyleId.materialized, BubbleStyleId.rounded, BubbleStyleId.md -> {
            val r = CornerRadius(16f)
            val flat = CornerRadius(4f)
            if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
        }

        // 18. WA+ Paper Redesigned / Fold / Fold v2 (folded corner origami)
        BubbleStyleId.waPaperRedesigned, BubbleStyleId.fold, BubbleStyleId.foldV2 ->
            Path().apply {
                if (isMe) {
                    moveTo(left + 12, top)
                    lineTo(right - 12, top)
                    lineTo(right, top + 12)
                    lineTo(right, bottom - 12)
                    cornerArc(Rect(right - 24, bottom - 24, right, bottom), 0f)
                    lineTo(left + 12, bottom)
                    cornerArc(Rect(left, bottom - 24, left + 24, bottom), 90f)
                    lineTo(left, top + 12)
                    cornerArc(Rect(left, top, left + 24, top + 24), 180f)
                } else {
                    moveTo(left + 12, top + 12)
                    lineTo(left, top)
                    lineTo(right - 12, top)
                    cornerArc(Rect(right - 24, top, right, top + 24), -90f)
                    lineTo(right, bottom - 12)
                    cornerArc(Rect(right - 24, bottom - 24, right, bottom), 0f)
                    lineTo(left + 12, bottom)
                    cornerArc(Rect(left, bottom - 24, left + 24, bottom), 90f)
                }
                close()
            }

        // 19. Transparent: clean rounded rect with stroke
        BubbleStyleId.transparent, BubbleStyleId.inBubble, BubbleStyleId.bryed ->
            uniform(full, 14f)

        // 20. BBM / Hike / Win: retro chat bubble
        BubbleStyleId.bbm, BubbleStyleId.hike, BubbleStyleId.win -> {
            val r = CornerRadius(8f)
            if (isMe) rrect(full, r, r, r, CornerRadius.Zero)
            else rrect(full, r, r, CornerRadius.Zero, r)
        }

        // 21. Bubble Drop / Samyadeep Dual / Popzup / Mood / Line
        BubbleStyleId.bubbleDrop, BubbleStyleId.samyadeepDual, BubbleStyleId.popzup,
        BubbleStyleId.line, BubbleStyleId.mood -> {
            val r = CornerRadius(20f)
            val flat = CornerRadius(6f)
            if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
        }

        // Textra SMS: capsule with a centered stem tail at the bottom.
        BubbleStyleId.textra -> uniform(full, min(24f, height / 2))

        // Go SMS / Rc Go SMS: rounded bubble with curved tail sweeping out.
        BubbleStyleId.goSms, BubbleStyleId.rcGoSms -> {
            val r = CornerRadius(min(16f, height / 2))
            val flat = CornerRadius(4f)
            val base = if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
            base.apply {
                if (isMe) {
                    moveTo(right - 10, bottom - 8)
                    quadraticBezierTo(right + 8, bottom - 2, right + 12, bottom - 14)
                    quadraticBezierTo(right + 2, bottom - 16, right - 2, bottom - 16)
                } else {
                    moveTo(left + 10, bottom - 8)
                    quadraticBezierTo(left - 8, bottom - 2, left - 12, bottom - 14)
                    quadraticBezierTo(left - 2, bottom - 16, left + 2, bottom - 16)
                }
                close()
            }
        }

        // CV ios: iOS-like rounded bubble with a small swept tail.
        BubbleStyleId.cvIos -> {
            val r = CornerRadius(min(18f, height / 2))
            val flat = CornerRadius(4f)
            if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
        }

        // Rc Pal / Rc Imess: dome (semicircular top), flat sides and bottom.
        BubbleStyleId.rcPal, BubbleStyleId.rcImess -> Path().apply {
            val domeR = min(size.width / 2, 26f)
            val cx = size.width / 2
            moveTo(left, bottom)
            lineTo(left, top + domeR)
            lineTo(cx - domeR, top + domeR)
            arcTo(Rect(cx - domeR, top, cx + domeR, top + domeR * 2), 180f, 180f, false)
            lineTo(right, top + domeR)
            lineTo(right, bottom)
            close()
        }

        // Rc ios Line: rounded-rect outline with a corner tail.
        BubbleStyleId.rcIosLine -> {
            val r = CornerRadius(min(18f, height / 2))
            val flat = CornerRadius(6f)
            val base = if (isMe) rrect(full, r, r, r, flat) else rrect(full, r, r, flat, r)
            base.apply {
                if (isMe) {
                    moveTo(right - 4, bottom - 12)
                    quadraticBezierTo(right + 8, bottom - 2, right + 2, bottom + 6)
                    quadraticBezierTo(right - 4, bottom - 2, right - 12, bottom - 4)
                } else {
                    moveTo(left + 4, bottom - 12)
                    quadraticBezierTo(left - 8, bottom - 2, left - 2, bottom + 6)
                    quadraticBezierTo(left + 4, bottom - 2, left + 12, bottom - 4)
                }
                close()
            }
        }

        // Rc IM Line / Rc tran Line: open top semicircle ring with flat base.
        BubbleStyleId.rcImLine, BubbleStyleId.rcTranLine -> Path().apply {
            val domeR = min(size.width / 2, if (styleId == BubbleStyleId.rcImLine) 22f else 24f)
            val cx = size.width / 2
            moveTo(left, bottom)
            lineTo(left, top + domeR)
            lineTo(cx - domeR, top + domeR)
            arcTo(Rect(cx - domeR, top, cx + domeR, top + domeR * 2), 180f, 180f, false)
            lineTo(right, top + domeR)
            lineTo(right, bottom)
            close()
        }

        // Gabi Mess: boxy outline bubble with a rounded top and corner tail.
        BubbleStyleId.gabiMess -> {
            val r = CornerRadius(20f)
            val flat = CornerRadius(4f)
            val base = rrect(full, r, r, flat, flat)
            base.apply {
                if (isMe) {
                    moveTo(right - 8, bottom - 6)
                    quadraticBezierTo(right + 10, bottom - 4, right + 14, bottom - 18)
                    quadraticBezierTo(right + 4, bottom - 14, right - 4, bottom - 14)
                } else {
                    moveTo(left + 8, bottom - 6)
                    quadraticBezierTo(left - 10, bottom - 4, left - 14, bottom - 18)
                    quadraticBezierTo(left - 4, bottom - 14, left + 4, bottom - 14)
                }
                close()
            }
        }

        // Rc Fancy: pinched-waist double-bulge silhouette.
        BubbleStyleId.rcFancy -> Path().apply {
            moveTo(left + 14, top)
            quadraticBezierTo(left + 2, top + height * 0.22f, left + 10, top + height * 0.34f)
            quadraticBezierTo(left - 6, height * 0.5f, left + 10, top + height * 0.66f)
            quadraticBezierTo(left + 2, bottom - height * 0.22f, left + 14, bottom)
            lineTo(right - 14, bottom)
            quadraticBezierTo(right - 2, bottom - height * 0.22f, right - 10, top + height * 0.66f)
            quadraticBezierTo(right + 6, height * 0.5f, right - 10, top + height * 0.34f)
            quadraticBezierTo(right - 2, top + height * 0.22f, right - 14, top)
            close()
        }
    }
}

/** Port of BubblePainter.paint: shadow, fill, optional outline, accents. */
fun DrawScope.drawBubble(
    styleId: BubbleStyleId,
    isMe: Boolean,
    fillColor: Color,
    strokeColor: Color,
    accentColor: Color,
) {
    val geometry = BubbleStyleRegistry.getGeometry(styleId)
    val path = bubblePath(styleId, size, isMe)

    if (geometry.hasDropShadow) {
        drawIntoCanvas { canvas ->
            val frameworkPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(38, 0, 0, 0) // black @ 0.15
                maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
            }
            val shadowPath = android.graphics.Path(path.asAndroidPath())
            shadowPath.offset(0f, 2f)
            canvas.nativeCanvas.drawPath(shadowPath, frameworkPaint)
        }
    }

    drawPath(path, fillColor)

    if (geometry.isOutlined) {
        drawPath(path, strokeColor, style = Stroke(width = geometry.strokeWidth.toFloat()))
    }

    drawBubbleAccents(styleId, isMe, fillColor, strokeColor, accentColor)
}

private fun DrawScope.drawBubbleAccents(
    styleId: BubbleStyleId,
    isMe: Boolean,
    fillColor: Color,
    strokeColor: Color,
    accentColor: Color,
) {
    val left = 0f
    val top = 0f
    val right = size.width
    val bottom = size.height

    when (styleId) {
        // Kitty: subtle cute ear outline at the top corner.
        BubbleStyleId.kitty -> {
            val earPaint = accentColor.copy(alpha = 0.7f)
            val ear = Path().apply {
                if (isMe) {
                    moveTo(right - 28, top)
                    lineTo(right - 22, top - 5)
                    lineTo(right - 16, top)
                } else {
                    moveTo(left + 16, top)
                    lineTo(left + 22, top - 5)
                    lineTo(left + 28, top)
                }
                close()
            }
            drawPath(ear, earPaint)
        }

        // Amor: small pink heart crest.
        BubbleStyleId.amor -> {
            val center = if (isMe) Offset(right - 14, top + 4) else Offset(left + 14, top + 4)
            drawCircle(Color(0xD9FF4081), radius = 3f, center = center)
        }

        // Gabi Dot & Gabi Dot 2 & Ilkhang: satellite dot at the corner.
        BubbleStyleId.gabiDot, BubbleStyleId.gabiDot2, BubbleStyleId.ilkhang -> {
            val dotCenter = if (isMe) Offset(right + 2, top + 4) else Offset(left - 2, top + 4)
            drawCircle(accentColor, radius = 3.5f, center = dotCenter)
        }

        // Fold / Fold v2 / WA+ Paper: folded paper corner triangle.
        BubbleStyleId.fold, BubbleStyleId.foldV2, BubbleStyleId.waPaperRedesigned -> {
            val fold = Path().apply {
                if (isMe) {
                    moveTo(right - 12, top)
                    lineTo(right, top + 12)
                    lineTo(right - 12, top + 12)
                } else {
                    moveTo(left + 12, top)
                    lineTo(left, top + 12)
                    lineTo(left + 12, top + 12)
                }
                close()
            }
            drawPath(fold, Color.Black.copy(alpha = 0.15f))
        }

        // Textra SMS: stem tail below the capsule.
        BubbleStyleId.textra -> {
            val cx = if (isMe) right - 26 else left + 26
            val stem = Path().apply {
                moveTo(cx - 4, bottom - 6)
                lineTo(cx - 4, bottom + 4)
                quadraticBezierTo(cx, bottom + 7, cx + 4, bottom + 4)
                lineTo(cx + 4, bottom - 6)
                close()
            }
            drawPath(stem, fillColor)
        }

        // Gabi Mess: double accent line across the top.
        BubbleStyleId.gabiMess -> {
            drawLine(
                color = strokeColor,
                start = Offset(left + 8, top + 4),
                end = Offset(right - 8, top + 4),
                strokeWidth = 1.2f,
            )
        }

        else -> Unit
    }
}
