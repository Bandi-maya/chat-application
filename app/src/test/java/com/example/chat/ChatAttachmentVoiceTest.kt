package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.ChatAttachmentActionController
import com.example.chat.ui.chats.detail.ContactReadAccess
import com.example.chat.ui.chats.detail.ContactsPort
import com.example.chat.ui.chats.detail.LocationPermissionState
import com.example.chat.ui.chats.detail.LocationPort
import com.example.chat.ui.chats.detail.PickedContactRef
import com.example.chat.ui.chats.detail.PickedFile
import com.example.chat.ui.chats.detail.PollDraft
import com.example.chat.ui.chats.detail.PollState
import com.example.chat.ui.chats.detail.AttachmentBackendPort
import com.example.chat.ui.chats.detail.ContactDetails
import com.example.chat.ui.chats.detail.VoiceGesturePolicy
import com.example.chat.ui.chats.detail.VoiceLevelMeter
import com.example.chat.ui.chats.detail.VoiceLevelSampler
import com.example.chat.ui.chats.detail.VoiceNoteController
import com.example.chat.ui.chats.detail.VoiceRecordConfig
import com.example.chat.ui.chats.detail.VoiceRecorderPort
import com.example.chat.ui.chats.detail.VoiceStagingPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fakes only - no live sends, recordings, or account writes. */
class ChatAttachmentVoiceTest {

    // ------------------------------------------------------------ voice core

    private class FakeRecorder : VoiceRecorderPort {
        var permission = true
        var amplitude = -42.0
        var started = 0
        var stopped = 0
        var disposed = false
        var lastConfig: VoiceRecordConfig? = null
        var lastPath: String? = null
        override suspend fun hasPermission(): Boolean = permission
        override suspend fun start(config: VoiceRecordConfig, path: String) {
            started++; lastConfig = config; lastPath = path
        }
        override suspend fun stop(): String? { stopped++; return lastPath }
        override suspend fun currentAmplitudeDbfs(): Double = amplitude
        override suspend fun dispose() { disposed = true }
    }

    private class FakeStaging : VoiceStagingPort {
        val deleted = mutableListOf<String>()
        override fun newStagingPath(epochMs: Long): String = "/tmp/chaty_voice_$epochMs.m4a"
        override suspend fun deleteQuietly(path: String) { deleted.add(path) }
    }

    private class Harness(
        var clock: Long = 1_000L,
        val uploaded: MutableList<Triple<String, String, Int>> = mutableListOf(),
        val sent: MutableList<MessageAttachment> = mutableListOf(),
    ) {
        val recorder = FakeRecorder()
        val staging = FakeStaging()
        val controller = VoiceNoteController(
            conversationId = "c1",
            recorder = recorder,
            staging = staging,
            uploadAudio = { path, name, seconds ->
                uploaded.add(Triple(path, name, seconds))
                MessageAttachment(id = "a1", type = "audio", name = name, size = "1 KB", url = path, durationSeconds = seconds)
            },
            sendAudioMessage = { sent.add(it) },
            now = { clock },
        )
    }

    @Test fun startRecordsWithDartConfig() = runBlocking {
        val h = Harness()
        h.controller.start()
        assertTrue(h.controller.isRecording)
        assertEquals(1, h.recorder.started)
        val config = h.recorder.lastConfig!!
        assertEquals(96000, config.bitRate)
        assertEquals(44100, config.sampleRate)
        assertEquals(1, config.numChannels)
        assertTrue(config.autoGain && config.echoCancel && config.noiseSuppress)
        assertEquals("/tmp/chaty_voice_1000.m4a", h.recorder.lastPath)
    }

    @Test fun startWithoutPermissionThrowsBeforeStaging() = runBlocking {
        val h = Harness()
        h.recorder.permission = false
        val error = runCatching { h.controller.start() }.exceptionOrNull()
        assertEquals("Microphone permission is required.", error?.message)
        assertFalse(h.controller.isRecording)
        // Dart checks permission BEFORE creating the staging path: no cleanup.
        assertTrue(h.staging.deleted.isEmpty())
        assertEquals(0, h.recorder.started)
    }

    @Test fun stopAndSendUnderOneSecondSendsNothing() = runBlocking {
        val h = Harness()
        h.controller.start()
        h.clock += 400 // 0.4s
        assertFalse(h.controller.stopAndSend())
        assertTrue(h.uploaded.isEmpty() && h.sent.isEmpty())
        assertFalse(h.controller.isRecording)
        // finally still cleans the recorded file
        assertEquals(1, h.staging.deleted.size)
    }

