package com.example.chat.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Exact port of lib/features/camera/lens/camera_face_detection_bridge.dart.
 *
 *  Low-bandwidth bridge to on-device face detection. No network service is
 *  involved. Detection runs only while a face-anchored lens is selected.
 *
 *  Two-tier structure preserved from the Dart:
 *  - PRIMARY: MediaPipe FaceLandmarker (canonical 478-landmark FaceMesh-V2
 *    model, asset face_landmarker.task) with the exact anchor math from
 *    _fromMeshResult via [MeshAnchorSolver]. A busy landmarker SKIPS the frame
 *    (empty result with rotated dims) exactly like pipeline.isBusy - the
 *    FaceAnchorSolver grace window holds the last pose.
 *  - FALLBACK: android.media.FaceDetector over the 160px RGB thumbnail from
 *    [CameraFrameSampler], ported verbatim from the Dart repo's own
 *    MainActivity.detectFacesOnDevice (chaty/camera_face_tracking channel).
 *
 *  Documented deviations from the Dart mesh pipeline:
 *  - MediaPipe FaceLandmarker in IMAGE mode has no tracking ids; faces are
 *    keyed by detection index (index+1), like the native-channel fallback.
 *    FaceAnchorSolver smoothing stays exact for the primary face.
 *  - FaceLandmarker exposes no per-face score; confidence = 1.0 (mesh.score
 *    in Dart). Consumers only compare relative presence, not thresholds.
 *  - The Dart mesh path consumed raw planes; here the full frame is converted
 *    to an ARGB bitmap via the same readers (full resolution, scale 1.0). */
class CameraFaceDetectionBridge(private val context: Context) {

    data class CameraFaceDetectionResult(
        val imageWidth: Int,
        val imageHeight: Int,
        val faces: Map<Int, RawFace>,
    )

    private var landmarker: FaceLandmarker? = null
    private val creationMutex = Mutex()
    private val busy = AtomicBoolean(false)

    /** Port of detect(): primary mesh path with busy-skip, fallback to the
     *  platform detector on any primary failure. */
    suspend fun detect(image: FrameData, sensorOrientation: Int): CameraFaceDetectionResult {
        val rotated = sensorOrientation % 180 != 0
        val resultWidth = if (rotated) image.height else image.width
        val resultHeight = if (rotated) image.width else image.height
        return try {
            if (busy.get()) {
                // Skip this frame instead of running the slower fallback
                // concurrently; the solver holds the last stable pose.
                return CameraFaceDetectionResult(resultWidth, resultHeight, emptyMap())
            }
            val pipeline = ensureLandmarker()
            if (pipeline == null) {
                detectWithBuiltin(image, sensorOrientation)
            } else {
                busy.set(true)
                try {
                    val bitmap = withContext(Dispatchers.Default) {
                        // tasks-vision 1.1.1 BitmapImageBuilder carries no
                        // rotation setter; rotate the pixels instead so the
                        // detector sees an upright frame (result dims use the
                        // rotated extent, matching the Dart contract).
                        val raw = frameToBitmap(image)
                        val degrees = ((sensorOrientation % 360) + 360) % 360
                        if (degrees == 0) raw else {
                            val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                                .also { if (it !== raw) raw.recycle() }
                        }
                    }
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    val result = pipeline.detect(mpImage)
                    val faces = mutableMapOf<Int, RawFace>()
                    // tasks-vision: each face is a List<NormalizedLandmark>
                    // (container class), not a proto wrapper.
                    result.faceLandmarks().forEachIndexed { index, faceLandmarks ->
                        val landmarks = faceLandmarks.map {
                            MeshAnchorSolver.Landmark(it.x(), it.y())
                        }
                        MeshAnchorSolver.solve(
                            trackingId = index + 1,
                            landmarks = landmarks,
                            imageWidth = resultWidth,
                            imageHeight = resultHeight,
                            score = 1.0,
                        )?.let { faces[it.trackingId] = it }
                    }
                    CameraFaceDetectionResult(resultWidth, resultHeight, faces)
                } finally {
                    busy.set(false)
                }
            }
        } catch (_: Exception) {
            // Gracefully fall back to the platform detector if the model can't
            // initialize or this device can't run the native runtime.
            try {
                detectWithBuiltin(image, sensorOrientation)
            } catch (_: Exception) {
                CameraFaceDetectionResult(resultWidth, resultHeight, emptyMap())
            }
        }
    }

