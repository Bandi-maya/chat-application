package com.example.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicIslandPrivacyPolicyTest {
    @Test
    fun capturesOnlyWhenEnabledAndOverlayGrantedOnPublicScreen() {
        assertTrue(
            DynamicIslandPrivacyPolicy.shouldCapture(
                enabled = true,
                overlayPermissionGranted = true,
                privateScreen = false,
            ),
        )
    }

    @Test
    fun failsClosedForEveryMissingPermissionOrPrivateScreen() {
        assertFalse(DynamicIslandPrivacyPolicy.shouldCapture(false, true, false))
        assertFalse(DynamicIslandPrivacyPolicy.shouldCapture(true, false, false))
        assertFalse(DynamicIslandPrivacyPolicy.shouldCapture(true, true, true))
        assertFalse(DynamicIslandPrivacyPolicy.shouldCapture(false, false, true))
    }
}
