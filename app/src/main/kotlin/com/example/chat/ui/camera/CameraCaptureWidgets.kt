package com.example.chat.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import java.io.File
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
    onPreviewView: (PreviewView) -> Unit,
    onViewport: (LensSize) -> Unit,
    lens: LensDefinition,
    facePoses: List<FacePose>,
    faceHealth: TrackingHealth,
    flashOn: Boolean,
    effectsOpen: Boolean,
    onClose: () -> Unit,
    onToggleFlash: () -> Unit,
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
            CameraPhase.ready -> {
                // ClipRect + OverflowBox + FittedBox(cover) -> FILL_CENTER.
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            onPreviewView(this)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(effectMatrixModifier(effect, effectIntensity)),
                )
            }
        }
        if (phase == CameraPhase.ready && captureViewMode == CaptureViewMode.photo) {
            LensOverlay(lens = lens, poses = facePoses, health = faceHealth)
        }
        if (phase == CameraPhase.ready && captureViewMode == CaptureViewMode.document) {
            DocumentGuideOverlay(Modifier.fillMaxSize())
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
                CameraCaptureRoundAction(
                    label = if (flashOn) "Turn flash off" else "Turn flash on",
                    kind = "flash",
                    onClick = onToggleFlash,
                )
                Spacer(Modifier.width(10.dp))
                if (captureViewMode == CaptureViewMode.photo) {
                    CameraCaptureRoundAction(
                        label = if (effectsOpen) "Hide color filters" else "Show color filters",
                        kind = "filters",
                        highlighted = effectsOpen || effectActive(effect),
                        onClick = onToggleEffects,
                    )
                }
            }
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
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (highlighted) Color.White.copy(alpha = 0.28f) else Color(0x3DFFFFFF))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CameraGlyph(kind = kind, color = Color.White, size = 21.dp)
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
            OutlinedButton(onClick = onAction) {
                CameraGlyph(kind = "filters", color = Color.White, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, color = Color.White)
            }
        }
    }
}

@Composable
fun CameraCaptureBottomControls(
    phase: CameraPhase,
    captureViewMode: CaptureViewMode,
    effectsOpen: Boolean,
    capturing: Boolean,
    engine: EffectEngine,
    lensManager: LensManager,
    faceHealth: TrackingHealth,
    deviceTier: DeviceCapabilityTier,
    lensIdForEffect: (String) -> String?,
    onSelectEffect: (ChatyCameraEffect) -> Unit,
    onSetViewMode: (CaptureViewMode) -> Unit,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    val ready = phase == CameraPhase.ready
    val configuration = LocalConfiguration.current
    val tight = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 340 ||
        configuration.screenHeightDp < 680
    val allEffects = EffectRegistry.allEffects

    Box(
        Modifier
            .fillMaxSize()
            .padding(),
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
                .navigationBarsPadding()
                .padding(bottom = (if (tight) 6 else 12).dp, top = (if (tight) 8 else 14).dp),
        ) {
            if (ready && captureViewMode == CaptureViewMode.photo) {
                // Lens name + tracking health row.
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
                Spacer(Modifier.height((if (tight) 1 else 4).dp))
                LensCarousel(manager = lensManager, deviceTier = deviceTier)
            }
            if (ready && captureViewMode == CaptureViewMode.document) {
                Text(
                    "DOCUMENT  •  Fit the page inside the frame",
                    color = Color.White,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 7.dp),
                )
            }
            if (ready && effectsOpen && captureViewMode == CaptureViewMode.photo) {
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
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clickable { onSelectEffect(effect) },
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
            // Mode selector.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (if (tight) 30 else 62).dp),
            ) {
                CameraCaptureModeButton(
                    label = "Photo",
                    document = false,
                    selected = captureViewMode == CaptureViewMode.photo,
                    tight = tight,
                    onClick = { onSetViewMode(CaptureViewMode.photo) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                CameraCaptureModeButton(
                    label = "Document",
                    document = true,
                    selected = captureViewMode == CaptureViewMode.document,
                    tight = tight,
                    onClick = { onSetViewMode(CaptureViewMode.document) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height((if (tight) 4 else 8).dp))
            // Gallery / shutter / flip.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (if (tight) 16 else 24).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size((if (tight) 46 else 52).dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPickFromGallery)
                        .semantics { contentDescription = "Pick image from gallery" },
                    contentAlignment = Alignment.Center,
                ) {
                    CameraGlyph(kind = "gallery", color = Color.White, size = 27.dp)
                }
                CameraCaptureShutter(enabled = ready, capturing = capturing, onClick = onShutter)
                Box(
                    modifier = Modifier
                        .size((if (tight) 46 else 52).dp)
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

@Composable
private fun CameraCaptureModeButton(
    label: String,
    document: Boolean,
    selected: Boolean,
    tight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height((if (tight) 34 else 38).dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = if (selected) Color.White else Color(0x4DFFFFFF),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = (if (tight) 9 else 14).dp)
            .semantics { contentDescription = "$label camera mode" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraGlyph(
            kind = if (document) "document" else "camera",
            color = if (selected) Color.Black else Color.White,
            size = 16.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
        )
    }
}

/** _buildShutter: 76dp ring (4dp white border, 5dp gap), spinner while
 *  capturing, filled inner circle otherwise. */
@Composable
private fun CameraCaptureShutter(enabled: Boolean, capturing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp)
            .semantics { contentDescription = "Take photo" },
        contentAlignment = Alignment.Center,
    ) {
        if (capturing) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.padding(16.dp),
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
                    kind = if (isChat) "filters" else "camera",
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
