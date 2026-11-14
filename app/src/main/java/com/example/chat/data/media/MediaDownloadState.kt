package com.example.chat.data.media

/** Pure per-attachment download state for the tap-to-download tile. No Android imports, JVM-testable.
 *  Identity (account session + conversation + message + attachment) is owned by [MediaDownloadManager];
 *  this only describes what the tile shows. */
sealed class MediaDownloadState {
    /** Not on the device. [totalBytes] is the sender-declared size when known. */
    data class Idle(val totalBytes: Long?) : MediaDownloadState()
    data class Queued(val totalBytes: Long?) : MediaDownloadState()
    data class Downloading(val doneBytes: Long, val totalBytes: Long?, val bytesPerSecond: Long) : MediaDownloadState()
    /** User paused, or the network dropped ([reason]). Partial bytes are kept for resume. */
    data class Paused(val doneBytes: Long, val totalBytes: Long?, val reason: PauseReason) : MediaDownloadState()
    data class Failed(val doneBytes: Long, val totalBytes: Long?, val reason: FailReason) : MediaDownloadState()
    object Done : MediaDownloadState()

    enum class PauseReason { USER, NO_NETWORK, DATA_SAVER }
    enum class FailReason { NETWORK, SERVER_GONE, TOO_LARGE, CORRUPT, NOT_ALLOWED, STORAGE }

    val isActive: Boolean get() = this is Queued || this is Downloading
}

sealed class MediaDownloadEvent {
    object Start : MediaDownloadEvent()
    data class Progress(val doneBytes: Long, val totalBytes: Long?, val bytesPerSecond: Long) : MediaDownloadEvent()
    object UserPause : MediaDownloadEvent()
    object NetworkLost : MediaDownloadEvent()
    object NetworkBack : MediaDownloadEvent()
    object UserCancel : MediaDownloadEvent()
    data class Error(val reason: MediaDownloadState.FailReason) : MediaDownloadEvent()
    object Finished : MediaDownloadEvent()
}

object MediaDownloadReducer {
    private fun done(s: MediaDownloadState): Long = when (s) {
        is MediaDownloadState.Downloading -> s.doneBytes
        is MediaDownloadState.Paused -> s.doneBytes
        is MediaDownloadState.Failed -> s.doneBytes
        else -> 0L
    }
    private fun total(s: MediaDownloadState): Long? = when (s) {
        is MediaDownloadState.Idle -> s.totalBytes
        is MediaDownloadState.Queued -> s.totalBytes
        is MediaDownloadState.Downloading -> s.totalBytes
        is MediaDownloadState.Paused -> s.totalBytes
        is MediaDownloadState.Failed -> s.totalBytes
        MediaDownloadState.Done -> null
    }

    fun reduce(s: MediaDownloadState, e: MediaDownloadEvent): MediaDownloadState {
        if (s == MediaDownloadState.Done) return s              // terminal: late events cannot reopen it
        return when (e) {
            MediaDownloadEvent.Start -> when (s) {
                is MediaDownloadState.Downloading, is MediaDownloadState.Queued -> s
                else -> MediaDownloadState.Queued(total(s))
            }
            is MediaDownloadEvent.Progress ->
                if (s is MediaDownloadState.Downloading || s is MediaDownloadState.Queued)
                    MediaDownloadState.Downloading(maxOf(e.doneBytes, done(s)), e.totalBytes ?: total(s), e.bytesPerSecond.coerceAtLeast(0))
                else s                                           // progress after pause/cancel is dropped
            MediaDownloadEvent.UserPause ->
                if (s.isActive) MediaDownloadState.Paused(done(s), total(s), MediaDownloadState.PauseReason.USER) else s
            MediaDownloadEvent.NetworkLost ->
                if (s.isActive) MediaDownloadState.Paused(done(s), total(s), MediaDownloadState.PauseReason.NO_NETWORK) else s
            // Only a network pause resumes by itself. A user pause stays paused.
            MediaDownloadEvent.NetworkBack ->
                if (s is MediaDownloadState.Paused && s.reason == MediaDownloadState.PauseReason.NO_NETWORK) MediaDownloadState.Queued(s.totalBytes) else s
            MediaDownloadEvent.UserCancel -> MediaDownloadState.Idle(total(s))
            is MediaDownloadEvent.Error ->
                if (s.isActive || s is MediaDownloadState.Paused) MediaDownloadState.Failed(done(s), total(s), e.reason) else s
            MediaDownloadEvent.Finished -> MediaDownloadState.Done
        }
    }
}

