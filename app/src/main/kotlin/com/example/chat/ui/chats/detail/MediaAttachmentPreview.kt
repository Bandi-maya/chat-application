package com.example.chat.ui.chats.detail

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.chat.domain.models.MessageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolve storage paths through the same port as the viewer. Never leave a silent blank tile. */
@Composable
internal fun MediaAttachmentPreview(
    attachment: MessageAttachment,
    remoteImage: RemoteImageSlot?,
    resolveUrl: (suspend (String) -> String)?,
    onTap: (() -> Unit)?,
    video: Boolean,
) {
    var url by remember(attachment.url) { mutableStateOf<String?>(null) }
    var failed by remember(attachment.url) { mutableStateOf(false) }
    var thumbnail by remember(attachment.url) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(attachment.url) { mutableStateOf(1.35f) }
    val context = LocalContext.current
    LaunchedEffect(attachment.url) {
        try {
            val path = attachment.url?.takeIf { it.isNotBlank() } ?: error("No media path")
            url = resolveUrl?.invoke(path) ?: path
            if (video) {
                thumbnail = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        val resolved = url!!
                        if (resolved.startsWith("http")) retriever.setDataSource(resolved, emptyMap())
                        else retriever.setDataSource(context, android.net.Uri.parse(resolved))
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally { retriever.release() }
                }
                thumbnail?.let { ratio = (it.width.toFloat() / it.height).coerceIn(0.65f, 1.8f) }
                failed = thumbnail == null
            }
        } catch (_: Exception) { failed = true }
    }
    Box(
        Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(ratio)
            .clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.12f))
            .clickable(enabled = onTap != null) { onTap?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.BrokenImage, contentDescription = "Preview unavailable")
                Text(if (video) "Tap to play video" else "Preview unavailable. Tap to open", style = MaterialTheme.typography.labelSmall)
            }
            video && thumbnail != null -> Image(thumbnail!!.asImageBitmap(), attachment.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            video || url == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            remoteImage != null && remoteImage !== chatyRemoteImage -> remoteImage(url!!, Modifier.fillMaxSize(), attachment.name)
            else -> SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                contentDescription = attachment.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    val image = state.result.drawable
                    if (image.intrinsicWidth > 0 && image.intrinsicHeight > 0)
                        ratio = (image.intrinsicWidth.toFloat() / image.intrinsicHeight).coerceIn(0.65f, 1.8f)
                },
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.BrokenImage, "Preview unavailable. Tap to open") } },
            )
        }
        if (video) FilledIconButton(onClick = { onTap?.invoke() }, modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Rounded.PlayArrow, "Play video")
        }
    }
}
