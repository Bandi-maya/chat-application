package com.example.chat.ui.auth

import com.example.chat.core.validators.ChatyValidators

/** Pure logic behind the register screen's username-availability indicator
 *  (approved preview 8: checking / available / taken / invalid / could not
 *  check). Split out of RegisterScreen so the mapping is JVM-testable, same
 *  pattern as OtpVerificationLogic.
 *
 *  The prepared /auth/username-availability public route is separate from the
 *  authenticated profile endpoint. Until deployed and returning a valid reply,
 *  the indicator says "could not check". Final Create still enforces uniqueness.
 */
object UsernameAvailabilityLogic {

    /** Debounce before probing, matching the workspace check (Dart: 400ms). */
    const val DEBOUNCE_MS = 400L

    /** What a wired probe can answer. A probe that fails for any reason
     *  (offline, 401, 5xx, missing endpoint) is not a Probe: the screen maps
     *  it to CouldNotCheck instead. */
    enum class Probe { Available, Taken }

    /** Indicator states from the approved preview 8. */
    enum class State { Idle, Checking, Available, Taken, Invalid, CouldNotCheck }

    /** Local format rule behind the Invalid state ("Show the server/local
     *  format rule."). Null when the format is fine and a server probe may
     *  run. Empty input is Idle, not Invalid, so it returns null here too. */
    fun localFormatError(username: String): String? {
        val clean = username.trim()
        if (clean.isEmpty()) return null
        return ChatyValidators.validateUsername(clean, forRegistration = true)
    }

    /** Map a probe answer; a missing probe (no endpoint wired) is
     *  CouldNotCheck, never Available. */
    fun stateForProbe(probe: Probe?): State = when (probe) {
        Probe.Available -> State.Available
        Probe.Taken -> State.Taken
        null -> State.CouldNotCheck
    }
}
