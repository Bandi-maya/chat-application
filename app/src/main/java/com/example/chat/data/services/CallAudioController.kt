package com.example.chat.data.services

import android.content.Context
import android.media.AudioManager

/**
 * Integrates the call controller with the native audio subsystem. Port of
 * lib/core/audio/call_audio_controller.dart's observable behavior: a call
 * audio session starts in MODE_IN_COMMUNICATION with the default route
 * (speaker for video, earpiece for voice), route switches map to
 * speakerphone on/off, and teardown restores MODE_NORMAL.
 *
 * The Dart file delegates to an app-wide audio policy subsystem
 * (AudioPolicyCoordinator / AudioCapabilityService, lib/core/audio/) that is
 * not yet ported; dual-output capability and bluetooth/wired enumeration land
 * with that subsystem. Bluetooth/headset selection stays on the system audio
 * route, exactly as CallSignalingService.setAudioRoute documents.
 */
class CallAudioController(
    appContext: Context,
    private val signalingService: CallSignalingService? = null,
) {
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { for (l in listeners) l() }

    val activeRoute: AudioRouteType
        get() = if (audioManager.isSpeakerphoneOn) AudioRouteType.speaker else AudioRouteType.earpiece

    /** Start audio session coordinated with the call engine. */
    suspend fun initializeCallAudio(isVideo: Boolean): Boolean {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isVideo
        try {
            signalingService?.setAudioRoute(
                if (isVideo) AudioRouteType.speaker else AudioRouteType.earpiece,
            )
        } catch (_: Exception) {
        }
        notifyListeners()
        return true
    }

    /** Switch audio route during an active call. */
    suspend fun switchRoute(targetRoute: AudioRouteType): Boolean {
        when (targetRoute) {
            AudioRouteType.speaker -> audioManager.isSpeakerphoneOn = true
            AudioRouteType.earpiece -> audioManager.isSpeakerphoneOn = false
            else -> return false
        }
        try {
            when (targetRoute) {
                AudioRouteType.speaker -> signalingService?.setAudioRoute(AudioRouteType.speaker)
                AudioRouteType.earpiece -> signalingService?.setAudioRoute(AudioRouteType.earpiece)
                else -> Unit
            }
        } catch (_: Exception) {
        }
        notifyListeners()
        return true
    }

    /** Teardown call audio session deterministically. */
    suspend fun terminateCallAudio() {
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        notifyListeners()
    }
}
