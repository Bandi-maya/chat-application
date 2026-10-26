package com.example.chat.platform

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Pure decisions copied from local_auth_android 1.0.56 (the version pinned by the Dart app's pubspec.lock, under
 * local_auth 2.3.0): LocalAuthPlugin.java and AuthenticationHelper.java. No Android calls, so it is unit-testable.
 */
object LockBiometricPolicy {
    // AndroidAuthMessages defaults (auth_messages_android.dart); Dart's lock service passes no AndroidAuthMessages.
    const val SIGN_IN_TITLE = "Authentication required"
    const val BIOMETRIC_HINT = "Verify identity"
    const val CANCEL_BUTTON = "Cancel"

    /** AuthenticationHelper constructor: WEAK | STRONG, plus DEVICE_CREDENTIAL when credentials are allowed.
     *  (BIOMETRIC_WEAK already contains the BIOMETRIC_STRONG bits, so WEAK | STRONG == WEAK.) */
    fun allowedAuthenticators(biometricOnly: Boolean, deviceCredentialAvailable: Boolean): Int {
        var a = BIOMETRIC_WEAK or BIOMETRIC_STRONG
        if (allowCredentials(biometricOnly, deviceCredentialAvailable)) a = a or DEVICE_CREDENTIAL
        return a
    }

    /** LocalAuthPlugin.authenticate: allowCredentials = !biometricOnly && canAuthenticateWithDeviceCredential(). */
    fun allowCredentials(biometricOnly: Boolean, deviceCredentialAvailable: Boolean): Boolean =
        !biometricOnly && deviceCredentialAvailable

    /** The prompt carries a negative (Cancel) button exactly when credentials are not allowed. */
    fun usesNegativeButton(biometricOnly: Boolean, deviceCredentialAvailable: Boolean): Boolean =
        !allowCredentials(biometricOnly, deviceCredentialAvailable)

    /** canAuthenticateWithDeviceCredential: before API 30 BiometricManager cannot check DEVICE_CREDENTIAL alone,
     *  so the plugin uses KeyguardManager.isDeviceSecure(); from API 30 it uses canAuthenticate(DEVICE_CREDENTIAL). */
    fun deviceCredentialAvailable(sdkInt: Int, isDeviceSecure: Boolean, canAuthenticateCredentialSuccess: Boolean): Boolean =
        if (sdkInt < 30) isDeviceSecure else canAuthenticateCredentialSuccess

    /** LocalAuthPlugin.isDeviceSupported: isDeviceSecure() || canAuthenticate(BIOMETRIC_WEAK) == SUCCESS. */
    fun isDeviceSupported(isDeviceSecure: Boolean, weakSuccess: Boolean): Boolean = isDeviceSecure || weakSuccess

    /** deviceCanSupportBiometrics: canAuthenticate(BIOMETRIC_WEAK) != BIOMETRIC_ERROR_NO_HARDWARE. */
    fun canCheckBiometrics(weakStatusIsNoHardware: Boolean): Boolean = !weakStatusIsNoHardware

    /** getEnrolledBiometrics order: weak first, then strong. */
    fun enrolledBiometrics(weakSuccess: Boolean, strongSuccess: Boolean): List<String> =
        buildList { if (weakSuccess) add("weak"); if (strongSuccess) add("strong") }
}

/**
 * The sticky-auth state of AuthenticationHelper (isAuthSticky + activityPaused + stop()).
 *  - onPause: sticky marks the activity paused (BiometricPrompt reports ERROR_CANCELED when paused).
 *  - ERROR_CANCELED is ignored only while sticky AND paused; otherwise the attempt completes with failure.
 *  - onResume (sticky): the prompt is shown again. Dart has no retry count; it repeats on every resume until the
 *    attempt completes or is stopped. Here the re-show happens on a resume that follows a pause.
 *  - After [stop] (completion, stopAuthentication, signout, lock cleared, activity detach) nothing resumes.
 */
class StickyAuthSession(private val sticky: Boolean) {
    var paused: Boolean = false
        private set
    var stopped: Boolean = false
        private set

    fun onPause() { if (sticky && !stopped) paused = true }

    /** True when the prompt should be shown again. */
    fun onResume(): Boolean {
        if (!sticky || stopped) return false
        val wasPaused = paused
        paused = false
        return wasPaused
    }

    /** True when ERROR_CANCELED must be ignored (wait for resume) instead of completing as failure. */
    fun ignoreCanceled(): Boolean = sticky && paused && !stopped

    fun stop() { stopped = true; paused = false }
}