    private suspend fun ensureLandmarker(): FaceLandmarker? = creationMutex.withLock {
        landmarker?.let { return it }
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(MAX_FACES)
                .build()
            FaceLandmarker.createFromOptions(context, options).also { landmarker = it }
        } catch (_: Exception) {
            null
        }
    }

    fun dispose() {
        try { landmarker?.close() } catch (_: Exception) {}
        landmarker = null
    }

    /** Full-resolution frame -> ARGB bitmap via the ported readers. */
    private fun frameToBitmap(image: FrameData): Bitmap {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        val yPlane = image.planes[0]
        val uvPlane = image.planes.getOrNull(1)
        val vPlane = image.planes.getOrNull(2)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = when (image.format) {
                    FrameFormat.bgra8888 -> CameraFrameSampler.readBgra(yPlane, x, y)
                    FrameFormat.nv21 -> CameraFrameSampler.readNv21(yPlane, w, h, x, y)
                    FrameFormat.yuv420 -> CameraFrameSampler.readYuv(yPlane, uvPlane, vPlane, x, y)
                }
                pixels[y * w + x] = (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /** Verbatim port of MainActivity.detectFacesOnDevice (Dart repo, Android
     *  fallback behind the chaty/camera_face_tracking channel). */
    private fun detectWithBuiltin(image: FrameData, sensorOrientation: Int): CameraFaceDetectionResult {
        val frame = CameraFrameSampler.toRgbThumbnail(image, sensorOrientation)
        val width = frame.width
        val height = frame.height
        val rgbBytes = frame.bytes

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        try {
            val pixels = IntArray(width * height)
            var src = 0
            for (i in pixels.indices) {
                val red = rgbBytes[src++].toInt() and 0xFF
                val green = rgbBytes[src++].toInt() and 0xFF
                val blue = rgbBytes[src++].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            val detector = android.media.FaceDetector(width, height, MAX_FACES)
            val nativeFaces = arrayOfNulls<android.media.FaceDetector.Face>(MAX_FACES)
            val count = detector.findFaces(bitmap, nativeFaces)
            val faces = mutableMapOf<Int, RawFace>()
            for (index in 0 until count) {
                val face = nativeFaces[index] ?: continue
                val midpoint = PointF()
                face.getMidPoint(midpoint)
                val eyeDistance = face.eyesDistance().toDouble().coerceAtLeast(4.0)
                val faceWidth = (eyeDistance * 2.35).coerceIn(eyeDistance * 1.8, width.toDouble())
                val faceHeight = (faceWidth * 1.32).coerceAtMost(height.toDouble())
                val left = (midpoint.x - faceWidth / 2).coerceAtLeast(0.0)
                val top = (midpoint.y - eyeDistance * 0.95).coerceAtLeast(0.0)
                val right = (left + faceWidth).coerceAtMost(width.toDouble())
                val bottom = (top + faceHeight).coerceAtMost(height.toDouble())
                val roll = runCatching {
                    face.pose(android.media.FaceDetector.Face.EULER_Z).toDouble()
                }.getOrDefault(0.0)
                val confidence = face.confidence().toDouble().coerceIn(0.0, 1.0)
                val id = index + 1
                faces[id] = RawFace(
                    trackingId = id,
                    headBounds = LensRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()),
                    leftEye = LensOffset((midpoint.x - eyeDistance / 2).toFloat(), midpoint.y),
                    rightEye = LensOffset((midpoint.x + eyeDistance / 2).toFloat(), midpoint.y),
                    noseBase = LensOffset(midpoint.x, (midpoint.y + eyeDistance * 0.55).coerceAtMost(height.toDouble()).toFloat()),
                    mouthCenter = LensOffset(midpoint.x, (midpoint.y + eyeDistance * 1.0).coerceAtMost(height.toDouble()).toFloat()),
                    rollRadians = Math.toRadians(roll),
                    yawRadians = 0.0,
                    pitchRadians = 0.0,
                    confidence = confidence,
                )
            }
            return CameraFaceDetectionResult(width, height, faces)
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val MODEL_ASSET = "face_landmarker.task"
        const val MAX_FACES = 3 // Dart: maxMeshFaces 3 / native maxFaces 3
    }
}
