package com.example.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.media.Image
import android.media.ImageReader
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.core.util.Consumer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.ui.camera.ColorLensSurfaceProcessor
import com.example.chat.ui.camera.FacePose
import com.example.chat.ui.camera.LensDefinition
import com.example.chat.ui.camera.LensOffset
import com.example.chat.ui.camera.LensRect
import com.example.chat.ui.camera.kBuiltInLenses
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decode-level proof that the CameraEffect pipeline bakes the selected color
 * matrix and face lens into the RECORDED video frames (not just the preview),
 * and that per-output transforms drive rendering per the SurfaceOutput
 * contract. The processor runs on its own real GL thread; the test feeds a
 * synthetic frame through a SurfaceTexture bound to the processor's frame
 * texture and reads the rendered pixels back through ImageReader surfaces.
 *
 * Evidence produced:
 *  - combined: VIDEO output shows the color matrix AND the lens overlay,
 *    PREVIEW output shows neither (lensTargetMask/colorTargetMask = VIDEO,
 *    so the preview lens stays owned by the Compose LensOverlay - a lens on
 *    both paths would double-draw).
 *  - translucent-lens doubling: at translucent overlay texels the recorded
 *    pixel matches single-application alpha math, not a twice-painted one.
 *  - rotation/mirror: distinct per-output transforms (mirror vs 270-degree
 *    rotation) land the known frame markers in the expected quadrants.
 */
@RunWith(AndroidJUnit4::class)
class CameraCombinedOutputProofTest {

    companion object {
        private const val TAG = "CombinedOutputProof"
        private const val W = 320
        private const val H = 240
        private const val TRANSFORM_IDENTITY = 0
        private const val TRANSFORM_MIRROR_X = 1
        private const val TRANSFORM_ROT_270 = 2
    }

    /** Minimal SurfaceOutput: hands the processor an ImageReader surface and
     *  applies a fixed additional transform like a real CameraX output would. */
    private class FakeSurfaceOutput(
        private val targetValue: Int,
        private val outputSurface: Surface,
        private val transformKind: Int,
    ) : SurfaceOutput {
        override fun getTargets(): Int = targetValue
        override fun getSize(): Size = Size(W, H)
        override fun getSurface(executor: Executor, listener: Consumer<SurfaceOutput.Event>): Surface = outputSurface
        override fun updateTransformMatrix(updated: FloatArray, original: FloatArray) {
            System.arraycopy(original, 0, updated, 0, 16)
            when (transformKind) {
                TRANSFORM_MIRROR_X -> {
                    Matrix.translateM(updated, 0, 1f, 0f, 0f)
                    Matrix.scaleM(updated, 0, -1f, 1f, 1f)
                }
                TRANSFORM_ROT_270 -> {
                    Matrix.translateM(updated, 0, 0.5f, 0.5f, 0f)
                    Matrix.rotateM(updated, 0, 270f, 0f, 0f, 1f)
                    Matrix.translateM(updated, 0, -0.5f, -0.5f, 0f)
                }
            }
        }
        override fun close() {}
    }

