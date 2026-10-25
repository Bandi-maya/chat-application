package com.example.chat.data.services

import com.example.chat.data.remote.SupabaseStorageClient
import java.util.UUID

/** Source of the media: device camera or the system gallery. */
enum class ProfileMediaSource { camera, gallery }

/** Picker/cropper cancellation (Dart _CancelledException; toString 'cancelled'). */
class ProfileMediaCancelledException : Exception("cancelled")

/**
 * Exact port of lib/data/services/profile_media_service.dart's validation,
 * compression and upload pipeline (uploadAvatar / uploadBanner /
 * _pickCompressUpload / isSupportedImageMime). The ImagePicker, cropper
 * dialog and flutter_image_compress are platform/UI concerns: callers hand
 * in the picked (already UI-cropped) image bytes and the [imageCompressor]
 * seam normalizes to JPEG at the target square; a null return keeps the
 * original file, exactly like the Dart `compressed != null ? ... : workingFile`.
 */
class ProfileMediaService(
    private val storage: SupabaseStorageClient,
    private val currentUserId: () -> String?,
    /** jpeg compression seam: (bytes, square, quality) -> bytes or null. */
    private val imageCompressor: suspend (bytes: ByteArray, square: Int, quality: Int) -> ByteArray? = { _, _, _ -> null },
) {
    suspend fun uploadAvatar(bytes: ByteArray): String =
        compressUpload(bytes, folder = "avatars", square = 512, quality = 88)

    suspend fun uploadBanner(bytes: ByteArray): String =
        compressUpload(bytes, folder = "banners", square = 1280, quality = 85)

    private suspend fun compressUpload(
        bytes: ByteArray,
        folder: String,
        square: Int,
        quality: Int,
    ): String {
        val userId = currentUserId() ?: "local_user"

        if (bytes.size > MAX_BYTES_BEFORE_COMPRESS) {
            throw IllegalStateException("Image is too large (max 6 MB before compression).")
        }

        val compressed = imageCompressor(bytes, square, quality)
        val file = compressed ?: bytes
        if (file.isEmpty()) {
            throw IllegalStateException("Image processing failed. Try a different picture.")
        }
        if (file.size > MAX_BYTES_AFTER_COMPRESS) {
            throw IllegalStateException("Processed image is still too large. Try another one.")
        }

        val objectPath = "$userId/$folder/${UUID.randomUUID()}.jpg"
        storage.upload(BUCKET, objectPath, file, "image/jpeg")
        return storage.getPublicUrl(BUCKET, objectPath)
    }

    companion object {
        const val BUCKET = "profile-media"
        const val MAX_BYTES_BEFORE_COMPRESS = 6 * 1024 * 1024
        const val MAX_BYTES_AFTER_COMPRESS = 2 * 1024 * 1024
        val ALLOWED_MIME = listOf("image/jpeg", "image/png", "image/webp")

        /** Exact port of isSupportedImageMime. */
        fun isSupportedImageMime(mime: String): Boolean = ALLOWED_MIME.contains(mime)
    }
}
