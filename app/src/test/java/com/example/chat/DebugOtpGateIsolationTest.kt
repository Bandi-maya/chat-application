package com.example.chat

import com.example.chat.ui.auth.DebugOtpGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DebugOtpGateIsolationTest {
    @Test fun approvedCodeAcceptedOnlyWhenDebugImplementationExists() {
        assertEquals(BuildConfig.DEBUG, DebugOtpGate.acceptsDebugCode("000000"))
        assertFalse(DebugOtpGate.acceptsDebugCode("123456"))
    }
}
