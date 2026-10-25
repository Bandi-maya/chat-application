package com.example.chat.ui.settings.security

import com.example.chat.data.services.LocalLockService

/** Binds the setup flow and selector sheet to the real LocalLockService. */
class LockSetupServiceAdapter(private val lock: LocalLockService) : LockSetupService, BiometricAvailability {
    override suspend fun hasCredential(method: String) = lock.hasCredential(method)
    override suspend fun verifyCredential(method: String, secret: String) = lock.verifyCredential(method, secret)
    override suspend fun setCredential(method: String, secret: String, pinLength: Int?) = lock.setCredential(method, secret, pinLength)
    override suspend fun authenticateBiometric(reason: String) = lock.authenticateBiometric(reason)
    override suspend fun authenticateDeviceCredential(reason: String) = lock.authenticateDeviceCredential(reason)
    override suspend fun canUseBiometrics() = lock.canUseBiometrics()
}
