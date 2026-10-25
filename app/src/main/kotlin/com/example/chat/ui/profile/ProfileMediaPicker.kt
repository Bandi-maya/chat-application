package com.example.chat.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.chat.data.services.ProfileMediaSource
import com.example.chat.ui.theme.LocalThemeConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android pick -> crop -> compress pipeline for profile media, porting the
 * platform halves of lib/data/services/profile_media_service.dart
 * (image_picker + image_editor_cropper_dialog.dart + flutter_image_compress)
 * that the JVM service seam leaves to callers (api @ 3bee780d).
 *
 * Flow: gallery (PickVisualMedia) or camera (TakePicture via FileProvider) ->
 * cache file (downscaled to the Dart picker's square*1.8 bound, EXIF
 * orientation applied like the plugin) -> full-screen crop dialog (1:1
 * circle "Crop Avatar Photo" / 16:9 rounded "Crop Profile Banner", pan/zoom
 * 1..4x, 90-degree rotations) -> cropped PNG file (cropped_avatar|banner_<ts>.png
 * in cacheDir, zero-fill and empty checks like the Dart) -> caller uploads.
 *
 * OWNER WIRING REQUIRED: the camera path needs a FileProvider manifest entry
 * (authority "${context.packageName}.fileprovider") plus res/xml/file_paths.xml
 * covering cache-path; the media worker's batch already queues the same
 * FileProvider for chat attachments - reconcile to one provider.
 */

/** flutter_image_compress seam implementation for ProfileMediaService:
 *  decode with EXIF orientation applied (the plugin's default), scale so the
 *  SHORTER side is [square] preserving aspect (Dart minWidth/minHeight),
 *  encode JPEG at [quality] with metadata dropped (keepExif: false). */
object ProfileImageCompressor {
    fun compress(bytes: ByteArray, square: Int, quality: Int): ByteArray? {
        val decoded = decodeWithOrientation(bytes) ?: return null
        val src = decoded
        val short = minOf(src.width, src.height)
        val scaled = if (short > square) {
            val ratio = square.toFloat() / short
            Bitmap.createScaledBitmap(
                src,
                (src.width * ratio).toInt().coerceAtLeast(1),
                (src.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            src
        }
        val out = java.io.ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== src) scaled.recycle()
        src.recycle()
        return if (ok) out.toByteArray() else null
    }

    /** Orientation matrix for [bytes]' EXIF flag (identity when none). */
    fun orientationMatrix(bytes: ByteArray): Matrix {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
    }

    /** Decode with EXIF orientation baked in (Image.file / the plugins all
     *  present the oriented bitmap). */
    fun decodeWithOrientation(bytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val matrix = orientationMatrix(bytes)
        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Apply a precomputed orientation matrix to an already-decoded bitmap. */
    fun applyOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val matrix = orientationMatrix(bytes)
        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}

private class PendingCrop(
    val file: File,
    val isAvatar: Boolean,
    val title: String,
)

class ProfileMediaPickerState internal constructor(
    private val launchGallery: () -> Unit,
    private val launchCamera: () -> Unit,
) {
    /** Start picking from [source]; the crop dialog follows automatically. */
    fun launch(source: ProfileMediaSource, isAvatar: Boolean) {
        pendingIsAvatar = isAvatar
        pendingTitle = if (isAvatar) "Crop Avatar Photo" else "Crop Profile Banner"
        when (source) {
            ProfileMediaSource.camera -> launchCamera()
            ProfileMediaSource.gallery -> launchGallery()
        }
    }

    internal var pendingIsAvatar: Boolean = true
    internal var pendingTitle: String = "Crop Avatar Photo"
}

/**
 * Self-contained pick+crop host. [onResult] receives the cropped PNG File,
 * or null when the user cancelled at any step (matching the Dart
 * _CancelledException path). Rendered once per screen that offers
 * avatar/banner picking.
 */
@Composable
fun rememberProfileMediaPicker(
    square: Int,
    onResult: (File?) -> Unit,
    onError: (String) -> Unit = {},
): ProfileMediaPickerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCrop by remember { mutableStateOf<PendingCrop?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val state = remember { mutableStateOf<ProfileMediaPickerState?>(null) }

    fun ingest(uri: Uri?, tagIsAvatar: Boolean, tagTitle: String) {
        if (uri == null) {
            onResult(null)
            return
        }
        scope.launch {
            val file = copyPickToCache(context, uri, square)
            if (file == null) {
                onError("The selected image is no longer accessible.")
            } else {
                pendingCrop = PendingCrop(file, tagIsAvatar, tagTitle)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val s = state.value ?: return@rememberLauncherForActivityResult
        ingest(uri, s.pendingIsAvatar, s.pendingTitle)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = cameraFile
        if (!success || file == null) {
            onResult(null)
        } else {
            val s = state.value ?: return@rememberLauncherForActivityResult
            pendingCrop = PendingCrop(file, s.pendingIsAvatar, s.pendingTitle)
        }
    }

    if (state.value == null) {
        // One-time init; launchers are captured by reference and stable.
        state.value = ProfileMediaPickerState(
            launchGallery = { galleryLauncher.launch("image/*") },
            launchCamera = {
                val file = File(
                    context.cacheDir,
                    "chaty_camera_" + System.currentTimeMillis() + ".jpg",
                )
                cameraFile = file
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file,
                )
                cameraLauncher.launch(uri)
            },
        )
    }

    pendingCrop?.let { crop ->
        ProfileImageCropperDialog(
            sourceFile = crop.file,
            isAvatar = crop.isAvatar,
            title = crop.title,
            onDone = { result ->
                pendingCrop = null
                if (result == null) {
                    crop.file.delete()
                    onResult(null)
                } else {
                    if (result.absolutePath != crop.file.absolutePath) crop.file.delete()
                    onResult(result)
                }
            },
            onError = onError,
        )
    }

    return state.value!!
}

/** image_picker half: copy the selected content into the cache dir, bounding
 *  the decode to square*1.8 on the long side (Dart maxWidth/maxHeight) and
 *  re-encoding JPEG at quality 95 (Dart imageQuality). EXIF orientation is
 *  applied, matching what Image.file shows. */
private suspend fun copyPickToCache(context: Context, uri: Uri, square: Int): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val maxDim = (square * 1.8).toInt()
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return@runCatching null
            // Orientation flag lives on the ORIGINAL bytes.
            val oriented = ProfileImageCompressor.applyOrientation(decoded, bytes)
            val file = File(
                context.cacheDir,
                "chaty_pick_${System.currentTimeMillis()}.jpg",
            )
            file.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            oriented.recycle()
            decoded.recycle()
            file
        }.getOrNull()
    }

/** Port of image_editor_cropper_dialog.dart as a full-screen dialog. */
@Composable
fun ProfileImageCropperDialog(
    sourceFile: File,
    isAvatar: Boolean,
    title: String,
    onDone: (File?) -> Unit,
    onError: (String) -> Unit = {},
) {
    val theme = LocalThemeConfig.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var quarterRotations by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }

