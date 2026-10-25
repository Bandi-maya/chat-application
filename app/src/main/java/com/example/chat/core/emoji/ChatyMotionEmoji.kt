package com.example.chat.core.emoji

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compose port of lib/core/emoji/chaty_motion_emoji.dart (674 lines).
 * High-performance, offline-first vector animated emoji engine: 10 genuinely
 * distinct emoji visual styles with procedural vector rendering and zero
 * external network or asset dependencies.
 *
 * Renders on the Dart 64x64 logical canvas, scaled to [size]. Motion math and
 * gradient geometry live in the pure JVM [ChatyEmojiMotion] (unit-tested).
 * Flutter radians are converted to Compose degrees via ChatyEmojiMotion.radToDeg.
 *
 * VERIFICATION LIMIT: compile-gated on owner CI; no runtime pixels verified.
 */
@Composable
fun ChatyMotionEmoji(
    unicode: String,
    size: Dp = 64.dp,
    animate: Boolean = true,
    autoStart: Boolean = true,
    style: ChatyEmojiStyleId = ChatyEmojiStyleId.chatyMotion,
    onCompleted: (() -> Unit)? = null,
) {
    // Dart: AnimationController(duration: 1400ms), forward(from: 0) when
    // animate && autoStart; didUpdateWidget restarts/stops on animate change.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animate, autoStart) {
        if (animate && autoStart) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = 1400, easing = LinearEasing))
            onCompleted?.invoke()
        } else if (!animate) {
            progress.stop()
        }
    }

    val clean = ChatyEmojiMotion.cleanUnicode(unicode)
    Canvas(Modifier.size(size)) {
        val scale = this.size.width / ChatyEmojiMotion.LOGICAL_CANVAS.toFloat()
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            ChatyEmojiPainter.draw(this, clean, if (animate) progress.value else 0f, style)
        }
    }
}

/** Identifies if this Unicode emoji has a dedicated vector rendering pipeline. */
fun chatyMotionEmojiIsSupported(unicode: String): Boolean = ChatyEmojiMotion.isSupported(unicode)

/** The painter, ported method-for-method from _ChatyMotionEmojiPainter. */
object ChatyEmojiPainter {

    fun draw(scope: DrawScope, unicode: String, t: Float, style: ChatyEmojiStyleId) {
        when (unicode) {
            "😂", "🤣" -> paintJoy(scope, t, style)
            "❤️", "💖" -> paintHeart(scope, t, style)
            "🔥" -> paintFire(scope, t, style)
            "👍" -> paintThumbsUp(scope, t, style)
            "🎉", "🥳" -> paintCelebration(scope, t, style)
            "🤔" -> paintThinking(scope, t, style)
            "😮", "😲" -> paintSurprise(scope, t, style)
            "😢", "😭" -> paintSad(scope, t, style)
            "👋" -> paintWaving(scope, t, style)
            else -> paintJoy(scope, t, style)
        }
    }

    // =========================================================================
    // 1. JOY / LAUGHING WITH TEARS (😂) ACROSS 10 DISTINCT STYLES
    // =========================================================================

