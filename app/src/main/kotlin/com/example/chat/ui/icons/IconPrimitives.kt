package com.example.chat.ui.icons

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.ceil

/** Platform-neutral mirror of the Dart dart:ui drawing subset used by chaty_icon_registry.dart.
 *  Painter code is a mechanical port; the Compose layer replays these onto a real Canvas. */
data class Offset(val dx: Double, val dy: Double)
fun Offset(x: Number, y: Number) = Offset(x.toDouble(), y.toDouble())

data class Rect(val left: Double, val top: Double, val width: Double, val height: Double) {
    val right get() = left + width
    val bottom get() = top + height
    companion object {
        fun fromLTWH(l: Number, t: Number, w: Number, h: Number) = Rect(l.toDouble(), t.toDouble(), w.toDouble(), h.toDouble())
        fun fromCenter(center: Offset, width: Number, height: Number) =
            Rect(center.dx - width.toDouble() / 2, center.dy - height.toDouble() / 2, width.toDouble(), height.toDouble())
    }
}

data class Radius(val x: Double) {
    companion object { fun circular(r: Number) = Radius(r.toDouble()) }
}

data class RRect(val rect: Rect, val radius: Radius) {
    companion object { fun fromRectAndRadius(rect: Rect, radius: Radius) = RRect(rect, radius) }
}

/** ARGB color; withValues(alpha) sets ABSOLUTE alpha like Dart Color.withValues. */
data class Color(val a: Double, val r: Double, val g: Double, val b: Double) {
    fun withValues(alpha: Double) = copy(a = alpha)
    fun toArgb(): Int =
        ((a * 255 + 0.5).toInt() shl 24) or ((r * 255 + 0.5).toInt() shl 16) or ((g * 255 + 0.5).toInt() shl 8) or (b * 255 + 0.5).toInt()
    companion object { fun fromArgb(v: Int) = Color(((v ushr 24) and 255) / 255.0, ((v shr 16) and 255) / 255.0, ((v shr 8) and 255) / 255.0, (v and 255) / 255.0) }
}

object Colors {
    val white = Color(1.0, 1.0, 1.0, 1.0)
    val red = Color.fromArgb(0xFFF44336.toInt())
    val redAccent = Color.fromArgb(0xFFFF5252.toInt())
    /** Flutter Colors.whiteNN are white at fixed alpha. */
    val white38 = Color.fromArgb(0x62FFFFFF)
    val white60 = Color.fromArgb(0x99FFFFFF.toInt())
    val white70 = Color.fromArgb(0xB3FFFFFF.toInt())
}

enum class PaintingStyle { fill, stroke }
enum class StrokeCap { butt, round, square }
enum class StrokeJoin { miter, round, bevel }

class Paint {
    var color: Color = Colors.white
    var style: PaintingStyle = PaintingStyle.fill
    var strokeWidth: Double = 0.0
    var strokeCap: StrokeCap = StrokeCap.butt
    var strokeJoin: StrokeJoin = StrokeJoin.miter
    var isAntiAlias: Boolean = true
}

sealed class PathOp {
    data class MoveTo(val x: Double, val y: Double) : PathOp()
    data class LineTo(val x: Double, val y: Double) : PathOp()
    data class QuadTo(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : PathOp()
    data class CubicTo(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x3: Double, val y3: Double) : PathOp()
    object Close : PathOp()
    data class AddOval(val rect: Rect) : PathOp()
    data class AddRRect(val rrect: RRect) : PathOp()
    data class AddArc(val rect: Rect, val start: Double, val sweep: Double) : PathOp()
}

class Path {
    val ops = ArrayList<PathOp>()
    private var cx = 0.0
    private var cy = 0.0
    private var sx = 0.0
    private var sy = 0.0

