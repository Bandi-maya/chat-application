package com.example.chat.data.services

import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.domain.models.CallType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Explicit typed states for the WebRTC & signaling state machine.
 *  Exact port of lib/domain/models/call_state.dart (CallSessionState). */
enum class CallSessionState {
    idle,
    initiating,
    ringing,
    incoming,
    connecting,
    connected,
    reconnecting,
    declined,
    busy,
    missed,
    ended,
    failed;

    /** Terminal states cannot transition back into a live media session. */
    val isTerminal: Boolean
        get() = this == idle || this == declined || this == busy ||
            this == missed || this == ended || this == failed
}

/** Available audio routing outputs. Port of call_state.dart AudioRouteType. */
enum class AudioRouteType { earpiece, speaker, wiredHeadset, bluetooth }

/** Information model representing an active or recent call session.
 *  Exact port of call_state.dart ChatyCallSession. */
data class ChatyCallSession(
    val callId: String,
    val remoteUserId: String,
    val remoteDisplayName: String,
    val remoteAvatarInitials: String? = null,
    val remoteAvatarColorHex: String? = null,
    val isVideo: Boolean,
    val isOutgoing: Boolean,
    val state: CallSessionState,
    val startedAt: Instant,
    val connectedAt: Instant? = null,
    val endedAt: Instant? = null,
    val audioRoute: AudioRouteType = AudioRouteType.speaker,
    val isMuted: Boolean = false,
    val isCameraOff: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isSharingScreen: Boolean = false,
    val isDemo: Boolean = false,
) {
    /** Whether the session is currently active or establishing media. */
    val isActive: Boolean
        get() = state == CallSessionState.connecting ||
            state == CallSessionState.connected ||
            state == CallSessionState.reconnecting ||
            state == CallSessionState.initiating ||
            (state == CallSessionState.ringing && isOutgoing)
}

/** Peer-connection transport states mapped from org.webrtc (kept webrtc-free
 *  so the state machine is JVM-testable). Mirrors the Dart switch on
 *  RTCPeerConnectionState. */
enum class CallTransportState { connected, disconnected, failed, closed, other }

/** Media engine seam over the WebRTC transport. The Android implementation
 *  (WebRtcCallMediaController) wraps org.webrtc; JVM tests substitute a fake.
 *  Every method mirrors the flutter_webrtc calls in
 *  call_signaling_service.dart. */
interface CallMediaController {
    val hasLocalMedia: Boolean
    val hasRemoteMedia: Boolean

    /** _createMediaTransport: build the peer connection and open local media. */
    suspend fun prepare(isVideo: Boolean, lowData: Boolean, iceServers: List<Map<String, Any?>>)

    /** createOffer + setLocalDescription; returns the local SDP offer. */
    suspend fun createOffer(isVideo: Boolean): String

    /** createAnswer + setLocalDescription; returns the local SDP answer. */
    suspend fun createAnswer(isVideo: Boolean): String

    /** setRemoteDescription; [type] is "offer" or "answer". */
    suspend fun setRemoteDescription(sdp: String, type: String)

    /** addCandidate for a remote ICE candidate. */
    suspend fun addRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?)

    /** Enables/disables local audio tracks (Dart toggleMute). No-op false when
     *  there is no local media. */
    fun setAudioEnabled(enabled: Boolean): Boolean

    /** Enables/disables local video tracks (Dart toggleCamera). */
    fun setVideoEnabled(enabled: Boolean): Boolean

    /** Helper.switchCamera on the first local video track. */
    suspend fun switchCamera(): Boolean

    /** Helper.setSpeakerphoneOn. */
    suspend fun setSpeakerphoneOn(on: Boolean)

    /** getDisplayMedia + replaceTrack on the video sender. */
    suspend fun startScreenShare(): Boolean

    /** Restore the camera track and stop the screen stream. */
    suspend fun stopScreenShare()

    /** Full teardown (Dart _disposeMediaTransport). */
    suspend fun dispose()

    /** Local ICE candidate produced by the peer connection. */
    var onLocalIceCandidate: ((candidate: String, sdpMid: String?, sdpMLineIndex: Int?) -> Unit)?

    /** Remote media stream arrived (Dart onTrack with streams). */
    var onRemoteStream: (() -> Unit)?

    /** Peer-connection state changed (Dart onConnectionState). */
    var onConnectionState: ((CallTransportState) -> Unit)?

    /** ICE connection state failed (Dart onIceConnectionState == Failed). */
    var onIceFailed: (() -> Unit)?
}

/** Outbound signaling sink: ApiBackendService.sendCallSignal on the api
 *  branch - fire-and-forget over the custom-backend WebSocket with payload
 *  {"type":"call_signal","target_user_id":...,"data":signalData}. */
fun interface CallSignalSender {
    fun sendCallSignal(targetUserId: String, data: Map<String, Any?>)
}

private data class QueuedCandidate(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?,
)

