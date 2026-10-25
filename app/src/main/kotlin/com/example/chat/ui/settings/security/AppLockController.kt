package com.example.chat.ui.settings.security

import com.example.chat.data.services.LocalLockService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Mirror of _AppLockOverlayModalState fields. */
data class AppLockUiState(
    val enteredPin: String = "",
    val errorMessage: String = "",
    val busy: Boolean = false,
    val biometricAvailable: Boolean = false,
    val hasConfiguredCredential: Boolean = true,
    val nativePromptStarted: Boolean = false,
    val pinLength: Int = 4,
    val cooldownSeconds: Int = 0,
)

/**
 * Pure-JVM state machine behind the app lock overlay, ported line for line
 * from _AppLockOverlayModalState (app_lock_overlay.dart): capability load,
 * native biometric/device prompt, secret verification with cooldown messages,
 * PIN digit entry. The overlay calls [onUnlocked] ONLY after a successful
 * verification; there is no other path to unlock (fail closed).
 */
class AppLockController(
    private val lockService: LocalLockService,
    private val lockMethod: () -> String,
    private val reason: String,
    private val onUnlocked: () -> Unit,
) {
    private val _state = MutableStateFlow(AppLockUiState())
    val state: StateFlow<AppLockUiState> get() = _state

    private fun update(f: (AppLockUiState) -> AppLockUiState) { _state.value = f(_state.value) }

    private fun tooMany(seconds: Int) = "Too many failed attempts. Try again in $seconds seconds."

    suspend fun loadCapabilities() {
        val method = lockMethod()
        val pinLength = lockService.getPinLength()
        val biometric = lockService.canUseBiometrics()
        val cooldown = lockService.getRemainingCooldownSeconds()
        var hasCredential = true
        if (method == "PIN" || method == "Pattern" || method == "Password") {
            hasCredential = lockService.hasCredential(method)
        }
        update {
            it.copy(
                pinLength = pinLength,
                biometricAvailable = biometric,
                hasConfiguredCredential = hasCredential,
                cooldownSeconds = cooldown,
                errorMessage = if (cooldown > 0) tooMany(cooldown) else it.errorMessage,
            )
        }
    }

    suspend fun startNativeMethodIfNeeded() {
        if (_state.value.nativePromptStarted) return
        val method = lockMethod()
        if (method != "Biometric" && method != "Device Credential") return
        update { it.copy(nativePromptStarted = true) }
        runNativeAuthentication(method)
    }

    suspend fun runNativeAuthentication(method: String) {
        if (_state.value.busy) return
        update { it.copy(busy = true, errorMessage = "") }
        val success = if (method == "Device Credential") lockService.authenticateDeviceCredential(reason)
        else lockService.authenticateBiometric(reason)
        if (success) { onUnlocked(); return }
        update {
            it.copy(
                busy = false,
                errorMessage = if (method == "Device Credential")
                    "Device authentication was cancelled or is unavailable."
                else "Biometric authentication was cancelled, unavailable, or no biometric is enrolled.",
            )
        }
    }

    suspend fun verifySecret(method: String, value: String) {
        if (_state.value.busy) return
        val cooldown = lockService.getRemainingCooldownSeconds()
        if (cooldown > 0) {
            update { it.copy(cooldownSeconds = cooldown, errorMessage = tooMany(cooldown), enteredPin = "") }
            return
        }
        update { it.copy(busy = true, errorMessage = "") }
        val valid = lockService.verifyCredential(method, value)
        if (valid) { onUnlocked(); return }
        val newCooldown = lockService.getRemainingCooldownSeconds()
        update {
            it.copy(
                busy = false,
                enteredPin = "",
                cooldownSeconds = newCooldown,
                errorMessage = if (newCooldown > 0) tooMany(newCooldown) else "Incorrect ${method.lowercase()}. Try again.",
            )
        }
    }

    suspend fun verifyPinDigit(digit: String) {
        val s = _state.value
        if (s.busy || s.enteredPin.length >= s.pinLength || s.cooldownSeconds > 0) return
        val next = s.enteredPin + digit
        update { it.copy(enteredPin = next, errorMessage = "") }
        if (next.length == s.pinLength) verifySecret("PIN", next)
    }

    fun deleteDigit() {
        val s = _state.value
        if (s.busy || s.enteredPin.isEmpty()) return
        update { it.copy(enteredPin = it.enteredPin.dropLast(1)) }
    }
}
