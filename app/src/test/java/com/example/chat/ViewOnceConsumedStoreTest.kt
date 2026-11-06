package com.example.chat

import com.example.chat.data.media.ViewOnceConsumedStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the view-once consumption contract: append-only (no eviction),
 *  durable-write success reporting, per-account isolation. Uses a fake
 *  Prefs seam; no Android needed. */
class ViewOnceConsumedStoreTest {

    /** In-memory fake; [failWrites] simulates disk failure. */
    private class FakePrefs(
        var failWrites: Boolean = false,
        var failReads: Boolean = false,
    ) : ViewOnceConsumedStore.Prefs {
        val data = mutableMapOf<String, Set<String>>()
        var writeCount = 0
        override fun read(key: String): Set<String> {
            if (failReads) throw RuntimeException("prefs unreadable")
            return data[key].orEmpty()
        }
        override fun write(key: String, ids: Set<String>): Boolean {
            writeCount++
            if (failWrites) return false
            data[key] = ids
            return true
        }
    }

    @Test fun consumedStartsEmpty() {
        assertTrue(ViewOnceConsumedStore.create(FakePrefs(), "acc1").consumed().isEmpty())
    }

    @Test fun markConsumedPersistsAndReportsSuccess() {
        val prefs = FakePrefs()
        val store = ViewOnceConsumedStore.create(prefs, "acc1")
        assertTrue(store.markConsumed("m1"))
        assertTrue(store.markConsumed("m2"))
        assertEquals(setOf("m1", "m2"), store.consumed())
        // A new store instance over the same prefs sees the persisted ids.
        assertEquals(setOf("m1", "m2"), ViewOnceConsumedStore.create(prefs, "acc1").consumed())
    }

    @Test fun noEvictionUnderVolume() {
        val prefs = FakePrefs()
        val store = ViewOnceConsumedStore.create(prefs, "acc1")
        val ids = (1..3000).map { "m$it" }
        ids.forEach { assertTrue(store.markConsumed(it)) }
        // Every id is retained; nothing is evicted, however large the set.
        assertEquals(ids.toSet(), store.consumed())
    }

    @Test fun duplicateMarkIsIdempotent() {
        val prefs = FakePrefs()
        val store = ViewOnceConsumedStore.create(prefs, "acc1")
        assertTrue(store.markConsumed("m1"))
        val writes = prefs.writeCount
        assertTrue(store.markConsumed("m1"))
        assertEquals(writes, prefs.writeCount) // no redundant write
        assertEquals(setOf("m1"), store.consumed())
    }

    @Test fun failedWriteReportsFalseAndDoesNotPersist() {
        val prefs = FakePrefs(failWrites = true)
        val store = ViewOnceConsumedStore.create(prefs, "acc1")
        assertFalse(store.markConsumed("m1"))
        // Nothing reached "disk": a fresh store does not see the id, and the
        // caller-side in-memory set is what keeps the card expired (screen).
        assertTrue(ViewOnceConsumedStore.create(prefs, "acc1").consumed().isEmpty())
    }

    @Test fun blankIdRejected() {
        val store = ViewOnceConsumedStore.create(FakePrefs(), "acc1")
        assertFalse(store.markConsumed(""))
        assertFalse(store.markConsumed("   "))
    }

    @Test fun readFailureReturnsFailedNeverEmpty() {
        // Initial-load failure behavior: the safe read reports Failed
        // (unknown), which callers must fail closed on — an unreadable store
        // must not look like an empty one.
        val store = ViewOnceConsumedStore.create(FakePrefs(failReads = true), "acc1")
        assertEquals(ViewOnceConsumedStore.ReadResult.Failed, store.readConsumed())
    }

    @Test fun readSuccessReturnsOkWithIds() {
        val prefs = FakePrefs()
        val store = ViewOnceConsumedStore.create(prefs, "acc1")
        assertTrue(store.markConsumed("m1"))
        assertEquals(
            ViewOnceConsumedStore.ReadResult.Ok(setOf("m1")),
            store.readConsumed(),
        )
    }

    @Test fun accountsAreIsolated() {
        val prefs = FakePrefs()
        assertTrue(ViewOnceConsumedStore.create(prefs, "acc1").markConsumed("m1"))
        assertTrue(ViewOnceConsumedStore.create(prefs, "acc2").consumed().isEmpty())
        assertEquals(setOf("m1"), ViewOnceConsumedStore.create(prefs, "acc1").consumed())
    }
}
