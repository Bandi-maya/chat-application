package com.example.chat.ui.chats.detail

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Port of ChatyEmojiStyleId (chaty_emoji_style.dart): 10 styles, the 4th
 *  Dart field is isAnimated. Titles kept verbatim for pref-string parity. */
enum class ChatyEmojiStyleId(
    val title: String,
    val description: String,
    val samplePreview: String,
    val isAnimated: Boolean,
) {
    chatyMotion("Chaty Motion", "Original expressive animated vector emoji with procedural physics and morphing expressions.", "✨ 😂 ❤️", true),
    chatyInk("Chaty Ink", "Original hand-drawn manga & calligraphic ink outlines with expressive comic character.", "✒️ 😜 💥", true),
    chatySoft3D("Chaty Soft 3D", "Original dimensional clay-morphism with multi-stop radial highlights and soft bevels.", "🧸 🤩 💖", true),
    chatyPixel("Chaty Pixel", "Original 8-bit stepped pixel grid with retro arcade block aesthetics.", "👾 🕹️ 🌟", false),
    openMoji("OpenMoji", "Verified CC BY-SA 4.0: 2px solid black contours with flat unshaded color fills.", "🙌 🌟 🎈", false),
    twemoji("Twemoji", "Verified CC BY 4.0: Modern flat vector silhouettes with signature capsule eyes.", "🎉 💯 🚀", false),
    notoColor("Noto Color", "Verified SIL OFL 1.1: Google Material soft linear gradients and circular geometry.", "😃 💖 👍", true),
    fluentColor("Fluent Color", "Verified MIT: Microsoft Fluent soft volumetric illumination and curved arches.", "🥳 😻 🔥", true),
    blobmoji("Blobmoji", "Verified Apache 2.0: Classic Android asymmetric teardrop jelly-blob silhouette.", "🍮 🫠 💫", true),
    emojiTwo("EmojiTwo", "Verified CC BY 4.0: Illustrated comic-book emoji with glossy rim highlights.", "😎 🍕 🏆", false),
    ;

    companion object {
        /** Exact port of fromString (chaty_emoji_style.dart:89-142): legacy
         *  aliases first, then name/title match, default chatyMotion. */
        fun fromString(value: String?): ChatyEmojiStyleId {
            if (value == null) return chatyMotion
            val normalized = value.trim().lowercase().replace(Regex("[\\s_\\-]+"), "")
            when (normalized) {
                "systemdefault", "system" -> return chatyMotion
                "twemojicolor", "twemoji" -> return twemoji
                "iosinspired", "ios" -> return chatySoft3D
                "oneuiinspired", "oneui" -> return notoColor
                "soft3d" -> return chatySoft3D
                "minimaloutline" -> return chatyInk
                "chatymotion", "motion" -> return chatyMotion
                "chatyink", "ink" -> return chatyInk
                "chatypixel", "pixel" -> return chatyPixel
                "openmoji" -> return openMoji
                "notocolor", "noto" -> return notoColor
                "fluentcolor", "fluent" -> return fluentColor
                "blobmoji", "blob" -> return blobmoji
                "emojitwo" -> return emojiTwo
            }
            for (style in entries) {
                val styleNorm = style.name.lowercase().replace(Regex("[\\s_\\-]+"), "")
                val titleNorm = style.title.lowercase().replace(Regex("[\\s_\\-]+"), "")
                if (normalized == styleNorm || normalized == titleNorm) return style
            }
            return chatyMotion
        }
    }
}

/** Port of ChatyMotionEmoji (chaty_motion_emoji.dart): 1400ms procedural
 *  vector animation, zero assets. All drawing happens in a 64x64 logical
 *  space scaled to the requested size, exactly like the Dart painter. */
