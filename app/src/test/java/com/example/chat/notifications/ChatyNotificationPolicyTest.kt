package com.example.chat.notifications

import org.junit.Assert.*
import org.junit.Test

class ChatyNotificationPolicyTest {
    private fun gate(global: Boolean = true, signedIn: Boolean = true, me: Boolean = false, muted: Boolean = false,
                     fg: Boolean = false, open: Boolean = false, sys: Boolean = false) =
        ChatyNotificationPolicy.MessageGate(global, signedIn, me, muted, fg, open, sys)

    @Test fun postRules() {
        assertTrue(ChatyNotificationPolicy.shouldPostMessage(gate()))
        assertTrue(ChatyNotificationPolicy.shouldPostMessage(gate(fg = true, open = false)))
        assertFalse(ChatyNotificationPolicy.shouldPostMessage(gate(fg = true, open = true)))
        assertTrue(ChatyNotificationPolicy.shouldPostMessage(gate(fg = false, open = true)))
        assertFalse(ChatyNotificationPolicy.shouldPostMessage(gate(global = false)))
        assertFalse(ChatyNotificationPolicy.shouldPostMessage(gate(signedIn = false)))
        assertFalse(ChatyNotificationPolicy.shouldPostMessage(gate(me = true)))
        assertFalse(ChatyNotificationPolicy.shouldPostMessage(gate(muted = true)))
        assertFalse(ChatyNotificationPolicy.shouldPostMessage(gate(sys = true)))
    }

    @Test fun protectedChatsHideContentAndActions() {
        assertEquals(ChatyNotificationPolicy.Detail.GENERIC, ChatyNotificationPolicy.detail(true, false))
        assertEquals(ChatyNotificationPolicy.Detail.GENERIC, ChatyNotificationPolicy.detail(true, true))
        assertEquals(ChatyNotificationPolicy.Detail.NO_REPLY, ChatyNotificationPolicy.detail(false, true))
        assertEquals(ChatyNotificationPolicy.Detail.FULL, ChatyNotificationPolicy.detail(false, false))
    }

    @Test fun replyText() {
        assertEquals("hi", ChatyNotificationPolicy.cleanReply("  hi "))
        assertNull(ChatyNotificationPolicy.cleanReply("   "))
        assertNull(ChatyNotificationPolicy.cleanReply(null))
        assertNull(ChatyNotificationPolicy.cleanReply("x".repeat(4001)))
    }

    @Test fun registryBindsExactScopeReference_ABA() {
        val reg = ChatyActionRegistry<Any>()
        val a1 = Any(); val b = Any(); val a2 = Any() // A, then B, then A signs in again (new scope object)
        val n = reg.register(a1, "mute", callId = "c1")
        val e = reg.resolve(n)!!
        assertSame(a1, e.scope); assertNotSame(a2, e.scope); assertNotSame(b, e.scope)
        reg.clear() // auth transition
        assertNull(reg.resolve(n))
    }

    @Test fun nonceIsStableReusedAndIsolatedPerKey() {
        // Deterministic supplier: registry stability/reuse/isolation must not depend on nonce
        // CONTENT. Known values deliberately include one containing "c1" - an accidental
        // substring of a random hex value is not a leak, so it is not an opacity property.
        val supplied = listOf("c1".padEnd(32, '0'), "ab".repeat(16), "ff".repeat(16))
        var draws = 0
        val reg = ChatyActionRegistry<Any>(nonce = { supplied[draws++] })
        val s = Any()
        val n1 = reg.register(s, "mute", callId = "c1")
        assertEquals("c1".padEnd(32, '0'), n1)
        assertEquals(n1, reg.register(s, "mute", callId = "c1")) // stable reuse draws nothing new
        assertEquals(1, draws)
        val n2 = reg.register(s, "hangup", callId = "c1")
        assertNotEquals(n1, n2)
        val n3 = reg.register(Any(), "mute", callId = "c1")
        assertNotEquals(n1, n3); assertNotEquals(n2, n3)
        assertEquals(3, draws)
        assertSame(s, reg.resolve(n1)?.scope)
        assertNull(reg.resolve("nope")); assertNull(reg.resolve(null))
    }

    @Test fun productionRandomNonceHasContractFormat() {
        // Grounded contract only: 32 lowercase hex chars, independent draws differ. A format and
        // collision check says nothing about entropy or opacity and is not claimed to.
        val n1 = ChatyActionRegistry.randomNonce()
        val n2 = ChatyActionRegistry.randomNonce()
        assertTrue(Regex("[0-9a-f]{32}").matches(n1))
        assertTrue(Regex("[0-9a-f]{32}").matches(n2))
        assertNotEquals(n1, n2)
    }

    @Test fun clearCallDropsOnlyCallEntries() {
        val reg = ChatyActionRegistry<Any>(); val s = Any()
        val msg = reg.register(s, "reply", conversationId = "r1")
        val call = reg.register(s, "mute", callId = "c1")
        reg.clearCalls()
        assertNull(reg.resolve(call)); assertNotNull(reg.resolve(msg))
    }

    @Test fun registryIsBounded() {
        val reg = ChatyActionRegistry<Any>(maxEntries = 3); val s = Any()
        repeat(10) { reg.register(s, "reply", conversationId = "r$it") }
        assertEquals(3, reg.size)
    }

    @Test fun styleIsSanitized() {
        val st = ChatyNotificationStyle.sanitized(0x00123456, 9)
        assertEquals(0xFF123456.toInt(), st.accentArgb)
        assertEquals(ChatyNotificationStyle.ICON_CHAT, st.iconVariant)
    }

    private val fmt = { t: String -> t }

    @Test fun vaultLockedChatRevealsNothingAndOffersNoActions() {
        val plan = ChatyNotificationPolicy.plan(ChatyNotificationPolicy.Detail.GENERIC, "Secret Person", "secret text", fmt)
        assertEquals("Chaty", plan.title); assertEquals("New message", plan.body)
        assertFalse(plan.offerReply); assertFalse(plan.offerDismiss)
    }

    @Test fun appLockOnShowsContentButNoReply() {
        val plan = ChatyNotificationPolicy.plan(ChatyNotificationPolicy.Detail.NO_REPLY, "Ann", "hello", fmt)
        assertEquals("Ann", plan.title); assertEquals("hello", plan.body)
        assertFalse(plan.offerReply); assertTrue(plan.offerDismiss)
    }

    @Test fun fullDetailOffersReplyAndFormatterIsApplied() {
        val plan = ChatyNotificationPolicy.plan(ChatyNotificationPolicy.Detail.FULL, null, "hello") { "New message" }
        assertEquals("Chaty", plan.title); assertEquals("New message", plan.body)
        assertTrue(plan.offerReply)
    }

    @Test fun replyRefusedWhenLockedOrProtected() {
        assertTrue(ChatyNotificationPolicy.mayReply(false, false, false))
        assertFalse(ChatyNotificationPolicy.mayReply(true, false, false))
        assertFalse(ChatyNotificationPolicy.mayReply(false, true, false))
        assertFalse(ChatyNotificationPolicy.mayReply(false, false, true))
    }

    @Test fun callActionsOnlyForSameLiveCall() {
        assertTrue(ChatyNotificationPolicy.callActionAllowed("c1", "c1", true))
        assertFalse(ChatyNotificationPolicy.callActionAllowed("c1", "c2", true))
        assertFalse(ChatyNotificationPolicy.callActionAllowed("c1", "c1", false))
        assertFalse(ChatyNotificationPolicy.callActionAllowed("c1", null, true))
        assertFalse(ChatyNotificationPolicy.callActionAllowed(null, null, true))
    }
}
