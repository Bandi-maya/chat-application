package com.example.chat.ui.media.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/** Exact port of lib/features/media/ui/icons/chaty_media_icons.dart.
 *
 *  Hand-drawn Chaty media icon set (CustomPainter -> Compose Canvas).
 *  All coordinates are fractions of the icon size, exactly as in Dart;
 *  strokeWidth is logical (dp) matching Flutter's logical-pixel stroke.
 *  Default color: colorScheme.onSurface (Dart: Theme colorScheme.onSurface).
 *
 *  Verification: CI-only (Compose). Geometry transliterated 1:1.
 */
enum class ChatyIconType {
    audio, video, pdf, document, archive, folder,
    play, pause, seekForward, seekBackward, queue,
    volumeHigh, volumeMute, fullscreen, exitFullscreen, subtitles,
    search, convert, bookmark, rotateRight, pageExtract,
    merge, split, share, delete,
}

@Composable
fun ChatyMediaIcon(
    icon: ChatyIconType,
    size: Dp = 24.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Float = 2f,
) {
    val themeColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.dp.toPx() }
    val dpPx = with(density) { 1.dp.toPx() }
    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        fun rrect(l: Float, t: Float, rw: Float, rh: Float, r: Float, fill: Boolean = false) =
            drawRoundRect(
                color = themeColor,
                topLeft = Offset(w * l, h * t),
                size = Size(w * rw, h * rh),
                cornerRadius = CornerRadius(r * dpPx, r * dpPx),
                style = if (fill) Fill else stroke,
            )

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(themeColor, Offset(w * x1, h * y1), Offset(w * x2, h * y2),
                strokeWidth = strokePx, cap = StrokeCap.Round)

        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
            drawCircle(themeColor, radius = w * r, center = Offset(w * cx, h * cy),
                style = if (fill) Fill else stroke)

        fun arc(l: Float, t: Float, aw: Float, ah: Float, startRad: Float, sweepRad: Float) =
            drawArc(
                color = themeColor,
                startAngle = (startRad * 180f / PI).toFloat(),
                sweepAngle = (sweepRad * 180f / PI).toFloat(),
                useCenter = false,
                topLeft = Offset(w * l, h * t),
                size = Size(w * aw, h * ah),
                style = stroke,
            )

        fun path(fill: Boolean = false, block: Path.() -> Unit) {
            val p = Path().apply(block)
            drawPath(p, themeColor, style = if (fill) Fill else stroke)
        }

        fun Path.m(x: Float, y: Float) = moveTo(w * x, h * y)
        fun Path.l(x: Float, y: Float) = lineTo(w * x, h * y)

        when (icon) {
            ChatyIconType.audio -> {
                // Waveform bars with headphones arch
                path { arcTo(Rect(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.85f), 180f, 180f, true) }
                rrect(0.1f, 0.45f, 0.15f, 0.35f, 3f)
                rrect(0.75f, 0.45f, 0.15f, 0.35f, 3f)
                line(0.4f, 0.65f, 0.4f, 0.45f)
                line(0.5f, 0.72f, 0.5f, 0.38f)
                line(0.6f, 0.65f, 0.6f, 0.45f)
            }
            ChatyIconType.video -> {
                rrect(0.1f, 0.22f, 0.55f, 0.56f, 4f)
                path {
                    m(0.65f, 0.4f); l(0.88f, 0.25f); l(0.88f, 0.75f); l(0.65f, 0.6f); close()
                }
            }
            ChatyIconType.pdf -> {
                path {
                    m(0.2f, 0.12f); l(0.58f, 0.12f); l(0.8f, 0.34f); l(0.8f, 0.88f); l(0.2f, 0.88f); close()
                }
                line(0.58f, 0.12f, 0.58f, 0.34f)
                line(0.58f, 0.34f, 0.8f, 0.34f)
                circle(0.5f, 0.62f, 0.1f, fill = true)
            }
            ChatyIconType.document -> {
                rrect(0.2f, 0.12f, 0.6f, 0.76f, 4f)
                line(0.32f, 0.35f, 0.68f, 0.35f)
                line(0.32f, 0.52f, 0.68f, 0.52f)
                line(0.32f, 0.69f, 0.52f, 0.69f)
            }
            ChatyIconType.archive -> {
                rrect(0.15f, 0.2f, 0.7f, 0.65f, 4f)
                line(0.15f, 0.38f, 0.85f, 0.38f)
                rrect(0.42f, 0.48f, 0.16f, 0.16f, 2f)
            }
            ChatyIconType.folder -> {
                path {
                    m(0.12f, 0.25f); l(0.4f, 0.25f); l(0.5f, 0.35f); l(0.88f, 0.35f)
                    l(0.88f, 0.82f); l(0.12f, 0.82f); close()
                }
            }
            ChatyIconType.play -> {
                path(fill = true) { m(0.32f, 0.2f); l(0.78f, 0.5f); l(0.32f, 0.8f); close() }
            }
            ChatyIconType.pause -> {
                rrect(0.26f, 0.2f, 0.16f, 0.6f, 2f, fill = true)
                rrect(0.58f, 0.2f, 0.16f, 0.6f, 2f, fill = true)
            }
            ChatyIconType.seekForward -> {
                line(0.3f, 0.3f, 0.52f, 0.5f)
                line(0.52f, 0.5f, 0.3f, 0.7f)
                line(0.52f, 0.3f, 0.74f, 0.5f)
                line(0.74f, 0.5f, 0.52f, 0.7f)
            }
            ChatyIconType.seekBackward -> {
                line(0.48f, 0.3f, 0.26f, 0.5f)
                line(0.26f, 0.5f, 0.48f, 0.7f)
                line(0.7f, 0.3f, 0.48f, 0.5f)
                line(0.48f, 0.5f, 0.7f, 0.7f)
            }
            ChatyIconType.queue -> {
                line(0.15f, 0.25f, 0.85f, 0.25f)
                line(0.15f, 0.5f, 0.65f, 0.5f)
                line(0.15f, 0.75f, 0.55f, 0.75f)
                path(fill = true) { m(0.72f, 0.6f); l(0.88f, 0.7f); l(0.72f, 0.8f); close() }
            }
            ChatyIconType.volumeHigh -> {
                path {
                    m(0.15f, 0.38f); l(0.35f, 0.38f); l(0.55f, 0.2f); l(0.55f, 0.8f)
                    l(0.35f, 0.62f); l(0.15f, 0.62f); close()
                }
                arc(0.45f, 0.32f, 0.3f, 0.36f, (-PI / 3).toFloat(), (2 * PI / 3).toFloat())
                arc(0.45f, 0.2f, 0.5f, 0.6f, (-PI / 3).toFloat(), (2 * PI / 3).toFloat())
            }
            ChatyIconType.volumeMute -> {
                path {
                    m(0.15f, 0.38f); l(0.35f, 0.38f); l(0.55f, 0.2f); l(0.55f, 0.8f)
                    l(0.35f, 0.62f); l(0.15f, 0.62f); close()
                }
                line(0.68f, 0.38f, 0.88f, 0.62f)
                line(0.88f, 0.38f, 0.68f, 0.62f)
            }
            ChatyIconType.fullscreen -> {
                line(0.15f, 0.35f, 0.15f, 0.15f); line(0.15f, 0.15f, 0.35f, 0.15f)
                line(0.85f, 0.35f, 0.85f, 0.15f); line(0.85f, 0.15f, 0.65f, 0.15f)
                line(0.15f, 0.65f, 0.15f, 0.85f); line(0.15f, 0.85f, 0.35f, 0.85f)
                line(0.85f, 0.65f, 0.85f, 0.85f); line(0.85f, 0.85f, 0.65f, 0.85f)
            }
            ChatyIconType.exitFullscreen -> {
                line(0.35f, 0.15f, 0.35f, 0.35f); line(0.35f, 0.35f, 0.15f, 0.35f)
                line(0.65f, 0.15f, 0.65f, 0.35f); line(0.65f, 0.35f, 0.85f, 0.35f)
                line(0.35f, 0.85f, 0.35f, 0.65f); line(0.35f, 0.65f, 0.15f, 0.65f)
                line(0.65f, 0.85f, 0.65f, 0.65f); line(0.65f, 0.65f, 0.85f, 0.65f)
            }
            ChatyIconType.subtitles -> {
                rrect(0.12f, 0.22f, 0.76f, 0.56f, 4f)
                line(0.25f, 0.52f, 0.45f, 0.52f)
                line(0.55f, 0.52f, 0.75f, 0.52f)
                line(0.3f, 0.65f, 0.7f, 0.65f)
            }
            ChatyIconType.search -> {
                circle(0.42f, 0.42f, 0.25f)
                line(0.6f, 0.6f, 0.85f, 0.85f)
            }
            ChatyIconType.convert -> {
                arc(0.2f, 0.2f, 0.6f, 0.6f, 0f, (PI * 0.75).toFloat())
                arc(0.2f, 0.2f, 0.6f, 0.6f, PI.toFloat(), (PI * 0.75).toFloat())
                line(0.8f, 0.45f, 0.8f, 0.6f)
                line(0.8f, 0.6f, 0.65f, 0.6f)
                line(0.2f, 0.55f, 0.2f, 0.4f)
                line(0.2f, 0.4f, 0.35f, 0.4f)
            }
            ChatyIconType.bookmark -> {
                path {
                    m(0.25f, 0.15f); l(0.75f, 0.15f); l(0.75f, 0.85f); l(0.5f, 0.65f)
                    l(0.25f, 0.85f); close()
                }
            }
            ChatyIconType.rotateRight -> {
                arc(0.15f, 0.15f, 0.7f, 0.7f, (-PI / 2).toFloat(), (1.5 * PI).toFloat())
                line(0.5f, 0.15f, 0.65f, 0.15f)
                line(0.65f, 0.15f, 0.55f, 0.05f)
            }
            ChatyIconType.pageExtract -> {
                rrect(0.15f, 0.25f, 0.5f, 0.65f, 3f)
                line(0.5f, 0.4f, 0.85f, 0.4f)
                line(0.75f, 0.3f, 0.85f, 0.4f)
                line(0.75f, 0.5f, 0.85f, 0.4f)
            }
            ChatyIconType.merge -> {
                rrect(0.15f, 0.2f, 0.3f, 0.4f, 2f)
                rrect(0.55f, 0.2f, 0.3f, 0.4f, 2f)
                line(0.5f, 0.55f, 0.5f, 0.85f)
                line(0.4f, 0.75f, 0.5f, 0.85f)
                line(0.6f, 0.75f, 0.5f, 0.85f)
            }
            ChatyIconType.split -> {
                rrect(0.35f, 0.15f, 0.3f, 0.4f, 2f)
                line(0.4f, 0.65f, 0.25f, 0.85f)
                line(0.6f, 0.65f, 0.75f, 0.85f)
            }
            ChatyIconType.share -> {
                circle(0.75f, 0.25f, 0.08f)
                circle(0.25f, 0.5f, 0.08f)
                circle(0.75f, 0.75f, 0.08f)
                line(0.32f, 0.46f, 0.68f, 0.28f)
                line(0.32f, 0.54f, 0.68f, 0.72f)
            }
            ChatyIconType.delete -> {
                line(0.2f, 0.3f, 0.8f, 0.3f)
                line(0.4f, 0.2f, 0.6f, 0.2f)
                path { m(0.28f, 0.3f); l(0.32f, 0.82f); l(0.68f, 0.82f); l(0.72f, 0.3f) }
                line(0.42f, 0.42f, 0.42f, 0.7f)
                line(0.58f, 0.42f, 0.58f, 0.7f)
            }
        }
    }
}