@Composable
fun ChatyMotionEmoji(
    unicode: String,
    size: Float,
    animate: Boolean = true,
    autoStart: Boolean = true,
    style: ChatyEmojiStyleId = ChatyEmojiStyleId.chatyMotion,
    onCompleted: (() -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(animate, autoStart, unicode, style) {
        if (animate && autoStart) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 1400, easing = LinearEasing))
            onCompleted?.invoke()
        } else if (!animate) {
            progress.snapTo(0f)
        }
    }

    val clean = unicode.replace("\uFE0F", "").replace("\uFE0E", "").trim()
    Canvas(Modifier.size(size.dp)) {
        val scale = this.size.width / 64f
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            val t = if (animate) progress.value else 0f
            when (clean) {
                "😂", "🤣" -> paintJoy(t, style)
                "❤️", "❤", "💖" -> paintHeart(t, style)
                "🔥" -> paintFire(t, style)
                "👍" -> paintThumbsUp(t, style)
                "🎉", "🥳" -> paintCelebration(t)
                "🤔" -> paintThinking()
                "😮", "😲" -> paintSurprise(t)
                "😢", "😭" -> paintSad(t)
                "👋" -> paintWaving(t)
                else -> paintJoy(t, style)
            }
        }
    }

}

/** Exact port of ChatyMotionEmoji.isSupported (chaty_motion_emoji.dart:25-32). */
fun chatyMotionEmojiIsSupported(unicode: String): Boolean {
    val raw = unicode.trim()
    val clean = raw.replace("\uFE0F", "").replace("\uFE0E", "")
    val set = setOf(
        "😂", "🤣", "❤️", "❤", "😍", "💖", "🔥", "👍",
        "🎉", "🥳", "🤔", "😮", "😲", "😢", "😭", "👋",
    )
    return set.contains(clean) || set.contains(raw)
}

// =====================================================================
// Painter bodies: DrawScope extensions mirroring the Dart painter methods
// one-for-one. All coordinates are the Dart 64x64 logical space.
// =====================================================================

