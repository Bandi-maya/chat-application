package com.example.chat.ui.chats.detail

import android.content.Context
import coil.ImageLoader

/** Shared decoder infrastructure, no response/image caches. Each row owns its cancellable request. */
internal object StickerImageLoader {
    @Volatile private var loader: ImageLoader? = null
    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .memoryCache(null).diskCache(null)
            .components { if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) }
            .build().also { loader = it }
    }
}
