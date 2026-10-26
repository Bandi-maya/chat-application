package com.example.chat.ui.chats.detail

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaCodec
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

internal data class MediaTrimResult(val file: File, val actualStartMs: Long, val durationMs: Long)

/** Lossless sample-copy trim. Video starts at a sync sample, not an invented frame-exact point.
 * Supports MP4 AVC/HEVC/MPEG4 video and AAC audio only. Unsupported formats fail explicitly.
 * Original is read-only; failed/cancelled outputs are deleted. No account or upload operation. */
internal suspend fun trimMediaCopy(context: Context, source: String, startMs: Long, endMs: Long, video: Boolean, onSampleWritten: suspend (Int) -> Unit = {}): MediaTrimResult {
    var pendingOutput: File? = null
    try { return withContext(Dispatchers.IO) {
    require(startMs >= 0 && endMs > startMs) { "End must be after start." }
    val extractor = MediaExtractor()
    val out = File(context.cacheDir, "chaty_trimmed-${java.util.UUID.randomUUID()}.${if (video) "mp4" else "m4a"}")
    pendingOutput = out
    var muxer: MediaMuxer? = null
    var started = false
    try {
        val uri = Uri.parse(source)
        if (uri.scheme in listOf("http", "https")) extractor.setDataSource(source)
        else extractor.setDataSource(context, uri, null)
        val outputMuxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = outputMuxer
        val tracks = mutableMapOf<Int, Int>()
        var hasVideo = false
        var videoIndex: Int? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
            if (!video && mime.startsWith("video/")) continue
            require(mime in setOf("video/avc", "video/hevc", "video/mp4v-es", "audio/mp4a-latm")) { "This format ($mime) cannot be trimmed losslessly here. Original kept unchanged." }
            if (mime.startsWith("video/")) { hasVideo = true; videoIndex = index }
            extractor.selectTrack(index)
            tracks[index] = outputMuxer.addTrack(format)
        }
        require(tracks.isNotEmpty() && (!video || hasVideo)) { "No supported media tracks found." }
        if (video) {
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, uri)
                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let { outputMuxer.setOrientationHint(it) }
            } finally { metadata.release() }
        }
        // Determine video cut from the VIDEO sync sample alone. Interleaved audio
        // must not shift the origin earlier than the chosen decodable video frame.
        val primaryVideo = videoIndex
        if (primaryVideo != null) tracks.keys.filter { it != primaryVideo }.forEach { extractor.unselectTrack(it) }
        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val actualStartUs = extractor.sampleTime.coerceAtLeast(0)
        if (primaryVideo != null) {
            require(extractor.sampleTrackIndex == primaryVideo && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) { "No decodable video keyframe at this cut." }
            tracks.keys.filter { it != primaryVideo }.forEach { extractor.selectTrack(it) }
            extractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }
        require(actualStartUs < endMs * 1000) { "No sample in this range." }
        outputMuxer.start(); started = true
        val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        var lastUs = actualStartUs
        var firstVideoWritten = false
        var writtenSamples = 0
        while (extractor.sampleTrackIndex >= 0) {
            coroutineContext.ensureActive()
            val sampleUs = extractor.sampleTime
            if (sampleUs < 0 || sampleUs >= endMs * 1000) break
            require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Protected media cannot be trimmed here." }
            buffer.clear()
            val count = extractor.readSampleData(buffer, 0)
            if (count < 0) break
            require(count <= buffer.capacity()) { "This sample is too large to trim safely." }
            val target = tracks[extractor.sampleTrackIndex]
            if (target != null && sampleUs >= actualStartUs) {
                info.set(0, count, sampleUs - actualStartUs,
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                if (extractor.sampleTrackIndex == primaryVideo && !firstVideoWritten) {
                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) { "First video sample is not a keyframe." }
                    firstVideoWritten = true
                }
                outputMuxer.writeSampleData(target, buffer, info)
                writtenSamples++
                onSampleWritten(writtenSamples)
                coroutineContext.ensureActive()
                lastUs = maxOf(lastUs, sampleUs)
            }
            if (!extractor.advance()) break
        }
        require(writtenSamples > 0 && (!video || firstVideoWritten)) { "No decodable samples in this range." }
        coroutineContext.ensureActive()
        outputMuxer.stop(); started = false
        require(out.length() > 0) { "The trimmed file is empty." }
        MediaTrimResult(out, actualStartUs / 1000, (lastUs - actualStartUs) / 1000)
    } catch (e: Throwable) {
        if (started) runCatching { muxer?.stop() }
        out.delete()
        throw e
    } finally {
        extractor.release()
        muxer?.release()
    }
}

    } catch (e: Throwable) {
        // Includes cancellation delivered while withContext returns to the UI dispatcher.
        pendingOutput?.delete()
        throw e
    }
}