private fun DrawScope.paintJoy(t: Float, style: ChatyEmojiStyleId) {
    val nod = sin(t * PI.toFloat() * 4) * 2.2f
    val center = Offset(32f, 32f + nod)

    when (style) {
        ChatyEmojiStyleId.chatyMotion -> {
            drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)
            drawJoyEyes(center, Color(0xFF664500), 3.2f)
            drawJoyMouth(center, Color(0xFF664500), Color(0xFFFF5252))
            drawTears(center, t, Color(0xFF5DADEC), outline = false)
        }
        ChatyEmojiStyleId.chatyInk -> {
            drawCircle(Color(0xFFFFFDE7), radius = 25.5f, center = center)
            drawCircle(
                Color(0xFF1A1A1A), radius = 25.5f, center = center,
                style = Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            drawJoyEyes(center, Color(0xFF1A1A1A), 3.8f)
            // Comic cheek blush hatching
            val hatchColor = Color(0xFFE53935)
            drawLine(hatchColor, Offset(center.x - 18, center.y + 1), Offset(center.x - 14, center.y + 5), strokeWidth = 1.4f)
            drawLine(hatchColor, Offset(center.x - 21, center.y + 1), Offset(center.x - 17, center.y + 5), strokeWidth = 1.4f)
            drawLine(hatchColor, Offset(center.x + 14, center.y + 1), Offset(center.x + 18, center.y + 5), strokeWidth = 1.4f)
            drawLine(hatchColor, Offset(center.x + 17, center.y + 1), Offset(center.x + 21, center.y + 5), strokeWidth = 1.4f)
            drawJoyMouth(center, Color(0xFF1A1A1A), Color(0xFFFF1744))
            drawTears(center, t, Color(0xFF29B6F6), outline = true)
        }
        ChatyEmojiStyleId.chatySoft3D -> {
            // RadialGradient(center: Alignment(-0.35,-0.4), radius: 0.9) in
            // Rect.fromCircle(center, 26): center=(17.68,16.64), r=0.9*52/2=23.4
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFA000), Color(0xFFE65100)),
                    center = Offset(center.x + (-0.35f) * 26f, center.y + (-0.4f) * 26f),
                    radius = 0.9f * 26f,
                ),
                radius = 26f, center = center,
            )
            drawOval(
                Color.White.copy(alpha = 0.45f),
                topLeft = Offset(center.x - 14, center.y - 20),
                size = Size(14f, 8f),
            )
            drawJoyEyes(center, Color(0xFF4E342E), 3.4f)
            drawJoyMouth(center, Color(0xFF3E2723), Color(0xFFFF5252))
            drawTears(center, t, Color(0xFF00B0FF), outline = false)
        }
        ChatyEmojiStyleId.chatyPixel -> drawPixelJoyFace(center, t)
        ChatyEmojiStyleId.openMoji -> {
            drawCircle(Color(0xFFFCEA2B), radius = 25f, center = center)
            drawCircle(Color.Black, radius = 25f, center = center, style = Stroke(width = 2.8f))
            drawJoyEyes(center, Color.Black, 3.0f)
            drawJoyMouth(center, Color.Black, Color(0xFFEA5A47))
            drawTears(center, t, Color(0xFF1E88E5), outline = true)
        }
        ChatyEmojiStyleId.twemoji -> {
            drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)
            drawJoyEyes(center, Color(0xFF664500), 3.2f)
            drawJoyMouth(center, Color(0xFF664500), Color(0xFFDD2E44))
            drawTears(center, t, Color(0xFF5DADEC), outline = false)
        }
        ChatyEmojiStyleId.notoColor -> {
            drawCircle(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFFA000)),
                    start = Offset(center.x, center.y - 26f),
                    end = Offset(center.x, center.y + 26f),
                ),
                radius = 26f, center = center,
            )
            drawJoyEyes(center, Color(0xFF42210B), 3.0f)
            drawJoyMouth(center, Color(0xFF42210B), Color(0xFFD32F2F))
            drawTears(center, t, Color(0xFF42A5F5), outline = false)
        }
        ChatyEmojiStyleId.fluentColor -> {
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFE082), Color(0xFFFFB74D), Color(0xFFFB8C00)),
                    center = Offset(center.x + (-0.2f) * 26f, center.y + (-0.3f) * 26f),
                    radius = 0.95f * 26f,
                ),
                radius = 26f, center = center,
            )
            val blush = Color(0xFFFF7043).copy(alpha = 0.35f)
            drawCircle(blush, radius = 5.5f, center = Offset(center.x - 16, center.y + 3))
            drawCircle(blush, radius = 5.5f, center = Offset(center.x + 16, center.y + 3))
            drawJoyEyes(center, Color(0xFF3E2723), 3.2f)
            drawJoyMouth(center, Color(0xFF3E2723), Color(0xFFFF5252))
            drawTears(center, t, Color(0xFF29B6F6), outline = false)
        }
        ChatyEmojiStyleId.blobmoji -> {
            val blobPath = Path().apply {
                moveTo(center.x - 23, center.y + 12)
                cubicTo(center.x - 27, center.y + 25, center.x + 27, center.y + 25, center.x + 23, center.y + 12)
                cubicTo(center.x + 23, center.y - 12, center.x + 14, center.y - 24, center.x, center.y - 22)
                cubicTo(center.x - 14, center.y - 24, center.x - 23, center.y - 12, center.x - 23, center.y + 12)
                close()
            }
            drawPath(
                blobPath,
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFCA28)),
                    start = Offset(center.x, center.y - 26f),
                    end = Offset(center.x, center.y + 26f),
                ),
            )
            drawJoyEyes(Offset(center.x, center.y - 3), Color(0xFF4527A0), 3.4f)
            drawJoyMouth(Offset(center.x, center.y - 2), Color(0xFF4527A0), Color(0xFFE91E63))
            drawTears(center, t, Color(0xFF00E5FF), outline = false)
        }
        ChatyEmojiStyleId.emojiTwo -> {
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD54F), Color(0xFFFF9800)),
                    center = Offset(center.x + 0f * 26f, center.y + (-0.2f) * 26f),
                    radius = 0.5f * 26f,
                ),
                radius = 26f, center = center,
            )
            // Top crescent gloss: addArc(Rect.fromCircle(center-(0,4), 24), -2.4, 1.6 rad)
            val gloss = Path().apply {
                addArc(
                    oval = Rect(center = Offset(center.x, center.y - 4), radius = 24f),
                    startAngleDegrees = Math.toDegrees(-2.4).toFloat(),
                    sweepAngleDegrees = Math.toDegrees(1.6).toFloat(),
                )
            }
            drawPath(gloss, Color.White.copy(alpha = 0.45f), style = Stroke(width = 3.0f))
            drawJoyEyes(center, Color(0xFF42210B), 3.2f)
            drawJoyMouth(center, Color(0xFF42210B), Color(0xFFD81B60))
            drawTears(center, t, Color(0xFF03A9F4), outline = false)
        }
    }
}

