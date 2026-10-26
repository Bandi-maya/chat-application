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

enum class CaptureViewMode { photo, document }

/** Result handed back to the host when the user confirms. */
data class ChatyCaptureResult(
    val path: String,
    val effectId: String,
    val effectIntensity: Double,
    val caption: String = "",
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

    fun bind(
        previewView: PreviewView,
        onFrame: (FrameData, Int) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit,
    ) {
        val provider = provider ?: return
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        unbind()
        val selector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val info = provider.getCameraInfo(selector)
        flashSupported = info.hasFlashUnit()
        stabilizationSupported = Preview.getPreviewCapabilities(info).isStabilizationSupported
        val preview = Preview.Builder().apply {
            if (stabilizationSupported) setPreviewStabilizationEnabled(stabilization)
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
                onFrame(proxy.toFrameData(), proxy.imageInfo.rotationDegrees)
            } finally {
                proxy.close()
            }
        }
        this.preview = preview
        imageCapture = capture
        analysis = analyzer
        try {
            val bound = try {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analyzer)
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
            onError(error.message ?: "The camera could not start.")
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
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val engine = remember { com.example.chat.ServiceLocator.effectEngine }
    val lensSchedulerScope = rememberCoroutineScope()
    val lensManager = remember {
        LensManager(
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
    val session = remember(context, lifecycleOwner) { CameraSession(context, lifecycleOwner) }

    var phase by remember { mutableStateOf(CameraPhase.initializing) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var effectsOpen by remember { mutableStateOf(false) }
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
    var videoOpen by remember { mutableStateOf(false) }
    var captureViewMode by remember { mutableStateOf(CaptureViewMode.photo) }
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
        captureViewMode == CaptureViewMode.photo &&
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

    fun bindCamera() {
        val pv = previewView ?: return
        if (!cameraPermissionGranted || capturedPath != null || scannerBusy || videoOpen) return
        bindJob?.cancel()
        bindJob = scope.launch {
            phase = CameraPhase.initializing
            errorMessage = null
            try {
                session.ensureProvider()
                session.bind(
                    previewView = pv,
                    onFrame = { frame, rotation -> processFaceFrame(frame, rotation) },
                    onError = { message ->
                        errorMessage = message
                        phase = CameraPhase.noCamera
                    },
                    onReady = {
                        phase = CameraPhase.ready
                        flashSupported = session.flashSupported
                        stabilizationSupported = session.stabilizationSupported
                        zoomRatio = session.camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
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
    LaunchedEffect(previewView, cameraPermissionGranted, capturedPath, scannerBusy, videoOpen, options.stabilization) {
        if (!cameraPermissionGranted) {
            session.unbind()
            phase = CameraPhase.permissionDenied
        } else if (capturedPath == null && !scannerBusy && !videoOpen) {
            session.stabilization = options.stabilization
            bindCamera()
        }
        else if (!videoOpen) session.unbind()
    }
    // Re-check after returning from Android app settings. A granted permission
    // must not leave this composition stranded in its old denied state.
    DisposableEffect(lifecycleOwner, session, capturedPath, scannerBusy, videoOpen) {
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
        capturedPath = path
        effectsOpen = false
    }

    val scanDocument = rememberDocumentScanner(
        onDocument = { path ->
            captureViewMode = CaptureViewMode.document
            stagePickedOrCaptured(path)
        },
        onBusy = { busy ->
            scannerBusy = busy
            if (busy) { timerJob?.cancel(); countdown = 0; session.unbind() }
        },
        onError = { scannerError = it },
    )

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
                    val dest = File(context.cacheDir, "chaty_picked_$stamp.jpg")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                    }
                    var selectedPath = dest.absolutePath
                    stagePickedOrCaptured(selectedPath)
                } catch (_: Exception) {
                    // Picker/copy failure leaves state untouched (Dart returns early).
                }
            }
        }
    }


    fun pickFromGallery() {
        if (captureViewMode == CaptureViewMode.document) { scanDocument(); return }
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
        ChatyHapticsPort.light(view)
        val stamp = System.currentTimeMillis()
        val out = File(context.cacheDir, "chaty_capture_$stamp.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(out).build()
        capture.takePicture(opts, session.captureExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                scope.launch {
                    var path = out.absolutePath
                    run {
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
                                val staged = File(context.cacheDir, "chaty_lens_$lensStamp.png")
                                staged.writeBytes(bytes)
                                val jpg = File(context.cacheDir, "chaty_lens_$lensStamp.jpg")
                                val ok = try {
                                    jpg.outputStream().use { os ->
                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            ?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os) == true
                                    }
                                } catch (_: Exception) { false }
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
                    try {
                        path = withContext(Dispatchers.IO) {
                            CameraPhotoOutput.process(context, path, captureOptions,
                                if (viewportSize.height > 0) viewportSize.width / viewportSize.height else 9f / 16f)
                        }
                    } catch (_: Exception) {
                        scannerError = "Could not apply capture options. Original photo kept; nothing was sent."
                    }
                    capturing = false
                    stagePickedOrCaptured(path)
                }
            }

            override fun onError(exception: ImageCaptureException) {
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
        if (captureViewMode == CaptureViewMode.document) { scanDocument(); return }
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
    }

    fun confirmAndSend() {
        ChatyHapticsPort.success(view)
        val raw = capturedPath ?: return
        val engineEffect = engine.activeEffect
        var path = raw
        var effectId = if (captureViewMode == CaptureViewMode.document) "none" else engineEffect.id
        var intensity = if (captureViewMode == CaptureViewMode.document) 1.0 else engine.intensity
        scope.launch {
            // Stories carry no metadata channel, so the look must live in the pixels.
            if (mode == ChatyCaptureMode.story &&
                captureViewMode != CaptureViewMode.document &&
                effectId != "none" &&
                engineEffect.colorMatrix != null
            ) {
                try {
                    path = ImageFxBaker.bakeEffectIntoFile(context, path, engineEffect, intensity)
                } catch (_: Exception) {
                    // Fall back to publishing the untouched capture.
                    effectId = "none"
                }
            }
            onResult(
                ChatyCaptureResult(
                    path = path,
                    effectId = effectId,
                    effectIntensity = intensity,
                    caption = caption.trim(),
                ),
            )
        }
    }

    // PopScope: back with a staged capture discards instead of leaving.
    BackHandler(enabled = capturedPath != null) { discard() }

    DisposableEffect(session) {
        onDispose { timerJob?.cancel(); bindJob?.cancel(); session.dispose(); faceDetection.dispose(); lensManager.dispose() }
    }

    Box(Modifier.fillMaxSize()) {
        if (videoOpen && onVideoResult != null) {
            BackHandler { videoOpen = false }
            VideoCaptureHost(
                selector = if (session.lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                stabilization = options.stabilization,
                onUseVideo = onVideoResult,
                onBackToPhoto = { videoOpen = false },
            )
        } else if (capturedPath == null) {
            CameraCaptureViewfinder(
                phase = phase,
                errorMessage = errorMessage,
                captureViewMode = captureViewMode,
                effect = if (captureViewMode == CaptureViewMode.document) EffectRegistry.allEffects.first { it.id == "none" } else engine.activeEffect,
                effectIntensity = engine.intensity,
                onPreviewView = { pv ->
                    previewView = pv
                    pv.setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP && phase == CameraPhase.ready) {
                            val point = pv.meteringPointFactory.createPoint(event.x, event.y)
                            val action = androidx.camera.core.FocusMeteringAction.Builder(point)
                                .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS).build()
                            session.camera?.cameraControl?.startFocusAndMetering(action)
                            pv.performClick()
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
                onToggleFlash = ::toggleFlash,
                onToggleEffects = {
                    ChatyHapticsPort.selection(view)
                    effectsOpen = !effectsOpen
                },
                onOpenSettings = { ChatyPermissions.openAppSettings(context) },
                onPickFromGallery = ::pickFromGallery,
            )
            CameraCaptureBottomControls(
                phase = phase,
                captureViewMode = captureViewMode,
                effectsOpen = effectsOpen,
                capturing = capturing,
                engine = engine,
                lensManager = lensManager,
                faceHealth = faceHealth,
                deviceTier = deviceTier,
                lensIdForEffect = ::lensIdForEffect,
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
                    if (m == CaptureViewMode.document) {
                        captureViewMode = m
                        optionsOpen = false
                        scanDocument()
                    }

                    if (captureViewMode != m) {
                        ChatyHapticsPort.selection(view)
                        captureViewMode = m
                        effectsOpen = false
                        if (!lensManager.selected.requiresFaceTracking) {
                            faceSolver.reset()
                            facePoses = emptyList()
                        }
                    }
                },
                onShutter = ::shutter,
                onFlip = ::flipCamera,
                onPickFromGallery = ::pickFromGallery,
            )
            if (options.grid || options.level) CameraGridAndLevel(options.grid, options.level,
                Modifier.align(Alignment.Center).then(options.aspect.portraitRatio?.let { Modifier.fillMaxWidth().aspectRatio(it) } ?: Modifier.fillMaxSize()))
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 60.dp),
            ) {
                androidx.compose.material3.TextButton(onClick = { optionsOpen = !optionsOpen }) {
                    androidx.compose.material3.Text("More settings", color = androidx.compose.ui.graphics.Color.White)
                }
                if (onVideoResult != null) androidx.compose.material3.TextButton(onClick = {
                    timerJob?.cancel(); countdown = 0; bindJob?.cancel(); session.unbind()
                    optionsOpen = false; videoOpen = true
                }, enabled = !capturing && !scannerBusy) {
                    androidx.compose.material3.Text("Video", color = androidx.compose.ui.graphics.Color.White)
                }
                if (optionsOpen) CameraOptionsPanel(options, stabilizationSupported, { next -> options = next }, { optionsOpen = false })
                if (scannerBusy) androidx.compose.material3.Text("Opening on-device scanner…", color = androidx.compose.ui.graphics.Color.White)
                scannerError?.let { message ->
                    androidx.compose.material3.TextButton(onClick = { scannerError = null }) {
                        androidx.compose.material3.Text(message + "  Dismiss", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (countdown > 0) androidx.compose.material3.Text("$countdown", color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 42.sp)
                androidx.compose.material3.TextButton(onClick = {
                    val camera = session.camera
                    val state = camera?.cameraInfo?.zoomState?.value
                    if (state != null) {
                        val target = if (zoomRatio >= 1.9f) 1f.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                            else 2f.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        val result = camera.cameraControl.setZoomRatio(target)
                        result.addListener({ try { result.get(); zoomRatio = target } catch (_: Exception) {
                            scannerError = "Zoom is unavailable on this camera."
                        } }, ContextCompat.getMainExecutor(context))
                    }
                }) { androidx.compose.material3.Text("${String.format(java.util.Locale.ROOT, "%.1f", zoomRatio)}x", color = androidx.compose.ui.graphics.Color.White) }
            }
        } else {
            CameraCaptureConfirmPane(
                path = capturedPath!!,
                mode = mode,
                contactName = contactName,
                effect = if (captureViewMode == CaptureViewMode.document) EffectRegistry.allEffects.first { it.id == "none" } else engine.activeEffect,
                effectIntensity = if (captureViewMode == CaptureViewMode.document) 1.0 else engine.intensity,
                caption = caption,
                onCaptionChange = { caption = it },
                onDiscard = ::discard,
                onConfirm = ::confirmAndSend,
            )
        }
    }
}
