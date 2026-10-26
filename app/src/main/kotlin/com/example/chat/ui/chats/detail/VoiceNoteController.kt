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
    private val now: () -> Long = System::currentTimeMillis,
) {
    var isRecording = false
        private set
    var isBusy = false
        private set
    private var disposed = false
    private var startedAtMs: Long? = null
    private var path: String? = null
    private var busySignal: CompletableDeferred<Unit>? = null
    private var disposeSignal: CompletableDeferred<Unit>? = null

    /** Dart elapsed getter (ms precision here; the UI formats mm:ss). */
    val elapsedMs: Long
        get() = startedAtMs?.let { now() - it } ?: 0L

    /** Dart currentLevel(): strictly read-only; -60 floor when idle or the
     *  probe fails so the meter can always render something stable. */
    suspend fun currentLevelDbfs(): Double {
        if (!isRecording) return -60.0
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
        val operation = enterBusy()
        var stagingPath: String? = null
        try {
            if (!recorder.hasPermission()) {
                throw IllegalStateException("Microphone permission is required.")
            }
            stagingPath = staging.newStagingPath(now())
            recorder.start(VoiceRecordConfig(), stagingPath)
            path = stagingPath
            startedAtMs = now()
            isRecording = true
        } catch (error: Throwable) {
            if (stagingPath != null) staging.deleteQuietly(stagingPath)
            throw error
        } finally {
            leaveBusy(operation)
        }
    }

    /** Dart stopAndSend(): stops, enforces the 1s floor, uploads, sends.
     *  Returns false for the no-send outcomes (guards, empty path, too short)
     *  and ALWAYS releases the recording state + staged file in finally. */
    suspend fun stopAndSend(): Boolean {
        if (disposed || !isRecording || isBusy) return false
        val operation = enterBusy()
        val started = startedAtMs
        val stagedPath = path
        var recordedPath: String? = null
        try {
            recordedPath = recorder.stop() ?: stagedPath
            if (recordedPath.isNullOrEmpty() || started == null) {
                return false
            }
            val seconds = ((now() - started) / 1000L).toInt()
            if (seconds < 1) return false
            val attachment = uploadAudio(
                recordedPath,
                "voice_note_${now()}.m4a",
                seconds,
            )
            sendAudioMessage(attachment)
            return true
        } finally {
            isRecording = false
            startedAtMs = null
            path = null
            val cleanupPath = recordedPath ?: stagedPath
            if (cleanupPath != null) staging.deleteQuietly(cleanupPath)
            leaveBusy(operation)
        }
    }

    suspend fun cancel() {
        if (isBusy) return
        if (!isRecording && path == null) return
        val operation = enterBusy()
        var target = path
        try {
            if (isRecording) target = recorder.stop() ?: target
        } finally {
            isRecording = false
            startedAtMs = null
            path = null
            if (target != null) staging.deleteQuietly(target)
            leaveBusy(operation)
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
            recorder.dispose()
            signal.complete(Unit)
        } catch (error: Throwable) {
            signal.completeExceptionally(error)
            throw error
        }
    }
}
