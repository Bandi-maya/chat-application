package com.example.chat.ui.chats.detail

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.chat.core.audio.PcmEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.nio.ByteOrder

/** Local decrypted lease only. No URL fetch, mic permission, persistent cache or source mutation. */
internal suspend fun decodeVoiceWaveform(file: File): List<Float>? = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
        extractor.setDataSource(file.absolutePath)
        val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return@withContext null
        val format = extractor.getTrackFormat(index)
        val duration = format.getLong(MediaFormat.KEY_DURATION)
        // Long recordings retain ordinary progress rather than decoding unbounded content in a message row.
        if (duration !in 1..(10L * 60 * 1_000_000)) return@withContext null
        extractor.selectTrack(index)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return@withContext null)
        decoder = codec
        codec.configure(format, null, null, 0); codec.start()
        val envelope = PcmEnvelope(durationUs = duration)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (channels !in 1..8 || sampleRate !in 8000..192000) return@withContext null
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000L
        var received = false
        while (!outputDone) {
            coroutineContext.ensureActive()
            if (android.os.SystemClock.elapsedRealtime() > deadline) return@withContext null
            if (!inputDone) {
                val slot = codec.dequeueInputBuffer(10_000)
                if (slot >= 0) {
                    val input = codec.getInputBuffer(slot) ?: return@withContext null
                    val count = extractor.readSampleData(input, 0)
                    if (count < 0) { codec.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(slot, 0, count, extractor.sampleTime, 0); extractor.advance() }
                }
            }
            when (val slot = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val output = codec.outputFormat
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    if (channels !in 1..8 || sampleRate !in 8000..192000) return@withContext null
                }
                else -> if (slot >= 0) {
                    try {
                        val output = codec.getOutputBuffer(slot) ?: return@withContext null
                        output.order(ByteOrder.LITTLE_ENDIAN); output.position(info.offset); output.limit(info.offset + info.size)
                        val bytes = when (encoding) { AudioFormat.ENCODING_PCM_FLOAT -> 4; AudioFormat.ENCODING_PCM_16BIT -> 2; else -> return@withContext null }
                        var sample = 0L
                        while (output.remaining() >= bytes) {
                            val value = if (bytes == 4) output.float else output.short / 32768f
                            envelope.accept(info.presentationTimeUs + (sample / channels) * 1_000_000 / sampleRate, value)
                            sample++
                        }
                        received = received || info.size > 0
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { codec.releaseOutputBuffer(slot, false) }
                }
            }
        }
        if (received) envelope.values() else null
    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
    catch (_: Exception) { null }
    finally { runCatching { decoder?.stop() }; runCatching { decoder?.release() }; extractor.release() }
}
