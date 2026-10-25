package com.example.chat.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dependency-free image loader for profile avatars and banners, mirroring
 * the Dart resolution rule used across profile screens:
 *  - http(s) URL   -> network fetch
 *  - file:// path  -> local file decode
 *  - anything else -> local file decode (Dart FileImage(path))
 * Returns null on any failure so callers fall back to initials/gradient,
 * exactly like the Dart errorBuilder fallbacks. No Coil/Glide dependency:
 * the integration owner keeps the dependency list unchanged for this slice.
 */
@Composable
internal fun rememberProfileImage(source: String?): ImageBitmap? {
    val resolved = source?.trim().orEmpty()
    return produceState<ImageBitmap?>(initialValue = null, resolved) {
        if (resolved.isEmpty()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                    val connection = URL(resolved).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = true
                    try {
                        connection.inputStream.use { input ->
                            BitmapFactory.decodeStream(input)?.asImageBitmap()
                        }
                    } finally {
                        connection.disconnect()
                    }
                } else {
                    val path = resolved.removePrefix("file://")
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                        ?: File(path).takeIf { it.exists() }?.inputStream()?.use { input ->
                            BitmapFactory.decodeStream(input)?.asImageBitmap()
                        }
                }
            }.getOrNull()
        }
    }
}
