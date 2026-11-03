package com.example.chat

import com.example.chat.data.services.LocalMediaPreparation
import org.junit.Assert.*
import org.junit.Test

class LocalMediaPreparationTest {
    @Test fun cameraFailureRollsBackBeforeRetry() {
        var stream = false; var audio = false; var video = false; var attached = false; var ready = false
        val failure = IllegalStateException("camera failed")
        val caught = assertThrows(IllegalStateException::class.java) {
            LocalMediaPreparation.run({ stream = true; audio = true; throw failure }, {
                stream = false; audio = false; video = false; attached = false; ready = false
            })
        }
        assertSame(failure, caught); assertFalse(stream); assertFalse(audio); assertFalse(ready)
        LocalMediaPreparation.run({
            check(!stream); stream = true; audio = true; video = true; attached = true; ready = true
        }, { fail("No rollback on success") })
        assertTrue(stream && audio && video && attached && ready)
    }
    @Test fun rollbackFailureDoesNotReplaceInitializationFailure() {
        val original = IllegalStateException("init"); val cleanup = IllegalArgumentException("cleanup")
        val caught = assertThrows(IllegalStateException::class.java) {
            LocalMediaPreparation.run({ throw original }, { throw cleanup })
        }
        assertSame(original, caught); assertSame(cleanup, caught.suppressed.single())
    }
    @Test fun streamCreationFailureStillRollsBack() {
        var rolledBack = false
        assertThrows(IllegalStateException::class.java) {
            LocalMediaPreparation.run({ error("stream") }, { rolledBack = true })
        }
        assertTrue(rolledBack)
    }
}
