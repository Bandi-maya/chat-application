package com.example.chat.data.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Render seam consumed by the Compose call surfaces: the Dart screens read
 *  RTCVideoRenderer-attached MediaStreams from the signaling service; here the
 *  UI hands SurfaceViewRenderers to the live media controller instead. */
interface CallVideoSurfaceProvider {
    val eglContext: EglBase.Context
    val hasRemoteVideo: Boolean
    val isFrontCamera: Boolean
    fun attachLocalRenderer(renderer: org.webrtc.SurfaceViewRenderer)
    fun attachRemoteRenderer(renderer: org.webrtc.SurfaceViewRenderer)
    fun detachRenderer(renderer: org.webrtc.SurfaceViewRenderer)
}

/**
 * org.webrtc-backed [CallMediaController]: the native counterpart of the
 * flutter_webrtc calls in call_signaling_service.dart. WebRTC is the media
 * source of truth; this class owns the peer connection, local/remote/screen
 * streams and track-level controls.
 *
 * Video surfaces are NOT created here: the Compose UI creates
 * SurfaceViewRenderers and attaches them via [attachLocalRenderer] /
 * [attachRemoteRenderer] so the service layer stays UI-free.
 */
class WebRtcCallMediaController(
    private val appContext: Context,
) : CallMediaController, CallVideoSurfaceProvider {

    private val eglBase: EglBase = EglBase.create()
    override val eglContext: EglBase.Context get() = eglBase.eglBaseContext

    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localStream: MediaStream? = null
    private var remoteStream: MediaStream? = null

    private var videoCapturer: VideoCapturer? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenStream: MediaStream? = null
    private var screenSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    /** MediaProjection permission result, captured by the UI via
     *  MediaProjectionManager.createScreenCaptureIntent() and handed here
     *  before [startScreenShare]. */
    private var screenCapturePermission: Intent? = null

    fun setScreenCapturePermission(data: Intent) {
        screenCapturePermission = data
    }

    override var hasLocalMedia: Boolean = false
        private set
    override var hasRemoteMedia: Boolean = false
        private set

    override var onLocalIceCandidate: ((String, String?, Int?) -> Unit)? = null
    override var onRemoteStream: (() -> Unit)? = null
    private var onScreenShareStopped: (() -> Unit)? = null

    override fun setOnScreenShareStopped(listener: (() -> Unit)?) {
        onScreenShareStopped = listener
    }
    override var onConnectionState: ((CallTransportState) -> Unit)? = null
    override var onIceFailed: (() -> Unit)? = null

    /** Cumulative WebRTC RTP bytes since this peer connected. */
    var onTrafficBytes: ((uploadedBytes: Long, downloadedBytes: Long) -> Unit)? = null
    var onNetworkMetrics: ((upstreamKbps: Int, packetLossPercent: Double, roundTripTimeMs: Long) -> Unit)? = null
    var onTrafficEnded: (() -> Unit)? = null
    private val statsHandler = Handler(Looper.getMainLooper())
    private var statsStarted = false
    private var trafficSessionPrepared = false
    private var trafficSessionEnded = false
    private var lastSentBytes = 0L
    private var lastReceivedBytes = 0L
    private val statsPoll = object : Runnable {
        override fun run() { pollTrafficStats() }
    }

    val localVideoTrackForRender: VideoTrack? get() = localVideoTrack
    val remoteVideoTrackForRender: VideoTrack? get() =
        remoteStream?.videoTracks?.firstOrNull()
    override val hasRemoteVideo: Boolean
        get() = remoteStream?.videoTracks?.isNotEmpty() == true

    override val isFrontCamera: Boolean = true

    /** _createMediaTransport: peer connection + local media. */
    override suspend fun prepare(
        isVideo: Boolean,
        lowData: Boolean,
        iceServers: List<Map<String, Any?>>,
    ) {
        CallPermissionGate.ensure(appContext, isVideo)
        disposeInternal()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .createPeerConnectionFactory()
        peerConnectionFactory = factory

        val rtcConfig = PeerConnection.RTCConfiguration(parseIceServers(iceServers)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                val sdp = candidate?.sdp
                if (!sdp.isNullOrEmpty()) {
                    onLocalIceCandidate?.invoke(sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                if (stream != null) {
                    remoteStream = stream
                    stream.audioTracks.forEach { it.setEnabled(remoteAudioEnabled) }
                    hasRemoteMedia = true
                    onRemoteStream?.invoke()
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val stream = streams?.firstOrNull()
                if (stream != null && remoteStream == null) {
                    remoteStream = stream
                    stream.audioTracks.forEach { it.setEnabled(remoteAudioEnabled) }
                    hasRemoteMedia = true
                    onRemoteStream?.invoke()
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                val mapped = when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> CallTransportState.connected
                    PeerConnection.PeerConnectionState.DISCONNECTED -> CallTransportState.disconnected
                    PeerConnection.PeerConnectionState.FAILED -> CallTransportState.failed
                    PeerConnection.PeerConnectionState.CLOSED -> CallTransportState.closed
                    else -> CallTransportState.other
                }
                onConnectionState?.invoke(mapped)
                if (mapped == CallTransportState.connected) startTrafficStats()
                else if (mapped == CallTransportState.closed || mapped == CallTransportState.failed) stopTrafficStats()
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.FAILED) {
                    onIceFailed?.invoke()
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: throw IllegalStateException("WebRTC did not produce a peer connection.")
        peerConnection = peer
        trafficSessionPrepared = true

        LocalMediaPreparation.run(
            initialize = { openLocalMedia(isVideo, lowData, factory, peer) },
            rollback = { disposeInternal() },
        )
    }

    /** _openLocalMedia: audio/video constraints mirror the Dart getUserMedia
     *  constraints, including the low-data profile. */
    private fun openLocalMedia(
        isVideo: Boolean,
        lowData: Boolean,
        factory: PeerConnectionFactory,
        peer: PeerConnection,
    ) {
        if (localStream != null) return

        val stream = factory.createLocalMediaStream("chaty_local_stream")
        localStream = stream // provisional ownership; failed preparation rolls back every field

        // Keep voice processing enabled in every network profile; low bandwidth must
        // not mean degraded intelligibility. The adaptive sender bitrate handles load.
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("channelCount", "1"))
        }
        val audioSource: AudioSource = factory.createAudioSource(audioConstraints)
        localAudioSource = audioSource
        localAudioTrack = factory.createAudioTrack("chaty_audio", audioSource)
        stream.addTrack(localAudioTrack)

        if (isVideo) {
            val capturer = createCameraCapturer()
                ?: throw IllegalStateException("No usable camera for the video call.")
            videoCapturer = capturer
            val helper = SurfaceTextureHelper.create("chaty_capture", eglContext)
            surfaceTextureHelper = helper
            val videoSource: VideoSource = factory.createVideoSource(capturer.isScreencast)
            localVideoSource = videoSource
            capturer.initialize(helper, appContext, videoSource.capturerObserver)
            val width = if (lowData) 640 else 1280
            val height = if (lowData) 360 else 720
            val frameRate = if (lowData) 15 else 30
            capturer.startCapture(width, height, frameRate)
            localVideoTrack = factory.createVideoTrack("chaty_video", videoSource)
            stream.addTrack(localVideoTrack)
        }

        for (track in stream.audioTracks) {
            peer.addTrack(track, listOf(stream.id))
        }
        for (track in stream.videoTracks) {
            peer.addTrack(track, listOf(stream.id))
        }
        hasLocalMedia = true
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in enumerator.deviceNames) {
            return enumerator.createCapturer(deviceName, null)
        }
        return null
    }

    private fun startTrafficStats() {
        if (statsStarted) return
        statsStarted = true
        lastSentBytes = 0L
        lastReceivedBytes = 0L
        pollTrafficStats()
    }

    private fun pollTrafficStats() {
        val peer = peerConnection ?: return
        if (!statsStarted) return
        peer.getStats(object : org.webrtc.StatsObserver {
            override fun onComplete(reports: Array<org.webrtc.StatsReport>) {
                if (!statsStarted) return
                var sent = 0L
                var received = 0L
                var availableOutgoingBps = 0L
                var packetsSent = 0L
                var packetsLost = 0L
                var roundTripTimeMs = 0L
                reports.forEach { report ->
                    report.values.forEach { value ->
                        val number = value.value.toDoubleOrNull() ?: 0.0
                        when (value.name) {
                            "bytesSent" -> if (report.type == "ssrc" || report.type == "outbound-rtp") sent += number.toLong()
                            "bytesReceived" -> if (report.type == "ssrc" || report.type == "inbound-rtp") received += number.toLong()
                            "availableOutgoingBitrate" -> availableOutgoingBps = maxOf(availableOutgoingBps, number.toLong())
                            "packetsSent" -> packetsSent += number.toLong()
                            "packetsLost" -> packetsLost += number.toLong().coerceAtLeast(0L)
                            "currentRoundTripTime" -> roundTripTimeMs = maxOf(roundTripTimeMs, (number * 1_000.0).toLong())
                        }
                    }
                }
                val lossDenominator = packetsSent + packetsLost
                val lossPercent = if (lossDenominator > 0L) packetsLost * 100.0 / lossDenominator else 0.0
                onNetworkMetrics?.invoke((availableOutgoingBps / 1_000L).toInt(), lossPercent, roundTripTimeMs)
                if (sent > 0L || received > 0L) {
                    lastSentBytes = maxOf(lastSentBytes, sent)
                    lastReceivedBytes = maxOf(lastReceivedBytes, received)
                    onTrafficBytes?.invoke(lastSentBytes, lastReceivedBytes)
                }
                if (statsStarted) statsHandler.postDelayed(statsPoll, 1_000L)
            }
        }, null)
    }

    private fun stopTrafficStats() {
        statsStarted = false
        statsHandler.removeCallbacks(statsPoll)
        if (trafficSessionPrepared && !trafficSessionEnded) {
            trafficSessionEnded = true
            onTrafficEnded?.invoke()
        }
    }

    /** Applies live video and Opus bitrate profiles; audio remains present at every quality tier. */
    override suspend fun applyNetworkProfile(profile: CallNetworkProfile) {
        val capturer = videoCapturer
        if (capturer != null) {
            runCatching {
                capturer.changeCaptureFormat(profile.width, profile.height, profile.frameRate)
            }.onFailure {
                android.util.Log.w("ChatyWebRTC", "Camera profile update deferred", it)
            }
        }
        val peer = peerConnection ?: return
        peer.senders.forEach { sender ->
            val kind = sender.track()?.kind()
            if (kind != "video" && kind != "audio") return@forEach
            runCatching {
                val parameters = sender.parameters
                parameters.encodings.forEach { encoding ->
                    if (kind == "video") {
                        encoding.maxBitrateBps = profile.maxBitrateBps
                        encoding.maxFramerate = profile.frameRate
                    } else {
                        encoding.maxBitrateBps = profile.audioBitrateBps
                    }
                }
                if (!sender.setParameters(parameters)) {
                    android.util.Log.w("ChatyWebRTC", "$kind sender rejected adaptive bitrate profile")
                }
            }.onFailure {
                android.util.Log.w("ChatyWebRTC", "Could not apply adaptive $kind bitrate", it)
            }
        }
    }

    override suspend fun createOffer(isVideo: Boolean): String {
        val peer = peerConnection ?: throw IllegalStateException("No peer connection.")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
        }
        val offer = createSdp(peer, constraints, isOffer = true)
        setLocalDescription(peer, offer)
        if (offer.description.isEmpty()) {
            throw IllegalStateException("WebRTC did not produce an SDP offer.")
        }
        return offer.description
    }

    override suspend fun createAnswer(isVideo: Boolean): String {
        val peer = peerConnection ?: throw IllegalStateException("No peer connection.")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
        }
        val answer = createSdp(peer, constraints, isOffer = false)
        setLocalDescription(peer, answer)
        if (answer.description.isEmpty()) {
            throw IllegalStateException("WebRTC did not produce an SDP answer.")
        }
        return answer.description
    }

    override suspend fun setRemoteDescription(sdp: String, type: String) {
        val peer = peerConnection ?: throw IllegalStateException("No peer connection.")
        val description = SessionDescription(
            if (type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER,
            sdp,
        )
        suspendCancellableCoroutine<Unit> { cont ->
            peer.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() { cont.resume(Unit) }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException(error ?: "setRemoteDescription failed"))
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                },
                description,
            )
        }
    }

    override suspend fun addRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidate))
    }

    private var remoteAudioEnabled = true
    override val supportsLocalHold: Boolean get() = true
    override fun setRemoteAudioEnabled(enabled: Boolean): Boolean {
        remoteAudioEnabled = enabled
        remoteStream?.audioTracks?.forEach { it.setEnabled(enabled) }
        return true
    }

    override fun setAudioEnabled(enabled: Boolean): Boolean {
        val track = localAudioTrack ?: return false
        track.setEnabled(enabled)
        return true
    }

    override fun setVideoEnabled(enabled: Boolean): Boolean {
        val track = localVideoTrack ?: return false
        track.setEnabled(enabled)
        return true
    }

    // This sender owns its track separately from localStream; ordinary audio is untouched.
    private var upgradeSender: org.webrtc.RtpSender? = null
    private var upgradePrepared = false
    private var upgradeCommitted = false
    // Kept disabled until rollback/camera/ICE behavior passes the two-endpoint gate.
    override val supportsVideoUpgrade: Boolean get() = false

    override suspend fun prepareVideoUpgrade() {
        check(localVideoTrack == null && !upgradePrepared) { "Video already allocated" }
        CallPermissionGate.ensure(appContext, true)
        val peer = peerConnection ?: error("No peer connection")
        val factory = peerConnectionFactory ?: error("No media factory")
        try {
            val capturer = createCameraCapturer() ?: error("No usable camera")
            videoCapturer = capturer
            val helper = SurfaceTextureHelper.create("chaty_upgrade_capture", eglContext)
            surfaceTextureHelper = helper
            val source = factory.createVideoSource(false)
            localVideoSource = source
            capturer.initialize(helper, appContext, source.capturerObserver)
            val track = factory.createVideoTrack("chaty_upgrade_video", source)
            track.setEnabled(false)
            localVideoTrack = track
            // No capture before matching remote answer+commit. Disabled track cannot send frames.
            upgradeSender = peer.addTrack(track, listOf("chaty_upgrade")) ?: error("No video sender")
            upgradePrepared = true
        } catch (error: Exception) { releaseUpgradeVideo(); throw error }
    }

    override suspend fun commitVideoUpgrade() {
        check(upgradePrepared) { "No pending video" }
        val capturer = videoCapturer ?: error("No camera")
        check(!upgradeCommitted) { "Video already committed" }
        capturer.startCapture(640, 360, 24)
        check(localVideoTrack?.setEnabled(true) == true) { "Video enable failed" }
        upgradeCommitted = true
    }

    override suspend fun abortVideoUpgrade() {
        if (!upgradePrepared && upgradeSender == null) return
        val peer = peerConnection
        // Cancel an in-progress offer without destroying the established audio transport.
        if (peer != null && (peer.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER ||
            peer.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER)) {
            runCatching { setLocalDescription(peer, SessionDescription(SessionDescription.Type.ROLLBACK, "")) }
        }
        releaseUpgradeVideo()
    }

    private fun releaseUpgradeVideo() {
        if (upgradeSender == null && !upgradePrepared && localStream?.videoTracks?.isNotEmpty() == true) return
        localVideoTrack?.setEnabled(false)
        upgradeSender?.let { sender -> runCatching { sender.setTrack(null, false) }; runCatching { peerConnection?.removeTrack(sender) } }
        upgradeSender = null
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }; videoCapturer = null
        runCatching { localVideoTrack?.dispose() }; localVideoTrack = null
        runCatching { localVideoSource?.dispose() }; localVideoSource = null
        runCatching { surfaceTextureHelper?.dispose() }; surfaceTextureHelper = null
        upgradePrepared = false
        upgradeCommitted = false
    }

    override suspend fun switchCamera(): Boolean {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return false
        return suspendCancellableCoroutine { cont ->
            capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) { cont.resume(true) }
                override fun onCameraSwitchError(error: String?) { cont.resume(false) }
            })
        }
    }

    override suspend fun setSpeakerphoneOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    /** getDisplayMedia + replaceTrack on the video sender. Requires
     *  [setScreenCapturePermission] first (MediaProjection consent). */
    override suspend fun startScreenShare(): Boolean {
        val peer = peerConnection ?: return false
        val factory = peerConnectionFactory ?: return false
        val permission = screenCapturePermission ?: return false
        // Android 10+ requires an active mediaProjection foreground-service
        // type before ScreenCapturerAndroid creates the MediaProjection.
        if (!CallForegroundService.ensureScreenProjectionForeground(appContext)) return false
        // Resolve the sender before allocating projection resources. The old
        // ordering leaked a live capturer when a video sender was unavailable.
        val videoSender = peer.senders.firstOrNull { it.track()?.kind() == "video" }
            ?: return false

        var capturer: ScreenCapturerAndroid? = null
        var helper: SurfaceTextureHelper? = null
        var source: VideoSource? = null
        var track: VideoTrack? = null
        var stream: MediaStream? = null
        return try {
            val createdCapturer = ScreenCapturerAndroid(
                permission,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        // Android/user revoked projection: restore the camera,
                        // release resources, and let the call state drop the
                        // mediaProjection foreground-service type.
                        Handler(Looper.getMainLooper()).post { stopScreenShareInternal() }
                    }
                },
            )
            capturer = createdCapturer
            val createdHelper = SurfaceTextureHelper.create("chaty_screen", eglContext)
            helper = createdHelper
            val createdSource = factory.createVideoSource(true)
            source = createdSource
            createdCapturer.initialize(createdHelper, appContext, createdSource.capturerObserver)
            createdCapturer.startCapture(1280, 720, 30)
            val createdTrack = factory.createVideoTrack("chaty_screen", createdSource)
            track = createdTrack
            createdTrack.setEnabled(true)
            val createdStream = factory.createLocalMediaStream("chaty_screen_stream")
            stream = createdStream
            createdStream.addTrack(createdTrack)
            if (!videoSender.setTrack(createdTrack, false)) {
                throw IllegalStateException("Unable to attach screen capture track.")
            }

            screenCapturer = createdCapturer
            screenSurfaceTextureHelper = createdHelper
            screenVideoSource = createdSource
            screenVideoTrack = createdTrack
            screenStream = createdStream
            screenCapturePermission = null // MediaProjection consent is single-use.
            true
        } catch (_: Exception) {
            try { capturer?.stopCapture() } catch (_: Exception) {}
            try { capturer?.dispose() } catch (_: Exception) {}
            // MediaStream owns and disposes tracks added to it.
            if (stream != null) {
                val owned = stream.videoTracks.any { it === track }
                try { stream.dispose() } catch (_: Exception) {}
                if (!owned) try { track?.dispose() } catch (_: Exception) {}
            } else try { track?.dispose() } catch (_: Exception) {}
            try { source?.dispose() } catch (_: Exception) {}
            try { helper?.dispose() } catch (_: Exception) {}
            screenCapturePermission = null
            false
        }
    }

    override suspend fun stopScreenShare() {
        stopScreenShareInternal()
    }

    @Synchronized
    private fun stopScreenShareInternal() {
        val wasActive = screenCapturer != null || screenStream != null
        val capturer = screenCapturer
        screenCapturer = null
        try {
            capturer?.stopCapture()
        } catch (_: Exception) {
        }
        try { capturer?.dispose() } catch (_: Exception) {}

        val cameraTrack = localVideoTrack
        val videoSender = peerConnection?.senders?.firstOrNull { it.track()?.kind() == "video" }
        if (videoSender != null && cameraTrack != null) {
            videoSender.setTrack(cameraTrack, false)
        }
        val oldScreenStream = screenStream.also { screenStream = null }
        val oldScreenTrack = screenVideoTrack.also { screenVideoTrack = null }
        val screenTrackOwned = oldScreenStream?.videoTracks?.any { it === oldScreenTrack } == true
        StreamTrackCleanup.dispose(
            stream = oldScreenStream?.let { { it.dispose() } },
            tracks = listOf(screenTrackOwned to { oldScreenTrack?.dispose(); Unit }),
        )
        screenVideoSource?.dispose()
        screenVideoSource = null
        screenSurfaceTextureHelper?.dispose()
        screenSurfaceTextureHelper = null
        screenCapturePermission = null
        if (wasActive) onScreenShareStopped?.invoke()
    }

    /** Attach a SurfaceViewRenderer created by the Compose UI. */
    override fun attachLocalRenderer(renderer: org.webrtc.SurfaceViewRenderer) {
        renderer.init(eglContext, null)
        localVideoTrackForRender?.addSink(renderer)
    }

    override fun attachRemoteRenderer(renderer: org.webrtc.SurfaceViewRenderer) {
        renderer.init(eglContext, null)
        remoteVideoTrackForRender?.addSink(renderer)
    }

    override fun detachRenderer(renderer: org.webrtc.SurfaceViewRenderer) {
        try {
            localVideoTrackForRender?.removeSink(renderer)
            remoteVideoTrackForRender?.removeSink(renderer)
            renderer.release()
        } catch (_: Exception) {
        }
    }

    override suspend fun dispose() {
        disposeInternal()
    }

    @Synchronized
    private fun disposeInternal() {
        if (upgradePrepared || upgradeSender != null) releaseUpgradeVideo()
        stopTrafficStats()
        trafficSessionPrepared = false
        hasLocalMedia = false
        hasRemoteMedia = false
        remoteAudioEnabled = true
        val screen = screenCapturer
        screenCapturer = null
        try {
            screen?.stopCapture()
        } catch (_: Exception) {
        }
        screen?.dispose()
        val oldScreenStream = screenStream.also { screenStream = null }
        val oldScreenTrack = screenVideoTrack.also { screenVideoTrack = null }
        val screenTrackOwned = oldScreenStream?.videoTracks?.any { it === oldScreenTrack } == true
        StreamTrackCleanup.dispose(
            stream = oldScreenStream?.let { { it.dispose() } },
            tracks = listOf(screenTrackOwned to { oldScreenTrack?.dispose(); Unit }),
        )
        screenVideoSource?.dispose()
        screenVideoSource = null
        screenSurfaceTextureHelper?.dispose()
        screenSurfaceTextureHelper = null
        screenCapturePermission = null
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        // A MediaStream disposes all tracks added to it. Do not dispose those wrappers twice.
        val oldLocalStream = localStream.also { localStream = null }
        val oldAudioTrack = localAudioTrack.also { localAudioTrack = null }
        val oldVideoTrack = localVideoTrack.also { localVideoTrack = null }
        val audioOwned = oldLocalStream?.audioTracks?.any { it === oldAudioTrack } == true
        val videoOwned = oldLocalStream?.videoTracks?.any { it === oldVideoTrack } == true
        StreamTrackCleanup.dispose(
            stream = oldLocalStream?.let { { it.dispose() } },
            tracks = listOf(audioOwned to { oldAudioTrack?.dispose(); Unit }, videoOwned to { oldVideoTrack?.dispose(); Unit }),
        )
        localAudioSource?.dispose()
        localAudioSource = null
        localVideoSource?.dispose()
        localVideoSource = null
        remoteStream?.dispose()
        remoteStream = null
        peerConnection?.dispose()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }

    private fun parseIceServers(raw: List<Map<String, Any?>>): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        for (entry in raw) {
            val urlsValue = entry["urls"]
            val urls = when (urlsValue) {
                is List<*> -> urlsValue.mapNotNull { it?.toString() }
                is String -> listOf(urlsValue)
                else -> emptyList()
            }
            if (urls.isEmpty()) continue
            val builder = PeerConnection.IceServer.builder(urls)
            val username = entry["username"]?.toString()
            val credential = entry["credential"]?.toString()
            if (username != null && credential != null) {
                builder.setUsername(username)
                builder.setPassword(credential)
            }
            servers.add(builder.createIceServer())
        }
        return servers
    }

    private suspend fun createSdp(
        peer: PeerConnection,
        constraints: MediaConstraints,
        isOffer: Boolean,
    ): SessionDescription = suspendCancellableCoroutine { cont ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) {
                    cont.resumeWithException(IllegalStateException("WebRTC returned no SDP."))
                } else {
                    cont.resume(description)
                }
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "WebRTC SDP failure."))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }
        if (isOffer) {
            peer.createOffer(observer, constraints)
        } else {
            peer.createAnswer(observer, constraints)
        }
    }

    private suspend fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
        suspendCancellableCoroutine<Unit> { cont ->
            peer.setLocalDescription(
                object : SdpObserver {
                    override fun onSetSuccess() { cont.resume(Unit) }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException(error ?: "setLocalDescription failed"))
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                },
                description,
            )
        }
    }
}
