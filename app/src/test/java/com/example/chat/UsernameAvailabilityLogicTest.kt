package com.example.chat

import com.example.chat.ui.auth.UsernameAvailabilityLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Register screen username-availability indicator (approved preview 8):
 *  probe mapping and the local format rule behind the Invalid state. */
class UsernameAvailabilityLogicTest {

    @Test
    fun probeMappingNeverClaimsAvailableWithoutAnAnswer() {
        assertEquals(
            UsernameAvailabilityLogic.State.Available,
            UsernameAvailabilityLogic.stateForProbe(UsernameAvailabilityLogic.Probe.Available),
        )
        assertEquals(
            UsernameAvailabilityLogic.State.Taken,
            UsernameAvailabilityLogic.stateForProbe(UsernameAvailabilityLogic.Probe.Taken),
        )
        // No probe wired (endpoint requires login) is CouldNotCheck, never Available.
        assertEquals(
            UsernameAvailabilityLogic.State.CouldNotCheck,
            UsernameAvailabilityLogic.stateForProbe(null),
        )
    }

    @Test
    fun localFormatRuleMatchesTheValidator() {
        assertNull(UsernameAvailabilityLogic.localFormatError(""))
        assertNull(UsernameAvailabilityLogic.localFormatError("maya_chat"))
        assertEquals(
            "Username must be at least 3 characters",
            UsernameAvailabilityLogic.localFormatError("ab"),
        )
        assertEquals(
            "Only letters, numbers, dots and underscores are allowed",
            UsernameAvailabilityLogic.localFormatError("maya chat"),
        )
        assertEquals(
            "Username can't start or end with a dot",
            UsernameAvailabilityLogic.localFormatError(".maya"),
        )
        assertEquals(
            "This username is reserved and cannot be registered",
            UsernameAvailabilityLogic.localFormatError("admin"),
        )
    }
}
