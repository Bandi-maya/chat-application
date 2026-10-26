package com.example.chat.core.preferences

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OrderedBlobWriterTest {
    private class Fake { val disk = HashMap<String?, Map<String, Any?>>(); var failTimes = 0; var calls = 0 }
    private fun writer(f: Fake, attempts: Int = 3) = OrderedBlobWriter(attempts, 1) { u, d ->
        f.calls++
        if (f.failTimes > 0) { f.failTimes--; throw java.io.IOException("x") }
        f.disk[u] = d
    }

    @Test fun `older snapshot arriving late never overwrites newer`() = runBlocking {
        val f = Fake(); val w = writer(f)
        val s1 = w.nextSeq(); val s2 = w.nextSeq()
        assertTrue(w.write(s2, "u", mapOf("v" to 2)))
        assertFalse(w.write(s1, "u", mapOf("v" to 1)))
        assertEquals(2, f.disk["u"]!!["v"]); assertEquals(1, f.calls)
    }

    @Test fun `per-user ordering - account switch keeps old account last save`() = runBlocking {
        val f = Fake(); val w = writer(f)
        val a = w.nextSeq(); val b = w.nextSeq()
        w.write(b, "new", mapOf("v" to "n")); w.write(a, "old", mapOf("v" to "o"))
        assertEquals("o", f.disk["old"]!!["v"]); assertEquals("n", f.disk["new"]!!["v"])
    }

    @Test fun `many concurrent writers end with highest sequence on disk`() = runBlocking {
        val f = Fake()
        val w = OrderedBlobWriter(3, 1) { u, d -> delay(1); f.disk[u] = d }
        val seqs = (1..200).map { w.nextSeq() }
        withContext(Dispatchers.Default) { seqs.shuffled().map { s -> launch { w.write(s, "u", mapOf("v" to s)) } }.joinAll() }
        assertEquals(200L, f.disk["u"]!!["v"])
    }

    @Test fun `cancelling the launcher does not drop a started write`() = runBlocking {
        val f = Fake(); val started = CompletableDeferred<Unit>()
        val w = OrderedBlobWriter(3, 1) { u, d -> started.complete(Unit); delay(50); f.disk[u] = d }
        val job = launch(Dispatchers.Default) { w.write(w.nextSeq(), "u", mapOf("v" to 1)) }
        started.await(); job.cancelAndJoin()
        assertEquals(1, f.disk["u"]!!["v"])
    }

    @Test fun `transient failure is retried and then succeeds`() = runBlocking {
        val f = Fake(); f.failTimes = 2; val w = writer(f)
        assertTrue(w.write(w.nextSeq(), "u", mapOf("v" to 1)))
        assertEquals(3, f.calls); assertFalse(w.hasUnsavedFailure("u"))
    }

    @Test fun `persistent failure is bounded, recorded, not marked written, and retryable`() = runBlocking {
        val f = Fake(); f.failTimes = 100; val w = writer(f)
        val s = w.nextSeq()
        assertFalse(w.write(s, "u", mapOf("v" to 1)))
        assertEquals(3, f.calls); assertTrue(w.hasUnsavedFailure("u")); assertNull(f.disk["u"])
        f.failTimes = 0
        assertTrue(w.write(s, "u", mapOf("v" to 1))) // same snapshot can be retried
        assertEquals(1, f.disk["u"]!!["v"]); assertFalse(w.hasUnsavedFailure("u"))
    }

    @Test fun `older snapshot after a failed newer one is skipped, next persist recovers`() = runBlocking {
        val f = Fake(); val w = writer(f)
        val s1 = w.nextSeq(); val s2 = w.nextSeq(); val s3 = w.nextSeq()
        assertTrue(w.write(s1, "u", mapOf("v" to 1)))
        f.failTimes = 100
        assertFalse(w.write(s3, "u", mapOf("v" to 3)))
        f.failTimes = 0
        assertFalse(w.write(s2, "u", mapOf("v" to 2)))      // stale, must not land
        assertEquals(1, f.disk["u"]!!["v"])
        assertTrue(w.write(w.nextSeq(), "u", mapOf("v" to 4)))
        assertEquals(4, f.disk["u"]!!["v"])
    }

    @Test fun `rapid toggles keep final value`() = runBlocking {
        val f = Fake(); val w = writer(f)
        repeat(50) { i -> w.write(w.nextSeq(), "u", mapOf("toggle" to (i % 2 == 0))) }
        assertEquals(false, f.disk["u"]!!["toggle"])
    }
}
