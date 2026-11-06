package com.example.chat.ui.camera

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ownership rule for live segment-preview frames.
 *
 * The one rule that keeps Compose safe: a frame that has been published is
 * NEVER explicitly recycled. Once [visible] holds a bitmap, an in-flight or
 * already-recorded draw can still reference it across recomposition and
 * RenderThread boundaries, with no timing window that makes recycle() safe.
 * Replaced, cleared and torn-down frames are simply dereferenced and left to
 * the GC, which reclaims the pixels with the Bitmap.
 *
 * Explicit recycle happens ONLY for frames rejected before publish - a stale
 * effect key or a handoff landing after [dispose]. Compose never saw those,
 * so recycling them cannot race a draw.
 *
 * Single-owner transfer: a produced frame is handed to [publish] exactly once
 * and the slot owns it from that call on; the caller never recycles after the
 * handoff, so no frame is recycled twice or leaked.
 *
 * Threading contract: ALL calls run on the main thread. The camera analysis
 * producer renders on a background dispatcher and hands the frame over inside
 * a NonCancellable main-thread block, so publish, clear and dispose are
 * serialized by the main looper and cannot interleave.
 *
 * Pure Kotlin with a reject callback so the rule is JVM-testable.
 */
class SegmentFrameSlot<T : Any>(private val onRejected: (T) -> Unit) {

    /** The frame the viewfinder may draw. Null while nothing is shown. */
    var visible: T? = null
        private set

    private val alive = AtomicBoolean(true)

    val isAlive: Boolean get() = alive.get()

    /**
     * Take ownership of a freshly produced [frame].
     *
     * When [accept] is true and the slot is alive, the frame becomes [visible]
     * and the replaced frame is dereferenced WITHOUT recycle - Compose may
     * still be drawing it. In every other outcome (stale frame, disposed slot)
     * the frame was never published, so it is recycled here and now.
     * Returns true only when the frame became visible.
     */
    fun publish(frame: T, accept: Boolean = true): Boolean {
        if (alive.get() && accept) {
            visible = frame
            return true
        }
        onRejected(frame)
        return false
    }

    /**
     * Hide the visible frame (effect changed or deselected). The frame is
     * dereferenced, not recycled: a recorded draw may still reference it.
     */
    fun clear() {
        if (alive.get()) visible = null
    }

    /**
     * End the slot. The caller stops the analysis producer first; a producer
     * already in flight hands its frame to [publish] afterwards, which
     * recycles it because it can never be shown. The last visible frame is
     * dereferenced without recycle - teardown does not prove the RenderThread
     * released it. A second dispose does nothing.
     */
    fun dispose() {
        if (!alive.getAndSet(false)) return
        visible = null
    }
}
