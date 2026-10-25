package com.example.chat.data.services

import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.data.remote.SupabaseStorageClient
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import com.example.chat.domain.models.PrivacyPublication
import com.example.chat.domain.models.PrivacyPublicationPolicy
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import java.util.UUID

data class StatusRecord(
    val id: String,
    val userId: String,
    val text: String,
    val mediaType: String,
    val mediaPath: String?,
    val mediaName: String?,
    val mediaSize: Long,
    val durationSeconds: Long,
    val createdAt: Instant,
    val expiresAt: Instant,
    val deletedAt: Instant?,
) {
    val hasMedia: Boolean get() = !mediaPath.isNullOrEmpty()
    val isDeleted: Boolean get() = deletedAt != null
}

data class StatusViewer(
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarInitials: String,
    val avatarColorHex: String,
    val viewedAt: Instant,
)

/**
 * Exact port of lib/data/services/status_service.dart's publishing, viewing
 * and deletion paths (publishText / pickAndPublish's validation+upload
 * pipeline as [publishMediaBytes] / publishMediaFile equivalent /
 * markViewed / viewersFor / deleteStatus / _insert / _fromRow /
 * _safeFileName / _fallbackMime). The platform file picker is a UI concern;
 * callers hand bytes + display name in. The revocation-watch stream and its
 * notification fan-out port with the realtime wiring slice.
 *
 * Preferences are seams: the full preferences controller lands with M7/M8.
 */
