package com.example.chat.data.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.chat.ui.chats.detail.VoiceRecorderPort
import com.example.chat.ui.chats.detail.VoiceRecordConfig
import com.example.chat.ui.chats.detail.VoiceStagingPort
import java.io.File
import kotlin.math.log10

/** Android [VoiceRecorderPort] over MediaRecorder, matching the Dart `record`
 *  plugin's behavior for voice_note_service.dart's RecordConfig:
 *  - AAC-LC in an MPEG-4 (.m4a) container, AudioSource.MIC.
 *  - autoGain/echoCancel/noiseSuppress map onto the audiofx effects attached
 *    to the recorder's audio session when the device provides them (the
 *    plugin does the same availability-gated attachment).
 *  - Amplitude: the plugin returns native dBFS; MediaRecorder exposes only
 *    maxAmplitude (0..32767 peak since last call), converted here as
 *    20*log10(peak/32767) clamped to -60..0. Same floor the Dart UI expects;
 *    peak-vs-instantaneous is the documented adapter approximation.
 *  PERMISSION: the record plugin's hasPermission() prompts; this port only
 *  CHECKS. The host must request RECORD_AUDIO (manifest entry + runtime
 *  prompt) before controller.start(); on denial the controller surfaces the
 *  Dart error string "Microphone permission is required.". */
class AndroidVoiceRecorderPort(
    private val context: Context,
) : VoiceRecorderPort {
    private var recorder: MediaRecorder? = null
    private var lastPath: String? = null
    private var gainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    override suspend fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun start(config: VoiceRecordConfig, path: String) {
        releaseQuietly()
        val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        next.setAudioSource(MediaRecorder.AudioSource.MIC)
        next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        next.setAudioSamplingRate(config.sampleRate)
        next.setAudioEncodingBitRate(config.bitRate)
        next.setAudioChannels(config.numChannels)
        next.setOutputFile(path)
        next.prepare()
        val sessionId = next.audioSessionId
        if (sessionId != 0) {
            if (config.autoGain && AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
            if (config.noiseSuppress && NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            if (config.echoCancel && AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
        }
        next.start()
        recorder = next
        lastPath = path
    }

    /** record stop(): the plugin returns the path even for very short
     *  captures; MediaRecorder.stop() throws RuntimeException then, so the
     *  failure is swallowed and the staged path returned either way (the
     *  controller's 1s floor owns the too-short policy). */
    override suspend fun stop(): String? {
        val current = recorder ?: return lastPath
        try {
            current.stop()
        } catch (_: RuntimeException) {
            // Too-short capture: keep the staged path like the plugin does.
        }
        current.reset()
        releaseAudioFx()
        current.release()
        recorder = null
        return lastPath
    }

    override suspend fun currentAmplitudeDbfs(): Double {
        val current = recorder ?: return -60.0
        val peak = current.maxAmplitude
        if (peak <= 0) return -60.0
        return (20.0 * log10(peak / 32767.0)).coerceIn(-60.0, 0.0)
    }

    override suspend fun dispose() {
        stop()
    }

    private fun releaseAudioFx() {
        gainControl?.release()
        noiseSuppressor?.release()
        echoCanceler?.release()
        gainControl = null
        noiseSuppressor = null
        echoCanceler = null
    }

    private fun releaseQuietly() {
        val current = recorder ?: return
        try {
            current.stop()
        } catch (_: RuntimeException) {
        }
        current.reset()
        releaseAudioFx()
        current.release()
        recorder = null
    }
}

/** Android [VoiceStagingPort]: Dart getTemporaryDirectory() == cacheDir;
 *  _deleteQuietly swallows every failure. */
class AndroidVoiceStagingPort(
    private val context: Context,
) : VoiceStagingPort {
    override fun newStagingPath(epochMs: Long): String =
        File(context.cacheDir, "chaty_voice_$epochMs.m4a").absolutePath

    override suspend fun deleteQuietly(path: String) {
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (_: Throwable) {
        }
    }
}
