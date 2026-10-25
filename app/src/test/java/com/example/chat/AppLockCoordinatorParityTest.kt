package com.example.chat

import com.example.chat.data.services.AppLockCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockCoordinatorParityTest {
    private var now = 1_000_000L
    private var auth = true
    private var enabled = true
    private var timeout = "1m"
    private var ready = true

    private fun make() = AppLockCoordinator(
        isAuthenticated = { auth }, isLockEnabled = { enabled },
        autoLockTimeout = { timeout }, isMethodReady = { ready }, nowMillis = { now },
    )

    @Test
    fun locksOnlyAfterTimeoutElapsed() = runBlocking {
        val c = make()
        c.onBackgrounded(); now += 59_000
        c.onResumed()
        assertFalse(c.lockRequired.value)
        c.onBackgrounded(); now += 60_000
        c.onResumed()
        assertTrue(c.lockRequired.value)
    }

    @Test
    fun firstBackgroundTimestampWins() = runBlocking {
        val c = make()
        c.onBackgrounded(); now += 40_000
        c.onBackgrounded(); now += 30_000 // ??= keeps the earlier stamp: 70s total
        c.onResumed()
        assertTrue(c.lockRequired.value)
    }

    @Test
    fun noLockWhenMethodNotReadyOrLoggedOutOrDisabled() = runBlocking {
        ready = false
        var c = make(); c.onBackgrounded(); now += 120_000; c.onResumed()
        assertFalse(c.lockRequired.value)
        ready = true; auth = false
        c = make(); c.onBackgrounded(); now += 120_000; c.onResumed()
        assertFalse(c.lockRequired.value)
        auth = true; enabled = false
        c = make(); c.onBackgrounded(); now += 120_000; c.onResumed()
        assertFalse(c.lockRequired.value)
    }

    @Test
    fun resumeWithoutBackgroundNeverLocksAndImmediateTimeoutLocks() = runBlocking {
        var c = make(); c.onResumed()
        assertFalse(c.lockRequired.value)
        timeout = "Immediately"
        c = make(); c.onBackgrounded(); c.onResumed()
        assertTrue(c.lockRequired.value)
    }

    @Test
    fun freshLoginSuppressesInitialLockAndSignOutClears() = runBlocking {
        val c = make()
        c.onSignedIn()
        c.scheduleInitialIfNeeded()
        assertFalse(c.lockRequired.value)
        c.onResumed() // clears fresh-login flag
        c.scheduleInitialIfNeeded()
        assertTrue(c.lockRequired.value)
        c.onSignedOut()
        assertFalse(c.lockRequired.value)
        c.onUnlocked()
        assertFalse(c.lockRequired.value)
    }
}
