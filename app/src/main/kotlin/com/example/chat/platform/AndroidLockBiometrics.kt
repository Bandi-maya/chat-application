package com.example.chat.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.example.chat.data.services.LockBiometrics
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The activity BiometricPrompt attaches to (MainActivity registers itself in onCreate/onDestroy). */
object LockPromptHost {
    @Volatile private var ref: WeakReference<FragmentActivity>? = null
    fun attach(activity: FragmentActivity) { ref = WeakReference(activity) }
    fun detach(activity: FragmentActivity) { if (ref?.get() === activity) ref = null }
    fun current(): FragmentActivity? = ref?.get()
}

/**
 * Android adapter for [LockBiometrics] (Dart: local_auth LocalAuthentication as used by local_lock_service.dart).
 *
 * Mapping, kept fail-closed like the Dart try/catch around PlatformException (every path returns false/empty on
 * any error, missing activity, missing hardware, lockout, user cancel or unknown result):
 *  - canCheckBiometrics: BiometricManager.canAuthenticate(BIOMETRIC_WEAK) == SUCCESS
 *  - getAvailableBiometrics: "strong" when BIOMETRIC_STRONG succeeds, "weak" when BIOMETRIC_WEAK succeeds
 *  - isDeviceSupported: canAuthenticate(BIOMETRIC_WEAK | DEVICE_CREDENTIAL) == SUCCESS
 *  - authenticate(biometricOnly: true): prompt allowing BIOMETRIC_WEAK only, with a Cancel button
 *  - authenticate(biometricOnly: false): prompt allowing BIOMETRIC_WEAK | DEVICE_CREDENTIAL (no negative button)
 *  - sensitiveTransaction: explicit confirmation required
 *  - stickyAuth: a system cancellation (the app went to the background mid-prompt) re-shows the prompt when the
 *    activity resumes, up to [MAX_STICKY_RETRIES] times; user cancel, lockout or any other error ends with false.
 */
class AndroidLockBiometrics(private val context: Context) : LockBiometrics {

    private fun status(authenticators: Int): Int = try {
        BiometricManager.from(context).canAuthenticate(authenticators)
    } catch (_: Throwable) {
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    override suspend fun canCheckBiometrics(): Boolean =
        status(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun availableBiometrics(): List<String> {
        val out = mutableListOf<String>()
        if (status(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) out.add("strong")
        if (status(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) out.add("weak")
        return out
    }

    override suspend fun isDeviceSupported(): Boolean =
        status(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticateBiometricOnly(reason: String): Boolean =
        prompt(reason, biometricOnly = true)

    override suspend fun authenticateWithDeviceCredential(reason: String): Boolean =
        prompt(reason, biometricOnly = false)

    private suspend fun prompt(reason: String, biometricOnly: Boolean): Boolean = try {
        withContext(Dispatchers.Main) {
            var attempts = 0
            var result = false
            while (true) {
                val activity = LockPromptHost.current() ?: return@withContext false
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@withContext false
                val outcome = showOnce(activity, reason, biometricOnly)
                if (outcome == Outcome.SUCCESS) { result = true; break }
                if (outcome == Outcome.SYSTEM_CANCELED && attempts < MAX_STICKY_RETRIES) {
                    attempts++
                    awaitResumed(activity)
                    continue
                }
                break
            }
            result
        }
    } catch (_: Throwable) {
        false
    }

    private enum class Outcome { SUCCESS, FAILED, SYSTEM_CANCELED }

    private suspend fun awaitResumed(activity: FragmentActivity) {
        suspendCancellableCoroutine<Unit> { cont ->
            val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                    activity.lifecycle.removeObserver(this)
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            activity.lifecycle.addObserver(observer)
            cont.invokeOnCancellation { activity.lifecycle.removeObserver(observer) }
        }
    }

    private suspend fun showOnce(activity: FragmentActivity, reason: String, biometricOnly: Boolean): Outcome =
        suspendCancellableCoroutine { cont: CancellableContinuation<Outcome> ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Outcome.SUCCESS)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val o = if (errorCode == BiometricPrompt.ERROR_CANCELED) Outcome.SYSTEM_CANCELED else Outcome.FAILED
                    if (cont.isActive) cont.resume(o)
                }
                // onAuthenticationFailed: one non-matching attempt; the prompt stays up for another try.
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setConfirmationRequired(true)
            if (biometricOnly) {
                builder.setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText("Cancel")
            } else {
                builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            }
            try {
                prompt.authenticate(builder.build())
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(Outcome.FAILED)
            }
            cont.invokeOnCancellation { try { prompt.cancelAuthentication() } catch (_: Throwable) {} }
        }

    private companion object {
        const val MAX_STICKY_RETRIES = 3
    }
}