    fun moveTo(x: Number, y: Number) { cx = x.toDouble(); cy = y.toDouble(); sx = cx; sy = cy; ops += PathOp.MoveTo(cx, cy) }
    fun lineTo(x: Number, y: Number) { cx = x.toDouble(); cy = y.toDouble(); ops += PathOp.LineTo(cx, cy) }
    fun quadraticBezierTo(x1: Number, y1: Number, x2: Number, y2: Number) {
        ops += PathOp.QuadTo(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble()); cx = x2.toDouble(); cy = y2.toDouble()
    }
    fun cubicTo(x1: Number, y1: Number, x2: Number, y2: Number, x3: Number, y3: Number) {
        ops += PathOp.CubicTo(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble(), x3.toDouble(), y3.toDouble()); cx = x3.toDouble(); cy = y3.toDouble()
    }
    fun close() { ops += PathOp.Close; cx = sx; cy = sy }
    fun addOval(rect: Rect) { ops += PathOp.AddOval(rect) }
    fun addRRect(rrect: RRect) { ops += PathOp.AddRRect(rrect) }
    fun addArc(rect: Rect, startAngle: Double, sweepAngle: Double) { ops += PathOp.AddArc(rect, startAngle, sweepAngle) }

    /** Dart Path.arcToPoint (rotation 0, largeArc false): SVG endpoint arc converted to cubics. */
    fun arcToPoint(arcEnd: Offset, radius: Radius = Radius(0.0), clockwise: Boolean = true) {
        val x1 = cx; val y1 = cy; val x2 = arcEnd.dx; val y2 = arcEnd.dy
        var rx = abs(radius.x); var ry = abs(radius.x)
        if (rx == 0.0 || ry == 0.0 || (x1 == x2 && y1 == y2)) { lineTo(x2, y2); return }
        val dx2 = (x1 - x2) / 2; val dy2 = (y1 - y2) / 2
        val x1p = dx2; val y1p = dy2
        val lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lam > 1) { val s = sqrt(lam); rx *= s; ry *= s }
        val sweepFlag = clockwise
        val sign = if (sweepFlag) 1.0 else -1.0 // large-arc false: sign = (fA != fS) ? 1 : -1
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = sign * sqrt(max(0.0, num / den))
        val cxp = coef * (rx * y1p / ry)
        val cyp = coef * -(ry * x1p / rx)
        val ccx = cxp + (x1 + x2) / 2
        val ccy = cyp + (y1 + y2) / 2
        val th1 = atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
        var dth = atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - th1
        if (sweepFlag && dth < 0) dth += 2 * PI
        if (!sweepFlag && dth > 0) dth -= 2 * PI
        val segs = ceil(abs(dth) / (PI / 2) - 1e-9).toInt().coerceAtLeast(1)
        val step = dth / segs
        val t = 4.0 / 3.0 * kotlin.math.tan(step / 4)
        var a = th1
        for (i in 0 until segs) {
            val b = a + step
            val cosA = cos(a); val sinA = sin(a); val cosB = cos(b); val sinB = sin(b)
            val e1x = ccx + rx * (cosA - t * sinA); val e1y = ccy + ry * (sinA + t * cosA)
            val e2x = ccx + rx * (cosB + t * sinB); val e2y = ccy + ry * (sinB - t * cosB)
            val px = ccx + rx * cosB; val py = ccy + ry * sinB
            cubicTo(e1x, e1y, e2x, e2y, if (i == segs - 1) x2 else px, if (i == segs - 1) y2 else py)
            a = b
        }
    }
}

interface IconCanvas {
    fun drawPath(path: Path, paint: Paint)
    fun drawLine(p1: Offset, p2: Offset, paint: Paint)
    fun drawRect(rect: Rect, paint: Paint)
    fun drawRRect(rrect: RRect, paint: Paint)
    fun drawCircle(center: Offset, radius: Number, paint: Paint)
    fun drawOval(rect: Rect, paint: Paint)
    fun drawArc(rect: Rect, startAngle: Number, sweepAngle: Number, useCenter: Boolean, paint: Paint)
    fun save()
    fun restore()
    fun translate(dx: Number, dy: Number)
    fun scale(sx: Number, sy: Number)
    fun rotate(radians: Number)
}
