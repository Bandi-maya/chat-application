package com.example.chat

import com.example.chat.ui.profile.ProfileCropGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity tests for the crop geometry, cross-checked against the Dart
 * formulas in image_editor_cropper_dialog.dart with concrete pixel cases.
 */
class ProfileCropGeometryTest {

    @Test
    fun `crop box is screen width minus 48 and banner is 16 by 9`() {
        val w = ProfileCropGeometry.cropBoxWidth(411f)
        assertEquals(363f, w, 0.001f)
        assertEquals(363f, ProfileCropGeometry.cropBoxHeight(w, true), 0.001f)
        assertEquals(363f * 9f / 16f, ProfileCropGeometry.cropBoxHeight(w, false), 0.001f)
    }

    @Test
    fun `portrait source covers square frame by width`() {
        // 1000x2000 portrait into a 300x300 frame: cover by width.
        val l = ProfileCropGeometry.layout(1000f, 2000f, 0, 300f, 300f)
        assertEquals(300f, l.childW, 0.001f)
        assertEquals(600f, l.childH, 0.001f)
        assertEquals(l.childW, l.footW, 0.001f)
        assertEquals(l.childH, l.footH, 0.001f)
    }

    @Test
    fun `landscape source covers square frame by height`() {
        val l = ProfileCropGeometry.layout(2000f, 1000f, 0, 300f, 300f)
        assertEquals(600f, l.childW, 0.001f)
        assertEquals(300f, l.childH, 0.001f)
    }

    @Test
    fun `odd rotation swaps footprint like RotatedBox`() {
        val l = ProfileCropGeometry.layout(1000f, 2000f, 1, 300f, 300f)
        // Dart: rotated source 2000x1000 covers 300x300 -> rotatedDisplaySize
        // 600x300; childDisplaySize swaps (300x600); RotatedBox footprint in
        // the viewer space swaps back to 600x300.
        assertEquals(300f, l.childW, 0.001f)
        assertEquals(600f, l.childH, 0.001f)
        assertEquals(600f, l.footW, 0.001f)
        assertEquals(300f, l.footH, 0.001f)
    }

    @Test
    fun `quarter rotation 2 keeps footprint`() {
        val l = ProfileCropGeometry.layout(1000f, 2000f, 2, 300f, 300f)
        assertEquals(300f, l.footW, 0.001f)
        assertEquals(600f, l.footH, 0.001f)
    }

    @Test
    fun `scale clamps to InteractiveViewer 1 to 4`() {
        assertEquals(1f, ProfileCropGeometry.clampScale(0.5f), 0.001f)
        assertEquals(4f, ProfileCropGeometry.clampScale(9f), 0.001f)
        assertEquals(2.5f, ProfileCropGeometry.clampScale(2.5f), 0.001f)
    }

    @Test
    fun `offset clamps so image always covers the frame`() {
        // footprint 300x600 at scale 1 in a 300x300 frame: only vertical pan.
        var (x, y) = ProfileCropGeometry.clampOffset(500f, 500f, 1f, 300f, 600f, 300f, 300f)
        assertEquals(0f, x, 0.001f)
        assertEquals(150f, y, 0.001f)
        // zoom 2x: horizontal range opens to (600-300)/2 = 150, vertical (1200-300)/2 = 450
        val c2 = ProfileCropGeometry.clampOffset(-999f, 999f, 2f, 300f, 600f, 300f, 300f)
        assertEquals(-150f, c2.first, 0.001f)
        assertEquals(450f, c2.second, 0.001f)
        // banner frame 320x180 with exact-fit image: no pan possible
        val c3 = ProfileCropGeometry.clampOffset(40f, 40f, 1f, 320f, 180f, 320f, 180f)
        assertEquals(0f, c3.first, 0.001f)
        assertEquals(0f, c3.second, 0.001f)
    }

    @Test
    fun `capture raster is crop window at 2 point 5 x`() {
        val (w, h) = ProfileCropGeometry.captureSize(363f, 363f * 9f / 16f)
        assertEquals(907, w)
        assertEquals(510, h)
    }
}