    @Test fun stopAndSendUploadsThenSends() = runBlocking {
        val h = Harness()
        h.controller.start()
        h.clock += 2500
        assertTrue(h.controller.stopAndSend())
        assertEquals(1, h.uploaded.size)
        val (path, name, seconds) = h.uploaded[0]
        assertEquals("/tmp/chaty_voice_1000.m4a", path)
        assertEquals("voice_note_3500.m4a", name)
        assertEquals(2, seconds)
        assertEquals(1, h.sent.size)
        assertEquals("audio", h.sent[0].type)
        assertFalse(h.controller.isRecording)
        assertEquals(1, h.staging.deleted.size)
    }

    @Test fun cancelStopsAndDeletes() = runBlocking {
        val h = Harness()
        h.controller.start()
        h.controller.cancel()
        assertFalse(h.controller.isRecording)
        assertEquals(listOf("/tmp/chaty_voice_1000.m4a"), h.staging.deleted)
        assertTrue(h.sent.isEmpty())
    }

    @Test fun levelFloorsAtMinus60WhenIdleOrProbeFails() = runBlocking {
        val h = Harness()
        assertEquals(-60.0, h.controller.currentLevelDbfs(), 0.0)
        h.controller.start()
        assertEquals(-42.0, h.controller.currentLevelDbfs(), 0.0)
    }

    @Test fun disposeCancelsActiveRecording() = runBlocking {
        val h = Harness()
        h.controller.start()
        h.controller.dispose()
        assertTrue(h.recorder.disposed)
        assertFalse(h.controller.isRecording)
        val error = runCatching { h.controller.start() }.exceptionOrNull()
        assertEquals("Voice-note service has been disposed.", error?.message)
    }

    // ------------------------------------------------------ gesture + levels

    @Test fun dragThresholdsMatchDart() {
        // dx < -70 cancels even mid-lock-drag
        assertEquals(
            VoiceGesturePolicy.DragOutcome.cancel,
            VoiceGesturePolicy.onDrag(-71f, 0f, recording = true, locked = false),
        )
        assertEquals(
            VoiceGesturePolicy.DragOutcome.lock,
            VoiceGesturePolicy.onDrag(0f, -56f, recording = true, locked = false),
        )
        // already locked: no repeated outcome
        assertEquals(
            VoiceGesturePolicy.DragOutcome.none,
            VoiceGesturePolicy.onDrag(0f, -56f, recording = true, locked = true),
        )
        // not recording: nothing
        assertEquals(
            VoiceGesturePolicy.DragOutcome.none,
            VoiceGesturePolicy.onDrag(-100f, -100f, recording = false, locked = false),
        )
        assertTrue(VoiceGesturePolicy.onReleaseSends(recording = true, locked = false))
        assertFalse(VoiceGesturePolicy.onReleaseSends(recording = true, locked = true))
    }

    @Test fun levelMeterMathMatchesDart() {
        assertEquals(VoiceLevelMeter.MIN_STUB, VoiceLevelMeter.normalize(-60.0), 0.0)
        assertEquals(1.0, VoiceLevelMeter.normalize(0.0), 0.0)
        assertEquals(0.5, VoiceLevelMeter.normalize(-30.0), 1e-9)
        assertEquals(VoiceLevelMeter.MIN_STUB, VoiceLevelMeter.normalize(-59.0), 1e-9)
    }

    @Test fun samplerShiftsLeftAndClampsStub() = runBlocking {
        val sampler = VoiceLevelSampler { -30.0 }
        assertEquals(VoiceLevelMeter.BAR_COUNT, sampler.levels().size)
        sampler.pollOnce()
        val levels = sampler.levels()
        assertEquals(0.0, levels[0], 0.0)
        assertEquals(0.5, levels[VoiceLevelMeter.BAR_COUNT - 1], 1e-9)
        sampler.reset()
        assertTrue(sampler.levels().all { it == 0.0 })
    }

    // ------------------------------------------------------- attachment flow

