package com.example.chat

import androidx.compose.ui.geometry.Offset
import com.example.chat.ui.camera.clampConfirmPanePan
import com.example.chat.ui.camera.confirmPaneZoomPan
import org.junit.Assert.assertEquals
import org.junit.Test

/** Parity with InteractiveViewer(maxScale: 4, boundaryMargin: zero,
 *  constrained: true): top-left pivot, focal-point zoom, AABB clamped to
 *  the viewport each update. */
class ConfirmPanePanParityTest {

    @Test
    fun `scale 1 allows no pan`() {
        val r = clampConfirmPanePan(Offset(50f, -50f), 1f, 360f, 640f)
        assertEquals(0f, r.x, 1e-6f)
        assertEquals(0f, r.y, 1e-6f)
    }

    @Test
    fun `scale 2 clamps to viewport range`() {
        val past = clampConfirmPanePan(Offset(-500f, 100f), 2f, 360f, 640f)
        assertEquals(-360f, past.x, 1e-6f)
        assertEquals(0f, past.y, 1e-6f)
    }

    @Test
    fun `zoom about center keeps center fixed`() {
        // c=(180,320), offset 0, s 1->2: offset' = c + (0-c)*2 = -c, and the
        // centroid's content point (c-offset)/s = c maps back to
        // offset' + c*2 = c.
        val (s, o) = confirmPaneZoomPan(
            offset = Offset.Zero, centroid = Offset(180f, 320f),
            pan = Offset.Zero, scale = 1f, zoom = 2f,
            viewportWidth = 360f, viewportHeight = 640f,
        )
        assertEquals(2f, s, 1e-6f)
        assertEquals(-180f, o.x, 1e-6f)
        assertEquals(-320f, o.y, 1e-6f)
        // centroid still maps to itself:
        assertEquals(180f, o.x + 180f * s, 1e-6f)
        assertEquals(320f, o.y + 320f * s, 1e-6f)
    }

    @Test
    fun `zoom about corner keeps corner fixed`() {
        val (s, o) = confirmPaneZoomPan(
            offset = Offset.Zero, centroid = Offset.Zero,
            pan = Offset.Zero, scale = 1f, zoom = 3f,
            viewportWidth = 360f, viewportHeight = 640f,
        )
        assertEquals(3f, s, 1e-6f)
        assertEquals(0f, o.x, 1e-6f)
        assertEquals(0f, o.y, 1e-6f)
    }

    @Test
    fun `zoom clamps at 4 and result clamps to bounds`() {
        val (s, o) = confirmPaneZoomPan(
            offset = Offset.Zero, centroid = Offset(360f, 640f),
            pan = Offset(50f, 50f), scale = 3f, zoom = 2f,
            viewportWidth = 360f, viewportHeight = 640f,
        )
        assertEquals(4f, s, 1e-6f)
        // unclamped: c + (0-c)*(4/3) + (50,50) = c*(1-4/3) + 50
        // = (-120+50, -213.33+50) = (-70, -163.33); range at s=4 is
        // [-1080,0]x[-1920,0] -> inside, no clamp.
        assertEquals(-70f, o.x, 1e-4f)
        assertEquals(-163.3333f, o.y, 1e-3f)
    }

    @Test
    fun `pan alone at scale 1 clamps to zero`() {
        val (s, o) = confirmPaneZoomPan(
            offset = Offset.Zero, centroid = Offset(100f, 100f),
            pan = Offset(30f, -30f), scale = 1f, zoom = 1f,
            viewportWidth = 360f, viewportHeight = 640f,
        )
        assertEquals(1f, s, 1e-6f)
        assertEquals(0f, o.x, 1e-6f)
        assertEquals(0f, o.y, 1e-6f)
    }
}
