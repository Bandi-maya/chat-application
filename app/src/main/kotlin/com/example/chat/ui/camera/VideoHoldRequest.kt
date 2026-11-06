package com.example.chat.ui.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One pointer gesture, created with the ORIGINAL camera-entry authority. Never reuse for retakes.
 * The composer owns pointer delivery: disposal cancels, never releases or sends. */
class VideoHoldRequest(private val isEntryCurrent: () -> Boolean) : AutoCloseable {
    enum class Phase { Held, Locked, Released, Cancelled }
    enum class Command { Start, Stop, Discard }
    private val mutablePhase = MutableStateFlow(Phase.Held)
    val phase: StateFlow<Phase> = mutablePhase
    private var started = false
    private var finished = false
    @Synchronized fun lock() { if(mutablePhase.value == Phase.Held && isEntryCurrent()) mutablePhase.value=Phase.Locked }
    @Synchronized fun release() { if(mutablePhase.value == Phase.Held) mutablePhase.value=Phase.Released }
    @Synchronized fun cancel() { mutablePhase.value=Phase.Cancelled }
    /** Main-thread consumer invokes commands outside this synchronized decision. No native action in scope locks. */
    @Synchronized fun next(ready: Boolean, boundFront: Boolean): Command? {
        if(!isEntryCurrent()) mutablePhase.value=Phase.Cancelled
        if(finished) return null
        return when(mutablePhase.value) {
            Phase.Cancelled -> { finished=true; if(started) Command.Discard else null }
            Phase.Released -> { finished=true; if(started) Command.Stop else null }
            Phase.Held, Phase.Locked -> if(!started && ready && boundFront) { started=true; Command.Start } else null
        }
    }
    override fun close() = cancel()
}
