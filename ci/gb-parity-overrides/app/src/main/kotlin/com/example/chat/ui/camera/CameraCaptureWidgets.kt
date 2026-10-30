package com.example.chat.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max

/** UI halves of camera_capture_screen.dart (viewfinder, top bar, bottom
 *  controls, mode selector, shutter, confirm pane, no-camera pane). All
 *  layout constants transliterated from the Dart; tight breakpoint:
 *  shortestSide < 340 || height < 680. */

@Composable
fun CameraCaptureViewfinder(
    phase: CameraPhase,
    errorMessage: String?,
    captureViewMode: CaptureViewMode,
    effect: ChatyCameraEffect,
    effectIntensity: Double,
    segmentPreview: android.graphics.Bitmap? = null,
    onPreviewView: (PreviewView) -> Unit,
    onViewport: (LensSize) -> Unit,
    lens: LensDefinition,
    facePoses: List<FacePose>,
    faceHealth: TrackingHealth,
    flashOn: Boolean,
    flashSupported: Boolean = true,
    previewAspect: Float? = null,
    effectsOpen: Boolean,
    onClose: () -> Unit,
    onToggleOptions: () -> Unit = {},
    onToggleFlash: () -> Unit,
    hdrOn: Boolean = false,
    hdrSupported: Boolean = false,
    onToggleHdr: () -> Unit = {},
    onToggleEffects: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned {
                onViewport(LensSize(it.size.width.toFloat(), it.size.height.toFloat()))
            },
    ) {
        // Mount the surface before binding. Waiting for ready here deadlocks:
        // ready itself requires a PreviewView surface to bind the camera.
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    contentDescription = "Camera preview surface"
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    onPreviewView(this)
                }
            },
            modifier = Modifier.align(Alignment.Center)
                .then(if (previewAspect != null) Modifier.fillMaxWidth().aspectRatio(previewAspect) else Modifier.fillMaxSize())
                .then(effectRenderModifier(effect, effectIntensity)),
        )
        // Live segmented background preview (bg_*): the composited analysis
        // frame covers the raw preview surface while a segment effect is
        // active. Best effort - when no composited frame exists yet (or the
        // segmenter fails open) the unfiltered preview shows through.
        if (segmentPreview != null && effect.shaderKey?.startsWith("segment_") == true) {
            Image(
                bitmap = segmentPreview.asImageBitmap(),
                contentDescription = "Live background filter preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.Center)
                    .then(if (previewAspect != null) Modifier.fillMaxWidth().aspectRatio(previewAspect) else Modifier.fillMaxSize()),
            )
        }
        when (phase) {
            CameraPhase.initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xB3FFFFFF))
            }
            CameraPhase.permissionDenied -> CameraCaptureNoCameraPane(
                title = "Camera permission needed",
                message = "Allow Chaty to use the camera to take photos and share them instantly.",
                actionLabel = "Open settings",
                onAction = onOpenSettings,
            )
            CameraPhase.noCamera -> CameraCaptureNoCameraPane(
                title = "Camera unavailable",
                message = errorMessage
                    ?: "No usable camera was found on this device. You can still share from your gallery.",
                actionLabel = "Pick from gallery",
                onAction = onPickFromGallery,
            )
            CameraPhase.ready -> Unit
        }
        if (phase == CameraPhase.ready && (captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses)) {
            LensOverlay(lens = lens, poses = facePoses, health = faceHealth)
        }
        if (phase == CameraPhase.ready && captureViewMode == CaptureViewMode.document) {
            // Paper detection/corner review belongs to the real scanner flow,
            // not a decorative viewfinder frame pretending detection.
        }
        // Top bar: top = statusBar + 8, horizontal 8.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraCaptureRoundAction(label = "Close camera", kind = "close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            if (phase == CameraPhase.ready) {
                if (flashSupported) CameraCaptureRoundAction(
                    label = if (flashOn) "Turn flash off" else "Turn flash on",
                    kind = "flash",
                    onClick = onToggleFlash,
                )
                if (hdrSupported) {
                    Spacer(Modifier.width(10.dp))
                    CameraCaptureRoundAction(
                        label = if (hdrOn) "Turn HDR off" else "Turn HDR on",
                        kind = "hdr",
                        highlighted = hdrOn,
                        onClick = onToggleHdr,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if ((captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses)) {
                    CameraCaptureRoundAction(
                        label = "Color filters",
                        actionState = if(effectsOpen) "Expanded" else "Collapsed",
                        kind = "filters",
                        highlighted = effectsOpen || effectActive(effect),
                        onClick = onToggleEffects,
                    )
                }
            }
            CameraCaptureRoundAction(label = "Camera settings", kind = "settings", onClick = onToggleOptions)
        }
    }
}

