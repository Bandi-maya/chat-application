package com.example.chat.ui.calls

import com.example.chat.data.services.CallSessionState
import org.junit.Assert.*
import org.junit.Test

class CallPipStatusTest {
    @Test fun onlyConnectedStateShowsDuration() {
        assertEquals("00:00", callPipStatus(CallSessionState.connected, -1))
        assertEquals("Incoming call", callPipStatus(CallSessionState.incoming, 120))
        assertEquals("Starting…", callPipStatus(CallSessionState.initiating, 120))
        assertEquals("Ringing…", callPipStatus(CallSessionState.ringing, 0))
        assertEquals("Connecting…", callPipStatus(CallSessionState.connecting, 80))
        assertEquals("Reconnecting…", callPipStatus(CallSessionState.reconnecting, 80))
        assertEquals("01:20", callPipStatus(CallSessionState.connected, 80))
        assertEquals("Call failed", callPipStatus(CallSessionState.failed, 80))
    }
}
