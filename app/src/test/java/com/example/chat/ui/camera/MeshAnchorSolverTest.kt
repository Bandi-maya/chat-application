package com.example.chat.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI

/** Deterministic fixture test of the mesh -> RawFace anchor math
 *  (camera_face_detection_bridge.dart _fromMeshResult). */
class MeshAnchorSolverTest {

    /** Synthetic 478-point mesh: identity spread over a known box with
     *  recognizable anchor landmarks. */
    private fun fixtureMesh(): List<MeshAnchorSolver.Landmark> {
        // normalized coords: x in [0.2, 0.6], y in [0.1, 0.9]
        val pts = MutableList(478) { i ->
            MeshAnchorSolver.Landmark(
                0.2f + 0.4f * (i % 22) / 21f,
                0.1f + 0.8f * (i / 22) / 21f,
            )
        }
        // eye corners (normalized): left eye around x 0.28-0.32, y 0.35
        pts[33] = MeshAnchorSolver.Landmark(0.28f, 0.34f)
        pts[133] = MeshAnchorSolver.Landmark(0.32f, 0.36f)
        pts[362] = MeshAnchorSolver.Landmark(0.48f, 0.34f)
        pts[263] = MeshAnchorSolver.Landmark(0.52f, 0.36f)
        pts[2] = MeshAnchorSolver.Landmark(0.40f, 0.50f)   // nose
        pts[13] = MeshAnchorSolver.Landmark(0.40f, 0.62f)  // mouth
        return pts
    }

    @Test
    fun anchorsMatchDartMath() {
        // image 1000x2000 so normalized->pixel mapping is obvious
        val face = MeshAnchorSolver.solve(7, fixtureMesh(), 1000, 2000, 0.83)!!
        assertEquals(7, face.trackingId)
        // leftEye = midpoint((0.28,0.34),(0.32,0.36)) = (0.30,0.35) -> (300,700)
        assertEquals(300f, face.leftEye.dx, 1e-3f)
        assertEquals(700f, face.leftEye.dy, 1e-3f)
        // rightEye = midpoint((0.48,0.34),(0.52,0.36)) = (0.50,0.35) -> (500,700)
        assertEquals(500f, face.rightEye.dx, 1e-3f)
        assertEquals(700f, face.rightEye.dy, 1e-3f)
        // nose (0.40,0.50) -> (400,1000); mouth (0.40,0.62) -> (400,1240)
        assertEquals(400f, face.noseBase.dx, 1e-3f)
        assertEquals(1000f, face.noseBase.dy, 1e-3f)
        assertEquals(400f, face.mouthCenter.dx, 1e-3f)
        assertEquals(1240f, face.mouthCenter.dy, 1e-3f)
        // level eyes -> roll = atan2(0, positive) = 0
        assertEquals(0.0, face.rollRadians, 1e-6)
        // bounds = full mesh spread: x [0.2,0.6]*1000, y [0.1,0.9]*2000
        assertEquals(200f, face.headBounds.left, 1e-3f)
        assertEquals(600f, face.headBounds.right, 1e-3f)
        assertEquals(200f, face.headBounds.top, 1e-3f)
        assertEquals(1800f, face.headBounds.bottom, 1e-3f)
        assertEquals(0.83, face.confidence, 1e-9)
    }

    @Test
    fun rollFollowsEyeLine() {
        val pts = fixtureMesh().toMutableList()
        // tilt right eye DOWN by 0.1 normalized-y (200px) -> positive roll
        pts[362] = MeshAnchorSolver.Landmark(0.48f, 0.44f)
        pts[263] = MeshAnchorSolver.Landmark(0.52f, 0.46f)
        val face = MeshAnchorSolver.solve(1, pts, 1000, 2000, 1.0)!!
        // leftEye (300,700), rightEye (500,900): atan2(200,200) = pi/4
        assertEquals(PI / 4, face.rollRadians, 1e-3)
    }

    @Test
    fun shortMeshIsSkipped() {
        assertNull(MeshAnchorSolver.solve(1, fixtureMesh().take(467), 100, 100, 1.0))
    }
}
