package com.example.chat.core.audio

/**
 * Coordinates media audio playback, ensuring stereo preservation and audio
 * focus rules. Exact port of lib/core/audio/media_audio_controller.dart.
 *
 * The Dart constructor took the concrete AudioPolicyCoordinator; the Kotlin
 * port accepts the [AudioPolicyGate] seam so feature code can be tested
 * against a fake. The production instance is AudioPolicyCoordinator.
 */
class MediaAudioController(private val coordinator: AudioPolicyGate) {

    suspend fun requestMediaFocus(): Boolean = coordinator.startMediaSession()

    suspend fun abandonMediaFocus() = coordinator.stopMediaSession()
}
