package com.example.chat.ui.camera

import kotlin.math.atan2

/** Exact port of CameraFaceDetectionBridge._fromMeshResult's per-face anchor
 *  math (camera_face_detection_bridge.dart).
 *
 *  Pure function: given one tracked face's normalized 478-landmark mesh
 *  (MediaPipe canonical topology, x/y in [0,1] relative to the image),
 *  produce the [RawFace] the Dart bridge produced. Anchor contract preserved
 *  verbatim:
 *  - eye anchors are eye-CORNER midpoints: left = (33,133), right = (362,263)
 *    ("produce correctly sized glasses and remain stable as pupils move")
 *  - nose = landmark 2, mouth = landmark 13
 *  - head bounds = min/max over ALL landmarks
 *  - roll = atan2 of the eye line
 *  - confidence = mesh score
 *  - meshes with fewer than 468 landmarks are skipped. */
object MeshAnchorSolver {

    const val MIN_LANDMARKS = 468

    data class Landmark(val x: Float, val y: Float)

    /** Returns null when the mesh has fewer than [MIN_LANDMARKS] points. */
    fun solve(
        trackingId: Int,
        landmarks: List<Landmark>,
        imageWidth: Int,
        imageHeight: Int,
        score: Double,
    ): RawFace? {
        if (landmarks.size < MIN_LANDMARKS) return null

        fun point(index: Int) = LensOffset(
            landmarks[index].x * imageWidth,
            landmarks[index].y * imageHeight,
        )

        val leftEye = LensOffset(
            (point(33).dx + point(133).dx) / 2f,
            (point(33).dy + point(133).dy) / 2f,
        )
        val rightEye = LensOffset(
            (point(362).dx + point(263).dx) / 2f,
            (point(362).dy + point(263).dy) / 2f,
        )
        val nose = point(2)
        val mouth = point(13)

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = Float.MIN_VALUE
        var bottom = Float.MIN_VALUE
        for (lm in landmarks) {
            val px = lm.x * imageWidth
            val py = lm.y * imageHeight
            if (px < left) left = px
            if (py < top) top = py
            if (px > right) right = px
            if (py > bottom) bottom = py
        }

        val roll = atan2(
            (rightEye.dy - leftEye.dy).toDouble(),
            (rightEye.dx - leftEye.dx).toDouble(),
        )

        return RawFace(
            trackingId = trackingId,
            headBounds = LensRect(left, top, right, bottom),
            leftEye = leftEye,
            rightEye = rightEye,
            noseBase = nose,
            mouthCenter = mouth,
            rollRadians = roll,
            yawRadians = 0.0,
            pitchRadians = 0.0,
            confidence = score,
        )
    }
}
