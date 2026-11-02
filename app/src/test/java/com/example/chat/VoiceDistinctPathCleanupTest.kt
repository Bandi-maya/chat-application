package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic files/recorder only; no microphone, account or network. */
class VoiceDistinctPathCleanupTest {
    private class Fixture(val stopPath: String?) {
        var now = 0L
        var sent = 0
        val deleted = mutableListOf<String>()
        val controller = VoiceNoteController("synthetic", object : VoiceRecorderPort {
            override suspend fun hasPermission() = true
            override suspend fun start(config: VoiceRecordConfig, path: String) {}
            override suspend fun stop() = stopPath
            override suspend fun currentAmplitudeDbfs() = -20.0
            override suspend fun dispose() {}
        }, object : VoiceStagingPort {
            override fun newStagingPath(epochMs: Long) = "staged.m4a"
            override suspend fun deleteQuietly(path: String) { deleted += path }
        }, uploadAudio = { _, _, _ -> MessageAttachment("a", "audio", "test", "1 KB") },
            sendAudioMessage = { sent++ }, now = { now })
    }
    @Test fun sendDeletesDistinctRecordedAndStagedPaths() = runBlocking {
        val f = Fixture("recorded.m4a"); f.controller.start(); f.now = 2000
        assertTrue(f.controller.stopAndSend())
        assertEquals(setOf("recorded.m4a", "staged.m4a"), f.deleted.toSet())
        assertEquals(2, f.deleted.size); assertEquals(1, f.sent)
        assertFalse(f.controller.isBusy); assertFalse(f.controller.isRecording)
    }
    @Test fun cancelDeletesDistinctRecordedAndStagedPathsWithoutSend() = runBlocking {
        val f = Fixture("recorded.m4a"); f.controller.start(); f.controller.cancel()
        assertEquals(setOf("recorded.m4a", "staged.m4a"), f.deleted.toSet())
        assertEquals(2, f.deleted.size); assertEquals(0, f.sent)
        assertFalse(f.controller.isBusy); assertFalse(f.controller.isRecording)
    }
    @Test fun samePathAndNullStopPathAreDeletedOnlyOnce() = runBlocking {
        for (stopPath in listOf("staged.m4a", null)) {
            val f = Fixture(stopPath); f.controller.start(); f.now = 2000
            assertTrue(f.controller.stopAndSend())
            assertEquals(listOf("staged.m4a"), f.deleted)
        }
    }
}
