package com.example.chat

import com.example.chat.ui.camera.SegmentFrameSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ownership-rule tests for the live segment-preview frame slot. */
class SegmentFrameSlotTest {

    private class Frame(val id: Int)
    private class Recorder {
        val recycled = mutableListOf<Int>()
        val slot = SegmentFrameSlot<Frame> { recycled.add(it.id) }
    }

    @Test fun publishShowsFrameAndNeverRecyclesAPublishedFrame() {
        val r = Recorder()
        val a = Frame(1); val b = Frame(2); val c = Frame(3)
        assertTrue(r.slot.publish(a))
        assertSame(a, r.slot.visible)
        assertTrue(r.slot.publish(b))
        assertSame(b, r.slot.visible)
        assertTrue(r.slot.publish(c))
        assertSame(c, r.slot.visible)
        // Replaced frames are dereferenced only: nothing published is recycled,
        // ever. GC reclaims them.
        assertTrue(r.recycled.isEmpty())
    }

    @Test fun staleFrameIsRecycledOnceBeforePublishAndNeverShown() {
        // The effect key changed mid-render: the frame is rejected before
        // Compose ever sees it, so immediate recycle is safe and single.
        val r = Recorder()
        val a = Frame(1)
        r.slot.publish(a)
        val stale = Frame(7)
        assertFalse(r.slot.publish(stale, accept = false))
        assertSame(a, r.slot.visible)
        assertEquals(listOf(7), r.recycled)
    }

    @Test fun publishAfterDisposeRecyclesTheRejectedFrameOnce() {
        val r = Recorder()
        val a = Frame(1)
        r.slot.publish(a)
        r.slot.dispose()
        r.recycled.clear()
        val late = Frame(9)
        assertFalse(r.slot.publish(late))
        assertNull(r.slot.visible)
        assertEquals(listOf(9), r.recycled)
    }

    @Test fun clearAndDisposeDereferenceWithoutRecycle() {
        val r = Recorder()
        val a = Frame(1); val b = Frame(2)
        r.slot.publish(a)
        r.slot.clear()
        assertNull(r.slot.visible)
        assertTrue(r.recycled.isEmpty())
        r.slot.publish(b)
        r.slot.dispose()
        assertNull(r.slot.visible)
        // No published frame was ever recycled, including at teardown.
        assertTrue(r.recycled.isEmpty())
        assertFalse(r.slot.isAlive)
    }

    @Test fun disposeIsIdempotentAndClearAfterDisposeIsANoOp() {
        val r = Recorder()
        val a = Frame(1)
        r.slot.publish(a)
        r.slot.dispose()
        r.slot.dispose()
        r.slot.clear()
        assertNull(r.slot.visible)
        assertTrue(r.recycled.isEmpty())
    }

    @Test fun cancelledAfterSuccessfulHandoffRecyclesNothing() {
        // Models a render coroutine cancelled while parked on the main hop:
        // the NonCancellable handoff completed, the slot owns the frame, and
        // the coroutine's cancellation path observes empty ownership
        // (produced == null) and recycles nothing.
        val r = Recorder()
        val a = Frame(1)
        assertTrue(r.slot.publish(a))
        assertSame(a, r.slot.visible)
        assertTrue(r.recycled.isEmpty())
    }
}
