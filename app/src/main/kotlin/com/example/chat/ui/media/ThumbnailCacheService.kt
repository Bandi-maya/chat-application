package com.example.chat.ui.media

/** Exact port of lib/features/media/services/thumbnail_cache_service.dart.
 *
 *  The memory-cache behavior (120-entry FIFO) is ported verbatim. The actual
 *  image decode (dart:ui instantiateImageCodec) is supplied as the
 *  [imageDecoder] seam; on Android use BitmapFactory with inSampleSize and
 *  re-encode to PNG bytes.
 */
class ThumbnailCacheService(
    private val imageDecoder: suspend (path: String, targetWidth: Int) -> ByteArray? = { _, _ -> null },
) {
    private val memoryCache = LinkedHashMap<String, ByteArray>()

    companion object {
        const val MAX_CACHE_ENTRIES = 120
    }

    fun hasThumbnail(filePath: String): Boolean = memoryCache.containsKey(filePath)

    fun getThumbnailBytes(filePath: String): ByteArray? = memoryCache[filePath]

    fun putThumbnailBytes(filePath: String, bytes: ByteArray) {
        if (memoryCache.size >= MAX_CACHE_ENTRIES) {
            val firstKey = memoryCache.keys.first()
            memoryCache.remove(firstKey)
        }
        memoryCache[filePath] = bytes
    }

    fun clear() {
        memoryCache.clear()
    }

    suspend fun generateImageThumbnail(imagePath: String, targetWidth: Int = 180): ByteArray? {
        if (memoryCache.containsKey(imagePath)) {
            return memoryCache[imagePath]
        }

        try {
            val thumbBytes = imageDecoder(imagePath, targetWidth)
            if (thumbBytes != null) {
                putThumbnailBytes(imagePath, thumbBytes)
                return thumbBytes
            }
        } catch (_: Exception) {
            // Fallback
        }
        return null
    }
}
