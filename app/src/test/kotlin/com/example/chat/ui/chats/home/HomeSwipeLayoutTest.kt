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
}
