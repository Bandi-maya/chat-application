package com.example.chat

import com.example.chat.ui.chats.detail.ActiveVoiceClock
import org.junit.Assert.*
import org.junit.Test

class ActiveVoiceClockTest {
    @Test fun pausedTimeNeverCountsTowardSendMinimum() {
        var now = 0L
        val clock = ActiveVoiceClock { now }
        clock.start(); now = 600; clock.pause(); now = 100_000
        assertTrue(clock.paused); assertEquals(600L, clock.elapsedMs)
        clock.resume(); now += 500
        assertEquals(1100L, clock.elapsedMs)
    }
    @Test fun repeatedPauseResumeIsIdempotentAndResetDropsPriorSession() {
        var now = 0L
        val clock = ActiveVoiceClock { now }
        clock.start(); now = 1200; clock.pause(); clock.pause(); now = 5000; clock.resume(); clock.resume()
        now = 5300; assertEquals(1500L, clock.elapsedMs)
        clock.reset(); assertEquals(0L, clock.elapsedMs); assertFalse(clock.paused)
        clock.start(); now += 10; assertEquals(10L, clock.elapsedMs)
    }
}
