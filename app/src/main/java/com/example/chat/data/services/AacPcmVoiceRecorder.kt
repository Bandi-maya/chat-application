package com.example.chat.data.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import androidx.core.content.ContextCompat
import com.example.chat.ui.chats.detail.VoiceRecordConfig
import com.example.chat.ui.chats.detail.VoiceRecorderPort
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min

/** Android [VoiceRecorderPort] implementing the record_android 2.1.2 DEFAULT engine
 *  (pubspec.lock: record 7.1.1 / record_android 2.1.2) that Dart selects for
 *  voice_note_service.dart's RecordConfig (aacLc 96k/44100/mono, autoGain/echoCancel/
 *  noiseSuppress all true, no useLegacy flag -> RecordConfig.useLegacy=false).
 *
 *  Class-for-class port of the locked plugin sources:
 *   - RecordThread  -> [CaptureThread] (capture loop, start latch, stop/cancel semantics)
 *   - PCMReader     -> [PcmReader] (AudioRecord 16-bit PCM, minBufferSize*2, amplitude
 *     = 0.0.coerceAtMost(20*log10(max/32767.0)) with -160.0 floor when max==0)
 *   - AudioEffectsManager -> [CaptureEffects] (AGC/AEC/NS availability-gated, attached to
 *     reader.audioSessionId, enabled from the Dart config flags)
 *   - AacFormat + FormatCodecSelector -> [AacFormatSupport] (AACObjectLC MediaFormat,
 *     codec enumeration with bit-rate/sample-rate/channel clamping to codec caps)
 *   - MediaCodecEncoder -> [AacEncoder] (HandlerThread + async callback, input queue,
 *     CSD capture, EOS, presentation timestamps from byte position)
 *   - MuxerContainer  -> [Mpeg4Writer] (MUXER_OUTPUT_MPEG_4, csd-0 injection, start/stop
 *     guards) -> .m4a, exactly what the Dart side produces.
 *
 *  Deliberately out of scope (plugin features Chaty cannot reach; each matches the
 *  previous adapter's behavior, so no parity change): streaming mode (path is always
 *  set), pause/resume (VoiceRecorderPort has none), audio-focus interruption handling
 *  (AudioSessionManager), bluetooth SCO management, input device selection, muteAudio.
 *  Amplitude exposure: plugin getAmplitude() returns [current, max]; Dart's
 *  VoiceNoteService.currentLevel() reads only `current`, mirrored here.
 *
 *  PERMISSION: same contract as the previous adapter - hasPermission() only CHECKS;
 *  the host prompts for RECORD_AUDIO before controller.start(). */
class AndroidPcmVoiceRecorderPort(
    private val context: Context,
) : VoiceRecorderPort {

    @Volatile private var capture: CaptureThread? = null
    @Volatile private var lastPath: String? = null

    override suspend fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun start(config: VoiceRecordConfig, path: String) {
        stopQuietly()
        val next = CaptureThread(config, path)
        next.startRecording() // throws when reader/encoder init fails (see CaptureThread.startRecording)
        capture = next
        lastPath = path
    }

    /** record plugin stop(): returns the recorded path after the capture thread and
     *  muxer have fully finished. Blocks until then, like the plugin's MethodChannel
     *  stop which answers from the onStop callback. */
    override suspend fun stop(): String? {
        val current = capture ?: return lastPath
        current.stopRecording()
        current.awaitStop()
        capture = null
        return lastPath
    }

    /** record plugin getAmplitude().current: the last PCM read's dBFS, -160.0 before
     *  the first read / when not recording (RecordThread.getAmplitude). */
    override suspend fun currentAmplitudeDbfs(): Double =
        capture?.getAmplitude() ?: DEFAULT_AMPLITUDE_DB

    override suspend fun dispose() {
        stop()
    }

    private fun stopQuietly() {
        val current = capture ?: return
        try {
            current.cancelRecording()
            current.awaitStop()
        } catch (_: Exception) {
        }
        capture = null
    }

    companion object {
        private const val DEFAULT_AMPLITUDE_DB = -160.0
    }
}

/** Port of record/recorder/RecordThread.kt (pause/resume removed; Chaty cannot reach
 *  them). Owns the capture loop thread, the start latch, and stop/cancel semantics. */
