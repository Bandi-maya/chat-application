package com.example.chat.templates
import com.example.chat.ui.navigation.referenceHeaderScrollOffset
import org.junit.Assert.assertEquals
import org.junit.Test
class HeaderScrollPolicyTest {
    @Test fun scrollCollapsesAndReversesWithoutChangingSelection() {
        assertEquals(-50f, referenceHeaderScrollOffset(0f, -50f, 194f), 0f)
        assertEquals(-194f, referenceHeaderScrollOffset(-50f, -500f, 194f), 0f)
        assertEquals(-144f, referenceHeaderScrollOffset(-194f, 50f, 194f), 0f)
        assertEquals(0f, referenceHeaderScrollOffset(-144f, 500f, 194f), 0f)
    }
    @Test fun zeroRangeNeverOvershoots() {
        assertEquals(0f, referenceHeaderScrollOffset(0f, -50f, 0f), 0f)
        assertEquals(0f, referenceHeaderScrollOffset(0f, 50f, 0f), 0f)
    }
}
