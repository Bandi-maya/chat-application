package com.example.chat.ui.chats.detail

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Non-destructive editor. Apply returns a NEW cache file; no original or remote message is overwritten. */
@Composable
fun ImageEditorScreen(source: String, onCancel: () -> Unit, onApply: (File) -> Unit, outputFile: File? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false; saveJob?.cancel() } }
    fun cancelEditor() { saveJob?.cancel(); onCancel() }
    var saving by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var draw by remember { mutableStateOf(false) }
    var ink by remember { mutableStateOf(Color.Red) }
    var strokes by remember { mutableStateOf<List<EditorStroke>>(emptyList()) }
    LaunchedEffect(source) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                try {
                    val result = loader.execute(ImageRequest.Builder(context).data(source).allowHardware(false).size(2048)
                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED).build())
                    (result as? SuccessResult)?.drawable?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
                        ?: throw IllegalStateException("Unable to load image for editing")
                } finally { loader.shutdown() }
            }
            originalBitmap = bitmap
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Unable to load image" }
    }
    androidx.activity.compose.BackHandler(onBack = ::cancelEditor)
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(::cancelEditor) { Text("Cancel") }
            Text("Edit a copy", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val original = bitmap ?: return@TextButton
                saving = true
                saveJob = scope.launch {
                    val file = outputFile ?: File(context.cacheDir, "chaty_edited-${java.util.UUID.randomUUID()}.jpg")
                    var handedOff = false
                    val sourceUri = android.net.Uri.parse(source)
                    val aliasesOriginal = sourceUri.scheme == "file" && file.canonicalPath == File(sourceUri.path.orEmpty()).canonicalPath
                    try {
                        require(!aliasesOriginal) { "Output cannot overwrite the source." }
                        withContext(Dispatchers.IO) {
                            val edited = renderEditedImage(original, brightness, contrast, strokes)
                            try {
                                file.outputStream().use { check(edited.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
                                coroutineContext.ensureActive()
                            } finally { edited.recycle() }
                        }
                        coroutineContext.ensureActive()
                        if (active) { onApply(file); handedOff = true }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Could not save edit" }
                    finally { if (!handedOff && !aliasesOriginal) file.delete(); saving = false }
                }
            }, enabled = bitmap != null && !saving) { Text(if (saving) "Saving" else "Apply") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image == null) {
                if (error == null) CircularProgressIndicator() else Text(error!!, color = Color.White)
            } else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = image.width.toFloat() / image.height
                val width = minOf(maxWidth, maxHeight * ratio)
                Box(Modifier.width(width).aspectRatio(ratio)) {
                    Image(image.asImageBitmap(), "Image being edited", Modifier.fillMaxSize(), colorFilter = ColorFilter.colorMatrix(ColorMatrix(editorColorValues(brightness, contrast))))
                    Canvas(Modifier.fillMaxSize().pointerInput(draw, ink, image) {
                        if (draw) detectDragGestures(
                            onDragStart = { point -> strokes = strokes + EditorStroke(ink.toArgb().toLong(), listOf(Offset(point.x / size.width, point.y / size.height))) },
                            onDrag = { change, _ ->
                                change.consume()
                                if (strokes.isNotEmpty()) {
                                    val last = strokes.last()
                                    val p = Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                    strokes = strokes.dropLast(1) + last.copy(points = last.points + p)
                                }
                            },
                        )
                    }) {
                        strokes.forEach { stroke ->
                            val color = Color(stroke.color.toInt())
                            stroke.points.zipWithNext().forEach { (a, b) -> drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), size.width * 0.008f, StrokeCap.Round) }
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(8.dp)) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton({ draw = !draw }, enabled = !saving) { Text(if (draw) "Drawing on" else "Draw") }
            TextButton({ strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty() && !saving) { Text("Undo") }
            TextButton({
                bitmap?.let { image ->
                    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                    bitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
                    strokes = strokes.map { s -> s.copy(points = s.points.map { Offset(1f - it.y, it.x) }) }
                }
            }, enabled = bitmap != null && !saving) { Text("Rotate") }
            TextButton({ brightness = 0f; contrast = 1f; strokes = emptyList(); bitmap = originalBitmap }, enabled = !saving) { Text("Reset") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("Crop", color = Color.White, modifier = Modifier.padding(12.dp))
            listOf("Square" to 1f, "Portrait" to (3f / 4f), "Landscape" to (4f / 3f)).forEach { (label, ratio) ->
                TextButton(onClick = {
                    bitmap?.let { image ->
                        val crop = editorCenterCrop(image.width, image.height, ratio)
                        bitmap = Bitmap.createBitmap(image, crop.left, crop.top, crop.width(), crop.height())
                        // Existing drawing coordinates follow the crop. Clip discarded strokes at export.
                        strokes = strokes.map { stroke -> stroke.copy(points = stroke.points.map { point ->
                            Offset((point.x * image.width - crop.left) / crop.width(), (point.y * image.height - crop.top) / crop.height())
                        }) }
                    }
                }, enabled = bitmap != null && !saving) { Text(label) }
            }
        }
        if (draw) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Color.Red, Color.Yellow, Color.White, Color.Black, Color.Cyan).forEach { color ->
                TextButton({ ink = color }) { Text(if (ink == color) "●" else "○", color = color) }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Brightness", color = Color.White)
            Slider(brightness, { brightness = it }, valueRange = -0.4f..0.4f, enabled = !saving)
            Text("Contrast", color = Color.White)
            Slider(contrast, { contrast = it }, valueRange = 0.5f..1.8f, enabled = !saving)
        }
    }
}

internal data class EditorStroke(val color: Long, val points: List<Offset>)
internal fun editorColorValues(brightness: Float, contrast: Float): FloatArray {
    val shift = brightness * 255f + 128f * (1f - contrast)
    return floatArrayOf(contrast,0f,0f,0f,shift, 0f,contrast,0f,0f,shift, 0f,0f,contrast,0f,shift, 0f,0f,0f,1f,0f)
}
internal fun renderEditedImage(original: Bitmap, brightness: Float, contrast: Float, strokes: List<EditorStroke>): Bitmap {
    val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    canvas.drawBitmap(original, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(AndroidColorMatrix(editorColorValues(brightness, contrast))) })
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = original.width * 0.008f }
    strokes.forEach { stroke ->
        paint.color = stroke.color.toInt()
        stroke.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x * original.width, a.y * original.height, b.x * original.width, b.y * original.height, paint) }
    }
    return output
}

/** Centered preset crop, never upscales. Caller retains the original input file. */
internal fun editorCenterCrop(width: Int, height: Int, ratio: Float): android.graphics.Rect {
    require(width > 0 && height > 0 && ratio > 0f)
    val w = minOf(width, (height * ratio).toInt()).coerceAtLeast(1)
    val h = minOf(height, (width / ratio).toInt()).coerceAtLeast(1)
    return android.graphics.Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
}
