package com.example.chat.core.audio

/**
 * Narrow seam over the audio policy coordinator for feature workers
 * (media player, calls, notifications) that must participate in shared audio
 * policy without owning the state machine. [AudioPolicyCoordinator] is the
 * single production implementation. Define-once contract: feature code must
 * import this interface from com.example.chat.core.audio rather than declaring
 * local copies.
 *
 * Surface mirrors every public operation of the Dart AudioPolicyCoordinator
 * (lib/core/audio/audio_policy_coordinator.dart).
 */
interface AudioPolicyGate {
    val state: AudioSessionState
    val activeOwner: AudioUseCase?
    val activeRoute: AudioOutputRoute
    val diagnostics: List<AudioPolicyDiagnosticEvent>

    /** Acquire audio session for VoIP call. */
    suspend fun startCallSession(initialRoute: AudioOutputRoute = AudioOutputRoute.receiver): Boolean

    /** Change call audio route (receiver, speaker, bluetooth, wired headset). */
    suspend fun setCallRoute(route: AudioOutputRoute): Boolean

    /** Handle media playback session acquisition. */
    suspend fun startMediaSession(): Boolean

    /** Play notification sound effect without stealing communication session. */
    suspend fun playNotificationSound(playerAction: suspend () -> Unit)

    /** Release call session deterministically. */
    suspend fun endCallSession()

    /** Release media session. */
    suspend fun stopMediaSession()

    /** Handle interruptions (phone call, alarm, audio focus loss). */
    fun onInterruption(began: Boolean)

    /** ChangeNotifier equivalent: listeners fire synchronously after each
     *  state mutation, matching Dart notifyListeners() semantics. */
    fun addListener(listener: () -> Unit)
    fun removeListener(listener: () -> Unit)
}
