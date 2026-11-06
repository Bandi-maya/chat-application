package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

/** Synthetic hardware and upload barriers only. Never opens microphone or a network connection. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionBarrierProofTest {
    private class Recorder : VoiceRecorderPort {
        var startGate: CompletableDeferred<Unit>? = null
        var starts = 0
        var stops = 0
        var disposed = false
        var failPause = false
        override suspend fun hasPermission() = true
        override suspend fun start(config: VoiceRecordConfig, path: String) { starts++; startGate?.await() }
        override suspend fun stop(): String? { stops++; return "synthetic.m4a" }
        override suspend fun pause() { check(!failPause) { "Synthetic pause failure" } }
        override suspend fun resume() {}
        override suspend fun currentAmplitudeDbfs() = -20.0
        override suspend fun dispose() { disposed = true }
    }
    private class Harness {
        val recorder = Recorder()
        var now = 0L
        var uploads = 0
        var sends = 0
        var uploadGate: CompletableDeferred<Unit>? = null
        val deleted = mutableListOf<String>()
        val controller = VoiceNoteController(
            conversationId = "synthetic-chat", recorder = recorder,
            staging = object : VoiceStagingPort {
                override fun newStagingPath(epochMs: Long) = "synthetic.m4a"
                override suspend fun deleteQuietly(path: String) { deleted += path }
            }, uploadAudio = { _, _, _ ->
                uploads++; uploadGate?.await()
                MessageAttachment("synthetic-attachment", "audio", "test", "1 KB", "offline")
            }, sendAudioMessage = { sends++ }, now = { now },
        )
    }
    @Test fun releaseDuringNativeStartCannotLeaveUnlockedOrphanCapture() = runTest {
        val h = Harness(); val gate = CompletableDeferred<Unit>(); h.recorder.startGate = gate
        val session = VoiceSessionController(h.controller, backgroundScope)
        session.beginVoice(locked = false); runCurrent()
        assertTrue(session.state.busy); session.onRelease(); gate.complete(Unit); runCurrent()
        assertFalse(session.state.recording); assertFalse(session.state.busy)
        assertEquals(1, h.recorder.stops); assertEquals(0, h.sends); assertEquals(1, h.deleted.size)
    }
    @Test fun cancelDuringNativeStartWinsOverReleaseAndCannotUpload() = runTest {
        val h = Harness(); val gate = CompletableDeferred<Unit>(); h.recorder.startGate = gate
        val session = VoiceSessionController(h.controller, backgroundScope)
        session.beginVoice(locked = false); runCurrent()
        session.onDrag(-71f, 0f); session.onRelease(); gate.complete(Unit); runCurrent()
        assertFalse(session.state.recording); assertEquals(1, h.recorder.stops)
        assertEquals(0, h.uploads); assertEquals(0, h.sends); assertEquals(1, h.deleted.size)
    }
    @Test fun lockDuringNativeStartSurvivesReleaseUntilExplicitDelete() = runTest {
        val h = Harness(); val gate = CompletableDeferred<Unit>(); h.recorder.startGate = gate
        val session = VoiceSessionController(h.controller, backgroundScope)
        session.beginVoice(locked = false); runCurrent()
        session.onDrag(0f, -56f); session.onRelease(); gate.complete(Unit); runCurrent()
        assertTrue(session.state.recording); assertTrue(session.state.locked); assertEquals(0, h.sends)
        session.cancelVoice(); runCurrent()
        assertFalse(session.state.recording); assertEquals(1, h.deleted.size); assertEquals(0, h.uploads)
    }
    @Test fun repeatedBeginDuringStartCannotAcquireSecondRecorder() = runTest {
        val h = Harness(); val gate = CompletableDeferred<Unit>(); h.recorder.startGate = gate
        val session = VoiceSessionController(h.controller, backgroundScope)
        session.beginVoice(locked = true); runCurrent(); session.beginVoice(locked = false)
        assertEquals(1, h.recorder.starts)
        gate.complete(Unit); runCurrent(); assertTrue(session.state.locked)
        session.cancelVoice(); runCurrent(); assertEquals(1, h.recorder.stops)
    }
    @Test fun invalidationDuringNativeStartStopsAndDeletesBeforeReportingRecording() = runTest {
        val h = Harness(); val gate = CompletableDeferred<Unit>(); h.recorder.startGate = gate
        val errors = mutableListOf<String>()
        val session = VoiceSessionController(h.controller, backgroundScope, showError = { errors += it })
        session.beginVoice(locked = true); runCurrent(); h.controller.invalidate()
        gate.complete(Unit); runCurrent()
        assertFalse(session.state.recording); assertFalse(h.controller.isRecording)
        assertEquals(1, h.recorder.stops); assertEquals(1, h.deleted.size)
        assertEquals(0, h.uploads); assertEquals(0, h.sends); assertEquals(1, errors.size)
    }
    @Test fun invalidationWhileUploadAwaitedCannotSendReturnedAttachment() = runTest {
        val h = Harness(); val gate = CompletableDeferred<Unit>(); h.uploadGate = gate
        h.controller.start(); h.now = 2000
        val result = async { runCatching { h.controller.stopAndSend() } }; runCurrent()
        assertEquals(1, h.uploads); h.controller.invalidate(); gate.complete(Unit); runCurrent()
        assertTrue(result.await().isFailure); assertEquals(0, h.sends)
        assertFalse(h.controller.isRecording); assertEquals(1, h.deleted.size)
    }
    @Test fun disposeWhileUploadAwaitedWaitsThenDeletesWithoutSend() = runTest {
        val h = Harness(); val gate = CompletableDeferred<Unit>(); h.uploadGate = gate
        h.controller.start(); h.now = 2000
        val sending = async { runCatching { h.controller.stopAndSend() } }; runCurrent()
        val disposing = async { h.controller.dispose() }; runCurrent()
        assertFalse(disposing.isCompleted); gate.complete(Unit); runCurrent()
        assertTrue(sending.await().isFailure); disposing.await()
        assertEquals(0, h.sends); assertEquals(1, h.deleted.size); assertTrue(h.recorder.disposed)
    }
    @Test fun pauseFailureCancelsHardwareAndNeverShowsPausedOrSends() = runTest {
        val h = Harness(); h.recorder.failPause = true
        val errors = mutableListOf<String>()
        val session = VoiceSessionController(h.controller, backgroundScope, showError = { errors += it })
        session.beginVoice(locked = true); runCurrent(); session.togglePause(); runCurrent()
        assertFalse(session.state.recording); assertFalse(session.state.paused); assertFalse(session.state.busy)
        assertEquals(1, h.recorder.stops); assertEquals(1, h.deleted.size)
        assertEquals(0, h.uploads); assertEquals(0, h.sends); assertEquals(1, errors.size)
    }
}
