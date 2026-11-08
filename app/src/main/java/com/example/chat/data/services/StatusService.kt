package com.example.chat.data.services

import com.example.chat.data.api.ApiStatusClient
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import com.example.chat.domain.models.PrivacyPublication
import com.example.chat.domain.models.PrivacyPublicationPolicy
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import org.json.JSONObject

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

/** Thrown by every StatusService write while the status identity is unbound (fail closed). */
class StatusIdentityUnboundException : IllegalStateException(
    "Status isn't available for this account connection yet. Nothing was sent.",
)

data class StatusViewer(
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarInitials: String,
    val avatarColorHex: String,
    val viewedAt: Instant,
)

/**
 * Status publishing, viewing and deletion on the live backend's status
 * plane (the /status routes): the server stores an opaque payload blob per post,
 * enforces a hard 24h expiry, and tracks views. The payload here is a JSON
 * document (see [encodePayload] / [decodePayload]) carrying the text and,
 * for media posts, the URL of the previously uploaded media file.
 *
 * The service keeps the fail-closed contract of the original port: while
 * [identityBound] is false every write throws [StatusIdentityUnboundException]
 * with zero network calls, and reads return empty.
 *
 * Preferences are seams: the full preferences controller lands with M7/M8.
 */
class StatusService(
    private val api: ApiStatusClient,
    /** Uploads media bytes and returns their public URL (custom backend /chat/upload). */
    private val mediaUpload: suspend (filename: String, bytes: ByteArray, mimeType: String) -> String,
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
    /**
     * True only when the signed-in app account is proven to own the backend
     * session this service writes through. The default is false: every write
     * below fails closed with zero network calls. Do not pass a constant
     * true; wire a real, verified binding when one exists.
     */
    private val identityBound: () -> Boolean = { false },
) {
    /** True only when status reads and writes may run. UI uses it for preflight and for the unavailable state. */
    fun isAvailable(): Boolean = identityBound()

    private fun requireBound() {
        if (!identityBound()) throw StatusIdentityUnboundException()
    }

    /**
     * Active statuses, newest first. The live plane has no realtime stream
     * for status, so this polls the feed on a fixed interval; it never
     * fabricates rows, and on a failed poll it re-serves the last good feed.
     */
    fun watchActiveStatuses(
        keepDeleted: () -> Boolean = { false },
        pollMillis: Long = 60_000L,
    ): kotlinx.coroutines.flow.Flow<List<StatusRecord>> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(queryActive(keepDeleted))
            kotlinx.coroutines.delay(pollMillis)
        }
    }

    private suspend fun queryActive(keepDeleted: () -> Boolean): List<StatusRecord> {
        if (!identityBound()) { lastActive = emptyList(); return emptyList() }
        if (currentUserId() == null) return emptyList()
        val result: List<StatusRecord> = try {
            val feed = api.feed() ?: throw IllegalStateException("feed failed")
            val now = Instant.now()
            feed.flatMap { entry ->
                entry.posts.mapNotNull { post ->
                    val rec = fromPayload(post, entry)
                    // Server purges expired rows; the filter stays as clock-skew protection.
                    if (rec.expiresAt.isAfter(now)) rec else null
                }
            }.sortedByDescending { it.createdAt }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            lastActive
        }
        lastActive = result
        return result
    }

    @Volatile private var lastActive: List<StatusRecord> = emptyList()

    /** Publish a text status. */
    suspend fun publishText(text: String): StatusRecord {
        // Offline demo: status posts need the server; refuse.
        if (com.example.chat.data.demo.DemoMode.active) throw IllegalStateException(com.example.chat.data.demo.DemoMode.UNAVAILABLE_MESSAGE)
        requireBound()
        val value = text.trim()
        if (value.isEmpty()) throw IllegalStateException("Enter status text before publishing.")
        return insert(text = value, mediaType = "text")
    }

    /**
     * Publish a media status: same validation and size limits as the
     * original port, then upload-then-post. If the post fails after the
     * upload succeeded the media file is orphaned server-side (the backend
     * has no per-file delete); the post itself is never half-created.
     */
    suspend fun publishMediaBytes(
        bytes: ByteArray,
        displayName: String,
        mediaType: String,
        text: String = "",
        mimeType: String? = null,
        durationSeconds: Long = 0,
    ): StatusRecord {
        requireBound()
        if (mediaType == "audio" && !gbAudioStatusEnabled()) {
        // Offline demo: status posts need the server; refuse.
        if (com.example.chat.data.demo.DemoMode.active) throw IllegalStateException(com.example.chat.data.demo.DemoMode.UNAVAILABLE_MESSAGE)
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

        currentUserId() ?: throw IllegalStateException("Authentication required.")
        val safeName = safeFileName(displayName)
        val effectiveMime = mimeType ?: fallbackMime(mediaType)

        val url = mediaUpload(safeName, bytes, effectiveMime)
        return insert(
            text = text.trim(),
            mediaType = mediaType,
            mediaPath = url,
            mediaName = safeName,
            mediaSize = size,
            mimeType = effectiveMime,
            durationSeconds = durationSeconds,
        )
    }

    /** Media URLs on this plane are already absolute; kept for interface parity. */
    suspend fun createSignedUrl(path: String, expiresInSeconds: Int = 900): String {
        requireBound()
        return path
    }

    /** Gated on preferences readiness, the publication policy, and the gb
     *  hide-view flag; silently no-ops when publication is suppressed. */
    suspend fun markViewed(status: StatusRecord): Boolean {
        requireBound()
        if (!preferencesReady()) return false
        val mayPublish = publicationPolicy.allows(
            PrivacyPublication.statusView, privacy(),
        ) && !gbHideStatusView()
        if (!mayPublish) return false
        return api.recordView(status.id)
    }

    /** Who viewed an own status (server enforces ownership). Errors -> empty. */
    suspend fun viewersFor(statusId: String): List<StatusViewer> {
        requireBound()
        return try {
            (api.views(statusId) ?: emptyList()).map { v ->
                StatusViewer(
                    userId = v.userId,
                    displayName = v.displayName.ifEmpty { "Chaty user" },
                    username = v.username.ifEmpty { "user" },
                    avatarInitials = initialsOf(v.displayName, v.username),
                    avatarColorHex = "0xFF6366F1",
                    viewedAt = com.example.chat.core.util.parseIsoToInstant(v.viewedAt) ?: Instant.now(),
                )
            }
        } catch (error: Throwable) {
            emptyList()
        }
    }

    /** Delete an own status (own-status guard + server delete). */
    suspend fun deleteStatus(status: StatusRecord) {
        requireBound()
        val userId = currentUserId()
        if (userId == null || userId != status.userId) {
            throw IllegalStateException("You can only delete your own status.")
        }
        if (!api.delete(status.id)) {
            throw IllegalStateException("Status could not be deleted.")
        }
    }

    /** POST the payload and shape the returned record (24h expiry comes from the server). */
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
        val payload = encodePayload(
            text = text,
            mediaType = mediaType,
            mediaPath = mediaPath,
            mediaName = mediaName,
            mimeType = mimeType,
            mediaSize = mediaSize,
            durationSeconds = durationSeconds,
        )
        val (id, expiresAtRaw) = api.postStatus(payload)
            ?: throw IllegalStateException("Status could not be published.")
        return StatusRecord(
            id = id,
            userId = userId,
            text = text,
            mediaType = mediaType,
            mediaPath = mediaPath,
            mediaName = mediaName,
            mediaSize = mediaSize,
            durationSeconds = durationSeconds,
            createdAt = Instant.now(),
            expiresAt = com.example.chat.core.util.parseIsoToInstant(expiresAtRaw)
                ?: Instant.now().plus(java.time.Duration.ofHours(24)),
            deletedAt = null,
        )
    }

    companion object {
        const val STORAGE_LIMIT_MB = 50

        /** Payload schema version written by [encodePayload]. */
        const val PAYLOAD_VERSION = 1

        /** Dart num.round(): half away from zero (Math.round is half toward +infinity).
         *  Finite values only; Dart throws on NaN/Infinity, which is not ported. */
        fun dartRound(n: Number): Long {
            val d = n.toDouble()
            return if (d < 0) -Math.round(-d) else Math.round(d)
        }

        /** Serialize a status to the opaque payload the server stores. */
        fun encodePayload(
            text: String,
            mediaType: String,
            mediaPath: String? = null,
            mediaName: String? = null,
            mimeType: String? = null,
            mediaSize: Long = 0,
            durationSeconds: Long = 0,
        ): String {
            val o = JSONObject()
            o.put("v", PAYLOAD_VERSION)
            o.put("media_type", mediaType)
            o.put("content", text)
            if (mediaPath != null) o.put("media_path", mediaPath)
            if (mediaName != null) o.put("media_name", mediaName)
            if (mimeType != null) o.put("mime_type", mimeType)
            if (mediaSize > 0) o.put("media_size", mediaSize)
            if (durationSeconds > 0) o.put("duration_seconds", durationSeconds)
            return o.toString()
        }

        /** Rebuild a record from a feed post. Payloads written by older or
         *  foreign clients that are not our JSON shape surface as plain text. */
        fun fromPayload(
            post: ApiStatusClient.StatusPostEntry,
            author: ApiStatusClient.StatusFeedEntry,
        ): StatusRecord {
            val o = runCatching { JSONObject(post.payload) }.getOrNull()
            return StatusRecord(
                id = post.id,
                userId = author.userId,
                text = o?.optString("content") ?: post.payload,
                mediaType = o?.optString("media_type")?.takeIf { it.isNotEmpty() } ?: "text",
                mediaPath = o?.optString("media_path")?.takeIf { it.isNotEmpty() },
                mediaName = o?.optString("media_name")?.takeIf { it.isNotEmpty() },
                mediaSize = o?.optLong("media_size") ?: 0,
                durationSeconds = o?.optLong("duration_seconds") ?: 0,
                createdAt = com.example.chat.core.util.parseIsoToInstant(post.createdAt) ?: Instant.now(),
                expiresAt = com.example.chat.core.util.parseIsoToInstant(post.expiresAt) ?: Instant.now(),
                deletedAt = null,
            )
        }

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

        private fun initialsOf(displayName: String, username: String): String {
            val base = displayName.ifEmpty { username }.trim()
            if (base.isEmpty()) return "CU"
            val parts = base.split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
                else -> base.take(2).uppercase()
            }
        }
    }
}
