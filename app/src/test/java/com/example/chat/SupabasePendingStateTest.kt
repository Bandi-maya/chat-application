package com.example.chat

import com.example.chat.data.auth.SupabaseAuthClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Nonce guard for chaty:// deep links (login-callback / reset-password):
 * creation shape, one-time consume, unknown rejection, and TTL expiry.
 * The store is a private companion field; reflection is used only to simulate
 * expiry without waiting 10 minutes.
 */
class SupabasePendingStateTest {

    @Suppress("UNCHECKED_CAST")
    private fun store(): ConcurrentHashMap<String, Long> {
        val field = SupabaseAuthClient.Companion::class.java.getDeclaredField("pendingAuthStates")
        field.isAccessible = true
        return field.get(SupabaseAuthClient.Companion) as ConcurrentHashMap<String, Long>
    }

    @Test
    fun registerReturnsUnique32HexNonces() {
        val a = SupabaseAuthClient.registerPendingState()
        val b = SupabaseAuthClient.registerPendingState()
        assertNotEquals(a, b)
        assertTrue("nonce is 32 lowercase hex chars", a.matches(Regex("[0-9a-f]{32}")))
        assertTrue("registered nonce is pending", store().containsKey(a))
    }

    @Test
    fun consumeAcceptsOnceThenRejectsReplay() {
        val nonce = SupabaseAuthClient.registerPendingState()
        assertTrue("first consume succeeds", SupabaseAuthClient.consumePendingState(nonce))
        assertFalse("replay of the same nonce fails", SupabaseAuthClient.consumePendingState(nonce))
    }

    @Test
    fun consumeRejectsUnknownNonce() {
        assertFalse(SupabaseAuthClient.consumePendingState("0".repeat(32)))
    }

    @Test
    fun consumeRejectsExpiredNonce() {
        val nonce = SupabaseAuthClient.registerPendingState()
        store()[nonce] = System.currentTimeMillis() - 1
        assertFalse("expired nonce fails", SupabaseAuthClient.consumePendingState(nonce))
        assertFalse("expired nonce is consumed, not retryable", SupabaseAuthClient.consumePendingState(nonce))
    }
}
