package com.example.chat.ui.settings.security

import com.example.chat.data.e2ee.SecureKeyValueStore
import com.example.chat.data.services.LocalLockService
import com.example.chat.data.services.LockBiometrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppLockControllerTest {
    private class Mem : SecureKeyValueStore {
        val m = mutableMapOf<String, String>()
        override suspend fun read(key: String) = m[key]
        override suspend fun write(key: String, value: String) { m[key] = value }
        override suspend fun delete(key: String) { m.remove(key) }
    }
    private var clock = 5_000_000L
    private fun make(method: String, bio: LockBiometrics? = null, unlocked: () -> Unit): Pair<AppLockController, LocalLockService> {
        val svc = LocalLockService(Mem(), bio) { clock }
        return AppLockController(svc, { method }, "r", unlocked) to svc
    }

    @Test fun correctPinUnlocksOnlyAfterFullLength() = runBlocking {
        var n = 0
        val (c, svc) = make("PIN") { n++ }
        svc.setCredential("PIN", "1234", 4)
        c.loadCapabilities()
        c.verifyPinDigit("1"); c.verifyPinDigit("2"); c.verifyPinDigit("3")
        assertEquals(0, n)
        c.verifyPinDigit("4")
        assertEquals(1, n)
    }

    @Test fun wrongPinClearsAndMessages() = runBlocking {
        var n = 0
        val (c, svc) = make("PIN") { n++ }
        svc.setCredential("PIN", "1234", 4)
        c.loadCapabilities()
        "0000".forEach { c.verifyPinDigit(it.toString()) }
        assertEquals(0, n)
        assertEquals("", c.state.value.enteredPin)
        assertEquals("Incorrect pin. Try again.", c.state.value.errorMessage)
    }

    @Test fun cooldownBlocksEvenCorrectSecret() = runBlocking {
        var n = 0
        val (c, svc) = make("Password") { n++ }
        svc.setCredential("Password", "secret1")
        repeat(5) { c.verifySecret("Password", "bad") }
        assertEquals("Too many failed attempts. Try again in 30 seconds.", c.state.value.errorMessage)
        c.verifySecret("Password", "secret1")
        assertEquals(0, n)
        clock += 31_000
        c.verifySecret("Password", "secret1")
        assertEquals(1, n)
    }

    @Test fun nativeFailsClosedWithoutAdapter() = runBlocking {
        var n = 0
        val (c, _) = make("Biometric") { n++ }
        c.startNativeMethodIfNeeded()
        assertEquals(0, n)
        assertFalse(c.state.value.busy)
        assertTrue(c.state.value.errorMessage.startsWith("Biometric authentication was cancelled"))
        val (d, _) = make("Device Credential") { n++ }
        d.startNativeMethodIfNeeded()
        assertEquals("Device authentication was cancelled or is unavailable.", d.state.value.errorMessage)
        assertEquals(0, n)
    }

    @Test fun nativeSuccessUnlocks() = runBlocking {
        var n = 0
        val bio = object : LockBiometrics {
            override suspend fun canCheckBiometrics() = true
            override suspend fun availableBiometrics() = listOf("face")
            override suspend fun isDeviceSupported() = true
            override suspend fun authenticateBiometricOnly(reason: String) = true
            override suspend fun authenticateWithDeviceCredential(reason: String) = true
        }
        val (c, _) = make("Biometric", bio) { n++ }
        c.startNativeMethodIfNeeded(); c.startNativeMethodIfNeeded()
        assertEquals(1, n)
    }

    @Test fun missingCredentialFlagged() = runBlocking {
        val (c, _) = make("Pattern") {}
        c.loadCapabilities()
        assertFalse(c.state.value.hasConfiguredCredential)
    }
}
