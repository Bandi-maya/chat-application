package com.example.chat.data.services

/** Pure admission policy. Media/session transitions require a separate guarded executor, not a UI flag. */
object CallWaitingPolicy {
    enum class Choice { DECLINE_WAITING, END_AND_ANSWER, HOLD_AND_ANSWER }
    enum class Admission { SHOW_INCOMING, SHOW_WAITING, REJECT_BUSY, IGNORE_REPLAY, DROP_EXPIRED }
    data class CallRef(val accountHash: String, val callId: String, val audioOnly: Boolean)
    data class State(val foreground: CallRef? = null, val held: CallRef? = null, val waiting: CallRef? = null)
    data class Offer(val ref: CallRef, val expiresAtEpochMs: Long)
    fun admit(state: State, offer: Offer, nowEpochMs: Long): Admission {
        if (offer.expiresAtEpochMs <= nowEpochMs) return Admission.DROP_EXPIRED
        if (listOfNotNull(state.foreground, state.held, state.waiting).any { it == offer.ref }) return Admission.IGNORE_REPLAY
        if (state.waiting != null || state.held != null) return Admission.REJECT_BUSY
        return if (state.foreground == null) Admission.SHOW_INCOMING else Admission.SHOW_WAITING
    }
    fun choices(state: State, canRetainIndependentConnection: Boolean, supportsFullMediaHold: Boolean): Set<Choice> {
        if (state.waiting == null) return emptySet()
        return buildSet {
            add(Choice.DECLINE_WAITING)
            if (state.foreground != null) add(Choice.END_AND_ANSWER)
            if (state.foreground != null && state.held == null && canRetainIndependentConnection && supportsFullMediaHold)
                add(Choice.HOLD_AND_ANSWER)
        }
    }
    /** User action only. Holding must succeed before handing media ownership to the waiting call. */
    fun holdAndAnswerPrepared(state: State, holdSucceeded: Boolean, connectionStillCurrent: Boolean): State? {
        if (!holdSucceeded || !connectionStillCurrent || state.foreground == null || state.held != null || state.waiting == null) return null
        return State(foreground = state.waiting, held = state.foreground)
    }
    /** Do not silently resume microphone/camera on another conversation after a failed second call. */
    fun secondCallFailed(state: State): State = State(held = state.held)
    fun resumeHeldByUser(state: State, connectionStillValid: Boolean): State? =
        if (state.foreground != null || state.held == null || !connectionStillValid) null else State(foreground = state.held, waiting = state.waiting)
}
