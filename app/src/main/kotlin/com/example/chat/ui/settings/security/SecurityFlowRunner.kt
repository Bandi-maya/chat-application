package com.example.chat.ui.settings.security

/**
 * Pure port of `_SecurityCenterScreenState._runFlow` / `_verifyCurrent` / method mapping from
 * lib/features/settings/security/security_center_screen.dart. All UI and platform work goes through
 * [SecurityFlowHost], so the decision logic is JVM-testable.
 */
interface SecurityFlowHost {
    /** Live (latest) values from preferences.security. */
    fun currentLockMethod(): String
    fun isAppLockEnabled(): Boolean
    suspend fun hasCredential(storageKey: String): Boolean
    suspend fun authenticateBiometric(reason: String): Boolean
    suspend fun authenticateDeviceCredential(reason: String): Boolean
    /** AppLockOverlayModal.show(title: "Confirm it's you", reason: "Verify your current lock to continue"). true = unlocked. */
    suspend fun showVerifyOverlay(): Boolean
    /** LockMethodSelectorSheet.show; null = dismissed. */
    suspend fun pickMethod(current: LockMethodType): LockMethodType?
    /** LockCredentialSetupModal.show(replaceExisting = true). */
    suspend fun setupCredential(storageKey: String, pinLength: Int): Boolean
    suspend fun reloadCapabilities()
    fun toast(message: String)
    /** Dart `mounted` guard. */
    fun isActive(): Boolean
    /** preferencesController.updateSecurity(latest.copyWith(...), logTitle). Null fields keep latest. */
    fun applySecurity(lockMethod: String?, appLockEnabled: Boolean?, logTitle: String)
}

object SecurityFlowRunner {
    fun methodTypeOf(stored: String): LockMethodType = when (stored) {
        "PIN" -> LockMethodType.pin
        "Pattern" -> LockMethodType.pattern
        "Password" -> LockMethodType.password
        "Biometric" -> LockMethodType.biometric
        "Device Credential" -> LockMethodType.deviceCredential
        else -> LockMethodType.pin
    }

    fun storageKeyOf(type: LockMethodType): String = when (type) {
        LockMethodType.pin -> "PIN"
        LockMethodType.pattern -> "Pattern"
        LockMethodType.password -> "Password"
        LockMethodType.biometric -> "Biometric"
        LockMethodType.deviceCredential -> "Device Credential"
    }

    suspend fun verifyCurrent(host: SecurityFlowHost, method: LockMethodType): Boolean {
        if (!SecurityFlowPlan.hasVerifiableSecret(method)) {
            val reason = "Confirm it is you to continue"
            val ok = if (method == LockMethodType.biometric) host.authenticateBiometric(reason)
            else host.authenticateDeviceCredential(reason)
            if (!ok && host.isActive()) host.toast("Authentication failed or was cancelled.")
            return ok
        }
        val unlocked = host.showVerifyOverlay()
        if (!unlocked) {
            if (host.isActive()) host.toast("Verification cancelled.")
            return false
        }
        return true
    }

    /** Returns true when the intent was applied. */
    suspend fun run(
        host: SecurityFlowHost,
        intent: SecurityIntent,
        target: LockMethodType? = null,
        setupPinLength: Int? = null,
        pinLength: Int = 4,
    ): Boolean {
        val current = methodTypeOf(host.currentLockMethod())
        val currentHasSecret = if (SecurityFlowPlan.hasVerifiableSecret(current)) host.hasCredential(storageKeyOf(current)) else false
        var chosen = target ?: current
        var verifiedCurrent = false

        val requiresCurrentProof = host.isAppLockEnabled() &&
            (intent == SecurityIntent.changeMethod || intent == SecurityIntent.changeCredential || intent == SecurityIntent.disableLock)
        if (requiresCurrentProof && (!SecurityFlowPlan.hasVerifiableSecret(current) || currentHasSecret)) {
            val valid = verifyCurrent(host, current)
            if (!valid || !host.isActive()) return false
            verifiedCurrent = true
        }

        if (intent == SecurityIntent.enableLock || (intent == SecurityIntent.changeMethod && target == null)) {
            val picked = host.pickMethod(current)
            if (picked == null || !host.isActive()) return false
            chosen = picked
        }

        val chosenHasSecret = if (SecurityFlowPlan.hasVerifiableSecret(chosen)) host.hasCredential(storageKeyOf(chosen)) else false
        val sameAsCurrent = chosen == current
        val needsCredentialSetup = SecurityFlowPlan.hasVerifiableSecret(chosen) &&
            (intent == SecurityIntent.changeCredential || !(sameAsCurrent && verifiedCurrent && chosenHasSecret))

        if (intent != SecurityIntent.disableLock && SecurityFlowPlan.hasVerifiableSecret(chosen) && needsCredentialSetup) {
            val configured = host.setupCredential(storageKeyOf(chosen), setupPinLength ?: pinLength)
            if (!configured || !host.isActive()) return false
            host.reloadCapabilities()
        }

        if (intent != SecurityIntent.disableLock && !SecurityFlowPlan.hasVerifiableSecret(chosen) && !(sameAsCurrent && verifiedCurrent)) {
            val ok = if (chosen == LockMethodType.biometric) host.authenticateBiometric("Confirm biometric unlock works for Chaty")
            else host.authenticateDeviceCredential("Confirm your device lock works for Chaty")
            if (!ok) {
                if (host.isActive()) {
                    host.toast(
                        if (chosen == LockMethodType.biometric)
                            "Biometric unavailable or not confirmed \u2014 choose another lock type."
                        else
                            "Device lock not confirmed \u2014 set a device screen lock first or choose another type.",
                    )
                }
                return false
            }
        }

        if (!host.isActive()) return false
        when (intent) {
            SecurityIntent.enableLock ->
                host.applySecurity(storageKeyOf(chosen), true, "Enable Chaty Lock (${storageKeyOf(chosen)})")
            SecurityIntent.changeMethod ->
                host.applySecurity(storageKeyOf(chosen), true, "Lock Method")
            SecurityIntent.changeCredential ->
                if (chosen != current) host.applySecurity(storageKeyOf(chosen), null, "Lock Authentication Method")
            SecurityIntent.disableLock ->
                host.applySecurity(null, false, "Disable App Lock")
        }
        return true
    }
}
