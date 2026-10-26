package com.example.chat

import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallForegroundPolicy
import com.example.chat.data.services.CallLifecycleCoordinator
import com.example.chat.data.services.CallMediaController
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.CallSignalingService
import com.example.chat.data.services.CallTransportState
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.ui.calls.CallPresentationMode
import com.example.chat.ui.calls.CallPresentationController
import com.example.chat.ui.calls.CallPresentationPreferencesStore
import com.example.chat.ui.calls.CallPresentationPrefsStorage
import com.example.chat.ui.calls.CallScreenTemplate
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-verifiable fake for the WebRTC seam; records every interaction. */
private class FakeMediaController : CallMediaController {
    override var hasLocalMedia = false
        private set
    override var hasRemoteMedia = false
        private set
    var prepared = false
    var preparedIsVideo = false
    var preparedLowData = false
    var offerSdp = "fake-offer-sdp"
    var answerSdp = "fake-answer-sdp"
    var remoteDescriptions = mutableListOf<Pair<String, String>>()
    var addedCandidates = mutableListOf<Triple<String, String?, Int?>>()
    var audioEnabled = true
    var videoEnabled = true
    var cameraSwitched = false
    var speakerOn: Boolean? = null
    var screenShareStarted = false
    var screenShareStopped = false
    var disposed = false
    var iceServersSeen: List<Map<String, Any?>> = emptyList()

    override var onLocalIceCandidate: ((String, String?, Int?) -> Unit)? = null
    override var onRemoteStream: (() -> Unit)? = null
    override var onConnectionState: ((CallTransportState) -> Unit)? = null
    override var onIceFailed: (() -> Unit)? = null

    override suspend fun prepare(isVideo: Boolean, lowData: Boolean, iceServers: List<Map<String, Any?>>) {
        prepared = true
        preparedIsVideo = isVideo
        preparedLowData = lowData
        iceServersSeen = iceServers
        hasLocalMedia = true
    }

    override suspend fun createOffer(isVideo: Boolean): String = offerSdp
    override suspend fun createAnswer(isVideo: Boolean): String = answerSdp

    override suspend fun setRemoteDescription(sdp: String, type: String) {
        remoteDescriptions.add(sdp to type)
    }

    override suspend fun addRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        addedCandidates.add(Triple(candidate, sdpMid, sdpMLineIndex))
    }

    override fun setAudioEnabled(enabled: Boolean): Boolean {
        audioEnabled = enabled
        return true
    }

    override fun setVideoEnabled(enabled: Boolean): Boolean {
        videoEnabled = enabled
        return true
    }

    override suspend fun switchCamera(): Boolean {
        cameraSwitched = true
        return true
    }

    override suspend fun setSpeakerphoneOn(on: Boolean) {
        speakerOn = on
    }

    override suspend fun startScreenShare(): Boolean {
        screenShareStarted = true
        return true
    }

    override suspend fun stopScreenShare() {
        screenShareStopped = true
    }

    override suspend fun dispose() {
        disposed = true
        hasLocalMedia = false
        hasRemoteMedia = false
    }
}

/** Records outbound call_signal payloads (target + data). */
private class FakeSignalSender {
    data class Sent(val targetUserId: String, val data: Map<String, Any?>)

    val sent = mutableListOf<Sent>()

    val sender = com.example.chat.data.services.CallSignalSender { target, data ->
        sent.add(Sent(target, data))
    }
}

private class FakePrefsStorage : CallPresentationPrefsStorage {
    val bools = mutableMapOf<String, Boolean>()
    val strings = mutableMapOf<String, String>()
    var failWrites = false

