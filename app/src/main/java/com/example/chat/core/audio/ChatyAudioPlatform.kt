package com.example.chat.core.audio

import kotlinx.coroutines.flow.Flow

/**
 * Platform channel abstraction for native audio session and route control.
 * Exact port of the ChatyAudioPlatform abstract interface in
 * lib/core/audio/chaty_audio_platform.dart. In Flutter this was backed by a
 * MethodChannel; in the Kotlin app the production implementation talks to
 * AudioManager directly (see AndroidChatyAudioPlatform).
 */
interface ChatyAudioPlatform {
    suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities
    suspend fun getCurrentRoute(useCase: AudioUseCase): AudioOutputRoute
    suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities
    suspend fun getDualOutputStatus(useCase: AudioUseCase): DualOutputStatus
    suspend fun releaseSession(useCase: AudioUseCase)
    val capabilityChanges: Flow<AudioCapabilities>
    val routeChanges: Flow<AudioOutputRoute>
}

/** Default capabilities returned by the Dart MethodChannel implementation on
 *  any channel error (chaty_audio_platform.dart _defaultCapabilities). */
fun defaultAudioCapabilities(@Suppress("UNUSED_PARAMETER") useCase: AudioUseCase): AudioCapabilities =
    AudioCapabilities(
        availableRoutes = listOf(
            AudioOutputRoute.systemDefault,
            AudioOutputRoute.speaker,
            AudioOutputRoute.receiver,
        ),
        activeRoute = AudioOutputRoute.systemDefault,
        dualOutputStatus = DualOutputStatus.notVerified,
        isStereoSupported = false,
        platform = "flutter",
    )
