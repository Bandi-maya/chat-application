package com.example.chat.ui.settings.appicon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Exact port of _CustomBrandIconEditor from
 *  lib/features/settings/appearance/app_icon_settings_screen.dart.
 *
 *  InteractiveViewer(transformationController, minScale: 1, maxScale: 6,
 *  boundaryMargin: EdgeInsets.all(80)): Flutter transforms about the child
 *  top-left origin, zooms about the gesture focal point, and clamps the
 *  child AABB inside the viewport INFLATED by the 80px margin each update.
 *  Compose graphicsLayer defaults to a center pivot, so the pivot is pinned
 *  top-left and the gesture math lives in [iconEditorGestureStep]
 *  (JVM-tested).
 *
 *  Capture: Dart RepaintBoundary.toImage(pixelRatio: 3) rasterizes exactly
 *  what is on screen - the cover-fit, rotated, user-transformed image inside
 *  the square preview. [renderCroppedIconPng] reproduces that raster
 *  deterministically with android.graphics (JVM-tested math in
 *  [coverFitDestination]); it does not depend on Compose capture APIs. */

/** One InteractiveViewer update with symmetric boundary margin. Range per
 *  axis: [viewport * (1 - scale) - margin, margin]. */
fun iconEditorGestureStep(
    offset: Offset,
    centroid: Offset,
    pan: Offset,
    scale: Float,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    margin: Float,
): Pair<Float, Offset> {
    val newScale = (scale * zoom).coerceIn(1f, 6f)
    val factor = newScale / scale
    val unclamped = Offset(
        centroid.x + (offset.x - centroid.x) * factor + pan.x,
        centroid.y + (offset.y - centroid.y) * factor + pan.y,
    )
    val minX = viewportWidth * (1 - newScale) - margin
    val minY = viewportHeight * (1 - newScale) - margin
    return newScale to Offset(
        unclamped.x.coerceIn(minX, margin),
        unclamped.y.coerceIn(minY, margin),
    )
}

/** ContentScale.Crop of an image into a square box: scale to fill, center
 *  the overflow. Returns the destination rect inside the box. */
fun coverFitDestination(
    imageWidth: Float,
    imageHeight: Float,
    boxSide: Float,
): RectF {
    val factor = maxOf(boxSide / imageWidth, boxSide / imageHeight)
    val drawnWidth = imageWidth * factor
    val drawnHeight = imageHeight * factor
    val left = (boxSide - drawnWidth) / 2f
    val top = (boxSide - drawnHeight) / 2f
    return RectF(left, top, left + drawnWidth, top + drawnHeight)
}

/** Reproduces RepaintBoundary.toImage(pixelRatio: 3) for the editor content:
 *  a [previewSize]x[previewSize] canvas at 3x density, image drawn cover-fit
 *  (centered), rotated [quarterTurns]*90 about the box center (RotatedBox),
 *  then the user transform (top-left pivot scale + translate). The
 *  ColoredBox background is transparent here on purpose: the Dart
 *  RepaintBoundary rasterizes with the theme surface color behind, but the
 *  downstream processor center-crops and re-canvases onto transparency, so
 *  only the image pixels matter; the square frame bounds the raster. */
fun renderCroppedIconPng(
    imageBytes: ByteArray,
    previewSize: Int,
    scale: Float,
    offset: Offset,
    quarterTurns: Int,
): ByteArray {
    val source = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        ?: throw IllegalStateException("Could not encode image.")
    try {
        val densityScale = 3f
        val side = previewSize * densityScale.toInt()
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        canvas.save()
        canvas.translate(offset.x * densityScale, offset.y * densityScale)
        canvas.scale(scale, scale)
        canvas.rotate(quarterTurns * 90f, side / 2f, side / 2f)
        val dst = coverFitDestination(source.width.toFloat(), source.height.toFloat(), side.toFloat())
        canvas.drawBitmap(source, Rect(0, 0, source.width, source.height), dst, paint)
        canvas.restore()

        val stream = java.io.ByteArrayOutputStream()
        if (!out.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
            out.recycle()
            throw IllegalStateException("Could not encode image.")
        }
        out.recycle()
        val png = stream.toByteArray()
        if (png.isEmpty()) throw IllegalStateException("Could not encode image.")
        return png
    } finally {
        source.recycle()
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun CustomBrandIconEditor(
    imageBytes: ByteArray,
    controller: AppIconController,
    onClose: () -> Unit,
    showToast: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var quarterTurns by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // Dart boundaryMargin: EdgeInsets.all(80) in physical px.
    val marginPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }

    // Dart _reset: Matrix4.identity() + quarterTurns = 0.
    fun reset() {
        scale = 1f
        offset = Offset.Zero
        quarterTurns = 0
    }

    fun apply() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val side = viewport.width
                if (side <= 0) throw IllegalStateException("Preview is not ready.")
                val png = withContext(Dispatchers.Default) {
                    renderCroppedIconPng(
                        imageBytes = imageBytes,
                        previewSize = side,
                        scale = scale,
                        offset = offset,
                        quarterTurns = quarterTurns,
                    )
                }
                val success = controller.saveCustomBrandIcon(png)
                if (success) {
                    onClose()
                } else {
                    showToast(controller.lastError ?: "Could not save custom icon.")
                }
            } catch (_: Exception) {
                showToast("Could not create the cropped icon.")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust custom icon") },
                actions = {
                    TextButton(onClick = { if (!saving) reset() }) { Text("Reset") }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp)
                .fillMaxSize(),
        ) {
            BoxWithConstraints(Modifier.weight(1f)) {
                // Dart: previewSize = min(screenWidth - 40, 360). Horizontal
                // padding is already applied by the Column, so maxWidth is
                // the available width.
                val previewDp = minOf(maxWidth, 360.dp)
                Column {
                    Text(
                        "Move and zoom the image inside the square. Launchers may apply circle, squircle, or rounded-square masks.",
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(previewDp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(scheme.surfaceVariant)
                                .onSizeChanged { viewport = it }
                                .pointerInput(Unit) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val step = iconEditorGestureStep(
                                            offset = offset,
                                            centroid = centroid,
                                            pan = pan,
                                            scale = scale,
                                            zoom = zoom,
                                            viewportWidth = size.width.toFloat(),
                                            viewportHeight = size.height.toFloat(),
                                            margin = marginPx,
                                        )
                                        scale = step.first
                                        offset = step.second
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Outer Box: InteractiveViewer matrix (top-left
                            // pivot). Inner image: RotatedBox equivalent
                            // (default CENTER pivot for the rotation).
                            Box(
                                Modifier.graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                            ) {
                                coil.compose.AsyncImage(
                                    model = imageBytes,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(previewDp)
                                        .graphicsLayer { rotationZ = quarterTurns * 90f },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { if (!saving) quarterTurns = (quarterTurns + 3) % 4 },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(),
                        ) { Icon(Icons.Rounded.RotateLeft, contentDescription = "Rotate left") }
                        Spacer(Modifier.width(12.dp))
                        IconButton(
                            onClick = { if (!saving) quarterTurns = (quarterTurns + 1) % 4 },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(),
                        ) { Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate right") }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledButton(
                        onClick = { if (!saving) apply() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                color = scheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (saving) "Saving…" else "Add & apply icon")
                    }
                }
            }
        }
    }
}
