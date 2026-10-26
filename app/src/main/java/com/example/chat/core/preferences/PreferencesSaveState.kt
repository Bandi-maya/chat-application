package com.example.chat.core.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Observable local-save outcome for the current account. No telemetry, no banner. */
sealed interface SaveState {
    data object Idle : SaveState
    data object Saved : SaveState
    data class Failed(val sinceMillis: Long) : SaveState
}

class PreferencesSaveTracker(private val now: () -> Long) {
    private val _state = MutableStateFlow<SaveState>(SaveState.Idle)
    val state: StateFlow<SaveState> = _state
    private var scopeUser: String? = null
    private var generation = 0L

    /** Account/session change: forget the previous account's outcome. Returns the new generation. */
    @Synchronized fun resetScope(userId: String?): Long {
        scopeUser = userId
        _state.value = SaveState.Idle
        return ++generation
    }

    @Synchronized fun currentGeneration(): Long = generation

    /**
     * Call after each OrderedBlobWriter.write. A result whose generation or user does not
     * match the current scope (an in-flight write from the previous account) is ignored.
     */
    @Synchronized fun onWriteFinished(gen: Long, userId: String?, wrote: Boolean, hasUnsavedFailure: Boolean) {
        if (gen != generation || userId != scopeUser) return
        val cur = _state.value
        _state.value = when {
            hasUnsavedFailure -> if (cur is SaveState.Failed) cur else SaveState.Failed(now())
            wrote -> SaveState.Saved
            else -> cur // skipped stale snapshot: no information
        }
    }
}