    private open class FakeBackend : AttachmentBackendPort {
        val sends = mutableListOf<List<Any?>>()
        val structured = mutableListOf<Triple<String, String, Map<String, Any?>>>()
        val uploads = mutableListOf<Pair<String, String>>()
        var pollsCreated = mutableListOf<PollDraft>()
        var reloaded = 0
        var pollRow: Map<String, Any?>? = null
        var failOn: String? = null
        override suspend fun upload(type: String, file: PickedFile, durationSeconds: Int): MessageAttachment {
            if (failOn == "upload") throw IllegalStateException("boom")
            uploads.add(type to file.name)
            return MessageAttachment(id = "att", type = type, name = file.name, size = "1 KB", url = "p", durationSeconds = durationSeconds)
        }
        override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) {
            if (failOn == "send") throw IllegalStateException("boom")
            sends.add(listOf(text, type, attachment?.name, extraMetadata))
        }
        override suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>) {
            structured.add(Triple(type, body, metadata))
        }
        override suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean) {
            pollsCreated.add(PollDraft(question, options, allowMultiple))
        }
        override suspend fun getPoll(messageId: String): Map<String, Any?>? = pollRow
        override suspend fun votePoll(messageId: String, optionId: String) {}
        override suspend fun reloadConversation() { reloaded++ }
    }

    private fun newController(backend: FakeBackend, toasts: MutableList<String>, imageLimit: Int = 30) =
        ChatAttachmentActionController(
            conversationId = "c1",
            backend = backend,
            toast = { toasts.add(it) },
            imageShareLimit = { imageLimit },
            now = { 7_000L },
        )

    @Test fun documentShareSendsEachAndToastsCount() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        c.shareMedia(
            "document",
            pickFiles = { _, _ -> listOf(PickedFile("a.pdf"), PickedFile("b.pdf")) },
            confirmViewOnce = { false },
        )
        assertEquals(2, backend.uploads.size)
        assertEquals(2, backend.sends.count { it[1] == MessageType.document })
        assertEquals(listOf("2 documents sent."), toasts)
    }

    @Test fun singleImageAsksViewOnceAndTagsMetadata() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts, imageLimit = 1)
        c.shareMedia(
            "image",
            pickFiles = { multiple, _ -> assertFalse(multiple); listOf(PickedFile("p.jpg")) },
            confirmViewOnce = { type -> assertEquals("image", type); true },
        )
        assertEquals(mapOf("view_once" to true), backend.sends[0][3])
    }

    @Test fun multiImageHonorsShareLimitAndToasts() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts, imageLimit = 5)
        c.shareMedia(
            "image",
            pickFiles = { multiple, maxFiles ->
                assertTrue(multiple); assertEquals(5, maxFiles)
                listOf(PickedFile("1.jpg"), PickedFile("2.jpg"))
            },
            confirmViewOnce = { error("view-once must not be asked for multi sends") },
        )
        assertEquals(2, backend.sends.size)
        assertEquals(listOf("2 files sent."), toasts)
    }

    @Test fun shareFailureToastsDartString() = runBlocking {
        val backend = FakeBackend().apply { failOn = "upload" }
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        c.shareMedia("image", pickFiles = { _, _ -> listOf(PickedFile("p.jpg")) }, confirmViewOnce = { false })
        assertEquals(listOf("Unable to send image: boom"), toasts)
    }

    @Test fun locationPermissionLadderStrings() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        val disabled = object : LocationPort {
            override suspend fun isLocationServiceEnabled() = false
            override suspend fun checkPermission() = LocationPermissionState.granted
            override suspend fun requestPermission() = LocationPermissionState.granted
            override suspend fun getCurrentPosition() = Triple(0.0, 0.0, 0.0)
        }
        c.shareLocation(disabled)
        assertEquals(
            listOf("Unable to share location: Location services are disabled. Enable GPS and try again."),
            toasts,
        )
    }

    @Test fun locationSendsStructuredPayload() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        val ok = object : LocationPort {
            override suspend fun isLocationServiceEnabled() = true
            override suspend fun checkPermission() = LocationPermissionState.granted
            override suspend fun requestPermission() = LocationPermissionState.granted
            override suspend fun getCurrentPosition() = Triple(12.5, 77.5, 4.0)
        }
        c.shareLocation(ok)
        val (type, body, metadata) = backend.structured[0]
        assertEquals("location", type)
        assertEquals("📍 Location\nhttps://maps.google.com/?q=12.5,77.5", body)
        assertEquals(12.5, metadata["latitude"])
        assertEquals(4.0, metadata["accuracy_meters"])
        assertEquals(1, backend.reloaded)
        assertEquals(listOf("Location sent."), toasts)
    }

    @Test fun contactBodyCapsFivePhonesThreeEmails() {
        val body = ChatAttachmentActionController.buildContactBody(
            "Asha",
            (1..7).map { "900000000$it" },
            listOf("a@x.io", "b@x.io", "c@x.io", "d@x.io"),
        )
        val lines = body.split("\n")
        assertEquals("👤 Asha", lines[0])
        assertEquals(6, lines.count { it.startsWith("📞") } + 1) // 5 phones + header
        assertEquals(3, lines.count { it.startsWith("✉️") })
    }

    @Test fun shareContactFallsBackToContactName() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        val contacts = object : ContactsPort {
            override suspend fun requestReadAccess() = ContactReadAccess.denied
            override suspend fun showPicker(canRead: Boolean): PickedContactRef? {
                assertFalse(canRead)
                return PickedContactRef(id = null, displayName = "  ")
            }
            override suspend fun fetchDetails(id: String): ContactDetails? = null
        }
        c.shareContact(contacts)
        assertEquals("👤 Contact", backend.structured[0].second)
        assertEquals("Contact", backend.structured[0].third["contact_name"])
    }

    @Test fun pollDraftValidationMatchesDialog() {
        assertNull(PollDraft.normalized("  ", listOf("a", "b"), false))
        assertNull(PollDraft.normalized("Q", listOf("a", " "), false))
        val ok = PollDraft.normalized(" Lunch? ", listOf(" A ", "B", ""), true)!!
        assertEquals("Lunch?", ok.question)
        assertEquals(listOf("A", "B"), ok.options)
        assertTrue(ok.allowMultiple)
        assertEquals(12, PollDraft.MAX_OPTIONS)
        assertEquals(240, PollDraft.QUESTION_MAX_LENGTH)
        assertEquals(120, PollDraft.OPTION_MAX_LENGTH)
    }

    @Test fun pollStateParsesAndFormatsFooter() {
        val state = PollState.from(
            mapOf(
                "question" to "Lunch?",
                "options" to listOf(
                    mapOf("id" to "o1", "label" to "A", "votes" to 3, "voted_by_me" to true),
                    mapOf("id" to "o2", "label" to "B", "votes" to 1),
                ),
                "total_votes" to 4,
                "allow_multiple" to true,
            ),
        )
        assertEquals("Lunch?", state.question)
        assertEquals(0.75, state.options[0].progress(state.totalVotes), 1e-9)
        assertEquals("4 total votes • multiple answers allowed", state.footerText)
        val single = state.copy(totalVotes = 1, allowMultiple = false)
        assertEquals("1 total vote", single.footerText)
    }

    @Test fun missingPollRowIsUnavailableError() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        val error = runCatching { c.loadPoll("m1") }.exceptionOrNull()
        assertEquals("Poll data is unavailable.", error?.message)
    }

    @Test fun voteFailureToastsAndKeepsState() = runBlocking {
        val backend = object : FakeBackend() {
            override suspend fun votePoll(messageId: String, optionId: String) {
                throw IllegalStateException("nope")
            }
        }
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        assertNull(c.votePoll("m1", "o1"))
        assertEquals(listOf("Unable to vote: nope"), toasts)
    }

    @Test fun voiceNoteSendFlowUsesAudioType() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        c.sendRecordedVoiceNote("/tmp/v.m4a", 7)
        assertEquals("audio", backend.uploads[0].first)
        assertEquals("voice_note_7000.m4a", backend.uploads[0].second)
        assertEquals(MessageType.audio, backend.sends[0][1])
        assertEquals(listOf("Voice note sent."), toasts)
    }

    @Test fun cameraPhotoCarriesCaptionAndEffect() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        var sentCalled = false
        val c = newController(backend, toasts)
        c.sendCapturedPhoto("/cache/cap.jpg", "hi", "vivid", 0.8) { sentCalled = true }
        assertEquals("hi", backend.sends[0][0])
        assertEquals(MessageType.image, backend.sends[0][1])
        assertEquals(mapOf("effect" to "vivid", "effect_intensity" to "0.8"), backend.sends[0][3])
        assertTrue(sentCalled)
    }

    @Test fun demoPayloadsMatchDart() = runBlocking {
        val backend = FakeBackend()
        val toasts = mutableListOf<String>()
        val c = newController(backend, toasts)
        c.sendDemoTask()
        assertEquals(
            "Review Dynamic Island animations & specular shaders",
            backend.sends[0][0],
        )
        assertEquals(MessageType.taskCard, backend.sends[0][1])
        assertEquals(
            mapOf("task_title" to "Validate Dynamic Island animations", "status" to "todo", "priority" to "high"),
            backend.sends[0][3],
        )
        assertEquals(listOf("📋 Demo task card sent."), toasts)
        assertTrue(ChatAttachmentActionController.isPollMessage("[POLL] lunch?"))
        assertFalse(ChatAttachmentActionController.isPollMessage("hello"))
        assertEquals(MessageType.document, ChatAttachmentActionController.messageTypeFor("zip"))
    }
}