private fun effectActive(effect: ChatyCameraEffect) = effect.id != "none"

/** _RoundIconAction: 42dp circle, white28 highlighted / white24, glyph 21. */
@Composable
private fun CameraCaptureRoundAction(
    label: String,
    kind: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    actionState: String? = null,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (highlighted) Color.White.copy(alpha = 0.28f) else Color(0x99000000))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; actionState?.let { stateDescription=it } },
        contentAlignment = Alignment.Center,
    ) {
        if (kind == "hdr") {
            androidx.compose.material3.Text(
                "HDR",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        } else {
            CameraGlyph(kind = kind, color = Color.White, size = 21.dp)
        }
    }
}

@Composable
private fun CameraCaptureNoCameraPane(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CameraGlyph(kind = "camera", color = Color(0x8AFFFFFF), size = 46.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.W800,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = Color(0x99FFFFFF),
                textAlign = TextAlign.Center,
                lineHeight = 19.5.sp,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = onAction) {
                CameraGlyph(kind = "filters", color = Color.White, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, color = Color.White)
            }
        }
    }
}

/** Recording accent: the user's design red (stop ring/glyph). Centralized so
 *  recording semantics stay red regardless of the configurable theme accent. */
internal val CameraRecordingRed = Color.Red

/** Active mode-strip text color: the reference camera (user video 2026-10-30)
 *  highlights the active mode in yellow; matches the user's zoom-preset yellow
 *  (CameraZoomControls 0xFFFFD54F) so there is one active-highlight token. */
internal val CameraModeActiveYellow = Color(0xFFFFD54F)

