package com.example.chat.ui.kit

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatyMenuPositionProviderTest {
    private val position = ChatyMenuPositionProvider(8)

    @Test fun opensBelowAnchorWhenThereIsRoom() {
        val p = position.calculatePosition(IntRect(40, 80, 120, 120), IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 250))
        assertEquals(40, p.x)
        assertEquals(128, p.y)
    }

    @Test fun flipsAboveNearBottomAndClampsHorizontalEdge() {
        val p = position.calculatePosition(IntRect(350, 650, 390, 700), IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 250))
        assertEquals(192, p.x)
        assertEquals(392, p.y)
    }

    @Test fun rtlAlignsToAnchorEndAndSmallWindowCannotCrash() {
        val p = position.calculatePosition(IntRect(120, 80, 200, 120), IntSize(400, 800), LayoutDirection.Rtl, IntSize(176, 250))
        assertEquals(24, p.x)
        val tiny = position.calculatePosition(IntRect(0, 0, 10, 10), IntSize(100, 100), LayoutDirection.Ltr, IntSize(200, 250))
        assertTrue(tiny.x >= 8 && tiny.y >= 8)
    }
}
