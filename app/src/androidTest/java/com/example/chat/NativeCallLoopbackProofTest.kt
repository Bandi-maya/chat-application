package com.example.chat

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.data.services.*
import com.example.chat.ui.calls.runCallAction
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.joinAll
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Native local-only test. No account, backend, TURN, camera or microphone capture. */
@RunWith(AndroidJUnit4::class)
class NativeCallLoopbackProofTest {
    private class PeerObserver : PeerConnection.Observer {
        val connected = CountDownLatch(1)
        val frames = AtomicInteger()
        @Volatile var candidate: (IceCandidate) -> Unit = {}
        override fun onIceCandidate(value: IceCandidate?) { value?.let(candidate) }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
            if (state == PeerConnection.PeerConnectionState.CONNECTED) connected.countDown()
        }
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            (receiver?.track() as? VideoTrack)?.addSink { frames.incrementAndGet() }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(value: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(values: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }
    private class SdpResult : SdpObserver {
        val done = CountDownLatch(1)
        var description: SessionDescription? = null
        var failed = false
        override fun onCreateSuccess(value: SessionDescription?) { description = value; done.countDown() }
        override fun onSetSuccess() { done.countDown() }
        override fun onCreateFailure(error: String?) { failed = true; done.countDown() }
        override fun onSetFailure(error: String?) { failed = true; done.countDown() }
        fun await() { assertTrue("SDP callback within 10s", done.await(10, TimeUnit.SECONDS)); assertFalse("SDP operation succeeded", failed) }
    }
    private class CandidateInbox(private val peer: PeerConnection) {
        private val queued = mutableListOf<IceCandidate>()
        private var ready = false
        private var closed = false
        val rejected = AtomicInteger()
        @Synchronized fun receive(value: IceCandidate) {
            if (closed) return
            if (ready) { if (!peer.addIceCandidate(value)) rejected.incrementAndGet() } else queued.add(value)
        }
        @Synchronized fun close() { closed = true; queued.clear() }
        @Synchronized fun remoteReady() {
            if (closed) return
            ready = true
            queued.forEach { if (!peer.addIceCandidate(it)) rejected.incrementAndGet() }
            queued.clear()
        }
    }
    @Test fun nativeTwoLocalPeersConnectAndReceiveSyntheticVideoFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        var factory: PeerConnectionFactory? = null
        var source: VideoSource? = null
        var track: VideoTrack? = null
        var a: PeerConnection? = null
        var b: PeerConnection? = null
        var toA: CandidateInbox? = null
        var toB: CandidateInbox? = null
        var pump: java.util.concurrent.ScheduledExecutorService? = null
        val aObserver = PeerObserver(); val bObserver = PeerObserver()
        try {
            val liveFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
                .setVideoDecoderFactory(SoftwareVideoDecoderFactory()).createPeerConnectionFactory()
            factory = liveFactory
            val liveSource = liveFactory.createVideoSource(false); source = liveSource
            val liveTrack = liveFactory.createVideoTrack("synthetic-video", liveSource); track = liveTrack
            val config = PeerConnection.RTCConfiguration(emptyList()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
            val liveA = liveFactory.createPeerConnection(config, aObserver) ?: error("Local peer A unavailable"); a = liveA
            val liveB = liveFactory.createPeerConnection(config, bObserver) ?: error("Local peer B unavailable"); b = liveB
            val livePump = Executors.newSingleThreadScheduledExecutor(); pump = livePump
            val inboxA = CandidateInbox(liveA); toA = inboxA
            val inboxB = CandidateInbox(liveB); toB = inboxB
            aObserver.candidate = inboxB::receive; bObserver.candidate = inboxA::receive
            assertNotNull("Synthetic video sender created", liveA.addTrack(liveTrack, listOf("synthetic-stream")))
            val offer = SdpResult().also { liveA.createOffer(it, MediaConstraints()); it.await() }.description!!
            SdpResult().also { liveA.setLocalDescription(it, offer); it.await() }
            SdpResult().also { liveB.setRemoteDescription(it, offer); it.await() }; inboxB.remoteReady()
            val answer = SdpResult().also { liveB.createAnswer(it, MediaConstraints()); it.await() }.description!!
            SdpResult().also { liveB.setLocalDescription(it, answer); it.await() }
            SdpResult().also { liveA.setRemoteDescription(it, answer); it.await() }; inboxA.remoteReady()
            liveSource.capturerObserver.onCapturerStarted(true)
            livePump.scheduleAtFixedRate({
                val frame = VideoFrame(JavaI420Buffer.allocate(160, 120), 0, System.nanoTime())
                try { liveSource.capturerObserver.onFrameCaptured(frame) } finally { frame.release() }
            }, 0, 100, TimeUnit.MILLISECONDS)
            assertTrue("Peer A native CONNECTED within 20s", aObserver.connected.await(20, TimeUnit.SECONDS))
            assertTrue("Peer B native CONNECTED within 20s", bObserver.connected.await(20, TimeUnit.SECONDS))
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            while (bObserver.frames.get() < 3 && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            assertTrue("Remote native decoder received at least 3 synthetic video frames", bObserver.frames.get() >= 3)
            assertEquals("Peer A rejects no candidates", 0, inboxA.rejected.get())
            assertEquals("Peer B rejects no candidates", 0, inboxB.rejected.get())
        } finally {
            // Close synchronized inboxes before disposing native peers. Late ICE
            // callbacks are harmless and cannot add to a disposed connection.
            toA?.close(); toB?.close()
            aObserver.candidate = {}; bObserver.candidate = {}
            var cleanupFailure: Throwable? = null
            fun clean(action: () -> Unit) {
                try { action() } catch (error: Throwable) {
                    if (cleanupFailure == null) cleanupFailure = error else cleanupFailure!!.addSuppressed(error)
                }
            }
            clean { pump?.shutdownNow(); pump?.awaitTermination(3, TimeUnit.SECONDS) }
            clean { source?.capturerObserver?.onCapturerStopped() }
            clean { a?.close() }; clean { b?.close() }
            clean { a?.dispose() }; clean { b?.dispose() }
            clean { track?.dispose() }; clean { source?.dispose() }; clean { factory?.dispose() }
            cleanupFailure?.let { throw it }
        }
        val alive = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { alive.countDown() }
        assertEquals("Main thread remains live after native cleanup", 0L, alive.count)
    }

    @Test fun explicitDeniedMediaSetupIsContainedAndMainThreadSurvives() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Deterministic synthetic denial, regardless of emulator's prior grants.
        // No pm revoke: revoking target permissions can kill the instrumentation process.
        val deniedContext = object : ContextWrapper(instrumentation.targetContext) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.RECORD_AUDIO || permission == Manifest.permission.CAMERA)
                    PackageManager.PERMISSION_DENIED else super.checkPermission(permission, pid, uid)
        }
        val previous = CallPermissionGate.requester
        var requests = 0; var failures = 0
        val media = WebRtcCallMediaController(deniedContext)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val service = CallSignalingService(scope, { "synthetic-local" }, signalSender = CallSignalSender { _, _ -> fail("Denied setup must not signal") }, mediaFactory = { media }, iceServersProvider = { emptyList() })
        try {
            CallPermissionGate.requester = { permissions -> requests++; permissions.associateWith { false } }
            val completed = runCallAction({ failures++ }) { service.initiateCall("synthetic-other", "Synthetic", isVideo = true) }
            assertFalse("Denied setup returns failure", completed)
            assertEquals("Denial path actually requested permissions", 1, requests)
            assertEquals("Failure shown once", 1, failures)
            assertEquals(CallSessionState.failed, service.currentSession?.state)
            assertFalse("No local media opened after denial", media.hasLocalMedia)
            instrumentation.runOnMainSync { assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper()) }
        } finally {
            CallPermissionGate.requester = previous
            service.dispose()
            scope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.joinAll()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
