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
 *  Artwork: the original 10 built-in lenses have been REDESIGNED with richer
 *  procedural detail (gems, shines, cheek pairs, glow tips) and 21 new
 *  original Chaty lenses share the same painters. Every shape is drawn in
 *  code here - no image assets, no third-party lens content.
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

/** Port of _LensAnchorPainter. Art authored against a 100px reference unit.
 *
 *  Dispatch is id-keyed first (each catalog lens owns its painter) and falls
 *  back to the anchor-generic accessory for any lens id the painter does not
 *  know yet, so older builds and forward-compatible catalogs keep rendering
 *  something sensible at the right anchor. */
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
            when (lens.id) {
                // --- Original 10 built-ins (redesigned artwork) -------------
                "lens_hearts_float" -> paintHearts(canvas, lens, pose, opacity)
                "lens_sunglasses_aviator" -> paintAviators(canvas, lens, pose, opacity)
                "lens_glasses_classic" -> paintGlasses(canvas, pose, opacity)
                "lens_crown_gold" -> paintCrown(canvas, pose, opacity)
                "lens_neon_crown" -> paintNeonCrown(canvas, lens, pose, opacity)
                "lens_angel_halo" -> paintAngelHalo(canvas, lens, pose, opacity)
                "lens_sparkles" -> paintSparkles(canvas, lens, pose, opacity)
                "lens_dog_ears" -> {
                    paintEars(canvas, pose, opacity)
                    paintDogNose(canvas, pose, opacity)
                }
                "lens_blush_glow" -> paintMouthGlow(canvas, pose, opacity)
                // --- New original lenses ------------------------------------
                "lens_cat_ears" -> paintCatEars(canvas, pose, opacity)
                "lens_bunny_ears" -> paintBunnyEars(canvas, pose, opacity)
                "lens_round_shades" -> paintRoundShades(canvas, lens, pose, opacity)
                "lens_heart_shades" -> paintHeartShades(canvas, lens, pose, opacity)
                "lens_star_eyes" -> paintStarEyes(canvas, lens, pose, opacity)
                "lens_monocle" -> paintMonocle(canvas, lens, pose, opacity)
                "lens_flower_crown" -> paintFlowerCrown(canvas, lens, pose, opacity)
                "lens_tiara" -> paintTiara(canvas, lens, pose, opacity)
                "lens_wizard_hat" -> paintWizardHat(canvas, lens, pose, opacity)
                "lens_headphones" -> paintHeadphones(canvas, lens, pose, opacity)
                "lens_devil_horns" -> paintDevilHorns(canvas, lens, pose, opacity)
                "lens_rainbow_arc" -> paintRainbowArc(canvas, pose, opacity)
                "lens_butterflies" -> paintButterflies(canvas, lens, pose, opacity)
                "lens_freckles" -> paintFreckles(canvas, pose, opacity)
                "lens_mustache" -> paintMustache(canvas, pose, opacity)
                "lens_lip_gloss" -> paintLipGloss(canvas, pose, opacity)
                "lens_clown_nose" -> paintClownNose(canvas, pose, opacity)
                "lens_pirate_patch" -> paintPiratePatch(canvas, pose, opacity)
                "lens_snow_drift" -> paintSnowDrift(canvas, pose, opacity)
                "lens_music_notes" -> paintMusicNotes(canvas, lens, pose, opacity)
                "lens_star_crown" -> paintStarCrown(canvas, lens, pose, opacity)
                // --- Anchor-generic fallback (unknown/forward-compat ids) ---
                else -> when (lens.anchor) {
                    LensAnchor.eyes -> paintGlasses(canvas, pose, opacity)
                    LensAnchor.forehead -> paintCrown(canvas, pose, opacity)
                    LensAnchor.ears -> paintEars(canvas, pose, opacity)
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

    /** Origin shared by ear-mounted accessories: slides with head yaw so
     *  turning the head carries the accessory instead of leaving it behind. */
    private fun earOrigin(pose: FacePose): Offset {
        val yawShift =
            pose.headBounds.width * 0.10f * sin(pose.yawRadians).toFloat().coerceIn(-1.0f, 1.0f)
        return Offset(
            pose.headBounds.center.dx + yawShift,
            pose.headBounds.top + pose.headBounds.height * 0.12f,
        )
    }

    /** Origin shared by crown/hat/forehead accessories. */
    private fun crownOrigin(pose: FacePose): Offset = Offset(
        pose.headBounds.center.dx,
        pose.headBounds.top + pose.headBounds.height * 0.08f,
    )

    /** Origin for full-head scene lenses (weather, arcs, floaters). */
    private fun fullHeadOrigin(pose: FacePose): Offset = Offset(
        pose.headBounds.center.dx,
        pose.headBounds.top + pose.headBounds.height * 0.30f,
    )

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
            androidx.compose.ui.unit.IntOffset(
                (l - margin).roundToInt(),
                (t - margin).roundToInt(),
            ),
            androidx.compose.ui.unit.IntSize(
                (r - l + 2 * margin).roundToInt().coerceAtLeast(1),
                (b - t + 2 * margin).roundToInt().coerceAtLeast(1),
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

    /** Four-point star path (Dart sparkle geometry shared by star lenses). */
    private fun starPath(center: Offset, radius: Float): Path {
        val path = Path()
        for (i in 0 until 8) {
            val angle = -Math.PI / 2 + i * Math.PI / 4
            val r = if (i % 2 == 0) radius else radius * 0.28f
            val x = center.x + (cos(angle) * r).toFloat()
            val y = center.y + (sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Heart path (shared by heart lenses and floaters). */
    private fun heartPath(center: Offset, scale: Float): Path = Path().apply {
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

    // =====================================================================
    // Original 10 built-ins - REDESIGNED artwork (ids/anchors unchanged)
    // =====================================================================

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
            // Double bridge bar reads as the classic aviator brow line.
            canvas.drawLine(Offset(-16f, -16f), Offset(16f, -16f), rim)
            canvas.drawLine(Offset(-15f, -10f), Offset(15f, -10f), strokePaint(lens.accentColor.copy(alpha = 0.7f * opacity), 3f))
            canvas.drawLine(Offset(-90f, -20f), Offset(-108f, -26f), rim)
            canvas.drawLine(Offset(90f, -20f), Offset(108f, -26f), rim)
            // Sun shine slashes on both lenses.
            val shine = strokePaint(Color.White.copy(alpha = 0.55f * opacity), 4f)
            canvas.drawLine(Offset(-64f, -14f), Offset(-44f, 12f), shine)
            canvas.drawLine(Offset(44f, -14f), Offset(64f, 12f), shine)
        }
    }

    private fun paintNeonCrown(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = crownOrigin(pose)
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
            // Glowing tip dots on every spike.
            val tip = fillPaint(lens.accentColor.copy(alpha = opacity))
            for (p in listOf(Offset(-45f, -22f), Offset(0f, -40f), Offset(45f, -22f))) {
                canvas.drawCircle(p, if (p.x == 0f) 5f else 4f, tip)
            }
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
            // Inner bright ring gives the halo depth instead of a flat band.
            canvas.drawOval(
                Rect(Offset(0f, -28f), 44f, 8f),
                strokePaint(Color.White.copy(alpha = 0.75f * opacity), 2.5f),
            )
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
            val paint = fillPaint(lens.accentColor.copy(alpha = opacity))
            val soft = fillPaint(lens.accentColor.copy(alpha = 0.8f * opacity))
            canvas.drawPath(starPath(Offset(-70f, -26f), 17f), paint)
            canvas.drawPath(starPath(Offset(74f, -34f), 13f), soft)
            canvas.drawPath(starPath(Offset(-48f, 42f), 9f), fillPaint(lens.accentColor.copy(alpha = 0.75f * opacity)))
            canvas.drawPath(starPath(Offset(53f, 37f), 8f), soft)
            canvas.drawPath(starPath(Offset(6f, -58f), 11f), fillPaint(lens.accentColor.copy(alpha = 0.85f * opacity)))
            canvas.drawPath(starPath(Offset(-10f, 58f), 7f), fillPaint(lens.accentColor.copy(alpha = 0.6f * opacity)))
            // Pin-dot dust between the stars.
            val dot = fillPaint(Color.White.copy(alpha = 0.8f * opacity))
            for (p in listOf(Offset(-56f, -52f), Offset(58f, -8f), Offset(-28f, 18f), Offset(30f, -44f))) {
                canvas.drawCircle(p, 1.8f, dot)
            }
        }
    }

    private fun paintHearts(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = Offset(
            pose.headBounds.center.dx,
            pose.headBounds.top + pose.headBounds.height * 0.18f,
        )
        val unit = pose.headBounds.width * 0.9f
        withFaceTransform(canvas, pose, origin, unit) {
            canvas.drawPath(heartPath(Offset(-48f, -6f), 0.40f), fillPaint(lens.accentColor.copy(alpha = 0.90f * opacity)))
            canvas.drawPath(heartPath(Offset(44f, -28f), 0.30f), fillPaint(lens.accentColor.copy(alpha = 0.78f * opacity)))
            canvas.drawPath(heartPath(Offset(78f, 36f), 0.22f), fillPaint(lens.accentColor.copy(alpha = 0.66f * opacity)))
            canvas.drawPath(heartPath(Offset(-82f, 34f), 0.16f), fillPaint(lens.accentColor.copy(alpha = 0.55f * opacity)))
            canvas.drawPath(heartPath(Offset(6f, -52f), 0.18f), fillPaint(lens.accentColor.copy(alpha = 0.60f * opacity)))
        }
    }

    private fun paintDogNose(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.noseBase.dx, pose.noseBase.dy), pose.interocular * 1.75f) {
            val outer = fillPaint(Color(0xFF1F2937).copy(alpha = 0.95f * opacity))
            val inner = strokePaint(Color(0xFF111827).copy(alpha = opacity), 2f)
            canvas.drawOval(Rect(Offset.Zero, 16f, 11f), outer)
            // Wet-nose shine.
            canvas.drawCircle(Offset(-5f, -3f), 3f, fillPaint(Color.White.copy(alpha = 0.7f * opacity)))
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
            // Glass shine arcs.
            val shine = strokePaint(Color.White.copy(alpha = 0.5f * opacity), 3f)
            canvas.drawArc(Rect(Offset(-56f, -6f), 20f, 20f), 200f, 100f, false, shine)
            canvas.drawArc(Rect(Offset(44f, -6f), 20f, 20f), 200f, 100f, false, shine)
        }
    }

    private fun paintCrown(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val origin = crownOrigin(pose)
        val unit = pose.headBounds.width * 0.72f
        withFaceTransform(canvas, pose, origin, unit) {
            val gold = fillPaint(Color(0xFFFBBF24).copy(alpha = opacity))
            val goldDark = strokePaint(Color(0xFFB45309).copy(alpha = 0.8f * opacity), 3f)
            val path = Path().apply {
                moveTo(-60f, 20f); lineTo(-45f, -22f); lineTo(-20f, 2f); lineTo(0f, -40f)
                lineTo(20f, 2f); lineTo(45f, -22f); lineTo(60f, 20f); close()
            }
            canvas.drawPath(path, gold)
            canvas.drawPath(path, goldDark)
            // Base band.
            val band = Path().apply {
                moveTo(-62f, 20f); lineTo(62f, 20f); lineTo(58f, 32f); lineTo(-58f, 32f); close()
            }
            canvas.drawPath(band, gold)
            canvas.drawPath(band, goldDark)
            // Gems: ruby center, sapphire sides.
            canvas.drawCircle(Offset(0f, 8f), 6f, fillPaint(Color(0xFFEF4444).copy(alpha = opacity)))
            canvas.drawCircle(Offset(-30f, 12f), 4.5f, fillPaint(Color(0xFF3B82F6).copy(alpha = opacity)))
            canvas.drawCircle(Offset(30f, 12f), 4.5f, fillPaint(Color(0xFF3B82F6).copy(alpha = opacity)))
        }
    }

    private fun paintEars(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val origin = earOrigin(pose)
        val unit = pose.headBounds.width * 0.9f
        withFaceTransform(canvas, pose, origin, unit) {
            val outer = fillPaint(Color(0xFF8D5B4C).copy(alpha = opacity))
            val inner = fillPaint(Color(0xFFFBB6CE).copy(alpha = opacity))
            val spot = fillPaint(Color(0xFF6B4237).copy(alpha = 0.85f * opacity))
            for (side in listOf(-1f, 1f)) {
                canvas.save()
                canvas.translate(side * 52, -6f)
                canvas.rotate(Math.toDegrees((side * 0.34f).toDouble()).toFloat())
                canvas.drawOval(Rect(Offset.Zero, 17f, 29f), outer)
                canvas.drawOval(Rect(Offset.Zero, 11f, 19f), inner)
                canvas.restore()
            }
            // Spotted patch on the right ear.
            canvas.save()
            canvas.translate(52f, -6f)
            canvas.rotate(Math.toDegrees(0.34).toFloat())
            canvas.drawCircle(Offset(4f, -10f), 5f, spot)
            canvas.restore()
        }
    }

    private fun paintMouthGlow(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.mouthCenter.dx, pose.mouthCenter.dy), pose.interocular * 1.6f) {
            // REDESIGN: two soft blush circles on the cheeks instead of one
            // centered glow - reads as blush, not as a mouth artifact.
            // Dart lens_overlay.dart:473 - fill, blur sigma 18.
            drawBlurredSprite(
                canvas, "blush:left", -86f, -46f, -34f, 6f, 14f, opacity,
                Color(0xFFF472B6).copy(alpha = 0.30f).value.toInt(), 0f, true,
            ) { ac, ap -> ac.drawCircle(-60f, -20f, 24f, ap) }
            drawBlurredSprite(
                canvas, "blush:right", 34f, -46f, 86f, 6f, 14f, opacity,
                Color(0xFFF472B6).copy(alpha = 0.30f).value.toInt(), 0f, true,
            ) { ac, ap -> ac.drawCircle(60f, -20f, 24f, ap) }
            canvas.drawCircle(Offset(-60f, -20f), 6f, fillPaint(Color(0xFFF472B6).copy(alpha = 0.30f * opacity)))
            canvas.drawCircle(Offset(60f, -20f), 6f, fillPaint(Color(0xFFF472B6).copy(alpha = 0.30f * opacity)))
        }
    }

    private fun paintNoseDot(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.noseBase.dx, pose.noseBase.dy), pose.interocular * 0.8f) {
            canvas.drawCircle(Offset.Zero, 9f, fillPaint(Color.White.copy(alpha = 0.5f * opacity)))
        }
    }

    // =====================================================================
    // New original lenses
    // =====================================================================

    private fun paintCatEars(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val origin = earOrigin(pose)
        val unit = pose.headBounds.width * 0.9f
        withFaceTransform(canvas, pose, origin, unit) {
            val outer = fillPaint(Color(0xFF52525B).copy(alpha = opacity))
            val inner = fillPaint(Color(0xFFF9A8D4).copy(alpha = opacity))
            for (side in listOf(-1f, 1f)) {
                canvas.save()
                canvas.translate(side * 46, -8f)
                canvas.rotate(Math.toDegrees((side * 0.18).toDouble()).toFloat())
                val ear = Path().apply {
                    moveTo(-22f, 12f); lineTo(0f, -42f); lineTo(22f, 12f); close()
                }
                val innerEar = Path().apply {
                    moveTo(-11f, 8f); lineTo(0f, -24f); lineTo(11f, 8f); close()
                }
                canvas.drawPath(ear, outer)
                canvas.drawPath(innerEar, inner)
                canvas.restore()
            }
        }
    }

    private fun paintBunnyEars(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val origin = earOrigin(pose)
        val unit = pose.headBounds.width * 0.9f
        withFaceTransform(canvas, pose, origin, unit) {
            val outer = fillPaint(Color(0xFFF8FAFC).copy(alpha = opacity))
            val inner = fillPaint(Color(0xFFF9A8D4).copy(alpha = opacity))
            for (side in listOf(-1f, 1f)) {
                canvas.save()
                canvas.translate(side * 38, -34f)
                canvas.rotate(Math.toDegrees((side * 0.16).toDouble()).toFloat())
                canvas.drawOval(Rect(Offset.Zero, 14f, 40f), outer)
                canvas.drawOval(Rect(Offset.Zero, 8f, 28f), inner)
                canvas.restore()
            }
        }
    }

    private fun paintRoundShades(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val mid = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 1.08f
        withFaceTransform(canvas, pose, mid, unit) {
            val glass = fillPaint(Color(0xFF111827).copy(alpha = 0.88f * opacity))
            val rim = strokePaint(lens.accentColor.copy(alpha = opacity), 5f)
            canvas.drawCircle(Offset(-50f, 0f), 32f, glass)
            canvas.drawCircle(Offset(50f, 0f), 32f, glass)
            canvas.drawCircle(Offset(-50f, 0f), 32f, rim)
            canvas.drawCircle(Offset(50f, 0f), 32f, rim)
            canvas.drawLine(Offset(-18f, -4f), Offset(18f, -4f), rim)
            canvas.drawLine(Offset(-82f, -2f), Offset(-108f, -10f), rim)
            canvas.drawLine(Offset(82f, -2f), Offset(108f, -10f), rim)
            val shine = strokePaint(Color.White.copy(alpha = 0.5f * opacity), 3.5f)
            canvas.drawArc(Rect(Offset(-56f, -6f), 18f, 18f), 200f, 90f, false, shine)
            canvas.drawArc(Rect(Offset(44f, -6f), 18f, 18f), 200f, 90f, false, shine)
        }
    }

    private fun paintHeartShades(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val mid = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 1.08f
        withFaceTransform(canvas, pose, mid, unit) {
            val glass = fillPaint(lens.accentColor.copy(alpha = 0.85f * opacity))
            val rim = strokePaint(Color.White.copy(alpha = 0.8f * opacity), 3.5f)
            canvas.drawPath(heartPath(Offset(-50f, -4f), 1.0f), glass)
            canvas.drawPath(heartPath(Offset(50f, -4f), 1.0f), glass)
            canvas.drawPath(heartPath(Offset(-50f, -4f), 1.0f), rim)
            canvas.drawPath(heartPath(Offset(50f, -4f), 1.0f), rim)
            canvas.drawLine(Offset(-18f, -10f), Offset(18f, -10f), rim)
            canvas.drawLine(Offset(-88f, -12f), Offset(-112f, -18f), rim)
            canvas.drawLine(Offset(88f, -12f), Offset(112f, -18f), rim)
            val shine = fillPaint(Color.White.copy(alpha = 0.55f * opacity))
            canvas.drawCircle(Offset(-58f, -14f), 4f, shine)
            canvas.drawCircle(Offset(42f, -14f), 4f, shine)
        }
    }

    private fun paintStarEyes(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val mid = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 1.08f
        withFaceTransform(canvas, pose, mid, unit) {
            val star = fillPaint(lens.accentColor.copy(alpha = opacity))
            canvas.drawPath(starPath(Offset(-50f, 0f), 30f), star)
            canvas.drawPath(starPath(Offset(50f, 0f), 30f), star)
            val dot = fillPaint(Color.White.copy(alpha = 0.9f * opacity))
            canvas.drawCircle(Offset(-50f, 0f), 5f, dot)
            canvas.drawCircle(Offset(50f, 0f), 5f, dot)
            // Tiny trailing sparkles.
            val small = fillPaint(lens.accentColor.copy(alpha = 0.7f * opacity))
            canvas.drawPath(starPath(Offset(-86f, -30f), 8f), small)
            canvas.drawPath(starPath(Offset(88f, -26f), 7f), small)
        }
    }

    private fun paintMonocle(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val mid = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 1.08f
        withFaceTransform(canvas, pose, mid, unit) {
            val rim = strokePaint(lens.accentColor.copy(alpha = opacity), 5f)
            canvas.drawCircle(Offset(48f, 0f), 30f, rim)
            canvas.drawCircle(Offset(48f, 0f), 24f, strokePaint(lens.accentColor.copy(alpha = 0.5f * opacity), 2.5f))
            // Glass shine.
            canvas.drawArc(Rect(Offset(43f, -5f), 17f, 17f), 200f, 90f, false, strokePaint(Color.White.copy(alpha = 0.55f * opacity), 3f))
            // Chain dropping from the rim.
            val chain = strokePaint(lens.accentColor.copy(alpha = 0.85f * opacity), 3f)
            val chainPath = Path().apply {
                moveTo(48f + 28f, 12f)
                quadraticTo(48f + 52f, 48f, 48f + 34f, 92f)
            }
            canvas.drawPath(chainPath, chain)
            canvas.drawCircle(Offset(48f + 34f, 92f), 4f, fillPaint(lens.accentColor.copy(alpha = opacity)))
        }
    }

    private fun paintFlowerCrown(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = crownOrigin(pose)
        val unit = pose.headBounds.width * 0.78f
        withFaceTransform(canvas, pose, origin, unit) {
            val petals = listOf(
                Color(0xFFF472B6),
                Color(0xFFF8FAFC),
                Color(0xFFC4B5FD),
                Color(0xFFF8FAFC),
                Color(0xFFF472B6),
            )
            val centers = listOf(-56f, -28f, 0f, 28f, 56f)
            for (i in centers.indices) {
                val cx = centers[i]
                val cy = -6f - (1f - kotlin.math.abs(cx) / 60f) * 14f
                val petal = fillPaint(petals[i].copy(alpha = opacity))
                val core = fillPaint(Color(0xFFFDE047).copy(alpha = opacity))
                for (k in 0 until 5) {
                    val angle = k * 2.0 * Math.PI / 5.0 - Math.PI / 2
                    canvas.drawCircle(
                        Offset(cx + (cos(angle) * 8).toFloat(), cy + (sin(angle) * 8).toFloat()),
                        6.5f, petal,
                    )
                }
                canvas.drawCircle(Offset(cx, cy), 4.5f, core)
            }
            // Vine band under the flowers.
            val vine = strokePaint(Color(0xFF16A34A).copy(alpha = 0.8f * opacity), 3f)
            canvas.drawLine(Offset(-62f, 4f), Offset(62f, 4f), vine)
        }
    }

    private fun paintTiara(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = crownOrigin(pose)
        val unit = pose.headBounds.width * 0.62f
        withFaceTransform(canvas, pose, origin, unit) {
            val band = strokePaint(lens.accentColor.copy(alpha = opacity), 4f)
            val arc = Path().apply {
                moveTo(-48f, 8f)
                quadraticTo(0f, -26f, 48f, 8f)
            }
            canvas.drawPath(arc, band)
            // Center gem triangle + side stones.
            val gem = fillPaint(lens.accentColor.copy(alpha = opacity))
            val gemPath = Path().apply {
                moveTo(-10f, -14f); lineTo(0f, -34f); lineTo(10f, -14f); close()
            }
            canvas.drawPath(gemPath, gem)
            canvas.drawCircle(Offset(-24f, -6f), 4f, gem)
            canvas.drawCircle(Offset(24f, -6f), 4f, gem)
            canvas.drawCircle(Offset(0f, -18f), 2.5f, fillPaint(Color.White.copy(alpha = 0.9f * opacity)))
        }
    }

    private fun paintWizardHat(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = crownOrigin(pose)
        val unit = pose.headBounds.width * 0.85f
        withFaceTransform(canvas, pose, origin, unit) {
            val cone = fillPaint(lens.accentColor.copy(alpha = opacity))
            val conePath = Path().apply {
                moveTo(-46f, 4f); lineTo(4f, -92f); lineTo(46f, 4f); close()
            }
            canvas.drawPath(conePath, cone)
            // Fold band + buckle.
            canvas.drawPath(Path().apply {
                moveTo(-46f, 4f); lineTo(46f, 4f); lineTo(42f, -14f); lineTo(-42f, -14f); close()
            }, fillPaint(Color(0xFF4C1D95).copy(alpha = opacity)))
            canvas.drawOval(Rect(Offset(0f, -5f), 8f, 6f), strokePaint(Color(0xFFFBBF24).copy(alpha = opacity), 3f))
            // Brim.
            canvas.drawOval(Rect(Offset(0f, 8f), 62f, 11f), fillPaint(Color(0xFF4C1D95).copy(alpha = opacity)))
            // Stars sprinkled on the cone.
            val star = fillPaint(Color(0xFFFDE047).copy(alpha = opacity))
            canvas.drawPath(starPath(Offset(-12f, -34f), 6f), star)
            canvas.drawPath(starPath(Offset(16f, -52f), 5f), star)
            canvas.drawPath(starPath(Offset(2f, -70f), 4f), star)
        }
    }

    private fun paintHeadphones(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = earOrigin(pose)
        val unit = pose.headBounds.width * 0.95f
        withFaceTransform(canvas, pose, origin, unit) {
            val dark = Color(0xFF1F2937)
            // Band over the top of the head.
            canvas.drawArc(
                Rect(Offset(0f, 16f), 62f, 62f), 180f, 180f, false,
                strokePaint(dark.copy(alpha = 0.95f * opacity), 10f),
            )
            canvas.drawArc(
                Rect(Offset(0f, 16f), 62f, 62f), 180f, 180f, false,
                strokePaint(lens.accentColor.copy(alpha = 0.6f * opacity), 3f),
            )
            // Ear cups.
            for (side in listOf(-1f, 1f)) {
                canvas.drawCircle(Offset(side * 62, 16f), 17f, fillPaint(dark.copy(alpha = 0.95f * opacity)))
                canvas.drawCircle(Offset(side * 62, 16f), 17f, strokePaint(lens.accentColor.copy(alpha = opacity), 3f))
                canvas.drawCircle(Offset(side * 62, 16f), 7f, fillPaint(lens.accentColor.copy(alpha = 0.85f * opacity)))
            }
        }
    }

    private fun paintDevilHorns(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = crownOrigin(pose)
        val unit = pose.headBounds.width * 0.8f
        withFaceTransform(canvas, pose, origin, unit) {
            val horn = fillPaint(lens.accentColor.copy(alpha = opacity))
            val shade = fillPaint(Color(0xFF7F1D1D).copy(alpha = 0.7f * opacity))
            for (side in listOf(-1f, 1f)) {
                canvas.save()
                canvas.scale(side, 1f)
                val path = Path().apply {
                    moveTo(24f, 6f)
                    quadraticTo(26f, -28f, 46f, -44f)
                    quadraticTo(40f, -20f, 42f, 4f)
                    close()
                }
                canvas.drawPath(path, horn)
                canvas.drawPath(Path().apply {
                    moveTo(33f, 0f)
                    quadraticTo(34f, -20f, 44f, -34f)
                    quadraticTo(39f, -16f, 40f, 0f)
                    close()
                }, shade)
                canvas.restore()
            }
        }
    }

    private fun paintRainbowArc(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val origin = fullHeadOrigin(pose)
        val unit = pose.headBounds.width * 1.05f
        withFaceTransform(canvas, pose, origin, unit) {
            val bands = listOf(
                Color(0xFFEF4444),
                Color(0xFFF59E0B),
                Color(0xFFFDE047),
                Color(0xFF10B981),
                Color(0xFF3B82F6),
            )
            bands.forEachIndexed { i, color ->
                val radius = 74f - i * 9f
                canvas.drawArc(
                    Rect(Offset(0f, 12f), radius, radius), 180f, 180f, false,
                    strokePaint(color.copy(alpha = 0.85f * opacity), 8f),
                )
            }
            // Cloud puffs at the arc ends.
            val cloud = fillPaint(Color.White.copy(alpha = 0.9f * opacity))
            for (side in listOf(-1f, 1f)) {
                canvas.drawCircle(Offset(side * 74, 12f), 10f, cloud)
                canvas.drawCircle(Offset(side * 84, 16f), 7f, cloud)
                canvas.drawCircle(Offset(side * 64, 17f), 7f, cloud)
            }
        }
    }

    private fun paintButterflies(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = fullHeadOrigin(pose)
        val unit = pose.headBounds.width * 0.95f
        withFaceTransform(canvas, pose, origin, unit) {
            val wing = fillPaint(lens.accentColor.copy(alpha = 0.9f * opacity))
            val body = strokePaint(Color(0xFF3F3F46).copy(alpha = 0.9f * opacity), 2.5f)
            val spots = listOf(
                Triple(-72f, -36f, 1.0f),
                Triple(64f, -58f, 0.8f),
                Triple(26f, 62f, 0.65f),
            )
            for ((cx, cy, s) in spots) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.scale(s, s)
                canvas.save()
                canvas.rotate(-28f)
                canvas.drawOval(Rect(Offset(-9f, 0f), 9f, 6f), wing)
                canvas.restore()
                canvas.save()
                canvas.rotate(28f)
                canvas.drawOval(Rect(Offset(9f, 0f), 9f, 6f), wing)
                canvas.restore()
                canvas.drawLine(Offset(0f, -7f), Offset(0f, 7f), body)
                canvas.restore()
            }
        }
    }

    private fun paintFreckles(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.mouthCenter.dx, pose.mouthCenter.dy), pose.interocular * 1.6f) {
            val dot = fillPaint(Color(0xFF92400E).copy(alpha = 0.75f * opacity))
            val offsets = listOf(
                Offset(0f, 0f), Offset(9f, -6f), Offset(-8f, -5f),
                Offset(5f, 7f), Offset(-4f, 8f), Offset(14f, 2f),
            )
            for (side in listOf(-1f, 1f)) {
                for (o in offsets) {
                    canvas.drawCircle(Offset(side * 58 + o.x, -22f + o.y), 2.1f, dot)
                }
            }
        }
    }

    private fun paintMustache(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.mouthCenter.dx, pose.mouthCenter.dy), pose.interocular * 1.6f) {
            val hair = fillPaint(Color(0xFF27272A).copy(alpha = 0.95f * opacity))
            for (side in listOf(-1f, 1f)) {
                canvas.save()
                canvas.scale(side, 1f)
                canvas.drawPath(Path().apply {
                    moveTo(0f, -12f)
                    quadraticTo(18f, -26f, 36f, -16f)
                    quadraticTo(44f, -12f, 40f, -6f)
                    quadraticTo(22f, -2f, 0f, -6f)
                    close()
                }, hair)
                canvas.restore()
            }
        }
    }

    private fun paintLipGloss(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.mouthCenter.dx, pose.mouthCenter.dy), pose.interocular * 1.6f) {
            val lip = fillPaint(Color(0xFFF472B6).copy(alpha = 0.7f * opacity))
            canvas.drawPath(Path().apply {
                moveTo(-30f, 0f)
                quadraticTo(-10f, -12f, 0f, -4f)
                quadraticTo(10f, -12f, 30f, 0f)
                quadraticTo(10f, 18f, 0f, 18f)
                quadraticTo(-10f, 18f, -30f, 0f)
                close()
            }, lip)
            // Gloss shine.
            canvas.drawLine(
                Offset(-14f, 8f), Offset(6f, 10f),
                strokePaint(Color.White.copy(alpha = 0.5f * opacity), 3f),
            )
        }
    }

    private fun paintClownNose(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        withFaceTransform(canvas, pose, Offset(pose.noseBase.dx, pose.noseBase.dy), pose.interocular * 0.9f) {
            canvas.drawCircle(Offset.Zero, 16f, fillPaint(Color(0xFFEF4444).copy(alpha = 0.95f * opacity)))
            canvas.drawCircle(Offset(-5f, -5f), 4.5f, fillPaint(Color.White.copy(alpha = 0.8f * opacity)))
        }
    }

    private fun paintPiratePatch(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val mid = Offset(pose.eyesMidpoint.dx, pose.eyesMidpoint.dy)
        val unit = pose.interocular * 1.08f
        withFaceTransform(canvas, pose, mid, unit) {
            val strap = strokePaint(Color(0xFF1F2937).copy(alpha = 0.9f * opacity), 6f)
            canvas.drawLine(Offset(-112f, -18f), Offset(80f, -34f), strap)
            val patch = fillPaint(Color(0xFF111827).copy(alpha = 0.95f * opacity))
            canvas.drawOval(Rect(Offset(-50f, 0f), 27f, 30f), patch)
            canvas.drawOval(Rect(Offset(-50f, 0f), 27f, 30f), strokePaint(Color(0xFF374151).copy(alpha = opacity), 2.5f))
        }
    }

    private fun paintSnowDrift(canvas: androidx.compose.ui.graphics.Canvas, pose: FacePose, opacity: Float) {
        val origin = fullHeadOrigin(pose)
        val unit = pose.headBounds.width * 1.15f
        withFaceTransform(canvas, pose, origin, unit) {
            val flake = fillPaint(Color.White.copy(alpha = 0.85f * opacity))
            val soft = fillPaint(Color(0xFFBAE6FD).copy(alpha = 0.7f * opacity))
            val flakes = listOf(
                Triple(-78f, -52f, 3.5f), Triple(-46f, -70f, 2.5f), Triple(-8f, -82f, 3.0f),
                Triple(34f, -74f, 2.2f), Triple(70f, -56f, 3.2f), Triple(86f, -18f, 2.4f),
                Triple(-88f, -14f, 2.8f), Triple(-60f, 24f, 2.2f), Triple(58f, 30f, 2.6f),
                Triple(-20f, 52f, 2.0f), Triple(18f, 60f, 2.4f), Triple(84f, 46f, 1.8f),
            )
            flakes.forEachIndexed { i, (x, y, r) ->
                canvas.drawCircle(Offset(x, y), r, if (i % 2 == 0) flake else soft)
            }
        }
    }

    private fun paintMusicNotes(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = fullHeadOrigin(pose)
        val unit = pose.headBounds.width * 0.95f
        withFaceTransform(canvas, pose, origin, unit) {
            val note = fillPaint(lens.accentColor.copy(alpha = opacity))
            val stem = strokePaint(lens.accentColor.copy(alpha = opacity), 3f)
            val notes = listOf(
                Triple(-70f, -42f, 1.0f),
                Triple(58f, -30f, 0.8f),
                Triple(34f, 58f, 0.7f),
            )
            for ((cx, cy, s) in notes) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.scale(s, s)
                // Note head (tilted oval), stem, flag.
                canvas.save()
                canvas.rotate(-18f)
                canvas.drawOval(Rect(Offset.Zero, 8f, 6f), note)
                canvas.restore()
                canvas.drawLine(Offset(7f, -2f), Offset(7f, -26f), stem)
                canvas.drawPath(Path().apply {
                    moveTo(7f, -26f); quadraticTo(18f, -22f, 14f, -10f)
                    quadraticTo(14f, -18f, 7f, -19f); close()
                }, note)
                canvas.restore()
            }
        }
    }

    private fun paintStarCrown(canvas: androidx.compose.ui.graphics.Canvas, lens: LensDefinition, pose: FacePose, opacity: Float) {
        val origin = crownOrigin(pose)
        val unit = pose.headBounds.width * 0.72f
        withFaceTransform(canvas, pose, origin, unit) {
            // Soft glow behind the center star.
            drawBlurredSprite(
                canvas, "starcrown:" + lens.accentColor.value.toString(),
                -16f, -44f, 16f, -12f, 6f, opacity,
                lens.accentColor.copy(alpha = 0.4f).value.toInt(), 0f, true,
            ) { ac, ap -> ac.drawCircle(0f, -28f, 16f, ap) }
            val star = fillPaint(lens.accentColor.copy(alpha = opacity))
            canvas.drawPath(starPath(Offset(-52f, -2f), 9f), star)
            canvas.drawPath(starPath(Offset(-26f, -14f), 11f), star)
            canvas.drawPath(starPath(Offset(0f, -28f), 15f), star)
            canvas.drawPath(starPath(Offset(26f, -14f), 11f), star)
            canvas.drawPath(starPath(Offset(52f, -2f), 9f), star)
            val dot = fillPaint(Color.White.copy(alpha = 0.8f * opacity))
            canvas.drawCircle(Offset(0f, -28f), 3f, dot)
        }
    }

    /** Rect from center + half extents (Dart Rect.fromCenter). */
    private fun Rect(center: Offset, halfWidth: Float, halfHeight: Float) =
        Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}
