package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source-contract tests: these prove wiring shape, not Android lifecycle execution. */
class BackgroundTimerWiringTest {
    private fun source(path: String): String {
        val root = if (File("app/src/main").exists()) File("app/src/main") else File("src/main")
        return File(root, path).readText()
    }
    private val api get() = source("kotlin/com/example/chat/data/api/ApiBackendService.kt")
    @Test fun catchUpStartsOnlyInForeground() {
        assertTrue(api.contains("if (!syncForeground || session.token == null || DemoMode.active) return"))
        assertTrue(api.contains("if (!syncForeground || session.token == null || DemoMode.active) break"))
    }
    @Test fun pauseCancelsCatchUpJob() {
        val stop = api.substringAfter("if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {").substringBefore("callCoordinator.foreground(false)")
        assertTrue(stop.contains("syncForeground = false"))
        assertTrue(stop.contains("activeSyncJob?.cancel()"))
        assertTrue(stop.contains("activeSyncJob = null"))
    }
    @Test fun foregroundLifecycleRestartsCatchUp() {
        val start = api.substringAfter("if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {").substringBefore("if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP)")
        assertTrue(start.contains("syncForeground = true"))
        assertTrue(start.contains("startBackgroundSync()"))
    }
    @Test fun healthHasNoRecurringTimer() {
        val s=source("java/com/example/chat/data/services/ConnectionHealthService.kt")
        assertFalse(s.contains("while (isActive)"));assertFalse(s.contains("delay(30_000)"))
        assertTrue(s.contains("override fun onResume"));assertTrue(s.contains("fun scheduleProbe("))
    }
    @Test fun automationHasNoRecurringTimer() {
        val s=source("java/com/example/chat/data/services/MessageAutomationService.kt")
        assertFalse(s.contains("while (isActive)"));assertFalse(s.contains("delay(30_000)"))
        assertTrue(s.contains("addListener(onPreferencesChanged)"));assertTrue(s.contains("if (syncPending) launchSync()"))
    }
    @Test fun deliveryAndSessionServicesNotRemoved() {
        assertTrue(source("kotlin/com/example/chat/ChatyApplication.kt").contains("ChatyRealtimeForegroundService.stop(this)"))
        assertTrue(api.contains("SessionRefreshWorker.schedule(appContext)"))
    }
}
