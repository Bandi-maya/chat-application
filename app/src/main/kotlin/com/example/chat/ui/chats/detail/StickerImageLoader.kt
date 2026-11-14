package com.example.chat.ui.chats.detail

import android.content.Context
import coil.ImageLoader

/** Shared decoder infrastructure. Each row owns its cancellable request.
 *  G4 (2026-11-14): bounded caches - with caching disabled every sticker row
 *  re-decoded (and re-fetched) on each scroll pass. Bounds keep it light on
 *  low-end phones: 3% of available memory (8MB maximum) + a 20MB disk cap with LRU eviction. */
internal object StickerImageLoader {
    @Volatile private var loader: ImageLoader? = null
    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        val app = context.applicationContext
        loader ?: ImageLoader.Builder(app)
            .memoryCache {
                coil.memory.MemoryCache.Builder(app)
                    .maxSizeBytes(minOf(8 * 1024 * 1024, (Runtime.getRuntime().maxMemory() * 0.03).toInt()))
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(app.cacheDir.resolve("sticker_cache"))
                    .maxSizeBytes(20L * 1024 * 1024)
                    .build()
            }
            .components { if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) }
            .build().also { loader = it }
    }
}