    override suspend fun getBool(key: String): Boolean? = bools[key]
    override suspend fun getString(key: String): String? = strings[key]
    override suspend fun putBool(key: String, value: Boolean): Boolean {
        if (failWrites) return false
        bools[key] = value
        return true
    }
    override suspend fun putString(key: String, value: String): Boolean {
        if (failWrites) return false
        strings[key] = value
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CallSignalingParityTest {

    private fun newService(
        scope: CoroutineScope,
        userId: String? = "me",
        sender: FakeSignalSender = FakeSignalSender(),
        media: FakeMediaController = FakeMediaController(),
        records: MutableList<CallRecord> = mutableListOf(),
        lowData: Boolean = false,
    ): CallSignalingService = CallSignalingService(
        scope = scope,
        currentUserId = { userId },
        currentUserDisplayName = { "Me" },
        currentUserAvatarInitials = { "M" },
        currentUserAvatarColorHex = { "0xFF112233" },
        signalSender = sender.sender,
        mediaFactory = { media },
        callRecordSink = { records.add(it) },
        lowDataUsageEnabled = { lowData },
    )

    // ── initiateCall guards (Dart initiateCall) ──────────────────────────

    @Test
    fun `initiateCall requires authentication`() = runTest {
        val service = newService(backgroundScope, userId = null)
        val error = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.initiateCall("peer", "Peer", isVideo = false)
            }
        }
        assertEquals("Authentication required.", error.message)
    }

