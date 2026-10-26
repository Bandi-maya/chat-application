package com.example.chat

import com.example.chat.ui.navigation.DrawerLayoutMetrics
import org.junit.Assert.*
import org.junit.Test

class DrawerLayoutMetricsTest {
    @Test fun `normal dimensions stay unchanged`() {
        assertEquals(300f, DrawerLayoutMetrics.modernWidth(412f), 0.001f)
        assertEquals(210f, DrawerLayoutMetrics.perspectiveWidth(412f), 0.001f)
        assertEquals(220f, DrawerLayoutMetrics.perspectiveTranslation(412f), 0.001f)
    }
    @Test fun `narrow bounds retain reachable lane`() {
        for (width in listOf(160f, 200f, 220f, 240f, 320f, 360f, 800f)) {
            assertTrue(DrawerLayoutMetrics.modernWidth(width) + 48f <= width)
            assertTrue(DrawerLayoutMetrics.perspectiveWidth(width) + 48f <= width)
            assertTrue(DrawerLayoutMetrics.perspectiveTranslation(width) + 48f <= width)
        }
    }
    @Test fun `zero bounds never produce negative sizes`() {
        for (width in listOf(0f, 24f, 48f)) {
            assertTrue(DrawerLayoutMetrics.modernWidth(width) >= 0f)
            assertTrue(DrawerLayoutMetrics.perspectiveWidth(width) >= 0f)
            assertTrue(DrawerLayoutMetrics.perspectiveTranslation(width) >= 0f)
        }
    }
}
