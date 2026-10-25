package com.example.chat

import com.example.chat.ui.island.CutoutAlignment
import com.example.chat.ui.island.CutoutAnchorInput
import com.example.chat.ui.island.CutoutAnchorResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parity with cutout_anchor_resolver.dart resolve(). */
class CutoutAnchorResolverParityTest {

    @Test
    fun `no cutout with status bar uses centered fallback`() {
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(viewPaddingTop = 48.0, paddingTop = 48.0, screenWidth = 400.0),
        )
        assertFalse(m.hasCutout)
        assertEquals(CutoutAlignment.topCenter, m.alignment)
        assertEquals(32.0, m.cutoutWidth, 1e-9)
        assertEquals(32.0, m.cutoutHeight, 1e-9)
        // max(6, (48-36)/2+3) = 9
        assertEquals(9.0, m.topOffset, 1e-9)
    }

    @Test
    fun `no cutout no padding gives 10`() {
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(viewPaddingTop = 0.0, paddingTop = 0.0, screenWidth = 400.0),
        )
        assertEquals(10.0, m.topOffset, 1e-9)
    }

    @Test
    fun `viewPadding wins over padding when positive`() {
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(viewPaddingTop = 42.0, paddingTop = 0.0, screenWidth = 400.0),
        )
        // max(6, (42-36)/2+3) = 6
        assertEquals(6.0, m.topOffset, 1e-9)
    }

    @Test
    fun `centered cutout vertically centers island`() {
        // punch-hole: left 184, top 8, right 216, bottom 40 (center x=200, y=24)
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(
                viewPaddingTop = 48.0, paddingTop = 48.0, screenWidth = 400.0,
                cutoutBounds = listOf(doubleArrayOf(184.0, 8.0, 216.0, 40.0)),
            ),
        )
        assertTrue(m.hasCutout)
        assertEquals(CutoutAlignment.topCenter, m.alignment)
        assertEquals(32.0, m.cutoutWidth, 1e-9)
        assertEquals(32.0, m.cutoutHeight, 1e-9)
        // max(2, 24-18) = 6
        assertEquals(6.0, m.topOffset, 1e-9)
    }

    @Test
    fun `left cutout aligns topLeft`() {
        // center x = 40 < 400*0.35 = 140
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(
                viewPaddingTop = 48.0, paddingTop = 48.0, screenWidth = 400.0,
                cutoutBounds = listOf(doubleArrayOf(24.0, 8.0, 56.0, 40.0)),
            ),
        )
        assertEquals(CutoutAlignment.topLeft, m.alignment)
    }

    @Test
    fun `right cutout aligns topRight`() {
        // center x = 360 > 400*0.65 = 260
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(
                viewPaddingTop = 48.0, paddingTop = 48.0, screenWidth = 400.0,
                cutoutBounds = listOf(doubleArrayOf(344.0, 8.0, 376.0, 40.0)),
            ),
        )
        assertEquals(CutoutAlignment.topRight, m.alignment)
    }

    @Test
    fun `cutout below status band is ignored`() {
        // top edge 80 >= 48+16 = 64 -> not an island anchor
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(
                viewPaddingTop = 48.0, paddingTop = 48.0, screenWidth = 400.0,
                cutoutBounds = listOf(doubleArrayOf(184.0, 80.0, 216.0, 112.0)),
            ),
        )
        assertFalse(m.hasCutout)
    }

    @Test
    fun `topOffset floors at 2`() {
        // cutout center y = 12 -> 12-18 = -6 -> floor 2
        val m = CutoutAnchorResolver.resolve(
            CutoutAnchorInput(
                viewPaddingTop = 24.0, paddingTop = 24.0, screenWidth = 400.0,
                cutoutBounds = listOf(doubleArrayOf(192.0, 4.0, 208.0, 20.0)),
            ),
        )
        assertTrue(m.hasCutout)
        assertEquals(2.0, m.topOffset, 1e-9)
    }
}
