package com.example.chat

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.example.chat.platform.LockBiometricPolicy
import com.example.chat.platform.StickyAuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Policy tests written from local_auth_android 1.0.56 (LocalAuthPlugin.java / AuthenticationHelper.java). Fakes only:
 *  no prompt, no enrollment, no hardware. They prove the decisions, not a device unlock. */
class LockBiometricPolicyTest {

    @Test fun weakAlreadyContainsStrongBits() {
        assertEquals(BIOMETRIC_WEAK, BIOMETRIC_WEAK or BIOMETRIC_STRONG)
    }

    @Test fun biometricOnlyNeverAllowsCredentialsAndShowsCancel() {
        assertEquals(BIOMETRIC_WEAK, LockBiometricPolicy.allowedAuthenticators(biometricOnly = true, deviceCredentialAvailable = true))
        assertTrue(LockBiometricPolicy.usesNegativeButton(biometricOnly = true, deviceCredentialAvailable = true))
    }

    @Test fun credentialPromptAddsDeviceCredentialAndDropsCancel() {
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, LockBiometricPolicy.allowedAuthenticators(false, true))
        assertFalse(LockBiometricPolicy.usesNegativeButton(false, true))
    }

    @Test fun credentialRequestedButUnavailableFallsBackToBiometricWithCancel() {
        assertEquals(BIOMETRIC_WEAK, LockBiometricPolicy.allowedAuthenticators(false, false))
        assertTrue(LockBiometricPolicy.usesNegativeButton(false, false))
    }

    @Test fun deviceCredentialCheckUsesKeyguardBelowApi30AndBiometricManagerFrom30() {
        for (sdk in 24..29) {
            assertTrue(LockBiometricPolicy.deviceCredentialAvailable(sdk, isDeviceSecure = true, canAuthenticateCredentialSuccess = false))
            assertFalse(LockBiometricPolicy.deviceCredentialAvailable(sdk, isDeviceSecure = false, canAuthenticateCredentialSuccess = true))
        }
        for (sdk in 30..36) {
            assertTrue(LockBiometricPolicy.deviceCredentialAvailable(sdk, isDeviceSecure = false, canAuthenticateCredentialSuccess = true))
            assertFalse(LockBiometricPolicy.deviceCredentialAvailable(sdk, isDeviceSecure = true, canAuthenticateCredentialSuccess = false))
        }
    }

    @Test fun deviceSupportedIsSecureOrWeakBiometric() {
        assertTrue(LockBiometricPolicy.isDeviceSupported(isDeviceSecure = true, weakSuccess = false))
        assertTrue(LockBiometricPolicy.isDeviceSupported(isDeviceSecure = false, weakSuccess = true))
        assertFalse(LockBiometricPolicy.isDeviceSupported(isDeviceSecure = false, weakSuccess = false))
    }

    @Test fun canCheckBiometricsIsHardwarePresence() {
        assertTrue(LockBiometricPolicy.canCheckBiometrics(weakStatusIsNoHardware = false))
        assertFalse(LockBiometricPolicy.canCheckBiometrics(weakStatusIsNoHardware = true))
    }

    @Test fun enrolledBiometricsOrderWeakThenStrong() {
        assertEquals(listOf("weak", "strong"), LockBiometricPolicy.enrolledBiometrics(true, true))
        assertEquals(listOf("weak"), LockBiometricPolicy.enrolledBiometrics(true, false))
        assertEquals(emptyList<String>(), LockBiometricPolicy.enrolledBiometrics(false, false))
    }

    @Test fun promptStringsAreLocalAuthDefaults() {
        assertEquals("Authentication required", LockBiometricPolicy.SIGN_IN_TITLE)
        assertEquals("Verify identity", LockBiometricPolicy.BIOMETRIC_HINT)
        assertEquals("Cancel", LockBiometricPolicy.CANCEL_BUTTON)
    }

    @Test fun stickyIgnoresCanceledOnlyWhilePaused() {
        val s = StickyAuthSession(sticky = true)
        assertFalse(s.ignoreCanceled()) // cancelled while resumed: user or system cancel completes as failure
        s.onPause()
        assertTrue(s.ignoreCanceled())
    }

    @Test fun stickyReshowsOnResumeAfterPauseWithNoRetryLimit() {
        val s = StickyAuthSession(sticky = true)
        assertFalse(s.onResume()) // a resume without a preceding pause does not stack a prompt
        repeat(10) {
            s.onPause()
            assertTrue(s.onResume())
        }
    }

    @Test fun nonStickyNeverIgnoresCancelOrReshows() {
        val s = StickyAuthSession(sticky = false)
        s.onPause()
        assertFalse(s.ignoreCanceled())
        assertFalse(s.onResume())
    }

    @Test fun stoppedSessionNeverResumesOrIgnoresCancel() {
        val s = StickyAuthSession(sticky = true)
        s.onPause()
        s.stop() // signout / lock cleared / activity detach / stopAuthentication
        assertFalse(s.onResume())
        assertFalse(s.ignoreCanceled())
        s.onPause()
        assertFalse(s.paused)
    }
}
