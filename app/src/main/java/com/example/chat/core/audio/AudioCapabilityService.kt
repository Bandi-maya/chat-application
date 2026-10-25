package com.example.chat.core.audio

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Service responsible for querying, caching, and streaming hardware and OS
 * audio capabilities. Exact port of lib/core/audio/audio_capability_service.dart.
 *
 * Dart used ChangeNotifier + broadcast stream subscriptions created in the
 * constructor; the Kotlin port collects the platform flows on an injected
 * scope (constructor-injected for pure-JVM testability) and fans changes out
 * to synchronous listeners.
 */
class AudioCapabilityService(
    private val platform: ChatyAudioPlatform,
    scope: CoroutineScope,
) {
    private val cachedCapabilities = mutableMapOf<AudioUseCase, AudioCapabilities>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        // Mirrors _initStreams(): capability stream replaces the cached
        // communication capabilities; route stream patches activeRoute only
        // when a communication capability is already cached. Errors are
        // swallowed exactly like the Dart onError: (_) {} handlers.
        scope.launch {
            platform.capabilityChanges
                .catch { }
                .collect { caps ->
                    cachedCapabilities[AudioUseCase.communication] = caps
                    notifyListeners()
                }
        }
        scope.launch {
            platform.routeChanges
                .catch { }
                .collect { route ->
                    val current = cachedCapabilities[AudioUseCase.communication]
                    if (current != null) {
                        cachedCapabilities[AudioUseCase.communication] =
                            current.copy(activeRoute = route)
                        notifyListeners()
                    }
                }
        }
    }

    fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.forEach { it() } }

    /** Get capabilities for a use case, returning cache if available or querying platform. */
    suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities {
        val cached = cachedCapabilities[useCase]
        if (cached != null) return cached
        val queried = platform.getCapabilities(useCase)
        cachedCapabilities[useCase] = queried
        notifyListeners()
        return queried
    }

    /** Active route for communication use case. */
    val activeCommunicationRoute: AudioOutputRoute
        get() = cachedCapabilities[AudioUseCase.communication]?.activeRoute
            ?: AudioOutputRoute.systemDefault

    /** Current dual output status for communication. */
    val communicationDualStatus: DualOutputStatus
        get() = cachedCapabilities[AudioUseCase.communication]?.dualOutputStatus
            ?: DualOutputStatus.notVerified

    /** Check whether independent dual-speaker output is verified active. */
    val isDualOutputActive: Boolean
        get() = communicationDualStatus == DualOutputStatus.active

    /** Available routes for communication. */
    val availableCommunicationRoutes: List<AudioOutputRoute>
        get() = cachedCapabilities[AudioUseCase.communication]?.availableRoutes
            ?: listOf(
                AudioOutputRoute.systemDefault,
                AudioOutputRoute.speaker,
                AudioOutputRoute.receiver,
            )

    /** Request route selection through the platform. */
    suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities {
        val updated = platform.setPreferredRoute(useCase, route)
        cachedCapabilities[useCase] = updated
        notifyListeners()
        return updated
    }

    /** Release native session. */
    suspend fun releaseSession(useCase: AudioUseCase) {
        platform.releaseSession(useCase)
        cachedCapabilities.remove(useCase)
        notifyListeners()
    }
}
