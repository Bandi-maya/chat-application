package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.MessageAttachment
import kotlinx.coroutines.CompletableDeferred

/** Exact port of lib/data/services/voice_note_service.dart's RecordConfig
 *  (AudioEncoder.aacLc, 96kbps, 44.1kHz, mono, autoGain, echoCancel,
 *  noiseSuppress). The encoder choice is implied; Android MediaRecorder maps
 *  AAC-LC onto the .m4a container. */
data class VoiceRecordConfig(
    val bitRate: Int = 96000,
    val sampleRate: Int = 44100,
    val numChannels: Int = 1,
    val autoGain: Boolean = true,
    val echoCancel: Boolean = true,
    val noiseSuppress: Boolean = true,
)

/** Hardware seam over the `record` plugin's AudioRecorder. */
interface VoiceRecorderPort {
    suspend fun hasPermission(): Boolean
    suspend fun start(config: VoiceRecordConfig, path: String)
    /** record stop(): the recorded path, or null to fall back to the staged one. */
    suspend fun stop(): String?
    /** record getAmplitude().current (dBFS, roughly -60..0). */
    suspend fun currentAmplitudeDbfs(): Double
    /** Unsupported recorders must fail explicitly, never simulate a paused capture. */
    suspend fun pause() { throw UnsupportedOperationException("Recorder pause is unavailable.") }
    suspend fun resume() { throw UnsupportedOperationException("Recorder resume is unavailable.") }
    suspend fun dispose()
}

/** File-system seam: staging path creation + quiet delete. */
interface VoiceStagingPort {
    /** '${tempDir}/chaty_voice_<epochMs>.m4a' */
    fun newStagingPath(epochMs: Long): String
    /** Dart _deleteQuietly: delete if it exists, swallow every failure. */
    suspend fun deleteQuietly(path: String)
}

/** Exact port of VoiceNoteService (voice_note_service.dart): busy single-flight
 *  semantics, staging-file lifecycle, 1s minimum duration, level floor of -60.
 *  Capture hardware, upload and send stay behind injected seams so the whole
 *  state machine is JVM-testable; wiring:
 *  - recorder/staging <- MediaRecorder + cacheDir on Android, fakes in tests.
 *  - uploadAudio <- ChatMediaService.uploadBytes(conversationId, "audio", ...,
 *    displayName, mimeType "audio/mp4", durationSeconds) after reading bytes.
 *  - sendAudioMessage <- the backend send-with-attachment seam
 *    (Dart dataStore.sendMessage(text: '', type: MessageType.audio, attachment:)). */
