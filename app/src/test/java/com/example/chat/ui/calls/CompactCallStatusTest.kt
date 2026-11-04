package com.example.chat.ui.calls

import com.example.chat.data.services.CallSessionState
import org.junit.Assert.*
import org.junit.Test

class CompactCallStatusTest {
    @Test fun timerOnlyForConnectedState() {
        for (state in CallSessionState.entries) {
            val label = compactCallStatus(state, 83)
            if (state == CallSessionState.connected) assertEquals(formatCallDuration(83), label)
            else assertFalse("$state must not imply connected duration", label.contains(":"))
        }
        assertEquals("Reconnecting", compactCallStatus(CallSessionState.reconnecting, 83))
        assertEquals("Connecting", compactCallStatus(CallSessionState.connecting, 0))
    }
}
