package com.example.chat

import com.example.chat.ui.chats.detail.SECOND_REFERENCE_ENTRY_STYLE
import com.example.chat.ui.chats.detail.STOCK_ENTRY_STYLE
import com.example.chat.ui.chats.detail.normalizeEntryStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saved conversation-entry-style values normalize to a renderable style; the
 *  stock style is the user-supplied default reference layout. */
class ChatComposerEntryStyleTest {

    @Test fun blankFallsBackToStock() {
        assertEquals(STOCK_ENTRY_STYLE, normalizeEntryStyle(""))
        assertEquals(STOCK_ENTRY_STYLE, normalizeEntryStyle("   "))
    }

    @Test fun legacyStockNamesCollapseToStock() {
        assertEquals(STOCK_ENTRY_STYLE, normalizeEntryStyle("Stock"))
        assertEquals(STOCK_ENTRY_STYLE, normalizeEntryStyle("Stock (Chaty Default)"))
    }

    @Test fun namedStylesSurviveWithTrimming() {
        assertEquals(SECOND_REFERENCE_ENTRY_STYLE, normalizeEntryStyle("  Reference 2  "))
        assertEquals("YOWA Style", normalizeEntryStyle("YOWA Style"))
        assertEquals("Simple Pill", normalizeEntryStyle("Simple Pill"))
    }
}
