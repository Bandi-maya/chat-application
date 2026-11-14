package com.example.chat.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperGuardDecideTest {

    private fun cleanSignals() = TamperGuard.Signals(
        signature = TamperGuard.SignatureState.MATCH,
        debuggerAttached = false,
        installerPackage = "com.android.vending",
        emulatorHits = emptyList(),
        hookArtifacts = emptyList(),
    )

    @Test
    fun cleanSignalsAreNotTampered() {
        val verdict = TamperGuard.decide(cleanSignals(), enforceEnabled = true)
        assertFalse(verdict.tampered)
        assertFalse(verdict.enforced)
    }

    @Test
    fun signatureMismatchFlagsTampering() {
        val verdict = TamperGuard.decide(
            cleanSignals().copy(signature = TamperGuard.SignatureState.MISMATCH),
            enforceEnabled = true,
        )
        assertTrue(verdict.tampered)
        assertTrue(verdict.enforced)
    }

    @Test
    fun unconfiguredOrUnavailableSignatureNeverFlags() {
        for (state in listOf(TamperGuard.SignatureState.UNCONFIGURED, TamperGuard.SignatureState.UNAVAILABLE)) {
            assertFalse(TamperGuard.decide(cleanSignals().copy(signature = state), true).tampered)
        }
    }

    @Test
    fun debuggerAloneDoesNotProveTampering() {
        assertFalse(TamperGuard.decide(cleanSignals().copy(debuggerAttached = true), false).tampered)
    }

    @Test
    fun singleEmulatorHitDoesNotFlag() {
        assertFalse(TamperGuard.decide(cleanSignals().copy(emulatorHits = listOf("hardware=goldfish")), true).tampered)
    }

    @Test
    fun emulatorHeuristicsDoNotProveTampering() {
        assertFalse(
            TamperGuard.decide(
                cleanSignals().copy(emulatorHits = listOf("hardware=goldfish", "product=sdk")),
                true,
            ).tampered,
        )
    }

    @Test
    fun rootArtifactsDoNotProveTampering() {
        assertFalse(
            TamperGuard.decide(cleanSignals().copy(hookArtifacts = listOf("/system/xbin/su")), true).tampered,
        )
    }

    @Test
    fun enforcementRequiresBothFlagAndTampering() {
        val tampered = cleanSignals().copy(signature = TamperGuard.SignatureState.MISMATCH)
        assertFalse(TamperGuard.decide(tampered, enforceEnabled = false).enforced)
        assertTrue(TamperGuard.decide(tampered, enforceEnabled = true).enforced)
        assertFalse(TamperGuard.decide(cleanSignals(), enforceEnabled = true).enforced)
    }

    @Test
    fun normalizeHexStripsSeparatorsAndCase() {
        assertEquals("abcdef0123", TamperGuard.normalizeHex("AB:CD:EF:01:23"))
        assertEquals("abcdef", TamperGuard.normalizeHex("ab-cd-ef"))
        assertEquals("", TamperGuard.normalizeHex("::--"))
    }

    @Test
    fun signatureStateComparison() {
        val hash = "aa" + "bb".repeat(31)
        assertEquals(
            TamperGuard.SignatureState.UNCONFIGURED,
            TamperGuard.signatureState("", hash),
        )
        assertEquals(
            TamperGuard.SignatureState.UNAVAILABLE,
            TamperGuard.signatureState(hash, null),
        )
        assertEquals(
            TamperGuard.SignatureState.MATCH,
            TamperGuard.signatureState(hash.uppercase(), hash),
        )
        assertEquals(
            TamperGuard.SignatureState.MATCH,
            TamperGuard.signatureState(hash.chunked(2).joinToString(":"), hash),
        )
        assertEquals(
            TamperGuard.SignatureState.MISMATCH,
            TamperGuard.signatureState(hash, "ff" + "ee".repeat(31)),
        )
    }
}
