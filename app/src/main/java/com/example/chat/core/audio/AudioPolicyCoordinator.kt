package com.example.chat.core.audio

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Concurrency-safe state machine managing session ownership and route transitions.
 * Exact port of lib/core/audio/audio_policy_coordinator.dart.
 *
 * Precedence Rules (verbatim from Dart doc):
 * 1. Active Call owns communication session.
 * 2. Explicit User Route selection takes precedence over automatic preference.
 * 3. Connected External Devices handled according to platform routing.
 * 4. Media playback follows playback policy when no higher-priority session owns audio.
 * 5. Notifications NEVER seize ownership from an active call.
 * 6. Every session has deterministic cleanup (releaseSession).
 *
 * Dart serialized transitions with a Completer-based spin lock; the Kotlin
 * port uses a fair Mutex, which preserves the same "one transition at a time"
 * guarantee for suspend callers.
 */
class AudioPolicyCoordinator(
    private val capabilityService: AudioCapabilityService,
) : AudioPolicyGate {

    private var _state: AudioSessionState = AudioSessionState.idle
    private var _activeOwner: AudioUseCase? = null
    private var _activeRoute: AudioOutputRoute = AudioOutputRoute.systemDefault
    private val _diagnostics = mutableListOf<AudioPolicyDiagnosticEvent>()
    private val transitionMutex = Mutex()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override val state: AudioSessionState get() = _state
    override val activeOwner: AudioUseCase? get() = _activeOwner
    override val activeRoute: AudioOutputRoute get() = _activeRoute
    override val diagnostics: List<AudioPolicyDiagnosticEvent> get() = _diagnostics.toList()

    override fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.forEach { it() } }

    private fun recordEvent(message: String, useCase: AudioUseCase? = null, error: String? = null) {
        _diagnostics.add(
            AudioPolicyDiagnosticEvent(
                message = message,
                useCase = useCase ?: _activeOwner ?: AudioUseCase.media,
                route = _activeRoute,
                state = _state,
                error = error,
            )
        )
        if (_diagnostics.size > 100) {
            _diagnostics.removeAt(0)
        }
    }

    /** Acquire audio session for VoIP call. */
    override suspend fun startCallSession(initialRoute: AudioOutputRoute): Boolean =
        transitionMutex.withLock {
            recordEvent("Starting call audio session", useCase = AudioUseCase.communication)
            _state = AudioSessionState.callStarting
            _activeOwner = AudioUseCase.communication
            notifyListeners()

            try {
                val caps = capabilityService.setPreferredRoute(
                    AudioUseCase.communication,
                    initialRoute,
                )
                _activeRoute = caps.activeRoute
                _state = stateForRoute(_activeRoute)
                recordEvent("Call audio session established", useCase = AudioUseCase.communication)
                notifyListeners()
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state = AudioSessionState.failed
                recordEvent("Call session setup failed", useCase = AudioUseCase.communication, error = e.toString())
                notifyListeners()
                false
            }
        }

    /** Change call audio route (receiver, speaker, bluetooth, wired headset). */
    override suspend fun setCallRoute(route: AudioOutputRoute): Boolean =
        transitionMutex.withLock {
            if (_activeOwner != AudioUseCase.communication) {
                recordEvent("Rejected route change: call is not active owner")
                return@withLock false
            }
            recordEvent("Switching call route to $route", useCase = AudioUseCase.communication)
            try {
                val caps = capabilityService.setPreferredRoute(
                    AudioUseCase.communication,
                    route,
                )
                _activeRoute = caps.activeRoute
                _state = stateForRoute(_activeRoute)
                recordEvent("Call route successfully updated to $_activeRoute", useCase = AudioUseCase.communication)
                notifyListeners()
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                recordEvent("Call route update failed, falling back", useCase = AudioUseCase.communication, error = e.toString())
                // Safe fallback to speakerphone or receiver
                try {
                    val fallback = capabilityService.setPreferredRoute(
                        AudioUseCase.communication,
                        AudioOutputRoute.speaker,
                    )
                    _activeRoute = fallback.activeRoute
                    _state = stateForRoute(_activeRoute)
                    notifyListeners()
                } catch (inner: Exception) {
                    if (inner is kotlinx.coroutines.CancellationException) throw inner
                }
                false
            }
        }

    /** Handle media playback session acquisition. */
    override suspend fun startMediaSession(): Boolean =
        transitionMutex.withLock {
            // If a call is active, call has strict precedence; do not override.
            if (_activeOwner == AudioUseCase.communication) {
                recordEvent("Media playback cannot override active call session")
                return@withLock false
            }
            _activeOwner = AudioUseCase.media
            _state = AudioSessionState.mediaActive
            recordEvent("Media audio session active", useCase = AudioUseCase.media)
            notifyListeners()
            true
        }

    /** Play notification sound effect without stealing communication session.
     *  Dart intentionally does NOT serialize this under the transition lock. */
    override suspend fun playNotificationSound(playerAction: suspend () -> Unit) {
        // If call is active, follow in-call policy: never reconfigure session.
        if (_activeOwner == AudioUseCase.communication) {
            recordEvent("Playing in-call notification without reconfiguring communication route", useCase = AudioUseCase.notification)
            try {
                playerAction()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            return
        }

        recordEvent("Playing notification sound", useCase = AudioUseCase.notification)
        try {
            playerAction()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /** Release call session deterministically. */
    override suspend fun endCallSession() {
        transitionMutex.withLock {
            if (_activeOwner != AudioUseCase.communication) return@withLock
            recordEvent("Releasing call audio session", useCase = AudioUseCase.communication)
            _state = AudioSessionState.releasing
            notifyListeners()

            try {
                capabilityService.releaseSession(AudioUseCase.communication)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                recordEvent("Error releasing session: $e")
            } finally {
                _activeOwner = null
                _activeRoute = AudioOutputRoute.systemDefault
                _state = AudioSessionState.idle
                recordEvent("Call audio session cleanly released")
                notifyListeners()
            }
        }
    }

    /** Release media session. */
    override suspend fun stopMediaSession() {
        transitionMutex.withLock {
            if (_activeOwner != AudioUseCase.media) return@withLock
            _activeOwner = null
            _state = AudioSessionState.idle
            recordEvent("Media audio session stopped")
            notifyListeners()
        }
    }

    /** Handle interruptions (phone call, alarm, audio focus loss).
     *  Dart is synchronous and NOT serialized; the port preserves that. */
    override fun onInterruption(began: Boolean) {
        if (began) {
            recordEvent("Audio interruption began")
            _state = AudioSessionState.interrupted
            notifyListeners()
        } else {
            recordEvent("Audio interruption ended, recovering")
            _state = AudioSessionState.recovering
            if (_activeOwner == AudioUseCase.communication) {
                _state = stateForRoute(_activeRoute)
            } else if (_activeOwner == AudioUseCase.media) {
                _state = AudioSessionState.mediaActive
            } else {
                _state = AudioSessionState.idle
            }
            notifyListeners()
        }
    }

    companion object {
        fun stateForRoute(route: AudioOutputRoute): AudioSessionState = when (route) {
            AudioOutputRoute.receiver -> AudioSessionState.receiverCall
            AudioOutputRoute.speaker -> AudioSessionState.speakerphoneCall
            AudioOutputRoute.bluetooth,
            AudioOutputRoute.wiredHeadset,
            AudioOutputRoute.usb -> AudioSessionState.externalCall
            else -> AudioSessionState.receiverCall
        }
    }
}