private class CaptureThread(
    private val config: VoiceRecordConfig,
    private val path: String,
) {
    private val recording = AtomicBoolean(false)
    @Volatile private var canceled = false
    @Volatile private var captureThread: Thread? = null
    @Volatile private var readerRef: PcmReader? = null
    @Volatile private var failure: Exception? = null
    private val stopLatch = CountDownLatch(1)

    fun isRecording(): Boolean = captureThread != null && recording.get()

    fun stopRecording() {
        if (isRecording()) {
            recording.set(false)
        }
    }

    fun cancelRecording() {
        canceled = true
        if (isRecording()) {
            stopRecording()
        } else if (captureThread == null) {
            deleteOutput()
        }
    }

    /** RecordThread.getAmplitude: PCMReader.getAmplitude() or -160.0 when no reader. */
    fun getAmplitude(): Double = readerRef?.getAmplitude() ?: -160.0

    /** Blocks until the capture loop finished start-up (reader + encoder initialized)
     *  or failed, mirroring RecordThread.startRecording's start latch. The plugin
     *  reports a start failure asynchronously through onFailure; this port rethrows it
     *  from start() so VoiceNoteController surfaces the same user-visible error
     *  without leaving recording state stuck. */
    @Throws(Exception::class)
    fun startRecording() {
        val startLatch = CountDownLatch(1)

        captureThread = Thread {
            var reader: PcmReader? = null
            var encoder: AacEncoder? = null
            try {
                val (mediaFormat, codecName) = AacFormatSupport.selectCodec(config)
                val pcmReader = PcmReader(config, mediaFormat)
                pcmReader.start()
                reader = pcmReader

                val aacEncoder = AacEncoder(mediaFormat, codecName, path) { ex -> fail(ex) }
                aacEncoder.startEncoding()
                encoder = aacEncoder

                readerRef = pcmReader
                recording.set(true)
                startLatch.countDown()

                while (isRecording()) {
                    val buffer = pcmReader.read()
                    if (buffer.isNotEmpty()) {
                        aacEncoder.encode(buffer)
                    }
                }
            } catch (ex: Exception) {
                fail(ex)
            } finally {
                startLatch.countDown()
                readerRef = null
                stopAndRelease(reader, encoder)
            }
        }.apply {
            name = "RecordThread-$path"
            isDaemon = true
            start()
        }

        startLatch.await()
        failure?.let { throw it }
    }

    /** Waits for the capture thread's finally block (reader released, encoder drained,
     *  muxer finalized, output deleted when canceled). */
    fun awaitStop() {
        stopLatch.await()
    }

    private fun fail(ex: Exception) {
        if (failure == null) failure = ex
        stopRecording()
    }

    private fun stopAndRelease(reader: PcmReader?, encoder: AacEncoder?) {
        try {
            try {
                reader?.stop()
                reader?.release()
            } catch (ex: Exception) {
                fail(ex)
            }
            try {
                encoder?.stopEncoding()
            } catch (ex: Exception) {
                fail(ex)
            }
            if (canceled) {
                deleteOutput()
            }
        } catch (ex: Exception) {
            fail(ex)
        } finally {
            captureThread = null
            stopLatch.countDown()
        }
    }

    private fun deleteOutput() {
        try {
            java.io.File(path).delete()
        } catch (_: Exception) {
        }
    }
}

/** Port of record/recorder/PCMReader.kt: AudioRecord 16-bit PCM capture with the
 *  plugin's exact buffer sizing, byte conversion, error reasons and amplitude math.
 *  Also attaches [CaptureEffects] (record/audio_manager/AudioEffectsManager.kt) to
 *  the reader's audio session - the AGC/AEC/NS path the legacy MediaRecorder engine
 *  cannot provide. */
