package com.example.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pending launcher-shortcut actions. Static shortcuts (res/xml/shortcuts.xml) launch
 * MainActivity with EXTRA_SHORTCUT_ACTION; AppRoot consumes the action only after the
 * authenticated shell is visible and the app lock is satisfied, so a shortcut can never
 * bypass sign-in or the biometric/app-lock prompt.
 *
 * Session identity comes from ApiBackendService.authSnapshot(): the ApiAccountScope
 * generation increments on every auth transition including same-uid re-login, so a tap
 * captured under one login can never fire under a later one (A -> B -> A included).
 * Each tap is a new [PendingShortcut] instance with a monotonically increasing sequence,
 * so repeat taps always emit through the StateFlow. Freshness uses a monotonic clock
 * (System.nanoTime), immune to wall-clock changes.
 *
 * Kept free of android.* imports so the routing rules are JVM-testable; MainActivity
 * extracts the intent extra and scope, AppRoot observes [pending].
 */
object LauncherShortcutRouter {
    const val EXTRA_SHORTCUT_ACTION = "com.example.chat.extra.SHORTCUT_ACTION"
    const val ACTION_NEW_CHAT = "new_chat"

    /** How long a shortcut tap stays valid while waiting for auth/unlock. */
    const val PENDING_TTL_MILLIS: Long = 120_000L
    private const val PENDING_TTL_NANOS: Long = PENDING_TTL_MILLIS * 1_000_000L

    data class PendingShortcut(
        val action: String,
        /** User id captured at tap time, null when tapped signed out. */
        val accountUserId: String?,
        /** ApiAccountScope generation at tap time; identifies the exact login session. */
        val scopeGeneration: Long,
        /** Per-process tap identity; guarantees each tap is a distinct StateFlow emission. */
        val sequence: Long,
        val issuedAtNanos: Long,
    )

    private val _pending = MutableStateFlow<PendingShortcut?>(null)
    val pending: StateFlow<PendingShortcut?> = _pending
    private var sequence = 0L

    /** Test seam: the JVM unit tests substitute a fake monotonic clock. */
    internal var nanoClockForTesting: (() -> Long)? = null
    private fun nowNanos(): Long = nanoClockForTesting?.invoke() ?: System.nanoTime()

    fun onShortcutAction(action: String?, accountUserId: String?, scopeGeneration: Long) {
        if (action.isNullOrBlank()) return
        _pending.value = PendingShortcut(action, accountUserId, scopeGeneration, ++sequence, nowNanos())
    }

    /** True while a pending action is still inside its TTL (monotonic age in [0, TTL)). */
    fun isFresh(): Boolean {
        val p = _pending.value ?: return false
        return nowNanos() - p.issuedAtNanos in 0 until PENDING_TTL_NANOS
    }

    /** True when the current session may consume the pending action: a tap made signed out
     *  may be used by the next sign-in within the TTL; a tap made under a login fires only
     *  for that exact login session (same user AND same scope generation). */
    fun scopeAllows(currentUserId: String?, currentScopeGeneration: Long): Boolean {
        val p = _pending.value ?: return false
        return if (p.accountUserId == null) true
        else p.accountUserId == currentUserId && p.scopeGeneration == currentScopeGeneration
    }

    /** Drops an expired pending action; returns true when it cleared one. */
    fun clearIfExpired(): Boolean {
        if (_pending.value != null && !isFresh()) {
            consume()
            return true
        }
        return false
    }

    fun consume() {
        _pending.value = null
    }
}
