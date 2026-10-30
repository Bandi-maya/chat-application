package com.example.chat.ui.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteOrder

/**
 * MediaPipe selfie-segmentation bridge for the bg_* camera effects.
 *
 * The model is the canonical MediaPipe selfie_segmenter (float16, 249,537
 * bytes, Apache 2.0), downloaded and SHA-256-verified at build time by the
 * downloadSelfieSegmenter gradle task into the app assets - the same contract
 * as face_landmarker (FaceDetectionBridge). No runtime network access.
 *
 * Used on BOTH paths: the live preview composites through LiveSegmentPreview
 * (best effort - failures leave the viewfinder unfiltered) and the capture
 * path bakes through ImageFxBaker (exact, full resolution). Mask failures fail
 * open: the preview and photo stay unfiltered rather than corrupt or block
 * the capture, and a failed bake never claims the filter was applied.
 */
class SegmentationBridge(private val context: Context) {

    private var segmenter: ImageSegmenter? = null
    private val creationMutex = Mutex()

    private suspend fun ensureSegmenter(): ImageSegmenter? = creationMutex.withLock {
        segmenter?.let { return it }
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build()
            ImageSegmenter.createFromOptions(context, options).also { segmenter = it }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Person-confidence mask for [bitmap] as a grayscale Bitmap (white = person,
     * black = background), at the model's mask resolution. Returns null when the
     * segmenter is unavailable or produces no masks. The selfie_segmenter emits
     * two confidence masks: index 0 = background, index 1 = person.
     */
    suspend fun personMask(bitmap: Bitmap): Bitmap? {
        val seg = ensureSegmenter() ?: return null
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = seg.segment(mpImage)
            val masks = result.confidenceMasks()
            if (!masks.isPresent || masks.get().isEmpty()) return null
            val list = masks.get()
            val maskImage = if (list.size > 1) list[1] else list[0]
            val floats = ByteBufferExtractor.extract(maskImage)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            val w = maskImage.width
            val h = maskImage.height
            if (floats.remaining() < w * h) return null
            val pixels = IntArray(w * h)
            for (i in pixels.indices) {
                val c = floats.get().coerceIn(0f, 1f)
                val v = (c * 255f).toInt()
                pixels[i] = (v shl 24) or (v shl 16) or (v shl 8) or v
            }
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            null
        }
    }

    fun dispose() {
        try {
            segmenter?.close()
        } catch (_: Exception) {
            // best effort
        }
        segmenter = null
    }

    companion object {
        const val MODEL_ASSET = "selfie_segmenter.tflite"
    }
}
