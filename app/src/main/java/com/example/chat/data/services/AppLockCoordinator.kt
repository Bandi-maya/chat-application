package com.example.chat.data.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Port of the app-lock state machine in main.dart's _ChatyAppState
 * (_backgroundedAt, _applyAutoLockOnResume, _appLockRequired,
 * _freshLoginSession, _initialAppLockScheduled, _scheduleInitialAppLockIfNeeded).
 *
 * Pure logic with injected time/readiness so it is JVM-unit-testable. The UI
 * shows the lock overlay when [lockRequired] is true AND the caller also
 * sees authenticated + lock enabled (Dart shouldShowLock).
 */
class AppLockCoordinator(
    private val isAuthenticated: () -> Boolean,
    private val isLockEnabled: () -> Boolean,
    private val autoLockTimeout: () -> String,
    /** Dart _isCurrentLockMethodReady. */
    private val isMethodReady: suspend () -> Boolean,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val _lockRequired = MutableStateFlow(false)
    val lockRequired: StateFlow<Boolean> get() = _lockRequired

    private var backgroundedAt: Long? = null
    private var freshLoginSession = false
    private var initialScheduled = false
    private var checking = false

    /** Dart paused/hidden/detached branch: `_backgroundedAt ??= now`. */
    fun onBackgrounded() {
        if (backgroundedAt == null) backgroundedAt = nowMillis()
    }

    /** Dart resumed branch: clears fresh-login flag, then _applyAutoLockOnResume. */
    suspend fun onResumed() {
        freshLoginSession = false
        if (!isAuthenticated() || !isLockEnabled()) {
            backgroundedAt = null
            return
        }
        val since = backgroundedAt
        backgroundedAt = null
        if (since == null) return
        if (nowMillis() - since < ProtectedResourceGate.parseTimeoutMillis(autoLockTimeout())) return
        if (_lockRequired.value || checking) return
        checking = true
        val ready = try { isMethodReady() } finally { checking = false }
        if (ready && isAuthenticated() && isLockEnabled() && !_lockRequired.value) {
            _lockRequired.value = true
        }
    }

    /** Dart _scheduleInitialAppLockIfNeeded (cold start with a restored session). */
    suspend fun scheduleInitialIfNeeded() {
        if (!isAuthenticated() || !isLockEnabled()) return
        if (freshLoginSession || initialScheduled || _lockRequired.value || checking) return
        checking = true
        val ready = try { isMethodReady() } finally { checking = false }
        if (!ready || freshLoginSession || !isAuthenticated() || !isLockEnabled()) return
        initialScheduled = true
        _lockRequired.value = true
    }

    /** Dart signedIn auth event. */
    fun onSignedIn() {
        freshLoginSession = true
        initialScheduled = false
        backgroundedAt = null
        _lockRequired.value = false
    }

    /** Dart signedOut auth event. */
    fun onSignedOut() {
        freshLoginSession = false
        initialScheduled = false
        backgroundedAt = null
        _lockRequired.value = false
    }

    /** Dart _handleSecurityPreferenceChanged when lock gets disabled. */
    fun onLockDisabled() {
        initialScheduled = false
        backgroundedAt = null
        _lockRequired.value = false
    }

    /** Dart _handleAppUnlocked. */
    fun onUnlocked() {
        _lockRequired.value = false
    }
}
