package com.example.chat.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transition coverage for [ForegroundCallRinger] (Bandi's foreground incoming-call ring,
 * coordinator-owned): the ring starts only while a live incoming call is present AND the app is
 * foreground, and every departure from that state stops it - answer, decline, end, background,
 * account switch / sign-out. Backgrounded calls must never start it (the system notification
 * rings there; both together would double-ring).
 */
class ForegroundCallRingerTest {

    private class FakePlayer : RingerPlayer {
        var playing = false
        var startCount = 0
        var stopCount = 0
        var failNextStart = false
        override val isPlaying: Boolean get() = playing
        override fun start() {
            if (failNextStart) { failNextStart = false; throw RuntimeException("synthetic ringtone failure") }
            startCount++; playing = true
        }
        override fun stop() { stopCount++; playing = false }
    }

    @Test
    fun foregroundIncomingCallRingsOnce() {
        val p = FakePlayer()
        val r = ForegroundCallRinger(p)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        assertTrue(p.playing)
        assertEquals(1, p.startCount)
        // Repeated snapshots of the same ringing state never restart it.
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        assertEquals(1, p.startCount)
        assertEquals(0, p.stopCount)
    }

    @Test
    fun noRingWithoutLiveIncomingCall() {
        val p = FakePlayer()
        val r = ForegroundCallRinger(p)
        r.onCallSnapshot(liveIncomingCall = false, appForeground = true)
        r.onCallSnapshot(liveIncomingCall = false, appForeground = false)
        assertFalse(p.playing)
        assertEquals(0, p.startCount)
        assertEquals(0, p.stopCount)
    }

    @Test
    fun noInAppRingWhileBackgrounded() {
        val p = FakePlayer()
        val r = ForegroundCallRinger(p)
        // Backgrounded incoming call: the system notification rings, never this player.
        r.onCallSnapshot(liveIncomingCall = true, appForeground = false)
        assertFalse(p.playing)
        assertEquals(0, p.startCount)
    }

    @Test
    fun ringStopsWhenCallIsAnsweredDeclinedOrEnded() {
        val p = FakePlayer()
        val r = ForegroundCallRinger(p)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        // Any transition out of the live-incoming state (session cleared or terminal) stops it.
        r.onCallSnapshot(liveIncomingCall = false, appForeground = true)
        assertFalse(p.playing)
        assertEquals(1, p.stopCount)
        // A further idle snapshot does not stop again.
        r.onCallSnapshot(liveIncomingCall = false, appForeground = true)
        assertEquals(1, p.stopCount)
    }

    @Test
    fun ringStopsWhenAppBackgrounds() {
        val p = FakePlayer()
        val r = ForegroundCallRinger(p)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = false)
        assertFalse(p.playing)
        assertEquals(1, p.stopCount)
    }

    @Test
    fun explicitStopCoversAccountSwitchAndSignOut() {
        val p = FakePlayer()
        val r = ForegroundCallRinger(p)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        r.stop()
        assertFalse(p.playing)
        assertEquals(1, p.stopCount)
        r.stop() // idempotent
        assertEquals(1, p.stopCount)
    }

    @Test
    fun foregroundRingFollowsCallAcrossBackgroundForegroundCycle() {
        val p = FakePlayer()
        val r = ForegroundCallRinger(p)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = false)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        assertTrue(p.playing)
        assertEquals(2, p.startCount)
        assertEquals(1, p.stopCount)
    }

    @Test
    fun failedStartDoesNotWedgeAndNextSnapshotRetries() {
        val p = FakePlayer()
        p.failNextStart = true
        val r = ForegroundCallRinger(p)
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        assertFalse(p.playing)
        // State stayed consistent, so the identical next snapshot retries the start.
        r.onCallSnapshot(liveIncomingCall = true, appForeground = true)
        assertTrue(p.playing)
        assertEquals(1, p.startCount)
    }
}
