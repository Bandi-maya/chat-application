package com.example.chat

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShortcutRouterTest {

    @After
    fun reset() {
        LauncherShortcutRouter.nanoClockForTesting = null
        LauncherShortcutRouter.consume()
    }

    private fun tapNewChat(user: String? = "u1", generation: Long = 1L) =
        LauncherShortcutRouter.onShortcutAction(LauncherShortcutRouter.ACTION_NEW_CHAT, user, generation)

    @Test
    fun `shortcut action is published with its session identity`() {
        tapNewChat(user = "u1", generation = 7L)
        val p = LauncherShortcutRouter.pending.value!!
        assertEquals(LauncherShortcutRouter.ACTION_NEW_CHAT, p.action)
        assertEquals("u1", p.accountUserId)
        assertEquals(7L, p.scopeGeneration)
        assertTrue(LauncherShortcutRouter.isFresh())
    }

    @Test
    fun `blank and missing actions are ignored`() {
        LauncherShortcutRouter.onShortcutAction(null, "u1", 1L)
        LauncherShortcutRouter.onShortcutAction("", "u1", 1L)
        LauncherShortcutRouter.onShortcutAction("   ", "u1", 1L)
        assertNull(LauncherShortcutRouter.pending.value)
    }

    @Test
    fun `repeated identical taps are distinct emissions`() {
        // MutableStateFlow conflates equal values; the sequence makes each tap a new object.
        tapNewChat(); val first = LauncherShortcutRouter.pending.value!!
        tapNewChat(); val second = LauncherShortcutRouter.pending.value!!
        assertNotSame(first, second)
        assertTrue(second.sequence > first.sequence)
    }

    @Test
    fun `consume clears the pending action`() {
        tapNewChat()
        LauncherShortcutRouter.consume()
        assertNull(LauncherShortcutRouter.pending.value)
    }

    @Test
    fun `pending action expires after the TTL on the monotonic clock`() {
        var now = 1_000_000_000L
        LauncherShortcutRouter.nanoClockForTesting = { now }
        tapNewChat()
        now += LauncherShortcutRouter.PENDING_TTL_MILLIS * 1_000_000L
        assertFalse(LauncherShortcutRouter.isFresh())
        assertTrue(LauncherShortcutRouter.clearIfExpired())
        assertNull(LauncherShortcutRouter.pending.value)
    }

    @Test
    fun `pending action inside the TTL is kept`() {
        var now = 1_000_000_000L
        LauncherShortcutRouter.nanoClockForTesting = { now }
        tapNewChat()
        now += LauncherShortcutRouter.PENDING_TTL_MILLIS * 1_000_000L - 1
        assertTrue(LauncherShortcutRouter.isFresh())
        assertFalse(LauncherShortcutRouter.clearIfExpired())
    }

    @Test
    fun `a negative clock delta fails closed and the action is dropped`() {
        var now = 1_000_000_000L
        LauncherShortcutRouter.nanoClockForTesting = { now }
        tapNewChat()
        now -= 5_000L
        assertFalse(LauncherShortcutRouter.isFresh())
        assertTrue(LauncherShortcutRouter.clearIfExpired())
        assertNull(LauncherShortcutRouter.pending.value)
    }

    @Test
    fun `tap while signed out may be used by the next sign-in`() {
        tapNewChat(user = null, generation = 3L)
        assertTrue(LauncherShortcutRouter.scopeAllows("u-any", currentScopeGeneration = 4L))
        assertTrue(LauncherShortcutRouter.scopeAllows(null, currentScopeGeneration = 3L))
    }

    @Test
    fun `tap under a login fires only for that exact login session`() {
        tapNewChat(user = "u1", generation = 10L)
        assertTrue(LauncherShortcutRouter.scopeAllows("u1", 10L))
        // Same uid, later login (A -> B -> A or A -> A'): generation moved on.
        assertFalse(LauncherShortcutRouter.scopeAllows("u1", 11L))
        // Different account entirely.
        assertFalse(LauncherShortcutRouter.scopeAllows("u2", 10L))
        assertFalse(LauncherShortcutRouter.scopeAllows(null, 10L))
    }
}
