package com.example.chat.data.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pure foreground-service policy from call_foreground_service.dart, kept
 *  Android-free so it is JVM-testable; the Android Service consumes it. */
object CallForegroundPolicy {
    /** Exact port of ChatyCallForegroundService.shouldRunFor. */
    fun shouldRunFor(session: ChatyCallSession?): Boolean {
        if (session == null) return false
        return when (session.state) {
            CallSessionState.ringing -> session.isOutgoing
            CallSessionState.connecting,
            CallSessionState.connected,
            CallSessionState.reconnecting -> true
            CallSessionState.idle,
            CallSessionState.initiating,
            CallSessionState.incoming,
            CallSessionState.declined,
            CallSessionState.busy,
            CallSessionState.missed,
            CallSessionState.ended,
            CallSessionState.failed -> false
        }
    }

    /** Exact port of _notificationText. */
    fun notificationTitle(session: ChatyCallSession): String =
        if (session.isVideo) "Chaty video call" else "Chaty voice call"

    fun notificationText(session: ChatyCallSession): String {
        val name = session.remoteDisplayName.trim().ifEmpty { "Chaty contact" }
        return when (session.state) {
            CallSessionState.ringing -> "Calling $name…"
            CallSessionState.connecting -> "Connecting with $name…"
            CallSessionState.reconnecting -> "Reconnecting with $name…"
            CallSessionState.connected -> "In call with $name"
            else -> "Call with $name"
        }
    }
}

/**
 * Owns process/lifecycle policy around the WebRTC call controller. Exact port
 * of lib/data/services/call_lifecycle_coordinator.dart. Signaling and media
 * remain CallSignalingService's job; this coordinator decides how long
 * reconnecting transports may survive, synchronizes Android foreground
 * execution with the authoritative call state, and terminates stranded
 * server/media state when the UI detaches.
 *
 * App lifecycle and the foreground-service effect are injected so the policy
 * is JVM-testable; the Android wiring lives with the integration owner.
 */
class CallLifecycleCoordinator(
    private val scope: CoroutineScope,
    private val callService: CallSignalingService,
    private val foregroundSync: (ChatyCallSession?) -> Unit,
    private val foregroundStop: () -> Unit = {},
    private val reconnectSignaling: () -> Unit = {},
    private val reconnectGracePeriodMs: Long = 15_000,
    private val onDebug: (String) -> Unit = {},
) {
    private var reconnectJob: Job? = null
    private var started = false
    private var disposed = false

    val isStarted: Boolean get() = started && !disposed

    fun start() {
        if (started || disposed) return
        started = true
        callService.addStateListener(::handleCallStateChanged)
        handleCallStateChanged()
    }

    private fun handleCallStateChanged() {
        if (disposed) return
        val session = callService.currentSession
        foregroundSync(session)

        if (session != null && needsReconnectDeadline(session.state)) {
            ensureReconnectDeadline()
            return
        }
        cancelReconnectDeadline()
    }

    private fun ensureReconnectDeadline() {
        if (reconnectJob != null || disposed) return
        reconnectJob = scope.launch {
            delay(reconnectGracePeriodMs)
            reconnectJob = null
            if (disposed) return@launch
            val session = callService.currentSession
            if (session == null || !needsReconnectDeadline(session.state)) return@launch
            onDebug("Chaty call reconnect grace period expired for ${session.callId}.")
            callService.endCall(reason = "reconnect_timeout")
        }
    }

    private fun cancelReconnectDeadline() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /** App returned to foreground (Dart didChangeAppLifecycleState resumed):
     *  re-establish signaling suspended by the OS and reconcile missed state. */
    fun onAppResumed() {
        if (disposed) return
        reconnectSignaling()
        handleCallStateChanged()
    }

    /** UI isolate detached (Dart didChangeAppLifecycleState detached): persist
     *  the terminal call state instead of leaving a stranded ringing/connected
     *  row; the foreground service is stopWithTask=true. */
    fun onAppDetached() {
        if (disposed) return
        cancelReconnectDeadline()
        val session = callService.currentSession
        if (session == null || isTerminalState(session.state)) {
            foregroundStop()
            return
        }
        callService.endCall(reason = "app_detached")
        foregroundStop()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        cancelReconnectDeadline()
        if (started) {
            callService.removeStateListener(::handleCallStateChanged)
        }
        foregroundStop()
        started = false
    }

    companion object {
        fun needsReconnectDeadline(state: CallSessionState): Boolean =
            state == CallSessionState.reconnecting

        fun isTerminalState(state: CallSessionState): Boolean = state.isTerminal
    }
}
