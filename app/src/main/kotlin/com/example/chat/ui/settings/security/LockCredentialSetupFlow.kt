package com.example.chat.ui.settings.security

/** Narrow service seam over LocalLockService (hasCredential/verifyCredential/setCredential/authenticate*). */
interface LockSetupService {
    suspend fun hasCredential(method: String): Boolean
    suspend fun verifyCredential(method: String, secret: String): Boolean
    suspend fun setCredential(method: String, secret: String, pinLength: Int?)
    suspend fun authenticateBiometric(reason: String): Boolean
    suspend fun authenticateDeviceCredential(reason: String): Boolean
}

enum class SetupStep { enterCurrent, enterNew, confirmNew }

/** What the UI must do after a flow action (focus/clear/reset/close are view concerns). */
enum class SetupOutcome { none, clearPin, clearText, resetPad, saved }

/**
 * Port of the logic in _LockCredentialSetupModalState (lock_credential_setup_modal.dart).
 * Pure state machine: every error string, validation and step transition matches the Dart source.
 */
class LockCredentialSetupFlow(
    val method: String,
    val pinLength: Int,
    private val service: LockSetupService,
    private val replaceExisting: Boolean = false,
) {
    var step: SetupStep = SetupStep.enterNew; private set
    var hasExistingSecret = false; private set
    var error = ""; private set
    var busy = false; private set
    var firstPatternDrawn: String? = null; private set
    var confirmPatternDrawn: String? = null; private set
    private var newSecret = ""

    val isPin get() = method == "PIN"
    val isPattern get() = method == "Pattern"
    val isPassword get() = method == "Password"
    val isBiometric get() = method == "Biometric"
    val isDeviceCredential get() = method == "Device Credential"

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun changed() = listeners.forEach { it() }

    suspend fun init() {
        val hasCred = service.hasCredential(method)
        hasExistingSecret = hasCred && !replaceExisting
        step = if (hasExistingSecret) SetupStep.enterCurrent else SetupStep.enterNew
        changed()
    }

    fun setPatternDrawnClear(isConfirm: Boolean) {
        // onPatternReset callback of the pad
        if (isConfirm) confirmPatternDrawn = null else firstPatternDrawn = null
        changed()
    }

    fun onPatternReset() = setPatternDrawnClear(step == SetupStep.confirmNew)

    /** PIN and Password share Dart's _handleContinue. [input] is the joined PIN or the raw password field. */
    suspend fun handleContinue(input: String): SetupOutcome {
        error = ""; changed()
        if (isPin) {
            val pin = input
            if (pin.length != pinLength) { error = "Enter all $pinLength digits."; changed(); return SetupOutcome.none }
            when (step) {
                SetupStep.enterCurrent -> {
                    busy = true; changed()
                    val ok = service.verifyCredential("PIN", pin)
                    busy = false; changed()
                    return if (ok) {
                        step = SetupStep.enterNew; error = ""; changed(); SetupOutcome.clearPin
                    } else {
                        error = "Current PIN is incorrect."; changed(); SetupOutcome.clearPin
                    }
                }
                SetupStep.enterNew -> {
                    if (Regex("^(\\d)\\1+$").containsMatchIn(pin)) {
                        error = "Weak PIN. Avoid repeating digits like 0000."; changed(); return SetupOutcome.none
                    }
                    newSecret = pin
                    step = SetupStep.confirmNew; error = ""; changed()
                    return SetupOutcome.clearPin
                }
                SetupStep.confirmNew -> {
                    if (pin != newSecret) {
                        error = "PINs do not match. Try entering again."; changed(); return SetupOutcome.clearPin
                    }
                    return saveFinalCredential(pin)
                }
            }
        } else if (isPassword) {
            val pass = input.trim()
            if (pass.isEmpty()) { error = "Please enter a password."; changed(); return SetupOutcome.none }
            when (step) {
                SetupStep.enterCurrent -> {
                    busy = true; changed()
                    val ok = service.verifyCredential("Password", pass)
                    busy = false; changed()
                    return if (ok) {
                        step = SetupStep.enterNew; error = ""; changed(); SetupOutcome.clearText
                    } else {
                        error = "Current password is incorrect."; changed(); SetupOutcome.none
                    }
                }
                SetupStep.enterNew -> {
                    if (pass.length < 6) { error = "Password must contain at least 6 characters."; changed(); return SetupOutcome.none }
                    newSecret = pass
                    step = SetupStep.confirmNew; error = ""; changed()
                    return SetupOutcome.clearText
                }
                SetupStep.confirmNew -> {
                    if (pass != newSecret) { error = "Passwords do not match. Try again."; changed(); return SetupOutcome.none }
                    return saveFinalCredential(pass)
                }
            }
        }
        return SetupOutcome.none
    }

    private suspend fun saveFinalCredential(secret: String): SetupOutcome {
        busy = true; error = ""; changed()
        return try {
            service.setCredential(method, secret, if (isPin) pinLength else null)
            SetupOutcome.saved
        } catch (e: Exception) {
            busy = false
            error = (e.message ?: e.toString()).replaceFirst("Invalid argument(s): ", "")
            changed()
            SetupOutcome.none
        }
    }

    fun onPatternComplete(pattern: String) {
        val nodes = pattern.split('-').filter { it.isNotEmpty() }
        if (nodes.size < 4) { error = "Connect at least 4 dots."; changed(); return }
        error = ""
        when (step) {
            SetupStep.enterCurrent, SetupStep.enterNew -> firstPatternDrawn = pattern
            SetupStep.confirmNew -> confirmPatternDrawn = pattern
        }
        changed()
    }

    val canContinuePattern: Boolean
        get() = (if (step == SetupStep.confirmNew) confirmPatternDrawn != null else firstPatternDrawn != null) && !busy

    suspend fun handlePatternContinue(): SetupOutcome {
        val pattern = if (step == SetupStep.confirmNew) confirmPatternDrawn else firstPatternDrawn
        if (pattern == null) {
            error = if (step == SetupStep.confirmNew) "Draw the confirmation pattern first." else "Draw your pattern first."
            changed(); return SetupOutcome.none
        }
        when (step) {
            SetupStep.enterCurrent -> {
                busy = true; changed()
                val ok = service.verifyCredential("Pattern", pattern)
                busy = false
                if (ok) { firstPatternDrawn = null; step = SetupStep.enterNew; error = "" }
                else { firstPatternDrawn = null; error = "Current pattern is incorrect." }
                changed(); return SetupOutcome.resetPad
            }
            SetupStep.enterNew -> {
                newSecret = pattern
                firstPatternDrawn = null; confirmPatternDrawn = null
                step = SetupStep.confirmNew; error = ""; changed()
                return SetupOutcome.resetPad
            }
            SetupStep.confirmNew -> {
                if (pattern != newSecret) {
                    confirmPatternDrawn = null
                    error = "Patterns do not match. Draw again to confirm."; changed()
                    return SetupOutcome.resetPad
                }
                return saveFinalCredential(newSecret)
            }
        }
    }

    suspend fun verifyNativeMethod(): SetupOutcome {
        if (busy) return SetupOutcome.none
        busy = true; error = ""; changed()
        val success = if (isBiometric) service.authenticateBiometric("Verify your biometric to enable Chaty Lock")
        else service.authenticateDeviceCredential("Verify your device screen lock to enable Chaty Lock")
        if (success) return SetupOutcome.saved
        busy = false
        error = if (isBiometric) "Biometric verification was cancelled or unavailable."
        else "Device credential verification was cancelled or unavailable."
        changed()
        return SetupOutcome.none
    }

    fun stepTitle(): String = when (step) {
        SetupStep.enterCurrent -> "Enter Current $method"
        SetupStep.enterNew -> if (hasExistingSecret) "Enter New $method" else "Create $method"
        SetupStep.confirmNew -> "Confirm New $method"
    }

    fun stepSubtitle(): String = when (step) {
        SetupStep.enterCurrent -> "Please authenticate with your existing $method to continue."
        SetupStep.enterNew -> when {
            isPin -> "Choose a $pinLength-digit PIN code."
            isPattern -> "Draw an unlock pattern connecting at least 4 dots."
            isPassword -> "Enter a password (min 6 characters)."
            else -> "Choose your credential."
        }
        SetupStep.confirmNew -> "Re-enter the same $method to ensure accuracy."
    }
}
