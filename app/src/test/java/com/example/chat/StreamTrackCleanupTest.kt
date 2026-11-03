package com.example.chat

import com.example.chat.data.services.StreamTrackCleanup
import org.junit.Assert.*
import org.junit.Test

class StreamTrackCleanupTest {
    private class Track {
        var calls = 0
        fun dispose() { check(calls == 0) { "MediaStreamTrack has been disposed" }; calls++ }
    }
    @Test fun streamOwnsAddedTracksNeverDisposeTwice() {
        val a = Track(); val v = Track()
        StreamTrackCleanup.dispose({ a.dispose(); v.dispose() }, listOf(true to a::dispose, true to v::dispose))
        assertEquals(1, a.calls); assertEquals(1, v.calls)
    }
    @Test fun detachedTracksReleasedWhenStreamExists() {
        val a = Track(); val v = Track()
        StreamTrackCleanup.dispose({ a.dispose() }, listOf(true to a::dispose, false to v::dispose))
        assertEquals(1, a.calls); assertEquals(1, v.calls)
    }
    @Test fun partiallyCreatedTracksReleasedWithoutStream() {
        val a = Track()
        StreamTrackCleanup.dispose(null, listOf(false to a::dispose))
        assertEquals(1, a.calls)
    }
    @Test fun emptyCleanupIsSafeRepeatedly() { repeat(20) { StreamTrackCleanup.dispose(null, emptyList()) } }
    @Test fun repeatedCallCyclesDisposeEachWrapperOnce() {
        repeat(100) {
            val a = Track(); val v = Track()
            StreamTrackCleanup.dispose({ a.dispose(); v.dispose() }, listOf(true to a::dispose, true to v::dispose))
        }
    }
    @Test fun oldTrackFirstOrderingReproducesCrash() {
        val a = Track(); a.dispose()
        assertThrows(IllegalStateException::class.java) { a.dispose() }
    }
}