@Composable
fun CameraCaptureBottomControls(
    phase: CameraPhase,
    captureViewMode: CaptureViewMode,
    effectsOpen: Boolean,
    capturing: Boolean,
    nightSupported: Boolean = false,
    portraitSupported: Boolean = false,
    videoSupported: Boolean = false,
    videoActive: Boolean = false,
    videoRecording: Boolean = false,
    videoSeconds: Int = 0,
    videoPaused: Boolean = false,
    videoSnapshotAvailable: Boolean = false,
    engine: EffectEngine,
    lensManager: LensManager,
    faceHealth: TrackingHealth,
    deviceTier: DeviceCapabilityTier,
    lensIdForEffect: (String) -> String?,
    onSelectEffect: (ChatyCameraEffect) -> Unit,
    onSetViewMode: (CaptureViewMode) -> Unit,
    onShutter: () -> Unit,
    onTogglePause: () -> Unit = {},
    onSnapshot: () -> Unit = {},
    onFlip: () -> Unit,
    onPickFromGallery: () -> Unit,
    onBrowseLenses: () -> Unit = {},
) {
    val ready = phase == CameraPhase.ready
    val configuration = LocalConfiguration.current
    val tight = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 340 ||
        configuration.screenHeightDp < 680
    val allEffects = EffectRegistry.allEffects

    Box(
        Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xDE000000), Color.Black),
                    ),
                )
                .padding(bottom = (if (tight) 6 else 12).dp, top = (if (tight) 8 else 14).dp),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * .22f).dp)
                .semantics { if(ready && effectsOpen && captureViewMode in listOf(CaptureViewMode.photo,CaptureViewMode.lenses)) contentDescription="Camera effects tray" }
                .verticalScroll(rememberScrollState())) {
            if (ready && captureViewMode == CaptureViewMode.document) {
                Text(
                    "DOCUMENT · Capture, then adjust paper corners",
                    color = Color.White,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 7.dp),
                )
            }
            if (ready && effectsOpen && (captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses)) {
                // Effects tray: horizontal, items centered at rest, 66/74 tall.
                val listState = rememberLazyListState()
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(
                        horizontal = max(8.0, (configuration.screenWidthDp - 52) / 2.0).dp,
                    ),
                    modifier = Modifier.height((if (tight) 66 else 74).dp),
                ) {
                    items(allEffects.size) { index ->
                        val effect = allEffects[index]
                        // Honest availability: effects with no working renderer are
                        // disabled, never shown as working. Segmented background
                        // effects preview live (best effort) and bake at capture.
                        val unavailable = effect.colorMatrix == null && effect.shaderKey == null &&
                            !effect.isFaceTrackingRequired && effect.id != "none"
                        val lensId = lensIdForEffect(effect.id)
                        val isSelected = if (effect.isFaceTrackingRequired) {
                            lensId != null && lensManager.selected.id == lensId
                        } else {
                            engine.activeEffect.id == effect.id
                        }
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 0.88f,
                            animationSpec = tween(180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                            label = "effect-scale",
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 5.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (unavailable) 0.45f else 1f }
                                .semantics { if (unavailable) stateDescription = "Not available yet" }
                                .clickable(enabled = !unavailable) { onSelectEffect(effect) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((if (tight) 42 else 48).dp)
                                    .clip(CircleShape)
                                    .background(
                                        effect.previewColor.copy(alpha = if (isSelected) 0.95f else 0.4f),
                                    )
                                    .border(
                                        width = (if (isSelected) 2.5 else 1.2).dp,
                                        color = if (isSelected) Color.White else Color(0x4DFFFFFF),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                EffectGlyph(
                                    id = effect.id,
                                    category = effect.category,
                                    color = if (isSelected) Color.Black else Color.White,
                                    size = (if (tight) 20 else 22).dp,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                effect.name,
                                color = if (isSelected) Color.White else Color(0x99FFFFFF),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) {
                                    androidx.compose.ui.text.font.FontWeight.W700
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.W500
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width((if (tight) 56 else 64).dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height((if (tight) 4 else 9).dp))
            }
            }
            // Reference-grounded (Snap/IG recording frames): idle controls hide
            // while a video records; only the stop control and timer stay.
            if (!videoRecording) {
            // Snap-style text mode strip (product direction 2026-10-30): plain
            // text items, never capsule chips; the active mode is centered and
            // underlined. Tapping selects and centers; horizontal swipes only
            // scroll - selection never derives from scroll position, so the
            // strip can never change or revert a mode by itself. Fixed slot
            // width + symmetric content padding from the real viewport width
            // make animateScrollToItem land the active item exactly centered.
            val stripState = rememberLazyListState()
            val stripModes = listOf(CaptureViewMode.photo,CaptureViewMode.portrait,CaptureViewMode.night,CaptureViewMode.lenses,CaptureViewMode.video,CaptureViewMode.document)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slotWidth = (if (tight) 76 else 88).dp
                LaunchedEffect(captureViewMode, maxWidth) {
                    val idx = stripModes.indexOf(captureViewMode)
                    if (idx >= 0) stripState.animateScrollToItem(idx)
                }
                LazyRow(
                    state = stripState,
                    contentPadding = PaddingValues(horizontal = ((maxWidth - slotWidth) / 2).coerceAtLeast(0.dp)),
                    modifier = Modifier.fillMaxWidth().semantics{contentDescription="Camera modes"},
                ) {
                    items(stripModes.size) { i ->
                        val mode = stripModes[i]
                        val supported=when(mode){CaptureViewMode.portrait->portraitSupported;CaptureViewMode.night->nightSupported;CaptureViewMode.video->videoSupported;else->true}
                        val name=mode.name.replaceFirstChar{it.uppercase()}
                        CameraCaptureModeButton(label=if(supported)name else "$name unavailable",
                            selected=captureViewMode==mode,tight=tight,enabled=supported&&!capturing,onClick={onSetViewMode(mode)},modifier=Modifier.width(slotWidth))
                    }
                }
            }
            Spacer(Modifier.height((if (tight) 4 else 8).dp))
            }
            // The lenses tab activates the Snap-style lens UI; the color-filter
            // tray is a separate surface opened only from the filters button, so
            // lens UI never mixes with the open tray.
            if (ready && captureViewMode == CaptureViewMode.lenses && !effectsOpen) {
                // Lens name + tracking health row.
                Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        lensManager.selected.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                        letterSpacing = 0.15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (lensManager.selected.requiresFaceTracking &&
                                    faceHealth == TrackingHealth.tracked
                                ) Color(0xFF7CFAAE) else Color(0x61FFFFFF),
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (!lensManager.selected.requiresFaceTracking) "Swipe to explore"
                        else when (faceHealth) {
                            TrackingHealth.tracked -> "Face locked"
                            TrackingHealth.temporarilyLost -> "Reacquiring…"
                            TrackingHealth.lost -> "Find a face"
                        },
                        color = Color(0xB3FFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Lens explorer entry: browse grid beside the carousel (Snap-style).
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onBrowseLenses)
                        .semantics { contentDescription = "Browse all lenses" },
                    contentAlignment = Alignment.Center,
                ) {
                    CameraGlyph(kind = "browse_grid", color = Color.White, size = 20.dp)
                }
                }
                Spacer(Modifier.height((if (tight) 1 else 4).dp))
            }
            // Snap-style inline row (verified reference): gallery, shutter, then the
            // lens carousel scrolling on the SAME row, flip at the far edge. Gallery
            // and flip keep their existing capabilities and safe-area padding.
            // While recording, the stop control stays centered alone (reference).
            if (videoRecording) {
                // Reference recording row (user video 2026-10-30): the stop control
                // stays centered; pause/resume sits at the right edge.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CameraCaptureShutter(enabled = true, capturing = false, videoActive = true, recording = true, onClick = onShutter)
                    if (videoSnapshotAvailable) {
                        // Reference recording row: still snapshot button left of stop.
                        // Drawn only when the session really bound the 3-use-case
                        // combination - never a dead button.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = (if (tight) 12 else 16).dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color.White, CircleShape)
                                .clickable(onClick = onSnapshot)
                                .semantics { contentDescription = "Take snapshot while recording" },
                        ) {}
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = (if (tight) 12 else 16).dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePause)
                            .semantics { contentDescription = if (videoPaused) "Resume recording" else "Pause recording" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (videoPaused) {
                            androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                                val w = size.width; val h = size.height
                                drawPath(androidx.compose.ui.graphics.Path().apply {
                                    moveTo(w * .25f, h * .12f); lineTo(w * .25f, h * .88f); lineTo(w * .88f, h * .5f); close()
                                }, color = Color.White)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                                Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                            }
                        }
                    }
                }
            } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (if (tight) 12 else 16).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size((if (tight) 48 else 52).dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPickFromGallery)
                        .semantics { contentDescription = "Pick image from gallery" },
                    contentAlignment = Alignment.Center,
                ) {
                    CameraGlyph(kind = "gallery", color = Color.White, size = 27.dp)
                }
                CameraCaptureShutter(enabled = ready, capturing = capturing, videoActive = videoActive, recording = videoRecording, onClick = onShutter)
                Box(Modifier.weight(1f)) {
                    // Lenses tab only: the carousel rides the gallery/shutter/flip
                    // row and stays hidden while the filters tray is open. In every
                    // other mode the row keeps its geometry with the slot empty.
                    // A bottom-to-top drag here opens the lens browser; horizontal
                    // scrolling stays with the carousel and taps still select, and
                    // preview tap-to-focus / pinch-zoom live on the viewfinder, so
                    // no gesture conflicts.
                    if (captureViewMode == CaptureViewMode.lenses && !effectsOpen) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .pointerInput(lensManager) {
                                    var draggedUp = 0f
                                    var opened = false
                                    detectVerticalDragGestures(
                                        onDragStart = { draggedUp = 0f; opened = false },
                                        onVerticalDrag = { _, dragAmount ->
                                            if (dragAmount < 0f) {
                                                draggedUp += -dragAmount
                                                if (!opened && draggedUp > 48.dp.toPx()) {
                                                    opened = true
                                                    onBrowseLenses()
                                                }
                                            }
                                        },
                                    )
                                },
                        ) {
                            LensCarousel(manager = lensManager, deviceTier = deviceTier)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size((if (tight) 48 else 52).dp)
                        .clip(CircleShape)
                        .clickable(enabled = ready, onClick = onFlip)
                        .semantics { contentDescription = "Switch camera" },
                    contentAlignment = Alignment.Center,
                ) {
                    CameraGlyph(
                        kind = "flip",
                        color = if (ready) Color.White else Color(0x4DFFFFFF),
                        size = 27.dp,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun CameraCaptureModeButton(
    label: String,
    selected: Boolean,
    tight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Plain text + active-only underline (Snap-style), 48dp touch target,
    // tab semantics preserved for the mode-strip proofs and TalkBack.
    Column(
        modifier = modifier
            .height(48.dp)
            .clearAndSetSemantics {
                contentDescription = "$label camera mode"
                this.selected = selected
                role = Role.Tab
                if(!enabled) disabled() else onClick { onClick(); true }
            }
            .clickable(enabled=enabled,onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) CameraModeActiveYellow else if(enabled) Color(0x99FFFFFF) else Color(0x4DFFFFFF),
            fontSize = (if (tight) 12 else 13).sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) CameraModeActiveYellow else Color.Transparent),
        )
    }
}

