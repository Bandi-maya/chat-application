package com.example.chat

import com.example.chat.data.services.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

private class LatestFakeMedia : CallMediaController {
    override var hasLocalMedia = false
        private set
    override var hasRemoteMedia = false
        private set
    var prepared = false
    var preparedIsVideo = false
    var preparedLowData = false
    var offerSdp = "fake-offer-sdp"
    var duringOffer: () -> Unit = {}
    var duringAnswer: () -> Unit = {}
    var duringRemoteDescription: () -> Unit = {}
    var duringPrepare: () -> Unit = {}
    var awaitPrepare: suspend () -> Unit = {}
    var awaitRemote: suspend () -> Unit = {}
    var awaitSwitch: suspend () -> Unit = {}
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
        duringPrepare()
        awaitPrepare()
        prepared = true
        preparedIsVideo = isVideo
        preparedLowData = lowData
        iceServersSeen = iceServers
        hasLocalMedia = true
    }

    override suspend fun createOffer(isVideo: Boolean): String { duringOffer(); return offerSdp }
    override suspend fun createAnswer(isVideo: Boolean): String { duringAnswer(); return answerSdp }

    override suspend fun setRemoteDescription(sdp: String, type: String) {
        duringRemoteDescription()
        awaitRemote()
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
        awaitSwitch()
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

class LatestApiCallParityTest {
    @Test fun twoSyntheticPeersExchangeSdpAndIceAndWaitForMediaConnectedCallback() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val aMedia = LatestFakeMedia(); val bMedia = LatestFakeMedia()
            val toA = ArrayDeque<Map<String, Any?>>()
            val toB = ArrayDeque<Map<String, Any?>>()
            val a = CallSignalingService(scope, { "synthetic-a" }, signalSender = CallSignalSender { target, data ->
                assertEquals("synthetic-b", target); toB.addLast(data + ("sender_id" to "synthetic-a"))
            }, mediaFactory = { aMedia })
            val b = CallSignalingService(scope, { "synthetic-b" }, signalSender = CallSignalSender { target, data ->
                assertEquals("synthetic-a", target); toA.addLast(data + ("sender_id" to "synthetic-b"))
            }, mediaFactory = { bMedia })
            aMedia.duringOffer = { aMedia.onLocalIceCandidate!!.invoke("candidate:a", "audio", 0) }
            bMedia.duringAnswer = { bMedia.onLocalIceCandidate!!.invoke("candidate:b", "audio", 0) }
            a.initiateCall("synthetic-b", "Synthetic B", isVideo = false)
            while (toB.isNotEmpty()) b.handleRemoteSignal(toB.removeFirst())
            assertEquals(CallSessionState.incoming, b.currentSession!!.state)
            b.acceptCall()
            while (toA.isNotEmpty()) a.handleRemoteSignal(toA.removeFirst())
            assertEquals(listOf("fake-offer-sdp" to "offer"), bMedia.remoteDescriptions)
            assertEquals(listOf("fake-answer-sdp" to "answer"), aMedia.remoteDescriptions)
            assertEquals("candidate:a", bMedia.addedCandidates.single().first)
            assertEquals("candidate:b", aMedia.addedCandidates.single().first)
            assertEquals(CallSessionState.connecting, a.currentSession!!.state)
            assertEquals(CallSessionState.connecting, b.currentSession!!.state)
            // A fake media callback verifies state plumbing only, not real RTC connectivity.
            aMedia.onConnectionState!!.invoke(CallTransportState.connected)
            bMedia.onConnectionState!!.invoke(CallTransportState.connected)
            assertEquals(CallSessionState.connected, a.currentSession!!.state)
            assertEquals(CallSessionState.connected, b.currentSession!!.state)
            a.endCall(); yield()
            while (toB.isNotEmpty()) b.handleRemoteSignal(toB.removeFirst())
            assertEquals(CallSessionState.ended, b.currentSession!!.state)
            assertTrue(aMedia.disposed); assertTrue(bMedia.disposed)
        } finally { scope.cancel() }
    }
    @Test fun answerPrecedesDeduplicatedBoundedEarlyCandidates() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val peer = LatestFakeMedia(); val sent = mutableListOf<Map<String, Any?>>()
            peer.duringAnswer = {
                repeat(200) { peer.onLocalIceCandidate!!.invoke("candidate:$it", "audio", 0) }
                peer.onLocalIceCandidate!!.invoke("candidate:0", "audio", 0)
            }
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, data -> sent.add(data) }, mediaFactory = { peer })
            service.handleRemoteSignal(mapOf("signal_type" to "offer", "call_id" to "in", "sender_id" to "peer", "sdp" to "offer"))
            service.acceptCall()
            assertEquals("answer", sent.first()["signal_type"])
            assertEquals(129, sent.size)
            assertEquals(128, sent.drop(1).map { it["candidate"] }.toSet().size)
            service.endCall(); yield()
            val count = sent.size
            peer.onLocalIceCandidate!!.invoke("candidate:late", "audio", 0)
            assertEquals(count, sent.size)
        } finally { scope.cancel() }
    }
    @Test fun queuedCandidatesAreDiscardedWhenAccountChangesDuringOfferCreation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var account: Any = Any(); val peer = LatestFakeMedia(); val sent = mutableListOf<Map<String, Any?>>()
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, data -> sent.add(data) }, mediaFactory = { peer },
                captureAccountScope = { account }, accountScopeValid = { it === account }, accountScopeGuard = { a, b -> if (a === account) { b(); true } else false })
            peer.duringOffer = {
                peer.onLocalIceCandidate!!.invoke("candidate:old", "audio", 0)
                account = Any(); service.detachForAccountTransition(account)
            }
            try { service.initiateCall("peer", "Peer", isVideo = false); fail("Must cancel") } catch (_: CancellationException) {}
            assertTrue(sent.isEmpty()); assertNull(service.currentSession)
        } finally { scope.cancel() }
    }
    @Test fun offerIsSentBeforeCandidatesEmittedDuringLocalDescription() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val peer = LatestFakeMedia()
            val sent = mutableListOf<Map<String, Any?>>()
            peer.duringOffer = { peer.onLocalIceCandidate!!.invoke("candidate:early", "audio", 0) }
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, data -> sent.add(data) }, mediaFactory = { peer })
            service.initiateCall("peer", "Peer", isVideo = false)
            assertEquals(listOf("offer", "candidate"), sent.map { it["signal_type"] })
        } finally { scope.cancel() }
    }
    @Test fun oldOutgoingPrepareCannotSendAfterSameAccountCallReplacement() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
            val oldPeer = LatestFakeMedia().apply { awaitPrepare = { started.complete(Unit); gate.await() } }
            val newPeer = LatestFakeMedia(); var count = 0
            val sent = mutableListOf<Map<String, Any?>>()
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, data -> sent.add(data) }, mediaFactory = { if (count++ == 0) oldPeer else newPeer })
            val old = async { runCatching { service.initiateCall("old", "Old", isVideo = true) } }
            started.await()
            service.endCall(); yield()
            service.initiateCall("new", "New", isVideo = true)
            val newId = service.currentSession!!.callId
            gate.complete(Unit); assertTrue(old.await().exceptionOrNull() is CancellationException)
            assertEquals(newId, service.currentSession!!.callId)
            assertEquals(CallSessionState.ringing, service.currentSession!!.state)
            assertTrue(oldPeer.disposed); assertFalse(newPeer.disposed)
            assertEquals(1, sent.count { it["signal_type"] == "offer" })
        } finally { scope.cancel() }
    }
    @Test fun oldAcceptRemoteDescriptionCannotAnswerReplacementCall() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
            val oldPeer = LatestFakeMedia().apply { awaitRemote = { started.complete(Unit); gate.await() } }
            val newPeer = LatestFakeMedia(); var count = 0; val sent = mutableListOf<Map<String, Any?>>()
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, data -> sent.add(data) }, mediaFactory = { if(count++ == 0) oldPeer else newPeer })
            service.handleRemoteSignal(mapOf("signal_type" to "offer", "call_id" to "incoming-old", "sender_id" to "old", "sdp" to "offer"))
            val accepting = async { runCatching { service.acceptCall() } }; started.await()
            service.declineCall()
            service.initiateCall("new", "New", isVideo = false)
            val newId = service.currentSession!!.callId
            gate.complete(Unit); assertTrue(accepting.await().exceptionOrNull() is CancellationException)
            assertEquals(newId, service.currentSession!!.callId); assertFalse(newPeer.disposed)
            assertTrue(sent.none { it["signal_type"] == "answer" })
        } finally { scope.cancel() }
    }
    @Test fun suspendedCameraSwitchCannotRestoreEndedOrReplacementSession() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
            val peer = LatestFakeMedia().apply { awaitSwitch = { started.complete(Unit); gate.await() } }
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, _ -> }, mediaFactory = { peer })
            service.initiateCall("old", "Old", isVideo = true)
            val switching = async { service.switchCamera() }; started.await()
            service.endCall(); yield()
            assertEquals(CallSessionState.ended, service.currentSession!!.state)
            gate.complete(Unit); switching.await()
            assertEquals(CallSessionState.ended, service.currentSession!!.state)
        } finally { scope.cancel() }
    }
    @Test fun deferredFailureCleanupOwnsOldPeerBeforeNewAccountStarts() = kotlinx.coroutines.test.runTest {
        var current: Any = Any(); val old = LatestFakeMedia(); val new = LatestFakeMedia(); var count = 0
        val service = CallSignalingService(this, { "me" }, signalSender = CallSignalSender { _, _ -> }, mediaFactory = { if (count++ == 0) old else new },
            captureAccountScope = { current }, accountScopeValid = { it === current }, accountScopeGuard = { e, b -> if (e === current) { b(); true } else false })
        service.initiateCall("old", "Old", isVideo = false)
        old.onIceFailed!!.invoke() // Cleanup is queued on the test scheduler.
        current = Any(); val transitionCleanup = service.detachForAccountTransition(current)
        service.initiateCall("new", "New", isVideo = false)
        testScheduler.runCurrent()
        transitionCleanup?.invoke()
        assertTrue(old.disposed); assertFalse(new.disposed)
        assertEquals("new", service.currentSession!!.remoteUserId)
        service.endCall(); testScheduler.runCurrent()
    }
    @Test fun suspendedInboundAnswerCannotReviveEndedCall() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
            val peer = LatestFakeMedia().apply { awaitRemote = { started.complete(Unit); gate.await() } }
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, _ -> }, mediaFactory = { peer })
            service.initiateCall("old", "Old", isVideo = false)
            val callId = service.currentSession!!.callId
            val answering = async { service.handleRemoteSignal(mapOf("signal_type" to "answer", "call_id" to callId, "sender_id" to "old", "sdp" to "answer")) }
            started.await(); service.endCall(); yield()
            gate.complete(Unit); answering.await()
            assertEquals(CallSessionState.ended, service.currentSession!!.state)
        } finally { scope.cancel() }
    }
    @Test fun failedPeerLateConnectedCallbackCannotReviveFailedCall() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val peer = LatestFakeMedia()
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, _ -> }, mediaFactory = { peer })
            service.initiateCall("old", "Old", isVideo = false)
            peer.onIceFailed!!.invoke()
            peer.onConnectionState!!.invoke(CallTransportState.connected)
            assertEquals(CallSessionState.failed, service.currentSession!!.state)
        } finally { scope.cancel() }
    }
    @Test fun initiatingAndRingingCannotBeReplacedBySecondIncomingOffer() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sent = mutableListOf<Map<String, Any?>>()
            val media = LatestFakeMedia()
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, data -> sent.add(data) }, mediaFactory = { media })
            service.initiateCall("peer", "Peer", isVideo = false)
            val call = service.currentSession!!
            assertTrue(service.hasActiveSession)
            service.handleRemoteSignal(mapOf("signal_type" to "offer", "call_id" to "other", "sender_id" to "other-peer", "sdp" to "new"))
            assertEquals(call.callId, service.currentSession!!.callId)
            assertEquals("busy", sent.last()["signal_type"])
            assertEquals("already_in_call", sent.last()["reason"])
            service.handleRemoteSignal(mapOf("signal_type" to "busy", "call_id" to call.callId, "sender_id" to "wrong-peer"))
            assertEquals(CallSessionState.ringing, service.currentSession!!.state)
            service.handleRemoteSignal(mapOf("signal_type" to "busy", "call_id" to call.callId, "sender_id" to "peer"))
            assertFalse(service.hasActiveSession)
            service.initiateCall("peer2", "Peer2", isVideo = true)
            assertEquals("video", sent.last()["call_type"])
        } finally { scope.cancel() }
    }
    @Test fun outgoingPrepareScopeSwitchSendsNoOfferOrNewAccountFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val expected=Any(); var current:Any=expected;val media=LatestFakeMedia();val sent=mutableListOf<Map<String,Any?>>()
            media.duringPrepare={current=Any()}
            val service=CallSignalingService(scope,{"me"},signalSender=CallSignalSender{_,data->sent.add(data)},mediaFactory={media},
                captureAccountScope={current},accountScopeValid={it === current},accountScopeGuard={e,b->if(e === current){b();true}else false})
            try { service.initiateCall("peer","Peer",isVideo=false);fail("Stale prepare must cancel") } catch (_:CancellationException) {}
            assertTrue(sent.isEmpty());assertTrue(media.disposed)
            assertNotEquals(CallSessionState.failed,service.currentSession?.state)
        } finally {scope.cancel()}
    }
    @Test fun incomingAcceptScopeSwitchSendsNoAnswer() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var current:Any=Any();val media=LatestFakeMedia();val sent=mutableListOf<Map<String,Any?>>()
            val service=CallSignalingService(scope,{"me"},signalSender=CallSignalSender{_,data->sent.add(data)},mediaFactory={media},
                captureAccountScope={current},accountScopeValid={it === current},accountScopeGuard={e,b->if(e === current){b();true}else false})
            service.handleRemoteSignal(mapOf("signal_type" to "offer","call_id" to "c","sender_id" to "peer","sdp" to "offer"))
            media.duringRemoteDescription={current=Any()}
            try {service.acceptCall();fail("Old accept must cancel")}catch(_:CancellationException){}
            assertTrue(sent.isEmpty());assertNotEquals(CallSessionState.connecting,service.currentSession?.state)
        } finally {scope.cancel()}
    }
    @Test fun suspendedRemoteAnswerSwitchCannotCommitConnectingState() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var valid=true;val media=LatestFakeMedia()
            val service=CallSignalingService(scope,{"me"},signalSender=CallSignalSender{_,_->},mediaFactory={media})
            service.initiateCall("peer","Peer",isVideo=false);val id=service.currentSession!!.callId
            media.duringRemoteDescription={valid=false}
            service.handleRemoteSignal(mapOf("signal_type" to "answer","call_id" to id,"sender_id" to "peer","sdp" to "answer"),
                validScope={valid},guardScope={b->if(valid){b();true}else false})
            assertEquals(CallSessionState.ringing,service.currentSession!!.state)
        } finally {scope.cancel()}
    }
    @Test fun detachedOldCleanupCannotDisposeNewAccountsPeer() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var current:Any=Any();val oldMedia=LatestFakeMedia();val newMedia=LatestFakeMedia();var calls=0
            val service=CallSignalingService(scope,{"me"},signalSender=CallSignalSender{_,_->},mediaFactory={if(calls++==0)oldMedia else newMedia},
                captureAccountScope={current},accountScopeValid={it === current},accountScopeGuard={e,b->if(e === current){b();true}else false})
            service.initiateCall("peer","Peer",isVideo=false)
            current=Any();val cleanup=service.detachForAccountTransition(current)!!
            assertNull(service.currentSession)
            service.initiateCall("new-peer","New",isVideo=false)
            val newId=service.currentSession!!.callId
            cleanup()
            assertTrue(oldMedia.disposed);assertFalse(newMedia.disposed);assertEquals(newId,service.currentSession!!.callId)
        }finally{scope.cancel()}
    }
    @Test fun queuedOldScopeOfferCannotCreateIncoming() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, _ -> }, mediaFactory = { LatestFakeMedia() })
            service.handleRemoteSignal(mapOf("signal_type" to "offer", "call_id" to "old", "sender_id" to "peer", "sdp" to "valid"),
                validScope = { false }, guardScope = { false })
            assertNull(service.currentSession)
        } finally { scope.cancel() }
    }
    @Test fun malformedOfferNeverCreatesIncomingSession() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, _ -> }, mediaFactory = { LatestFakeMedia() })
            for (sdp in listOf(null, "", " ", mapOf("x" to "bad"))) {
                service.handleRemoteSignal(mapOf("signal_type" to "offer", "call_id" to "c", "sender_id" to "peer", "sdp" to sdp))
                assertNull(service.currentSession)
            }
        } finally { scope.cancel() }
    }
    @Test fun repeatedOfferNestedSdpAndEndAliasesAreIdempotent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val media = LatestFakeMedia()
            val service = CallSignalingService(scope, { "me" }, signalSender = CallSignalSender { _, _ -> }, mediaFactory = { media })
            val offer = mapOf("signal_type" to "offer", "call_id" to "call", "sender_id" to "peer", "offer" to mapOf("sdp" to "nested-offer"), "call_type" to "video")
            service.handleRemoteSignal(offer)
            val original = service.currentSession
            service.handleRemoteSignal(offer)
            assertSame(original, service.currentSession)
            service.acceptCall()
            assertTrue(media.remoteDescriptions.contains("nested-offer" to "offer"))
            service.handleRemoteSignal(mapOf("signal_type" to "candidate", "call_id" to "call", "sender_id" to "peer", "candidate" to mapOf("candidate" to "ice", "sdpMid" to "0", "sdpMLineIndex" to "1")))
            assertTrue(media.addedCandidates.contains(Triple("ice", "0", 1)))
            service.handleRemoteSignal(mapOf("signal_type" to "end", "call_id" to "call", "sender_id" to "peer"))
            assertEquals(CallSessionState.ended, service.currentSession!!.state)
            service.handleRemoteSignal(offer)
            assertEquals(CallSessionState.ended, service.currentSession!!.state)
        } finally { scope.cancel() }
    }
}
