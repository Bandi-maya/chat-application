package com.example.chat.data.media

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.chat.data.local.EditKind
import com.example.chat.data.local.EditedOutputValidator
import java.io.File

/** Native validator (moved from the media worker's proposal). NOT compiled or run by this task. */
object AndroidEditedOutputValidator : EditedOutputValidator {
    override fun isValid(file: File, kind: EditKind): Boolean = when (kind) {
        EditKind.IMAGE_JPEG -> jpeg(file)
        EditKind.VIDEO_MP4 -> mp4(file, requireVideo = true)
        EditKind.AUDIO_MP4 -> mp4(file, requireVideo = false)
    }

    internal fun jpeg(file: File): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth > 0 && bounds.outHeight > 0)
        // Bounds alone accept truncated streams, so also decode a bounded sample.
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
        val image = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        try { image.width > 0 && image.height > 0 } finally { image.recycle() }
    }.getOrDefault(false)

    internal fun mp4(file: File, requireVideo: Boolean): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            var audio = false
            var video = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    require(mime in setOf("video/avc", "video/hevc", "video/mp4v-es"))
                    require(format.getInteger(MediaFormat.KEY_WIDTH) > 0 && format.getInteger(MediaFormat.KEY_HEIGHT) > 0)
                    video = true
                } else if (mime.startsWith("audio/")) {
                    require(mime == "audio/mp4a-latm")
                    require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) > 0 && format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) > 0)
                    audio = true
                }
                extractor.selectTrack(i)
            }
            require(if (requireVideo) video else audio && !video)
            require(extractor.sampleTime >= 0)
            true
        } finally { extractor.release() }
    }.getOrDefault(false)
}
