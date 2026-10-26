package com.example.chat.ui.core.system

/** What main.dart wants published as presence. */
enum class PresenceAction { None, Offline, Online }

/** Result of a decision plus the new value of Dart's `_presenceOverrideWasActive`. */
data class PresenceDecision(val action: PresenceAction, val overrideWasActive: Boolean)

/** Lifecycle states the Dart handler distinguishes. */
enum class AppLifecycle { resumed, inactive, hidden, paused, detached }

/**
 * Pure port of main.dart presence switches:
 *  - _applyPresenceOverrideOnPreferenceChange (settings toggled while running)
 *  - didChangeAppLifecycleState presence tail (airplane/ghost/alwaysOnline)
 * airplane = home.airplaneModeSimulator || gb 'yo_want_airplanemode'; ghost = home.ghostMode || gb 'yo_want_ghostmode';
 * alwaysOnline = universal.enableAlwaysOnline || gb 'always_online'.
 */
object PresencePolicy {
    fun onPreferenceChange(airplane: Boolean, ghost: Boolean, wasActive: Boolean, authenticated: Boolean, resumed: Boolean): PresenceDecision {
        if (airplane || ghost) {
            return PresenceDecision(if (authenticated) PresenceAction.Offline else PresenceAction.None, true)
        }
        if (!wasActive) return PresenceDecision(PresenceAction.None, false)
        return PresenceDecision(if (authenticated && resumed) PresenceAction.Online else PresenceAction.None, false)
    }

    fun onLifecycle(state: AppLifecycle, airplane: Boolean, ghost: Boolean, alwaysOnline: Boolean, authenticated: Boolean, wasActive: Boolean): PresenceDecision {
        if (!authenticated) return PresenceDecision(PresenceAction.None, wasActive)
        if (airplane || ghost) return PresenceDecision(PresenceAction.Offline, true)
        val action = when {
            state == AppLifecycle.resumed || alwaysOnline -> PresenceAction.Online
            state == AppLifecycle.paused || state == AppLifecycle.detached || state == AppLifecycle.hidden -> PresenceAction.Offline
            else -> PresenceAction.None
        }
        return PresenceDecision(action, false)
    }
}
