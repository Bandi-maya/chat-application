package com.example.chat.ui.calls
import org.junit.Assert.*
import org.junit.Test
class DialpadToneDispatcherTest {
    @Test fun allKeysDispatchExactlyOneBriefToneOnlyWhenAllowed() {
        val heard = mutableListOf<Pair<DialpadToneDispatcher.Tone, Int>>()
        val dispatcher = DialpadToneDispatcher { tone, ms -> heard += tone to ms; true }
        val keys = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "#", "+")
        keys.forEach { assertTrue(dispatcher.dispatch(it, true, true)) }
        assertEquals(DialpadToneDispatcher.Tone.entries + DialpadToneDispatcher.Tone.ZERO, heard.map { it.first })
        assertTrue(heard.all { it.second == 100 })
    }
    @Test fun disabledSilentVibrateAndInvalidInputNeverReachOutput() {
        var count = 0; val dispatcher = DialpadToneDispatcher { _, _ -> count++; true }
        for (key in listOf("2", "*", "#", "+")) {
            assertFalse(dispatcher.dispatch(key, false, true))
            assertFalse(dispatcher.dispatch(key, true, false))
            assertFalse(dispatcher.dispatch(key, false, false))
        }
        for (key in listOf("", "22", "A", "delete")) assertFalse(dispatcher.dispatch(key, true, true))
        assertEquals(0, count)
    }
    @Test fun rejectedAudioOutputIsNotReportedAsPlayed() {
        assertFalse(DialpadToneDispatcher { _, _ -> false }.dispatch("2", true, true))
    }
    @Test fun audioFailureDoesNotEscapeIntoDigitEntry() {
        val dispatcher = DialpadToneDispatcher { _, _ -> error("synthetic audio unavailable") }
        assertFalse(dispatcher.dispatch("2", true, true))
    }
}
