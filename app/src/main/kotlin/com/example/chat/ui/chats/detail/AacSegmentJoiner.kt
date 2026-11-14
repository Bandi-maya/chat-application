package com.example.chat.ui.chats.detail

/** One compressed audio sample read from a segment. Time is in microseconds, relative to its segment. */
class AudioSample(val data: ByteArray, val timeUs: Long, val flags: Int)

/** Track format facts needed to decide whether two segments can be joined without re-encoding. */
class AudioTrackFormat(
    val mime: String, val sampleRate: Int, val channels: Int,
    /** AAC profile (KEY_AAC_PROFILE) or -1 when unknown. */
    val profile: Int = -1,
    /** AudioSpecificConfig bytes (csd-0) or null when unknown. */
    val csd0: ByteArray? = null,
    val native: Any? = null,
) {
    /** True only when the compressed streams are interchangeable inside one track. */
    fun compatibleWith(o: AudioTrackFormat): Boolean =
        mime == o.mime && sampleRate == o.sampleRate && channels == o.channels && profile == o.profile &&
            ((csd0 == null && o.csd0 == null) || (csd0 != null && o.csd0 != null && csd0.contentEquals(o.csd0)))
}

interface AudioSegmentSource {
    val format: AudioTrackFormat
    /** Samples in decode order. */
    fun samples(): Sequence<AudioSample>
    fun close() {}
}

interface AudioSegmentSink {
    fun start(format: AudioTrackFormat)
    fun write(sample: AudioSample)
    fun finish()
    /** Called when joining fails: discard partial output. */
    fun abort() {}
}

class AacJoinException(message: String) : Exception(message)

/** Join AAC segments into one stream without re-encoding. Pure logic, no Android types, so it is JVM-testable.
 *  Each later segment is shifted by the end time of the previous one, where the end time is the last sample time
 *  plus one frame (1024 samples at the segment sample rate). Fails, never guesses, when formats differ. */
object AacSegmentJoiner {
    private const val FRAME_SAMPLES = 1024L

    fun join(sources: List<AudioSegmentSource>, sink: AudioSegmentSink): Long {
        if (sources.isEmpty()) throw AacJoinException("No recording segments to join.")
        val first = sources.first().format
        sources.forEach { s ->
            val f = s.format
            if (!f.compatibleWith(first)) {
                throw AacJoinException("Recording parts use different audio formats.")
            }
        }
        var offsetUs = 0L
        var written = 0
        try {
            sink.start(first)
            for (source in sources) {
                var lastUs = -1L
                var count = 0
                for (sample in source.samples()) {
                    if (sample.timeUs < 0) throw AacJoinException("Recording part has invalid timing.")
                    if (sample.timeUs < lastUs) throw AacJoinException("Recording part has out-of-order samples.")
                    lastUs = sample.timeUs
                    sink.write(AudioSample(sample.data, sample.timeUs + offsetUs, sample.flags))
                    count++; written++
                }
                if (count == 0) throw AacJoinException("A recording part has no audio.")
                offsetUs += lastUs + frameUs(first.sampleRate)
            }
            sink.finish()
        } catch (e: Throwable) {
            runCatching { sink.abort() }
            throw e
        }
        if (written == 0) throw AacJoinException("Joined recording is empty.")
        return offsetUs
    }

    fun frameUs(sampleRate: Int): Long = FRAME_SAMPLES * 1_000_000L / sampleRate.coerceAtLeast(1)
}

/** Android adapters over MediaExtractor and MediaMuxer. Copies compressed samples, no decode or encode. */
object AndroidAacJoin {
    fun join(segmentPaths: List<String>, outputPath: String) {
        val extractors = mutableListOf<android.media.MediaExtractor>()
        var muxer: android.media.MediaMuxer? = null
        try {
            val sources = segmentPaths.map { path ->
                val ex = android.media.MediaExtractor().also { extractors += it; it.setDataSource(path) }
                val idx = (0 until ex.trackCount).firstOrNull {
                    ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw AacJoinException("A recording part has no audio track.")
                ex.selectTrack(idx)
                val mf = ex.getTrackFormat(idx)
                object : AudioSegmentSource {
                    override val format = AudioTrackFormat(
                        mf.getString(android.media.MediaFormat.KEY_MIME) ?: "",
                        mf.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE),
                        mf.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT),
                        profile = if (mf.containsKey(android.media.MediaFormat.KEY_AAC_PROFILE)) mf.getInteger(android.media.MediaFormat.KEY_AAC_PROFILE) else -1,
                        csd0 = mf.getByteBuffer("csd-0")?.let { b -> val c = b.duplicate(); c.rewind(); ByteArray(c.remaining()).also { c.get(it) } },
                        native = mf,
                    )
                    override fun samples(): Sequence<AudioSample> = sequence {
                        val buf = java.nio.ByteBuffer.allocate(1 shl 16)
                        while (true) {
                            buf.clear()
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) break
                            val bytes = ByteArray(n); buf.position(0); buf.get(bytes, 0, n)
                            yield(AudioSample(bytes, ex.sampleTime, ex.sampleFlags))
                            ex.advance()
                        }
                    }
                }
            }
            var track = -1
            val sink = object : AudioSegmentSink {
                override fun start(format: AudioTrackFormat) {
                    val m = android.media.MediaMuxer(outputPath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    muxer = m
                    track = m.addTrack(format.native as android.media.MediaFormat)
                    m.start()
                }
                override fun write(sample: AudioSample) {
                    val info = android.media.MediaCodec.BufferInfo()
                    info.set(0, sample.data.size, sample.timeUs, sample.flags)
                    muxer!!.writeSampleData(track, java.nio.ByteBuffer.wrap(sample.data), info)
                }
                override fun finish() { muxer?.stop(); muxer?.release(); muxer = null }
                override fun abort() {
                    runCatching { muxer?.release() }; muxer = null
                    runCatching { java.io.File(outputPath).delete() }
                }
            }
            AacSegmentJoiner.join(sources, sink)
        } finally {
            extractors.forEach { runCatching { it.release() } }
            runCatching { muxer?.release() }
        }
    }
}
