package com.example.chat.data.services

import com.example.chat.data.remote.SupabaseStorageClient
import com.example.chat.domain.models.MessageAttachment
import java.util.UUID

/**
 * Exact port of lib/data/services/chat_media_service.dart's upload pipeline
 * (uploadFile / _maxBytes / createSignedUrl / deleteOwnAttachment /
 * _safeFileName / _fallbackMimeForType / _formatBytes). The platform file
 * picker and flutter_image_compress are UI/platform concerns: callers hand
 * bytes in via [uploadBytes]; optional image compression (Img_highres_seek
 * quality < 100) is a wiring seam ([imageCompressor]) that returns the
 * compressed bytes or null to bypass, exactly like the Dart try/catch bypass.
 * The ApiBackendService legacy upload branch and demo:// paths are not
 * ported (api branch keeps them registered-but-legacy; Supabase storage is
 * the production path).
 */
class ChatMediaService(
    private val storage: SupabaseStorageClient,
    private val currentUserId: () -> String?,
    /** universal.sendImagesFullResolutionMb (clamped 1..6 upstream). */
    private val sendImagesFullResolutionMb: () -> Int = { 6 },
    /** gbDouble(key, fallback) preference seam. */
    private val gbSizeLimitMb: (key: String, fallback: Double) -> Double = { _, fb -> fb },
    /** Img share count limit for multi-pick (Img_share_limit, fallback 30). */
    private val imageShareLimit: () -> Int = { 30 },
    /** Optional image compression seam (quality percent -> bytes or null). */
    private val imageCompressor: suspend (bytes: ByteArray, quality: Int) -> ByteArray? = { _, _ -> null },
) {
    /** Exact port of pickAndUploadMultiple's limit enforcement. */
    fun enforceShareLimit(count: Int, maxFiles: Int? = null) {
        val limit = (maxFiles ?: imageShareLimit()).coerceIn(1, 100)
        if (count > limit) {
            throw IllegalStateException(
                "You selected $count files; your configured send limit is $limit.",
            )
        }
    }

    /** Exact port of uploadFile (post-picker; compression bypass on failure
     *  is intentional and matches the Dart catch). */
    suspend fun uploadBytes(
        conversationId: String,
        type: String,
        bytes: ByteArray,
        displayName: String,
        mimeType: String? = null,
        durationSeconds: Int = 0,
        imageQuality: Int = 85,
    ): MessageAttachment {
        var payload = bytes
        var rawName = displayName
        var effectiveMime = mimeType ?: fallbackMimeForType(type)

        if (type == "image" && effectiveMime != "image/gif") {
            val quality = imageQuality.coerceIn(10, 100)
            if (quality < 100) {
                try {
                    val compressed = imageCompressor(payload, quality)
                    if (compressed != null && compressed.isNotEmpty()) {
                        payload = compressed
                        val dot = rawName.lastIndexOf('.')
                        rawName = "${if (dot > 0) rawName.substring(0, dot) else rawName}.jpg"
                        effectiveMime = "image/jpeg"
                    }
                } catch (error: Throwable) {
                    // Chaty media: optional image compression bypassed.
                }
            }
        }

        val size = payload.size.toLong()
        if (size <= 0) throw IllegalStateException("The selected file is empty.")
        val maxBytes = maxBytes(type)
        if (size > maxBytes) {
            val limitMb = maxBytes / (1024 * 1024)
            throw IllegalStateException("This $type exceeds the $limitMb MB storage limit.")
        }

        val authUserId = currentUserId()
            ?: throw IllegalStateException("Authentication required.")
        val safeName = safeFileName(rawName)
        val objectPath = "$authUserId/$conversationId/${UUID.randomUUID()}_$safeName"

        storage.upload(BUCKET, objectPath, payload, effectiveMime)

        return MessageAttachment(
            id = UUID.randomUUID().toString(),
            type = type,
            name = rawName,
            size = formatBytes(size),
            url = objectPath,
            durationSeconds = durationSeconds,
        )
    }

    /** Exact port of _maxBytes. */
    fun maxBytes(type: String): Long {
        if (type == "image") {
            val fullResMb = sendImagesFullResolutionMb().coerceIn(1, 6).toDouble()
            val configured = gbSizeLimitMb("Up_size_limit", fullResMb)
            val effectiveMb = configured.coerceIn(1.0, STORAGE_LIMIT_MB.toDouble()).toInt()
            return effectiveMb * 1024L * 1024L
        }
        val configured = if (type == "audio") {
            gbSizeLimitMb("abo_saleh_audio_limit_check", STORAGE_LIMIT_MB.toDouble())
        } else {
            gbSizeLimitMb("Up_size_limit", STORAGE_LIMIT_MB.toDouble())
        }
        val effectiveMb = configured.coerceIn(1.0, STORAGE_LIMIT_MB.toDouble()).toInt()
        return effectiveMb * 1024L * 1024L
    }

    /** Exact port of createSignedUrl (http(s)/data: pass through; absolute
     *  /path joins the REST base via the supplied seam). */
    suspend fun createSignedUrl(
        objectPath: String,
        expiresInSeconds: Int = 900,
        restApiBaseUrl: () -> String = { "" },
    ): String {
        if (objectPath.trim().isEmpty()) throw IllegalStateException("Attachment path is missing.")
        if (objectPath.startsWith("http://") || objectPath.startsWith("https://")) {
            return objectPath
        }
        if (objectPath.startsWith("data:")) {
            return objectPath
        }
        if (objectPath.startsWith("/")) {
            return "${restApiBaseUrl()}$objectPath"
        }
        return storage.createSignedUrl(BUCKET, objectPath, expiresInSeconds)
    }

    /** Exact port of deleteOwnAttachment. */
    suspend fun deleteOwnAttachment(objectPath: String) {
        if (objectPath.trim().isEmpty()) return
        storage.remove(BUCKET, listOf(objectPath))
    }

    companion object {
        const val BUCKET = "chat-media"
        const val STORAGE_LIMIT_MB = 50

        /** Exact port of _safeFileName. */
        fun safeFileName(source: String): String {
            val cleaned = source
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .replace(Regex("_+"), "_")
            if (cleaned.isEmpty()) return "attachment"
            return if (cleaned.length > 120) cleaned.substring(cleaned.length - 120) else cleaned
        }

        /** Exact port of _fallbackMimeForType. */
        fun fallbackMimeForType(type: String): String = when (type) {
            "image" -> "image/jpeg"
            "video" -> "video/mp4"
            "audio" -> "audio/mp4"
            else -> "application/octet-stream"
        }

        /** Exact port of _formatBytes. */
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            return "%.1f MB".format(mb)
        }
    }
}
