package com.example.chat.ui.calls

import kotlinx.coroutines.CancellationException

/** UI boundary for recoverable call failures. Never hide structured cancellation. */
internal suspend fun runCallAction(onFailure: () -> Unit, action: suspend () -> Unit): Boolean = try {
    action()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    // Raw exceptions can contain SDP, server responses or contact details.
    onFailure()
    false
}
