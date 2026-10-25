package com.example.chat.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact port of lib/features/camera/lens/lens_overlay.dart.
 *
 *  Renders a lens at its SOLVED facial anchor: every element is positioned by
 *  tracked geometry and rotates/scales with the face (requirements 18-21).
 *  Lenses fade rather than freeze when tracking drops (requirement 25).
 *
 *  One painter implementation serves both the live Compose preview and the
 *  still-capture bake ([composeLensIntoImage]), exactly like the Dart
 *  _LensAnchorPainter serves CustomPaint and the PictureRecorder. The painter
 *  targets androidx.compose.ui.graphics.Canvas; the bake path wraps an
 *  android.graphics.Canvas.
 *
 *  INTEGRATION NOTE (blur glow, implemented): Paint.setMaskFilter is never
 *  hardware-accelerated (official Android hardware-acceleration drawing
 *  table), and Compose's Paint exposes no public framework-paint access in
 *  ui-graphics-android 1.7.6 (asFrameworkPaint is not a public top-level in
 *  the shipped AAR - confirmed against the artifact and by the real CI
 *  compiler). Both the live preview and the still-capture bake therefore
 *  paint their three blurred glows from ONE path: the glow sprite cache,
 *  which renders each shape ONCE per (shape, color) onto a Bitmap-backed
 *  software android.graphics.Canvas where android.graphics.BlurMaskFilter
 *  renders exactly, then draws the bitmap under the same face transform.
 *  API 24+ safe: bitmaps, LinkedHashMap LRU and the framework mask filter
 *  only - no RenderEffect/API-31 path. */

@Composable
fun LensOverlay(
    lens: LensDefinition,
    poses: List<FacePose>,
    health: TrackingHealth,
    opacity: Float = 1.0f,
) {
    if (lens.isNoLens || poses.isEmpty() || health == TrackingHealth.lost) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {}
        return
    }
    val effectiveOpacity =
        opacity * (if (health == TrackingHealth.temporarilyLost) 0.55f else 1.0f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            LensAnchorPainter.paint(canvas, lens, poses, effectiveOpacity)
        }
    }
}

/** Port of composeLensIntoImage: bakes the active lens onto a captured still.
 *  [poses] are in the live, cover-cropped preview coordinate space; they are
 *  inverse-mapped onto the output image before painting.
 *  Returns PNG bytes (the caller then JPEG-compresses like the Dart). */