/** _buildShutter: 76dp ring (4dp white border, 5dp gap), spinner while
 *  capturing, filled inner circle otherwise. */
@Composable
private fun CameraCaptureShutter(enabled: Boolean, capturing: Boolean, videoActive: Boolean = false, recording: Boolean = false, onClick: () -> Unit) {
    // Reference (user video 2026-10-30): white ring in every state; photo fill is
    // white, video idle is a red filled circle, recording is a red rounded square.
    // Size stays 76dp so the row geometry is stable.
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp)
            .semantics { contentDescription = if (videoActive) { if (recording) "Stop recording" else "Record video" } else "Take photo" },
        contentAlignment = Alignment.Center,
    ) {
        if (capturing) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.padding(16.dp),
            )
        } else if (recording) {
            // Reference-grounded stop affordance: rounded square inside the ring.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CameraRecordingRed),
            )
        } else if (videoActive) {
            // Reference (user video 2026-10-30): video idle shows a red filled
            // shutter inside the white ring.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (enabled) CameraRecordingRed else Color(0x61FF0000)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else Color(0x61FFFFFF)),
            )
        }
    }
}

/** Top-center recording time (user reference video: red dot + white
 *  "HH:MM:SS" text, top-center, no pill). */
@Composable
fun CameraCaptureTimerTop(seconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Recording time" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(CameraRecordingRed))
        Spacer(Modifier.width(6.dp))
        Text(
            String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
        )
    }
}

