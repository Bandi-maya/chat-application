package com.example.chat.ui.calls

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CallActionRunnerTest {
    @Test fun successfulActionDoesNotShowFailure() = runBlocking {
        var failures = 0; var ran = false
        assertTrue(runCallAction({ failures++ }) { ran = true })
        assertTrue(ran); assertEquals(0, failures)
    }
    @Test fun permissionOrMediaFailureIsContainedWithoutRawExceptionDisclosure() = runBlocking {
        var failures = 0
        assertFalse(runCallAction({ failures++ }) { throw SecurityException("sensitive server body") })
        assertEquals(1, failures)
    }
    @Test fun cancellationIsRethrownWithoutFailureFeedback() = runBlocking {
        var failures = 0
        val expected = CancellationException("scope ended")
        try { runCallAction({ failures++ }) { throw expected }; fail("Cancellation must propagate") }
        catch (actual: CancellationException) { assertSame(expected, actual) }
        assertEquals(0, failures)
    }
}