    // Decode once, bounded for display (Dart instantiates at 1280 target).
    val image: ImageBitmap? = remember(sourceFile) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 1280) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(sourceFile.absolutePath, opts)?.asImageBitmap()
        }.getOrNull()
    }
    val srcW = image?.width?.toFloat() ?: 1f
    val srcH = image?.height?.toFloat() ?: 1f

    Dialog(
        onDismissRequest = { if (!isProcessing) onDone(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // App bar: close / title / reset / rotate.
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (!isProcessing) onDone(null) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    userScale = 1f
                    userOffset = Offset.Zero
                    quarterRotations = 0
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset", tint = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { quarterRotations = (quarterRotations + 1) % 4 }) {
                    Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate 90°", tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                if (isAvatar) "Pinch or drag to position inside the circular avatar"
                else "Pinch or drag to frame the banner",
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))

            // Crop frame sizing (Dart MediaQuery width - 48; banner 16:9).
            val cropBoxWidthDp = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 48).dp
            val cropBoxHeightDp = if (isAvatar) cropBoxWidthDp else cropBoxWidthDp * 9 / 16
            val cropWpx = with(density) { cropBoxWidthDp.toPx() }
            val cropHpx = with(density) { cropBoxHeightDp.toPx() }

            // Cover-fit display sizing and clamp - the Dart formulas, JVM-tested
            // in ProfileCropGeometryTest.
            val layout = ProfileCropGeometry.layout(srcW, srcH, quarterRotations, cropWpx, cropHpx)
            val childW = layout.childW
            val childH = layout.childH

            fun clampOffset(o: Offset, sc: Float): Offset {
                val (cx, cy) = ProfileCropGeometry.clampOffset(
                    o.x, o.y, sc, layout.footW, layout.footH, cropWpx, cropHpx,
                )
                return Offset(cx, cy)
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        // Dart BoxShadow blur 24 spread 8 black 0.6 (approximation:
                        // Compose shadow has no spread radius).
                        .shadow(
                            24.dp,
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.6f),
                            spotColor = Color.Black.copy(alpha = 0.6f),
                        )
                        .size(cropBoxWidthDp, cropBoxHeightDp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp))
                        .border(
                            2.5.dp,
                            Color(theme.accentColor).copy(alpha = 0.8f),
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                        )
                        .clip(RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 14.dp))
                        .pointerInput(quarterRotations) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (userScale * zoom).coerceIn(1f, 4f)
                                userScale = newScale
                                userOffset = clampOffset(userOffset + pan, newScale)
                            }
                        },
                ) {
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(
                                    with(density) { childW.toDp() },
                                    with(density) { childH.toDp() },
                                )
                                .graphicsLayer {
                                    scaleX = userScale
                                    scaleY = userScale
                                    translationX = userOffset.x
                                    translationY = userOffset.y
                                    rotationZ = quarterRotations * 90f
                                },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                OutlinedButton(
                    onClick = { if (!isProcessing) onDone(null) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Button(
                    onClick = {
                        if (isProcessing) return@Button
                        isProcessing = true
                        scope.launch {
                            try {
                                val out = captureCrop(
                                    context = context,
                                    sourceFile = sourceFile,
                                    isAvatar = isAvatar,
                                    quarterRotations = quarterRotations,
                                    userScale = userScale,
                                    userOffset = userOffset,
                                    cropWpx = cropWpx,
                                    cropHpx = cropHpx,
                                )
                                isProcessing = false
                                onDone(out)
                            } catch (e: Throwable) {
                                isProcessing = false
                                onError("Failed to save cropped image: ${e.message ?: e}")
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.accentColor),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isProcessing) "Cropping…" else "Crop & Use",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Off-screen equivalent of the Dart RepaintBoundary.toImage(pixelRatio: 2.5):
 * renders the transformed bitmap into a crop-sized PNG at 2.5x. The transform
 * chain mirrors the Compose graphicsLayer (center -> rotate -> scale ->
 * translate offset), the layout cover-fit and the boundary clamp guarantee
 * the image covers the crop window. Output naming and zero-length checks
 * match the Dart _captureAndSaveCrop.
 */
private suspend fun captureCrop(
    context: Context,
    sourceFile: File,
    isAvatar: Boolean,
    quarterRotations: Int,
    userScale: Float,
    userOffset: Offset,
    cropWpx: Float,
    cropHpx: Float,
): File = withContext(Dispatchers.IO) {
    // Full-quality decode (bounded) with EXIF orientation applied.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw Exception("The selected image is no longer accessible.")
    }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 4096) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
        ?: throw Exception("The selected image is no longer accessible.")
    val bitmap = ProfileImageCompressor.applyOrientation(raw, sourceFile.readBytes())

    val layout = ProfileCropGeometry.layout(
        bitmap.width.toFloat(), bitmap.height.toFloat(), quarterRotations, cropWpx, cropHpx,
    )
    val childW = layout.childW
    val childH = layout.childH

    val (outW, outH) = ProfileCropGeometry.captureSize(cropWpx, cropHpx)
    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.scale(2.5f, 2.5f)
    canvas.translate(cropWpx / 2f + userOffset.x, cropHpx / 2f + userOffset.y)
    canvas.scale(userScale, userScale)
    canvas.rotate(quarterRotations * 90f)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        bitmap,
        null,
        android.graphics.RectF(-childW / 2f, -childH / 2f, childW / 2f, childH / 2f),
        paint,
    )
    bitmap.recycle()

    val png = java.io.ByteArrayOutputStream()
    if (!output.compress(Bitmap.CompressFormat.PNG, 100, png)) {
        output.recycle()
        throw Exception("Unable to encode cropped image bytes")
    }
    output.recycle()
    val buffer = png.toByteArray()
    if (buffer.isEmpty()) {
        throw Exception("The crop is empty. Reposition the image inside the frame.")
    }
    val tag = if (isAvatar) "avatar" else "banner"
    val file = File(
        context.cacheDir,
        "cropped_" + tag + "_" + System.currentTimeMillis() + ".png",
    )
    file.writeBytes(buffer)
    if (file.length() == 0L) {
        throw Exception("The crop could not be saved. Try again.")
    }
    file
}
