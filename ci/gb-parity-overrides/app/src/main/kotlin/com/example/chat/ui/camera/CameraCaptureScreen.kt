package com.example.chat.ui.camera

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.onboarding.ChatyPermissions
import com.example.chat.ui.onboarding.rememberPermissionRequester
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Exact port of lib/features/camera/camera_capture_screen.dart.
 *
 *  Full-screen camera capture, WhatsApp-style: live filtered viewfinder,
 *  shutter / flip / flash / gallery, an effects tray backed by the shared
 *  [EffectRegistry], face lenses via [LensCarousel]/[LensOverlay]/
 *  [FaceAnchorSolver]/[CameraFaceDetectionBridge], and a confirm pane with
 *  caption before anything sends.
 *
 *  Effects are honest end-to-end: in chat mode the look travels as message
 *  metadata and receivers render it too ([ApplyStoredEffect]); in story mode
 *  it is baked into the published image because statuses have no metadata
 *  channel. Document mode bakes the document-scan matrix into the file.
 *
 *  CameraX mapping (camera package -> CameraX):
 *  - CameraController(ResolutionPreset.high, enableAudio false) ->
 *    Preview + ImageCapture(CAPTURE_MODE_MAXIMIZE_QUALITY) +
 *    ImageAnalysis(KEEP_ONLY_LATEST) bound to the lifecycle.
 *  - availableCameras/firstWhere(back) -> CameraSelector.DEFAULT_BACK_CAMERA.
 *  - setFlashMode(torch/off) -> cameraControl.enableTorch; front camera
 *    resets the toggle like the Dart.
 *  - startImageStream throttle 45ms -> ImageAnalysis backpressure + 45ms gate
 *    in the analyzer, frames converted by [CameraFrameSampler]-compatible
 *    readers through [ImageProxy.toFrameData].
 *  - Permission -> canonical ChatyPermissions.camera +
 *    rememberPermissionRequester (ui/onboarding PermissionRequest.kt @ e0233f6+).
 *  - openAppSettings -> ChatyPermissions.openAppSettings.
 *  - PopScope discard -> BackHandler when a capture is staged.
 *  - App lifecycle: CameraX rebinds automatically with the lifecycle owner;
 *    the Dart's manual dispose/re-init maps to provider.unbindAll on dispose. */

enum class ChatyCaptureMode { chat, story }

/** Only the composer camera action selects frontVideo. Attachment/story defaults stay unchanged. */
enum class CameraEntryPreset { photoBack, composerFrontVideo }

enum class CaptureViewMode { photo, document, portrait, night, lenses, video }

/** Result handed back to the host when the user confirms. */
data class ChatyCaptureResult(
    val path: String,
    val effectId: String,
    val effectIntensity: Double,
    val caption: String = "",
    val ownedResult: CameraOwnedResult? = null,
)

enum class CameraPhase { initializing, ready, permissionDenied, noCamera }

/** CameraX ImageProxy -> the sampler's frame contract (YUV_420_888 planes
 *  with row/pixel strides honored; bytes copied out). */
fun ImageProxy.toFrameData(): FrameData {
    val planesOut = planes.map { plane ->
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bytes = ByteArray(rowStride * height)
        // Copy the largest contiguous span the buffer holds; the readers only
        // index within [rowStride * height).
        val remaining = buffer.remaining()
        val count = minOf(remaining, bytes.size)
        buffer.get(bytes, 0, count)
        FramePlane(bytes, rowStride, pixelStride)
    }
    return FrameData(width, height, FrameFormat.yuv420, planesOut)
}