    private fun paintJoy(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val nod = ChatyEmojiMotion.joyNod(t.toDouble()).toFloat()
        val center = Offset(32f, 32f + nod)

        when (style) {
            ChatyEmojiStyleId.chatyMotion -> {
                // Original Chaty Motion: vibrant yellow with animated spurting teardrops
                scope.drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)
                drawJoyEyes(scope, center, Color(0xFF664500), 3.2f)
                drawJoyMouth(scope, center, Color(0xFF664500), Color(0xFFFF5252))
                drawTears(scope, center, t, Color(0xFF5DADEC), false)
            }
            ChatyEmojiStyleId.chatyInk -> {
                // Original Chaty Ink: 3.0px bold manga black ink contour + comic hatching
                scope.drawCircle(Color(0xFFFFFDE7), radius = 25.5f, center = center)
                scope.drawCircle(
                    Color(0xFF1A1A1A), radius = 25.5f, center = center,
                    style = Stroke(width = 3.2f, cap = StrokeCap.Round),
                )
                drawJoyEyes(scope, center, Color(0xFF1A1A1A), 3.8f)
                // Comic cheek blush hatching
                val hatch = Stroke(width = 1.4f)
                val hatchColor = Color(0xFFE53935)
                scope.drawLine(hatchColor, Offset(center.x - 18, center.y + 1), Offset(center.x - 14, center.y + 5), strokeWidth = hatch.width)
                scope.drawLine(hatchColor, Offset(center.x - 21, center.y + 1), Offset(center.x - 17, center.y + 5), strokeWidth = hatch.width)
                scope.drawLine(hatchColor, Offset(center.x + 14, center.y + 1), Offset(center.x + 18, center.y + 5), strokeWidth = hatch.width)
                scope.drawLine(hatchColor, Offset(center.x + 17, center.y + 1), Offset(center.x + 21, center.y + 5), strokeWidth = hatch.width)
                drawJoyMouth(scope, center, Color(0xFF1A1A1A), Color(0xFFFF1744))
                drawTears(scope, center, t, Color(0xFF29B6F6), true)
            }
            ChatyEmojiStyleId.chatySoft3D -> {
                // Original Chaty Soft 3D: multi-stop radial gradient sphere + specular highlight pill
                val rect = ChatyEmojiMotion.rectFromCircle(center.x.toDouble(), center.y.toDouble(), 26.0)
                val (gx, gy) = ChatyEmojiMotion.radialCenter(rect[0], rect[1], rect[2], rect[3], -0.35, -0.4)
                val radiusPx = ChatyEmojiMotion.radialRadius(0.9, 52.0)
                val grad = Brush.radialGradient(
                    listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFA000), Color(0xFFE65100)),
                    center = Offset(gx.toFloat(), gy.toFloat()),
                    radius = radiusPx.toFloat(),
                )
                scope.drawCircle(grad, radius = 26f, center = center)
                // Specular highlight bubble
                scope.drawOval(
                    Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(center.x - 14, center.y - 20),
                    size = Size(14f, 8f),
                )
                drawJoyEyes(scope, center, Color(0xFF4E342E), 3.4f)
                drawJoyMouth(scope, center, Color(0xFF3E2723), Color(0xFFFF5252))
                drawTears(scope, center, t, Color(0xFF00B0FF), false)
            }
            ChatyEmojiStyleId.chatyPixel -> {
                // Original Chaty Pixel: authentic 8-bit stepped pixel grid
                drawPixelJoyFace(scope, center, t)
            }
            ChatyEmojiStyleId.openMoji -> {
                // OpenMoji (CC BY-SA 4.0): 2.8px solid black contour enclosing flat unshaded canary yellow
                scope.drawCircle(Color(0xFFFCEA2B), radius = 25f, center = center)
                scope.drawCircle(Color.Black, radius = 25f, center = center, style = Stroke(width = 2.8f))
                drawJoyEyes(scope, center, Color.Black, 3.0f)
                drawJoyMouth(scope, center, Color.Black, Color(0xFFEA5A47))
                drawTears(scope, center, t, Color(0xFF1E88E5), true)
            }
            ChatyEmojiStyleId.twemoji -> {
                // Twemoji (CC BY 4.0): pure flat Twitter yellow, pill capsule eyes, clean horizontal smile
                scope.drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)
                drawJoyEyes(scope, center, Color(0xFF664500), 3.2f)
                drawJoyMouth(scope, center, Color(0xFF664500), Color(0xFFDD2E44))
                drawTears(scope, center, t, Color(0xFF5DADEC), false)
            }
            ChatyEmojiStyleId.notoColor -> {
                // Noto Color (SIL OFL 1.1): Google Material soft vertical linear gradient
                val rect = ChatyEmojiMotion.rectFromCircle(center.x.toDouble(), center.y.toDouble(), 26.0)
                val (sx, sy) = ChatyEmojiMotion.linearTopCenter(rect[0], rect[1], rect[2])
                val (ex, ey) = ChatyEmojiMotion.linearBottomCenter(rect[0], rect[1], rect[2], rect[3])
                val grad = Brush.linearGradient(
                    listOf(Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFFA000)),
                    start = Offset(sx.toFloat(), sy.toFloat()),
                    end = Offset(ex.toFloat(), ey.toFloat()),
                )
                scope.drawCircle(grad, radius = 26f, center = center)
                drawJoyEyes(scope, center, Color(0xFF42210B), 3.0f)
                drawJoyMouth(scope, center, Color(0xFF42210B), Color(0xFFD32F2F))
                drawTears(scope, center, t, Color(0xFF42A5F5), false)
            }
            ChatyEmojiStyleId.fluentColor -> {
                // Fluent Color (MIT): Microsoft Fluent soft 3D dimensional gradient with peach cheek blush
                val rect = ChatyEmojiMotion.rectFromCircle(center.x.toDouble(), center.y.toDouble(), 26.0)
                val (gx, gy) = ChatyEmojiMotion.radialCenter(rect[0], rect[1], rect[2], rect[3], -0.2, -0.3)
                val radiusPx = ChatyEmojiMotion.radialRadius(0.95, 52.0)
                val grad = Brush.radialGradient(
                    listOf(Color(0xFFFFE082), Color(0xFFFFB74D), Color(0xFFFB8C00)),
                    center = Offset(gx.toFloat(), gy.toFloat()),
                    radius = radiusPx.toFloat(),
                )
                scope.drawCircle(grad, radius = 26f, center = center)
                // Peach cheek blush
                val blush = Color(0xFFFF7043).copy(alpha = 0.35f)
                scope.drawCircle(blush, radius = 5.5f, center = Offset(center.x - 16, center.y + 3))
                scope.drawCircle(blush, radius = 5.5f, center = Offset(center.x + 16, center.y + 3))
                drawJoyEyes(scope, center, Color(0xFF3E2723), 3.2f)
                drawJoyMouth(scope, center, Color(0xFF3E2723), Color(0xFFFF5252))
                drawTears(scope, center, t, Color(0xFF29B6F6), false)
            }
            ChatyEmojiStyleId.blobmoji -> {
                // Blobmoji (Apache 2.0 / OFL 1.1): Classic Android asymmetric teardrop jelly-blob silhouette
                val blobPath = Path().apply {
                    moveTo(center.x - 23, center.y + 12)
                    cubicTo(center.x - 27, center.y + 25, center.x + 27, center.y + 25, center.x + 23, center.y + 12)
                    cubicTo(center.x + 23, center.y - 12, center.x + 14, center.y - 24, center.x, center.y - 22)
                    cubicTo(center.x - 14, center.y - 24, center.x - 23, center.y - 12, center.x - 23, center.y + 12)
                    close()
                }
                val rect = ChatyEmojiMotion.rectFromCircle(center.x.toDouble(), center.y.toDouble(), 26.0)
                val (sx, sy) = ChatyEmojiMotion.linearTopCenter(rect[0], rect[1], rect[2])
                val (ex, ey) = ChatyEmojiMotion.linearBottomCenter(rect[0], rect[1], rect[2], rect[3])
                val grad = Brush.linearGradient(
                    listOf(Color(0xFFFFF176), Color(0xFFFFCA28)),
                    start = Offset(sx.toFloat(), sy.toFloat()),
                    end = Offset(ex.toFloat(), ey.toFloat()),
                )
                scope.drawPath(blobPath, grad)
                drawJoyEyes(scope, Offset(center.x, center.y - 3), Color(0xFF4527A0), 3.4f)
                drawJoyMouth(scope, Offset(center.x, center.y - 2), Color(0xFF4527A0), Color(0xFFE91E63))
                drawTears(scope, center, t, Color(0xFF00E5FF), false)
            }
            ChatyEmojiStyleId.emojiTwo -> {
                // EmojiTwo (CC BY 4.0): Classic open-source illustrated emoji with top crescent glossy rim
                val rect = ChatyEmojiMotion.rectFromCircle(center.x.toDouble(), center.y.toDouble(), 26.0)
                val (gx, gy) = ChatyEmojiMotion.radialCenter(rect[0], rect[1], rect[2], rect[3], 0.0, -0.2)
                val radiusPx = ChatyEmojiMotion.radialRadius(0.5, 52.0) // Dart default radius 0.5
                val grad = Brush.radialGradient(
                    listOf(Color(0xFFFFD54F), Color(0xFFFF9800)),
                    center = Offset(gx.toFloat(), gy.toFloat()),
                    radius = radiusPx.toFloat(),
                )
                scope.drawCircle(grad, radius = 26f, center = center)
                // Top crescent gloss: addArc(Rect.fromCircle((cx, cy-4), 24), -2.4, 1.6) radians
                val gloss = Path().apply {
                    addArc(
                        oval = Rect(center = Offset(center.x, center.y - 4), radius = 24f),
                        startAngle = ChatyEmojiMotion.radToDeg(-2.4).toFloat(),
                        sweepAngle = ChatyEmojiMotion.radToDeg(1.6).toFloat(),
                    )
                }
                scope.drawPath(gloss, Color.White.copy(alpha = 0.45f), style = Stroke(width = 3.0f))
                drawJoyEyes(scope, center, Color(0xFF42210B), 3.2f)
                drawJoyMouth(scope, center, Color(0xFF42210B), Color(0xFFD81B60))
                drawTears(scope, center, t, Color(0xFF03A9F4), false)
            }
        }
    }

    private fun drawJoyEyes(scope: DrawScope, center: Offset, color: Color, strokeWidth: Float) {
        val eyeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        val leftEye = Path().apply {
            moveTo(center.x - 16, center.y - 6)
            quadraticBezierTo(center.x - 10, center.y - 12, center.x - 4, center.y - 6)
        }
        scope.drawPath(leftEye, color, style = eyeStyle)

        val rightEye = Path().apply {
            moveTo(center.x + 4, center.y - 6)
            quadraticBezierTo(center.x + 10, center.y - 12, center.x + 16, center.y - 6)
        }
        scope.drawPath(rightEye, color, style = eyeStyle)
    }

    private fun drawJoyMouth(scope: DrawScope, center: Offset, strokeColor: Color, tongueColor: Color) {
        val mouthPath = Path().apply {
            moveTo(center.x - 15, center.y + 3)
            quadraticBezierTo(center.x, center.y + 23, center.x + 15, center.y + 3)
            close()
        }
        scope.drawPath(mouthPath, strokeColor)

        val tonguePath = Path().apply {
            moveTo(center.x - 8, center.y + 12)
            quadraticBezierTo(center.x, center.y + 22, center.x + 8, center.y + 12)
            close()
        }
        scope.drawPath(tonguePath, tongueColor)

        val teethPath = Path().apply {
            moveTo(center.x - 13.5f, center.y + 3)
            quadraticBezierTo(center.x, center.y + 9, center.x + 13.5f, center.y + 3)
            close()
        }
        scope.drawPath(teethPath, Color.White)
    }

    private fun drawTears(scope: DrawScope, center: Offset, t: Float, color: Color, outline: Boolean) {
        val phase = ChatyEmojiMotion.tearPhase(t.toDouble())
        val alpha = ChatyEmojiMotion.tearAlpha(phase).toFloat()
        val tearColor = color.copy(alpha = alpha)

        val (lx, ly) = ChatyEmojiMotion.tearLeftOffset(center.x.toDouble(), center.y.toDouble(), phase)
        val (rx, ry) = ChatyEmojiMotion.tearRightOffset(center.x.toDouble(), center.y.toDouble(), phase)

        scope.drawOval(tearColor, topLeft = Offset(lx.toFloat() - 3.5f, ly.toFloat() - 5.5f), size = Size(7f, 11f))
        scope.drawOval(tearColor, topLeft = Offset(rx.toFloat() - 3.5f, ry.toFloat() - 5.5f), size = Size(7f, 11f))

        if (outline) {
            val borderColor = Color.Black.copy(alpha = alpha)
            val borderStyle = Stroke(width = 1.4f)
            scope.drawOval(borderColor, topLeft = Offset(lx.toFloat() - 3.5f, ly.toFloat() - 5.5f), size = Size(7f, 11f), style = borderStyle)
            scope.drawOval(borderColor, topLeft = Offset(rx.toFloat() - 3.5f, ry.toFloat() - 5.5f), size = Size(7f, 11f), style = borderStyle)
        }
    }

    private fun drawPixelJoyFace(scope: DrawScope, center: Offset, t: Float) {
        val fill = Color(0xFFFFD700)
        val black = Color.Black
        val blue = Color(0xFF00B0FF)

        // 16x16 Pixel Face Matrix
        scope.drawRect(fill, topLeft = Offset(center.x - 22, center.y - 22), size = Size(44f, 44f))
        scope.drawRect(fill, topLeft = Offset(center.x - 24, center.y - 18), size = Size(48f, 36f))
        scope.drawRect(fill, topLeft = Offset(center.x - 18, center.y - 24), size = Size(36f, 48f))

        // Stepped eyes (2x2 pixel blocks)
        scope.drawRect(black, topLeft = Offset(center.x - 14, center.y - 8), size = Size(4f, 4f))
        scope.drawRect(black, topLeft = Offset(center.x - 10, center.y - 12), size = Size(4f, 4f))
        scope.drawRect(black, topLeft = Offset(center.x + 10, center.y - 8), size = Size(4f, 4f))
        scope.drawRect(black, topLeft = Offset(center.x + 6, center.y - 12), size = Size(4f, 4f))

        // Pixel smile
        scope.drawRect(black, topLeft = Offset(center.x - 12, center.y + 4), size = Size(24f, 8f))
        scope.drawRect(Color.White, topLeft = Offset(center.x - 8, center.y + 4), size = Size(16f, 4f))

        // Pixel tears
        val tearY = ChatyEmojiMotion.pixelTearY(center.y.toDouble(), t.toDouble()).toFloat()
        scope.drawRect(blue, topLeft = Offset(center.x - 18, tearY), size = Size(4f, 6f))
        scope.drawRect(blue, topLeft = Offset(center.x + 14, tearY), size = Size(4f, 6f))
    }

    // =========================================================================
    // 2. HEART (❤️) ACROSS DISTINCT STYLES
    // =========================================================================

    private fun paintHeart(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val scale = ChatyEmojiMotion.heartScale(t.toDouble()).toFloat()

        scope.withTransform({
            translate(32f, 32f)
            scale(scale, scale, pivot = Offset.Zero)
            translate(-32f, -32f)
        }) {
            val heartPath = Path().apply {
                moveTo(32f, 52f)
                cubicTo(12f, 38f, 4f, 25f, 12f, 13f)
                cubicTo(18f, 5f, 27f, 8f, 32f, 17f)
                cubicTo(37f, 8f, 46f, 5f, 52f, 13f)
                cubicTo(60f, 25f, 52f, 38f, 32f, 52f)
                close()
            }

            when (style) {
                ChatyEmojiStyleId.chatyPixel -> {
                    // 8-bit stepped pixel heart
                    val red = Color(0xFFFF0044)
                    drawRect(red, topLeft = Offset(18f, 14f), size = Size(10f, 8f))
                    drawRect(red, topLeft = Offset(36f, 14f), size = Size(10f, 8f))
                    drawRect(red, topLeft = Offset(14f, 22f), size = Size(36f, 10f))
                    drawRect(red, topLeft = Offset(18f, 32f), size = Size(28f, 8f))
                    drawRect(red, topLeft = Offset(24f, 40f), size = Size(16f, 6f))
                    drawRect(red, topLeft = Offset(28f, 46f), size = Size(8f, 5f))
                }
                ChatyEmojiStyleId.openMoji -> {
                    drawPath(heartPath, Color(0xFFEA5A47))
                    drawPath(heartPath, Color.Black, style = Stroke(width = 2.8f))
                }
                ChatyEmojiStyleId.chatyInk -> {
                    drawPath(heartPath, Color(0xFFFF1744))
                    drawPath(heartPath, Color(0xFF1A1A1A), style = Stroke(width = 3.2f))
                }
                ChatyEmojiStyleId.chatySoft3D -> {
                    val (gx, gy) = ChatyEmojiMotion.radialCenter(0.0, 0.0, 64.0, 64.0, -0.3, -0.4)
                    val radiusPx = ChatyEmojiMotion.radialRadius(0.9, 64.0)
                    val grad = Brush.radialGradient(
                        listOf(Color(0xFFFF5252), Color(0xFFD50000), Color(0xFF880E4F)),
                        center = Offset(gx.toFloat(), gy.toFloat()),
                        radius = radiusPx.toFloat(),
                    )
                    drawPath(heartPath, grad)
                    // Specular highlight bubble
                    drawOval(Color.White.copy(alpha = 0.45f), topLeft = Offset(18f, 14f), size = Size(10f, 6f))
                }
                else -> {
                    val (gx, gy) = ChatyEmojiMotion.radialCenter(0.0, 0.0, 64.0, 64.0, -0.2, -0.3)
                    val radiusPx = ChatyEmojiMotion.radialRadius(0.5, 64.0) // Dart default radius 0.5
                    val grad = Brush.radialGradient(
                        listOf(Color(0xFFFF2A55), Color(0xFFD6002A)),
                        center = Offset(gx.toFloat(), gy.toFloat()),
                        radius = radiusPx.toFloat(),
                    )
                    drawPath(heartPath, grad)
                }
            }
        }
    }

    // =========================================================================
    // 3. FIRE (🔥) ACROSS DISTINCT STYLES
    // =========================================================================

    private fun paintFire(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val flicker = ChatyEmojiMotion.fireFlicker(t.toDouble()).toFloat()

        val outerFlame = Path().apply {
            moveTo(32f, 56f)
            cubicTo(16f, 56f, 12f, 42f, 18f, 30f)
            cubicTo(20f, 25f, 26f, 26f, 24f, 16f)
            cubicTo(24f + flicker, 8f, 34f, 4f, 38f, 14f)
            cubicTo(42f, 24f, 48f, 22f, 50f, 32f)
            cubicTo(54f, 42f, 48f, 56f, 32f, 56f)
            close()
        }

        val innerFlame = Path().apply {
            moveTo(32f, 53f)
            cubicTo(22f, 53f, 20f, 44f, 24f, 36f)
            cubicTo(26f, 32f, 30f, 32f, 30f, 24f)
            cubicTo(32f, 18f, 36f, 22f, 38f, 28f)
            cubicTo(40f, 34f, 44f, 36f, 42f, 44f)
            cubicTo(40f, 52f, 38f, 53f, 32f, 53f)
            close()
        }

        if (style == ChatyEmojiStyleId.chatyPixel) {
            val red = Color(0xFFFF3D00)
            val yellow = Color(0xFFFFD600)
            scope.drawRect(red, topLeft = Offset(20f, 32f), size = Size(24f, 20f))
            scope.drawRect(red, topLeft = Offset(24f, 18f), size = Size(16f, 16f))
            scope.drawRect(red, topLeft = Offset(28f, 8f), size = Size(8f, 12f))
            scope.drawRect(yellow, topLeft = Offset(26f, 36f), size = Size(12f, 12f))
            scope.drawRect(yellow, topLeft = Offset(28f, 26f), size = Size(8f, 12f))
            return
        }

        scope.drawPath(outerFlame, Color(0xFFFF5722))
        scope.drawPath(innerFlame, Color(0xFFFFEB3B))

        if (style == ChatyEmojiStyleId.openMoji || style == ChatyEmojiStyleId.chatyInk) {
            val strokeW = if (style == ChatyEmojiStyleId.chatyInk) 3.0f else 2.4f
            scope.drawPath(outerFlame, Color.Black, style = Stroke(width = strokeW))
        }
    }

    // =========================================================================
    // 4. THUMBS UP (👍)
    // =========================================================================

    private fun paintThumbsUp(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val thumbBounce = ChatyEmojiMotion.thumbBounce(t.toDouble()).toFloat()

        scope.withTransform({
            translate(0f, thumbBounce)
        }) {
            val hand = Path().apply {
                moveTo(14f, 30f); lineTo(22f, 30f); lineTo(22f, 14f)
                quadraticBezierTo(22f, 8f, 27f, 8f)
                quadraticBezierTo(32f, 8f, 32f, 14f)
                lineTo(32f, 28f); lineTo(46f, 28f)
                quadraticBezierTo(52f, 28f, 52f, 34f)
                quadraticBezierTo(52f, 40f, 46f, 40f)
                quadraticBezierTo(50f, 43f, 46f, 46f)
                quadraticBezierTo(49f, 49f, 44f, 52f)
                lineTo(22f, 52f); lineTo(14f, 52f); close()
            }

            drawPath(hand, Color(0xFFFFCC4D))

            if (style == ChatyEmojiStyleId.openMoji || style == ChatyEmojiStyleId.chatyInk) {
                drawPath(hand, Color.Black, style = Stroke(width = 2.6f))
            } else {
                drawPath(hand, Color(0xFF664500).copy(alpha = 0.2f), style = Stroke(width = 1.4f))
            }
        }
    }

    // =========================================================================
    // 5. CELEBRATION (🎉)
    // =========================================================================

    private fun paintCelebration(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        // Party popper cone
        val cone = Path().apply {
            moveTo(14f, 50f); lineTo(28f, 36f); lineTo(14f, 22f); close()
        }
        scope.drawPath(cone, Color(0xFFFF9800))

        // Confetti particles
        val r = Color(0xFFE91E63)
        val b = Color(0xFF2196F3)
        val g = Color(0xFF4CAF50)
        val y = Color(0xFFFFEB3B)

        val burst = ChatyEmojiMotion.celebrationBurst(t.toDouble()).toFloat()
        scope.drawCircle(r, radius = 3.0f, center = Offset(28f + burst, 26f - burst))
        scope.drawCircle(b, radius = 2.6f, center = Offset(36f + burst, 32f - (burst * 0.5f)))
        scope.drawCircle(g, radius = 2.8f, center = Offset(32f + (burst * 0.8f), 42f + burst))
        scope.drawCircle(y, radius = 3.2f, center = Offset(44f + burst, 20f - burst))
    }

    // =========================================================================
    // 6. THINKING (🤔)
    // =========================================================================

    private fun paintThinking(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val center = Offset(32f, 28f)
        scope.drawCircle(Color(0xFFFFCC4D), radius = 22f, center = center)

        // One raised eyebrow
        val browColor = Color(0xFF664500)
        val browWidth = 2.4f
        scope.drawLine(browColor, Offset(center.x - 12, center.y - 12), Offset(center.x - 4, center.y - 10), strokeWidth = browWidth)
        scope.drawLine(browColor, Offset(center.x + 4, center.y - 14), Offset(center.x + 12, center.y - 10), strokeWidth = browWidth)

        // Eyes looking up-right
        scope.drawCircle(browColor, radius = 2.4f, center = Offset(center.x - 8, center.y - 4))
        scope.drawCircle(browColor, radius = 2.4f, center = Offset(center.x + 8, center.y - 4))

        // Skeptical smirk mouth
        scope.drawLine(browColor, Offset(center.x - 6, center.y + 8), Offset(center.x + 6, center.y + 6), strokeWidth = browWidth)

        // Hand on chin
        val hand = Path().apply {
            moveTo(24f, 48f); lineTo(36f, 48f); lineTo(36f, 40f); lineTo(32f, 34f); lineTo(28f, 40f); close()
        }
        scope.drawPath(hand, Color(0xFFFFB74D))
    }

    // =========================================================================
    // 7. SURPRISE (😮)
    // =========================================================================

    private fun paintSurprise(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val center = Offset(32f, 32f)
        scope.drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)

        // Round open eyes
        val eyeColor = Color(0xFF664500)
        scope.drawCircle(eyeColor, radius = 4.2f, center = Offset(center.x - 9, center.y - 6))
        scope.drawCircle(eyeColor, radius = 4.2f, center = Offset(center.x + 9, center.y - 6))
        scope.drawCircle(Color.White, radius = 1.2f, center = Offset(center.x - 7.5f, center.y - 7.5f))
        scope.drawCircle(Color.White, radius = 1.2f, center = Offset(center.x + 10.5f, center.y - 7.5f))

        // Big round open 'O' mouth
        val mouthScale = ChatyEmojiMotion.surpriseMouthScale(t.toDouble()).toFloat()
        scope.drawOval(
            eyeColor,
            topLeft = Offset(center.x - (13f * mouthScale) / 2f, center.y + 10f - (17f * mouthScale) / 2f),
            size = Size(13f * mouthScale, 17f * mouthScale),
        )
    }

    // =========================================================================
    // 8. SAD (😢)
    // =========================================================================

    private fun paintSad(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val center = Offset(32f, 32f)
        scope.drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)

        // Downcast curved eyes: drawArc(rect, 0, pi) -> degrees 0..180
        val eyeColor = Color(0xFF664500)
        val arcStyle = Stroke(width = 2.8f)
        scope.drawArc(
            eyeColor,
            startAngle = ChatyEmojiMotion.radToDeg(0.0).toFloat(),
            sweepAngle = ChatyEmojiMotion.radToDeg(Math.PI).toFloat(),
            useCenter = false,
            topLeft = Offset(center.x - 14, center.y - 8),
            size = Size(8f, 6f),
            style = arcStyle,
        )
        scope.drawArc(
            eyeColor,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x + 6, center.y - 8),
            size = Size(8f, 6f),
            style = arcStyle,
        )

        // Frowning mouth: drawArc(rect, pi, pi) -> start 180, sweep 180
        scope.drawArc(
            eyeColor,
            startAngle = ChatyEmojiMotion.radToDeg(Math.PI).toFloat(),
            sweepAngle = ChatyEmojiMotion.radToDeg(Math.PI).toFloat(),
            useCenter = false,
            topLeft = Offset(center.x - 8, center.y + 8),
            size = Size(16f, 12f),
            style = arcStyle,
        )

        // Single tear sliding down left cheek
        val tearY = ChatyEmojiMotion.sadTearY(center.y.toDouble(), t.toDouble()).toFloat()
        scope.drawOval(
            Color(0xFF29B6F6),
            topLeft = Offset(center.x - 9f - 2.75f, tearY - 4.25f),
            size = Size(5.5f, 8.5f),
        )
    }

    // =========================================================================
    // 9. WAVING HAND (👋)
    // =========================================================================

    private fun paintWaving(scope: DrawScope, t: Float, style: ChatyEmojiStyleId) {
        val waveAngleDeg = ChatyEmojiMotion.radToDeg(ChatyEmojiMotion.waveAngleRad(t.toDouble())).toFloat()

        scope.withTransform({
            translate(32f, 46f)
            rotate(waveAngleDeg, pivot = Offset.Zero)
            translate(-32f, -46f)
        }) {
            val hand = Path().apply {
                moveTo(22f, 46f)
                lineTo(22f, 28f) // Pinky
                lineTo(26f, 22f) // Ring
                lineTo(32f, 16f) // Middle
                lineTo(38f, 20f) // Index
                lineTo(44f, 30f) // Thumb
                quadraticBezierTo(48f, 40f, 38f, 48f)
                close()
            }

            drawPath(hand, Color(0xFFFFCC4D))
            drawPath(hand, Color(0xFF664500).copy(alpha = 0.25f), style = Stroke(width = 1.4f))
        }
    }
}
