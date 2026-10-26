package com.example.chat.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors

/** Explicitly typed output. Never pass to the existing image-only capture callback. */
data class FinalizedVideo(val file: File, val durationMs: Long, val hasAudio: Boolean, val mimeType: String = "video/mp4")
sealed interface VideoSessionState {
    data object Preparing : VideoSessionState
    data class Ready(val stabilizationEnabled: Boolean) : VideoSessionState
    data class Recording(val hasAudio: Boolean) : VideoSessionState
    data object Finalizing : VideoSessionState
    data class Review(val video: FinalizedVideo) : VideoSessionState
    data class Error(val message: String) : VideoSessionState
}

/** Main-thread API. Caller must release its image/analysis use cases BEFORE binding.
 * Owns only its own preview/video bindings, never unbindAll. No permission request,
 * upload, send, or capture starts automatically. */
class CameraVideoSession(
    context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val selector: CameraSelector,
    private val desiredStabilization: Boolean,
    private val onState: (VideoSessionState) -> Unit,
) : DefaultLifecycleObserver, AutoCloseable {
    private val app = context.applicationContext
    private val main = ContextCompat.getMainExecutor(app)
    private val io = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var activeFile: File? = null
    private var reviewFile: File? = null
    private var closed = false
    private var busy = false
    private var generation = 0
    private var stabilized = false
    private var binding = false

    init { owner.lifecycle.addObserver(this) }
    fun bind() {
        checkMain()
        if (closed || capture != null || binding) return
        binding = true
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            binding = false
            onState(VideoSessionState.Error("Camera permission is needed to record video.")); return
        }
        onState(VideoSessionState.Preparing)
        val future = ProcessCameraProvider.getInstance(app)
        future.addListener({
            if (closed || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                binding = false
                return@addListener
            }
            binding = false
            try {
                val p = future.get()
                val info = p.availableCameraInfos.firstOrNull { selector.filter(listOf(it)).isNotEmpty() }
                    ?: error("Selected camera is unavailable")
                stabilized = desiredStabilization && Recorder.getVideoCapabilities(info).isStabilizationSupported
                val recorder = Recorder.Builder().setQualitySelector(
                    QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)),
                ).build()
                val video = VideoCapture.Builder(recorder).setVideoStabilizationEnabled(stabilized).build()
                val view = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.bindToLifecycle(owner, selector, view, video)
                provider = p; preview = view; capture = video
                onState(VideoSessionState.Ready(stabilized))
            } catch (e: Exception) { onState(VideoSessionState.Error("Video camera could not start. Try Photo or reopen the camera.")) }
        }, main)
    }

    fun start(requestAudio: Boolean) {
        checkMain()
        if (closed || busy || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val video = capture ?: return
        val audio = shouldRecordAudio(requestAudio, ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        reviewFile?.delete(); reviewFile = null
        val file = try {
            File.createTempFile("video-", ".mp4", File(app.cacheDir, "camera-video").apply { mkdirs() })
        } catch (_: Exception) {
            onState(VideoSessionState.Error("Not enough storage to start recording."))
            return
        }
        activeFile = file; busy = true
        val ticket = ++generation
        try {
            var pending = video.output.prepareRecording(app, FileOutputOptions.Builder(file).build())
            if (audio) pending = pending.withAudioEnabled()
            recording = pending.start(main) { event ->
                if (event is VideoRecordEvent.Start && !closed) onState(VideoSessionState.Recording(audio))
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    io.execute {
                        val metadata = if (!event.hasError()) validatedMetadata(file) else null
                        main.execute {
                            if (closed) io.shutdown()
                            busy = false; activeFile = null
                            if (closed || ticket != generation || metadata == null) {
                                file.delete()
                                if (!closed) {
                                    if (ticket != generation) onState(VideoSessionState.Ready(stabilized))
                                    else onState(VideoSessionState.Error("Video was not saved. Please record again."))
                                }
                            } else {
                                reviewFile = file
                                onState(VideoSessionState.Review(FinalizedVideo(file, metadata.first, metadata.second)))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            busy = false; activeFile = null; file.delete()
            onState(VideoSessionState.Error("Recording could not start. Please try again."))
        }
    }
    fun stop() { checkMain(); if (recording != null) { onState(VideoSessionState.Finalizing); recording?.stop() } }
    fun discard() {
        checkMain()
        if (closed) return
        generation++
        reviewFile?.delete(); reviewFile = null
        if (busy) { onState(VideoSessionState.Finalizing); recording?.stop() }
        else onState(VideoSessionState.Ready(stabilized))
    }
    /** Only transfers a finalized local video after explicit review confirmation. */
    fun takeReviewedVideo(video: FinalizedVideo): FinalizedVideo? {
        checkMain()
        if (closed || reviewFile != video.file || !video.file.isFile) return null
        reviewFile = null
        return video
    }
    // Pause finishes a local reviewable clip; it never confirms/transfers it.
    override fun onPause(owner: LifecycleOwner) { stop() }
    override fun onResume(owner: LifecycleOwner) { if (!closed && capture == null) bind() }
    override fun onDestroy(owner: LifecycleOwner) { close() }
    override fun close() {
        checkMain()
        if (closed) return
        closed = true; generation++; owner.lifecycle.removeObserver(this)
        recording?.stop(); reviewFile?.delete(); reviewFile = null
        // Finalize owns active-file cleanup, so do not delete a file still being encoded.
        if (!busy) activeFile?.delete()
        preview?.let { provider?.unbind(it) }; capture?.let { provider?.unbind(it) }
        preview = null; capture = null
        // Shutdown accepts already queued finalization work; no further starts are possible.
        if (!busy) io.shutdown()
    }
    private fun validatedMetadata(file: File): Pair<Long, Boolean>? = runCatching {
        if (!file.isFile || file.length() == 0L) return@runCatching null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (canReviewVideo(false, file.length(), duration, hasVideo)) {
                duration!! to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
            } else null
        } finally { retriever.release() }
    }.getOrNull()
    private fun checkMain() { check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) }
}
