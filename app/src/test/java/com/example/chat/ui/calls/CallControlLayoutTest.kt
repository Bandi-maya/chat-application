package com.example.chat.ui.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallControlLayoutTest {
    @Test fun voiceControlsUseFullWidthWithoutOverflow() { assertEquals(2, visibleCallActionCount(328, 2)) }
    @Test fun allVideoControlsWhenTheyFit() { assertEquals(5, visibleCallActionCount(380, 5)) }
    @Test fun narrowVideoKeepsEndAndMore() { assertEquals(3, visibleCallActionCount(328, 5)); assertEquals(1, visibleCallActionCount(200, 5)) }
    @Test fun extremeCompactUsesVerticalMoreAndEnd() { assertEquals(0, visibleCallActionCount(80, 5)) }
    @Test fun boundsAcrossPhoneWidths() {
        for (width in 116..800) for (actions in 1..5) {
            val shown = visibleCallActionCount(width, actions)
            val controls = shown + 1 + if (shown < actions) 1 else 0
            assertTrue("controls fit width=$width", controls * 54 + (controls - 1) * 8 <= width)
        }
    }
}