class VoiceNoteController(
    val conversationId: String,
    private val recorder: VoiceRecorderPort,
    private val staging: VoiceStagingPort,
    private val uploadAudio: suspend (sourcePath: String, displayName: String, durationSeconds: Int) -> MessageAttachment,
    private val sendAudioMessage: suspend (attachment: MessageAttachment) -> Unit,
    /** Adaptive AAC-LC profile selected from the current uplink/data-saver state (32-96 kbps). */
    private val recordConfig: () -> VoiceRecordConfig = { VoiceRecordConfig() },
    private val now: () -> Long = System::currentTimeMillis,
    private val validateSession: () -> Unit = {},
    /** When true, a recording that fails to upload/send stays as a private retry draft. */
    private val retainOnFailure: Boolean = false,
    /** Join finished segments (no re-encode) into the output path; throws when it cannot. */
    private val joinSegments: suspend (segments: List<String>, outputPath: String) -> Unit =
        { _, _ -> throw AacJoinException("Joining recording parts is unavailable.") },
) {
    private var recorderActive = false
    private val segments = mutableListOf<String>()
    private var joinedPath: String? = null
    private var segmentCounter = 0
    private var segmentStartMs = 0L
    private var sessionConfig: VoiceRecordConfig? = null
    private var totalSeconds = 0
    private var lastPartSeconds = 1

    /** Failed-send recording kept for retry; bound to this controller's account scope by the caller. */
    data class RetryDraft(val path: String, val displayName: String, val durationSeconds: Int)
    var retryDraft: RetryDraft? = null
        private set

    var isRecording = false
        private set
    var isBusy = false
        private set
    private var disposed = false
    private val invalidated = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Safe inside account transition listener: no IO or hardware call. */
    fun invalidate() { invalidated.set(true) }
    /** Re-arm after an account transition, only while idle, before a new recording starts. */
    fun rearm() { if (!isRecording && !isBusy && !disposed) invalidated.set(false) }
    val isInvalidated: Boolean get() = invalidated.get()
    private fun requireActive() {
        check(!invalidated.get() && !disposed) { "Recording session is no longer active." }
        validateSession()
    }
    private val clock = ActiveVoiceClock(now)
    val isPaused: Boolean get() = clock.paused
    private var path: String? = null
    private var busySignal: CompletableDeferred<Unit>? = null
    private var disposeSignal: CompletableDeferred<Unit>? = null

    /** Dart elapsed getter (ms precision here; the UI formats mm:ss). */
    val elapsedMs: Long
        get() = clock.elapsedMs

    /** Dart currentLevel(): strictly read-only; -60 floor when idle or the
     *  probe fails so the meter can always render something stable. */
    suspend fun currentLevelDbfs(): Double {
        if (!isRecording || isPaused) return -60.0
        return try {
            recorder.currentAmplitudeDbfs()
        } catch (_: Throwable) {
            -60.0
        }
    }

    private fun enterBusy(): CompletableDeferred<Unit> {
        isBusy = true
        val signal = CompletableDeferred<Unit>()
        busySignal = signal
        return signal
    }

    private fun leaveBusy(operation: CompletableDeferred<Unit>) {
        isBusy = false
        if (busySignal === operation) {
            busySignal = null
            operation.complete(Unit)
        }
    }

    suspend fun start() {
        if (disposed) throw IllegalStateException("Voice-note service has been disposed.")
        if (isRecording || isBusy) return
        requireActive()
        val operation = enterBusy()
        var stagingPath: String? = null
        try {
            if (!recorder.hasPermission()) {
                throw IllegalStateException("Microphone permission is required.")
            }
            stagingPath = staging.newStagingPath(now())
            requireActive()
            val config = recordConfig()
            sessionConfig = config
            recorder.start(config, stagingPath)
            try { requireActive() } catch (error: Throwable) {
                runCatching { recorder.stop() }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
            path = stagingPath
            segments.clear(); joinedPath = null; segmentStartMs = 0L
            clock.start()
            isRecording = true
            recorderActive = true
        } catch (error: Throwable) {
            if (stagingPath != null) staging.deleteQuietly(stagingPath)
            throw error
        } finally {
            leaveBusy(operation)
        }
    }

    suspend fun togglePause() {
        if (disposed || !isRecording || isBusy) return
        requireActive()
        val operation = enterBusy()
        try {
            requireActive()
            if (isPaused) {
                recorder.resume()
                clock.resume()
            } else {
                recorder.pause()
                clock.pause()
            }
        } finally { leaveBusy(operation) }
    }

    /** Dart stopAndSend(): stops, enforces the 1s floor, uploads, sends.
     *  Returns false for the no-send outcomes (guards, empty path, too short)
     *  and ALWAYS releases the recording state + staged file in finally. */
    suspend fun stopAndSend(): Boolean {
        if (disposed || !isRecording || isBusy) return false
        val operation = enterBusy()
        val activeMs = elapsedMs
        val stagedPath = path
        var recordedPath: String? = null
        var keepFile = false
        try {
            recordedPath = recorder.stop() ?: stagedPath
            if (recordedPath.isNullOrEmpty()) {
                return false
            }
            val seconds = (activeMs / 1000L).toInt()
            if (seconds < 1) return false
            requireActive()
            val displayName = "voice_note_${now()}.m4a"
            try {
                val attachment = uploadAudio(recordedPath, displayName, seconds)
                requireActive()
                sendAudioMessage(attachment)
            } catch (error: Throwable) {
                // Keep the file only when the session is still valid; an account change must not keep it.
                if (retainOnFailure && error !is kotlinx.coroutines.CancellationException &&
                    !invalidated.get() && !disposed
                ) {
                    retryDraft = RetryDraft(recordedPath, displayName, seconds)
                    keepFile = true
                }
                throw error
            }
            return true
        } finally {
            isRecording = false
            recorderActive = false
            clock.reset()
            path = null
            try {
                for (cleanupPath in listOfNotNull(recordedPath, stagedPath).distinct()) {
                    if (keepFile && cleanupPath == recordedPath) continue
                    staging.deleteQuietly(cleanupPath)
                }
            } finally { leaveBusy(operation) }
        }
    }

    /** Retry the kept draft. Deleted on success; kept on another failure; dropped if the session changed. */
    suspend fun retryDraft(): Boolean {
        val draft = retryDraft ?: return false
        if (disposed || isBusy || isRecording) return false
        val operation = enterBusy()
        try {
            requireActive()
            val attachment = uploadAudio(draft.path, draft.displayName, draft.durationSeconds)
            requireActive()
            sendAudioMessage(attachment)
            retryDraft = null
            staging.deleteQuietly(draft.path)
            return true
        } catch (error: Throwable) {
            if (invalidated.get() || disposed) discardDraftInternal()
            throw error
        } finally { leaveBusy(operation) }
    }

    /** Drop the draft: cancel, logout, account change or dispose. */
    suspend fun discardDraft() { discardDraftInternal() }

    private suspend fun discardDraftInternal() {
        val draft = retryDraft ?: return
        retryDraft = null
        staging.deleteQuietly(draft.path)
    }


    /** A finished recording held for review before sending. [joinFailed] means the parts could not be joined: the clip is only the latest part for listening and cannot be sent until a join succeeds. */
    data class Clip(val path: String, val displayName: String, val durationSeconds: Int, val joinFailed: Boolean = false)

    val hasSegments: Boolean get() = segments.isNotEmpty()

    private suspend fun deleteSegments() {
        val all = segments.toList() + listOfNotNull(joinedPath)
        segments.clear(); joinedPath = null
        for (p in all.distinct()) staging.deleteQuietly(p)
    }

    /** Pause: finalize the current segment, join all segments so far, and hand back a clip to review.
     *  Null when there is under 1s of audio (everything removed). */
    suspend fun pauseForReview(): Clip? {
        if (disposed || !isRecording || !recorderActive || isBusy) return null
        val operation = enterBusy()
        try {
            val staged = path
            val seg = recorder.stop() ?: staged
            recorderActive = false
            clock.pause()
            if (!seg.isNullOrEmpty()) segments += seg
            if (staged != null && staged != seg) staging.deleteQuietly(staged)
            path = null
            val totalMs = clock.elapsedMs
            val seconds = (totalMs / 1000L).toInt()
            if (segments.isEmpty() || seconds < 1) { endSession(); return null }
            requireActive()
            lastPartSeconds = ((totalMs - segmentStartMs) / 1000L).toInt().coerceAtLeast(1)
            totalSeconds = seconds
            return joinedClip("voice_note_${now()}.m4a")
        } catch (error: Throwable) {
            endSession(); throw error
        } finally { leaveBusy(operation) }
    }

    /** One part: use it as is. Several: join without re-encode. Join failure keeps every part and flags the clip. */
    private suspend fun joinedClip(name: String): Clip {
        if (segments.size == 1) return Clip(segments[0], name, totalSeconds)
        val out = staging.newStagingPath(now() + (++segmentCounter))
        return try {
            joinSegments(segments.toList(), out)
            joinedPath?.let { staging.deleteQuietly(it) }
            joinedPath = out
            Clip(out, name, totalSeconds)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            staging.deleteQuietly(out)
            Clip(segments.last(), name, lastPartSeconds, joinFailed = true)
        }
    }

    /** Try the join again for the paused parts. Segments are kept either way. */
    suspend fun retryJoin(current: Clip): Clip {
        if (disposed || !isRecording || recorderActive || isBusy || segments.isEmpty()) return current
        val operation = enterBusy()
        try { requireActive(); return joinedClip(current.displayName) } finally { leaveBusy(operation) }
    }

    /** Resume from review: start a new segment. The joined review file is removed; segments stay. */
    suspend fun resumeSegment() {
        if (disposed || !isRecording || recorderActive || isBusy) return
        val operation = enterBusy()
        try {
            requireActive()
            if (!recorder.hasPermission()) throw IllegalStateException("Microphone permission is required.")
            val next = staging.newStagingPath(now() + (++segmentCounter))
            // Same encoder settings for every part: adaptive config must not change between segments.
            recorder.start(sessionConfig ?: recordConfig().also { sessionConfig = it }, next)
            path = next
            recorderActive = true
            segmentStartMs = clock.elapsedMs
            clock.resume()
            joinedPath?.let { staging.deleteQuietly(it) }
            joinedPath = null
        } finally { leaveBusy(operation) }
    }

    private suspend fun endSession() {
        isRecording = false; recorderActive = false
        sessionConfig = null
        clock.reset(); path = null
        deleteSegments()
    }

    /** Upload and send a reviewed clip. Everything is deleted on success; on failure the clip stays for another Send. */
    suspend fun sendClip(clip: Clip): Boolean {
        if (disposed || isBusy || recorderActive) return false
        if (clip.joinFailed) throw AacJoinException("Recording parts must be joined before sending.")
        val operation = enterBusy()
        try {
            requireActive()
            val attachment = uploadAudio(clip.path, clip.displayName, clip.durationSeconds)
            requireActive()
            sendAudioMessage(attachment)
            endSession()
            return true
        } finally { leaveBusy(operation) }
    }

    /** Delete from review: remove every part and end the session. */
    suspend fun discardReview() { if (!isBusy) endSession() }

    suspend fun cancel() {
        if (isBusy) return
        if (!isRecording && path == null) return
        val operation = enterBusy()
        val stagedPath = path
        var target = stagedPath
        try {
            if (isRecording && recorderActive) target = recorder.stop() ?: target
        } finally {
            isRecording = false
            recorderActive = false
            clock.reset()
            path = null
            try {
                for (cleanupPath in listOfNotNull(target, stagedPath).distinct()) {
                    staging.deleteQuietly(cleanupPath)
                }
                deleteSegments()
            } finally { leaveBusy(operation) }
        }
    }

    /** Dart dispose(): single-flight; waits for any pending native start/stop
     *  to finish before cancelling + disposing the recorder. */
    suspend fun dispose() {
        val existing = disposeSignal
        if (existing != null) {
            existing.await()
            return
        }
        val signal = CompletableDeferred<Unit>()
        disposeSignal = signal
        try {
            disposed = true
            busySignal?.await()
            if (isRecording || path != null) cancel()
            discardDraftInternal()
            recorder.dispose()
            signal.complete(Unit)
        } catch (error: Throwable) {
            signal.completeExceptionally(error)
            throw error
        }
    }
}
