package com.example.chat.ui.settings.security

import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.chat.ui.settings.security.SecurityFlowStep as S
import com.example.chat.ui.settings.security.LockMethodType as M
import com.example.chat.ui.settings.security.SecurityIntent as I

class SecurityFlowPlanTest {
    @Test fun enableWithExistingPinVerifiesFirst() = assertEquals(
        listOf(S.verifyCurrent, S.chooseMethod, S.setupNew, S.apply),
        SecurityFlowPlan.plan(I.enableLock, M.pin, true, M.pattern))
    @Test fun enableNoSecretSkipsVerify() = assertEquals(
        listOf(S.chooseMethod, S.setupNew, S.apply),
        SecurityFlowPlan.plan(I.enableLock, M.pin, false, M.pin))
    @Test fun changeToBiometricPreflights() = assertEquals(
        listOf(S.verifyCurrent, S.chooseMethod, S.preflightOsAuth, S.apply),
        SecurityFlowPlan.plan(I.changeMethod, M.password, true, M.biometric))
    @Test fun changeCredentialVerifiesIfConfigured() {
        assertEquals(listOf(S.verifyCurrent, S.setupNew, S.apply), SecurityFlowPlan.plan(I.changeCredential, M.pin, true))
        assertEquals(listOf(S.setupNew, S.apply), SecurityFlowPlan.plan(I.changeCredential, M.pin, false))
    }
    @Test fun disable() {
        assertEquals(listOf(S.verifyCurrent, S.apply), SecurityFlowPlan.plan(I.disableLock, M.pin, true))
        assertEquals(listOf(S.apply, S.apply), SecurityFlowPlan.plan(I.disableLock, M.pin, false))
        assertEquals(listOf(S.preflightOsAuth, S.apply), SecurityFlowPlan.plan(I.disableLock, M.biometric, false))
    }
    @Test fun keys() {
        assertEquals("PIN", SecurityFlowPlan.methodStorageKey(M.pin))
        assertEquals("", SecurityFlowPlan.methodStorageKey(M.biometric))
    }
}
