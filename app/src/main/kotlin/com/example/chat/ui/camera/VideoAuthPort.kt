package com.example.chat.ui.camera

import java.util.concurrent.atomic.AtomicBoolean

/** Snapshot and listener values use reference identity. Adapter must not run IO in withScope. */
interface VideoAuthPort {
    fun snapshot(): Any
    fun isSignedIn(snapshot: Any): Boolean
    fun withScope(expected: Any, block: () -> Unit): Boolean
    fun addListener(listener: (Any) -> Unit)
    fun removeListener(listener: (Any) -> Unit)
}

/** Thread-safe denial happens in listener; UI/reader cleanup is posted separately. */
class VideoScopeLease(private val port: VideoAuthPort, private val onInvalidated: () -> Unit) : AutoCloseable {
    private val expected = port.snapshot()
    private val valid = AtomicBoolean(port.isSignedIn(expected))
    private val listener: (Any) -> Unit = { next ->
        if (next !== expected && valid.getAndSet(false)) onInvalidated()
    }
    init { port.addListener(listener); if (!port.isSignedIn(expected)) onInvalidated() }
    fun runIfCurrent(block: () -> Unit): Boolean {
        if (!valid.get()) return false
        return port.withScope(expected) { if (valid.get()) block() } && valid.get()
    }
    fun isCurrent(): Boolean = runIfCurrent {}
    override fun close() { valid.set(false); port.removeListener(listener) }
}
