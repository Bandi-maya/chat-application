package com.example.chat.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Verifies FaceAnchorSolver against face_tracker.dart semantics. */
class FaceAnchorSolverTest {

    private val image = LensSize(100f, 200f)
    private val crop = LensRect(0f, 0f, 100f, 200f) // 1:1 mapping

    private fun raw(x: Float, y: Float) = RawFace(
        trackingId = 7,
        headBounds = LensRect(x - 10, y - 10, x + 10, y + 10),
        leftEye = LensOffset(x - 5, y),
        rightEye = LensOffset(x + 5, y),
        noseBase = LensOffset(x, y + 5),
        mouthCenter = LensOffset(x, y + 10),
    )

    @Test
    fun mirroredSolvesAfterFlip() {
        val solver = FaceAnchorSolver()
        val poses = solver.update(1000, mapOf(7 to raw(20f, 40f)), mirrored = true, imageExtent = image, previewCrop = crop, confidenceScale = 1.0)
        assertEquals(1, poses.size)
        // x mirrored: 100 - 20 = 80; left/right eyes swap sides after mirror
        assertEquals(80f, poses[0].noseBase.dx, 1e-4f)
        assertEquals(45f, poses[0].noseBase.dy, 1e-4f)
        assertEquals(TrackingHealth.tracked, solver.health)
    }

    @Test
    fun exponentialLowPassSmooths() {
        val solver = FaceAnchorSolver(smoothingAlpha = 0.5)
        solver.update(1000, mapOf(7 to raw(20f, 40f)), false, image, crop, 1.0)
        val poses = solver.update(1016, mapOf(7 to raw(40f, 40f)), false, image, crop, 1.0)
        // lerp(prev=20, current=40, alpha=0.5) = 30
        assertEquals(30f, poses[0].noseBase.dx, 1e-4f)
    }

    @Test
    fun gracePeriodHoldsThenDrops() {
        val solver = FaceAnchorSolver(gracePeriod = 350.milliseconds)
        solver.update(1000, mapOf(7 to raw(20f, 40f)), false, image, crop, 1.0)
        // within grace: held pose, temporarilyLost
        val held = solver.update(1200, emptyMap(), false, image, crop, 1.0)
        assertEquals(TrackingHealth.temporarilyLost, solver.health)
        assertEquals(1, held.size)
        // beyond grace: lost, empty, smoothed cleared
        val dropped = solver.update(2000, emptyMap(), false, image, crop, 1.0)
        assertEquals(TrackingHealth.lost, solver.health)
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun initRequiresAlphaInRange() {
        var threw = false
        try { FaceAnchorSolver(smoothingAlpha = 0.0) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }
}
