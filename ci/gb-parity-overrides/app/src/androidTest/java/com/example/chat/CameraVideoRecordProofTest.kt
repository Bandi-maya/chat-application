package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.camera.ColorLensSurfaceProcessor
import com.example.chat.ui.camera.FacePose
import com.example.chat.ui.camera.LensOffset
import com.example.chat.ui.camera.LensRect
import com.example.chat.ui.camera.kBuiltInLenses
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Encoded-video proof: binds a real CameraX Preview + VideoCapture with the
 * production ColorLensSurfaceProcessor effect, records an actual mp4 on the
 * device/emulator camera, finalizes it, and decodes frames to verify the
 * color matrix and the lens overlay are baked into the RECORDED video.
 *
 * Evidence produced (encoded-file level, complementing the synthetic GL
 * harness in CameraCombinedOutputProofTest):
 *  - zero-G color matrix: the decoded frame's green channel is suppressed
 *    everywhere except the (matrix-exempt) lens overlay pixels.
 *  - lens overlay: a neon-cyan crown lens at a synthetic pose leaves cyan
 *    pixels in the decoded frame (scene green is matrix-killed, so cyan
 *    cannot come from the camera feed).
 *  - finalize: the recording completes with a non-empty file.
 *
 * NOT claimed here: real-face detection/alignment - the pose is synthetic
 * (the emulator virtual scene has no face); that path needs a fixture-based
 * analyzer test or a device run.
 */
@RunWith(AndroidJUnit4::class)
class CameraVideoRecordProofTest {

    companion object {
        private const val TAG = "VideoRecordProof"
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        init { registry.currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun zeroGreenMatrix(): FloatArray {
        val m = FloatArray(20)
        m[0 * 5 + 0] = 1f
        // green row stays all-zero: matrix kills the green channel
        m[2 * 5 + 2] = 1f
        m[3 * 5 + 3] = 1f
        return m
    }

    @Test fun recordedVideoBakesColorMatrixAndLens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.CAMERA",
        ).close()

        val lens = kBuiltInLenses.first { it.id == "lens_neon_crown" }
        val pose = FacePose(
            1,
            LensRect(280f, 180f, 360f, 260f),
            LensOffset(300f, 215f), LensOffset(340f, 215f),
            LensOffset(320f, 230f), LensOffset(320f, 245f),
            0.0, 0.0, 0.0, 1.0,
        )
        val processor = ColorLensSurfaceProcessor(
            { lens }, { listOf(pose) }, { Size(640, 480) }, { zeroGreenMatrix() },
            CameraEffect.VIDEO_CAPTURE, CameraEffect.VIDEO_CAPTURE,
        )
        val drainHandler = Handler(HandlerThread("preview-drain").apply { start() }.looper)
        val drainExecutor = java.util.concurrent.Executor { r -> drainHandler.post(r) }
        val effect = object : CameraEffect(
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            processor.getGlExecutor(),
            processor,
            Consumer { err -> Log.e(TAG, "effect error", err) },
        ) {}

        var previewReader: ImageReader? = null
        val preview = Preview.Builder().build()
        // Preview.setSurfaceProvider enforces the main thread (CameraX Threads.
        // checkMainThread); the test body runs on the instrumentation thread.
        instrumentation.runOnMainSync {
            preview.setSurfaceProvider(drainExecutor) { request: SurfaceRequest ->
                val reader = ImageReader.newInstance(
                    request.resolution.width, request.resolution.height, PixelFormat.RGBA_8888, 3,
                )
                previewReader = reader
                reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close() }, drainHandler)
                request.provideSurface(reader.surface, drainExecutor) { }
            }
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD))
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(videoCapture)
            .addEffect(effect)
            .build()

        val provider = ProcessCameraProvider.getInstance(context).get(15, TimeUnit.SECONDS)
        // LifecycleRegistry enforces main-thread state changes: the owner must
        // be CONSTRUCTED on the main thread too (its init sets RESUMED).
        lateinit var owner: TestLifecycleOwner
        instrumentation.runOnMainSync { owner = TestLifecycleOwner() }
        instrumentation.runOnMainSync {
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
        }

        val outFile = File.createTempFile("fxproof_", ".mp4", context.cacheDir)
        val finalizeLatch = CountDownLatch(1)
        var finalizeOk = false
        var recording: Recording? = null
        instrumentation.runOnMainSync {
            recording = recorder.prepareRecording(context, FileOutputOptions.Builder(outFile).build())
                .start(drainExecutor) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        finalizeOk = !event.hasError()
                        if (event.hasError()) Log.e(TAG, "finalize error: ${event.error}")
                        finalizeLatch.countDown()
                    }
                }
        }
        Thread.sleep(3000)
        instrumentation.runOnMainSync { recording?.stop() }
        assertTrue("recording did not finalize in time", finalizeLatch.await(20, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { provider.unbindAll() }
        previewReader?.close()
        processor.release()

        assertTrue("recording finalize reported an error", finalizeOk)
        assertTrue("recorded file is empty", outFile.length() > 0)
        Log.i(TAG, "recorded bytes=${outFile.length()}")

        // Decode frames and verify matrix + overlay in the ENCODED output.
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(outFile.absolutePath)
        val frame = retriever.getFrameAtTime(1_500_000)
            ?: retriever.getFrameAtTime(500_000)
        assertNotNull("could not decode a frame from the recorded video", frame)
        val bmp = frame!!
        try {
            var greenHeavy = 0
            var cyan = 0
            var total = 0
            for (y in 0 until bmp.height step 4) {
                for (x in 0 until bmp.width step 4) {
                    val c = bmp.getPixel(x, y)
                    val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                    total++
                    if (g > 150 && b > 150 && r < 120) cyan++
                    else if (g > 50) greenHeavy++
                }
            }
            Log.i(TAG, "greenHeavy=$greenHeavy cyan=$cyan total=$total")
            assertTrue(
                "zero-G matrix must suppress scene green in the recorded video (greenHeavy=$greenHeavy/$total)",
                greenHeavy * 20 < total,
            )
            assertTrue(
                "lens overlay (cyan crown) must appear in the recorded video (cyan=$cyan/$total)",
                cyan > 20,
            )
        } finally {
            bmp.recycle()
            retriever.release()
            outFile.delete()
        }
    }
}
