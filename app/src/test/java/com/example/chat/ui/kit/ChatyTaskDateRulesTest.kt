package com.example.chat.ui.kit

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ChatyTaskDateRulesTest {
    @Test fun leapMonthHasEveryDayOnceAndMondayOffset() {
        val cells = ChatyTaskDateRules.cells(YearMonth.of(2028, 2))
        assertEquals(42, cells.size)
        assertNull(cells.first())
        assertEquals(LocalDate.of(2028, 2, 1), cells[1])
        assertEquals(29, cells.filterNotNull().size)
        assertEquals(29, cells.filterNotNull().distinct().size)
    }
    @Test fun boundsIncludeTodayAndLastAllowedDay() {
        val today = LocalDate.of(2026, 10, 26)
        assertFalse(ChatyTaskDateRules.selectable(today.minusDays(1), today))
        assertTrue(ChatyTaskDateRules.selectable(today, today))
        assertTrue(ChatyTaskDateRules.selectable(today.plusDays(3650), today))
        assertFalse(ChatyTaskDateRules.selectable(today.plusDays(3651), today))
    }
    @Test fun selectionPreservesLocalTimeAndDoesNotShiftDayThroughUtc() {
        val zone = ZoneId.of("Asia/Kolkata")
        val old = Instant.parse("2026-10-26T13:12:42Z")
        val chosen = LocalDate.of(2026, 11, 3)
        val result = ChatyTaskDateRules.replaceDate(old, chosen, zone).atZone(zone)
        assertEquals(chosen, result.toLocalDate())
        assertEquals(18, result.hour)
        assertEquals(42, result.minute)
        assertEquals(0, result.second)
    }
}