private class PcmReader(
    private val config: VoiceRecordConfig,
    mediaFormat: MediaFormat,
) {
    private val bufferSize: Int
    private val readBuffer: ShortArray
    private val reader: AudioRecord
    private val effects: CaptureEffects

    @Volatile private var amplitudeDb: Double = DEFAULT_AMPLITUDE_DB

    init {
        val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = channelsConfig(mediaFormat)
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        bufferSize = calculateBufferSize(sampleRate, channels, audioFormat)
        readBuffer = ShortArray(bufferSize / 2)
        reader = createReader(sampleRate, channels, audioFormat, bufferSize)
        effects = CaptureEffects(reader.audioSessionId).also { it.apply(config) }
    }

    fun start() {
        reader.startRecording()
    }

    fun stop() {
        if (reader.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            reader.stop()
        }
    }

    @Throws(Exception::class)
    fun read(): ByteArray {
        val readResult = reader.read(readBuffer, 0, readBuffer.size)
        if (readResult < 0) {
            throw Exception(readFailureReason(readResult))
        }
        if (readResult > 0) {
            amplitudeDb = calculateAmplitudeDb(readResult)
        }
        return convertToByteArray(readResult)
    }

    fun getAmplitude(): Double = amplitudeDb

    fun release() {
        stop()
        effects.release()
        reader.release()
    }

    @android.annotation.SuppressLint("MissingPermission")
    @Throws(Exception::class)
    private fun createReader(
        sampleRate: Int,
        channels: Int,
        audioFormat: Int,
        bufferSize: Int,
    ): AudioRecord {
        // Chaty passes no androidConfig: plugin default MediaRecorder.AudioSource.DEFAULT.
        val reader = try {
            AudioRecord(
                android.media.MediaRecorder.AudioSource.DEFAULT,
                sampleRate,
                channels,
                audioFormat,
                bufferSize,
            )
        } catch (e: IllegalArgumentException) {
            throw Exception("Unable to instantiate PCM reader.", e)
        }
        if (reader.state != AudioRecord.STATE_INITIALIZED) {
            reader.release()
            throw Exception("PCM reader failed to initialize.")
        }
        return reader
    }

    private fun channelsConfig(mediaFormat: MediaFormat): Int =
        if (mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }

    @Throws(Exception::class)
    private fun calculateBufferSize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        return when (minBufferSize) {
            AudioRecord.ERROR_BAD_VALUE, AudioRecord.ERROR ->
                throw Exception(
                    "Recording config is not supported by the hardware, or an invalid config was provided."
                )
            else -> minBufferSize * 2 // Double the minimum buffer size for safety margin
        }
    }

    private fun convertToByteArray(size: Int): ByteArray {
        val byteBuffer = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until size) {
            byteBuffer.putShort(readBuffer[i])
        }
        return byteBuffer.array()
    }

    private fun calculateAmplitudeDb(size: Int): Double {
        val max = readBuffer.take(size).maxOf { abs(it.toInt()) }
        if (max == 0) return DEFAULT_AMPLITUDE_DB
        return 0.0.coerceAtMost(20 * log10(max / MAX_PCM_VALUE))
    }

    private fun readFailureReason(errorCode: Int): String {
        val message = when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION: Failure due to improper method use"
            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE: Invalid value used"
            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT: Object no longer valid, needs recreation"
            AudioRecord.ERROR -> "ERROR: Generic operation failure"
            else -> "Unknown error code: $errorCode"
        }
        return "Error when reading audio data: $message"
    }

    companion object {
        private const val DEFAULT_AMPLITUDE_DB = -160.0
        private const val MAX_PCM_VALUE = 32767.0 // 2^15 - 1 for 16-bit signed
    }
}

/** Port of record/audio_manager/AudioEffectsManager.kt: availability-gated AGC/AEC/NS
 *  attached to the AudioRecord session id, enabled from the Dart config flags. */
private class CaptureEffects(
    private val audioSessionId: Int,
) {
    private var agc: AutomaticGainControl? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    fun apply(config: VoiceRecordConfig) {
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(audioSessionId)
            agc?.enabled = config.autoGain
        } else if (config.autoGain) {
            Log.d(TAG, "Auto gain effect is not available.")
        }
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(audioSessionId)
            aec?.enabled = config.echoCancel
        } else if (config.echoCancel) {
            Log.d(TAG, "Echo canceler effect is not available.")
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(audioSessionId)
            ns?.enabled = config.noiseSuppress
        } else if (config.noiseSuppress) {
            Log.d(TAG, "Noise suppressor effect is not available.")
        }
    }

    fun release() {
        agc?.release()
        aec?.release()
        ns?.release()
    }

    companion object {
        private val TAG = CaptureEffects::class.java.simpleName
    }
}

/** Port of record/format/AacFormat.kt + FormatCodecSelector.kt reduced to Chaty's fixed
 *  encoder (aacLc): AACObjectLC MediaFormat and codec enumeration with the plugin's
 *  exact capability clamping (bit-rate range, nearest sample rate, channel bounds). */
private object AacFormatSupport {

