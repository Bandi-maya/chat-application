package com.example.chat.ui.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies CameraFrameSampler against the Dart camera_face_detection_bridge
 *  sampling contract. */
class CameraFrameSamplerTest {

    @Test
    fun unrotateMatchesDartCases() {
        // 90: (y, height-1-x); 180: (w-1-x, h-1-y); 270: (w-1-y, x); 0: identity
        assertEquals(2 to 4, CameraFrameSampler.unrotate(3, 2, 10, 8, 90)) // (y, h-1-x) = (2, 8-1-3)
        assertEquals(6 to 3, CameraFrameSampler.unrotate(3, 4, 10, 8, 180))
        assertEquals(10 - 1 - 4 to 3, CameraFrameSampler.unrotate(3, 4, 10, 8, 270))
        assertEquals(3 to 4, CameraFrameSampler.unrotate(3, 4, 10, 8, 0))
        // negative orientation normalizes like Dart ((o % 360 + 360) % 360)
        assertEquals(CameraFrameSampler.unrotate(3, 4, 10, 8, 270), CameraFrameSampler.unrotate(3, 4, 10, 8, -90))
    }

    @Test
    fun thumbnailSizingMatchesDart() {
        // 320x240, orientation 0: scale 0.5 -> 160x120 (width even)
        val frame = FrameData(320, 240, FrameFormat.yuv420, listOf(
            FramePlane(ByteArray(320 * 240) { 128.toByte() }, 320, 1),
            FramePlane(ByteArray(160 * 120) { 128.toByte() }, 160, 1),
            FramePlane(ByteArray(160 * 120) { 128.toByte() }, 160, 1),
        ))
        val out = CameraFrameSampler.toRgbThumbnail(frame, 0)
        assertEquals(160, out.width)
        assertEquals(120, out.height)
        assertEquals(160 * 120 * 3, out.bytes.size)
        // neutral gray Y=128 U=128 V=128 -> R=G=B=128
        assertEquals(128.toByte(), out.bytes[0])
        assertEquals(128.toByte(), out.bytes[1])
        assertEquals(128.toByte(), out.bytes[2])

        // 90-degree rotation: oriented 240x320 -> scale 0.5 -> 120x160
        val out90 = CameraFrameSampler.toRgbThumbnail(frame, 90)
        assertEquals(120, out90.width)
        assertEquals(160, out90.height)
    }

    @Test
    fun nv21AndBgraReadersMatchDart() {
        // NV21: Y=255 at (0,0), V=128,U=128 -> pure white
        val nv21 = FramePlane(ByteArray(4 * 2 + 4 * 1) { i -> if (i < 8) -1 else 128.toByte() }, 4, 1)
        val white = CameraFrameSampler.readNv21(nv21, 4, 2, 0, 0)
        assertArrayEquals(intArrayOf(255, 255, 255), white)

        // BGRA pixel B=10,G=20,R=30 at origin -> (30,20,10)
        val bgra = FramePlane(byteArrayOf(10, 20, 30, -1), 4, 4)
        val rgb = CameraFrameSampler.readBgra(bgra, 0, 0)
        assertArrayEquals(intArrayOf(30, 20, 10), rgb)
    }
}
