package com.example.chat.ui.calls

import com.example.chat.data.services.CallSessionState

internal fun callPipStatus(state: CallSessionState, durationSeconds: Int): String = when (state) {
    CallSessionState.connected -> "%02d:%02d".format(durationSeconds.coerceAtLeast(0) / 60, durationSeconds.coerceAtLeast(0) % 60)
    CallSessionState.initiating -> "Starting…"
    CallSessionState.ringing -> "Ringing…"
    CallSessionState.incoming -> "Incoming call"
    CallSessionState.connecting -> "Connecting…"
    CallSessionState.reconnecting -> "Reconnecting…"
    CallSessionState.busy -> "Busy"
    CallSessionState.failed -> "Call failed"
    else -> "Call ended"
}