/**
 * Production call-session controller. Exact behavioral port of
 * lib/data/services/call_signaling_service.dart restricted to the custom
 * backend (api-hub) signaling path: offers/answers/ICE/decline/busy/hangup
 * travel as `call_signal` WebSocket payloads via [CallSignalSender], matching
 * the Dart ApiBackendService branch. The Dart file's Supabase Postgres
 * signaling fallback is intentionally not ported (custom-backend target).
 *
 * WebRTC remains the media source of truth through [CallMediaController].
 */
class CallSignalingService(
    private val scope: CoroutineScope,
    private val currentUserId: () -> String?,
    private val currentUserDisplayName: () -> String = { "User" },
    private val currentUserAvatarInitials: () -> String? = { null },
    private val currentUserAvatarColorHex: () -> String? = { null },
    private val signalSender: CallSignalSender,
    private val mediaFactory: () -> CallMediaController,
    /** Dart dataStore.addCallRecord sink for terminal call records. */
    private val callRecordSink: (CallRecord) -> Unit = {},
    /** TURN/ICE server provider (Dart _loadIceServers Supabase edge function).
     *  Default falls back to the same Google STUN list the Dart ships. */
    private val iceServersProvider: suspend () -> List<Map<String, Any?>> = { defaultIceServers() },
    private val lowDataUsageEnabled: () -> Boolean = { false },
    private val now: () -> Instant = { Instant.now() },
    private val onCallWaiting: (String) -> Unit = {},
    private val captureAccountScope: () -> Any? = { null },
    private val accountScopeValid: (Any?) -> Boolean = { true },
    private val accountScopeGuard: (Any?, () -> Unit) -> Boolean = { _, action -> action(); true },
) {
    var currentSession: ChatyCallSession? = null
        private set
    var callDurationSeconds: Int = 0
        private set

    private var cachedRemoteOfferSdp: String? = null
    private var media: CallMediaController? = null
    private var callAccountScope: Any? = null
    private fun requireCallScope(expected: Any?) {
        if (!accountScopeValid(expected)) throw kotlinx.coroutines.CancellationException("Call account scope changed")
    }
    private fun requireCallIdentity(expected: Any?, callId: String, peer: CallMediaController? = null) {
        var matches = false
        accountScopeGuard(expected) {
            matches = callAccountScope === expected && currentSession?.callId == callId &&
                currentSession?.state?.isTerminal == false && (peer == null || media === peer)
        }
        if (!matches) throw kotlinx.coroutines.CancellationException("Call session changed")
    }
    private fun guardCall(expected: Any?, callId: String, peer: CallMediaController? = null, action: () -> Unit): Boolean {
        var ran = false
        accountScopeGuard(expected) {
            if (callAccountScope === expected && currentSession?.callId == callId &&
                currentSession?.state?.isTerminal == false && (peer == null || media === peer)) {
                action(); ran = true
            }
        }
        return ran
    }
    private val queuedRemoteCandidates = mutableListOf<QueuedCandidate>()
    private var remoteDescriptionReady = false
    private var localDescriptionSignaled = false
    private val pendingLocalCandidates = linkedSetOf<QueuedCandidate>()

    /** Run under the captured account/call/peer guard, after offer/answer enqueue. */
    private fun flushLocalCandidates(session: ChatyCallSession) {
        localDescriptionSignaled = true
        val pending = pendingLocalCandidates.toList()
        pendingLocalCandidates.clear()
        pending.forEach { candidate ->
            signalSender.sendCallSignal(session.remoteUserId,
                CallSignalCodec.outgoingCandidate(session.callId, candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex))
        }
    }

    private var ringTimeoutJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var durationJob: Job? = null
    private val loggedCallIds = mutableSetOf<String>()
    private var acceptingCallId: String? = null
    private var endingCallId: String? = null
    private var endCallJob: Job? = null

    @Volatile
    private var initialized = false
    private var initializeJob: Job? = null

    /** Dart ChangeNotifier surface (addListener/removeListener/notifyListeners). */
    private val callWaitingListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addCallWaitingListener(listener: () -> Unit) { callWaitingListeners.add(listener) }
    fun removeCallWaitingListener(listener: () -> Unit) { callWaitingListeners.remove(listener) }
    private val stateListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addStateListener(listener: () -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: () -> Unit) {
        stateListeners.remove(listener)
    }

    private fun notifyChanged() {
        for (listener in stateListeners) listener()
    }

    val hasLocalMedia: Boolean get() = media?.hasLocalMedia == true
    val hasRemoteMedia: Boolean get() = media?.hasRemoteMedia == true

    /** The live media transport, exposed for UI video-surface attachment
     *  (Dart OngoingCallScreen reads localStream/remoteStream the same way).
     *  Android UI downcasts to CallVideoSurfaceProvider; tests never touch it. */
    val activeMedia: CallMediaController? get() = media

    val isInActiveCall: Boolean
        get() = currentSession?.let {
            it.state == CallSessionState.connecting ||
                it.state == CallSessionState.connected ||
                it.state == CallSessionState.reconnecting
        } == true

    val hasActiveSession: Boolean get() = currentSession?.state?.isTerminal == false

    /** Auth-transition hook: synchronous detach only. Returned cleanup owns the
     * detached peer and cannot touch a later account's media/session. */
    fun detachForAccountTransition(next: Any?): (suspend () -> Unit)? {
        if (callAccountScope === next) return null
        val old = media
        media = null; currentSession = null; callAccountScope = next
        acceptingCallId = null
        cachedRemoteOfferSdp = null; remoteDescriptionReady = false; queuedRemoteCandidates.clear()
        localDescriptionSignaled = false; pendingLocalCandidates.clear()
        ringTimeoutJob?.cancel(); connectionTimeoutJob?.cancel(); endCallJob?.cancel()
        ringTimeoutJob = null; connectionTimeoutJob = null; endCallJob = null; endingCallId = null
        stopDurationTimer(); callDurationSeconds = 0
        return { old?.dispose(); notifyChanged() }
    }

    /** Dart initialize(): one-time guard shared by concurrent callers. */
    fun initialize() {
        if (initialized) return
        if (initializeJob != null) return
        initialized = true
    }

    /** Inbound `call_signal` WebSocket payload (Dart _handleApiCallSignal).
     *  Wire from the realtime client's call_signal event stream. */
    suspend fun handleRemoteSignal(
        signal: Map<String, Any?>,
        validScope: () -> Boolean = { true },
        guardScope: ((() -> Unit) -> Boolean) = { action -> action(); true },
    ) {
        if (!validScope()) return
        val signalType = signal["signal_type"]?.toString()
        val callId = signal["call_id"]?.toString() ?: ""
        val senderId = signal["sender_id"]?.toString()
            ?: signal["caller_id"]?.toString()
            ?: ""
        val myId = currentUserId()

        if (myId == null || callId.isEmpty()) return
        if (senderId.isBlank() || senderId == myId) return // Ignore missing sender and own echo
        if (signalType != "offer") {
            val active = currentSession ?: return
            if (active.callId != callId || active.remoteUserId != senderId || active.state.isTerminal) return
        }

        val expectedAccount = callAccountScope
        val receiveValid = {
            validScope() && (signalType == "offer" || runCatching { requireCallIdentity(expectedAccount, callId) }.isSuccess)
        }
        val receiveGuard: ((() -> Unit) -> Boolean) = { action ->
            var applied = false
            guardScope {
                if (signalType == "offer") { action(); applied = true }
                else applied = guardCall(expectedAccount, callId, action = action)
            }
            applied
        }
        when (signalType) {
            "offer" -> {
                receiveGuard offerGuard@{
                val active = currentSession
                if (active != null && active.callId != callId && !active.state.isTerminal) {
                    signalSender.sendCallSignal(senderId, mapOf(
                        "signal_type" to "busy",
                        "call_id" to callId,
                        "reason" to "already_in_call",
                    ))
                    onCallWaiting(signal["caller_name"]?.toString() ?: "Contact")
                    callWaitingListeners.forEach { it() }
                    return@offerGuard
                }

                // Dual-channel delivery can repeat an offer. Never reset an
                // existing session or revive a terminal call with the same ID.
                if (active?.callId == callId) return@offerGuard
                val offerSdp = CallSignalCodec.sdp(signal["sdp"] ?: signal["offer"])
                if (offerSdp.isBlank()) return@offerGuard
                callAccountScope = captureAccountScope()
                cachedRemoteOfferSdp = offerSdp
                callDurationSeconds = 0
                val isVideo = CallSignalCodec.isVideo(signal)
                currentSession = ChatyCallSession(
                    callId = callId,
                    remoteUserId = senderId,
                    remoteDisplayName = signal["caller_name"]?.toString() ?: "Contact",
                    remoteAvatarInitials = signal["caller_avatar_initials"]?.toString(),
                    remoteAvatarColorHex = signal["caller_avatar_color_hex"]?.toString(),
                    isVideo = isVideo,
                    isOutgoing = false,
                    state = CallSessionState.incoming,
                    startedAt = now(),
                    audioRoute = if (isVideo) AudioRouteType.speaker else AudioRouteType.earpiece,
                )
                notifyChanged()

                ringTimeoutJob?.cancel()
                ringTimeoutJob = scope.launch {
                    delay(40_000)
                    val s = currentSession
                    if (receiveValid() && s?.callId == callId && s.state == CallSessionState.incoming) {
                        receiveGuard { endCall(reason = "no_answer") }
                    }
                }
                }
            }

            "answer" -> {
                val session = currentSession
                if (session?.callId == callId && session.isOutgoing) {
                    val answerSdp = CallSignalCodec.sdp(signal["sdp"] ?: signal["answer"])
                    val peer = media
                    if (answerSdp.isNotEmpty() && !remoteDescriptionReady && peer != null) {
                        ringTimeoutJob?.cancel()
                        if (!receiveValid()) return
                        peer.setRemoteDescription(answerSdp, "answer")
                        if (!receiveValid()) return
                        if (!receiveGuard {
                            if (currentSession?.callId == callId && media === peer) {
                                remoteDescriptionReady = true
                                currentSession = session.copy(state = CallSessionState.connecting)
                                startConnectionTimeout(callId)
                                notifyChanged()
                            }
                        }) return
                        flushRemoteCandidates(receiveValid, receiveGuard)
                    }
                }
            }

            "ice_candidate", "candidate" -> {
                if (currentSession?.callId == callId) {
                    val decoded = CallSignalCodec.candidate(signal)
                    if (decoded != null) {
                        val candidate = QueuedCandidate(decoded.candidate, decoded.sdpMid, decoded.sdpMLineIndex)
                        val peer = media
                        if (!remoteDescriptionReady || peer == null) receiveGuard { queuedRemoteCandidates.add(candidate) }
                        else {
                            if (!receiveValid()) return
                            peer.addRemoteCandidate(candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex)
                            if (!receiveValid()) return
                        }
                    }
                }
            }

            "busy", "decline", "reject" -> {
                val session = currentSession
                if (session?.callId == callId) {
                    var oldPeer: CallMediaController? = null
                    if (!receiveGuard {
                    ringTimeoutJob?.cancel()
                    connectionTimeoutJob?.cancel()
                    stopDurationTimer()
                    val duration = callDurationSeconds
                    logCallRecordOnce(
                        session,
                        if (session.isOutgoing) CallDirection.outgoing else CallDirection.missed,
                        duration,
                    )
                    currentSession = session.copy(
                        state = if (signalType == "busy") CallSessionState.busy else CallSessionState.declined,
                        endedAt = now(),
                    )
                        oldPeer = media
                        media = null; remoteDescriptionReady = false; queuedRemoteCandidates.clear()
                        localDescriptionSignaled = false; pendingLocalCandidates.clear()
                        notifyChanged()
                    }) return
                    oldPeer?.dispose()
                    if (!receiveValid()) return
                }
            }

            "hangup", "end" -> {
                val session = currentSession
                if (session?.callId == callId) {
                    var oldPeer: CallMediaController? = null
                    if (!receiveGuard {
                    ringTimeoutJob?.cancel()
                    connectionTimeoutJob?.cancel()
                    stopDurationTimer()
                    val duration = callDurationSeconds
                    logCallRecordOnce(
                        session,
                        if (session.isOutgoing) CallDirection.outgoing
                        else (if (duration > 0) CallDirection.incoming else CallDirection.missed),
                        duration,
                    )
                    currentSession = session.copy(
                        state = CallSessionState.ended,
                        endedAt = now(),
                    )
                        oldPeer = media
                        media = null; remoteDescriptionReady = false; queuedRemoteCandidates.clear()
                        localDescriptionSignaled = false; pendingLocalCandidates.clear()
                        notifyChanged()
                    }) return
                    oldPeer?.dispose()
                    if (!receiveValid()) return
                }
            }
        }
    }

    /** Starts a real outgoing WebRTC call (Dart initiateCall, api-hub path). */
    suspend fun initiateCall(
        remoteUserId: String,
        remoteDisplayName: String,
        remoteAvatarInitials: String? = null,
        remoteAvatarColorHex: String? = null,
        isVideo: Boolean,
    ) {
        initialize()
        val expectedAccount = captureAccountScope()
        requireCallScope(expectedAccount)
        val myId = currentUserId() ?: throw IllegalStateException("Authentication required.")
        require(remoteUserId.isNotEmpty() && remoteUserId != myId) {
            "A different call participant is required."
        }
        val existing = currentSession
        if (existing != null && !existing.state.isTerminal) {
            throw IllegalStateException("You are already in another call. Please end it before starting a new one.")
        }

        val callId = UUID.randomUUID().toString()
        var previousPeer: CallMediaController? = null
        if (!accountScopeGuard(expectedAccount) {
        check(!hasActiveSession) { "Another call is active" }
        previousPeer = detachMediaTransport()
        callDurationSeconds = 0
        cachedRemoteOfferSdp = null
        callAccountScope = expectedAccount
        currentSession = ChatyCallSession(
            callId = callId,
            remoteUserId = remoteUserId,
            remoteDisplayName = remoteDisplayName,
            remoteAvatarInitials = remoteAvatarInitials,
            remoteAvatarColorHex = remoteAvatarColorHex,
            isVideo = isVideo,
            isOutgoing = true,
            state = CallSessionState.initiating,
            startedAt = now(),
            audioRoute = if (isVideo) AudioRouteType.speaker else AudioRouteType.earpiece,
        )
        notifyChanged()
        }) return

        try {
            previousPeer?.dispose()
            requireCallIdentity(expectedAccount, callId)
            val peer = createMediaTransport(isVideo, expectedAccount, callId)
            requireCallIdentity(expectedAccount, callId, peer)
            val offerSdp = peer.createOffer(isVideo)
            requireCallIdentity(expectedAccount, callId, peer)
            if (offerSdp.isEmpty()) {
                throw IllegalStateException("WebRTC did not produce an SDP offer.")
            }

            if (!guardCall(expectedAccount, callId, peer) {
            signalSender.sendCallSignal(remoteUserId, mapOf(
                "signal_type" to "offer",
                "call_id" to callId,
                "call_type" to if (isVideo) "video" else "voice",
                "caller_id" to myId,
                "caller_name" to currentUserDisplayName(),
                "caller_avatar_initials" to currentUserAvatarInitials(),
                "caller_avatar_color_hex" to currentUserAvatarColorHex(),
                "is_video" to isVideo,
                "sdp" to offerSdp,
            ))

            currentSession?.let { flushLocalCandidates(it) }
            currentSession = currentSession?.copy(state = CallSessionState.ringing)
            notifyChanged()

            }) return
            if (!guardCall(expectedAccount, callId) {
            ringTimeoutJob?.cancel()
            ringTimeoutJob = scope.launch {
                delay(40_000)
                val state = currentSession?.state
                if (!accountScopeValid(expectedAccount) || currentSession?.callId != callId) return@launch
                if (state == CallSessionState.ringing ||
                    state == CallSessionState.initiating ||
                    state == CallSessionState.connecting
                ) {
                    guardCall(expectedAccount, callId) { endCall(reason = "no_answer") }
                }
            }
            }) return
        } catch (error: Throwable) {
            guardCall(expectedAccount, callId) { markTransportFailed(error.toString()) }
            throw error
        }
    }

    /** Accepts an incoming call (Dart acceptCall, api-hub path). */
    suspend fun acceptCall() {
        initialize()
        val expectedAccount = callAccountScope
        requireCallScope(expectedAccount)
        val session = currentSession
        if (session == null || session.state != CallSessionState.incoming) {
            throw IllegalStateException("No authorized incoming call is available.")
        }

        check(acceptingCallId != session.callId) { "Call is already being accepted" }
        ringTimeoutJob?.cancel()
        if (session.isDemo) {
            callDurationSeconds = 0
            currentSession = session.copy(
                state = CallSessionState.connected,
                connectedAt = now(),
            )
            startDurationTimer()
            notifyChanged()
            return
        }
        try {
            val offerSdp = cachedRemoteOfferSdp
            if (offerSdp.isNullOrEmpty()) {
                throw IllegalStateException("Incoming call has no SDP offer.")
            }

            if (!guardCall(expectedAccount, session.callId) {
                check(currentSession?.state == CallSessionState.incoming && acceptingCallId != session.callId) { "Call is already being accepted" }
                acceptingCallId = session.callId
            }) return
            val peer = createMediaTransport(session.isVideo, expectedAccount, session.callId)
            requireCallIdentity(expectedAccount, session.callId, peer)
            peer.setRemoteDescription(offerSdp, "offer")
            requireCallIdentity(expectedAccount, session.callId, peer)
            if (!guardCall(expectedAccount, session.callId, peer) { remoteDescriptionReady = true }) return
            flushRemoteCandidates({ runCatching { requireCallIdentity(expectedAccount, session.callId, peer) }.isSuccess },
                { action -> guardCall(expectedAccount, session.callId, peer, action) })
            requireCallIdentity(expectedAccount, session.callId, peer)
            val answerSdp = peer.createAnswer(session.isVideo)
            requireCallIdentity(expectedAccount, session.callId, peer)
            if (answerSdp.isEmpty()) {
                throw IllegalStateException("WebRTC did not produce an SDP answer.")
            }

            if (!guardCall(expectedAccount, session.callId, peer) {
            signalSender.sendCallSignal(session.remoteUserId, mapOf(
                "signal_type" to "answer",
                "call_id" to session.callId,
                "callee_id" to currentUserId(),
                "sdp" to answerSdp,
            ))

            flushLocalCandidates(session)
            currentSession = session.copy(state = CallSessionState.connecting)
            startConnectionTimeout(session.callId)
            notifyChanged()
            }) return
        } catch (error: Throwable) {
            guardCall(expectedAccount, session.callId) { markTransportFailed(error.toString()) }
            throw error
        } finally {
            accountScopeGuard(expectedAccount) { if (acceptingCallId == session.callId) acceptingCallId = null }
        }
    }

    /** Dart declineCall. */
    suspend fun declineCall() {
        val expected = callAccountScope
        var detached: CallMediaController? = null
        if (!accountScopeGuard(expected) {
            val session = currentSession
            if (session != null && session.state == CallSessionState.incoming) {
                ringTimeoutJob?.cancel(); connectionTimeoutJob?.cancel()
                if (!session.isDemo) signalSender.sendCallSignal(session.remoteUserId, mapOf("signal_type" to "decline", "call_id" to session.callId))
                logCallRecordOnce(session, CallDirection.missed, 0)
                currentSession = session.copy(state = CallSessionState.declined, endedAt = now())
                detached = media; media = null; remoteDescriptionReady = false; queuedRemoteCandidates.clear()
                        localDescriptionSignaled = false; pendingLocalCandidates.clear()
                notifyChanged()
            }
        }) return
        detached?.dispose()
    }

    /** Dart endCall: idempotent per call id. */
    fun endCall(reason: String = "ended") {
        val expected = callAccountScope
        if (!accountScopeValid(expected)) return
        val session = currentSession
        if (session == null || session.state.isTerminal) {
            return
        }
        if (session.isDemo) {
            ringTimeoutJob?.cancel()
            stopDurationTimer()
            currentSession = session.copy(
                state = CallSessionState.ended,
                endedAt = now(),
            )
            notifyChanged()
            scope.launch {
                delay(350)
                accountScopeGuard(expected) {
                    if (currentSession?.callId == session.callId) {
                        currentSession = null; callDurationSeconds = 0; notifyChanged()
                    }
                }
            }
            return
        }
        if (endingCallId == session.callId && endCallJob != null) {
            return
        }
        endingCallId = session.callId
        endCallJob = scope.launch {
            try {
                if (accountScopeValid(expected) && currentSession?.callId == session.callId) endCallSession(session, expected)
            } finally {
                if (endingCallId == session.callId) {
                    endCallJob = null
                    endingCallId = null
                }
            }
        }
    }

    private suspend fun endCallSession(session: ChatyCallSession, expected: Any?) {
        var detached: CallMediaController? = null
        if (!accountScopeGuard(expected) {
            if (currentSession?.callId == session.callId) {
                ringTimeoutJob?.cancel(); connectionTimeoutJob?.cancel(); stopDurationTimer()
                signalSender.sendCallSignal(session.remoteUserId, mapOf("signal_type" to "hangup", "call_id" to session.callId))
                val direction = if (session.isOutgoing) CallDirection.outgoing else if (callDurationSeconds > 0) CallDirection.incoming else CallDirection.missed
                logCallRecordOnce(session, direction, callDurationSeconds)
                currentSession = session.copy(state = CallSessionState.ended, endedAt = now())
                detached = media; media = null; remoteDescriptionReady = false; queuedRemoteCandidates.clear()
                        localDescriptionSignaled = false; pendingLocalCandidates.clear()
                notifyChanged()
            }
        }) return
        detached?.dispose()
    }

    /** Demo walk: initiating -> ringing (1.2s) -> connected (1.8s), or a held
     *  incoming demo. Exact port of startDemoCall. */
    fun startDemoCall(
        remoteUserId: String,
        remoteDisplayName: String,
        remoteAvatarInitials: String? = null,
        remoteAvatarColorHex: String? = null,
        isVideo: Boolean = false,
        isOutgoing: Boolean = true,
    ) {
        ringTimeoutJob?.cancel()
        stopDurationTimer()
        callDurationSeconds = 0

        val callId = "demo-${UUID.randomUUID()}"
        currentSession = ChatyCallSession(
            callId = callId,
            remoteUserId = remoteUserId,
            remoteDisplayName = remoteDisplayName,
            remoteAvatarInitials = remoteAvatarInitials,
            remoteAvatarColorHex = remoteAvatarColorHex,
            isVideo = isVideo,
            isOutgoing = isOutgoing,
            state = if (isOutgoing) CallSessionState.initiating else CallSessionState.incoming,
            startedAt = now(),
            audioRoute = if (isVideo) AudioRouteType.speaker else AudioRouteType.earpiece,
            isDemo = true,
        )
        notifyChanged()

        if (!isOutgoing) {
            // Incoming demo: hold until the UI answers or declines.
            return
        }

        ringTimeoutJob = scope.launch {
            delay(1200)
            val s = currentSession
            if (s == null || !s.isDemo || s.state.isTerminal) return@launch
            currentSession = s.copy(state = CallSessionState.ringing)
            notifyChanged()

            delay(1800)
            val current = currentSession
            if (current == null || !current.isDemo || current.state.isTerminal) return@launch
            currentSession = current.copy(
                state = CallSessionState.connected,
                connectedAt = now(),
            )
            callDurationSeconds = 0
            startDurationTimer()
            notifyChanged()
        }
    }

    fun rejectDemoCall() {
        val session = currentSession
        if (session == null || !session.isDemo) return
        ringTimeoutJob?.cancel()
        stopDurationTimer()
        currentSession = session.copy(
            state = CallSessionState.declined,
            endedAt = now(),
        )
        notifyChanged()
    }

    fun failDemoCall() {
        val session = currentSession
        if (session == null || !session.isDemo) return
        ringTimeoutJob?.cancel()
        stopDurationTimer()
        currentSession = session.copy(
            state = CallSessionState.failed,
            endedAt = now(),
        )
        notifyChanged()
    }

    /** Dart sendCallReaction: production throws UnsupportedError. */
    fun sendCallReaction(@Suppress("UNUSED_PARAMETER") emoji: String) {
        throw UnsupportedOperationException(
            "Call reactions are not available in production yet.",
        )
    }

    fun toggleMute() {
        accountScopeGuard(callAccountScope) control@{
        val session = currentSession ?: return@control
        val nextMuted = !session.isMuted
        if (session.isDemo) {
            currentSession = session.copy(isMuted = nextMuted)
            notifyChanged()
            return@control
        }
        val stream = media ?: return@control
        if (!stream.hasLocalMedia) return@control
        stream.setAudioEnabled(!nextMuted)
        currentSession = session.copy(isMuted = nextMuted)
        notifyChanged()
        }
    }

    fun toggleCamera() {
        accountScopeGuard(callAccountScope) control@{
        val session = currentSession
        if (session == null || !session.isVideo) return@control
        val nextCameraOff = !session.isCameraOff
        if (session.isDemo) {
            currentSession = session.copy(isCameraOff = nextCameraOff)
            notifyChanged()
            return@control
        }
        val stream = media ?: return@control
        if (!stream.hasLocalMedia) return@control
        stream.setVideoEnabled(!nextCameraOff)
        currentSession = session.copy(isCameraOff = nextCameraOff)
        notifyChanged()
        }
    }

    val isSharingScreen: Boolean get() = currentSession?.isSharingScreen == true

    suspend fun startScreenShare() {
        val expected = callAccountScope
        requireCallScope(expected)
        val session = currentSession
        val peer = media
        if (session == null || peer == null || !session.isVideo) return
        if (session.isSharingScreen) return
        if (!peer.startScreenShare()) return
        requireCallScope(expected)
        if (!guardCall(expected, session.callId) {
        currentSession = session.copy(isSharingScreen = true)
        notifyChanged()
        }) return
    }

    suspend fun stopScreenShare() {
        val expected = callAccountScope
        requireCallScope(expected)
        val session = currentSession
        val peer = media
        if (session == null || !session.isSharingScreen) return
        peer?.stopScreenShare()
        requireCallScope(expected)
        if (!guardCall(expected, session.callId) {
        currentSession = session.copy(isSharingScreen = false)
        notifyChanged()
        }) return
    }

    suspend fun toggleScreenShare() {
        if (isSharingScreen) stopScreenShare() else startScreenShare()
    }

    suspend fun switchCamera() {
        val expected = callAccountScope
        requireCallScope(expected)
        val session = currentSession
        val stream = media
        if (session == null || stream == null || !session.isVideo) return
        if (session.isSharingScreen) return
        if (!stream.hasLocalMedia) return
        val switched = stream.switchCamera()
        if (!switched) return
        requireCallScope(expected)
        if (!guardCall(expected, session.callId) {
        currentSession = session.copy(isFrontCamera = !session.isFrontCamera)
        notifyChanged()
        }) return
    }

    suspend fun setAudioRoute(route: AudioRouteType) {
        val expected = callAccountScope
        requireCallScope(expected)
        val session = currentSession ?: return
        if (session.isDemo) {
            requireCallScope(expected)
        if (!guardCall(expected, session.callId) {
        currentSession = session.copy(audioRoute = route)
            notifyChanged()
        }) return
            return
        }
        if (route != AudioRouteType.speaker && route != AudioRouteType.earpiece) {
            throw UnsupportedOperationException(
                "Select Bluetooth/headset through the system audio route.",
            )
        }
        val oldPeer = media
        requireCallIdentity(expected, session.callId, oldPeer)
        oldPeer?.setSpeakerphoneOn(route == AudioRouteType.speaker)
        requireCallScope(expected)
        if (!guardCall(expected, session.callId) {
        currentSession = session.copy(audioRoute = route)
        notifyChanged()
        }) return
    }

    private suspend fun createMediaTransport(isVideo: Boolean, expectedAccount: Any?, callId: String): CallMediaController {
        requireCallIdentity(expectedAccount, callId)
        var previous: CallMediaController? = null
        if (!guardCall(expectedAccount, callId) {
            // Incoming trickle ICE can arrive while ringing, before media exists.
            // Preserve this call's candidates while replacing only its old transport.
            val pending = queuedRemoteCandidates.toList()
            previous = detachMediaTransport()
            queuedRemoteCandidates.addAll(pending)
        }) throw kotlinx.coroutines.CancellationException("Call changed")
        previous?.dispose()
        requireCallIdentity(expectedAccount, callId)
        val iceServers = iceServersProvider()
        requireCallIdentity(expectedAccount, callId)
        val peer = mediaFactory()
        if (!guardCall(expectedAccount, callId) { media = peer }) { peer.dispose(); throw kotlinx.coroutines.CancellationException("Call changed") }

        peer.onLocalIceCandidate = { candidate, sdpMid, sdpMLineIndex ->
            if (candidate.isNotEmpty()) guardCall(expectedAccount, callId, peer) {
                val session = currentSession!!
                val item = QueuedCandidate(candidate, sdpMid, sdpMLineIndex)
                if (localDescriptionSignaled) {
                    signalSender.sendCallSignal(session.remoteUserId,
                        CallSignalCodec.outgoingCandidate(callId, candidate, sdpMid, sdpMLineIndex))
                } else if (pendingLocalCandidates.size < 128) {
                    pendingLocalCandidates.add(item)
                }
            }
        }
        peer.onRemoteStream = { guardCall(expectedAccount, callId, peer) { notifyChanged() } }
        peer.onConnectionState = { state -> guardCall(expectedAccount, callId, peer) { handlePeerConnectionState(state) } }
        peer.onIceFailed = { guardCall(expectedAccount, callId, peer) { markTransportFailed("ICE negotiation failed.") } }
        peer.prepare(isVideo, lowDataUsageEnabled(), iceServers)
        try { requireCallIdentity(expectedAccount, callId, peer) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { peer.dispose(); throw cancelled }
        peer.setSpeakerphoneOn(isVideo)
        requireCallIdentity(expectedAccount, callId, peer)
        return peer
    }

    private fun handlePeerConnectionState(state: CallTransportState) {
        val session = currentSession ?: return
        when (state) {
            CallTransportState.connected -> {
                ringTimeoutJob?.cancel()
                connectionTimeoutJob?.cancel()
                currentSession = session.copy(
                    state = CallSessionState.connected,
                    connectedAt = now(),
                )
                startDurationTimer()
                notifyChanged()
            }
            CallTransportState.disconnected -> {
                if (session.state == CallSessionState.connected ||
                    session.state == CallSessionState.reconnecting
                ) {
                    currentSession = session.copy(state = CallSessionState.reconnecting)
                    startConnectionTimeout(session.callId)
                    notifyChanged()
                }
            }
            CallTransportState.failed ->
                markTransportFailed("WebRTC peer connection failed.")
            CallTransportState.closed -> stopDurationTimer()
            CallTransportState.other -> Unit
        }
    }

    private fun markTransportFailed(message: String) {
        accountScopeGuard(callAccountScope) failed@{
        val session = currentSession
        if (session == null || session.state == CallSessionState.failed) return@failed
        ringTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        stopDurationTimer()
        logCallRecordOnce(
            session,
            if (session.isOutgoing) CallDirection.outgoing
            else (if (callDurationSeconds > 0) CallDirection.incoming else CallDirection.missed),
            callDurationSeconds,
        )
        currentSession = session.copy(
            state = CallSessionState.failed,
            endedAt = now(),
        )
        val oldPeer = detachMediaTransport()
        notifyChanged()
        scope.launch { oldPeer?.dispose() }
        }
    }

    private suspend fun failCurrentCall(error: Throwable) {
        markTransportFailed(error.toString())
    }

    private fun startConnectionTimeout(callId: String) {
        val expected = callAccountScope
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(25_000)
            val session = currentSession
            if (session == null ||
                session.callId != callId ||
                (session.state != CallSessionState.connecting &&
                    session.state != CallSessionState.reconnecting)
            ) {
                return@launch
            }
            guardCall(expected, callId) { endCall(reason = "connection_timeout") }
        }
    }

    private fun startDurationTimer() {
        if (durationJob != null) return
        callDurationSeconds = 0
        val expected = callAccountScope
        val callId = currentSession?.callId ?: return
        durationJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (!guardCall(expected, callId) { callDurationSeconds++; notifyChanged() }) return@launch
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    private fun logCallRecordOnce(
        session: ChatyCallSession,
        direction: CallDirection,
        durationSec: Int,
    ) {
        if (!loggedCallIds.add(session.callId)) return
        val myId = currentUserId() ?: ""
        callRecordSink(
            CallRecord(
                id = session.callId,
                callerId = if (session.isOutgoing) myId else session.remoteUserId,
                participantIds = listOf(myId, session.remoteUserId),
                type = if (session.isVideo) CallType.video else CallType.voice,
                direction = direction,
                durationSeconds = durationSec.toLong(),
                timestamp = session.startedAt,
            ),
        )
    }

    private suspend fun flushRemoteCandidates(
        validScope: () -> Boolean = { true },
        guardScope: ((() -> Unit) -> Boolean) = { action -> action(); true },
    ) {
        if (!validScope()) return
        val peer = media
        if (peer == null || !remoteDescriptionReady) return
        val pending = queuedRemoteCandidates.toList()
        if (!guardScope { queuedRemoteCandidates.clear() }) return
        for (candidate in pending) {
            if (!validScope()) return
            peer.addRemoteCandidate(candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex)
            if (!validScope()) return
        }
    }

    /** Called only while the caller owns the expected account guard. */
    private fun detachMediaTransport(): CallMediaController? {
        localDescriptionSignaled = false; pendingLocalCandidates.clear()
        remoteDescriptionReady = false
        queuedRemoteCandidates.clear()
        val peer = media
        media = null
        return peer
    }

    private suspend fun disposeMediaTransport(expected: Any?) {
        var peer: CallMediaController? = null
        if (!accountScopeGuard(expected) { peer = detachMediaTransport() }) return
        peer?.dispose()
    }

    /** Dart dispose(). */
    fun dispose() {
        ringTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        stopDurationTimer()
        var peer: CallMediaController? = null
        accountScopeGuard(callAccountScope) { peer = detachMediaTransport() }
        scope.launch { peer?.dispose() }
    }

    companion object {
        /** Dart _loadIceServers fallback list. */
        fun defaultIceServers(): List<Map<String, Any?>> = listOf(
            mapOf(
                "urls" to listOf(
                    "stun:stun.l.google.com:19302",
                    "stun:stun1.l.google.com:19302",
                    "stun:stun2.l.google.com:19302",
                    "stun:stun3.l.google.com:19302",
                    "stun:stun4.l.google.com:19302",
                ),
            ),
        )
    }
}