private class CameraSession(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
) {
    var provider: ProcessCameraProvider? = null
    var camera: androidx.camera.core.Camera? = null
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var imageCapture: ImageCapture? = null
    var analysis: ImageAnalysis? = null
    var preview: Preview? = null
    val analysisExecutor = Executors.newSingleThreadExecutor()
    val captureExecutor = Executors.newSingleThreadExecutor()
    var torchOn: Boolean = false
    var stabilization: Boolean = false
    var flashSupported = false
    var stabilizationSupported = false
    var portrait = false
    var portraitSupported = false
    var portraitActive = false
    var night = false
    var nightSupported = false
    var nightActive = false
    var hdr = false
    var hdrSupported = false
    var hdrActive = false
    var extensions: androidx.camera.extensions.ExtensionsManager? = null
    @Volatile var analyzeFaces = false
    @Volatile var analyzeSegment = false
    private var lastAnalysisNs = 0L

    suspend fun ensureProvider(): ProcessCameraProvider {
        provider?.let { return it }
        return withTimeout(12_000) {
            suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (continuation.isActive) {
                        try {
                            val ready = future.get()
                            provider = ready
                            continuation.resume(ready)
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }

    suspend fun ensureExtensions() {
        if (extensions != null) return
        val p = provider ?: return
        extensions = withTimeout(8_000) {
            suspendCancellableCoroutine { continuation ->
                val future = androidx.camera.extensions.ExtensionsManager.getInstanceAsync(context,p)
                future.addListener({ if (continuation.isActive) {
                    try { continuation.resume(future.get()) }
                    catch (error:Exception) { continuation.resumeWithException(error) }
                } },ContextCompat.getMainExecutor(context))
            }
        }
    }

    fun bind(
        previewView: PreviewView,
        onFrame: (FrameData, Int) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit,
    ) {
        val provider = provider ?: return
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        unbind()
        val baseSelector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val manager = extensions
        nightSupported = manager?.isExtensionAvailable(baseSelector,androidx.camera.extensions.ExtensionMode.NIGHT) == true
        portraitSupported = manager?.isExtensionAvailable(baseSelector,androidx.camera.extensions.ExtensionMode.BOKEH) == true
        nightActive = shouldUseNight(night,nightSupported)
        portraitActive = portrait && portraitSupported
        hdrSupported = manager?.isExtensionAvailable(baseSelector,androidx.camera.extensions.ExtensionMode.HDR) == true
        // Extensions are mutually exclusive; night/portrait win over HDR.
        hdrActive = hdr && hdrSupported && !nightActive && !portraitActive
        val selector = if (nightActive || portraitActive || hdrActive) try {
            manager!!.getExtensionEnabledCameraSelector(baseSelector,
                if(nightActive)androidx.camera.extensions.ExtensionMode.NIGHT
                else if(portraitActive)androidx.camera.extensions.ExtensionMode.BOKEH
                else androidx.camera.extensions.ExtensionMode.HDR)
        } catch (_:Exception) { nightActive=false; nightSupported=false; portraitActive=false; portraitSupported=false; hdrActive=false; hdrSupported=false; baseSelector } else baseSelector
        val info = provider.getCameraInfo(selector)
        flashSupported = info.hasFlashUnit()
        stabilizationSupported = Preview.getPreviewCapabilities(info).isStabilizationSupported
        val preview = Preview.Builder().apply {
            if (stabilizationSupported && !nightActive && !portraitActive) setPreviewStabilizationEnabled(stabilization)
        }.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analyzer.setAnalyzer(analysisExecutor) { proxy ->
            try {
                val now = System.nanoTime()
                if (shouldAnalyzeCameraFrame(analyzeFaces || analyzeSegment,now,lastAnalysisNs)) {
                    lastAnalysisNs = now
                    onFrame(proxy.toFrameData(), proxy.imageInfo.rotationDegrees)
                }
            } finally {
                proxy.close()
            }
        }
        this.preview = preview
        imageCapture = capture
        analysis = analyzer
        try {
            val bound = try {
                if (nightActive || portraitActive) provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                else provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analyzer)
            } catch (_: IllegalArgumentException) {
                // Some cameras cannot supply all three streams at once. Keep
                // photo capture working without optional face analysis.
                provider.unbind(preview, capture, analyzer)
                analyzer.clearAnalyzer()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            }
            camera = bound
            bound.cameraControl.enableTorch(torchOn && lensFacing == CameraSelector.LENS_FACING_BACK)
            bound.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                if (state.error != null) {
                    onError("The camera could not open (code ${state.error?.code}). Close another app using the camera and try again.")
                } else if (state.type == CameraState.Type.OPEN) {
                    onReady()
                }
            }
        } catch (error: Exception) {
            if (nightActive || portraitActive) {
                night = false; nightActive = false; portrait=false; portraitActive=false; hdr = false; hdrActive = false
                bind(previewView,onFrame,onError,onReady)
            } else onError(error.message ?: "The camera could not start.")
        }
    }

    /** Dart setFlashMode(torch/off): torch only applies to the back camera;
     *  a failed call reverts the toggle (caller mirrors the Dart catch). */
    fun setTorch(on: Boolean): Boolean {
        return try {
            if (camera?.cameraInfo?.hasFlashUnit() != true) return false
            camera?.cameraControl?.enableTorch(on) ?: return false
            torchOn = on
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unbind() {
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        try {
            preview?.let { provider?.unbind(it) }
            imageCapture?.let { provider?.unbind(it) }
            analysis?.let { provider?.unbind(it) }
        } catch (_: Exception) {}
    }

    fun dispose() {
        unbind()
        analysisExecutor.shutdown()
        captureExecutor.shutdown()
    }
}

@Composable
fun ChatyCameraCaptureScreen(
    mode: ChatyCaptureMode,
    contactName: String? = null,
    deviceTier: DeviceCapabilityTier = DeviceCapabilityTier.midRange,
    onResult: (ChatyCaptureResult) -> Unit,
    onDismiss: () -> Unit,
    onVideoResult: ((FinalizedVideo) -> Unit)? = null,
    videoAuthPort: VideoAuthPort? = null,
    entryPreset: CameraEntryPreset = CameraEntryPreset.photoBack,
    videoHoldRequest: VideoHoldRequest? = null,
) {
    require(videoHoldRequest == null || entryPreset == CameraEntryPreset.composerFrontVideo) { "Hold recording is composer front-video only" }
    require(entryPreset != CameraEntryPreset.composerFrontVideo || videoAuthPort != null) { "Front-video entry requires its captured auth port" }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val ownedFiles = remember { CameraOwnedFiles() }

    val engine = remember { com.example.chat.ServiceLocator.effectEngine }
    val lensSchedulerScope = rememberCoroutineScope()
    val lensManager = remember {
        val lensPrefs = context.getSharedPreferences("chaty_camera_lens", Context.MODE_PRIVATE)
        LensManager(
            // Recents/favorites survive process death (LensStringListStore contract).
            prefs = object : LensStringListStore {
                override fun getStringList(key: String): List<String>? =
                    lensPrefs.getString(key, null)?.split('|')?.filter { it.isNotBlank() }
                override fun setStringList(key: String, value: List<String>) {
                    lensPrefs.edit().putString(key, value.joinToString("|")).apply()
                }
            },
            // Dart settle Timer -> composition-scoped coroutine delay.
            timerScheduler = LensTimerScheduler { delayMs, action ->
                val job = lensSchedulerScope.launch {
                    kotlinx.coroutines.delay(delayMs)
                    action()
                }
                LensTimerHandle { job.cancel() }
            },
        )
    }
    val faceSolver = remember { FaceAnchorSolver() }
    val faceDetection = remember { CameraFaceDetectionBridge(context) }
    val session = remember(context, lifecycleOwner, entryPreset) { CameraSession(context, lifecycleOwner).apply {
        lensFacing = if(entryPreset == CameraEntryPreset.composerFrontVideo) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
    } }

    var phase by remember { mutableStateOf(CameraPhase.initializing) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var effectsOpen by remember { mutableStateOf(false) }
    var lensBrowserOpen by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(CameraCaptureOptions()) }
    var optionsOpen by remember { mutableStateOf(false) }
    var flashSupported by remember { mutableStateOf(false) }
    var stabilizationSupported by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var scannerBusy by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var hdrOn by remember { mutableStateOf(false) }
    var hdrSupported by remember { mutableStateOf(false) }
    var portraitSupported by remember { mutableStateOf(false) }
    var portraitActive by remember { mutableStateOf(false) }
    var nightSupported by remember { mutableStateOf(false) }
    var nightActive by remember { mutableStateOf(false) }
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }
    var videoOpen by remember(entryPreset) { mutableStateOf(entryPreset == CameraEntryPreset.composerFrontVideo) }
    var captureViewMode by remember(entryPreset) { mutableStateOf(if(entryPreset == CameraEntryPreset.composerFrontVideo) CaptureViewMode.video else CaptureViewMode.photo) }
    var documentReviewPath by remember { mutableStateOf<String?>(null) }
    var capturedPath by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var facePoses by remember { mutableStateOf<List<FacePose>>(emptyList()) }
    var faceHealth by remember { mutableStateOf(TrackingHealth.lost) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraPermissionGranted by remember {
        mutableStateOf(ChatyPermissions.isGranted(context, ChatyPermissions.camera))
    }
    var bindJob by remember { mutableStateOf<Job?>(null) }

    val wantsFaceTracking = capturedPath == null &&
        (captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses) &&
        lensManager.selected.requiresFaceTracking

    fun lensIdForEffect(effectId: String): String? = when (effectId) {
        "face_dog_filter" -> "lens_dog_ears"
        "face_sparkles" -> "lens_sparkles"
        "face_hearts_float" -> "lens_hearts_float"
        "face_neon_crown" -> "lens_neon_crown"
        "face_angel_halo" -> "lens_angel_halo"
        "face_sunglasses_aviator" -> "lens_sunglasses_aviator"
        else -> null
    }

    // Face frame pipeline: throttle 45ms, mirror for front camera, solver maps
    // into the cover-cropped preview space.
    var lastFaceFrameMs by remember { mutableStateOf(0L) }
    var processingFaceFrame by remember { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(LensSize(0f, 0f)) }

    // Live segmented background preview (bg_* effects): the same analysis
    // stream as face frames, throttled harder and composited off the main
    // thread. Failures are silent and leave the viewfinder unfiltered - the
    // capture-time bake stays the exact path. Never runs during night /
    // portrait / video / review, matching the face pipeline's discipline.
    val segmentationBridge = remember { SegmentationBridge(context) }
    val liveSegmentPreview = remember { LiveSegmentPreview(segmentationBridge) }
    DisposableEffect(segmentationBridge) { onDispose { segmentationBridge.dispose() } }
    var segmentPreviewFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var segmentPublishedKey by remember { mutableStateOf<String?>(null) }
    var processingSegmentFrame by remember { mutableStateOf(false) }
    var lastSegmentFrameMs by remember { mutableStateOf(0L) }
    val segmentEffectKey = if (engine.activeEffect.isSegmentationRequired &&
        (captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses) &&
        !capturing && !videoOpen && capturedPath == null && documentReviewPath == null
    ) engine.activeEffect.shaderKey else null

    fun processSegmentFrame(frame: FrameData, rotationDegrees: Int) {
        val key = segmentEffectKey ?: return
        if (processingSegmentFrame) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSegmentFrameMs < 150) return
        lastSegmentFrameMs = nowMs
        processingSegmentFrame = true
        val intensity = engine.intensity
        val mirrored = session.lensFacing == CameraSelector.LENS_FACING_FRONT
        scope.launch(Dispatchers.Default) {
            try {
                val bmp = liveSegmentPreview.render(frame, rotationDegrees, mirrored, key, intensity)
                if (bmp != null && segmentEffectKey == key) {
                    segmentPreviewFrame = bmp
                    segmentPublishedKey = key
                } else bmp?.recycle()
            } catch (_: Exception) {
                // Best-effort preview: never interrupt capture.
            } finally {
                processingSegmentFrame = false
            }
        }
    }

    fun processFaceFrame(frame: FrameData, rotationDegrees: Int) {
        if (!wantsFaceTracking || capturing || processingFaceFrame) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastFaceFrameMs < 45) return
        lastFaceFrameMs = nowMs
        processingFaceFrame = true
        scope.launch {
            try {
                val detection = faceDetection.detect(frame, rotationDegrees)
                if (!wantsFaceTracking || capturing) return@launch
                val preview = if (viewportSize.width <= 0f || viewportSize.height <= 0f) {
                    LensSize(frame.width.toFloat(), frame.height.toFloat())
                } else viewportSize
                val imageW = detection.imageWidth.toFloat()
                val imageH = detection.imageHeight.toFloat()
                val scale = max(preview.width / imageW, preview.height / imageH)
                val scaledW = imageW * scale
                val scaledH = imageH * scale
                val crop = LensRect(
                    (preview.width - scaledW) / 2f,
                    (preview.height - scaledH) / 2f,
                    (preview.width + scaledW) / 2f,
                    (preview.height + scaledH) / 2f,
                )
                val poses = faceSolver.update(
                    nowMs = nowMs,
                    rawFaces = detection.faces,
                    mirrored = session.lensFacing == CameraSelector.LENS_FACING_FRONT,
                    imageExtent = LensSize(imageW, imageH),
                    previewCrop = crop,
                    confidenceScale = 1.0,
                )
                facePoses = poses
                faceHealth = faceSolver.health
            } catch (_: Exception) {
                // Detection is best-effort. Never interrupt capture.
            } finally {
                processingFaceFrame = false
            }
        }
    }

    androidx.compose.runtime.SideEffect {
        session.analyzeFaces = wantsFaceTracking && !nightActive && !portraitActive && !capturing && !videoOpen && documentReviewPath == null
        session.analyzeSegment = segmentEffectKey != null && !nightActive && !portraitActive
        // A key change (blur -> office/gradient, or deselect) must never keep the
        // previous effect's composite on screen while its replacement is in flight.
        if (segmentEffectKey != segmentPublishedKey) segmentPreviewFrame = null
    }

    fun bindCamera() {
        val pv = previewView ?: return
        if (!cameraPermissionGranted || capturedPath != null || documentReviewPath != null || scannerBusy || videoOpen) return
        bindJob?.cancel()
        bindJob = scope.launch {
            phase = CameraPhase.initializing
            errorMessage = null
            try {
                session.ensureProvider()
                try { session.ensureExtensions() } catch (_: Exception) { /* Photo stays available. */ }
                session.bind(
                    previewView = pv,
                    onFrame = { frame, rotation -> processFaceFrame(frame, rotation); processSegmentFrame(frame, rotation) },
                    onError = { message ->
                        errorMessage = message
                        phase = CameraPhase.noCamera
                    },
                    onReady = {
                        phase = CameraPhase.ready
                        portraitSupported = session.portraitSupported
                        portraitActive = session.portraitActive
                        if(captureViewMode==CaptureViewMode.night && !session.nightActive) { captureViewMode=CaptureViewMode.photo; scannerError="Night unavailable. Photo restored." }
                        if(captureViewMode==CaptureViewMode.portrait && !portraitActive) { captureViewMode=CaptureViewMode.photo; scannerError="Portrait unavailable. Photo restored." }
                        nightSupported = session.nightSupported
                        nightActive = session.nightActive
                        hdrSupported = session.hdrSupported
                        if (hdrOn && !session.hdrActive) { hdrOn = false; scannerError = "HDR is unavailable on this camera." }
                        if (nightActive) { faceSolver.reset(); facePoses = emptyList() }
                        if (options.night && !nightActive) { options = options.copy(night=false); scannerError = "Night mode unavailable. Photo restored." }
                        flashSupported = session.flashSupported
                        stabilizationSupported = session.stabilizationSupported
                        val zoom = session.camera?.cameraInfo?.zoomState?.value
                        zoomRatio = zoom?.zoomRatio ?: 1f
                        minZoom = zoom?.minZoomRatio ?: 1f
                        maxZoom = zoom?.maxZoomRatio ?: 1f
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "The camera could not start. Try reopening it."
                phase = CameraPhase.noCamera
            }
        }
    }

    val requestPermission = rememberPermissionRequester { granted, _ ->
        cameraPermissionGranted = granted
        if (!granted) phase = CameraPhase.permissionDenied
    }
    LaunchedEffect(Unit) { requestPermission(ChatyPermissions.camera) }
    LaunchedEffect(previewView, cameraPermissionGranted, capturedPath, documentReviewPath, scannerBusy, videoOpen, options.stabilization, options.night, captureViewMode, hdrOn) {
        if (!cameraPermissionGranted) {
            session.unbind()
            phase = CameraPhase.permissionDenied
        } else if (capturedPath == null && documentReviewPath == null && !scannerBusy && !videoOpen) {
            session.stabilization = options.stabilization
            session.night = options.night || captureViewMode == CaptureViewMode.night
            session.portrait = captureViewMode == CaptureViewMode.portrait
            session.hdr = hdrOn
            bindCamera()
        }
        else if (!videoOpen) session.unbind()
    }
    // Re-check after returning from Android app settings. A granted permission
    // must not leave this composition stranded in its old denied state.
    DisposableEffect(lifecycleOwner, session, capturedPath, documentReviewPath, scannerBusy, videoOpen) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                timerJob?.cancel()
                countdown = 0
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ChatyPermissions.isGranted(context, ChatyPermissions.camera)
                cameraPermissionGranted = granted
                if (granted) bindCamera() else {
                    session.unbind()
                    phase = CameraPhase.permissionDenied
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(phase, previewView, cameraPermissionGranted) {
        if (phase == CameraPhase.initializing && previewView != null && cameraPermissionGranted) {
            kotlinx.coroutines.delay(15_000)
            if (phase == CameraPhase.initializing) {
                errorMessage = "The camera did not open. Close another app using it, then reopen the camera. You can also pick from your gallery."
                phase = CameraPhase.noCamera
                session.unbind()
            }
        }
    }

    fun toggleFlash() {
        if (!flashSupported) return
        val camera = session.camera ?: return
        val next = !flashOn
        val future = camera.cameraControl.enableTorch(next)
        future.addListener({
            try {
                future.get()
                flashOn = next
                session.torchOn = next
            } catch (_: Exception) {
                flashOn = false
                session.torchOn = false
                scannerError = "Flash could not change on this camera."
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun flipCamera() {
        if (phase == CameraPhase.initializing || capturing || countdown > 0 || scannerBusy) return
        ChatyHapticsPort.selection(view)
        session.lensFacing = if (session.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        if (session.lensFacing == CameraSelector.LENS_FACING_FRONT) {
            flashOn = false
            session.torchOn = false
        }
        faceSolver.reset()
        facePoses = emptyList()
        bindCamera()
    }

    fun stagePickedOrCaptured(path: String) {
        if (!ownedFiles.isOpen()) return
        capturedPath = path
        effectsOpen = false
    }

    // Dart FilePicker(type: image) -> Android Photo Picker (the Dart repo's
    // MainActivity used PickVisualMedia). Picked content is copied into the
    // cache so downstream code always has a file path, then document mode
    // bakes the scan matrix like the Dart.
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val stamp = System.currentTimeMillis()
                    val dest = File.createTempFile("chaty_picked_", ".jpg", context.cacheDir)
                    if(!ownedFiles.own(dest)) return@launch
                    try { withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        } ?: error("Image source is unavailable")
                    } } finally { if(!ownedFiles.isOpen() || !kotlinx.coroutines.currentCoroutineContext().isActive) dest.delete() }
                    var selectedPath = dest.absolutePath
                    if (captureViewMode == CaptureViewMode.document) documentReviewPath = selectedPath
                    else stagePickedOrCaptured(selectedPath)
                } catch(cancelled:CancellationException) { throw cancelled
                } catch (_: Exception) {
                    // Picker/copy failure leaves state untouched (Dart returns early).
                }
            }
        }
    }


    fun pickFromGallery() {
        
        ChatyHapticsPort.light(view)
        galleryLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }

    fun captureNow(captureOptions: CameraCaptureOptions) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || scannerBusy) return
        val capture = session.imageCapture ?: return
        if (phase != CameraPhase.ready || capturing) return
        capturing = true
        val documentCapture = captureViewMode == CaptureViewMode.document
        ChatyHapticsPort.light(view)
        val stamp = System.currentTimeMillis()
        val out = File.createTempFile("chaty_capture_", ".jpg", context.cacheDir)
        if(!ownedFiles.own(out)) { capturing=false; return }
        val opts = ImageCapture.OutputFileOptions.Builder(out).build()
        capture.takePicture(opts, session.captureExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                if(!ownedFiles.isOpen()) { out.delete(); return }
                val work = scope.launch {
                    var path = out.absolutePath
                    if (!documentCapture && !session.nightActive && !session.portraitActive) {
                        val lens = lensManager.selected
                        val poses = facePoses
                        if (!lens.isNoLens && poses.isNotEmpty() && faceSolver.health != TrackingHealth.lost) {
                            try {
                                val bytes = composeLensIntoImage(
                                    sourceBytes = File(path).readBytes(),
                                    previewSize = if (viewportSize.width > 0) viewportSize
                                        else LensSize(1080f, 1920f),
                                    lens = lens,
                                    poses = poses,
                                )
                                val lensStamp = System.currentTimeMillis()
                                val staged = File.createTempFile("chaty_lens_", ".png", context.cacheDir)
                                if(!ownedFiles.own(staged)) return@launch
                                staged.writeBytes(bytes)
                                if(!ownedFiles.own(staged)) return@launch
                                val jpg = File.createTempFile("chaty_lens_", ".jpg", context.cacheDir)
                                if(!ownedFiles.own(jpg)) return@launch
                                val ok = try {
                                    jpg.outputStream().use { os ->
                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            ?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os) == true
                                    }
                                } catch (_: Exception) { false }
                                if(!ownedFiles.own(jpg)) return@launch
                                if (ok) {
                                    path = jpg.absolutePath
                                    try { staged.delete() } catch (_: Exception) {}
                                } else {
                                    path = staged.absolutePath
                                }
                            } catch (_: Exception) {
                                // The face lens could not be baked; keep the photo.
                            }
                        }
                    }
                    if (documentCapture) {
                        capturing = false
                        documentReviewPath = path
                        return@launch
                    }
                    var derived: String? = null
                    try {
                        path = withContext(Dispatchers.IO) {
                            CameraPhotoOutput.process(context, path, captureOptions,
                                if (viewportSize.height > 0) viewportSize.width / viewportSize.height else 9f / 16f).also { derived=it; if(it != path) ownedFiles.own(File(it)) }
                        }
                    } catch(cancelled:CancellationException) { derived?.takeIf { it != out.absolutePath }?.let { File(it).delete() }; throw cancelled
                    } catch (_: Exception) {
                        scannerError = "Could not apply capture options. Original photo kept; nothing was sent."
                    }
                    capturing = false
                    stagePickedOrCaptured(path)
                }
                work.invokeOnCompletion { if(!ownedFiles.isOpen()) out.delete() }
            }

            override fun onError(exception: ImageCaptureException) {
                out.delete()
                scope.launch {
                    capturing = false
                    errorMessage = exception.message ?: "The photo could not be captured."
                    phase = CameraPhase.noCamera
                }
            }
        })
    }

    fun shutter() {
        if (scannerBusy || capturing || countdown > 0) return
        
        val captureOptions = options
        timerJob = scope.launch {
            try {
                for (seconds in captureOptions.timerSeconds downTo 1) {
                    countdown = seconds
                    kotlinx.coroutines.delay(1_000)
                }
                countdown = 0
                captureNow(captureOptions)
            } finally { countdown = 0 }
        }
    }

    fun discard() {
        ChatyHapticsPort.light(view)
        capturedPath = null
        ownedFiles.discard()
    }

    fun confirmAndSend() {
        ChatyHapticsPort.success(view)
        val raw = capturedPath ?: return
        val engineEffect = engine.activeEffect
        var path = raw
        var effectId = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) "none" else engineEffect.id
        var intensity = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) 1.0 else engine.intensity
        scope.launch {
            // Stories carry no metadata channel, so the look must live in the pixels.
            // Shader effects (pixelate, segmented backgrounds) have no receiver-side
            // renderer, so they bake into the pixels in EVERY photo mode and the
            // metadata tag is cleared - never a double-apply, never a claimed but
            // unrendered filter (fail-open bakes report applied=false). The exact
            // contract lives in ImageFxBaker.resolveCaptureEffectPlan (unit-tested).
            val effectPlan = ImageFxBaker.resolveCaptureEffectPlan(
                storyMode = mode == ChatyCaptureMode.story,
                effectId = effectId,
                hasRenderPath = engineEffect.colorMatrix != null || engineEffect.shaderKey != null,
                isShaderEffect = engineEffect.shaderKey != null,
                intensity = intensity,
            )
            if (captureViewMode != CaptureViewMode.document && effectPlan.bake) {
                try {
                    val baked = ImageFxBaker.bakeEffectIntoFile(context, path, engineEffect, intensity, onCreated={ ownedFiles.own(it) })
                    path = baked.path
                    val (tag, tagIntensity) = ImageFxBaker.tagAfterBake(effectPlan, baked.applied, effectId, intensity)
                    effectId = tag
                    intensity = tagIntensity
                } catch(cancelled:CancellationException) { throw cancelled
                } catch (_: Exception) {
                    scannerError = "Could not apply this effect at full resolution. Original kept; nothing sent."
                    return@launch
                } catch (_: OutOfMemoryError) {
                    scannerError = "Not enough memory for the full-resolution effect. Original kept; nothing sent."
                    return@launch
                }
            }
            if(!ownedFiles.isOpen()) return@launch
            val transferred = ownedFiles.transfer(path) ?: return@launch
            try { onResult(
                ChatyCaptureResult(
                    path = path,
                    effectId = effectId,
                    effectIntensity = intensity,
                    caption = caption.trim(),
                    ownedResult = transferred,
                ),
            ) } catch(error:Throwable) { transferred.close(); throw error }
        }
    }

    // PopScope: back with a staged capture discards instead of leaving.
    BackHandler(enabled = !videoOpen && (capturedPath != null || documentReviewPath != null)) {
        if (documentReviewPath != null) { documentReviewPath = null; ownedFiles.discard() } else discard()
    }

    DisposableEffect(session) {
        onDispose { videoHoldRequest?.close(); ownedFiles.close(); timerJob?.cancel(); bindJob?.cancel(); session.dispose(); faceDetection.dispose(); lensManager.dispose() }
    }

    Box(Modifier.fillMaxSize()) {
        if (documentReviewPath != null) {
            InlineDocumentReview(documentReviewPath!!,
                onRetake = { documentReviewPath = null; ownedFiles.discard() },
                onCropped = { path -> if(ownedFiles.own(File(path))) { documentReviewPath = null; stagePickedOrCaptured(path) } },
            )
        } else if (videoOpen && videoAuthPort != null && cameraPermissionGranted) {
            BackHandler { videoOpen = false }
            VideoCaptureHost(
                selector = if (session.lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                stabilization = options.stabilization,
                authPort = videoAuthPort,
                holdRequest = videoHoldRequest,
                onUseVideo = null, // Local recording only. No durable video send is connected.
                onAuthInvalidated = onDismiss,
                onBackToPhoto = { videoOpen = false;captureViewMode=CaptureViewMode.photo },
            )
        } else if (capturedPath == null) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            CameraCaptureViewfinder(
                phase = phase,
                errorMessage = errorMessage,
                captureViewMode = captureViewMode,
                effect = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) EffectRegistry.allEffects.first { it.id == "none" } else engine.activeEffect,
                effectIntensity = engine.intensity,
                segmentPreview = segmentPreviewFrame,
                onPreviewView = { pv ->
                    previewView = pv
                    var touchDownX = 0f
                    var touchDownY = 0f
                    pv.setOnTouchListener { _, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                touchDownX = event.x
                                touchDownY = event.y
                            }
                            android.view.MotionEvent.ACTION_UP -> if (phase == CameraPhase.ready) {
                                val dx = event.x - touchDownX
                                val dy = event.y - touchDownY
                                val swipeThreshold = 56 * pv.resources.displayMetrics.density
                                // Lenses tab only: a decisive horizontal swipe on the
                                // live preview cycles lenses (left = next, right =
                                // previous); an ordinary tap still focuses.
                                val lensMode = captureViewMode == CaptureViewMode.lenses
                                if (lensMode && kotlin.math.abs(dx) > swipeThreshold &&
                                    kotlin.math.abs(dx) > 1.6f * kotlin.math.abs(dy)) {
                                    val catalog = kBuiltInLenses
                                    if (catalog.isNotEmpty()) {
                                        val current = catalog.indexOfFirst { it.id == lensManager.selected.id }
                                            .let { if (it >= 0) it else 0 }
                                        val next = if (dx < 0f) (current + 1) % catalog.size
                                            else (current - 1 + catalog.size) % catalog.size
                                        lensManager.select(catalog[next].id)
                                    }
                                } else {
                                    val point = pv.meteringPointFactory.createPoint(event.x, event.y)
                                    val action = androidx.camera.core.FocusMeteringAction.Builder(point)
                                        .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS).build()
                                    session.camera?.cameraControl?.startFocusAndMetering(action)
                                }
                                pv.performClick()
                            }
                        }
                        true
                    }
                },
                onViewport = { viewportSize = it },
                lens = lensManager.selected,
                facePoses = facePoses,
                faceHealth = faceHealth,
                flashOn = flashOn,
                flashSupported = flashSupported,
                previewAspect = options.aspect.portraitRatio,
                effectsOpen = effectsOpen,
                onClose = onDismiss,
                onToggleOptions = { optionsOpen = !optionsOpen; if(optionsOpen)effectsOpen=false },
                onToggleFlash = ::toggleFlash,
                hdrOn = hdrOn,
                hdrSupported = hdrSupported && captureViewMode in listOf(CaptureViewMode.photo, CaptureViewMode.lenses) && !nightActive && !portraitActive,
                onToggleHdr = { hdrOn = !hdrOn },
                onToggleEffects = {
                    ChatyHapticsPort.selection(view)
                    effectsOpen = !effectsOpen
                    if(effectsOpen)optionsOpen=false
                },
                onOpenSettings = { ChatyPermissions.openAppSettings(context) },
                onPickFromGallery = ::pickFromGallery,
            )
            if (options.grid || options.level) CameraGridAndLevel(options.grid, options.level,
                Modifier.align(Alignment.Center).then(options.aspect.portraitRatio?.let { Modifier.fillMaxWidth().aspectRatio(it) } ?: Modifier.fillMaxSize()))

                // Full-bleed preview remains mounted beneath bounded, safe chrome.
                androidx.compose.foundation.layout.Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 64.dp, start = 12.dp, end = 12.dp)
                        .heightIn(max = (maxHeight - 288.dp).coerceIn(48.dp, 350.dp)),
                ) {
                    if (optionsOpen) CameraOptionsPanel(options, stabilizationSupported, nightSupported, { next -> options = next; if(next.night)captureViewMode=CaptureViewMode.night else if(captureViewMode==CaptureViewMode.night)captureViewMode=CaptureViewMode.photo }, { optionsOpen = false })
                    scannerError?.let { message -> androidx.compose.material3.TextButton(onClick = { scannerError = null }) {
                        androidx.compose.material3.Text(message + "  Dismiss", color = androidx.compose.ui.graphics.Color.White)
                    } }
                }
                if (countdown > 0) androidx.compose.material3.Text("$countdown", modifier = Modifier.align(Alignment.Center),
                    color = androidx.compose.ui.graphics.Color.White, fontSize = 42.sp)
                androidx.compose.foundation.layout.Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .35f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                if (portraitActive) androidx.compose.material3.Text("Portrait · device background blur",color=androidx.compose.ui.graphics.Color.White)
                if (nightActive) androidx.compose.material3.Text("Night · hold still while capturing",color=androidx.compose.ui.graphics.Color.White)
                CameraZoomControls(zoomRatio,minZoom,maxZoom,enabled = phase == CameraPhase.ready && !capturing) { requested ->
                    val control = session.camera?.cameraControl
                    if (control != null) {
                        val result = control.setZoomRatio(clampCameraZoom(requested,minZoom,maxZoom))
                        result.addListener({ try { result.get(); zoomRatio = clampCameraZoom(requested,minZoom,maxZoom) }
                            catch (_: Exception) { scannerError = "Zoom is unavailable on this camera." }
                        }, ContextCompat.getMainExecutor(context))
                    }
                }
            if (lensBrowserOpen) {
                // Lens explorer: shares this screen's LensManager so selection,
                // recents and favorites stay in sync with the carousel.
                LensBrowserHost(
                    manager = lensManager,
                    onDismiss = { lensBrowserOpen = false },
                    deviceTier = deviceTier,
                )
            }
            CameraCaptureBottomControls(
                phase = phase,
                captureViewMode = captureViewMode,
                effectsOpen = effectsOpen,
                capturing = capturing,
                nightSupported = nightSupported,
                portraitSupported = portraitSupported,
                videoSupported = videoAuthPort != null,
                engine = engine,
                lensManager = lensManager,
                faceHealth = faceHealth,
                deviceTier = deviceTier,
                lensIdForEffect = ::lensIdForEffect,
                onBrowseLenses = { lensBrowserOpen = true },
                onSelectEffect = { effect ->
                    ChatyHapticsPort.selection(view)
                    if (effect.isFaceTrackingRequired) {
                        lensIdForEffect(effect.id)?.let { lensManager.select(it) }
                        effectsOpen = false
                    } else {
                        engine.selectEffect(effect)
                    }
                },
                onSetViewMode = { m ->
                    if(!capturing && countdown==0) {
                        optionsOpen=false
                        options=options.copy(night=false)
                        if(m==CaptureViewMode.video && videoAuthPort!=null) {
                            timerJob?.cancel();countdown=0;bindJob?.cancel();session.unbind()
                            effectsOpen=false;captureViewMode=m;videoOpen=true
                        } else if((m!=CaptureViewMode.night||nightSupported)&&(m!=CaptureViewMode.portrait||portraitSupported)) {
                            ChatyHapticsPort.selection(view);captureViewMode=m
                            // Color filters open only from the filters button - the
                            // lenses tab shows the lens carousel, never the tray.
                            effectsOpen=false
                            faceSolver.reset();facePoses=emptyList()
                        }
                    }
                },
                onShutter = ::shutter,
                onFlip = ::flipCamera,
                onPickFromGallery = ::pickFromGallery,
            )
                }
            }
        } else {
            CameraCaptureConfirmPane(
                path = capturedPath!!,
                mode = mode,
                contactName = contactName,
                effect = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) EffectRegistry.allEffects.first { it.id == "none" } else engine.activeEffect,
                effectIntensity = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) 1.0 else engine.intensity,
                caption = caption,
                onCaptionChange = { caption = it },
                onDiscard = ::discard,
                onConfirm = ::confirmAndSend,
            )
        }
    }
}