private fun DrawScope.drawJoyEyes(center: Offset, color: Color, strokeWidth: Float) {
    val eyeStyle = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val leftEye = Path().apply {
        moveTo(center.x - 16, center.y - 6)
        quadraticTo(center.x - 10, center.y - 12, center.x - 4, center.y - 6)
    }
    drawPath(leftEye, color, style = eyeStyle)
    val rightEye = Path().apply {
        moveTo(center.x + 4, center.y - 6)
        quadraticTo(center.x + 10, center.y - 12, center.x + 16, center.y - 6)
    }
    drawPath(rightEye, color, style = eyeStyle)
}

private fun DrawScope.drawJoyMouth(center: Offset, strokeColor: Color, tongueColor: Color) {
    val mouthPath = Path().apply {
        moveTo(center.x - 15, center.y + 3)
        quadraticTo(center.x, center.y + 23, center.x + 15, center.y + 3)
        close()
    }
    drawPath(mouthPath, strokeColor)
    val tonguePath = Path().apply {
        moveTo(center.x - 8, center.y + 12)
        quadraticTo(center.x, center.y + 22, center.x + 8, center.y + 12)
        close()
    }
    drawPath(tonguePath, tongueColor)
    val teethPath = Path().apply {
        moveTo(center.x - 13.5f, center.y + 3)
        quadraticTo(center.x, center.y + 9, center.x + 13.5f, center.y + 3)
        close()
    }
    drawPath(teethPath, Color.White)
}

private fun DrawScope.drawTears(center: Offset, t: Float, color: Color, outline: Boolean) {
    val phase = (t * 2) % 1.0f
    val alpha = sin(phase * PI.toFloat()).coerceIn(0f, 1f)
    val tearColor = color.copy(alpha = alpha)

    val l = Offset(center.x - 22 - (phase * 4), center.y + 2 + (phase * 12))
    val r = Offset(center.x + 22 + (phase * 4), center.y + 2 + (phase * 12))
    drawOval(tearColor, topLeft = Offset(l.x - 3.5f, l.y - 5.5f), size = Size(7f, 11f))
    drawOval(tearColor, topLeft = Offset(r.x - 3.5f, r.y - 5.5f), size = Size(7f, 11f))

    if (outline) {
        val borderColor = Color.Black.copy(alpha = alpha)
        val borderStyle = Stroke(width = 1.4f)
        drawOval(borderColor, topLeft = Offset(l.x - 3.5f, l.y - 5.5f), size = Size(7f, 11f), style = borderStyle)
        drawOval(borderColor, topLeft = Offset(r.x - 3.5f, r.y - 5.5f), size = Size(7f, 11f), style = borderStyle)
    }
}

private fun DrawScope.drawPixelJoyFace(center: Offset, t: Float) {
    val fill = Color(0xFFFFD700)
    val black = Color.Black
    val blue = Color(0xFF00B0FF)

    // 16x16 Pixel Face Matrix
    drawRect(fill, topLeft = Offset(center.x - 22, center.y - 22), size = Size(44f, 44f))
    drawRect(fill, topLeft = Offset(center.x - 24, center.y - 18), size = Size(48f, 36f))
    drawRect(fill, topLeft = Offset(center.x - 18, center.y - 24), size = Size(36f, 48f))

    // Stepped eyes (2x2 pixel blocks)
    drawRect(black, topLeft = Offset(center.x - 14, center.y - 8), size = Size(4f, 4f))
    drawRect(black, topLeft = Offset(center.x - 10, center.y - 12), size = Size(4f, 4f))
    drawRect(black, topLeft = Offset(center.x + 10, center.y - 8), size = Size(4f, 4f))
    drawRect(black, topLeft = Offset(center.x + 6, center.y - 12), size = Size(4f, 4f))

    // Pixel smile
    drawRect(black, topLeft = Offset(center.x - 12, center.y + 4), size = Size(24f, 8f))
    drawRect(Color.White, topLeft = Offset(center.x - 8, center.y + 4), size = Size(16f, 4f))

    // Pixel tears
    val tearY = center.y - 2 + (t * 12)
    drawRect(blue, topLeft = Offset(center.x - 18, tearY), size = Size(4f, 6f))
    drawRect(blue, topLeft = Offset(center.x + 14, tearY), size = Size(4f, 6f))
}

