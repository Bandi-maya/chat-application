package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic recorder, staging and samples only; no microphone, account, network or MediaMuxer. */
class VoiceSegmentTest {
    private class Src(override val format: AudioTrackFormat, private val times: List<Long>) : AudioSegmentSource {
        override fun samples() = times.asSequence().map { AudioSample(byteArrayOf(1), it, 0) }
    }
    private class Sink : AudioSegmentSink {
        val written = mutableListOf<Long>(); var started = false; var finished = false; var aborted = false
        override fun start(format: AudioTrackFormat) { started = true }
        override fun write(sample: AudioSample) { written += sample.timeUs }
        override fun finish() { finished = true }
        override fun abort() { aborted = true }
    }
    private val fmt = AudioTrackFormat("audio/mp4a-latm", 44100, 1)
    private val frame = AacSegmentJoiner.frameUs(44100)

    @Test fun shiftsLaterSegmentsByPreviousEnd() {
        val sink = Sink()
        AacSegmentJoiner.join(listOf(Src(fmt, listOf(0, 23219, 46438)), Src(fmt, listOf(0, 23219))), sink)
        val offset = 46438 + frame
        assertEquals(listOf(0L, 23219L, 46438L, offset, offset + 23219), sink.written)
        assertTrue(sink.finished)
        assertTrue(sink.written.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun rejectsDifferentFormatsAndAborts() {
        val sink = Sink()
        val other = AudioTrackFormat("audio/mp4a-latm", 48000, 1)
        assertTrue(runCatching { AacSegmentJoiner.join(listOf(Src(fmt, listOf(0)), Src(other, listOf(0))), sink) }.isFailure)
        assertFalse(sink.started)
    }

    @Test fun rejectsDifferentProfileOrCsd0() {
        val base = AudioTrackFormat("audio/mp4a-latm", 44100, 1, profile = 2, csd0 = byteArrayOf(18, 8))
        val otherProfile = AudioTrackFormat("audio/mp4a-latm", 44100, 1, profile = 5, csd0 = byteArrayOf(18, 8))
        val otherCsd = AudioTrackFormat("audio/mp4a-latm", 44100, 1, profile = 2, csd0 = byteArrayOf(18, 16))
        val sameCopy = AudioTrackFormat("audio/mp4a-latm", 44100, 1, profile = 2, csd0 = byteArrayOf(18, 8))
        assertFalse(base.compatibleWith(otherProfile))
        assertFalse(base.compatibleWith(otherCsd))
        assertTrue(base.compatibleWith(sameCopy))
        assertFalse(base.compatibleWith(AudioTrackFormat("audio/mp4a-latm", 44100, 1)))
        val sink = Sink()
        assertTrue(runCatching { AacSegmentJoiner.join(listOf(Src(base, listOf(0)), Src(otherCsd, listOf(0))), sink) }.isFailure)
        assertFalse(sink.started)
    }

    @Test fun emptyOrDisorderedSegmentFailsAndAbortsSink() {
        val a = Sink()
        assertTrue(runCatching { AacSegmentJoiner.join(listOf(Src(fmt, listOf(0)), Src(fmt, emptyList())), a) }.isFailure)
        assertTrue(a.aborted)
        val b = Sink()
        assertTrue(runCatching { AacSegmentJoiner.join(listOf(Src(fmt, listOf(100, 50))), b) }.isFailure)
        assertTrue(b.aborted)
    }

    private class Fixture(var joinFails: Boolean = false, val configs: ArrayDeque<VoiceRecordConfig> = ArrayDeque()) {
        val startedWith = mutableListOf<VoiceRecordConfig>()
        var now = 0L; var sent = 0
        val deleted = mutableListOf<String>(); var stops = 0; var starts = 0
        var joinedParts: List<String>? = null
        val controller = VoiceNoteController("synthetic", object : VoiceRecorderPort {
            override suspend fun hasPermission() = true
            override suspend fun start(config: VoiceRecordConfig, path: String) { starts++; startedWith += config }
            override suspend fun stop(): String? { stops++; return null }
            override suspend fun currentAmplitudeDbfs() = -20.0
            override suspend fun dispose() {}
        }, object : VoiceStagingPort {
            private var n = 0
            override fun newStagingPath(epochMs: Long) = "seg${n++}_$epochMs.m4a"
            override suspend fun deleteQuietly(path: String) { deleted += path }
        }, uploadAudio = { _, _, _ -> MessageAttachment("a", "audio", "t", "1 KB") },
            sendAudioMessage = { sent++ }, now = { now },
            recordConfig = { configs.removeFirstOrNull() ?: VoiceRecordConfig() },
            joinSegments = { parts, _ -> joinedParts = parts; if (joinFails) throw AacJoinException("no") })
    }

    @Test fun singleSegmentReviewNeedsNoJoinAndSendDeletesFile() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 2000
        val clip = f.controller.pauseForReview()!!
        assertNull(f.joinedParts); assertFalse(clip.joinFailed)
        assertTrue(f.controller.sendClip(clip)); assertEquals(1, f.sent)
        assertTrue(f.deleted.any { it == clip.path })
    }

    @Test fun pauseResumePauseJoinsAllPartsInOrder() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 2000
        val first = f.controller.pauseForReview()!!
        f.controller.resumeSegment(); f.now = 5000
        val second = f.controller.pauseForReview()!!
        assertEquals(2, f.joinedParts!!.size); assertEquals(first.path, f.joinedParts!![0])
        assertNotEquals(first.path, second.path); assertFalse(second.joinFailed)
        assertEquals(5, second.durationSeconds)
    }

