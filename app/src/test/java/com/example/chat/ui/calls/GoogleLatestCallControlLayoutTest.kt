package com.example.chat.ui.calls

import org.junit.Assert.*
import org.junit.Test

class GoogleLatestCallControlLayoutTest {
    @Test fun compactWidthsStackAndPhonesUseGroupedColumns() {
        assertEquals(1, googleLatestCallControlColumns(100))
        assertEquals(2, googleLatestCallControlColumns(220))
        assertEquals(3, googleLatestCallControlColumns(328))
        assertEquals(3, googleLatestCallControlColumns(800))
    }
    @Test fun labelledControlsFitWithoutHorizontalOverlap() {
        for (width in 88..800) {
            val columns = googleLatestCallControlColumns(width)
            assertTrue("width=$width", columns * 88 + (columns - 1) * 12 <= width)
        }
    }
}
