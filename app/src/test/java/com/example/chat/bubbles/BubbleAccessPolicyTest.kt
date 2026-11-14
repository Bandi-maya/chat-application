package com.example.chat.bubbles

import org.junit.Assert.*
import org.junit.Test

class BubbleAccessPolicyTest {
    private fun allowed(enabled: Boolean = true, auth: Boolean = true, scope: Boolean = true,
        account: Boolean = true, deviceLock: Boolean = false, appLock: Boolean = false,
        protectedChat: Boolean = false, exists: Boolean = true) = BubbleAccessPolicy.allowed(
            enabled, auth, scope, account, deviceLock, appLock, protectedChat, exists)
    @Test fun onlyExplicitSafeCurrentChatPasses() { assertTrue(allowed()) }
    @Test fun defaultOffRejects() { assertFalse(allowed(enabled = false)) }
    @Test fun logoutRejects() { assertFalse(allowed(auth = false)) }
    @Test fun sameUserReloginStillRejectsOldScope() { assertFalse(allowed(scope = false)) }
    @Test fun wrongAccountRejectsEvenMatchingRoomId() { assertFalse(allowed(account = false)) }
    @Test fun deviceLockRejects() { assertFalse(allowed(deviceLock = true)) }
    @Test fun appLockRejects() { assertFalse(allowed(appLock = true)) }
    @Test fun lockedOrHiddenChatRejects() { assertFalse(allowed(protectedChat = true)) }
    @Test fun deletedOrMissingChatRejects() { assertFalse(allowed(exists = false)) }
}
