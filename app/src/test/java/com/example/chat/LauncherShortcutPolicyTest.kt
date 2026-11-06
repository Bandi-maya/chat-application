package com.example.chat

import com.example.chat.LauncherShortcutPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gating matrix a real A-B-A session lifecycle walks through. These are policy-level
 * proofs only; the AppRoot listener wiring still needs instrumentation (auth seam gap).
 */
class LauncherShortcutPolicyTest {

    private val newChat = LauncherShortcutRouter.ACTION_NEW_CHAT

    @Test fun `no pending action waits`() {
        assertEquals(Decision.Wait, LauncherShortcutPolicy.decide(null, fresh = true, scopeAllowed = true, authenticatedShellVisible = true, lockRequired = false))
    }

    @Test fun `fresh in-scope action on unlocked shell opens new chat`() {
        assertEquals(Decision.OpenNewChat, LauncherShortcutPolicy.decide(newChat, fresh = true, scopeAllowed = true, authenticatedShellVisible = true, lockRequired = false))
    }

    @Test fun `expired action is dropped even on the shell`() {
        assertEquals(Decision.Drop, LauncherShortcutPolicy.decide(newChat, fresh = false, scopeAllowed = true, authenticatedShellVisible = true, lockRequired = false))
    }

    @Test fun `out-of-scope session is dropped never fired`() {
        // A-B-A: tap under login A, shell reached under a different login -> drop, not open.
        assertEquals(Decision.Drop, LauncherShortcutPolicy.decide(newChat, fresh = true, scopeAllowed = false, authenticatedShellVisible = true, lockRequired = false))
    }

    @Test fun `signed-out tap waits until the shell appears`() {
        assertEquals(Decision.Wait, LauncherShortcutPolicy.decide(newChat, fresh = true, scopeAllowed = true, authenticatedShellVisible = false, lockRequired = false))
    }

    @Test fun `locked shell waits for unlock`() {
        assertEquals(Decision.Wait, LauncherShortcutPolicy.decide(newChat, fresh = true, scopeAllowed = true, authenticatedShellVisible = true, lockRequired = true))
    }

    @Test fun `expiry wins over every other state`() {
        assertEquals(Decision.Drop, LauncherShortcutPolicy.decide(newChat, fresh = false, scopeAllowed = false, authenticatedShellVisible = false, lockRequired = true))
    }

    @Test fun `scope mismatch wins over an unlocked shell`() {
        assertEquals(Decision.Drop, LauncherShortcutPolicy.decide(newChat, fresh = true, scopeAllowed = false, authenticatedShellVisible = true, lockRequired = false))
    }
}
