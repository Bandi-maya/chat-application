package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic recorder and staging only; no microphone, account or network. */
class VoiceRetryDraftTest {
    private class Fixture(retain: Boolean, var failUpload: Boolean = true) {
        var now = 0L
        var sent = 0
        var invalidateOnUpload = false
        val deleted = mutableListOf<String>()
        lateinit var controller: VoiceNoteController
        init {
            controller = VoiceNoteController("synthetic", object : VoiceRecorderPort {
                override suspend fun hasPermission() = true
                override suspend fun start(config: VoiceRecordConfig, path: String) {}
                override suspend fun stop() = "recorded.m4a"
                override suspend fun currentAmplitudeDbfs() = -20.0
                override suspend fun dispose() {}
            }, object : VoiceStagingPort {
                override fun newStagingPath(epochMs: Long) = "staged.m4a"
                override suspend fun deleteQuietly(path: String) { deleted += path }
            }, uploadAudio = { _, _, _ ->
                if (invalidateOnUpload) controller.invalidate()
                if (failUpload) throw java.io.IOException("offline")
                MessageAttachment("a", "audio", "t", "1 KB")
            }, sendAudioMessage = { sent++ }, now = { now }, retainOnFailure = retain)
        }
    }

    @Test fun defaultOffDeletesFileOnFailure() = runBlocking {
        val f = Fixture(retain = false); f.controller.start(); f.now = 2000
        runCatching { f.controller.stopAndSend() }
        assertNull(f.controller.retryDraft)
        assertTrue("recorded.m4a" in f.deleted)
    }

    @Test fun failedSendKeepsDraftThenRetrySucceedsAndDeletes() = runBlocking {
        val f = Fixture(retain = true); f.controller.start(); f.now = 2000
        assertTrue(runCatching { f.controller.stopAndSend() }.isFailure)
        assertNotNull(f.controller.retryDraft)
        assertFalse("recorded.m4a" in f.deleted)
        f.failUpload = false
        assertTrue(f.controller.retryDraft())
        assertEquals(1, f.sent); assertNull(f.controller.retryDraft)
        assertTrue("recorded.m4a" in f.deleted)
    }

    @Test fun accountChangeDuringUploadDoesNotKeepDraft() = runBlocking {
        val f = Fixture(retain = true); f.invalidateOnUpload = true
        f.controller.start(); f.now = 2000
        runCatching { f.controller.stopAndSend() }
        assertNull(f.controller.retryDraft)
        assertTrue("recorded.m4a" in f.deleted)
    }

    @Test fun discardAndDisposeRemoveDraft() = runBlocking {
        val f = Fixture(retain = true); f.controller.start(); f.now = 2000
        runCatching { f.controller.stopAndSend() }
        f.controller.discardDraft()
        assertNull(f.controller.retryDraft); assertTrue("recorded.m4a" in f.deleted)
        val g = Fixture(retain = true); g.controller.start(); g.now = 2000
        runCatching { g.controller.stopAndSend() }
        g.controller.dispose()
        assertNull(g.controller.retryDraft); assertTrue("recorded.m4a" in g.deleted)
    }
}