    fun getMediaFormat(config: VoiceRecordConfig): MediaFormat =
        MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, config.sampleRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.numChannels)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

    /** Finds an AAC encoder, adjusting the format to device/codec capabilities exactly
     *  as FormatCodecSelector.findCodec + adjustToCapabilities do. Returns the final
     *  negotiated MediaFormat (shared by the PCM reader and the encoder, like the
     *  plugin's Format.createEncoder) and the codec name. */
    @Throws(Exception::class)
    fun selectCodec(config: VoiceRecordConfig): Pair<MediaFormat, String> {
        val mediaFormat = getMediaFormat(config)
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecs.codecInfos) {
            if (!info.isEncoder) continue
            try {
                val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC)
                if (caps != null && adjustToCapabilities(caps, mediaFormat)) {
                    return Pair(mediaFormat, info.name)
                }
            } catch (_: IllegalArgumentException) {
                // type not supported by this codec
            }
        }
        throw Exception("No codec found for given config $mediaFormat. You should try with other values.")
    }

    private fun adjustToCapabilities(
        caps: MediaCodecInfo.CodecCapabilities,
        mediaFormat: MediaFormat,
    ): Boolean {
        var supported = caps.isFormatSupported(mediaFormat)
        if (!supported) {
            val audioCapabilities = caps.audioCapabilities
            if (audioCapabilities != null) {
                mediaFormat.setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    checkBounds(audioCapabilities.bitrateRange, mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE)),
                )
                if (audioCapabilities.supportedSampleRates != null) {
                    mediaFormat.setInteger(
                        MediaFormat.KEY_SAMPLE_RATE,
                        nearestValue(
                            audioCapabilities.supportedSampleRates,
                            mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        ),
                    )
                }
                mediaFormat.setInteger(
                    MediaFormat.KEY_CHANNEL_COUNT,
                    checkBounds(
                        Range(1, audioCapabilities.maxInputChannelCount),
                        mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    ),
                )
            }
            supported = caps.isFormatSupported(mediaFormat)
        }
        return supported
    }

    private fun checkBounds(range: Range<Int>, value: Int): Int {
        if (range.lower > value) return range.lower
        if (range.upper < value) return range.upper
        return value
    }

    private fun nearestValue(values: IntArray, value: Int): Int {
        var distance = abs(values[0] - value)
        var idx = 0
        for (c in 1 until values.size) {
            val cDistance = abs(values[c] - value)
            if (cDistance < distance) {
                idx = c
                distance = cDistance
            }
        }
        return values[idx]
    }
}

/** Port of record/encoder/MediaCodecEncoder.kt (streaming callbacks removed; Chaty
 *  always records to a file): HandlerThread + async MediaCodec callback, input queue,
 *  CSD capture feeding the container, EOS on stop, timestamps from input byte position. */