suspend fun composeLensIntoImage(
    sourceBytes: ByteArray,
    previewSize: LensSize,
    lens: LensDefinition,
    poses: List<FacePose>,
): ByteArray = withContext(Dispatchers.Default) {
    // instantiateImageCodec(targetWidth: 2048) -> downsample decode.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, opts)
        ?: throw IllegalStateException("decode failed")
    val image = if (decoded.width > 2048) {
        val h = (decoded.height.toLong() * 2048 / decoded.width).toInt()
        Bitmap.createScaledBitmap(decoded, 2048, h, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } else decoded

    try {
        val imageW = image.width.toFloat()
        val imageH = image.height.toFloat()
        val scale = max(previewSize.width / imageW, previewSize.height / imageH)
        val cropLeft = (previewSize.width - imageW * scale) / 2
        val cropTop = (previewSize.height - imageH * scale) / 2

        fun toImage(p: LensOffset) = LensOffset(
            (p.dx - cropLeft) / scale,
            (p.dy - cropTop) / scale,
        )

        val mappedPoses = poses.map { pose ->
            FacePose(
                trackingId = pose.trackingId,
                headBounds = LensRect.fromPoints(
                    toImage(LensOffset(pose.headBounds.left, pose.headBounds.top)),
                    toImage(LensOffset(pose.headBounds.right, pose.headBounds.bottom)),
                ),
                leftEye = toImage(pose.leftEye),
                rightEye = toImage(pose.rightEye),
                noseBase = toImage(pose.noseBase),
                mouthCenter = toImage(pose.mouthCenter),
                rollRadians = pose.rollRadians,
                yawRadians = pose.yawRadians,
                pitchRadians = pose.pitchRadians,
                confidence = pose.confidence,
            )
        }

        val rendered = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val androidCanvas = android.graphics.Canvas(rendered)
        androidCanvas.drawBitmap(image, 0f, 0f, null)
        val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
        LensAnchorPainter.paint(composeCanvas, lens, mappedPoses, 1.0f)

        val out = ByteArrayOutputStream()
        if (!rendered.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            throw IllegalStateException("Lens image encoding failed.")
        }
        rendered.recycle()
        out.toByteArray()
    } finally {
        image.recycle()
    }
}

/** Port of _LensAnchorPainter. Art authored against a 100px reference unit. */
object LensAnchorPainter {

    fun paint(
        canvas: androidx.compose.ui.graphics.Canvas,
        lens: LensDefinition,
        poses: List<FacePose>,
        opacity: Float,
    ) {
        // Per-lens multi-face capability (requirement 26): each tracked face
        // receives an independent transform - never everything on face #0.
        for (pose in poses.take(max(1, lens.maxFaces))) {
            canvas.save()
            if (lens.id == "lens_hearts_float") {
                paintHearts(canvas, lens, pose, opacity)
            } else {
                when (lens.anchor) {
                    LensAnchor.eyes ->
                        if (lens.id == "lens_sunglasses_aviator") paintAviators(canvas, lens, pose, opacity)
                        else paintGlasses(canvas, pose, opacity)
                    LensAnchor.forehead -> when (lens.id) {
                        "lens_angel_halo" -> paintAngelHalo(canvas, lens, pose, opacity)
                        "lens_sparkles" -> paintSparkles(canvas, lens, pose, opacity)
                        "lens_neon_crown" -> paintNeonCrown(canvas, lens, pose, opacity)
                        else -> paintCrown(canvas, pose, opacity)
                    }
                    LensAnchor.ears -> {
                        paintEars(canvas, pose, opacity)
                        if (lens.id == "lens_dog_ears") paintDogNose(canvas, pose, opacity)
                    }
                    LensAnchor.mouth -> paintMouthGlow(canvas, pose, opacity)
                    LensAnchor.noseBridge, LensAnchor.noseTip -> paintNoseDot(canvas, pose, opacity)
                    LensAnchor.fullHead -> Unit
                }
            }
            canvas.restore()
        }
    }

    /** Common transform: origin at [origin], rotated by head roll, scaled so
     *  the accessory grows/shrinks with the face (art reference = 100px). */
    private inline fun withFaceTransform(
        canvas: androidx.compose.ui.graphics.Canvas,
        pose: FacePose,
        origin: Offset,
        unit: Float,
        draw: () -> Unit,
    ) {
        canvas.translate(origin.x, origin.y)
        canvas.rotate(Math.toDegrees(pose.rollRadians).toFloat())
        canvas.scale(unit / 100.0f, unit / 100.0f)
        draw()
    }

    // ---- Glow sprites (live preview AND still-capture bake) -------------
    // Paint.setMaskFilter is never hardware-accelerated (official Android
    // hardware-acceleration drawing-support table), so the Dart blur glows
    // cannot be painted directly on the live Compose canvas. Each blurred
    // shape is baked ONCE per (shape, color) into a small software bitmap -
    // a canvas where BlurMaskFilter renders exactly - and the sprite is then
    // drawn under the same face transform, so the blur scales with the head
    // exactly like Skia's CTM-respecting mask filter does in Flutter.

    private const val GLOW_SPRITE_SCALE = 2f

    /** Total pixel-memory bound for the sprite cache (bytes, ARGB_8888).
     *  Entry counts do not bound memory because sprite sizes vary with the
     *  blurred shape; ~4 MB covers the full lens set with headroom. */
    private const val GLOW_SPRITE_MAX_BYTES = 4 * 1024 * 1024

    private var glowSpriteBytes = 0L

    // Evicted bitmaps are NOT recycled manually: a hardware display list
    // recorded for a previous frame may still reference the bitmap, and
    // drawing a recycled bitmap crashes. Dropping the reference and letting
    // the GC reclaim it once the display list releases it is the safe path.
    private val glowSpriteCache = LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true)

    /** Framework BlurMaskFilter radius for a Dart blur sigma:
     *  sigma = 0.57735 * radius + 0.5 (frameworks/base libs/hwui/jni/
     *  MaskFilter.cpp createBlur), so radius = (sigma - 0.5) / 0.57735,
     *  matching Skia's own SkBlurMask::ConvertSigmaToRadius. */
    private fun blurRadiusForSigma(sigma: Float): Float = (sigma - 0.5f) / 0.57735f

    /** Draws a blurred shape via its cached software-baked sprite. [l],[t],
     *  [r],[b] are the shape bounds in local (transformed) units INCLUDING
     *  stroke; [alpha] carries the per-frame opacity (the sprite is baked at
     *  the Dart base alpha, so the product matches the Dart color exactly).
     *  [bake] receives the software canvas and an anti-aliased framework
     *  paint with the blur mask filter already set. */
    private fun drawBlurredSprite(
        canvas: androidx.compose.ui.graphics.Canvas,
        key: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        blurSigma: Float,
        alpha: Float,
        baseColor: Int,
        strokeWidth: Float,
        fill: Boolean,
        bake: (android.graphics.Canvas, android.graphics.Paint) -> Unit,
    ) {
        val margin = 3f * blurRadiusForSigma(blurSigma) + 2f
        val sprite = synchronized(glowSpriteCache) {
            val hit = glowSpriteCache[key]
            if (hit != null) {
                hit
            } else {
                val w = ((r - l + 2 * margin) * GLOW_SPRITE_SCALE).roundToInt().coerceAtLeast(1)
                val h = ((b - t + 2 * margin) * GLOW_SPRITE_SCALE).roundToInt().coerceAtLeast(1)
                val bmp = android.graphics.Bitmap.createBitmap(
                    w, h, android.graphics.Bitmap.Config.ARGB_8888,
                )
                val ac = android.graphics.Canvas(bmp)
                ac.scale(GLOW_SPRITE_SCALE, GLOW_SPRITE_SCALE)
                ac.translate(margin - l, margin - t)
                val ap = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = baseColor
                    style = if (fill) android.graphics.Paint.Style.FILL
                            else android.graphics.Paint.Style.STROKE
                    if (!fill) {
                        this.strokeWidth = strokeWidth
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    maskFilter = android.graphics.BlurMaskFilter(
                        blurRadiusForSigma(blurSigma),
                        android.graphics.BlurMaskFilter.Blur.NORMAL,
                    )
                }
                bake(ac, ap)
                glowSpriteCache[key] = bmp
                glowSpriteBytes += w.toLong() * h.toLong() * 4L
                while (glowSpriteBytes > GLOW_SPRITE_MAX_BYTES) {
                    val it = glowSpriteCache.entries.iterator()
                    if (!it.hasNext()) break
                    val eldest = it.next()
                    glowSpriteBytes -= eldest.value.width.toLong() * eldest.value.height.toLong() * 4L
                    it.remove() // reference drop only - see recycle note above
                }
                bmp
            }
        }
        // The sprite has GLOW_SPRITE_SCALE pixels per local unit; map it back
        // onto its local-units rect so the baked blur lands at the same size
        // the CTM-respecting mask filter produces in Flutter.
        canvas.drawImageRect(
            sprite.asImageBitmap(),
            androidx.compose.ui.unit.IntOffset(0, 0),
            androidx.compose.ui.unit.IntSize(sprite.width, sprite.height),
            Offset(l - margin, t - margin),
            androidx.compose.ui.geometry.Size(
                (r - l + 2 * margin),
                (b - t + 2 * margin),
            ),
            Paint().apply { this.alpha = alpha },
        )
    }

    private fun fillPaint(color: Color) = Paint().apply {
        this.color = color
        style = PaintingStyle.Fill
    }

    private fun strokePaint(color: Color, width: Float) = Paint().apply {
        this.color = color
        style = PaintingStyle.Stroke
        strokeWidth = width
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
    }

    private fun paintAviators(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val mid = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 1.08f
        withFaceTransform(canvas, pose, mid, unit) {
            val fill = fillPaint(Color(0xFF0F172A).copy(alpha = 0.90f * opacity))
            val rim = strokePaint(lens.accentColor.copy(alpha = opacity), 5f)
            val leftLens = Path().apply {
                moveTo(-91f, -22f); quadraticTo(-50f, -34f, -15f, -17f)
                lineTo(-25f, 20f); quadraticTo(-48f, 42f, -70f, 22f); close()
            }
            val rightLens = Path().apply {
                moveTo(15f, -17f); quadraticTo(50f, -34f, 91f, -22f)
                lineTo(70f, 22f); quadraticTo(48f, 42f, 25f, 20f); close()
            }
            canvas.drawPath(leftLens, fill)
            canvas.drawPath(rightLens, fill)
            canvas.drawPath(leftLens, rim)
            canvas.drawPath(rightLens, rim)
            canvas.drawLine(Offset(-16f, -16f), Offset(16f, -16f), rim)
            canvas.drawLine(Offset(-90f, -20f), Offset(-108f, -26f), rim)
            canvas.drawLine(Offset(90f, -20f), Offset(108f, -26f), rim)
        }
    }

    private fun paintNeonCrown(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = Offset(
            pose.headBounds.center.dx,
            pose.headBounds.top + pose.headBounds.height * 0.08f,
        )
        val unit = pose.headBounds.width * 0.72f
        withFaceTransform(canvas, pose, origin, unit) {
            val line = strokePaint(lens.accentColor.copy(alpha = opacity), 4f)
            val path = Path().apply {
                moveTo(-60f, 20f); lineTo(-45f, -22f); lineTo(-20f, 2f); lineTo(0f, -40f)
                lineTo(20f, 2f); lineTo(45f, -22f); lineTo(60f, 20f)
            }
            // Dart lens_overlay.dart:233 - stroke 11, blur sigma 7.
            drawBlurredSprite(
                canvas, "crown:" + lens.accentColor.value.toString(),
                -65.5f, -45.5f, 65.5f, 25.5f, 7f, opacity,
                lens.accentColor.copy(alpha = 0.45f).value.toInt(), 11f, false,
            ) { ac, ap -> ac.drawPath(path.asAndroidPath(), ap) }
            canvas.drawPath(path, line)
            canvas.drawLine(Offset(-60f, 20f), Offset(60f, 20f), line)
        }
    }

    private fun paintAngelHalo(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = Offset(
            pose.headBounds.center.dx,
            pose.headBounds.top + pose.headBounds.height * 0.05f,
        )
        val unit = pose.headBounds.width * 0.68f
        withFaceTransform(canvas, pose, origin, unit) {
            val halo = strokePaint(lens.accentColor.copy(alpha = opacity), 5f)
            val oval = Rect(Offset(0f, -28f), 56f, 12f)
            // Dart lens_overlay.dart:265 - stroke 12, blur sigma 8.
            drawBlurredSprite(
                canvas, "halo:" + lens.accentColor.value.toString(),
                -62f, -46f, 62f, -10f, 8f, opacity,
                lens.accentColor.copy(alpha = 0.45f).value.toInt(), 12f, false,
            ) { ac, ap -> ac.drawOval(android.graphics.RectF(-56f, -40f, 56f, -16f), ap) }
            canvas.drawOval(oval, halo)
            for (x in listOf(-42f, 0f, 42f)) {
                canvas.drawCircle(
                    Offset(x, -29f),
                    if (x == 0f) 3.5f else 2.2f,
                    fillPaint(Color.White.copy(alpha = opacity)),
                )
            }
        }
    }

    private fun paintSparkles(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 2.15f
        withFaceTransform(canvas, pose, origin, unit) {
            fun star(center: Offset, radius: Float, alpha: Float) {
                val path = Path()
                for (i in 0 until 8) {
                    val angle = -Math.PI / 2 + i * Math.PI / 4
                    val r = if (i % 2 == 0) radius else radius * 0.28f
                    val x = center.x + (cos(angle) * r).toFloat()
                    val y = center.y + (sin(angle) * r).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, fillPaint(lens.accentColor.copy(alpha = alpha * opacity)))
            }
            star(Offset(-70f, -26f), 17f, 1.0f)
            star(Offset(74f, -34f), 13f, 0.9f)
            star(Offset(-48f, 42f), 9f, 0.75f)
            star(Offset(53f, 37f), 8f, 0.8f)
        }
    }

    private fun paintHearts(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = Offset(
            pose.headBounds.center.dx,
            pose.headBounds.top + pose.headBounds.height * 0.18f,
        )
        val unit = pose.headBounds.width * 0.9f
        withFaceTransform(canvas, pose, origin, unit) {
            fun heart(center: Offset, scale: Float, alpha: Float) {
                val path = Path().apply {
                    moveTo(center.x, center.y + 24 * scale)
                    cubicTo(
                        center.x - 40 * scale, center.y - 1 * scale,
                        center.x - 26 * scale, center.y - 25 * scale,
                        center.x, center.y - 10 * scale,
                    )
                    cubicTo(
                        center.x + 26 * scale, center.y - 25 * scale,
                        center.x + 40 * scale, center.y - 1 * scale,
                        center.x, center.y + 24 * scale,
                    )
                    close()
                }
                canvas.drawPath(path, fillPaint(lens.accentColor.copy(alpha = alpha * opacity)))
            }
            heart(Offset(-48f, -6f), 0.40f, 0.90f)
            heart(Offset(44f, -28f), 0.30f, 0.78f)
            heart(Offset(78f, 36f), 0.22f, 0.66f)
        }
    }

    private fun paintDogNose(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.noseBase.dx, pose.noseBase.dy), pose.interocular * 1.75f) {
            val outer = fillPaint(Color(0xFF1F2937).copy(alpha = 0.95f * opacity))
            val inner = strokePaint(Color(0xFF111827).copy(alpha = opacity), 2f)
            canvas.drawOval(Rect(Offset.Zero, 16f, 11f), outer)
            canvas.drawLine(Offset(0f, 9f), Offset(0f, 17f), inner)
            canvas.drawArc(Rect(Offset(-5f, 17f), 5f, 3.5f), 0f, 180f, false, inner)
            canvas.drawArc(Rect(Offset(5f, 17f), 5f, 3.5f), 0f, 180f, false, inner)
        }
    }

    private fun paintGlasses(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val mid = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 1.08f
        withFaceTransform(canvas, pose, mid, unit) {
            val frame = strokePaint(Color.Black.copy(alpha = 0.85f * opacity), 6f)
            val bridge = strokePaint(Color.Black.copy(alpha = 0.85f * opacity), 6f)
            canvas.drawCircle(Offset(-50f, 0f), 34f, frame)
            canvas.drawCircle(Offset(50f, 0f), 34f, frame)
            canvas.drawLine(Offset(-16f, 0f), Offset(16f, 0f), bridge)
            // Temples reach toward the ears so rotation reads naturally.
            canvas.drawLine(Offset(-84f, 0f), Offset(-110f, -6f), bridge)
            canvas.drawLine(Offset(84f, 0f), Offset(110f, -6f), bridge)
        }
    }

    private fun paintCrown(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val origin = Offset(
            pose.headBounds.center.dx,
            pose.headBounds.top + pose.headBounds.height * 0.08f,
        )
        val unit = pose.headBounds.width * 0.72f
        withFaceTransform(canvas, pose, origin, unit) {
            val paint = fillPaint(Color(0xFFFBBF24).copy(alpha = opacity))
            val path = Path().apply {
                moveTo(-60f, 20f); lineTo(-45f, -22f); lineTo(-20f, 2f); lineTo(0f, -40f)
                lineTo(20f, 2f); lineTo(45f, -22f); lineTo(60f, 20f); close()
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun paintEars(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        // Ear anchor derives from head bounds width and yaw offset so turning
        // the head slides the ears with it rather than leaving them behind.
        val yawShift =
            pose.headBounds.width * 0.10f * sin(pose.yawRadians).toFloat().coerceIn(-1.0f, 1.0f)
        val origin = Offset(
            pose.headBounds.center.dx + yawShift,
            pose.headBounds.top + pose.headBounds.height * 0.12f,
        )
        val unit = pose.headBounds.width * 0.9f
        withFaceTransform(canvas, pose, origin, unit) {
            val outer = fillPaint(Color(0xFF8D5B4C).copy(alpha = opacity))
            val inner = fillPaint(Color(0xFFFBB6CE).copy(alpha = opacity))
            for (side in listOf(-1f, 1f)) {
                canvas.save()
                canvas.translate(side * 52, -6f)
                canvas.rotate(Math.toDegrees((side * 0.28f).toDouble()).toFloat())
                canvas.drawOval(Rect(Offset.Zero, 17f, 29f), outer)
                canvas.drawOval(Rect(Offset.Zero, 11f, 19f), inner)
                canvas.restore()
            }
        }
    }

    private fun paintMouthGlow(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.mouthCenter.dx, pose.mouthCenter.dy), pose.interocular * 1.6f) {
            // Dart lens_overlay.dart:473 - fill, blur sigma 18.
            drawBlurredSprite(
                canvas, "mouth", -26f, -26f, 26f, 26f, 18f, opacity,
                Color(0xFFF472B6).copy(alpha = 0.30f).value.toInt(), 0f, true,
            ) { ac, ap -> ac.drawCircle(0f, 0f, 26f, ap) }
            canvas.drawCircle(Offset.Zero, 8f, fillPaint(Color.White.copy(alpha = 0.35f * opacity)))
        }
    }

    private fun paintNoseDot(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.noseBase.dx, pose.noseBase.dy), pose.interocular * 0.8f) {
            canvas.drawCircle(Offset.Zero, 9f, fillPaint(Color.White.copy(alpha = 0.5f * opacity)))
        }
    }

    /** Rect from center + half extents (Dart Rect.fromCenter). */
    private fun Rect(center: Offset, halfWidth: Float, halfHeight: Float) =
        Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}
