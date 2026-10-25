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
) {
    var currentSession: ChatyCallSession? = null
        private set
    var callDurationSeconds: Int = 0
        private set

    private var cachedRemoteOfferSdp: String? = null
    private var media: CallMediaController? = null
    private val queuedRemoteCandidates = mutableListOf<QueuedCandidate>()
    private var remoteDescriptionReady = false

    private var ringTimeoutJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var durationJob: Job? = null
    private val loggedCallIds = mutableSetOf<String>()
    private var endingCallId: String? = null
    private var endCallJob: Job? = null

    @Volatile
    private var initialized = false
    private var initializeJob: Job? = null

    /** Dart ChangeNotifier surface (addListener/removeListener/notifyListeners). */
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

    /** Dart initialize(): one-time guard shared by concurrent callers. */
    fun initialize() {
        if (initialized) return
        if (initializeJob != null) return
        initialized = true
    }

    /** Inbound `call_signal` WebSocket payload (Dart _handleApiCallSignal).
     *  Wire from the realtime client's call_signal event stream. */
    suspend fun handleRemoteSignal(signal: Map<String, Any?>) {
        val signalType = signal["signal_type"]?.toString()
        val callId = signal["call_id"]?.toString() ?: ""
        val senderId = signal["sender_id"]?.toString()
            ?: signal["caller_id"]?.toString()
            ?: ""
        val myId = currentUserId()

        if (myId == null || callId.isEmpty()) return
        if (senderId == myId) return // Ignore echo of our own signals

        when (signalType) {
            "offer" -> {
                val active = currentSession
                if (active != null && active.callId != callId && isInActiveCall) {
                    signalSender.sendCallSignal(senderId, mapOf(
                        "signal_type" to "busy",
                        "call_id" to callId,
                    ))
                    return
                }

                cachedRemoteOfferSdp = signal["sdp"]?.toString()
                callDurationSeconds = 0
                val isVideo = signal["is_video"] == true || signal["is_video"] == "true"
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
                    if (s?.callId == callId && s.state == CallSessionState.incoming) {
                        endCall(reason = "no_answer")
                    }
                }
            }

            "answer" -> {
                val session = currentSession
                if (session?.callId == callId && session.isOutgoing) {
                    val answerSdp = signal["sdp"]?.toString() ?: ""
                    val peer = media
                    if (answerSdp.isNotEmpty() && !remoteDescriptionReady && peer != null) {
                        ringTimeoutJob?.cancel()
                        peer.setRemoteDescription(answerSdp, "answer")
                        remoteDescriptionReady = true
                        currentSession = session.copy(state = CallSessionState.connecting)
                        startConnectionTimeout(callId)
                        flushRemoteCandidates()
                        notifyChanged()
                    }
                }
            }

            "ice_candidate" -> {
                if (currentSession?.callId == callId) {
                    val candidateText = signal["candidate"]?.toString()
                    if (!candidateText.isNullOrEmpty()) {
                        val sdpMid = signal["sdpMid"]?.toString()
                        val sdpMLineIndex = (signal["sdpMLineIndex"] as? Int)
                            ?: signal["sdpMLineIndex"]?.toString()?.toIntOrNull()
                        val candidate = QueuedCandidate(candidateText, sdpMid, sdpMLineIndex)
                        val peer = media
                        if (!remoteDescriptionReady || peer == null) {
                            queuedRemoteCandidates.add(candidate)
                        } else {
                            peer.addRemoteCandidate(candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex)
                        }
                    }
                }
            }

            "busy", "decline" -> {
                val session = currentSession
                if (session?.callId == callId) {
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
                    disposeMediaTransport()
                    notifyChanged()
                }
            }

            "hangup" -> {
                val session = currentSession
                if (session?.callId == callId) {
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
                    disposeMediaTransport()
                    notifyChanged()
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
        val myId = currentUserId() ?: throw IllegalStateException("Authentication required.")
        require(remoteUserId.isNotEmpty() && remoteUserId != myId) {
            "A different call participant is required."
        }
        val existing = currentSession
        if (existing != null &&
            existing.state != CallSessionState.ended &&
            existing.state != CallSessionState.declined &&
            existing.state != CallSessionState.failed
        ) {
            throw IllegalStateException("Another call is already active.")
        }

        disposeMediaTransport()
        val callId = UUID.randomUUID().toString()
        callDurationSeconds = 0
        cachedRemoteOfferSdp = null

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

        try {
            createMediaTransport(isVideo)
            val offerSdp = media!!.createOffer(isVideo)
            if (offerSdp.isEmpty()) {
                throw IllegalStateException("WebRTC did not produce an SDP offer.")
            }

            signalSender.sendCallSignal(remoteUserId, mapOf(
                "signal_type" to "offer",
                "call_id" to callId,
                "caller_id" to myId,
                "caller_name" to currentUserDisplayName(),
                "caller_avatar_initials" to currentUserAvatarInitials(),
                "caller_avatar_color_hex" to currentUserAvatarColorHex(),
                "is_video" to isVideo,
                "sdp" to offerSdp,
            ))

            currentSession = currentSession?.copy(state = CallSessionState.ringing)
            notifyChanged()

            ringTimeoutJob?.cancel()
            ringTimeoutJob = scope.launch {
                delay(40_000)
                val state = currentSession?.state
                if (state == CallSessionState.ringing ||
                    state == CallSessionState.initiating ||
                    state == CallSessionState.connecting
                ) {
                    endCall(reason = "no_answer")
                }
            }
        } catch (error: Throwable) {
            failCurrentCall(error)
            throw error
        }
    }

    /** Accepts an incoming call (Dart acceptCall, api-hub path). */
    suspend fun acceptCall() {
        initialize()
        val session = currentSession
        if (session == null || session.state != CallSessionState.incoming) {
            throw IllegalStateException("No authorized incoming call is available.")
        }

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

            createMediaTransport(session.isVideo)
            media!!.setRemoteDescription(offerSdp, "offer")
            remoteDescriptionReady = true
            flushRemoteCandidates()

            val answerSdp = media!!.createAnswer(session.isVideo)
            if (answerSdp.isEmpty()) {
                throw IllegalStateException("WebRTC did not produce an SDP answer.")
            }

            signalSender.sendCallSignal(session.remoteUserId, mapOf(
                "signal_type" to "answer",
                "call_id" to session.callId,
                "callee_id" to currentUserId(),
                "sdp" to answerSdp,
            ))

            currentSession = session.copy(state = CallSessionState.connecting)
            startConnectionTimeout(session.callId)
            notifyChanged()
        } catch (error: Throwable) {
            failCurrentCall(error)
            throw error
        }
    }

    /** Dart declineCall. */
    suspend fun declineCall() {
        initialize()
        val session = currentSession
        if (session == null || session.state != CallSessionState.incoming) return

        ringTimeoutJob?.cancel()
        if (session.isDemo) {
            currentSession = session.copy(
                state = CallSessionState.declined,
                endedAt = now(),
            )
            notifyChanged()
            return
        }
        try {
            signalSender.sendCallSignal(session.remoteUserId, mapOf(
                "signal_type" to "decline",
                "call_id" to session.callId,
            ))
        } finally {
            connectionTimeoutJob?.cancel()
            logCallRecordOnce(session, CallDirection.missed, 0)
            currentSession = session.copy(
                state = CallSessionState.declined,
                endedAt = now(),
            )
            disposeMediaTransport()
            notifyChanged()
        }
    }

    /** Dart endCall: idempotent per call id. */
    fun endCall(reason: String = "ended") {
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
                currentSession = null
                callDurationSeconds = 0
                notifyChanged()
            }
            return
        }
        if (endingCallId == session.callId && endCallJob != null) {
            return
        }
        endingCallId = session.callId
        endCallJob = scope.launch {
            try {
                endCallSession(session)
            } finally {
                if (endingCallId == session.callId) {
                    endCallJob = null
                    endingCallId = null
                }
            }
        }
    }

    private suspend fun endCallSession(session: ChatyCallSession) {
        ringTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        stopDurationTimer()
        val duration = callDurationSeconds
        try {
            signalSender.sendCallSignal(session.remoteUserId, mapOf(
                "signal_type" to "hangup",
                "call_id" to session.callId,
            ))
        } finally {
            val direction = if (session.isOutgoing) CallDirection.outgoing
                else (if (duration > 0) CallDirection.incoming else CallDirection.missed)
            logCallRecordOnce(session, direction, duration)
            currentSession = session.copy(
                state = CallSessionState.ended,
                endedAt = now(),
            )
            disposeMediaTransport()
            notifyChanged()
        }
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
        val session = currentSession ?: return
        val nextMuted = !session.isMuted
        if (session.isDemo) {
            currentSession = session.copy(isMuted = nextMuted)
            notifyChanged()
            return
        }
        val stream = media ?: return
        if (!stream.hasLocalMedia) return
        stream.setAudioEnabled(!nextMuted)
        currentSession = session.copy(isMuted = nextMuted)
        notifyChanged()
    }

    fun toggleCamera() {
        val session = currentSession
        if (session == null || !session.isVideo) return
        val nextCameraOff = !session.isCameraOff
        if (session.isDemo) {
            currentSession = session.copy(isCameraOff = nextCameraOff)
            notifyChanged()
            return
        }
        val stream = media ?: return
        if (!stream.hasLocalMedia) return
        stream.setVideoEnabled(!nextCameraOff)
        currentSession = session.copy(isCameraOff = nextCameraOff)
        notifyChanged()
    }

    val isSharingScreen: Boolean get() = currentSession?.isSharingScreen == true

    suspend fun startScreenShare() {
        val session = currentSession
        val peer = media
        if (session == null || peer == null || !session.isVideo) return
        if (session.isSharingScreen) return
        if (!peer.startScreenShare()) return
        currentSession = session.copy(isSharingScreen = true)
        notifyChanged()
    }

    suspend fun stopScreenShare() {
        val session = currentSession
        val peer = media
        if (session == null || !session.isSharingScreen) return
        peer?.stopScreenShare()
        currentSession = session.copy(isSharingScreen = false)
        notifyChanged()
    }

    suspend fun toggleScreenShare() {
        if (isSharingScreen) stopScreenShare() else startScreenShare()
    }

    suspend fun switchCamera() {
        val session = currentSession
        val stream = media
        if (session == null || stream == null || !session.isVideo) return
        if (session.isSharingScreen) return
        if (!stream.hasLocalMedia) return
        val switched = stream.switchCamera()
        if (!switched) return
        currentSession = session.copy(isFrontCamera = !session.isFrontCamera)
        notifyChanged()
    }

    suspend fun setAudioRoute(route: AudioRouteType) {
        val session = currentSession ?: return
        if (session.isDemo) {
            currentSession = session.copy(audioRoute = route)
            notifyChanged()
            return
        }
        if (route != AudioRouteType.speaker && route != AudioRouteType.earpiece) {
            throw UnsupportedOperationException(
                "Select Bluetooth/headset through the system audio route.",
            )
        }
        media?.setSpeakerphoneOn(route == AudioRouteType.speaker)
        currentSession = session.copy(audioRoute = route)
        notifyChanged()
    }

    private suspend fun createMediaTransport(isVideo: Boolean) {
        disposeMediaTransport()
        val iceServers = iceServersProvider()
        val peer = mediaFactory()
        media = peer

        peer.onLocalIceCandidate = { candidate, sdpMid, sdpMLineIndex ->
            val session = currentSession
            if (candidate.isNotEmpty() && session != null) {
                signalSender.sendCallSignal(session.remoteUserId, mapOf(
                    "signal_type" to "ice_candidate",
                    "call_id" to session.callId,
                    "candidate" to candidate,
                    "sdpMid" to sdpMid,
                    "sdpMLineIndex" to sdpMLineIndex,
                ))
            }
        }
        peer.onRemoteStream = {
            notifyChanged()
        }
        peer.onConnectionState = { state -> handlePeerConnectionState(state) }
        peer.onIceFailed = {
            markTransportFailed("ICE negotiation failed.")
        }

        peer.prepare(isVideo, lowDataUsageEnabled(), iceServers)
        peer.setSpeakerphoneOn(isVideo)
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
        val session = currentSession
        if (session == null || session.state == CallSessionState.failed) return
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
        notifyChanged()
        scope.launch { disposeMediaTransport() }
    }

    private suspend fun failCurrentCall(error: Throwable) {
        markTransportFailed(error.toString())
    }

    private fun startConnectionTimeout(callId: String) {
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
            endCall(reason = "connection_timeout")
        }
    }

    private fun startDurationTimer() {
        if (durationJob != null) return
        callDurationSeconds = 0
        durationJob = scope.launch {
            while (isActive) {
                delay(1000)
                callDurationSeconds++
                notifyChanged()
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

    private suspend fun flushRemoteCandidates() {
        val peer = media
        if (peer == null || !remoteDescriptionReady) return
        val pending = queuedRemoteCandidates.toList()
        queuedRemoteCandidates.clear()
        for (candidate in pending) {
            peer.addRemoteCandidate(candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex)
        }
    }

    private suspend fun disposeMediaTransport() {
        remoteDescriptionReady = false
        queuedRemoteCandidates.clear()
        val peer = media
        media = null
        peer?.dispose()
    }

    /** Dart dispose(). */
    fun dispose() {
        ringTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        stopDurationTimer()
        scope.launch { disposeMediaTransport() }
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