private class AacEncoder(
    private val mediaFormat: MediaFormat,
    private val codecName: String,
    path: String,
    private val onFailure: (Exception) -> Unit,
) : HandlerThread("MediaCodecEncoder Thread") {

    private var mHandler: Handler? = null
    private var mCodec: MediaCodec? = null
    private var mContainer: Mpeg4Writer? = Mpeg4Writer(path)
    private val mQueue = LinkedList<Sample>()
    private var mRate = 0f // bytes per us
    private var mInputBufferPosition: Long = 0
    private var mInputBufferIndex = -1
    private var mContainerTrack = 0
    private var mContainerSetup = false
    private var mPendingFormat: MediaFormat? = null

    @Volatile private var mStoppedCompleter: Semaphore? = null
    @Volatile private var mStopped = false

    fun encode(bytes: ByteArray) {
        if (mStopped) {
            return
        }
        val s = Sample(bytes)
        mHandler?.post {
            mQueue.add(s)
            if (mInputBufferIndex >= 0) {
                processInputBuffer()
            }
        }
    }

    fun startEncoding() {
        start() // Start the thread
        mHandler = Handler(looper)
        mHandler?.post { initEncoding() }
    }

    fun stopEncoding() {
        if (mStopped) {
            return
        }
        mStopped = true

        val completer = Semaphore(0)
        mStoppedCompleter = completer

        mHandler?.post {
            if (mInputBufferIndex >= 0) {
                processInputBuffer()
            }
        }

        try {
            // Wait for the encoder to finish
            completer.acquire()
        } finally {
            quitSafely()
        }
    }

    private fun initEncoding() {
        calculateInputRate()
        var codec: MediaCodec? = null
        try {
            codec = MediaCodec.createByCodecName(codecName)
            codec.setCallback(AudioRecorderCodecCallback(), Handler(looper))
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            mCodec = codec
        } catch (e: Exception) {
            codec?.release()
            onError(e)
        }
    }

    private fun processInputBuffer() {
        val codec = mCodec ?: return
        try {
            val s = mQueue.peekFirst()
            if (s == null) {
                // There's no more data to encode.
                if (mStoppedCompleter != null) {
                    // We're done, so send EOS
                    codec.queueInputBuffer(
                        mInputBufferIndex, 0, 0,
                        getPresentationTimestampUs(mInputBufferPosition),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    mInputBufferIndex = -1 // Reset index after sending EOS
                }
                return
            }

            val b = codec.getInputBuffer(mInputBufferIndex)!!
            val sz = min(b.capacity(), s.remaining())
            val ts = getPresentationTimestampUs(mInputBufferPosition)
            b.put(s.bytes, s.position, sz)

            codec.queueInputBuffer(mInputBufferIndex, 0, sz, ts, 0)

            mInputBufferPosition += sz.toLong()
            s.position += sz

            if (s.isConsumed()) {
                mQueue.pop()
            }

            // Reset the input buffer index
            mInputBufferIndex = -1
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun processOutputBuffer(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
        try {
            val container = mContainer
            if (container != null && info.size != 0) {
                val isCsd = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (isCsd) {
                    onCsdBuffer(codec, index, info, container)
                } else {
                    val out = codec.getOutputBuffer(index)
                    if (out != null && mContainerSetup) {
                        container.writeSampleData(mContainerTrack, out, info)
                    }
                }
            }
            codec.releaseOutputBuffer(index, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                stopAndRelease()
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun onCsdBuffer(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
        container: Mpeg4Writer,
    ) {
        val csd = codec.getOutputBuffer(index)?.let { out ->
            out.position(info.offset)
            out.limit(info.offset + info.size)
            ByteArray(info.size).also { out.get(it) }
        }
        container.onCsdBuffer(csd)
        trySetupContainer()
    }

    private fun trySetupContainer() {
        if (mContainerSetup) return
        val container = mContainer ?: return
        val format = mPendingFormat ?: return
        if (!container.isReadyForSetup(format)) return

        mContainerSetup = true
        mContainerTrack = container.addTrack(format)
        container.start()
        Log.d("MediaCodecEncoder", "Container setup done: $format")
    }

    private fun onError(e: Exception) {
        mStopped = true
        stopAndRelease()
        onFailure(e)
    }

    private fun stopAndRelease() {
        try {
            mCodec?.stop()
        } finally {
            mCodec?.release()
            mCodec = null
        }
        mContainer?.release()
        mContainer = null
        mStoppedCompleter?.release()
        mStoppedCompleter = null
    }

    private fun calculateInputRate() {
        val bitsPerSample = 16 // Default to 16-bit PCM
        val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        // bytes per microsecond
        mRate = (bitsPerSample / 8.0f) * sampleRate * channelCount * 1e-6f
    }

    private fun getPresentationTimestampUs(position: Long): Long =
        (position / mRate).toLong()

    internal inner class AudioRecorderCodecCallback : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            mInputBufferIndex = index
            processInputBuffer()
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            processOutputBuffer(codec, index, info)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            onError(e)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (mContainerSetup) return
            mPendingFormat = format
            trySetupContainer()
        }
    }

    private class Sample(val bytes: ByteArray) {
        var position: Int = 0
        fun remaining(): Int = bytes.size - position
        fun isConsumed(): Boolean = position >= bytes.size
    }
}

/** Port of record/container/MuxerContainer.kt fixed to AacFormat.createWriter's file
 *  path: MUXER_OUTPUT_MPEG_4, requiresCodecSpecificDataInFormat = true. */
private class Mpeg4Writer(
    private val path: String,
) {
    private var mMuxer: MediaMuxer? = null
    private var mStarted = false
    private var mStopped = false
    private var mCsdReceived = false
    private var mPendingCsd: ByteArray? = null

    fun start() {
        if (mStarted || mStopped) return
        mStarted = true
        mMuxer?.start()
    }

    fun stop() {
        if (!mStarted || mStopped) return
        mStarted = false
        mStopped = true
        mMuxer?.stop()
    }

    fun addTrack(mediaFormat: MediaFormat): Int {
        if (mStarted || mStopped) return -1
        if (mMuxer == null) {
            mMuxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
        if (!mediaFormat.containsKey("csd-0")) {
            mPendingCsd?.let { mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
        }
        return mMuxer!!.addTrack(mediaFormat)
    }

    fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!mStarted || mStopped) return
        mMuxer?.writeSampleData(trackIndex, byteBuffer, bufferInfo)
    }

    fun release() {
        stop()
        mMuxer?.release()
        mMuxer = null
    }

    fun onCsdBuffer(csd: ByteArray?) {
        mCsdReceived = true
        mPendingCsd = csd
    }

    fun isReadyForSetup(format: MediaFormat): Boolean =
        format.containsKey("csd-0") || mCsdReceived
}
