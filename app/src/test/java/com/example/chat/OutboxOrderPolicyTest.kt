package com.example.chat

import com.example.chat.data.api.OutboxOrderPolicy
import org.junit.Assert.*
import org.junit.Test

class OutboxOrderPolicyTest {
    private data class Row(val id: Int, val chat: String, val blocked: Boolean = false)
    @Test fun blockedThreadHeadNeverLetsLaterMessageOvertake() {
        val rows = listOf(Row(1, "a", true), Row(2, "a"), Row(3, "b"), Row(4, "b"))
        assertEquals(3, OutboxOrderPolicy.next(rows, { it.chat }, { !it.blocked })?.id)
    }
    @Test fun eligibleHeadWinsAndSameThreadRemainsOrdered() {
        val rows = listOf(Row(1, "a"), Row(2, "a"), Row(3, "b"))
        assertEquals(1, OutboxOrderPolicy.next(rows, { it.chat }, { !it.blocked })?.id)
        assertEquals(2, OutboxOrderPolicy.next(rows.drop(1), { it.chat }, { !it.blocked })?.id)
    }
    @Test fun allBlockedMeansNoAutomaticSend() {
        assertNull(OutboxOrderPolicy.next(listOf(Row(1, "a", true), Row(2, "a")), { it.chat }, { !it.blocked }))
    }
}
