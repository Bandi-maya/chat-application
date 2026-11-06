package com.example.chat

import com.example.chat.core.audio.releaseAfterReader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReaderLeaseReleaseTest {
    @Test fun noReaderReleasesImmediately() {
        var count = 0
        releaseAfterReader(null) { count++ }
        assertEquals(1, count)
    }
    @Test fun readerFinallyPrecedesLeaseRelease() = runBlocking {
        val cleanupMayFinish = CompletableDeferred<Unit>()
        val cleanupEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val reader = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() }
            finally {
                withContext(NonCancellable) {
                    cleanupEntered.complete(Unit)
                    cleanupMayFinish.await()
                    events += "codec closed"
                }
            }
        }
        releaseAfterReader(reader) { events += "lease released" }
        cleanupEntered.await()
        assertTrue(events.isEmpty())
        cleanupMayFinish.complete(Unit)
        reader.join()
        assertEquals(listOf("codec closed", "lease released"), events)
    }
    @Test fun completedReaderReleasesImmediatelyOnce() = runBlocking {
        val reader = launch { }
        reader.join()
        var count = 0
        releaseAfterReader(reader) { count++ }
        assertEquals(1, count)
    }
}
