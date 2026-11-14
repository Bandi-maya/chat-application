package com.example.chat.ui.chats.detail

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.example.chat.data.packs.ChatyPackServer
import com.example.chat.data.packs.ContentPackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-demand emoji pack delivery (Bandi, 2026-11-14: "all emojis handled in the
 * server, compressed, downloaded on demand like other apps").
 *
 * Behaviour mirrored from other chat apps:
 *  - Telegram (core.telegram.org/stickers): packs described by a server index,
 *    downloaded per set on first use, cached locally; missing art falls back to
 *    the plain glyph.
 *  - WhatsApp sticker packs (github.com/WhatsApp/stickers sample app): explicit
 *    per-pack download with progress, removable to reclaim storage.
 *  - Signal pack install-then-cache flow: UNSOURCED - treated as unverified,
 *    not mirrored explicitly.
 *  - Every app: system emoji is always the final fallback.
 *
 * ChatyEmojiPack keeps its "no hooks, no globals" rule: this file is the only
 * place where pack delivery meets rendering, and it reads ChatyEmojiPack's
 * public mapping instead of changing it.
 */

/** The shared store, or null while the host has not configured a server URL. */
@Composable
internal fun rememberPackStore(): ContentPackStore? {
    if (!ChatyPackServer.configured) return null
    val context = LocalContext.current
    return remember(context, ChatyPackServer.baseUrl) { ContentPackStore.get(context) }
}

/**
 * Absolute path of the downloaded art for [style]/[unicode], or null when the
 * pack (or file) is not installed. A downloaded file WINS over the bundled
 * asset; when neither exists the caller's normal fallback chain runs.
 */
@Composable
internal fun rememberDownloadedEmojiArt(style: String, unicode: String): String? {
    val store = rememberPackStore() ?: return null
    val revision by store.revision.collectAsState()
    val path = remember(style, unicode) {
        val assetPath = (if (style == ChatyEmojiPack.TELEGRAM_LOTTIE) ChatyEmojiPack.lottieAssetPath(unicode) else ChatyEmojiPack.assetPathFor(style, unicode)) ?: return@remember null
        // assets/emoji/<packId>/<file> -> pack store id + file name. The server
        // manifest MUST use these same ids and names (it serves the same files).
        val packId = assetPath.substringAfter("emoji/").substringBefore("/")
        packId to assetPath.substringAfterLast("/")
    } ?: return null
    // Store lookups are disk IO: resolved asynchronously, never in composition.
    // A null first frame renders the normal fallback glyph, then upgrades.
    val resolved by produceState<String?>(null, store, path, revision) {
        value = store.localPath(path.first, path.second)
        if (value != null) store.touch(path.first, path.second) // LRU touch, also IO
    }
    return resolved
}

/**
 * Plays one animated WebP from the pack store (a File, not an asset). Same
 * decode policy as IosAnimatedEmoji: display-density target size, software
 * allocator, stops when leaving composition.
 */
@Composable
internal fun FileAnimatedEmoji(
    filePath: String,
    size: Float,
    animate: Boolean = true,
    onUnavailable: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < 28) {
        LaunchedEffect(filePath) { onUnavailable() }
        return
    }
    val context = LocalContext.current
    val resumed = com.example.chat.ui.island.rememberIslandHostResumed()
    val actualAnimate = animate && resumed && !com.example.chat.ui.kit.rememberChatyReduceMotion()
    var drawable by remember(filePath, actualAnimate, size) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(filePath, actualAnimate, size) {
        val d = withContext(Dispatchers.IO) {
            runCatching {
                val src = ImageDecoder.createSource(java.io.File(filePath))
                val px = (size * context.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
                if (actualAnimate) ImageDecoder.decodeDrawable(src) { decoder, _, _ ->
                    decoder.setTargetSize(px, px)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                } else android.graphics.drawable.BitmapDrawable(
                    context.resources,
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ -> decoder.setTargetSize(px, px) },
                )
            }.getOrNull()
        }
        if (d == null) onUnavailable() else drawable = d
    }
    val d = drawable ?: return
    var view by remember { mutableStateOf<ImageView?>(null) }
    DisposableEffect(d) {
        onDispose {
            if (d is AnimatedImageDrawable) d.stop()
            view?.takeIf { it.drawable === d }?.setImageDrawable(null)
        }
    }
    AndroidView(
        modifier = Modifier.size(size.dp),
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER }.also { v -> view = v } },
        update = { image ->
            if (image.drawable !== d) image.setImageDrawable(d)
            if (d is AnimatedImageDrawable && actualAnimate) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                if (!d.isRunning) d.start()
            }
        },
    )
}

/** Static pack image from the pack store (e.g. OpenMoji PNG). Never animated. */
@Composable
internal fun FileStaticEmoji(
    filePath: String,
    size: Float,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val targetPx = (size * density).toInt().coerceAtLeast(1)
    var bitmap by remember(filePath, targetPx) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(filePath, targetPx) {
        val b = withContext(Dispatchers.IO) {
            runCatching {
                com.example.chat.ui.core.system.SampledBitmapDecoder.decode(
                    { java.io.File(filePath).inputStream() },
                    targetPx,
                )
            }.getOrNull()
        }
        if (b == null) onUnavailable() else bitmap = b
    }
    val b = bitmap ?: return
    AndroidView(
        modifier = Modifier.size(size.dp),
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageBitmap(b) },
    )
}