class StatusService(
    private val queryClient: PostgrestQueryClient,
    private val storage: SupabaseStorageClient,
    private val rpc: com.example.chat.data.messaging.SupabaseRpc,
    private val currentUserId: () -> String?,
    private val privacy: () -> PrivacyPreferencesSnapshot,
    private val publicationPolicy: PrivacyPublicationPolicy = PrivacyPublicationPolicy(),
    /** preferences.preferencesReady */
    private val preferencesReady: () -> Boolean = { true },
    /** gbBool('yoHideStatViewV2') */
    private val gbHideStatusView: () -> Boolean = { false },
    /** gbBool('abu9aleh_status_audio') - audio-status advanced feature flag. */
    private val gbAudioStatusEnabled: () -> Boolean = { false },
    /** gbDouble('abo_saleh_audio_limit_check' | 'Up_size_limit', fallback). */
    private val gbSizeLimitMb: (key: String, fallback: Double) -> Double = { _, fb -> fb },
) {
    /**
     * Dart watchActiveStatuses (status_updates stream, newest first, unexpired,
     * deleted rows kept only when anti-delete is on). The Supabase realtime
     * stream is not ported on this plane, so this polls the same table on a
     * fixed interval; it never fabricates rows.
     */
    fun watchActiveStatuses(
        keepDeleted: () -> Boolean = { false },
        pollMillis: Long = 60_000L,
    ): kotlinx.coroutines.flow.Flow<List<StatusRecord>> = kotlinx.coroutines.flow.flow {
        while (true) {
            if (currentUserId() == null) {
                // No Supabase session (custom-backend login): no query, empty list.
                emit(emptyList())
                kotlinx.coroutines.delay(pollMillis)
                continue
            }
            try {
                val rows = queryClient.from("status_updates")
                    .select()
                    .order("created_at", ascending = false)
                    .execute()
                val now = Instant.now()
                val keep = keepDeleted()
                emit(
                    rows.map { fromRow(it) }
                        .filter { it.expiresAt.isAfter(now) && (!it.isDeleted || keep) },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // keep the last emission; the next poll retries
            }
            kotlinx.coroutines.delay(pollMillis)
        }
    }

    /** Exact port of publishText. */
    suspend fun publishText(text: String): StatusRecord {
        val value = text.trim()
        if (value.isEmpty()) throw IllegalStateException("Enter status text before publishing.")
        return insert(text = value, mediaType = "text")
    }

    /**
     * Exact port of pickAndPublish's post-picker pipeline and
     * publishMediaFile: identical validation, size limits, object naming,
     * upload, insert, and upload-rollback on insert failure.
     */
    suspend fun publishMediaBytes(
        bytes: ByteArray,
        displayName: String,
        mediaType: String,
        text: String = "",
        mimeType: String? = null,
        durationSeconds: Long = 0,
    ): StatusRecord {
        if (mediaType == "audio" && !gbAudioStatusEnabled()) {
            throw IllegalStateException("Enable Audio Status in Advanced Features first.")
        }
        val size = bytes.size.toLong()
        if (size <= 0) throw IllegalStateException("The selected file is empty.")
        val configuredMb = if (mediaType == "audio") {
            gbSizeLimitMb("abo_saleh_audio_limit_check", STORAGE_LIMIT_MB.toDouble())
        } else {
            gbSizeLimitMb("Up_size_limit", STORAGE_LIMIT_MB.toDouble())
        }
        val effectiveLimitMb = configuredMb.coerceIn(1.0, STORAGE_LIMIT_MB.toDouble()).toInt()
        val maxBytes = effectiveLimitMb * 1024L * 1024L
        if (size > maxBytes) {
            throw IllegalStateException(
                "Selected media exceeds the $effectiveLimitMb MB storage limit.",
            )
        }

        val userId = currentUserId()
            ?: throw IllegalStateException("Authentication required.")
        val safeName = safeFileName(displayName)
        val objectPath = "$userId/${UUID.randomUUID()}_$safeName"
        val effectiveMime = mimeType ?: fallbackMime(mediaType)

        storage.upload(BUCKET, objectPath, bytes, effectiveMime)

        try {
            return insert(
                text = text.trim(),
                mediaType = mediaType,
                mediaPath = objectPath,
                mediaName = safeName,
                mediaSize = size,
                mimeType = effectiveMime,
                durationSeconds = durationSeconds,
            )
        } catch (error: Throwable) {
            runCatching { storage.remove(BUCKET, listOf(objectPath)) }
            throw error
        }
    }

    /** Exact port of createSignedUrl (default 900s). */
    suspend fun createSignedUrl(path: String, expiresInSeconds: Int = 900): String =
        storage.createSignedUrl(BUCKET, path, expiresInSeconds)

    /** Exact port of markViewed: gated on preferences readiness, the
     *  publication policy, and the gb hide-view flag; silently no-ops when
     *  publication is suppressed. */
    suspend fun markViewed(status: StatusRecord): Boolean {
        if (!preferencesReady()) return false
        val mayPublish = publicationPolicy.allows(
            PrivacyPublication.statusView, privacy(),
        ) && !gbHideStatusView()
        if (!mayPublish) return false
        rpc.rpc(
            "mark_status_viewed",
            mapOf("p_status_id" to status.id, "p_hide_view" to false),
        )
        return true
    }

    /** Exact port of viewersFor (embedded profiles join; errors -> empty). */
    suspend fun viewersFor(statusId: String): List<StatusViewer> {
        return try {
            val rows = queryClient.from("status_view_events")
                .select(
                    "viewer_id,viewed_at," +
                        "profiles!status_view_events_viewer_id_fkey(" +
                        "display_name,username,avatar_initials,avatar_color_hex)",
                )
                .eq("status_id", statusId)
                .order("viewed_at", ascending = false)
                .execute()
            rows.map { row ->
                @Suppress("UNCHECKED_CAST")
                val profile = (row["profiles"] as? Map<String, Any?>) ?: emptyMap()
                StatusViewer(
                    userId = row["viewer_id"]?.toString() ?: "",
                    displayName = profile["display_name"]?.toString() ?: "Chaty user",
                    username = profile["username"]?.toString() ?: "user",
                    avatarInitials = profile["avatar_initials"]?.toString() ?: "CU",
                    avatarColorHex = profile["avatar_color_hex"]?.toString() ?: "0xFF6366F1",
                    viewedAt = com.example.chat.core.util.parseIsoToInstant(
                        row["viewed_at"]?.toString(),
                    ) ?: Instant.now(),
                )
            }
        } catch (error: Throwable) {
            emptyList()
        }
    }

    /** Exact port of deleteStatus (own-status guard + RPC). */
    suspend fun deleteStatus(status: StatusRecord) {
        val userId = currentUserId()
        if (userId == null || userId != status.userId) {
            throw IllegalStateException("You can only delete your own status.")
        }
        rpc.rpc("delete_status_update", mapOf("p_status_id" to status.id))
    }

    /** Exact port of _insert (24h expiry, inserted row returned). */
    private suspend fun insert(
        text: String,
        mediaType: String,
        mediaPath: String? = null,
        mediaName: String? = null,
        mimeType: String? = null,
        mediaSize: Long = 0,
        durationSeconds: Long = 0,
    ): StatusRecord {
        val userId = currentUserId()
            ?: throw IllegalStateException("Authentication required.")
        val expiresAt = Instant.now().plus(java.time.Duration.ofHours(24))
        val rows = queryClient.from("status_updates").insert(
            mapOf(
                "user_id" to userId,
                "content" to text,
                "media_path" to mediaPath,
                "media_type" to mediaType,
                "mime_type" to mimeType,
                "media_name" to mediaName,
                "media_size" to mediaSize,
                "duration_seconds" to durationSeconds,
                "expires_at" to expiresAt.toString(),
            ),
        )
        val row = rows.firstOrNull()
            ?: throw IllegalStateException("Status insert returned no row.")
        return fromRow(row)
    }

    companion object {
        const val BUCKET = "status-media"
        const val STORAGE_LIMIT_MB = 50

        /** Exact port of _fromRow. */
        fun fromRow(row: Map<String, Any?>): StatusRecord = StatusRecord(
            id = row["id"]?.toString() ?: "",
            userId = row["user_id"]?.toString() ?: "",
            text = row["content"]?.toString() ?: "",
            mediaType = row["media_type"]?.toString() ?: "text",
            mediaPath = row["media_path"]?.toString(),
            mediaName = row["media_name"]?.toString(),
            mediaSize = (row["media_size"] as? Number)?.toLong()
                ?: row["media_size"]?.toString()?.toLongOrNull() ?: 0,
            durationSeconds = (row["duration_seconds"] as? Number)?.toLong() ?: 0,
            createdAt = com.example.chat.core.util.parseIsoToInstant(
                row["created_at"]?.toString(),
            ) ?: Instant.now(),
            expiresAt = com.example.chat.core.util.parseIsoToInstant(
                row["expires_at"]?.toString(),
            ) ?: Instant.now(),
            deletedAt = row["deleted_at"]?.let {
                com.example.chat.core.util.parseIsoToInstant(it.toString())
            },
        )

        /** Exact port of _safeFileName. */
        fun safeFileName(source: String): String {
            val cleaned = source
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .replace(Regex("_+"), "_")
            if (cleaned.isEmpty()) return "status"
            return if (cleaned.length > 120) cleaned.substring(cleaned.length - 120) else cleaned
        }

        /** Exact port of _fallbackMime. */
        fun fallbackMime(type: String): String = when (type) {
            "image" -> "image/jpeg"
            "video" -> "video/mp4"
            "audio" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }
}
