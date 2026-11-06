package com.example.chat.ui.chats.detail

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import com.example.chat.data.media.ResolvedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses only the existing protected attachment resolver's owned bytes/file. No second network path. */
@Composable
internal fun ReceivedStickerArtwork(source: ResolvedSource, modifier: Modifier) {
    var drawable by remember(source) { mutableStateOf<Drawable?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    var view by remember(source) { mutableStateOf<ImageView?>(null) }
    LaunchedEffect(source) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (android.os.Build.VERSION.SDK_INT < 28) {
                    val bitmap = when (source) {
                        is ResolvedSource.Bytes -> android.graphics.BitmapFactory.decodeByteArray(source.bytes, 0, source.bytes.size)
                        is ResolvedSource.Leased -> android.graphics.BitmapFactory.decodeFile(source.lease.file.absolutePath)
                        else -> null
                    } ?: error("Sticker bytes unavailable")
                    return@runCatching android.graphics.drawable.BitmapDrawable(null, bitmap)
                }
                val input = when (source) {
                    is ResolvedSource.Bytes -> ImageDecoder.createSource(java.nio.ByteBuffer.wrap(source.bytes))
                    is ResolvedSource.Leased -> ImageDecoder.createSource(source.lease.file)
                    else -> error("Sticker bytes are not available locally")
                }
                ImageDecoder.decodeDrawable(input)
            }.getOrNull()
        }
        failed = result == null; drawable = result
    }
    val d = drawable
    DisposableEffect(d) {
        (d as? AnimatedImageDrawable)?.let { it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; it.start() }
        onDispose { (d as? AnimatedImageDrawable)?.stop(); view?.setImageDrawable(null) }
    }
    if (failed) Text("Sticker preview unavailable. Tap to open image.")
    else if (d != null) AndroidView(modifier = modifier,
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER }.also { view = it } },
        update = { it.setImageDrawable(d) })
}
