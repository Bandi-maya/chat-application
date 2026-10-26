package com.example.chat

import android.graphics.Bitmap
import android.graphics.Color
import android.media.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native local fixtures only, no backend, real media, uploads or OS settings mutations. */
@RunWith(AndroidJUnit4::class)
class MediaFollowOnProofTest {
    @Test fun centerCropStaysInsideSourceAndUsesRequestedAspect() {
        val crop = editorCenterCrop(240, 360, 1f)
        assertEquals(240, crop.width()); assertEquals(240, crop.height()); assertEquals(60, crop.top)
        val landscape = editorCenterCrop(240, 360, 4f / 3f)
        assertEquals(240, landscape.width()); assertEquals(180, landscape.height())
    }
    @Test fun trimRejectsInvalidRangeWithoutCreatingOutput() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.cacheDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        assertTrue(runCatching { trimMediaCopy(context, "file:///does-not-exist", 1000, 500, false) }.isFailure)
        val after = context.cacheDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        assertEquals(before, after)
    }
    @Test fun nativeAacTrimCreatesPlayableCopyAndPreservesOriginalBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "synthetic-silence-${System.nanoTime()}.m4a")
        createSilentAac(source)
        val original = source.readBytes()
        val result = trimMediaCopy(context, source.toURI().toString(), 500, 1500, false)
        assertTrue(result.file.isFile && result.file.length() > 0)
        assertNotEquals(source.path, result.file.path)
        assertArrayEquals(original, source.readBytes())
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(result.file.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Trimmed duration $duration", duration in 700..1400)
        } finally { retriever.release(); source.delete(); result.file.delete() }
    }
    @Test fun syntheticAvcTrimPreservesAudioOrientationAndSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = File(context.cacheDir, "synthetic-aac-${System.nanoTime()}.m4a")
        val source = File(context.cacheDir, "synthetic-avc-${System.nanoTime()}.mp4")
        try {
            createSilentAac(audio)
            createAvcWithAudio(source, audio)
            val bytes = source.readBytes()
            val result = trimMediaCopy(context, source.toURI().toString(), 400, 1400, true)
            try {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(result.file.path)
                    val mimes = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
                    assertTrue("Video track preserved", mimes.any { it == "video/avc" })
                    assertTrue("Audio track preserved", mimes.any { it == "audio/mp4a-latm" })
                    val videoTrack = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "video/avc" }
                    extractor.selectTrack(videoTrack)
                    assertTrue("First exported video sample is decodable keyframe", extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    assertEquals("Video starts at zero-based cut origin", 0L, extractor.sampleTime)
                    assertTrue("Reported real start is not after request", result.actualStartMs in 0..400)
                    assertTrue("Reported sample span is bounded", result.durationMs in 500..1600)
                } finally { extractor.release() }
                val metadata = MediaMetadataRetriever()
                try {
                    metadata.setDataSource(result.file.path)
                    assertEquals("90", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
                    val decodedFrame = metadata.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    assertNotNull("Export actually decodes a frame", decodedFrame)
                    decodedFrame?.recycle()
                    assertTrue(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 500..1600)
                } finally { metadata.release() }
                assertArrayEquals(bytes, source.readBytes())
            } finally { result.file.delete() }
        } finally { source.delete(); audio.delete() }
    }
    @Test fun failedAndCancelledTrimLeaveNoPartialOutputs() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bad = File(context.cacheDir, "synthetic-invalid-video.bin").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        fun outputs() = context.cacheDir.listFiles()?.filter { it.name.startsWith("chaty_trimmed-") }?.map { it.name }?.toSet().orEmpty()
        val before = outputs()
        assertTrue(runCatching { trimMediaCopy(context, bad.toURI().toString(), 0, 1000, true) }.isFailure)
        assertEquals(before, outputs())
        bad.delete()
        val source = File(context.cacheDir, "synthetic-cancel-audio.m4a")
        try {
            createSilentAac(source)
            val job = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                trimMediaCopy(context, source.toURI().toString(), 0, 1500, false)
            }
            job.cancel(); job.join()
            assertEquals(before, outputs())
        } finally { source.delete() }
    }
    @Test fun cancelAfterFirstWrittenSampleDeletesPartialMuxerOutput() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "synthetic-midexport-cancel.m4a")
        createSilentAac(source)
        val original = source.readBytes()
        fun outputs() = context.cacheDir.listFiles()?.filter { it.name.startsWith("chaty_trimmed-") }?.map { it.name }?.toSet().orEmpty()
        val before = outputs()
        var didWrite = false
        val job = launch {
            trimMediaCopy(context, source.toURI().toString(), 0, 1500, false, onSampleWritten = {
                didWrite = true
                throw kotlinx.coroutines.CancellationException("Synthetic cancellation after actual muxer write")
            })
        }
        job.join()
        assertTrue("Test reached real written sample before cancellation", didWrite)
        assertTrue(job.isCancelled)
        assertEquals("No partial trim copy after cancelled write", before, outputs())
        assertArrayEquals(original, source.readBytes())
        source.delete()
    }

    internal fun createAvcWithAudio(file: File, audio: File) {
        val codec = MediaCodec.createEncoderByType("video/avc")
        val format = MediaFormat.createVideoFormat("video/avc", 64, 64).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 10)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val aac = MediaExtractor()
        var started = false
        try {
            aac.setDataSource(audio.path)
            aac.selectTrack(0)
            val audioTrack = muxer.addTrack(aac.getTrackFormat(0))
            muxer.setOrientationHint(90)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            var frame = 0
            var inputDone = false
            var outputDone = false
            var videoTrack = -1
            val info = MediaCodec.BufferInfo()
            val deadline = System.currentTimeMillis() + 15000
            while (!outputDone) {
                check(System.currentTimeMillis() < deadline) { "Synthetic AVC encoder timeout" }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val size = if (frame < 20) 64 * 64 * 3 / 2 else 0
                        codec.getInputBuffer(index)!!.apply { clear(); repeat(size) { put(128.toByte()) } }
                        codec.queueInputBuffer(index, 0, size, frame * 100000L, if (size == 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        frame++; inputDone = size == 0
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 10000)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { videoTrack = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                else if (output >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) muxer.writeSampleData(videoTrack, codec.getOutputBuffer(output)!!, info)
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                }
            }
            val buffer = java.nio.ByteBuffer.allocate(65536)
            while (aac.sampleTrackIndex >= 0) {
                buffer.clear()
                val count = aac.readSampleData(buffer, 0)
                if (count < 0) break
                info.set(0, count, aac.sampleTime, if (aac.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(audioTrack, buffer, info)
                if (!aac.advance()) break
            }
            muxer.stop(); started = false
        } finally {
            if (started) runCatching { muxer.stop() }
            aac.release(); muxer.release(); runCatching { codec.stop() }; codec.release()
        }
    }

    internal fun createSilentAac(file: File) {
        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        }
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            var frames = 0
            var inputDone = false
            var outputDone = false
            var track = -1
            val info = MediaCodec.BufferInfo()
            val deadline = System.currentTimeMillis() + 15000
            while (!outputDone) {
                check(System.currentTimeMillis() < deadline) { "Synthetic AAC encoder timeout" }
                if (!inputDone) {
                    val input = codec.dequeueInputBuffer(10000)
                    if (input >= 0) {
                        val count = minOf(1024, 88200 - frames)
                        codec.getInputBuffer(input)!!.apply { clear(); repeat(count) { putShort(0) } }
                        codec.queueInputBuffer(input, 0, count * 2, frames * 1000000L / 44100,
                            if (count == 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        frames += count; inputDone = count == 0
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 10000)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                else if (output >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(started); muxer.writeSampleData(track, codec.getOutputBuffer(output)!!, info)
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                }
            }
            muxer.stop(); started = false
        } finally {
            if (started) runCatching { muxer.stop() }
            muxer.release(); runCatching { codec.stop() }; codec.release()
        }
    }
}
