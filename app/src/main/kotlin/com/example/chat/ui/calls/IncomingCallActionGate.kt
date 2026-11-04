package com.example.chat.ui.calls

/** UI-action admission only; the service remains authoritative for call state.
 * Synchronous entry prevents two taps being queued before recomposition.
 */
internal class IncomingCallActionGate {
    var pending: Boolean = false
        private set

    fun tryEnter(): Boolean {
        if (pending) return false
        pending = true
        return true
    }

    fun complete() { pending = false }
}