/** Text for the tile. Decimal MB like Android and WhatsApp. */
object MediaDownloadFormat {
    fun size(bytes: Long): String = when {
        bytes < 1_000 -> "$bytes B"
        bytes < 1_000_000 -> "%.0f KB".format(bytes / 1_000.0)
        bytes < 1_000_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "%.2f GB".format(bytes / 1_000_000_000.0)
    }
    /** MessageAttachment.size is a String: plain byte count or "2.4 MB" style. Unknown -> null (never guessed). */
    fun parseBytes(text: String?): Long? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        t.toLongOrNull()?.let { return it.takeIf { n -> n >= 0 } }
        val m = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*([KMG]?)B", RegexOption.IGNORE_CASE).matchEntire(t) ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2].uppercase()) { "K" -> 1_000.0; "M" -> 1_000_000.0; "G" -> 1_000_000_000.0; else -> 1.0 }
        return (n * mult).toLong()
    }
    fun percent(done: Long, total: Long?): Int? =
        total?.takeIf { it > 0 }?.let { ((done.coerceIn(0, it) * 100) / it).toInt() }
    fun speed(bytesPerSecond: Long): String? =
        if (bytesPerSecond <= 0) null else "${size(bytesPerSecond)}/s"
    /** "250 KB/s  about 6 s left" - either part may be missing. */
    fun speedAndEta(done: Long, total: Long?, bytesPerSecond: Long): String? =
        listOfNotNull(speed(bytesPerSecond), eta(done, total, bytesPerSecond)).takeIf { it.isNotEmpty() }?.joinToString("  ")
    fun eta(done: Long, total: Long?, bytesPerSecond: Long): String? {
        if (total == null || bytesPerSecond <= 0) return null
        val s = ((total - done).coerceAtLeast(0) / bytesPerSecond)
        return when { s < 60 -> "about ${maxOf(s, 1)} s left"; s < 3600 -> "about ${s / 60} min left"; else -> "about ${s / 3600} h left" }
    }
    fun label(s: MediaDownloadState): String = when (s) {
        is MediaDownloadState.Idle -> s.totalBytes?.let { size(it) } ?: "Tap to download"
        is MediaDownloadState.Queued -> "Waiting to download"
        is MediaDownloadState.Downloading ->
            percent(s.doneBytes, s.totalBytes)?.let { "${size(s.doneBytes)} of ${size(s.totalBytes!!)}  $it%" } ?: size(s.doneBytes)
        is MediaDownloadState.Paused -> {
            val p = percent(s.doneBytes, s.totalBytes)?.let { " $it%" } ?: ""
            when (s.reason) {
                MediaDownloadState.PauseReason.USER -> "Paused$p"
                MediaDownloadState.PauseReason.NO_NETWORK -> "Paused$p  waiting for network"
                MediaDownloadState.PauseReason.DATA_SAVER -> "Paused$p  data saver on"
            }
        }
        is MediaDownloadState.Failed -> when (s.reason) {
            MediaDownloadState.FailReason.SERVER_GONE -> "No longer available"
            MediaDownloadState.FailReason.TOO_LARGE -> "Too large to download"
            MediaDownloadState.FailReason.NOT_ALLOWED -> "Not available"
            MediaDownloadState.FailReason.STORAGE -> "Not enough storage"
            MediaDownloadState.FailReason.CORRUPT -> "Damaged. Tap to retry"
            MediaDownloadState.FailReason.NETWORK -> "Couldn't download. Tap to retry"
        }
        MediaDownloadState.Done -> ""
    }
}
