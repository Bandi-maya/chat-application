package com.example.chat.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/** Explicitly typed output. Never pass to the existing image-only capture callback. */
data class FinalizedVideo(val file: File, val durationMs: Long, val hasAudio: Boolean, val mimeType: String = "video/mp4")
sealed interface VideoSessionState {
    data object Preparing : VideoSessionState
    data class Ready(val stabilizationEnabled: Boolean, val boundFront: Boolean = false, val snapshotAvailable: Boolean = false, val lensEffectActive: Boolean = true) : VideoSessionState
    data class Recording(val hasAudio: Boolean) : VideoSessionState
    data class Paused(val hasAudio: Boolean) : VideoSessionState
    data object Finalizing : VideoSessionState
    data class Review(val video: FinalizedVideo) : VideoSessionState
    data class Error(val message: String) : VideoSessionState
}

/** Instrumentation-only timing/observation hooks. Release builds ignore all setters at use sites. */
internal object CameraVideoNativeProof {
    @Volatile var beforeBind: (suspend () -> Unit)? = null
    @Volatile var observe: ((VideoSessionState) -> Unit)? = null
    /** Instrumentation-only: simulate a device that cannot bind the 3-use-case
     *  combination so tests can prove the no-snapshot fallback path. */
    @Volatile var forceSnapshotUnavailable: Boolean = false
    // DEBUG-only: emulator cameras see no real face, so tests inject a synthetic
    // pose to prove the recorded-video compositing path with real output pixels.
    @Volatile var fakeLensPoses: List<FacePose>? = null
    fun reset() { beforeBind=null; observe=null; forceSnapshotUnavailable=false; fakeLensPoses=null }
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
    private val auth: VideoScopeLease,
    private val viewportProvider: () -> LensSize = { LensSize(0f, 0f) },
    private val lensProvider: () -> LensDefinition? = { null },
    private val posesProvider: () -> List<FacePose> = { emptyList() },
    private val onState: (VideoSessionState) -> Unit,
) : DefaultLifecycleObserver, AutoCloseable {
    private val app = context.applicationContext
    private val main = ContextCompat.getMainExecutor(app)
    private val io = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: androidx.camera.core.Camera? = null
    private var snapshot: ImageCapture? = null
    private var recordingAudio = false
    private var activeFile: File? = null
    private var reviewFile: File? = null
    private var closed = false
    private var busy = false
    private var generation = 0
    private var stabilized = false
    private var binding = false
    private var boundFront = false

    init { owner.lifecycle.addObserver(this) }
    private fun publish(state: VideoSessionState) {
        // The auth barrier authorizes only a short decision. Compose/native work stays outside.
        if (auth.isCurrent()) {
            onState(state)
            if(com.example.chat.BuildConfig.DEBUG) try { CameraVideoNativeProof.observe?.invoke(state) } catch (_:Throwable) { /* Collect-only instrumentation cannot affect capture. */ }
        }
    }
    fun hasDraft(): Boolean = busy || activeFile != null || reviewFile != null
    fun bind() {
        checkMain()
        if (!auth.isCurrent() || closed || capture != null || binding) return
        binding = true
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            binding = false
            publish(VideoSessionState.Error("Camera permission is needed to record video.")); return
        }
        publish(VideoSessionState.Preparing)
        val future = ProcessCameraProvider.getInstance(app)
        fun finishBinding() {
            // Recheck after any debug timing suspension, and also on the ordinary path.
            if (!auth.isCurrent() || closed || capture != null || !binding ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                binding = false
                return
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
                // Reference camera UI offers a still snapshot while recording.
                // That needs Preview+VideoCapture+ImageCapture together, which not
                // every device supports: try the 3-use-case bind first and fall
                // back to video-only, reporting real availability in Ready so the
                // UI never draws a dead snapshot button.
                // Gallery save uses scoped storage (API 29+); below that a public
                // Pictures write needs a permission this app does not request, so
                // the snapshot combination is only attempted where saving can work.
                val canSaveSnapshot = android.os.Build.VERSION.SDK_INT >= 29
                val wantSnapshot = canSaveSnapshot && !(com.example.chat.BuildConfig.DEBUG && CameraVideoNativeProof.forceSnapshotUnavailable)
                // Selected face lenses composite into BOTH preview and the recorded
                // file (CameraX OverlayEffect) so video output is never a raw feed
                // behind a filtered preview. Drawing reuses the same
                // LensAnchorPainter as the photo bake path; poses map from preview
                // cover-crop space onto each frame like composeLensIntoImage.
                val overlay = androidx.camera.effects.OverlayEffect(
                    androidx.camera.effects.OverlayEffect.PREVIEW or androidx.camera.effects.OverlayEffect.VIDEO_CAPTURE,
                    0,
                    android.os.Handler(android.os.Looper.getMainLooper()),
                    androidx.core.util.Consumer { /* overlay errors must never kill recording */ },
                )
                overlay.setOnDrawListener { frame ->
                    val lens = lensProvider()
                    val poses = (if (com.example.chat.BuildConfig.DEBUG) CameraVideoNativeProof.fakeLensPoses else null) ?: posesProvider()
                    if (lens == null || lens.isNoLens || poses.isEmpty()) return@setOnDrawListener true
                    val oc = frame.overlayCanvas
                    val w = oc.width.toFloat(); val h = oc.height.toFloat()
                    val pv = viewportProvider()
                    if (w <= 0f || h <= 0f || pv.width <= 0f || pv.height <= 0f) return@setOnDrawListener true
                    val scale = maxOf(pv.width / w, pv.height / h)
                    val cropLeft = (pv.width - w * scale) / 2f
                    val cropTop = (pv.height - h * scale) / 2f
                    fun toFrame(p0: LensOffset) = LensOffset((p0.dx - cropLeft) / scale, (p0.dy - cropTop) / scale)
                    val mapped = poses.map { pose ->
                        FacePose(
                            pose.trackingId,
                            LensRect.fromPoints(toFrame(LensOffset(pose.headBounds.left, pose.headBounds.top)), toFrame(LensOffset(pose.headBounds.right, pose.headBounds.bottom))),
                            toFrame(pose.leftEye), toFrame(pose.rightEye), toFrame(pose.noseBase), toFrame(pose.mouthCenter),
                            pose.rollRadians, pose.yawRadians, pose.pitchRadians, pose.confidence,
                        )
                    }
                    LensAnchorPainter.paint(androidx.compose.ui.graphics.Canvas(oc), lens, mapped, 1.0f)
                    true
                }
                fun bindWithEffect(vararg extra: androidx.camera.core.UseCase): androidx.camera.core.Camera? = try {
                    val group = androidx.camera.core.UseCaseGroup.Builder()
                        .addUseCase(view).addUseCase(video)
                        .apply { extra.forEach { addUseCase(it) } }
                        .addEffect(overlay)
                        .build()
                    p.bindToLifecycle(owner, selector, group)
                } catch (_: Exception) { null }
                var actual: androidx.camera.core.Camera? = null
                var lensEffectActive = false
                if (wantSnapshot) {
                    val snap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    actual = bindWithEffect(snap)
                    if (actual != null) { snapshot = snap; lensEffectActive = true }
                }
                // Plain-recording fallback: devices rejecting the effect bind still
                // record video rather than failing the camera outright, with the UI
                // told the lens will NOT be in the recording (lensEffectActive=false).
                if (actual == null) actual = bindWithEffect().also { if (it != null) lensEffectActive = true }
                if (actual == null) actual = p.bindToLifecycle(owner, selector, view, video)
                boundFront = CameraSelector.DEFAULT_FRONT_CAMERA.filter(listOf(actual.cameraInfo)).isNotEmpty()
                provider = p; preview = view; capture = video; camera = actual
                publish(VideoSessionState.Ready(stabilized, boundFront, snapshot != null, lensEffectActive))
            } catch (e: Exception) { publish(VideoSessionState.Error("Video camera could not start. Try Photo or reopen the camera.")) }
        }
        future.addListener({
            val barrier=if(com.example.chat.BuildConfig.DEBUG) CameraVideoNativeProof.beforeBind else null
            if(barrier == null) finishBinding()
            else owner.lifecycleScope.launch {
                try { barrier(); finishBinding() }
                catch(cancelled:kotlinx.coroutines.CancellationException) { binding=false; throw cancelled }
                catch(error:Throwable) { binding=false; throw error }
            }
        }, main)
    }

    fun start(requestAudio: Boolean) {
        checkMain()
        if (!auth.isCurrent() || closed || busy || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val video = capture ?: return
        val audio = shouldRecordAudio(requestAudio, ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        reviewFile?.delete(); reviewFile = null
        val file = try {
            File.createTempFile("video-", ".mp4", File(app.cacheDir, "camera-video").apply { mkdirs() })
        } catch (_: Exception) {
            publish(VideoSessionState.Error("Not enough storage to start recording."))
            return
        }
        activeFile = file; busy = true
        val ticket = ++generation
        try {
            var pending = video.output.prepareRecording(app, FileOutputOptions.Builder(file).build())
            if (audio) pending = pending.withAudioEnabled()
            if (!auth.isCurrent()) { busy = false; activeFile = null; file.delete(); return }
            recording = pending.start(main) { event ->
                if (event is VideoRecordEvent.Start && !closed && auth.isCurrent()) { recordingAudio = audio; publish(VideoSessionState.Recording(audio)) }
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    io.execute {
                        val metadata = if (!event.hasError()) validatedMetadata(file) else null
                        main.execute {
                            if (closed) io.shutdown()
                            busy = false; activeFile = null
                            if (!auth.isCurrent() || closed || ticket != generation || metadata == null) {
                                file.delete()
                                if (!closed) {
                                    if (ticket != generation) publish(VideoSessionState.Ready(stabilized, boundFront))
                                    else publish(VideoSessionState.Error("Video was not saved. Please record again."))
                                }
                            } else {
                                reviewFile = file
                                publish(VideoSessionState.Review(FinalizedVideo(file, metadata.first, metadata.second)))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            busy = false; activeFile = null; file.delete()
            publish(VideoSessionState.Error("Recording could not start. Please try again."))
        }
    }
    /** The bound camera for this session's own preview/video binding, for zoom control.
     *  Null before bind completes and after close. */
    fun boundCamera(): androidx.camera.core.Camera? = camera
    /** Still snapshot while recording (reference camera UI). Only possible when the
     *  device bound the 3-use-case combination (Ready.snapshotAvailable). Saves the
     *  JPEG to the device gallery via MediaStore; the callback reports the real
     *  result - no fake success. */
    fun takeSnapshot(onResult: (Boolean) -> Unit) {
        checkMain()
        val snap = snapshot
        if (snap == null || !auth.isCurrent() || closed || recording == null) { onResult(false); return }
        if (android.os.Build.VERSION.SDK_INT < 29) { onResult(false); return }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "chaty-recording-${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Chaty")
        }
        val output = ImageCapture.OutputFileOptions.Builder(app.contentResolver, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        snap.takePicture(output, main, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) { onResult(true) }
            override fun onError(exception: ImageCaptureException) { onResult(false) }
        })
    }
    /** Pause an active recording (reference camera UI: pause control beside stop).
     *  Same auth/main-thread discipline as start/stop; no-op unless recording. */
    fun pauseRecording() {
        checkMain()
        if (!auth.isCurrent() || closed) return
        val active = recording ?: return
        active.pause()
        publish(VideoSessionState.Paused(recordingAudio))
    }
    fun resumeRecording() {
        checkMain()
        if (!auth.isCurrent() || closed) return
        val active = recording ?: return
        active.resume()
        publish(VideoSessionState.Recording(recordingAudio))
    }
    fun stop() { checkMain(); if (recording != null) { publish(VideoSessionState.Finalizing); recording?.stop() } }
    fun discard() {
        checkMain()
        if (closed) return
        generation++
        reviewFile?.delete(); reviewFile = null
        if (busy) { publish(VideoSessionState.Finalizing); recording?.stop() }
        else publish(VideoSessionState.Ready(stabilized, boundFront))
    }
    /** Only transfers a finalized local video after explicit review confirmation. */
    fun takeReviewedVideo(video: FinalizedVideo): FinalizedVideo? {
        checkMain()
        if (closed || reviewFile != video.file || !video.file.isFile) return null
        var allowed = false
        auth.runIfCurrent { allowed = true }
        if (!allowed) return null
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
        snapshot?.let { provider?.unbind(it) }
        preview = null; capture = null; camera = null; snapshot = null
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
            val frame = if (hasVideo) retriever.getFrameAtTime(0) else null
            val decoded = frame != null
            frame?.recycle()
            if (canReviewVideo(false, file.length(), duration, hasVideo) && decoded) {
                duration!! to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
            } else null
        } finally { retriever.release() }
    }.getOrNull()
    private fun checkMain() { check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) }
}
