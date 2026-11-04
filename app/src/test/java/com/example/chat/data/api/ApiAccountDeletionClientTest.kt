package com.example.chat.data.api
import org.junit.Assert.*
import org.junit.Test
class ApiAccountDeletionClientTest {
    private fun scope() = ApiAccountScope("me", "test-token", "tenant", "https://test.invalid", "", 1)
    @Test fun rejectedOrPendingIsNotCompletion() {
        for (status in listOf(200, 202, 403, 500)) {
            val api = ApiAccountDeletionClient(ApiHttpTransport { _, _, _, _ -> status to "{}" })
            try { api.delete(scope(), { true }, "me"); fail("$status must not complete") } catch (_: IllegalStateException) {}
        }
    }
    @Test fun noContentIsExplicitCompletion() {
        ApiAccountDeletionClient(ApiHttpTransport { method, _, _, body -> assertEquals("DELETE", method); assertNull(body); 204 to "" }).delete(scope(), { true }, "me")
    }
    @Test fun accountSwitchDoesNotApply() {
        var current = true
        val api = ApiAccountDeletionClient(ApiHttpTransport { _, _, _, _ -> current = false; 204 to "" })
        try { api.delete(scope(), { current }, "me"); fail() } catch (_: IllegalStateException) {}
    }
}
