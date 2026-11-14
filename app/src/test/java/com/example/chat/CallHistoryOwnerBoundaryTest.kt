package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.data.services.ApiCallHistoryService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CallHistoryOwnerBoundaryTest {
    private fun owner() = ApiAccountScope("same-user", "synthetic", "tenant", "https://example.invalid", "", 1)
    @Test fun sameUserReloginCannotPublishPreviousScopeHistory() = runBlocking {
        var current = owner()
        val original = current
        lateinit var history: ApiCallHistoryService
        history = ApiCallHistoryService(
            currentScope = { current },
            clientFor = { captured -> ApiCallHistoryClient({ captured.baseUrl }, { captured.tenantId },
                ApiHttpTransport { _, _, _, _ ->
                    current = owner() // Same uid/tenant/token, different reference owner.
                    history.stop()
                    200 to """{"data":{"entries":[{"id":"old","other_user_id":"remote","direction":"outgoing"}]}}"""
                }, { captured.token }) },
            withScope = { expected, block -> if (expected === current) { block(); true } else false },
        )
        history.refresh()
        assertNotSame(original, current)
        assertTrue(history.records.isEmpty())
        assertNull(history.lastError)
    }
    @Test fun staleDeleteFailureCannotRestoreOtherAccountRecords() = runBlocking {
        var current = owner()
        var calls = 0
        lateinit var history: ApiCallHistoryService
        history = ApiCallHistoryService(
            currentScope = { current },
            clientFor = { captured -> ApiCallHistoryClient({ captured.baseUrl }, { captured.tenantId },
                ApiHttpTransport { _, _, _, _ ->
                    calls++
                    if (calls == 1) 200 to """{"data":{"entries":[{"id":"old","other_user_id":"remote","direction":"outgoing"}]}}"""
                    else { current = owner(); history.stop(); throw java.io.IOException("synthetic failure") }
                }, { captured.token }) },
            withScope = { expected, block -> if (expected === current) { block(); true } else false },
        )
        history.refresh()
        val row = history.records.single()
        try { history.deleteEntry(row); fail("Expected transport failure") } catch (_: Exception) { }
        assertTrue(history.records.isEmpty())
        assertNull(history.lastError)
    }
}