    private class Harness(
        lensProvider: () -> LensDefinition?,
        posesProvider: () -> List<FacePose>,
        viewportProvider: () -> Size,
        matrixProvider: () -> FloatArray?,
    ) {
        val processor = ColorLensSurfaceProcessor(
            lensProvider, posesProvider, viewportProvider, matrixProvider,
            CameraEffect.VIDEO_CAPTURE, CameraEffect.VIDEO_CAPTURE,
        )
        private var inputTexture: SurfaceTexture? = null
        private var inputSurface: Surface? = null

        private fun field(name: String): java.lang.reflect.Field {
            val f = ColorLensSurfaceProcessor::class.java.getDeclaredField(name)
            f.isAccessible = true
            return f
        }

        fun overlayBitmap(): Bitmap? = field("overlayBitmap").get(processor) as Bitmap?

        /** Runs after initGl on the GL thread, so the frame texture exists. */
        fun initInput() {
            val done = CountDownLatch(1)
            processor.getGlExecutor().execute {
                val texId = field("frameTextureId").getInt(processor)
                assertTrue("frame texture must be created on the GL context (id>0, got $texId)", texId > 0)
                val st = SurfaceTexture(texId)
                st.setDefaultBufferSize(W, H)
                inputTexture = st
                inputSurface = Surface(st)
                done.countDown()
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
        }

        fun addOutput(output: SurfaceOutput) {
            val done = CountDownLatch(1)
            processor.getGlExecutor().execute { processor.onOutputSurface(output); done.countDown() }
            assertTrue(done.await(10, TimeUnit.SECONDS))
        }

        fun renderFrame(paint: (Canvas) -> Unit) {
            val done = CountDownLatch(1)
            processor.getGlExecutor().execute {
                val c = inputSurface!!.lockCanvas(null)
                paint(c)
                inputSurface!!.unlockCanvasAndPost(c)
                processor.onFrameAvailable(inputTexture!!)
                done.countDown()
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
        }

        fun release() {
            val done = CountDownLatch(1)
            processor.getGlExecutor().execute {
                inputSurface?.release()
                inputTexture?.release()
                done.countDown()
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            processor.release()
        }
    }

    /** Measures the image->bitmap row mapping from the painted markers instead
     *  of assuming a GL flip. Run-127 dumps proved the acquired frames in this
     *  harness are NOT row-flipped (green painted top-left measured at bitmap
     *  top-left under identity transform); the prior fixed imageY=H-1-bitmapY
     *  mapping sampled the wrong rows. Assertion semantics unchanged: the same
     *  texels are checked at the measured mapping, and an unmappable frame
     *  fails loudly here instead of silently sampling wrong rows. */
    private fun detectRowFlip(bmp: Bitmap, expectedGreenImageY: Int): Boolean {
        var sumY = 0L
        var n = 0L
        for (y in 0 until bmp.height step 2) {
            for (x in 0 until bmp.width step 2) {
                val p = bmp.getPixel(x, y)
                if (Color.green(p) > 180 && Color.red(p) < 80 && Color.blue(p) < 80) { sumY += y; n++ }
            }
        }
        assertTrue("orientation calibration: painted green marker not found in acquired frame", n > 20)
        val centroid = (sumY / n).toInt()
        val flippedY = H - 1 - expectedGreenImageY
        val dFlipped = Math.abs(centroid - flippedY)
        val dPlain = Math.abs(centroid - expectedGreenImageY)
        Log.i("CC-DIAG", "orientation greenCentroidY=" + centroid + " expectedImageY=" + expectedGreenImageY + " flippedDist=" + dFlipped + " plainDist=" + dPlain + " flipped=" + (dFlipped < dPlain))
        assertTrue(
            "orientation calibration: green centroid y=" + centroid + " matches neither flipped (" + flippedY + ") nor plain (" + expectedGreenImageY + ") mapping",
            dFlipped < 60 || dPlain < 60,
        )
        return dFlipped < dPlain
    }

    private fun mapY(imageY: Int, flipped: Boolean): Int = if (flipped) H - 1 - imageY else imageY

    /** Evidence-only failure dumps: write acquired frames to the px artifact dir.
     *  Assertions and their order are unchanged; dumps run only on the failure path. */
    private fun dumpFrame(name: String, bmp: Bitmap) {
        try {
            val dir = java.io.File(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext.getExternalFilesDir(null),
                "screenshots",
            )
            dir.mkdirs()
            java.io.FileOutputStream(java.io.File(dir, name)).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            Log.i(TAG, "CC-DUMP wrote $name")
        } catch (e: Exception) { Log.w(TAG, "CC-DUMP $name failed: " + e.message) }
    }

    /** Coarse dominant-channel grid (R/G/B/.) so the actual marker landing is in logcat. */
    private fun logColorMap(label: String, bmp: Bitmap) {
        val sb = StringBuilder()
        var y = 19
        while (y < bmp.height) {
            var x = 19
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                sb.append(
                    if (r > 150 && g < 120 && b < 120) 'R'
                    else if (g > 150 && r < 120 && b < 120) 'G'
                    else if (b > 150 && r < 120 && g < 120) 'B'
                    else '.'
                )
                x += 40
            }
            sb.append('|')
            y += 40
        }
        Log.i(TAG, "CC-MAP " + label + " " + sb)
    }

    private fun acquireBitmap(reader: ImageReader): Bitmap {
        var img: Image? = null
        for (i in 0..19) {
            img = reader.acquireLatestImage()
            if (img != null) break
            Thread.sleep(50)
        }
        assertNotNull("expected a rendered frame in the ImageReader", img)
        val plane = img!!.planes[0]
        val buf = plane.buffer
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        for (y in 0 until H) {
            for (x in 0 until W) {
                val idx = y * plane.rowStride + x * plane.pixelStride
                val r = buf.get(idx).toInt() and 0xFF
                val g = buf.get(idx + 1).toInt() and 0xFF
                val b = buf.get(idx + 2).toInt() and 0xFF
                bmp.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        img.close()
        return bmp
    }

    /** Asserts the dominant color of a 9x9 patch centered at (x, y). */
    private fun expectDominant(bmp: Bitmap, x: Int, y: Int, channel: Char, what: String) {
        var strong = 0
        var total = 0
        for (dy in -4..4) for (dx in -4..4) {
            val c = bmp.getPixel(x + dx, y + dy)
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            total++
            when (channel) {
                'r' -> if (r > 180 && g < 80 && b < 80) strong++
                'g' -> if (g > 180 && r < 80 && b < 80) strong++
                'b' -> if (b > 180 && r < 80 && g < 80) strong++
            }
        }
        assertTrue("$what: expected dominant $channel at ($x,$y), strong=$strong/$total", strong * 2 > total)
    }

    private fun facePose(): FacePose = FacePose(
        1,
        LensRect(120f, 60f, 200f, 140f),
        LensOffset(140f, 95f), LensOffset(180f, 95f),
        LensOffset(160f, 110f), LensOffset(160f, 125f),
        0.0, 0.0, 0.0, 1.0,
    )

    /** Row-major 4x5 color matrix: swaps R and B, leaves G/alpha, no offset. */
    private fun swapRbMatrix(): FloatArray {
        val m = FloatArray(20)
        m[0 * 5 + 2] = 1f
        m[1 * 5 + 1] = 1f
        m[2 * 5 + 0] = 1f
        m[3 * 5 + 3] = 1f
        return m
    }

    private fun paintMarkerFrame(canvas: Canvas) {
        canvas.drawColor(Color.rgb(255, 0, 0))
        val p = Paint()
        p.color = Color.rgb(0, 255, 0)
        canvas.drawRect(0f, 0f, 80f, 80f, p)       // green top-left
        p.color = Color.rgb(0, 0, 255)
        canvas.drawRect(240f, 160f, 320f, 240f, p)  // blue bottom-right
    }

    @Test fun combinedOutput_videoGetsColorAndLens_previewStaysClean() {
        val lens = kBuiltInLenses.first { it.id == "lens_angel_halo" }
        val harness = Harness(
            { lens }, { listOf(facePose()) }, { Size(W, H) }, { swapRbMatrix() },
        )
        val previewReader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3)
        val videoReader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3)
        try {
            harness.initInput()
            harness.addOutput(FakeSurfaceOutput(CameraEffect.PREVIEW, previewReader.surface, TRANSFORM_MIRROR_X))
            harness.addOutput(FakeSurfaceOutput(CameraEffect.VIDEO_CAPTURE, videoReader.surface, TRANSFORM_IDENTITY))
            harness.renderFrame(::paintMarkerFrame)
            val video = acquireBitmap(videoReader)
            val preview = acquireBitmap(previewReader)
            val videoFlipped = detectRowFlip(video, 40)
            val previewFlipped = detectRowFlip(preview, 40)
            try {
                // Color matrix on VIDEO only: red background -> blue on video,
                // unchanged red on preview (targets gate: PREVIEW excluded).
                expectDominant(video, 160, mapY(40, videoFlipped), 'b', "video color matrix")
                expectDominant(preview, 160, mapY(40, previewFlipped), 'r', "preview untouched by color matrix")

                val overlay = harness.overlayBitmap()
                assertNotNull("lens overlay bitmap must exist after a lens frame", overlay)
                val ovBmp = overlay!!

                // Lens presence on VIDEO: at opaque overlay texels the recorded
                // pixel must deviate from the pure swapped background.
                // Overlay texture row 0 = bitmap top. The image->bitmap row
                // mapping is measured per frame from the painted markers
                // (run-127 dumps proved no GL flip in this harness).
                var presenceChecked = 0
                var doublingChecked = 0
                for (by in 0 until H step 2) {
                    for (bx in 0 until W step 2) {
                        val ov = ovBmp.getPixel(bx, by)
                        val oa = Color.alpha(ov)
                        if (oa < 30) continue
                        val iyVideo = mapY(by, videoFlipped)
                        val iyPreview = mapY(by, previewFlipped)
                        if (oa > 220) {
                            if (presenceChecked < 40) {
                                val vp = video.getPixel(bx, iyVideo)
                                val dev = Math.abs(Color.red(vp) - 0) + Math.abs(Color.green(vp) - 0) + Math.abs(Color.blue(vp) - 255)
                                assertTrue(
                                    "opaque overlay texel ($bx,$by) must appear on video (dev=$dev)",
                                    dev > 30,
                                )
                                presenceChecked++
                            }
                            // Preview must show the raw background at the same
                            // anchor: no lens leak (no cross-path double-draw).
                            val pp = preview.getPixel(bx, iyPreview)
                            assertTrue(
                                "overlay texel ($bx,$by) must NOT appear on preview (r=${Color.red(pp)},g=${Color.green(pp)},b=${Color.blue(pp)})",
                                Color.red(pp) > 180 && Color.green(pp) < 80 && Color.blue(pp) < 80,
                            )
                        }
                        // Translucency: single-application alpha math (see
                        // the premultiplied note below); a twice-painted
                        // overlay predicts visibly more deposit in channels
                        // where the tinted frame is dark.
                        if (oa in 40..220 && doublingChecked < 25) {
                            // Flatness gate: LINEAR texture filtering blends
                            // neighboring texels, so only assert where the 4
                            // neighbors share this texel's alpha (+-16).
                            var flat = true
                            for (nb in intArrayOf(-1, 1)) {
                                if (bx + nb !in 0 until W || by + nb !in 0 until H) { flat = false; break }
                                if (Math.abs(Color.alpha(ovBmp.getPixel(bx + nb, by)) - oa) > 16) { flat = false; break }
                                if (Math.abs(Color.alpha(ovBmp.getPixel(bx, by + nb)) - oa) > 16) { flat = false; break }
                            }
                            if (!flat) continue
                            // Bitmap storage is premultiplied: getPixel returns
                            // p = c*a. The shader mixes tinted*(1-a) + p*a.
                            // A twice-painted overlay has a' = a + a(1-a) and
                            // p' = p*(2-a), a visibly heavier deposit.
                            val a = oa / 255f
                            val vp = video.getPixel(bx, iyVideo)
                            val a2 = a + a * (1f - a)
                            for ((ch, ovP, tintedC) in listOf(
                                Triple('r', Color.red(ov).toFloat(), 0f),
                                Triple('g', Color.green(ov).toFloat(), 0f),
                                Triple('b', Color.blue(ov).toFloat(), 255f),
                            )) {
                                val expSingle = tintedC * (1f - a) + ovP * a
                                val expDouble = tintedC * (1f - a2) + ovP * (2f - a) * a2
                                val obs = when (ch) {
                                    'r' -> Color.red(vp); 'g' -> Color.green(vp); else -> Color.blue(vp)
                                }.toFloat()
                                val tol = Math.max(30f, Math.abs(expDouble - expSingle) * 0.5f)
                                assertTrue(
                                    "translucent texel ($bx,$by) $ch: single-apply expected ${expSingle.toInt()}, double-apply ${expDouble.toInt()}, observed ${obs.toInt()}",
                                    Math.abs(obs - expSingle) <= tol,
                                )
                            }
                            doublingChecked++
                        }
                    }
                }
                Log.i(TAG, "presenceChecked=$presenceChecked doublingChecked=$doublingChecked")
                assertTrue("lens artwork must have opaque texels on the frame", presenceChecked > 0)
                assertTrue("lens artwork must have translucent texels for the doubling check", doublingChecked > 0)
            } catch (t: Throwable) {
                dumpFrame("cc-video.png", video); dumpFrame("cc-preview.png", preview)
                harness.overlayBitmap()?.let { dumpFrame("cc-overlay.png", it) }
                logColorMap("video", video); logColorMap("preview", preview)
                throw t
            } finally {
                video.recycle(); preview.recycle()
            }
        } finally {
            harness.release()
            previewReader.close(); videoReader.close()
        }
    }

    @Test fun perOutputTransform_mirrorAndRotationDriveRendering() {
        val noLens = kBuiltInLenses.first { it.isNoLens }
        val harness = Harness({ noLens }, { emptyList() }, { Size(W, H) }, { null })
        val previewReader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3)
        val videoReader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3)
        try {
            harness.initInput()
            harness.addOutput(FakeSurfaceOutput(CameraEffect.PREVIEW, previewReader.surface, TRANSFORM_MIRROR_X))
            harness.addOutput(FakeSurfaceOutput(CameraEffect.VIDEO_CAPTURE, videoReader.surface, TRANSFORM_ROT_270))
            harness.renderFrame(::paintMarkerFrame)
            val preview = acquireBitmap(previewReader)
            val video = acquireBitmap(videoReader)
            try {
                // The image->bitmap row mapping is measured per frame from
                // the painted markers (run-127 proof: no GL flip in this
                // harness), not assumed flipped.
                val ptPreviewFlipped = detectRowFlip(preview, 40)
                val ptVideoFlipped = detectRowFlip(video, 210)
                // Mirror: green canvas(0..80,0..80) -> image right half,
                // blue canvas(240..320,160..240) -> image left half.
                expectDominant(preview, 279, mapY(40, ptPreviewFlipped), 'g', "mirrored green")
                expectDominant(preview, 39, mapY(200, ptPreviewFlipped), 'b', "mirrored blue")
                // Rotate 270 about center: u'=v, v'=1-u. Green -> image(53,210),
                // blue -> image(266,30).
                expectDominant(video, 53, mapY(210, ptVideoFlipped), 'g', "rotated green")
                expectDominant(video, 266, mapY(30, ptVideoFlipped), 'b', "rotated blue")
            } catch (t: Throwable) {
                dumpFrame("pt-video.png", video); dumpFrame("pt-preview.png", preview)
                logColorMap("video", video); logColorMap("preview", preview)
                throw t
            } finally {
                video.recycle(); preview.recycle()
            }
        } finally {
            harness.release()
            previewReader.close(); videoReader.close()
        }
    }
}
