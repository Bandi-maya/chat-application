package com.example.chat

import com.example.chat.ui.profile.ProfileCropGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression lock for the 2026-10-30 user report: portrait photos were
 * squashed into the wide banner frame. Cover-fit must always preserve the
 * source aspect ratio and overflow (never fit-inside / scale-to-fill).
 */
class ProfileCropAspectGeometryTest {

    @Test
    fun `portrait source into banner frame overflows vertically and keeps aspect`() {
        // 1080x1920 portrait into the 16:9 banner window of a 411dp-wide
        // phone at 2.625 density: crop 953x536 px.
        val cropW = 953f
        val cropH = cropW * 9f / 16f
        val l = ProfileCropGeometry.layout(1080f, 1920f, 0, cropW, cropH)
        assertEquals(cropW, l.childW, 0.01f)
        assertTrue("portrait must overflow the banner height", l.childH > cropH)
        assertEquals("child keeps the source aspect", 1080f / 1920f, l.childW / l.childH, 0.0001f)
    }

    @Test
    fun `landscape source into banner frame overflows horizontally and keeps aspect`() {
        // 2000x1000 (2:1) is wider than 16:9, so it must overflow the width.
        // (An exactly-16:9 source fills the frame exactly - that is correct.)
        val cropW = 953f
        val cropH = cropW * 9f / 16f
        val l = ProfileCropGeometry.layout(2000f, 1000f, 0, cropW, cropH)
        assertEquals(cropH, l.childH, 0.01f)
        assertTrue("wider-than-16:9 must overflow the banner width", l.childW > cropW)
        assertEquals("child keeps the source aspect", 2f, l.childW / l.childH, 0.0001f)
    }

    @Test
    fun `exact 16 by 9 source fills the banner frame exactly`() {
        val cropW = 953f
        val cropH = cropW * 9f / 16f
        val l = ProfileCropGeometry.layout(1920f, 1080f, 0, cropW, cropH)
        assertEquals(cropW, l.childW, 0.01f)
        assertEquals(cropH, l.childH, 0.01f)
    }

    @Test
    fun `portrait source into avatar frame overflows vertically and keeps aspect`() {
        val crop = 953f
        val l = ProfileCropGeometry.layout(1080f, 1920f, 0, crop, crop)
        assertEquals(crop, l.childW, 0.01f)
        assertTrue(l.childH > crop)
        assertEquals(1080f / 1920f, l.childW / l.childH, 0.0001f)
    }

    @Test
    fun `layout never fits the whole source inside the frame`() {
        // Scale-to-fill/fit-inside is the reported bug: for any mismatched
        // aspect, at least one child axis must exceed the frame.
        // None of these is exactly 16:9 in any quarter rotation, so every
        // case must overflow the frame on at least one axis.
        val cases = arrayOf(
            floatArrayOf(1000f, 2000f), floatArrayOf(2000f, 1000f),
            floatArrayOf(3000f, 4000f), floatArrayOf(4000f, 3000f),
            floatArrayOf(500f, 900f),
        )
        for (q in 0..3) {
            for (c in cases) {
                val l = ProfileCropGeometry.layout(c[0], c[1], q, 953f, 536f)
                val overflows = l.footW > 953f + 0.01f || l.footH > 536f + 0.01f
                assertTrue("src ${c[0]}x${c[1]} q$q must overflow, got ${l.footW}x${l.footH}", overflows)
            }
        }
    }

    @Test
    fun `pan clamp keeps the frame covered for the portrait banner case`() {
        val cropW = 953f
        val cropH = cropW * 9f / 16f
        val l = ProfileCropGeometry.layout(1080f, 1920f, 0, cropW, cropH)
        val (cx, cy) = ProfileCropGeometry.clampOffset(0f, 10000f, 1f, l.footW, l.footH, cropW, cropH)
        assertEquals(0f, cx, 0.001f)
        assertTrue("vertical pan is allowed but bounded", cy > 0f && cy < 10000f)
        val (cx2, cy2) = ProfileCropGeometry.clampOffset(0f, -10000f, 1f, l.footW, l.footH, cropW, cropH)
        assertEquals(-cy, cy2, 0.001f)
        assertEquals(0f, cx2, 0.001f)
    }
}
