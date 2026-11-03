package com.example.chat

import com.example.chat.ui.updates.*
import org.junit.Assert.*
import org.junit.Test

class ReleaseHistoryCodecTest {
    @Test fun roundTripPreservesEveryFieldAndSpecialText() {
        val entries = listOf(ReleaseHistoryEntry("1.0.0", 1, "r2", listOf("Quotes \"x\"", "Line\nTwo", "emoji 😀")))
        assertEquals(entries, ReleaseHistoryCodec.read(ReleaseHistoryCodec.write(entries)))
    }
    @Test fun corruptPayloadQuarantinedAndRecoveryAllowed() {
        val recovered = ReleaseHistoryCodec.recover("broken", null)
        assertTrue(recovered.canWrite); assertEquals("broken", recovered.quarantine)
        assertEquals(emptyList<ReleaseHistoryEntry>(), recovered.entries)
    }
    @Test fun secondDifferentCorruptionNeverDestroysEitherPayload() {
        val recovered = ReleaseHistoryCodec.recover("new broken", "old broken")
        assertFalse(recovered.canWrite); assertEquals("old broken", recovered.quarantine)
    }
    @Test fun alreadyQuarantinedIdenticalPayloadCanRecover() {
        assertTrue(ReleaseHistoryCodec.recover("broken", "broken").canWrite)
    }
    @Test fun validHistoryRetainsBackup() {
        val recovered = ReleaseHistoryCodec.recover("[]", "old broken")
        assertTrue(recovered.canWrite); assertEquals("old broken", recovered.quarantine)
    }
}
