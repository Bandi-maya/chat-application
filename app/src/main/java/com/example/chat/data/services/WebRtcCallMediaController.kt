package com.example.chat.data.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
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
    override var onConnectionState: ((CallTransportState) -> Unit)? = null
    override var onIceFailed: (() -> Unit)? = null

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
                    hasRemoteMedia = true
                    onRemoteStream?.invoke()
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val stream = streams?.firstOrNull()
                if (stream != null && remoteStream == null) {
                    remoteStream = stream
                    hasRemoteMedia = true
                    onRemoteStream?.invoke()
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                onConnectionState?.invoke(
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> CallTransportState.connected
                        PeerConnection.PeerConnectionState.DISCONNECTED -> CallTransportState.disconnected
                        PeerConnection.PeerConnectionState.FAILED -> CallTransportState.failed
                        PeerConnection.PeerConnectionState.CLOSED -> CallTransportState.closed
                        else -> CallTransportState.other
                    },
                )
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

        openLocalMedia(isVideo, lowData, factory, peer)
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

        val audioConstraints = MediaConstraints().apply {
            if (lowData) {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                optional.add(MediaConstraints.KeyValuePair("channelCount", "1"))
            }
        }
        val audioSource: AudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("chaty_audio", audioSource)
        stream.addTrack(localAudioTrack)

        if (isVideo) {
            val capturer = createCameraCapturer()
                ?: throw IllegalStateException("No usable camera for the video call.")
            videoCapturer = capturer
            val helper = SurfaceTextureHelper.create("chaty_capture", eglContext)
            surfaceTextureHelper = helper
            val videoSource: VideoSource = factory.createVideoSource(capturer.isScreencast)
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
        localStream = stream
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

        val capturer = ScreenCapturerAndroid(
            permission,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    // User revoked projection; restore camera track.
                }
            },
        )
        val helper = SurfaceTextureHelper.create("chaty_screen", eglContext)
        val source = factory.createVideoSource(true)
        capturer.initialize(helper, appContext, source.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        val track = factory.createVideoTrack("chaty_screen", source)
        track.setEnabled(true)

        val videoSender = peer.senders.firstOrNull { it.track()?.kind() == "video" }
            ?: return false
        videoSender.setTrack(track, false)

        screenCapturer = capturer
        screenStream = factory.createLocalMediaStream("chaty_screen_stream")
        screenStream?.addTrack(track)
        return true
    }

    override suspend fun stopScreenShare() {
        val capturer = screenCapturer
        screenCapturer = null
        try {
            capturer?.stopCapture()
        } catch (_: Exception) {
        }
        capturer?.dispose()
        val cameraTrack = localVideoTrack
        val videoSender = peerConnection?.senders?.firstOrNull { it.track()?.kind() == "video" }
        if (videoSender != null && cameraTrack != null) {
            videoSender.setTrack(cameraTrack, false)
        }
        val stream = screenStream
        screenStream = null
        stream?.dispose()
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

    private fun disposeInternal() {
        hasLocalMedia = false
        hasRemoteMedia = false
        val screen = screenCapturer
        screenCapturer = null
        try {
            screen?.stopCapture()
        } catch (_: Exception) {
        }
        screen?.dispose()
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        localStream?.dispose()
        localStream = null
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
