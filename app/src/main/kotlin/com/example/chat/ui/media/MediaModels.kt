package com.example.chat.ui.media

import java.io.File
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Exact port of MediaCategory from
 *  lib/features/media/models/media_file_descriptor.dart. */
enum class MediaCategory {
    image,
    video,
    audio,
    document,
    pdf,
    archive,
    other,
}

/** Exact port of MediaFileDescriptor from media_file_descriptor.dart.
 *  Dart DateTime -> Instant. */
data class MediaFileDescriptor(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: MediaCategory,
    val modifiedAt: Instant,
    val thumbnailPath: String? = null,
    val durationSeconds: Int = 0,
    val pageCount: Int = 0,
    val isEncrypted: Boolean = false,
    val isFavorite: Boolean = false,
    val metadata: Map<String, Any?> = emptyMap(),
) {
    val humanReadableSize: String
        get() {
            if (sizeBytes < 1024) return "$sizeBytes B"
            if (sizeBytes < 1024 * 1024) return "%.1f KB".format(sizeBytes / 1024.0)
            if (sizeBytes < 1024 * 1024 * 1024) return "%.1f MB".format(sizeBytes / (1024.0 * 1024))
            return "%.2f GB".format(sizeBytes / (1024.0 * 1024 * 1024))
        }

    val extension: String
        get() {
            val idx = name.lastIndexOf('.')
            if (idx == -1 || idx == name.length - 1) return ""
            return name.substring(idx + 1).lowercase()
        }

    val exists: Boolean get() = File(path).exists()
}

/** Exact port of PlaybackStatus from
 *  lib/features/media/models/media_playback_state.dart. */
enum class PlaybackStatus {
    idle,
    loading,
    playing,
    paused,
    stopped,
    error,
}

/** Exact port of ChatyRepeatMode from media_playback_state.dart. */
enum class ChatyRepeatMode {
    off,
    one,
    all,
}

/** Exact port of MediaPlaybackState from media_playback_state.dart.
 *  Dart Duration -> kotlin.time.Duration. */
data class MediaPlaybackState(
    val status: PlaybackStatus = PlaybackStatus.idle,
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
    val bufferedPosition: Duration = Duration.ZERO,
    val speed: Double = 1.0,
    val isShuffle: Boolean = false,
    val repeatMode: ChatyRepeatMode = ChatyRepeatMode.off,
    val activeItem: MediaFileDescriptor? = null,
    val queue: List<MediaFileDescriptor> = emptyList(),
    val queueIndex: Int = 0,
    val errorMessage: String? = null,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.playing
    val hasActiveItem: Boolean get() = activeItem != null
    val hasNext: Boolean get() = queue.isNotEmpty() && queueIndex < queue.size - 1
    val hasPrevious: Boolean get() = queue.isNotEmpty() && queueIndex > 0

    val progress: Double
        get() {
            if (duration.inWholeMilliseconds == 0L) return 0.0
            return (position.inWholeMilliseconds.toDouble() / duration.inWholeMilliseconds)
                .coerceIn(0.0, 1.0)
        }
}

// ── conversion_models.dart ────────────────────────────────────────────────

/** Exact port of ConversionType from
 *  lib/features/media/models/conversion_models.dart. */
enum class ConversionType {
    imagesToPdf,
    pdfToImages,
    textToPdf,
    pdfToText,
    pdfMerge,
    pdfSplit,
    pdfRotate,
    pdfExtractPages,
}

/** Exact port of ConversionStatus from conversion_models.dart. */
enum class ConversionStatus {
    idle,
    processing,
    completed,
    cancelled,
    failed,
}

/** Exact port of ConversionRequest from conversion_models.dart. */
data class ConversionRequest(
    val id: String,
    val type: ConversionType,
    val sourceFilePaths: List<String>,
    val outputDirectory: String,
    val outputFileName: String,
    val options: Map<String, Any?> = emptyMap(),
)

/** Exact port of ConversionProgress from conversion_models.dart. */
data class ConversionProgress(
    val fraction: Double,
    val currentOperation: String,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
)

/** Exact port of ConversionResult from conversion_models.dart. */
data class ConversionResult(
    val success: Boolean,
    val outputFilePaths: List<String> = emptyList(),
    val totalBytes: Long = 0,
    val elapsed: Duration = Duration.ZERO,
    val errorMessage: String? = null,
)

// ── reader_models.dart ────────────────────────────────────────────────────

/** Exact port of DocumentOutlineNode from
 *  lib/features/media/models/reader_models.dart. */
data class DocumentOutlineNode(
    val title: String,
    val pageNumber: Int,
    val depth: Int = 0,
    val children: List<DocumentOutlineNode> = emptyList(),
)

/** Exact port of DocumentBookmark from reader_models.dart. */
data class DocumentBookmark(
    val id: String,
    val pageNumber: Int,
    val label: String,
    val createdAt: Instant,
)

/** Exact port of DocumentSearchMatch from reader_models.dart. */
data class DocumentSearchMatch(
    val pageNumber: Int,
    val matchIndex: Int,
    val snippet: String,
    val startOffset: Int,
    val endOffset: Int,
)

/** Exact port of ReaderZoomMode from reader_models.dart. */
enum class ReaderZoomMode {
    fitWidth,
    fitPage,
    manual,
}
