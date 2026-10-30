package com.example.chat.ui.calls

import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.CallSignalingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Receiver-hangup reachability: after a process restart mid-call the presentation
 * controller has no minimize record, so the full call UI (hangup + controls) must be
 * restored. An intentional minimize (island/PiP) is preserved, and an ended call is
 * never resurrected on screen.
 *
 * The demo-call seam (startDemoCall) drives a real CallSignalingService through
 * initiating -> ringing (~1.2s) -> connected (~1.8s); total real delay ~3.5s per test.
 */
class CallPresentationRestoreTest {

    private class MemoryStorage : CallPresentationPrefsStorage {
        private val bools = mutableMapOf<String, Boolean>()
        private val strings = mutableMapOf<String, String>()
        override suspend fun getBool(key: String): Boolean? = bools[key]
        override suspend fun getString(key: String): String? = strings[key]
        override suspend fun putBool(key: String, value: Boolean): Boolean { bools[key] = value; return true }
        override suspend fun putString(key: String, value: String): Boolean { strings[key] = value; return true }
    }

    private fun newService(scope: CoroutineScope) = CallSignalingService(
        scope = scope,
        currentUserId = { "self" },
        signalSender = { _, _ -> },
        mediaFactory = { throw UnsupportedOperationException("demo calls never open media") },
    )

    private fun awaitCondition(timeoutMs: Long, what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.sleep(50)
        }
    }

    private fun connectedVoiceService(scope: CoroutineScope): CallSignalingService {
        val service = newService(scope)
        service.startDemoCall("peer-1", "Peer One", isVideo = false, isOutgoing = true)
        awaitCondition(8000, "demo call to connect") {
            service.currentSession?.state == CallSessionState.connected
        }
        return service
    }

    @Test
    fun restartWithActiveCallRestoresFullCallUi() {
        val scope = CoroutineScope(Dispatchers.Default)
        val service = connectedVoiceService(scope)
        // Fresh controller = process restart: no minimize record exists.
        val controller = CallPresentationController(service, CallPresentationPreferencesStore(MemoryStorage(), scope))
        controller.start()
        assertEquals(CallPresentationMode.fullScreen, controller.mode)
        assertTrue(controller.isFullScreen)
        service.endCall()
        awaitCondition(5000, "call to end") { !service.hasActiveSession }
    }

    @Test
    fun intentionalMinimizeIsPreservedAcrossLifecycle() {
        val scope = CoroutineScope(Dispatchers.Default)
        val service = connectedVoiceService(scope)
        val controller = CallPresentationController(service, CallPresentationPreferencesStore(MemoryStorage(), scope))
        controller.start()
        assertTrue(controller.isFullScreen)
        // User intentionally minimizes: voice call collapses to the island.
        controller.minimizeToPipOrIsland()
        assertEquals(CallPresentationMode.inAppIsland, controller.mode)
        // Background then foreground: the minimize choice survives.
        controller.onAppLifecycleChanged(CallPresentationController.AppLifecycle.backgrounded)
        assertEquals(CallPresentationMode.backgroundNotification, controller.mode)
        controller.onAppLifecycleChanged(CallPresentationController.AppLifecycle.resumed)
        assertEquals(CallPresentationMode.inAppIsland, controller.mode)
        service.endCall()
        awaitCondition(5000, "call to end") { !service.hasActiveSession }
    }

    @Test
    fun endedCallIsNeverResurrectedOnScreen() {
        val scope = CoroutineScope(Dispatchers.Default)
        val service = connectedVoiceService(scope)
        val controller = CallPresentationController(service, CallPresentationPreferencesStore(MemoryStorage(), scope))
        controller.start()
        assertTrue(controller.isFullScreen)
        service.endCall()
        awaitCondition(5000, "call to end") { !service.hasActiveSession }
        awaitCondition(5000, "presentation to clear") { controller.mode == CallPresentationMode.none }
        // A later reconcile with no session keeps the UI cleared.
        controller.onAppLifecycleChanged(CallPresentationController.AppLifecycle.resumed)
        assertEquals(CallPresentationMode.none, controller.mode)
    }
}
