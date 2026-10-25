package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPolicyParityTest {

    private class FakePlatform : ChatyAudioPlatform {
        val routeCalls = mutableListOf<Pair<AudioUseCase, AudioOutputRoute>>()
        val releases = mutableListOf<AudioUseCase>()
        var failNextSetRoute: Boolean = false
        var failOnRoute: AudioOutputRoute? = null
        var setRouteGate: CompletableDeferred<Unit>? = null
        var concurrentCalls = 0
        var maxConcurrentCalls = 0
        val capsFlow = MutableSharedFlow<AudioCapabilities>(extraBufferCapacity = 8)
        val routesFlow = MutableSharedFlow<AudioOutputRoute>(extraBufferCapacity = 8)
        var nextCaps: AudioCapabilities = AudioCapabilities(
            availableRoutes = listOf(AudioOutputRoute.receiver, AudioOutputRoute.speaker),
            activeRoute = AudioOutputRoute.receiver,
            dualOutputStatus = DualOutputStatus.notVerified,
        )

        override suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities = nextCaps
        override suspend fun getCurrentRoute(useCase: AudioUseCase): AudioOutputRoute = nextCaps.activeRoute
        override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities {
            concurrentCalls++
            maxConcurrentCalls = maxOf(maxConcurrentCalls, concurrentCalls)
            try {
                setRouteGate?.await()
                routeCalls.add(useCase to route)
                if (failNextSetRoute || failOnRoute == route) {
                    failNextSetRoute = false
                    throw RuntimeException("platform route failure")
                }
                return nextCaps.copy(activeRoute = route)
            } finally {
                concurrentCalls--
            }
        }
        override suspend fun getDualOutputStatus(useCase: AudioUseCase): DualOutputStatus = DualOutputStatus.notVerified
        override suspend fun releaseSession(useCase: AudioUseCase) { releases.add(useCase) }
        override val capabilityChanges: Flow<AudioCapabilities> get() = capsFlow
        override val routeChanges: Flow<AudioOutputRoute> get() = routesFlow
    }

    private class Fixture(scope: kotlinx.coroutines.CoroutineScope) {
        val platform = FakePlatform()
        val capabilities = AudioCapabilityService(platform, scope)
        val coordinator = AudioPolicyCoordinator(capabilities)
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope) = Fixture(scope)

    @Test fun `startCallSession acquires communication ownership with receiver default`() = runTest {
        val f = fixture(backgroundScope)
        val ok = f.coordinator.startCallSession()
        assertTrue(ok)
        assertEquals(AudioUseCase.communication, f.coordinator.activeOwner)
        assertEquals(AudioOutputRoute.receiver, f.coordinator.activeRoute)
        assertEquals(AudioSessionState.receiverCall, f.coordinator.state)
        assertEquals(listOf(AudioUseCase.communication to AudioOutputRoute.receiver), f.platform.routeCalls)
    }

    @Test fun `startCallSession video default requests speaker and yields speakerphoneCall`() = runTest {
        val f = fixture(backgroundScope)
        val ok = f.coordinator.startCallSession(initialRoute = AudioOutputRoute.speaker)
        assertTrue(ok)
        assertEquals(AudioSessionState.speakerphoneCall, f.coordinator.state)
    }

    @Test fun `startCallSession failure yields failed state and false`() = runTest {
        val f = fixture(backgroundScope)
        f.platform.failNextSetRoute = true
        val ok = f.coordinator.startCallSession()
        assertFalse(ok)
        assertEquals(AudioSessionState.failed, f.coordinator.state)
        assertTrue(f.coordinator.diagnostics.any { it.error != null })
    }

    @Test fun `setCallRoute rejected when call not owner`() = runTest {
        val f = fixture(backgroundScope)
        val ok = f.coordinator.setCallRoute(AudioOutputRoute.speaker)
        assertFalse(ok)
        assertTrue(f.platform.routeCalls.isEmpty())
    }

    @Test fun `setCallRoute success updates route and state`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startCallSession()
        val ok = f.coordinator.setCallRoute(AudioOutputRoute.speaker)
        assertTrue(ok)
        assertEquals(AudioOutputRoute.speaker, f.coordinator.activeRoute)
        assertEquals(AudioSessionState.speakerphoneCall, f.coordinator.state)
    }

    @Test fun `setCallRoute failure falls back to speaker and returns false`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startCallSession()
        f.platform.failOnRoute = AudioOutputRoute.bluetooth
        val ok = f.coordinator.setCallRoute(AudioOutputRoute.bluetooth)
        assertFalse(ok)
        // Fallback request issued
        assertEquals(AudioOutputRoute.speaker, f.platform.routeCalls.last().second)
        assertEquals(AudioOutputRoute.speaker, f.coordinator.activeRoute)
        assertEquals(AudioSessionState.speakerphoneCall, f.coordinator.state)
    }

    @Test fun `media cannot override active call`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startCallSession()
        val ok = f.coordinator.startMediaSession()
        assertFalse(ok)
        assertEquals(AudioUseCase.communication, f.coordinator.activeOwner)
        assertEquals(AudioSessionState.receiverCall, f.coordinator.state)
    }

    @Test fun `media session starts when idle`() = runTest {
        val f = fixture(backgroundScope)
        assertTrue(f.coordinator.startMediaSession())
        assertEquals(AudioUseCase.media, f.coordinator.activeOwner)
        assertEquals(AudioSessionState.mediaActive, f.coordinator.state)
    }

    @Test fun `notification during call plays without reconfiguring route`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startCallSession()
        val routeCallsBefore = f.platform.routeCalls.size
        var played = false
        f.coordinator.playNotificationSound { played = true }
        assertTrue(played)
        assertEquals(routeCallsBefore, f.platform.routeCalls.size)
    }

    @Test fun `notification when idle plays`() = runTest {
        val f = fixture(backgroundScope)
        var played = false
        f.coordinator.playNotificationSound { played = true }
        assertTrue(played)
    }

    @Test fun `endCallSession releases and resets to idle`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startCallSession()
        f.coordinator.endCallSession()
        assertNull(f.coordinator.activeOwner)
        assertEquals(AudioOutputRoute.systemDefault, f.coordinator.activeRoute)
        assertEquals(AudioSessionState.idle, f.coordinator.state)
        assertEquals(listOf(AudioUseCase.communication), f.platform.releases)
    }

    @Test fun `endCallSession no-ops when call not owner`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.endCallSession()
        assertTrue(f.platform.releases.isEmpty())
    }

    @Test fun `stopMediaSession releases only media ownership`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startMediaSession()
        f.coordinator.stopMediaSession()
        assertEquals(AudioSessionState.idle, f.coordinator.state)
        assertNull(f.coordinator.activeOwner)
        // No platform release for media (Dart only releases communication)
        assertTrue(f.platform.releases.isEmpty())
        // No-op when owner is call
        f.coordinator.startCallSession()
        f.coordinator.stopMediaSession()
        assertEquals(AudioUseCase.communication, f.coordinator.activeOwner)
    }

    @Test fun `interruption during call recovers to route state`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startCallSession(initialRoute = AudioOutputRoute.speaker)
        f.coordinator.onInterruption(began = true)
        assertEquals(AudioSessionState.interrupted, f.coordinator.state)
        f.coordinator.onInterruption(began = false)
        assertEquals(AudioSessionState.speakerphoneCall, f.coordinator.state)
    }

    @Test fun `interruption during media recovers to mediaActive, idle stays idle`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startMediaSession()
        f.coordinator.onInterruption(began = true)
        f.coordinator.onInterruption(began = false)
        assertEquals(AudioSessionState.mediaActive, f.coordinator.state)
        val g = fixture(backgroundScope)
        g.coordinator.onInterruption(began = true)
        g.coordinator.onInterruption(began = false)
        assertEquals(AudioSessionState.idle, g.coordinator.state)
    }

    @Test fun `diagnostics capped at 100`() = runTest {
        val f = fixture(backgroundScope)
        f.coordinator.startCallSession()
        repeat(150) { f.coordinator.setCallRoute(AudioOutputRoute.speaker) }
        assertEquals(100, f.coordinator.diagnostics.size)
    }

    @Test fun `stateForRoute mapping matches Dart`() {
        assertEquals(AudioSessionState.receiverCall, AudioPolicyCoordinator.stateForRoute(AudioOutputRoute.receiver))
        assertEquals(AudioSessionState.speakerphoneCall, AudioPolicyCoordinator.stateForRoute(AudioOutputRoute.speaker))
        assertEquals(AudioSessionState.externalCall, AudioPolicyCoordinator.stateForRoute(AudioOutputRoute.bluetooth))
        assertEquals(AudioSessionState.externalCall, AudioPolicyCoordinator.stateForRoute(AudioOutputRoute.wiredHeadset))
        assertEquals(AudioSessionState.externalCall, AudioPolicyCoordinator.stateForRoute(AudioOutputRoute.usb))
        assertEquals(AudioSessionState.receiverCall, AudioPolicyCoordinator.stateForRoute(AudioOutputRoute.systemDefault))
    }

    @Test fun `listeners fire on transitions`() = runTest {
        val f = fixture(backgroundScope)
        var count = 0
        f.coordinator.addListener { count++ }
        f.coordinator.startCallSession()
        assertTrue(count >= 2) // callStarting + established
    }

    @Test fun `transitions serialize - platform never sees concurrent calls`() = runTest {
        val f = fixture(backgroundScope)
        f.platform.setRouteGate = CompletableDeferred()
        val a = async { f.coordinator.startCallSession() }
        val b = async { f.coordinator.startCallSession() }
        advanceUntilIdle()
        f.platform.setRouteGate!!.complete(Unit)
        a.await(); b.await()
        assertEquals(1, f.platform.maxConcurrentCalls)
    }

    @Test fun `capability service caches and streams update cache`() = runTest {
        val f = fixture(backgroundScope)
        val first = f.capabilities.getCapabilities(AudioUseCase.communication)
        assertEquals(AudioOutputRoute.receiver, first.activeRoute)
        advanceTimeBy(1) // let backgroundScope collectors start before emitting (replay=0 drops early emissions)
        // Stream event replaces cached communication capabilities
        f.platform.capsFlow.emit(first.copy(activeRoute = AudioOutputRoute.bluetooth, dualOutputStatus = DualOutputStatus.active))
        advanceTimeBy(1) // backgroundScope collectors advance on virtual time only
        assertEquals(AudioOutputRoute.bluetooth, f.capabilities.activeCommunicationRoute)
        assertTrue(f.capabilities.isDualOutputActive)
        // Route stream patches activeRoute only
        f.platform.routesFlow.emit(AudioOutputRoute.wiredHeadset)
        advanceTimeBy(1)
        assertEquals(AudioOutputRoute.wiredHeadset, f.capabilities.activeCommunicationRoute)
        // Release clears cache
        f.capabilities.releaseSession(AudioUseCase.communication)
        assertEquals(AudioOutputRoute.systemDefault, f.capabilities.activeCommunicationRoute)
        assertEquals(DualOutputStatus.notVerified, f.capabilities.communicationDualStatus)
    }

    @Test fun `capability service default routes when uncached`() = runTest {
        val f = fixture(backgroundScope)
        assertEquals(
            listOf(AudioOutputRoute.systemDefault, AudioOutputRoute.speaker, AudioOutputRoute.receiver),
            f.capabilities.availableCommunicationRoutes,
        )
    }

    @Test fun `settings controller preference mode and distribution`() = runTest {
        val f = fixture(backgroundScope)
        val settings = AudioSettingsController(f.capabilities, f.coordinator)
        assertEquals(AudioPreferenceMode.automatic, settings.preferenceMode)
        var notified = 0
        settings.addListener { notified++ }
        settings.setPreferenceMode(AudioPreferenceMode.automatic) // no-op
        assertEquals(0, notified)
        settings.setPreferenceMode(AudioPreferenceMode.preferStereo)
        assertEquals(1, notified)
        assertEquals(AudioPreferenceMode.preferStereo, settings.preferenceMode)
        val dist = AudioSettingsController.computeTestDistribution()
        assertEquals(3, dist["Verified Dual Output"])
        assertEquals(1, dist["Native Stereo"])
        assertEquals(1, dist["Standard Single Route"])
        assertEquals(1, dist["Unsupported or Failed"])
        assertEquals(1, dist["Not Yet Tested"])
        assertEquals(7, AudioSettingsController.verifiedDeviceRecords.size)
    }

    @Test fun `media and notification controllers delegate through gate`() = runTest {
        val f = fixture(backgroundScope)
        val media = MediaAudioController(f.coordinator)
        val notifications = NotificationAudioController(f.coordinator)
        assertTrue(media.requestMediaFocus())
        assertEquals(AudioSessionState.mediaActive, f.coordinator.state)
        var played = false
        notifications.playInAppNotificationSound { played = true }
        assertTrue(played)
        media.abandonMediaFocus()
        assertEquals(AudioSessionState.idle, f.coordinator.state)
    }
}
