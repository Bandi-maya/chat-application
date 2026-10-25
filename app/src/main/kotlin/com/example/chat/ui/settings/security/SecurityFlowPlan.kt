package com.example.chat.ui.settings.security

/** What the user is trying to do with the App Lock credential. */
enum class SecurityIntent { enableLock, changeMethod, changeCredential, disableLock }

/** Lock method types offered by the selector. */
enum class LockMethodType { pin, pattern, password, biometric, deviceCredential }

/** Ordered flow steps; the UI runs them front to back and aborts on first failure. */
enum class SecurityFlowStep { verifyCurrent, chooseMethod, setupNew, preflightOsAuth, apply }

/** Exact port of lib/features/settings/security/security_flow_plan.dart. */
object SecurityFlowPlan {
    fun hasVerifiableSecret(method: LockMethodType): Boolean = when (method) {
        LockMethodType.pin, LockMethodType.pattern, LockMethodType.password -> true
        LockMethodType.biometric, LockMethodType.deviceCredential -> false
    }

    fun methodStorageKey(method: LockMethodType): String = when (method) {
        LockMethodType.pin -> "PIN"
        LockMethodType.pattern -> "Pattern"
        LockMethodType.password -> "Password"
        else -> ""
    }

    fun plan(
        intent: SecurityIntent,
        currentMethod: LockMethodType,
        currentSecretConfigured: Boolean,
        targetMethod: LockMethodType? = null,
    ): List<SecurityFlowStep> {
        val target = targetMethod ?: currentMethod
        return when (intent) {
            SecurityIntent.enableLock, SecurityIntent.changeMethod -> buildList {
                if (hasVerifiableSecret(currentMethod) && currentSecretConfigured) add(SecurityFlowStep.verifyCurrent)
                add(SecurityFlowStep.chooseMethod)
                if (hasVerifiableSecret(target)) add(SecurityFlowStep.setupNew)
                if (!hasVerifiableSecret(target)) add(SecurityFlowStep.preflightOsAuth)
                add(SecurityFlowStep.apply)
            }
            SecurityIntent.changeCredential -> buildList {
                if (currentSecretConfigured) add(SecurityFlowStep.verifyCurrent)
                add(SecurityFlowStep.setupNew)
                add(SecurityFlowStep.apply)
            }
            SecurityIntent.disableLock -> listOf(
                if (hasVerifiableSecret(currentMethod)) {
                    if (currentSecretConfigured) SecurityFlowStep.verifyCurrent else SecurityFlowStep.apply
                } else SecurityFlowStep.preflightOsAuth,
                SecurityFlowStep.apply,
            )
        }
    }
}
