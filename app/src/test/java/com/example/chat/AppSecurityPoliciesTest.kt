package com.example.chat

import com.example.chat.data.security.AppLockPolicy
import com.example.chat.data.security.AutoBackupPolicy
import com.example.chat.data.security.KeyChangeDetector
import com.example.chat.data.security.LockedChatsPolicy
import com.example.chat.data.security.ScreenSecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSecurityPoliciesTest {

    // AppLockPolicy

    private val policy = AppLockPolicy(relockMillis = 30_000)

    @Test fun `fresh login never prompts`() {
        assertEquals(AppLockPolicy.Gate.Unlocked,
            policy.gate(enabled = true, freshLogin = true, manuallyLocked = false, backgroundedAtMillis = 0, nowMillis = 1_000_000))
    }

    @Test fun `prompts only after relock timer elapsed`() {
        assertEquals(AppLockPolicy.Gate.Unlocked,
            policy.gate(true, freshLogin = false, manuallyLocked = false, backgroundedAtMillis = 100, nowMillis = 29_000))
        assertEquals(AppLockPolicy.Gate.RequireBiometric,
            policy.gate(true, freshLogin = false, manuallyLocked = false, backgroundedAtMillis = 100, nowMillis = 31_000))
    }

    @Test fun `manual lock always prompts and disabled never does`() {
        assertEquals(AppLockPolicy.Gate.RequireBiometric,
            policy.gate(true, freshLogin = false, manuallyLocked = true, backgroundedAtMillis = 0, nowMillis = 1))
        assertEquals(AppLockPolicy.Gate.Unlocked,
            policy.gate(false, freshLogin = false, manuallyLocked = true, backgroundedAtMillis = 0, nowMillis = 9_999_999))
    }

    // LockedChatsPolicy

    private fun locked() = LockedChatsPolicy(mutableMapOf()) { "h:$it" }

    @Test fun `lock hides chat and wrong code cannot unlock`() {
        val l = locked()
        l.lock("room1", "1234")
        assertTrue(l.isLocked("room1"))
        assertEquals(listOf("room2"), l.visibleConversationIds(listOf("room1", "room2")))
        assertFalse(l.unlock("room1", "9999"))
        assertTrue(l.unlock("room1", "1234"))
        assertFalse(l.isLocked("room1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short code rejected`() { locked().lock("r", "abc") }

    // ScreenSecurityPolicy

    @Test fun `view-once forces flag secure`() {
        assertTrue(ScreenSecurityPolicy(false).flagSecureFor(screenIsViewOnce = true))
        assertFalse(ScreenSecurityPolicy(false).flagSecureFor(screenIsViewOnce = false))
        assertTrue(ScreenSecurityPolicy(true).flagSecureFor(screenIsViewOnce = false))
    }

    // KeyChangeDetector

    @Test fun `first seen then unchanged then changed`() {
        val d = KeyChangeDetector(mutableMapOf())
        assertEquals(KeyChangeDetector.Result.FirstSeen, d.observe("u1", "fp-a"))
        assertEquals(KeyChangeDetector.Result.Unchanged, d.observe("u1", "fp-a"))
        val r = d.observe("u1", "fp-b")
        assertTrue(r is KeyChangeDetector.Result.Changed)
        assertEquals("fp-a", (r as KeyChangeDetector.Result.Changed).previousFingerprint)
    }

    // AutoBackupPolicy

    @Test fun `due when never backed up or interval elapsed`() {
        assertTrue(AutoBackupPolicy.backupDue(null, 1_000))
        assertFalse(AutoBackupPolicy.backupDue(0, 86_399_000))
        assertTrue(AutoBackupPolicy.backupDue(0, 86_400_000))
        assertEquals(86_400_000, AutoBackupPolicy.nextFireMillis(0))
    }
}
