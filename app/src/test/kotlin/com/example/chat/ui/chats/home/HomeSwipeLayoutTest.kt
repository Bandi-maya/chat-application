package com.example.chat.ui.chats.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSwipeLayoutTest {
    @Test fun traysNeverCoverTheWholeRowAndEveryActionFits() {
        for (width in 124..900) for (count in 1..5) {
            val extent = homeSwipeExtent(width.toFloat(), count, 74f)
            assertTrue(extent > 0f && extent <= 74f)
            assertTrue(extent * count <= width * .60001f)
        }
        assertEquals(0f, homeSwipeExtent(320f, 0, 74f), 0f)
    }
    @Test fun lowVelocityShortDragsCloseAndHalfwayDragsOpenBothDirections() {
        assertEquals(0f, homeSwipeSettleTarget(80f, 0f, 148f, 192f, 420f, 12f), 0f)
        assertEquals(192f, homeSwipeSettleTarget(100f, 0f, 148f, 192f, 420f, 12f), 0f)
        assertEquals(-148f, homeSwipeSettleTarget(-80f, 0f, 148f, 192f, 420f, 12f), 0f)
        assertEquals(0f, homeSwipeSettleTarget(-70f, 0f, 148f, 192f, 420f, 12f), 0f)
    }
    @Test fun flingThresholdsUseSameUnitsAsOffsetAndVelocity() {
        assertEquals(192f, homeSwipeSettleTarget(13f, 421f, 148f, 192f, 420f, 12f), 0f)
        assertEquals(0f, homeSwipeSettleTarget(11f, 1000f, 148f, 192f, 420f, 12f), 0f)
        assertEquals(-148f, homeSwipeSettleTarget(-13f, -421f, 148f, 192f, 420f, 12f), 0f)
        assertEquals(0f, homeSwipeSettleTarget(30f, -900f, 148f, 192f, 420f, 12f), 0f)
    }
    @Test fun productionAmberUsesDarkTextAndDarkActionsKeepWhite() {
        assertTrue(homeSwipeUsesDarkForeground(245/255f, 158/255f, 11/255f))
        assertTrue(homeSwipeUsesDarkForeground(1f, 1f, 1f))
        assertTrue(!homeSwipeUsesDarkForeground(0f, 0f, 0f))
        assertTrue(!homeSwipeUsesDarkForeground(0f, .2f, .1f))
    }
    @Test fun winningBlackWhiteContrastStaysAboveNormalTextTarget() {
        fun linear(c: Double) = if (c <= .04045) c/12.92 else Math.pow((c+.055)/1.055, 2.4)
        for (r in 0..20) for (g in 0..20) for (b in 0..20) {
            val red = r/20f; val green = g/20f; val blue = b/20f
            val l = .2126*linear(red.toDouble()) + .7152*linear(green.toDouble()) + .0722*linear(blue.toDouble())
            val ratio = if (homeSwipeUsesDarkForeground(red, green, blue)) (l+.05)/.05 else 1.05/(l+.05)
            assertTrue("Insufficient contrast $ratio for $red,$green,$blue", ratio >= 4.5)
        }
    }
}