    @Test fun joinFailureKeepsAllPartsFlagsClipAndBlocksSend() = runBlocking {
        val f = Fixture(joinFails = true); f.controller.start(); f.now = 2000
        val first = f.controller.pauseForReview()!!
        f.controller.resumeSegment(); f.now = 4000
        val clip = f.controller.pauseForReview()!!
        assertTrue(clip.joinFailed)
        assertFalse(first.path in f.deleted)
        assertTrue(f.controller.hasSegments)
        assertTrue(runCatching { f.controller.sendClip(clip) }.isFailure)
        assertEquals(0, f.sent)
    }

    @Test fun retryJoinSucceedsThenSendWorks() = runBlocking {
        val f = Fixture(joinFails = true); f.controller.start(); f.now = 2000
        f.controller.pauseForReview(); f.controller.resumeSegment(); f.now = 4000
        val failed = f.controller.pauseForReview()!!
        f.joinFails = false
        val joined = f.controller.retryJoin(failed)
        assertFalse(joined.joinFailed); assertEquals(4, joined.durationSeconds)
        assertTrue(f.controller.sendClip(joined)); assertEquals(1, f.sent)
        assertFalse(f.controller.hasSegments)
    }

    @Test fun retryJoinStillFailingKeepsParts() = runBlocking {
        val f = Fixture(joinFails = true); f.controller.start(); f.now = 2000
        f.controller.pauseForReview(); f.controller.resumeSegment(); f.now = 4000
        val failed = f.controller.pauseForReview()!!
        assertTrue(f.controller.retryJoin(failed).joinFailed)
        assertTrue(f.controller.hasSegments)
    }

    @Test fun resumeReusesSessionEncodingConfig() = runBlocking {
        val low = VoiceRecordConfig(bitRate = 32000)
        val f = Fixture(configs = ArrayDeque(listOf(low, VoiceRecordConfig(bitRate = 96000))))
        f.controller.start(); f.now = 2000
        f.controller.pauseForReview(); f.controller.resumeSegment()
        assertEquals(listOf(32000, 32000), f.startedWith.map { it.bitRate })
    }

    @Test fun deleteFromReviewRemovesEveryPart() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 2000
        val a = f.controller.pauseForReview()!!
        f.controller.resumeSegment(); f.now = 4000
        f.controller.pauseForReview()
        f.controller.discardReview()
        assertTrue(a.path in f.deleted); assertFalse(f.controller.isRecording); assertFalse(f.controller.hasSegments)
    }

    @Test fun underOneSecondIsDiscarded() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 500
        assertNull(f.controller.pauseForReview()); assertFalse(f.controller.isRecording)
    }

    @Test fun sendRefusedAfterAccountChangeAndFileKept() = runBlocking {
        val f = Fixture(); f.controller.start(); f.now = 2000
        val clip = f.controller.pauseForReview()!!
        f.controller.invalidate()
        assertTrue(runCatching { f.controller.sendClip(clip) }.isFailure)
        assertEquals(0, f.sent); assertFalse(clip.path in f.deleted)
    }
}
