package com.example.chat.ui.split

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitChatLayoutPolicyTest {

    @Test
    fun resolvePresentationMode_onePane_returnsFloatingMini() {
        val mode = SplitChatLayoutPolicy.resolvePresentationMode(
            paneCount = 1,
            windowWidthDp = 400.dp,
            windowHeightDp = 800.dp,
        )
        assertEquals(SplitPresentationMode.FLOATING_MINI, mode)
    }

    @Test
    fun resolvePresentationMode_twoPanes_returnsTwoPaneStack() {
        val mode = SplitChatLayoutPolicy.resolvePresentationMode(
            paneCount = 2,
            windowWidthDp = 400.dp,
            windowHeightDp = 800.dp,
        )
        assertEquals(SplitPresentationMode.TWO_PANE_STACK, mode)
    }

    @Test
    fun resolvePresentationMode_threePanes_returnsThreePaneGrid() {
        val mode = SplitChatLayoutPolicy.resolvePresentationMode(
            paneCount = 3,
            windowWidthDp = 600.dp,
            windowHeightDp = 900.dp,
        )
        assertEquals(SplitPresentationMode.THREE_PANE_GRID, mode)
    }

    @Test
    fun resolvePresentationMode_fourPanes_returnsFourPaneGrid() {
        val mode = SplitChatLayoutPolicy.resolvePresentationMode(
            paneCount = 4,
            windowWidthDp = 800.dp,
            windowHeightDp = 900.dp,
        )
        assertEquals(SplitPresentationMode.FOUR_PANE_GRID, mode)
    }

    @Test
    fun resolvePresentationMode_imeVisible_fallsBackToCompactSwitcher() {
        val mode = SplitChatLayoutPolicy.resolvePresentationMode(
            paneCount = 3,
            windowWidthDp = 800.dp,
            windowHeightDp = 900.dp,
            isImeVisible = true,
        )
        assertEquals(SplitPresentationMode.COMPACT_SWITCHER, mode)
    }

    @Test
    fun resolvePresentationMode_tightHeight_fallsBackToCompactSwitcher() {
        val mode = SplitChatLayoutPolicy.resolvePresentationMode(
            paneCount = 2,
            windowWidthDp = 600.dp,
            windowHeightDp = 350.dp,
        )
        assertEquals(SplitPresentationMode.COMPACT_SWITCHER, mode)
    }

    @Test
    fun clampOffset_clampsCorrectlyInsideScreen() {
        val (x, y) = SplitChatLayoutPolicy.clampOffset(
            currentX = -50f,
            currentY = 1500f,
            windowWidthPx = 1080f,
            windowHeightPx = 1920f,
            miniWidthPx = 300f,
            miniHeightPx = 400f,
            marginPx = 20f,
        )
        assertEquals(20f, x, 0.01f)
        assertEquals(1500f.coerceAtMost(1920f - 400f - 20f), y, 0.01f)
    }
}

