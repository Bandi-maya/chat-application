package com.example.chat.ui.chats.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.links.MessageLinkPolicy
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.links.LinkPreviewService
import com.example.chat.data.links.PreviewImagePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded thumbnails are shared across bubbles under a byte budget
 *  (width * height * 4 per entry), not a count. */
private val decodedPreviewCache = object : LruCache<String, ImageBitmap>(DECODED_CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}
private const val DECODED_CACHE_BYTES = 4 * 1024 * 1024

/** Bounded, off-main decode: bounds first, hard source-pixel cap, then
 *  downsampled on a decoded-pixel budget and max dimension
 *  (PreviewImagePolicy), so even a very wide/short panorama cannot decode
 *  anywhere near full size or block the UI thread. */
private suspend fun decodePreviewThumbnail(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (!PreviewImagePolicy.accepts(bounds.outWidth, bounds.outHeight)) return@withContext null
    val options = BitmapFactory.Options().apply {
        inSampleSize = PreviewImagePolicy.sampleSizeFor(bounds.outWidth, bounds.outHeight)
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

/** Chaty #34: in-bubble link preview card. Shows the link host immediately and
 *  upgrades to title/description/thumbnail when the OpenGraph fetch lands.
 *  Thumbnails arrive as bytes from the guarded LinkPreviewService client and
 *  are decoded off-main with bounds + downsampling, so a public og:image can
 *  never redirect an unguarded loader at a private/local target or exhaust
 *  memory. Tap opens the link in the browser. Never blocks bubble layout. */
@Composable
fun LinkPreviewCard(
    url: String,
    theme: ThemeConfig,
    textColor: Color,
    service: LinkPreviewService,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preview by produceState<LinkPreviewService.Preview?>(initialValue = null, url) {
        value = runCatching { service.preview(url) }.getOrNull()
    }
    val imageUrl = preview?.imageUrl
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, imageUrl) {
        val target = imageUrl ?: return@produceState
        decodedPreviewCache.get(target)?.let { value = it; return@produceState }
        val bytes = runCatching { service.image(target) }.getOrNull() ?: return@produceState
        val decoded = decodePreviewThumbnail(bytes) ?: return@produceState
        decodedPreviewCache.put(target, decoded)
        value = decoded
    }
    val host = MessageLinkPolicy.displayHost(url)
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(theme.surfaceColor).copy(alpha = 0.35f))
            .clickable(role = Role.Button, onClickLabel = "Open link $host") {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    // No browser available; the link text itself remains visible.
                }
            }
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            imageBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f).heightIn(min = 44.dp)) {
                preview?.title?.let { title ->
                    Text(
                        title,
                        color = textColor,
                        fontSize = (13.5 * theme.fontScale).sp,
                        fontWeight = FontWeight.W600,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview?.description?.let { description ->
                    Text(
                        description,
                        color = textColor.copy(alpha = 0.75f),
                        fontSize = (12 * theme.fontScale).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    preview?.siteName ?: host,
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = (11 * theme.fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
