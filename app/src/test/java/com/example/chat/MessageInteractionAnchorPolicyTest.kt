package com.example.chat.ui.chats.detail

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInteractionAnchorPolicyTest {

    @Test
    fun computePlacement_sufficientHeadroom_placesRailAboveAndMenuBelow() {
        // Viewport: 400dp x 800dp. Density = 2.0 (so 1dp = 2px)
        // Bubble at y = 300dp..360dp (px: 600..720)
        val bubble = Rect(left = 100f, top = 600f, right = 600f, bottom = 720f)
        val placement = MessageInteractionAnchorPolicy.computePlacement(
            bubbleBounds = bubble,
            viewportWidthDp = 400.dp,
            viewportHeightDp = 800.dp,
            density = 2.0f,
            isOutgoing = true,
        )

        // bubbleTopDp = 300.dp. Space above = 300 - 8 = 292dp >= 48 + 8 = 56dp.
        // Rail should be above: 300 - 48 - 8 = 244.dp
        assertEquals(244.dp, placement.reactionRailTopDp)
        // Menu should be below bubble: 360 + 8 = 368.dp
        assertEquals(368.dp, placement.menuTopDp)
        assertTrue(placement.menuMaxHeightDp > 200.dp)
    }

    @Test
    fun computePlacement_tightHeadroom_placesRailAndMenuBelow() {
        // Viewport: 400dp x 800dp. Bubble near top: top = 20dp (px: 40), bottom = 80dp (px: 160)
        val bubble = Rect(left = 100f, top = 40f, right = 600f, bottom = 160f)
        val placement = MessageInteractionAnchorPolicy.computePlacement(
            bubbleBounds = bubble,
            viewportWidthDp = 400.dp,
            viewportHeightDp = 800.dp,
            density = 2.0f,
            isOutgoing = false,
        )

        // Space above = 20 - 8 = 12dp < 56dp. Both rail and menu should be placed below bubble.
        // Rail at bottom of bubble + 8dp = 80 + 8 = 88.dp
        assertEquals(88.dp, placement.reactionRailTopDp)
        // Menu at railBottom + 8dp = 88 + 48 + 8 = 144.dp
        assertEquals(144.dp, placement.menuTopDp)
    }

    @Test
    fun computePlacement_clamping_respectsViewportBoundaries() {
        val bubble = Rect(left = 0f, top = 0f, right = 400f, bottom = 200f)
        val placement = MessageInteractionAnchorPolicy.computePlacement(
            bubbleBounds = bubble,
            viewportWidthDp = 320.dp,
            viewportHeightDp = 400.dp,
            density = 1.0f,
            isOutgoing = true,
        )

        assertTrue(placement.reactionRailLeftDp >= 8.dp)
        assertTrue(placement.menuLeftDp >= 8.dp)
        assertTrue(placement.menuMaxHeightDp >= 100.dp)
    }
}
