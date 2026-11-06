package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic recorder and clock only. No microphone, upload or real account. */
class VoicePauseControllerTest {
    private class Recorder : VoiceRecorderPort {
        var paused = false
        var stops = 0
        override suspend fun hasPermission() = true
        override suspend fun start(config: VoiceRecordConfig, path: String) { paused = false }
        override suspend fun pause() { check(!paused); paused = true }
        override suspend fun resume() { check(paused); paused = false }
        override suspend fun stop(): String? { stops++; return "synthetic.m4a" }
        override suspend fun currentAmplitudeDbfs() = -12.0
        override suspend fun dispose() {}
    }
    private class Fixture {
        var now = 0L
        val recorder = Recorder()
        var uploads = 0
        var sent = 0
        var duration = 0
        val deleted = mutableListOf<String>()
        val controller = VoiceNoteController("synthetic", recorder, object : VoiceStagingPort {
            override fun newStagingPath(epochMs: Long) = "synthetic.m4a"
            override suspend fun deleteQuietly(path: String) { deleted.add(path) }
        }, uploadAudio = { _, _, seconds ->
            uploads++; duration = seconds
            MessageAttachment(id = "a", type = "audio", name = "synthetic", size = "1 KB", url = "synthetic")
        }, sendAudioMessage = { sent++ }, now = { now })
    }
    @Test fun longPauseCannotTurnShortCaptureIntoSend() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 700
        f.controller.togglePause(); f.now = 100_000
        assertTrue(f.recorder.paused); assertEquals(700L, f.controller.elapsedMs)
        assertEquals(-60.0, f.controller.currentLevelDbfs(), 0.0)
        assertFalse(f.controller.stopAndSend()); assertEquals(0, f.uploads); assertEquals(0, f.sent)
        assertEquals(1, f.deleted.size); assertEquals(1, f.recorder.stops)
    }
    @Test fun resumeCountsOnlyActiveTimeAndPausedSendIsAllowed() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 600; f.controller.togglePause()
        f.now = 50_000; f.controller.togglePause(); f.now += 1500
        f.controller.togglePause(); f.now += 90_000
        assertTrue(f.controller.stopAndSend()); assertEquals(2, f.duration)
        assertEquals(1, f.sent); assertEquals(1, f.deleted.size)
    }
    @Test fun deletingPausedCaptureNeverUploadsAndResetsClock() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 3000; f.controller.togglePause()
        f.controller.cancel(); assertFalse(f.controller.isRecording); assertFalse(f.controller.isPaused)
        assertEquals(0L, f.controller.elapsedMs); assertEquals(0, f.uploads); assertEquals(0, f.sent)
        assertEquals(1, f.deleted.size)
    }
    @Test fun invalidatedBeforeSendAlwaysDeletesWithoutUpload() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 3000; f.controller.invalidate()
        assertTrue(runCatching { f.controller.stopAndSend() }.isFailure)
        assertEquals(0, f.uploads); assertEquals(0, f.sent); assertEquals(1, f.deleted.size)
    }

    @Test fun invalidatedControllerCannotStartDuringQueuedRebuild() = runBlocking {
        val f = Fixture(); f.controller.invalidate()
        assertTrue(f.controller.isInvalidated)
        assertTrue(runCatching { f.controller.start() }.isFailure)
        assertFalse(f.controller.isRecording); assertEquals(0, f.uploads); assertEquals(0, f.sent)
    }
    @Test fun startWhileAlreadyRecordingDoesNotCreateSecondCapture() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 500; f.controller.start()
        assertEquals(500L, f.controller.elapsedMs)
        f.controller.cancel(); assertEquals(1, f.recorder.stops)
    }

}
