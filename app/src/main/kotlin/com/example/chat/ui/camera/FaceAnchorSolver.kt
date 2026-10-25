package com.example.chat.ui.camera

import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Exact port of lib/features/camera/lens/face_tracker.dart.
 *
 *  Pure-math pipeline that turns raw tracker output into stable, correctly
 *  mirrored preview-space poses and solves lens anchor transforms.
 *  Deterministic; no widget, camera or platform dependency.
 *
 *  Dart quirk preserved (documented): both lens_definition.dart and
 *  face_tracker.dart define `FacePoseX`; the lens_definition one (ported in
 *  LensModels.kt) leaves [FacePose.interocular] unclamped while this file's
 *  clamps to >= 8.0. Callers in the Dart tree see the extension from the
 *  library they import; here the clamped variant is explicit as
 *  [FaceAnchorSolver.clampedInterocular] and lens rendering keeps the
 *  unclamped LensModels version. */

/** Raw, unprocessed detection supplied by the platform tracker in IMAGE space. */
data class RawFace(
    val trackingId: Int,
    val headBounds: LensRect,
    val leftEye: LensOffset,
    val rightEye: LensOffset,
    val noseBase: LensOffset,
    val mouthCenter: LensOffset,
    val rollRadians: Double = 0.0,
    val yawRadians: Double = 0.0,
    val pitchRadians: Double = 0.0,
    val confidence: Double = 1.0,
)

class FaceAnchorSolver(
    /** Exponential low-pass factor: higher = more responsive, lower = smoother. */
    val smoothingAlpha: Double = 0.45,
    /** How long a vanished face is held by prediction before lenses fade. */
    val gracePeriod: Duration = 350.milliseconds,
) {
    init {
        require(smoothingAlpha > 0 && smoothingAlpha <= 1) {
            "smoothingAlpha must be in (0, 1]"
        }
    }

    var health: TrackingHealth = TrackingHealth.lost
        private set

    private var smoothed: SmoothedFace? = null
    private var lastSeenMs: Long? = null

    /** Drops previous-frame smoothing and grace state when the camera changes,
     *  tracking stops, or the user leaves face-lens mode. */
    fun reset() {
        smoothed = null
        lastSeenMs = null
        health = TrackingHealth.lost
    }

    /** Feeds one raw detection set (or empty when the frame had no face).
     *
     *  [mirrored] must be true for front-camera previews where the image is
     *  displayed flipped; solving happens after mirroring so anchors can never
     *  move opposite to the user (requirement 42).
     *  [previewCrop] maps image space onto the cover-cropped preview space
     *  (requirement 44): scale then offset, never stretch. */
    fun update(
        nowMs: Long,
        rawFaces: Map<Int, RawFace>?,
        mirrored: Boolean = true,
        imageExtent: LensSize,
        previewCrop: LensRect,
        confidenceScale: Double,
    ): List<FacePose> {
        if (rawFaces.isNullOrEmpty()) {
            val held = health == TrackingHealth.tracked &&
                nowMs - (lastSeenMs ?: nowMs) <= gracePeriod.inWholeMilliseconds
            health = if (held) TrackingHealth.temporarilyLost else TrackingHealth.lost
            if (!held) {
                smoothed = null
                return emptyList()
            }
            // Hold last pose during the grace window so brief occlusions do not
            // make lenses blink out of existence.
            return listOfNotNull(smoothed?.pose)
        }

        lastSeenMs = nowMs
        health = TrackingHealth.tracked

        val poses = mutableListOf<FacePose>()
        for ((id, raw) in rawFaces) {
            val existing = if (smoothed?.trackingId == id) smoothed else null
            val pose = solveAndSmooth(
                raw = raw,
                trackingId = id,
                previous = existing,
                mirrored = mirrored,
                imageExtent = imageExtent,
                previewCrop = previewCrop,
                confidenceScale = confidenceScale,
            )
            poses.add(pose)
            if (smoothed == null || smoothed?.trackingId == id) {
                smoothed = SmoothedFace(id, pose)
            }
        }
        val current = smoothed
        if (current != null && poses.none { it.trackingId == current.trackingId }) {
            val replacement = poses.firstOrNull()
            smoothed = replacement?.let { SmoothedFace(it.trackingId, it) }
        }
        return poses
    }

    private fun solveAndSmooth(
        raw: RawFace,
        trackingId: Int,
        previous: SmoothedFace?,
        mirrored: Boolean,
        imageExtent: LensSize,
        previewCrop: LensRect,
        confidenceScale: Double,
    ): FacePose {
        fun map(p: LensOffset): LensOffset {
            val x = if (mirrored) imageExtent.width - p.dx else p.dx
            val sx = previewCrop.width / imageExtent.width
            val sy = previewCrop.height / imageExtent.height
            return LensOffset(
                previewCrop.left + x * sx,
                previewCrop.top + p.dy * sy,
            )
        }

        fun mapRect(r: LensRect): LensRect {
            val topLeft = map(LensOffset(r.left, r.top))
            val bottomRight = map(LensOffset(r.right, r.bottom))
            return LensRect.fromPoints(topLeft, bottomRight)
        }

        fun smooth(current: LensOffset, previousValue: LensOffset?): LensOffset {
            if (previousValue == null) return current
            // Lerp toward the measurement - exponential low-pass (requirement 23).
            return previousValue + (current - previousValue) * smoothingAlpha.toFloat()
        }

        val headBounds = mapRect(raw.headBounds)
        val prev = previous?.pose
        val leftEye = smooth(map(raw.leftEye), prev?.leftEye)
        val rightEye = smooth(map(raw.rightEye), prev?.rightEye)
        val noseBase = smooth(map(raw.noseBase), prev?.noseBase)
        val mouthCenter = smooth(map(raw.mouthCenter), prev?.mouthCenter)

        val rawRoll = if (mirrored) -raw.rollRadians else raw.rollRadians
        return FacePose(
            trackingId = trackingId,
            headBounds = headBounds,
            leftEye = leftEye,
            rightEye = rightEye,
            noseBase = noseBase,
            mouthCenter = mouthCenter,
            rollRadians = lerpAngle(prev?.rollRadians ?: rawRoll, rawRoll, smoothingAlpha),
            yawRadians = lerpAngle(prev?.yawRadians ?: raw.yawRadians, raw.yawRadians, smoothingAlpha),
            pitchRadians = lerpAngle(prev?.pitchRadians ?: raw.pitchRadians, raw.pitchRadians, smoothingAlpha),
            confidence = raw.confidence * confidenceScale.coerceIn(0.0, 1.0),
        )
    }

    private data class SmoothedFace(val trackingId: Int, val pose: FacePose)

    companion object {
        private fun lerpAngle(a: Double, b: Double, t: Double) = a + (b - a) * t

        /** face_tracker.dart's clamped interocular variant. */
        fun clampedInterocular(pose: FacePose): Double =
            max(8.0, abs(pose.interocular.toDouble()))
    }
}