/** _buildConfirmPane: zoomable preview with the stored effect applied exactly
 *  like receivers render it, caption field, discard + confirm. */
@Composable
fun CameraCaptureConfirmPane(
    path: String,
    mode: ChatyCaptureMode,
    contactName: String?,
    effect: ChatyCameraEffect,
    effectIntensity: Double,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onDiscard: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isChat = mode == ChatyCaptureMode.chat
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // InteractiveViewer(maxScale: 4)
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            // Dart InteractiveViewer(maxScale: 4, boundaryMargin: zero,
            // constrained: true): the transform is about the child's TOP-LEFT
            // origin (Flutter Transform default), pinch zoom keeps the gesture
            // focal point fixed, and the child AABB is clamped inside the
            // viewport every update. Compose graphicsLayer defaults to a
            // CENTER pivot, so the pivot is pinned to top-left explicitly and
            // the gesture math lives in confirmPaneZoomPan (JVM-tested).
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val step = confirmPaneZoomPan(
                                offset = offset,
                                centroid = centroid,
                                pan = pan,
                                scale = scale,
                                zoom = zoom,
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat(),
                            )
                            scale = step.first
                            offset = step.second
                        }
                    }
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center,
            ) {
                ApplyStoredEffect(effectId = effect.id, intensity = effectIntensity) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDiscard)
                    .semantics { contentDescription = "Discard photo" },
                contentAlignment = Alignment.Center,
            ) {
                CameraGlyph(kind = "close", color = Color.White, size = 26.dp)
            }
            Spacer(Modifier.width(10.dp))
            TextField(
                value = caption,
                onValueChange = onCaptionChange,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 2,
                placeholder = {
                    Text(
                        if (isChat && contactName != null) "Message $contactName…" else "Add a caption…",
                        color = Color(0x8AFFFFFF),
                    )
                },
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x1FFFFFFF),
                    unfocusedContainerColor = Color(0x1FFFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onConfirm)
                    .semantics {
                        contentDescription = if (isChat) "Send photo" else "Post to my status"
                    },
                contentAlignment = Alignment.Center,
            ) {
                CameraGlyph(
                    kind = "send",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    size = 24.dp,
                )
            }
        }
    }
}


/** InteractiveViewer pan clamp with zero boundaryMargin over a
 *  viewport-constrained child (top-left pivot): offset stays in
 *  [viewport * (1-scale), 0] per axis. Pure for JVM tests. */
fun clampConfirmPanePan(
    offset: Offset,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Offset {
    val minX = viewportWidth * (1f - scale)
    val minY = viewportHeight * (1f - scale)
    return Offset(
        offset.x.coerceIn(minX, 0f),
        offset.y.coerceIn(minY, 0f),
    )
}


/** One InteractiveViewer scale/pan update: zoom clamps to [1,4], the gesture
 *  centroid stays fixed on screen (Flutter scales the matrix about the focal
 *  point), pan accumulates, then the result is boundary-clamped with
 *  [clampConfirmPanePan]. Returns (newScale, newOffset). Pure for JVM tests. */
fun confirmPaneZoomPan(
    offset: Offset,
    centroid: Offset,
    pan: Offset,
    scale: Float,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Pair<Float, Offset> {
    val newScale = (scale * zoom).coerceIn(1f, 4f)
    // Keep the centroid fixed: offset' = c + (offset - c) * (s'/s) + pan.
    val factor = newScale / scale
    val unclamped = Offset(
        centroid.x + (offset.x - centroid.x) * factor + pan.x,
        centroid.y + (offset.y - centroid.y) * factor + pan.y,
    )
    return newScale to clampConfirmPanePan(
        unclamped, newScale, viewportWidth, viewportHeight,
    )
}
