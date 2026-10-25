package com.example.chat

import com.example.chat.ui.settings.messagemanagement.formatScheduledDateTime
import com.example.chat.ui.settings.messagemanagement.quickReplyTitle
import com.example.chat.ui.settings.messagemanagement.shouldDeleteRuleById
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageManagementParityTest {

    @Test
    fun `format matches Dart d slash M slash yyyy H colon mm`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val value = ZonedDateTime.of(2026, 10, 25, 9, 5, 0, 0, java.time.ZoneId.of("UTC"))
        assertEquals("25/10/2026 9:05", formatScheduledDateTime(value))
        // single-digit hour NOT padded, minute padded (Dart interpolation).
        val early = ZonedDateTime.of(2026, 1, 3, 4, 7, 0, 0, java.time.ZoneId.of("UTC"))
        assertEquals("3/1/2026 4:07", formatScheduledDateTime(early))
    }

    @Test
    fun `delete routing is id length 36 else signature`() {
        assertTrue(shouldDeleteRuleById("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(shouldDeleteRuleById("rule_1700000000000"))
        assertFalse(shouldDeleteRuleById(""))
    }

    @Test
    fun `quick reply title falls back to shortcut`() {
        assertEquals("#thanks", quickReplyTitle("#thanks", ""))
        assertEquals("Gratitude", quickReplyTitle("#thanks", "Gratitude"))
    }
}
