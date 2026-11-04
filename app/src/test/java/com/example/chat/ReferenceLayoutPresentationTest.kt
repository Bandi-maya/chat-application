package com.example.chat

import com.example.chat.ui.kit.ReferenceLayoutPresentation
import com.example.chat.ui.kit.ReferenceLayoutStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceLayoutPresentationTest {
    @Test fun menuCornerRadiusIsTenAcrossEveryStyle() {
        // Contract: popup menus/dialogs use a 10dp corner radius for every
        // layout style (was per-layout 24/4/14/10). The user asked for the
        // radius to stay fixed at 10 ("10px", interpreted as 10dp pending his
        // unit confirmation), not to vary per layout.
        ReferenceLayoutStyle.entries.forEach { style ->
            assertEquals(10, ReferenceLayoutPresentation(style).menuCornerDp)
            assertEquals(10, ReferenceLayoutPresentation(style, true).menuCornerDp)
        }
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
