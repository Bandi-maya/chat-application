package com.example.chat.platform

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.chat.data.services.LockBiometrics
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The activity BiometricPrompt attaches to. MainActivity registers itself in onCreate and detaches in onDestroy;
 *  detaching stops any pending authentication so a prompt never resumes against a dead activity. */
object LockPromptHost {
    @Volatile private var ref: WeakReference<FragmentActivity>? = null
    @Volatile var onDetach: (() -> Unit)? = null
    fun attach(activity: FragmentActivity) { ref = WeakReference(activity) }
    fun detach(activity: FragmentActivity) {
        if (ref?.get() === activity) {
            ref = null
            onDetach?.invoke()
        }
    }
    fun current(): FragmentActivity? = ref?.get()
}

/**
 * Android adapter for [LockBiometrics], source-faithful to local_auth 2.3.0 / local_auth_android 1.0.56 as used by
 * local_lock_service.dart (decisions live in [LockBiometricPolicy] / [StickyAuthSession], both unit-tested).
 * Fail-closed: no attached activity, hardware missing, any thrown error, lockout, cancel or unknown result is false
 * (Dart: PlatformException / any exception -> false in LocalLockService).
 */
class AndroidLockBiometrics(private val context: Context) : LockBiometrics {

    @Volatile private var generation = 0
    @Volatile private var live: LivePrompt? = null

    init { LockPromptHost.onDetach = { stopAuthentication() } }

    private class LivePrompt(val prompt: BiometricPrompt, val cont: CancellableContinuation<Boolean>, val session: StickyAuthSession)

    private fun status(authenticators: Int): Int = try {
        BiometricManager.from(context).canAuthenticate(authenticators)
    } catch (_: Throwable) {
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    private fun isDeviceSecure(): Boolean = try {
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure ?: false
    } catch (_: Throwable) {
        false
    }

    override suspend fun canCheckBiometrics(): Boolean =
        LockBiometricPolicy.canCheckBiometrics(status(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

    override suspend fun availableBiometrics(): List<String> = LockBiometricPolicy.enrolledBiometrics(
        weakSuccess = status(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS,
        strongSuccess = status(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS,
    )

    override suspend fun isDeviceSupported(): Boolean = LockBiometricPolicy.isDeviceSupported(
        isDeviceSecure = isDeviceSecure(),
        weakSuccess = status(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS,
    )

    override suspend fun authenticateBiometricOnly(reason: String): Boolean = authenticate(reason, biometricOnly = true)

    override suspend fun authenticateWithDeviceCredential(reason: String): Boolean =
        authenticate(reason, biometricOnly = false)

    /** local_auth stopAuthentication(): cancels the pending prompt and makes the awaiting call return false. */
    fun stopAuthentication() {
        generation++
        val l = live
        live = null
        if (l != null) {
            l.session.stop()
            try { l.prompt.cancelAuthentication() } catch (_: Throwable) {}
            if (l.cont.isActive) l.cont.resume(false)
        }
    }

    private suspend fun authenticate(reason: String, biometricOnly: Boolean): Boolean = try {
        withContext(Dispatchers.Main) {
            val activity = LockPromptHost.current() ?: return@withContext false // errorNoActivity
            if (!isDeviceSupported()) return@withContext false // errorNotAvailable
            if (live != null) return@withContext false // errorAlreadyInProgress (auth_in_progress)
            val credentialAvailable = LockBiometricPolicy.deviceCredentialAvailable(
                sdkInt = Build.VERSION.SDK_INT,
                isDeviceSecure = isDeviceSecure(),
                canAuthenticateCredentialSuccess = status(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS,
            )
            show(activity, reason, biometricOnly, credentialAvailable)
        }
    } catch (_: Throwable) {
        false
    }

    private suspend fun show(
        activity: FragmentActivity,
        reason: String,
        biometricOnly: Boolean,
        credentialAvailable: Boolean,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val myGeneration = generation
        val session = StickyAuthSession(sticky = true) // Dart passes stickyAuth: true
        val executor = ContextCompat.getMainExecutor(activity)
        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setDescription(reason)
            .setTitle(LockBiometricPolicy.SIGN_IN_TITLE)
            .setSubtitle(LockBiometricPolicy.BIOMETRIC_HINT)
            .setConfirmationRequired(true) // sensitiveTransaction: true
        if (LockBiometricPolicy.usesNegativeButton(biometricOnly, credentialAvailable)) {
            infoBuilder.setNegativeButtonText(LockBiometricPolicy.CANCEL_BUTTON)
        }
        infoBuilder.setAllowedAuthenticators(LockBiometricPolicy.allowedAuthenticators(biometricOnly, credentialAvailable))
        val info = infoBuilder.build()

        fun finish(result: Boolean) {
            session.stop()
            activity.lifecycle.removeObserver(lifecycleObserverHolder)
            if (live?.cont === cont) live = null
            if (cont.isActive) cont.resume(result)
        }

        lateinit var prompt: BiometricPrompt
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                finish(generation == myGeneration)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_CANCELED && session.ignoreCanceled()) return // wait for resume
                finish(false)
            }
            // onAuthenticationFailed: a non-matching attempt; the prompt stays up (Dart: empty).
        }
        prompt = BiometricPrompt(activity, executor, callback)
        live = LivePrompt(prompt, cont, session)

        lifecycleObserverHolder = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { session.onPause() }
            override fun onResume(owner: LifecycleOwner) {
                if (generation == myGeneration && session.onResume()) {
                    // Dart posts the re-authenticate to the UI queue because the prompt cannot show mid-resume.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (!session.stopped && generation == myGeneration && cont.isActive) {
                            try { prompt.authenticate(info) } catch (_: Throwable) { finish(false) }
                        }
                    }
                }
            }
        }
        activity.lifecycle.addObserver(lifecycleObserverHolder)
        cont.invokeOnCancellation { stopAuthentication() }
        try {
            prompt.authenticate(info)
        } catch (_: Throwable) {
            finish(false)
        }
    }

    private var lifecycleObserverHolder: DefaultLifecycleObserver = object : DefaultLifecycleObserver {}
}