private fun DrawScope.paintHeart(t: Float, style: ChatyEmojiStyleId) {
    val beat = (sin(t * PI.toFloat() * 6) * 0.12f).coerceIn(-0.15f, 0.15f)
    val scale = 1.0f + beat

    withTransform({
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
                // RadialGradient(center: Alignment(-0.3,-0.4), radius: 0.9) in
                // Rect(0,0,64,64): center=(22.4,19.2), radius=0.9*64/2=28.8
                drawPath(
                    heartPath,
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF5252), Color(0xFFD50000), Color(0xFF880E4F)),
                        center = Offset(32f + (-0.3f) * 32f, 32f + (-0.4f) * 32f),
                        radius = 0.9f * 32f,
                    ),
                )
                drawOval(
                    Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(18f, 14f),
                    size = Size(10f, 6f),
                )
            }
            else -> {
                // Dart default branch (chatyMotion, twemoji, notoColor,
                // fluentColor, blobmoji, emojiTwo all fall here for hearts).
                drawPath(
                    heartPath,
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF2A55), Color(0xFFD6002A)),
                        center = Offset(32f + (-0.2f) * 32f, 32f + (-0.3f) * 32f),
                        radius = 0.5f * 32f,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.paintFire(t: Float, style: ChatyEmojiStyleId) {
    val flicker = sin(t * PI.toFloat() * 8) * 1.8f

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
        drawRect(red, topLeft = Offset(20f, 32f), size = Size(24f, 20f))
        drawRect(red, topLeft = Offset(24f, 18f), size = Size(16f, 16f))
        drawRect(red, topLeft = Offset(28f, 8f), size = Size(8f, 12f))
        drawRect(yellow, topLeft = Offset(26f, 36f), size = Size(12f, 12f))
        drawRect(yellow, topLeft = Offset(28f, 26f), size = Size(8f, 12f))
        return
    }

    drawPath(outerFlame, Color(0xFFFF5722))
    drawPath(innerFlame, Color(0xFFFFEB3B))

    if (style == ChatyEmojiStyleId.openMoji || style == ChatyEmojiStyleId.chatyInk) {
        val strokeW = if (style == ChatyEmojiStyleId.chatyInk) 3.0f else 2.4f
        drawPath(outerFlame, Color.Black, style = Stroke(width = strokeW))
    }
}

