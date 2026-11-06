package com.example.chat

/**
 * Pure decision rules for consuming a pending launcher-shortcut action. Extracted from
 * AppRoot so the full session/TTL/lock matrix is JVM-testable without a Compose runtime
 * or an auth session. No behavior change: AppRoot's effect must mirror these branches.
 */
object LauncherShortcutPolicy {

    enum class Decision {
        /** Route to New chat now. */
        OpenNewChat,
        /** Keep waiting (still signed out, locked, or before the shell). */
        Wait,
        /** Drop the pending action without firing (expired or wrong session). */
        Drop,
    }

    fun decide(
        pendingAction: String?,
        fresh: Boolean,
        scopeAllowed: Boolean,
        authenticatedShellVisible: Boolean,
        lockRequired: Boolean,
    ): Decision = when {
        pendingAction == null -> Decision.Wait
        !fresh -> Decision.Drop
        !scopeAllowed -> Decision.Drop
        pendingAction == LauncherShortcutRouter.ACTION_NEW_CHAT &&
            authenticatedShellVisible && !lockRequired -> Decision.OpenNewChat
        else -> Decision.Wait
    }
}
