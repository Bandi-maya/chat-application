package com.example.chat

import com.example.chat.data.polls.LiveLocationPolicy
import com.example.chat.data.polls.PlaybackSpeedPolicy
import com.example.chat.data.polls.Poll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollsAndLocationTest {

    @Test fun `votes tally and revote replaces`() {
        val p = Poll("p1", "Lunch?", listOf("Pizza", "Sushi"))
        p.vote("a", 0); p.vote("b", 1); p.vote("a", 1)
        assertEquals(listOf(0, 2), p.tally())
        assertEquals(2, p.totalVoters())
    }

    @Test fun `closed poll rejects votes`() {
        val p = Poll("p1", "Q", listOf("A"))
        p.close()
        assertFalse(p.vote("a", 0))
        assertFalse(p.vote("a", 5))
    }

    @Test fun `live location window and manual stop`() {
        assertTrue(LiveLocationPolicy.isLive(0, 15, 14 * 60_000L, manuallyStopped = false))
        assertFalse(LiveLocationPolicy.isLive(0, 15, 16 * 60_000L, manuallyStopped = false))
        assertFalse(LiveLocationPolicy.isLive(0, 480, 1_000L, manuallyStopped = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid duration rejected`() { LiveLocationPolicy.endMillis(0, 30) }

    @Test fun `playback speed cycles 1, 1_5, 2, back to 1`() {
        assertEquals(1.5f, PlaybackSpeedPolicy.nextSpeed(1.0f))
        assertEquals(2.0f, PlaybackSpeedPolicy.nextSpeed(1.5f))
        assertEquals(1.0f, PlaybackSpeedPolicy.nextSpeed(2.0f))
    }
}
