package com.example.chat

import com.example.chat.ui.island.ISLAND_SPRING_DAMPING_RATIO
import com.example.chat.ui.island.IslandMode
import com.example.chat.ui.island.islandDimensions
import com.example.chat.ui.island.islandModeToTarget
import com.example.chat.ui.island.islandWaveformBarHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parity with dynamic_island_overlay_host.dart dimension lerps and
 *  dynamic_island_views.dart waveform bar math. */
class IslandOverlayHostParityTest {

    @Test
    fun `mode targets are 0,1,2,3`() {
        assertEquals(0f, islandModeToTarget(IslandMode.idle), 1e-6f)
        assertEquals(1f, islandModeToTarget(IslandMode.compact), 1e-6f)
        assertEquals(2f, islandModeToTarget(IslandMode.split), 1e-6f)
        assertEquals(3f, islandModeToTarget(IslandMode.expanded), 1e-6f)
    }

    @Test
    fun `spring damping ratio matches framer reference`() {
        // 36 / (2 * sqrt(380 * 0.9)) = 0.9733285
        assertEquals(0.9733285, ISLAND_SPRING_DAMPING_RATIO.toDouble(), 1e-5)
    }

    @Test
    fun `idle to compact lerp`() {
        // t=0: width 32, radius 16, opacity 0
        var d = islandDimensions(0.0, IslandMode.idle, isPlayer = false, isCall = false, screenWidth = 400.0)
        assertEquals(32.0, d.width, 1e-9)
        assertEquals(16.0, d.radius, 1e-9)
        assertEquals(0.0, d.opacity, 1e-9)
        assertFalse(d.visible)
        // t=1: compact width 84, radius 999, opacity 1
        d = islandDimensions(1.0, IslandMode.compact, isPlayer = false, isCall = false, screenWidth = 400.0)
        assertEquals(84.0, d.width, 1e-9)
        assertEquals(999.0, d.radius, 1e-9)
        assertEquals(1.0, d.opacity, 1e-9)
        assertTrue(d.visible)
    }

    @Test
    fun `compact width depends on payload kind`() {
        assertEquals(92.0, islandDimensions(1.0, IslandMode.compact, true, false, 400.0).width, 1e-9)
        assertEquals(96.0, islandDimensions(1.0, IslandMode.compact, false, true, 400.0).width, 1e-9)
        assertEquals(84.0, islandDimensions(1.0, IslandMode.compact, false, false, 400.0).width, 1e-9)
    }

    @Test
    fun `split lerp to 104`() {
        val d = islandDimensions(2.0, IslandMode.split, false, false, 400.0)
        assertEquals(104.0, d.width, 1e-9)
        assertEquals(999.0, d.radius, 1e-9)
    }

    @Test
    fun `expanded from split starts at 104, from compact at compactWidth`() {
        // t=2 exactly: both modes sit at 104 (second branch, splitT=1).
        assertEquals(104.0, islandDimensions(2.0, IslandMode.split, false, false, 400.0).width, 1e-9)
        assertEquals(104.0, islandDimensions(2.0, IslandMode.compact, false, false, 400.0).width, 1e-9)
        // mid-expand (spring overshoot past 2): startW is mode-dependent.
        assertEquals(230.0, islandDimensions(2.5, IslandMode.split, false, false, 400.0).width, 1e-9)
        assertEquals(220.0, islandDimensions(2.5, IslandMode.compact, false, false, 400.0).width, 1e-9)
        // t=3: expanded 356 (non-player), radius 28
        val d = islandDimensions(3.0, IslandMode.expanded, false, false, 400.0)
        assertEquals(356.0, d.width, 1e-9)
        assertEquals(28.0, d.radius, 1e-9)
    }

    @Test
    fun `width bounded by screen minus 24`() {
        val d = islandDimensions(3.0, IslandMode.expanded, false, false, 360.0)
        assertEquals(336.0, d.width, 1e-9)
    }

    @Test
    fun `player expanded width is 340`() {
        assertEquals(340.0, islandDimensions(3.0, IslandMode.expanded, true, false, 400.0).width, 1e-9)
    }

    @Test
    fun `target content width per mode`() {
        assertEquals(32.0, islandDimensions(0.5, IslandMode.idle, false, false, 400.0).targetContentWidth, 1e-9)
        assertEquals(104.0, islandDimensions(1.5, IslandMode.split, false, false, 400.0).targetContentWidth, 1e-9)
        assertEquals(84.0, islandDimensions(1.0, IslandMode.compact, false, false, 400.0).targetContentWidth, 1e-9)
        assertEquals(356.0, islandDimensions(2.5, IslandMode.expanded, false, false, 400.0).targetContentWidth, 1e-9)
    }

    @Test
    fun `waveform bar math matches dart`() {
        // inactive: base heights clamped to >= 3
        assertEquals(3.0, islandWaveformBarHeight(0, false, 0.0, 18.0), 1e-9)
        assertEquals(9.0, islandWaveformBarHeight(4, false, 0.0, 18.0), 1e-9)
        // active phase 0, bar 0: sin(0)=0 -> factor 1 -> base
        assertEquals(3.0, islandWaveformBarHeight(0, true, 0.0, 18.0), 1e-9)
        // bar 1 at phase 0: sin(0.4) * 1.1 factor
        assertEquals(5.0 * (1.0 + kotlin.math.abs(kotlin.math.sin(0.4)) * 1.1),
            islandWaveformBarHeight(1, true, 0.0, 18.0), 1e-9)
        // clamped at max height (sin(1.6) ~ 1 -> factor ~2.1 -> 9*2.1 > 10)
        assertEquals(10.0, islandWaveformBarHeight(4, true, 0.0, 10.0), 1e-9)
        // factor uses |sin(phase*2pi + i*0.4)| * 1.1
        val expected = 3.0 * (1.0 + kotlin.math.abs(kotlin.math.sin(0.3 * 2 * Math.PI + 0 * 0.4)) * 1.1)
        assertEquals(expected, islandWaveformBarHeight(0, true, 0.3, 18.0), 1e-9)
    }
}