private fun DrawScope.paintThumbsUp(t: Float, style: ChatyEmojiStyleId) {
    val thumbBounce = (sin(t * PI.toFloat() * 4) * 2.0f).coerceIn(-2.0f, 2.0f)

    withTransform({
        translate(0f, thumbBounce)
    }) {
        val hand = Path().apply {
            moveTo(14f, 30f); lineTo(22f, 30f); lineTo(22f, 14f)
            quadraticTo(22f, 8f, 27f, 8f)
            quadraticTo(32f, 8f, 32f, 14f)
            lineTo(32f, 28f); lineTo(46f, 28f)
            quadraticTo(52f, 28f, 52f, 34f)
            quadraticTo(52f, 40f, 46f, 40f)
            quadraticTo(50f, 43f, 46f, 46f)
            quadraticTo(49f, 49f, 44f, 52f)
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

private fun DrawScope.paintCelebration(t: Float) {
    // Party popper cone
    val cone = Path().apply {
        moveTo(14f, 50f); lineTo(28f, 36f); lineTo(14f, 22f); close()
    }
    drawPath(cone, Color(0xFFFF9800))

    // Confetti particles
    val burst = t * 14
    drawCircle(Color(0xFFE91E63), radius = 3.0f, center = Offset(28 + burst, 26 - burst))
    drawCircle(Color(0xFF2196F3), radius = 2.6f, center = Offset(36 + burst, 32 - (burst * 0.5f)))
    drawCircle(Color(0xFF4CAF50), radius = 2.8f, center = Offset(32 + (burst * 0.8f), 42 + burst))
    drawCircle(Color(0xFFFFEB3B), radius = 3.2f, center = Offset(44 + burst, 20 - burst))
}

private fun DrawScope.paintThinking() {
    val center = Offset(32f, 28f)
    drawCircle(Color(0xFFFFCC4D), radius = 22f, center = center)

    // One raised eyebrow
    val browColor = Color(0xFF664500)
    drawLine(browColor, Offset(center.x - 12, center.y - 12), Offset(center.x - 4, center.y - 10), strokeWidth = 2.4f)
    drawLine(browColor, Offset(center.x + 4, center.y - 14), Offset(center.x + 12, center.y - 10), strokeWidth = 2.4f)

    // Eyes looking up-right
    drawCircle(browColor, radius = 2.4f, center = Offset(center.x - 8, center.y - 4))
    drawCircle(browColor, radius = 2.4f, center = Offset(center.x + 8, center.y - 4))

    // Skeptical smirk mouth
    drawLine(browColor, Offset(center.x - 6, center.y + 8), Offset(center.x + 6, center.y + 6), strokeWidth = 2.4f)

    // Hand on chin
    val hand = Path().apply {
        moveTo(24f, 48f); lineTo(36f, 48f); lineTo(36f, 40f); lineTo(32f, 34f); lineTo(28f, 40f); close()
    }
    drawPath(hand, Color(0xFFFFB74D))
}

private fun DrawScope.paintSurprise(t: Float) {
    val center = Offset(32f, 32f)
    drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)

    // Round open eyes
    val eyeColor = Color(0xFF664500)
    drawCircle(eyeColor, radius = 4.2f, center = Offset(center.x - 9, center.y - 6))
    drawCircle(eyeColor, radius = 4.2f, center = Offset(center.x + 9, center.y - 6))
    drawCircle(Color.White, radius = 1.2f, center = Offset(center.x - 7.5f, center.y - 7.5f))
    drawCircle(Color.White, radius = 1.2f, center = Offset(center.x + 10.5f, center.y - 7.5f))

    // Big round open 'O' mouth
    val mouthScale = 1.0f + sin(t * PI.toFloat() * 4) * 0.15f
    drawOval(
        eyeColor,
        topLeft = Offset(center.x - (13 * mouthScale) / 2, center.y + 10 - (17 * mouthScale) / 2),
        size = Size(13 * mouthScale, 17 * mouthScale),
    )
}

private fun DrawScope.paintSad(t: Float) {
    val center = Offset(32f, 32f)
    drawCircle(Color(0xFFFFCC4D), radius = 26f, center = center)

    // Downcast curved eyes (Flutter drawArc radians -> Compose degrees)
    val eyeStyle = Stroke(width = 2.8f)
    val eyeColor = Color(0xFF664500)
    drawArc(eyeColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(center.x - 14, center.y - 8), size = Size(8f, 6f), style = eyeStyle)
    drawArc(eyeColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(center.x + 6, center.y - 8), size = Size(8f, 6f), style = eyeStyle)

    // Frowning mouth
    drawArc(eyeColor, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(center.x - 8, center.y + 8), size = Size(16f, 12f), style = eyeStyle)

    // Single tear sliding down left cheek
    val tearY = center.y - 2 + (t * 16)
    drawOval(Color(0xFF29B6F6), topLeft = Offset(center.x - 9 - 2.75f, tearY - 4.25f), size = Size(5.5f, 8.5f))
}

private fun DrawScope.paintWaving(t: Float) {
    val waveAngle = sin(t * PI.toFloat() * 6) * 0.35f

    withTransform({
        translate(32f, 46f)
        rotate(Math.toDegrees(waveAngle.toDouble()).toFloat(), pivot = Offset.Zero)
        translate(-32f, -46f)
    }) {
        val hand = Path().apply {
            moveTo(22f, 46f)
            lineTo(22f, 28f) // Pinky
            lineTo(26f, 22f) // Ring
            lineTo(32f, 16f) // Middle
            lineTo(38f, 20f) // Index
            lineTo(44f, 30f) // Thumb
            quadraticTo(48f, 40f, 38f, 48f)
            close()
        }
        drawPath(hand, Color(0xFFFFCC4D))
        drawPath(hand, Color(0xFF664500).copy(alpha = 0.25f), style = Stroke(width = 1.4f))
    }
}
