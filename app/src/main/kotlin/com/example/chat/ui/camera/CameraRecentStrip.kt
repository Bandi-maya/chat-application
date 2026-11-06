package com.example.chat.ui.camera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.chat.ui.onboarding.ChatyPermissions

/** Recent gallery photos for the camera's drag-up strip (GB DragGalleryStrip
 *  parity). Newest first; empty when the photos permission is not granted or
 *  the query fails, so the caller simply keeps the strip hidden. */
fun queryRecentImageUris(context: Context, limit: Int = 12): List<Uri> {
    if (!ChatyPermissions.isGranted(context, ChatyPermissions.photos)) return emptyList()
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val out = ArrayList<Uri>(limit)
    try {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && out.size < limit) {
                out.add(ContentUris.withAppendedId(collection, cursor.getLong(idCol)))
            }
        }
    } catch (_: Exception) {
        // A provider hiccup leaves the strip empty, never crashes capture.
    }
    return out
}

/** GB-style recent-photos strip above the shutter: a drag handle opens a row
 *  of the latest gallery images; tapping one imports it. The strip itself is
 *  only composed when the caller has already confirmed photos permission. */
@Composable
fun CameraRecentStrip(
    open: Boolean,
    photos: List<Uri>,
    onToggle: (Boolean) -> Unit,
    onPick: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 24.dp.toPx() }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        var dragY by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(open) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY < -dragThresholdPx) onToggle(true)
                            else if (dragY > dragThresholdPx) onToggle(false)
                            dragY = 0f
                        },
                        onVerticalDrag = { _, amount -> dragY += amount },
                    )
                }
                .clickable { onToggle(!open) }
                .semantics {
                    contentDescription = if (open) "Hide recent photos" else "Show recent photos"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x66FFFFFF)),
            )
        }
        if (open) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                items(photos.size) { i ->
                    val uri = photos[i]
                    coil.compose.AsyncImage(
                        model = uri,
                        contentDescription = "Recent photo ${i + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(uri) },
                    )
                }
            }
        }
    }
}
