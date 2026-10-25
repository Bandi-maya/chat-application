package com.example.chat.data.services

import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MemStore : SecureKeyValueStore {
    val m = mutableMapOf<String, String>()
    override suspend fun read(key: String) = m[key]
    override suspend fun write(key: String, value: String) { m[key] = value }
    override suspend fun delete(key: String) { m.remove(key) }
}

class LocalLockServiceFullTest {
    private var clock = 1_000_000L
    private fun svc(s: MemStore, b: LockBiometrics? = null) = LocalLockService(s, b) { clock }

    @Test fun cooldownTiers() = runBlocking {
        val s = MemStore(); val l = svc(s)
        l.setCredential("pin", "1234", 4)
        repeat(4) { assertFalse(l.verifyCredential("pin", "0000")) }
        assertEquals(0, l.getRemainingCooldownSeconds())
        assertFalse(l.verifyCredential("pin", "0000")) // 5th -> 30s
        assertEquals(30, l.getRemainingCooldownSeconds())
        assertFalse(l.verifyCredential("pin", "1234")) // correct but locked out
        clock += 31_000
        assertEquals(0, l.getRemainingCooldownSeconds())
        for (i in 0 until 4) l.recordFailedAttempt() // attempts 6..9, each 30s
        assertEquals(9, l.getFailedAttempts())
        assertEquals(30, l.getRemainingCooldownSeconds())
        l.recordFailedAttempt() // 10th -> 60s
        assertEquals(60, l.getRemainingCooldownSeconds())
        clock += 61_000
        for (i in 0 until 5) l.recordFailedAttempt() // 11..15 -> 5min at 15
        assertEquals(300, l.getRemainingCooldownSeconds())
        clock += 301_000
        assertTrue(l.verifyCredential("pin", "1234"))
        assertEquals(0, l.getFailedAttempts())
    }

    @Test fun secretPhrase() = runBlocking {
        val l = svc(MemStore())
        assertFalse(l.hasSecretPhrase())
        l.setSecretPhrase("  Open   SESAME ")
        assertTrue(l.hasSecretPhrase())
        assertTrue(l.verifySecretPhrase("open sesame"))
        assertFalse(l.verifySecretPhrase("open"))
        assertFalse(l.verifySecretPhrase("   "))
        l.clearSecretPhrase()
        assertFalse(l.hasSecretPhrase())
        try { l.setSecretPhrase("  "); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun biometricsSeam() = runBlocking {
        assertFalse(svc(MemStore()).canUseBiometrics())
        assertFalse(svc(MemStore()).authenticateDeviceCredential())
        val b = object : LockBiometrics {
            override suspend fun canCheckBiometrics() = true
            override suspend fun availableBiometrics() = listOf("fingerprint")
            override suspend fun isDeviceSupported() = true
            override suspend fun authenticateBiometricOnly(reason: String) = reason.isNotEmpty()
            override suspend fun authenticateWithDeviceCredential(reason: String) = true
        }
        val l = svc(MemStore(), b)
        assertTrue(l.canUseBiometrics()); assertTrue(l.authenticateBiometric()); assertTrue(l.authenticateDeviceCredential())
    }

    @Test fun clearAllAndNormalize() = runBlocking {
        val s = MemStore(); val l = svc(s)
        l.setCredential("PIN", "123456", 6); l.setCredential("pattern", "0-1-2-3"); l.setCredential("password", "abcdef")
        l.setSecretPhrase("x")
        l.clearAll()
        assertTrue(s.m.isEmpty())
        assertEquals(4, l.getPinLength())
    }
}
