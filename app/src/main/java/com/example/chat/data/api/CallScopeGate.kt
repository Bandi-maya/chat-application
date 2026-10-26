package com.example.chat.data.api

/** One lock owns scope and delivery. A reset cannot interleave between identity
 * check and final listener invocation. Listener must be nonblocking and must not
 * re-enter account mutation. HTTP work never runs while this lock is held. */
internal class CallScopeGate {
    private val lock = Any()
    private var scope: CallSignalScope? = null
    private val cancelers = mutableSetOf<() -> Unit>()
    fun replace(next: CallSignalScope?) = synchronized(lock) {
        if (scope === next) return@synchronized
        scope = next
        val old = cancelers.toList(); cancelers.clear()
        old.forEach { it() }
    }
    fun isCurrent(expected: CallSignalScope): Boolean = synchronized(lock) { scope === expected }
    /** Register BEFORE launching or executing request. Old work cannot register after reset. */
    fun register(expected: CallSignalScope, cancel: () -> Unit): Boolean = synchronized(lock) {
        if (scope !== expected) { cancel(); false } else { cancelers.add(cancel); true }
    }
    fun unregister(cancel: () -> Unit) = synchronized(lock) { cancelers.remove(cancel) }
    fun deliver(expected: CallSignalScope, signals: List<Map<String, Any?>>, listener: (Map<String, Any?>) -> Unit): Boolean = synchronized(lock) {
        if (scope !== expected) return@synchronized false
        signals.forEach { signal ->
            if (scope !== expected) return@synchronized false
            listener(signal)
        }
        true
    }
    fun mutate(expected: CallSignalScope, action: () -> Unit): Boolean = synchronized(lock) {
        if (scope !== expected) false else { action(); true }
    }
}
