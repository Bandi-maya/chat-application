package com.example.chat.core.preferences

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PreferencesSaveTrackerTest {
    private fun tr(t: () -> Long = { 1 }) = PreferencesSaveTracker(t).also { it.resetScope("u") }

    @Test fun startsIdle() { assertEquals(SaveState.Idle, PreferencesSaveTracker { 1 }.state.value) }

    @Test fun failureThenSuccessClears() = runBlocking {
        var t = 10L; var fail = true
        val w = OrderedBlobWriter(maxAttempts = 2, backoffMs = 1) { _, _ -> if (fail) error("disk") }
        val x = tr { t }; val g = x.currentGeneration()
        var ok = w.write(w.nextSeq(), "u", mapOf("a" to 1))
        x.onWriteFinished(g, "u", ok, w.hasUnsavedFailure("u"))
        assertEquals(SaveState.Failed(10L), x.state.value)
        t = 20
        ok = w.write(w.nextSeq(), "u", mapOf("a" to 2))
        x.onWriteFinished(g, "u", ok, w.hasUnsavedFailure("u"))
        assertEquals(SaveState.Failed(10L), x.state.value)
        fail = false
        ok = w.write(w.nextSeq(), "u", mapOf("a" to 3))
        x.onWriteFinished(g, "u", ok, w.hasUnsavedFailure("u"))
        assertEquals(SaveState.Saved, x.state.value)
    }

    @Test fun skippedStaleSnapshotKeepsState() {
        val x = tr(); val g = x.currentGeneration()
        x.onWriteFinished(g, "u", false, true)
        x.onWriteFinished(g, "u", false, false)
        assertTrue(x.state.value is SaveState.Failed)
        val y = tr()
        y.onWriteFinished(y.currentGeneration(), "u", false, false)
        assertEquals(SaveState.Idle, y.state.value)
    }

    @Test fun oldUserInflightResultIgnoredAfterAccountSwitch() {
        val x = tr(); val oldGen = x.currentGeneration()
        x.resetScope("v")
        x.onWriteFinished(oldGen, "u", false, true)
        assertEquals(SaveState.Idle, x.state.value)
        x.onWriteFinished(oldGen, "u", true, false)
        assertEquals(SaveState.Idle, x.state.value)
        x.onWriteFinished(x.currentGeneration(), "v", true, false)
        assertEquals(SaveState.Saved, x.state.value)
    }

    @Test fun resetClearsFailed() {
        val x = tr(); x.onWriteFinished(x.currentGeneration(), "u", false, true)
        x.resetScope(null)
        assertEquals(SaveState.Idle, x.state.value)
    }

    @Test fun sameUserReloginNewGenerationIgnoresOldCallback() {
        val x = tr(); val g = x.currentGeneration()
        x.resetScope("u")
        x.onWriteFinished(g, "u", false, true)
        assertEquals(SaveState.Idle, x.state.value)
    }
}