    @Test
    fun `initiateCall rejects self call`() = runTest {
        val service = newService(backgroundScope)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.initiateCall("me", "Me", isVideo = false)
            }
        }
    }

    @Test
    fun `initiateCall rejects while another call is active`() = runTest {
        val sender = FakeSignalSender()
        val media = FakeMediaController()
        val service = newService(backgroundScope, sender = sender, media = media)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.initiateCall("other", "Other", isVideo = false)
            }
        }
    }

    // ── outgoing offer flow ──────────────────────────────────────────────

    @Test
    fun `initiateCall sends offer signal and rings`() = runTest {
        val sender = FakeSignalSender()
        val media = FakeMediaController()
        val service = newService(backgroundScope, sender = sender, media = media)
        service.initiateCall("peer", "Peer Name", "P", "0xFF445566", isVideo = true)
        advanceUntilIdle()

        assertTrue(media.prepared)
        assertTrue(media.preparedIsVideo)
        assertEquals(true, media.speakerOn)
        assertEquals(1, sender.sent.size)
        val offer = sender.sent.single()
        assertEquals("peer", offer.targetUserId)
        assertEquals("offer", offer.data["signal_type"])
        assertEquals("me", offer.data["caller_id"])
        assertEquals("Me", offer.data["caller_name"])
        assertEquals("M", offer.data["caller_avatar_initials"])
        assertEquals("0xFF112233", offer.data["caller_avatar_color_hex"])
        assertEquals(true, offer.data["is_video"])
        assertEquals("fake-offer-sdp", offer.data["sdp"])
        val callId = offer.data["call_id"] as String
        assertTrue(callId.isNotEmpty())

        val session = service.currentSession!!
        assertEquals(CallSessionState.ringing, session.state)
        assertEquals(callId, session.callId)
        assertTrue(session.isOutgoing)
        assertTrue(session.isVideo)
        assertEquals(AudioRouteType.speaker, session.audioRoute)
        assertEquals(CallSignalingService.defaultIceServers(), media.iceServersSeen)
    }

    @Test
    fun `voice call starts on earpiece`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        assertEquals(false, media.speakerOn)
        assertEquals(AudioRouteType.earpiece, service.currentSession!!.audioRoute)
    }

    @Test
    fun `low data preference reaches media constraints`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media, lowData = true)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        assertTrue(media.preparedLowData)
    }

    @Test
    fun `unanswered outgoing call ends after 40s ring timeout`() = runTest {
        val sender = FakeSignalSender()
        val records = mutableListOf<CallRecord>()
        val service = newService(backgroundScope, sender = sender, records = records)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        assertEquals(CallSessionState.ringing, service.currentSession!!.state)

        advanceTimeBy(40_001)
        advanceUntilIdle()

        val session = service.currentSession!!
        assertEquals(CallSessionState.ended, session.state)
        assertEquals("hangup", sender.sent.last().data["signal_type"])
        assertEquals(1, records.size)
        assertEquals(CallDirection.outgoing, records.single().direction)
        assertEquals(0L, records.single().durationSeconds)
    }

    // ── answer flow ──────────────────────────────────────────────────────

    @Test
    fun `answer signal applies remote description and connects`() = runTest {
        val sender = FakeSignalSender()
        val media = FakeMediaController()
        val service = newService(backgroundScope, sender = sender, media = media)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        val callId = service.currentSession!!.callId

        // A candidate arriving before the remote description is queued.
        service.handleRemoteSignal(mapOf(
            "signal_type" to "ice_candidate",
            "call_id" to callId,
            "sender_id" to "peer",
            "candidate" to "candidate:early",
            "sdpMid" to "0",
            "sdpMLineIndex" to "0",
        ))
        advanceUntilIdle()
        assertTrue(media.addedCandidates.isEmpty())

        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer",
            "call_id" to callId,
            "sender_id" to "peer",
            "sdp" to "remote-answer-sdp",
        ))
        advanceUntilIdle()

        assertEquals(listOf("remote-answer-sdp" to "answer"), media.remoteDescriptions)
        assertEquals(CallSessionState.connecting, service.currentSession!!.state)
        // Queued candidate flushed after the remote description.
        assertEquals(listOf(Triple("candidate:early", "0", 0)), media.addedCandidates)
    }

    @Test
    fun `echo of own signals is ignored`() = runTest {
        val service = newService(backgroundScope)
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer",
            "call_id" to "c1",
            "sender_id" to "me",
            "sdp" to "x",
        ))
        advanceUntilIdle()
        assertNull(service.currentSession)
    }

    // ── incoming offer flow ──────────────────────────────────────────────

    @Test
    fun `incoming offer creates incoming session with caller presentation`() = runTest {
        val service = newService(backgroundScope)
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer",
            "call_id" to "call-9",
            "caller_id" to "peer",
            "caller_name" to "Priya",
            "caller_avatar_initials" to "P",
            "caller_avatar_color_hex" to "0xFFAA00",
            "is_video" to true,
            "sdp" to "remote-offer",
        ))
        advanceUntilIdle()

        val session = service.currentSession!!
        assertEquals("call-9", session.callId)
        assertEquals("peer", session.remoteUserId)
        assertEquals("Priya", session.remoteDisplayName)
        assertEquals("P", session.remoteAvatarInitials)
        assertEquals("0xFFAA00", session.remoteAvatarColorHex)
        assertTrue(session.isVideo)
        assertFalse(session.isOutgoing)
        assertEquals(CallSessionState.incoming, session.state)
        assertEquals(AudioRouteType.speaker, session.audioRoute)
    }

    @Test
    fun `second offer during active call gets busy`() = runTest {
        val sender = FakeSignalSender()
        val service = newService(backgroundScope, sender = sender)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        // Move to connecting so isInActiveCall is true.
        val callId = service.currentSession!!.callId
        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer", "call_id" to callId, "sender_id" to "peer", "sdp" to "a",
        ))
        advanceUntilIdle()
        assertEquals(CallSessionState.connecting, service.currentSession!!.state)

        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer",
            "call_id" to "other-call",
            "caller_id" to "intruder",
            "sdp" to "x",
        ))
        advanceUntilIdle()
        val busy = sender.sent.last()
        assertEquals("intruder", busy.targetUserId)
        assertEquals("busy", busy.data["signal_type"])
        assertEquals("other-call", busy.data["call_id"])
        // Original session untouched.
        assertEquals(callId, service.currentSession!!.callId)
    }

    @Test
    fun `unanswered incoming call times out with missed record`() = runTest {
        val sender = FakeSignalSender()
        val records = mutableListOf<CallRecord>()
        val service = newService(backgroundScope, sender = sender, records = records)
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to "call-1", "caller_id" to "peer", "sdp" to "o",
        ))
        advanceUntilIdle()
        assertEquals(CallSessionState.incoming, service.currentSession!!.state)

        advanceTimeBy(40_001)
        advanceUntilIdle()

        assertEquals(CallSessionState.ended, service.currentSession!!.state)
        assertEquals("hangup", sender.sent.last().data["signal_type"])
        assertEquals(CallDirection.missed, records.single().direction)
    }

    // ── accept / decline / hangup ────────────────────────────────────────

    @Test
    fun `acceptCall sends answer with cached offer sdp`() = runTest {
        val sender = FakeSignalSender()
        val media = FakeMediaController()
        val service = newService(backgroundScope, sender = sender, media = media)
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to "call-2", "caller_id" to "peer", "sdp" to "cached-offer",
        ))
        advanceUntilIdle()

        service.acceptCall()
        advanceUntilIdle()

        assertEquals(listOf("cached-offer" to "offer"), media.remoteDescriptions)
        val answer = sender.sent.last()
        assertEquals("peer", answer.targetUserId)
        assertEquals("answer", answer.data["signal_type"])
        assertEquals("call-2", answer.data["call_id"])
        assertEquals("fake-answer-sdp", answer.data["sdp"])
        assertEquals(CallSessionState.connecting, service.currentSession!!.state)
    }

    @Test
    fun `malformed offer is rejected before incoming media creation`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media)
        // Invalid protocol input must not present a fake answerable call.
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to "call-3", "caller_id" to "peer",
        ))
        advanceUntilIdle()
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { service.acceptCall() }
        }
        advanceUntilIdle()
        assertNull(service.currentSession)
        assertFalse(media.prepared)
    }

    @Test
    fun `declineCall sends decline and logs missed`() = runTest {
        val sender = FakeSignalSender()
        val records = mutableListOf<CallRecord>()
        val service = newService(backgroundScope, sender = sender, records = records)
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to "call-4", "caller_id" to "peer", "sdp" to "o",
        ))
        advanceUntilIdle()

        service.declineCall()
        advanceUntilIdle()

        assertEquals("decline", sender.sent.last().data["signal_type"])
        assertEquals(CallSessionState.declined, service.currentSession!!.state)
        val record = records.single()
        assertEquals(CallDirection.missed, record.direction)
        assertEquals(0L, record.durationSeconds)
        assertEquals("peer", record.callerId)
    }

    @Test
    fun `remote hangup on connected incoming call logs incoming duration`() = runTest {
        val sender = FakeSignalSender()
        val media = FakeMediaController()
        val records = mutableListOf<CallRecord>()
        val service = newService(backgroundScope, sender = sender, media = media, records = records)
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to "call-5", "caller_id" to "peer", "sdp" to "o",
        ))
        advanceUntilIdle()
        service.acceptCall()
        advanceUntilIdle()

        // Transport connects: duration timer starts.
        media.onConnectionState?.invoke(CallTransportState.connected)
        advanceUntilIdle()
        assertEquals(CallSessionState.connected, service.currentSession!!.state)
        advanceTimeBy(3001)
        assertTrue(service.callDurationSeconds >= 3)

        service.handleRemoteSignal(mapOf(
            "signal_type" to "hangup", "call_id" to "call-5", "sender_id" to "peer",
        ))
        advanceUntilIdle()

        assertEquals(CallSessionState.ended, service.currentSession!!.state)
        val record = records.single()
        assertEquals(CallDirection.incoming, record.direction)
        assertTrue(record.durationSeconds >= 3)
        assertTrue(media.disposed)
    }

    @Test
    fun `remote busy marks outgoing call busy and logs once`() = runTest {
        val sender = FakeSignalSender()
        val records = mutableListOf<CallRecord>()
        val service = newService(backgroundScope, sender = sender, records = records)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        val callId = service.currentSession!!.callId

        service.handleRemoteSignal(mapOf(
            "signal_type" to "busy", "call_id" to callId, "sender_id" to "peer",
        ))
        advanceUntilIdle()

        assertEquals(CallSessionState.busy, service.currentSession!!.state)
        assertEquals(1, records.size)
        assertEquals(CallDirection.outgoing, records.single().direction)
    }

    // ── media controls ───────────────────────────────────────────────────

    @Test
    fun `toggleMute flips local audio tracks`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()

        service.toggleMute()
        assertTrue(service.currentSession!!.isMuted)
        assertFalse(media.audioEnabled)
        service.toggleMute()
        assertFalse(service.currentSession!!.isMuted)
        assertTrue(media.audioEnabled)
    }

    @Test
    fun `toggleCamera only applies to video calls`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        service.toggleCamera()
        assertFalse(service.currentSession!!.isCameraOff)
    }

    @Test
    fun `switchCamera flips camera facing on success`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media)
        service.initiateCall("peer", "Peer", isVideo = true)
        advanceUntilIdle()

        service.switchCamera()
        advanceUntilIdle()
        assertTrue(media.cameraSwitched)
        assertFalse(service.currentSession!!.isFrontCamera)
    }

    @Test
    fun `setAudioRoute rejects bluetooth like Dart`() = runTest {
        val service = newService(backgroundScope)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking { service.setAudioRoute(AudioRouteType.bluetooth) }
        }
    }

    @Test
    fun `screen share toggles replace-track flow`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media)
        service.initiateCall("peer", "Peer", isVideo = true)
        advanceUntilIdle()

        service.startScreenShare()
        advanceUntilIdle()
        assertTrue(media.screenShareStarted)
        assertTrue(service.currentSession!!.isSharingScreen)

        // Camera switch is blocked while sharing (Dart guard).
        media.cameraSwitched = false
        service.switchCamera()
        advanceUntilIdle()
        assertFalse(media.cameraSwitched)

        service.stopScreenShare()
        advanceUntilIdle()
        assertTrue(media.screenShareStopped)
        assertFalse(service.currentSession!!.isSharingScreen)
    }

    @Test
    fun `sendCallReaction stays unsupported like Dart`() = runTest {
        val service = newService(backgroundScope)
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            service.sendCallReaction("👍")
        }
    }

    // ── demo calls ───────────────────────────────────────────────────────

    @Test
    fun `demo outgoing call walks initiating-ringing-connected`() = runTest {
        val service = newService(backgroundScope)
        service.startDemoCall(remoteUserId = "demo-peer", remoteDisplayName = "Demo")
        assertEquals(CallSessionState.initiating, service.currentSession!!.state)
        assertTrue(service.currentSession!!.isDemo)

        advanceTimeBy(1201)
        assertEquals(CallSessionState.ringing, service.currentSession!!.state)

        advanceTimeBy(1801)
        assertEquals(CallSessionState.connected, service.currentSession!!.state)

        advanceTimeBy(2001)
        assertTrue(service.callDurationSeconds >= 2)
    }

    @Test
    fun `demo incoming call holds until accept`() = runTest {
        val service = newService(backgroundScope)
        service.startDemoCall(remoteUserId = "demo-peer", remoteDisplayName = "Demo", isOutgoing = false)
        assertEquals(CallSessionState.incoming, service.currentSession!!.state)
        advanceTimeBy(10_001)
        assertEquals(CallSessionState.incoming, service.currentSession!!.state)

        service.acceptCall()
        advanceUntilIdle()
        assertEquals(CallSessionState.connected, service.currentSession!!.state)
    }

    @Test
    fun `demo endCall clears session after 350ms`() = runTest {
        val service = newService(backgroundScope)
        service.startDemoCall(remoteUserId = "demo-peer", remoteDisplayName = "Demo")
        service.endCall()
        advanceUntilIdle()
        assertEquals(CallSessionState.ended, service.currentSession!!.state)
        advanceTimeBy(351)
        advanceUntilIdle()
        assertNull(service.currentSession)
        assertEquals(0, service.callDurationSeconds)
    }

    // ── transport state mapping ──────────────────────────────────────────

    @Test
    fun `transport disconnect during connected call enters reconnecting`() = runTest {
        val media = FakeMediaController()
        val service = newService(backgroundScope, media = media)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        val callId = service.currentSession!!.callId
        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer", "call_id" to callId, "sender_id" to "peer", "sdp" to "a",
        ))
        advanceUntilIdle()
        media.onConnectionState?.invoke(CallTransportState.connected)
        advanceUntilIdle()

        media.onConnectionState?.invoke(CallTransportState.disconnected)
        advanceUntilIdle()
        assertEquals(CallSessionState.reconnecting, service.currentSession!!.state)
    }

    @Test
    fun `ice failure marks call failed with record`() = runTest {
        val media = FakeMediaController()
        val records = mutableListOf<CallRecord>()
        val service = newService(backgroundScope, media = media, records = records)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        val callId = service.currentSession!!.callId
        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer", "call_id" to callId, "sender_id" to "peer", "sdp" to "a",
        ))
        advanceUntilIdle()

        media.onIceFailed?.invoke()
        advanceUntilIdle()
        assertEquals(CallSessionState.failed, service.currentSession!!.state)
        assertEquals(1, records.size)
        assertEquals(CallDirection.outgoing, records.single().direction)
    }

    @Test
    fun `connection timeout ends connecting call`() = runTest {
        val sender = FakeSignalSender()
        val service = newService(backgroundScope, sender = sender)
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        val callId = service.currentSession!!.callId
        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer", "call_id" to callId, "sender_id" to "peer", "sdp" to "a",
        ))
        advanceUntilIdle()
        assertEquals(CallSessionState.connecting, service.currentSession!!.state)

        advanceTimeBy(25_001)
        advanceUntilIdle()
        assertEquals(CallSessionState.ended, service.currentSession!!.state)
        assertEquals("hangup", sender.sent.last().data["signal_type"])
    }

    // ── foreground policy (Dart ChatyCallForegroundService.shouldRunFor) ──

    private fun sessionIn(state: CallSessionState, outgoing: Boolean = true) = ChatyCallSession(
        callId = "c",
        remoteUserId = "r",
        remoteDisplayName = "R",
        isVideo = false,
        isOutgoing = outgoing,
        state = state,
        startedAt = Instant.now(),
    )

    @Test
    fun `foreground policy matches Dart shouldRunFor table`() {
        assertFalse(CallForegroundPolicy.shouldRunFor(null))
        assertTrue(CallForegroundPolicy.shouldRunFor(sessionIn(CallSessionState.ringing, outgoing = true)))
        assertFalse(CallForegroundPolicy.shouldRunFor(sessionIn(CallSessionState.ringing, outgoing = false)))
        assertTrue(CallForegroundPolicy.shouldRunFor(sessionIn(CallSessionState.connecting)))
        assertTrue(CallForegroundPolicy.shouldRunFor(sessionIn(CallSessionState.connected)))
        assertTrue(CallForegroundPolicy.shouldRunFor(sessionIn(CallSessionState.reconnecting)))
        for (state in listOf(
            CallSessionState.idle, CallSessionState.initiating, CallSessionState.incoming,
            CallSessionState.declined, CallSessionState.busy, CallSessionState.missed,
            CallSessionState.ended, CallSessionState.failed,
        )) {
            assertFalse("state $state", CallForegroundPolicy.shouldRunFor(sessionIn(state)))
        }
    }

    @Test
    fun `foreground notification text matches Dart`() {
        assertEquals("Chaty voice call", CallForegroundPolicy.notificationTitle(sessionIn(CallSessionState.connected)))
        assertEquals("Chaty video call", CallForegroundPolicy.notificationTitle(sessionIn(CallSessionState.connected).copy(isVideo = true)))
        assertEquals("Calling R…", CallForegroundPolicy.notificationText(sessionIn(CallSessionState.ringing)))
        assertEquals("Connecting with R…", CallForegroundPolicy.notificationText(sessionIn(CallSessionState.connecting)))
        assertEquals("Reconnecting with R…", CallForegroundPolicy.notificationText(sessionIn(CallSessionState.reconnecting)))
        assertEquals("In call with R", CallForegroundPolicy.notificationText(sessionIn(CallSessionState.connected)))
    }

    // ── lifecycle coordinator ────────────────────────────────────────────

    @Test
    fun `reconnect grace expiry ends the call`() = runTest {
        val media = FakeMediaController()
        val sender = FakeSignalSender()
        val service = newService(backgroundScope, sender = sender, media = media)
        val coordinator = CallLifecycleCoordinator(
            scope = backgroundScope,
            callService = service,
            foregroundSync = {},
        )
        coordinator.start()
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        val callId = service.currentSession!!.callId
        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer", "call_id" to callId, "sender_id" to "peer", "sdp" to "a",
        ))
        advanceUntilIdle()
        media.onConnectionState?.invoke(CallTransportState.connected)
        advanceUntilIdle()
        media.onConnectionState?.invoke(CallTransportState.disconnected)
        advanceUntilIdle()
        assertEquals(CallSessionState.reconnecting, service.currentSession!!.state)

        advanceTimeBy(15_001)
        advanceUntilIdle()
        assertEquals(CallSessionState.ended, service.currentSession!!.state)
        coordinator.dispose()
    }

    @Test
    fun `app detach ends live call and stops foreground`() = runTest {
        val service = newService(backgroundScope)
        var stopped = false
        val coordinator = CallLifecycleCoordinator(
            scope = backgroundScope,
            callService = service,
            foregroundSync = {},
            foregroundStop = { stopped = true },
        )
        coordinator.start()
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()

        coordinator.onAppDetached()
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(CallSessionState.ended, service.currentSession!!.state)
        assertTrue(stopped)
        coordinator.dispose()
    }

    // ── presentation preferences + controller ────────────────────────────

    @Test
    fun `preferences store loads Dart defaults`() = runTest {
        val store = CallPresentationPreferencesStore(FakePrefsStorage(), backgroundScope)
        store.initialize()
        advanceTimeBy(1)
        assertTrue(store.isInitialized)
        assertTrue(store.value.dynamicIslandEnabled)
        assertTrue(store.value.pictureInPictureEnabled)
        assertFalse(store.value.lowDataUsageEnabled)
        assertEquals("chatyClassic", store.value.incomingCallTemplate)
        assertEquals("chatyClassic", store.value.ongoingCallTemplate)
    }

    @Test
    fun `preferences store persists with Dart keys`() = runTest {
        val storage = FakePrefsStorage()
        val store = CallPresentationPreferencesStore(storage, backgroundScope)
        store.setLowDataUsageEnabled(true)
        store.setIncomingCallTemplate("card")
        store.setOutgoingCallTemplate("edge")
        advanceTimeBy(1)
        assertEquals(true, storage.bools["chaty.calls.low_data_usage_enabled.v1"])
        assertEquals("card", storage.strings["chaty.calls.incoming_template.v1"])
        assertEquals("edge", storage.strings["chaty.calls.ongoing_template.v1"])
        assertTrue(store.value.lowDataUsageEnabled)
        assertEquals("card", store.value.incomingCallTemplate)
        assertEquals("edge", store.value.ongoingCallTemplate)
    }

    @Test
    fun `CallScreenTemplate fromName falls back to classic`() {
        assertEquals(CallScreenTemplate.chatyClassic, CallScreenTemplate.fromName(null))
        assertEquals(CallScreenTemplate.chatyClassic, CallScreenTemplate.fromName("bogus"))
        assertEquals(CallScreenTemplate.sheet, CallScreenTemplate.fromName("sheet"))
        assertEquals(CallScreenTemplate.card, CallScreenTemplate.fromName("card"))
        assertEquals(CallScreenTemplate.edge, CallScreenTemplate.fromName("edge"))
    }

    @Test
    fun `presentation controller minimize picks pip for video and island for voice`() = runTest {
        val service = newService(backgroundScope)
        val store = CallPresentationPreferencesStore(FakePrefsStorage(), backgroundScope)
        val controller = CallPresentationController(service, store)
        controller.start()
        advanceUntilIdle()

        service.initiateCall("peer", "Peer", isVideo = true)
        advanceUntilIdle()
        controller.showFullScreen()
        controller.minimizeToPipOrIsland()
        assertEquals(CallPresentationMode.inAppVideoPip, controller.mode)

        service.endCall()
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(CallPresentationMode.none, controller.mode)
        controller.dispose()
    }

    @Test
    fun `presentation controller blocks invalid forced modes`() = runTest {
        val service = newService(backgroundScope)
        val store = CallPresentationPreferencesStore(FakePrefsStorage(), backgroundScope)
        val controller = CallPresentationController(service, store)
        controller.start()
        advanceUntilIdle()

        // No active call: non-none modes rejected.
        controller.setMode(CallPresentationMode.inAppIsland)
        assertEquals(CallPresentationMode.none, controller.mode)

        // Voice call: video pip rejected; system pip requires native confirmation.
        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        controller.setMode(CallPresentationMode.inAppVideoPip)
        assertFalse(controller.mode == CallPresentationMode.inAppVideoPip)
        controller.setMode(CallPresentationMode.systemPip)
        assertFalse(controller.mode == CallPresentationMode.systemPip)
        controller.dispose()
    }

    @Test
    fun `reportNativePipMode honors video+preference gate`() = runTest {
        val service = newService(backgroundScope)
        val store = CallPresentationPreferencesStore(FakePrefsStorage(), backgroundScope)
        store.initialize()
        advanceTimeBy(1)
        val controller = CallPresentationController(service, store)
        controller.start()
        advanceUntilIdle()

        service.initiateCall("peer", "Peer", isVideo = false)
        advanceUntilIdle()
        controller.reportNativePipMode(true) // voice call: rejected
        assertFalse(controller.mode == CallPresentationMode.systemPip)
        controller.dispose()
    }
}
