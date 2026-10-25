package com.example.chat.core.audio

/**
 * Exact Kotlin port of lib/core/audio/audio_types.dart (api branch, anchor
 * 3bee780d0e5a0e1df2e44f6d5b9f6c691d10a682). Pure JVM - no Flutter/Android deps.
 */

/** Target use case for audio session routing. */
enum class AudioUseCase { media, communication, notification }

/** Logical audio output route. */
enum class AudioOutputRoute { systemDefault, receiver, speaker, bluetooth, wiredHeadset, usb }

/** Status of dual-output (earpiece + loudspeaker) capability. */
enum class DualOutputStatus { active, unavailable, unsupported, notVerified, failed }

/** Capability report for an audio use case. */
data class AudioCapabilities(
    val availableRoutes: List<AudioOutputRoute>,
    val activeRoute: AudioOutputRoute,
    val dualOutputStatus: DualOutputStatus,
    val failureReason: String? = null,
    val isStereoSupported: Boolean = false,
    val platform: String = "unknown",
)

/** State machine lifecycle states for Chaty audio coordination. */
enum class AudioSessionState {
    idle,
    mediaActive,
    callStarting,
    receiverCall,
    speakerphoneCall,
    externalCall,
    interrupted,
    recovering,
    failed,
    releasing,
}

/** Diagnostic log event for capability and route transitions. */
data class AudioPolicyDiagnosticEvent(
    val message: String,
    val useCase: AudioUseCase,
    val state: AudioSessionState,
    val route: AudioOutputRoute? = null,
    val error: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/** User audio preference mode in settings. */
enum class AudioPreferenceMode { automatic, preferStereo, experimentalDualOutput }
