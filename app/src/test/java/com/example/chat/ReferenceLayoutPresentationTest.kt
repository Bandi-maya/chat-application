package com.example.chat

import com.example.chat.ui.kit.ReferenceLayoutPresentation
import com.example.chat.ui.kit.ReferenceLayoutStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceLayoutPresentationTest {
    @Test fun layoutCornersStayDistinct() {
        assertEquals(24, ReferenceLayoutPresentation(ReferenceLayoutStyle.Default).menuCornerDp)
        assertEquals(4, ReferenceLayoutPresentation(ReferenceLayoutStyle.Old).menuCornerDp)
        assertEquals(14, ReferenceLayoutPresentation(ReferenceLayoutStyle.Ios).menuCornerDp)
        assertEquals(10, ReferenceLayoutPresentation().menuCornerDp)
    }
    @Test fun reducedMotionSkipsMenuEntryAcrossEveryStyle() {
        ReferenceLayoutStyle.entries.forEach { style ->
            assertEquals(0, ReferenceLayoutPresentation(style, true).menuEnterMs)
            assertEquals(140, ReferenceLayoutPresentation(style, false).menuEnterMs)
            assertEquals(0, ReferenceLayoutPresentation(style, true).menuExitMs)
            assertEquals(90, ReferenceLayoutPresentation(style, false).menuExitMs)
        }
    }
}
