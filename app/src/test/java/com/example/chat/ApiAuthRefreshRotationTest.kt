package com.example.chat

import com.example.chat.data.api.ApiAuthClient
import com.example.chat.data.api.ApiHttpTransport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The secure backend rotates refresh tokens on every /auth/refresh and
 *  revokes the whole session if a consumed token is replayed. The client
 *  must therefore surface (and its caller persist) the rotated token. */
class ApiAuthRefreshRotationTest {

    private fun clientReturning(status: Int, body: String): ApiAuthClient {
        val transport = ApiHttpTransport { _, _, _, _ -> status to body }
        return ApiAuthClient(baseUrl = { "https://example.test" }, tenantId = { "t" }, transport = transport)
    }

    private fun refreshBody(access: String, refresh: String?, snake: Boolean = false): String {
        val data = JSONObject().put("token", access)
        if (refresh != null) data.put(if (snake) "refresh_token" else "refreshToken", refresh)
        return JSONObject().put("success", true).put("data", data).toString()
    }

    @Test fun `refresh returns the rotated refresh token (camelCase)`() {
        val client = clientReturning(200, refreshBody("at2", "rt2"))
        val result = client.refresh("rt1")
        assertTrue(result is ApiAuthClient.RefreshResult.Ok)
        val ok = result as ApiAuthClient.RefreshResult.Ok
        assertEquals("at2", ok.accessToken)
        assertEquals("rt2", ok.refreshToken)
    }

    @Test fun `refresh returns the rotated refresh token (snake_case)`() {
        val client = clientReturning(200, refreshBody("at2", "rt2", snake = true))
        val ok = client.refresh("rt1") as ApiAuthClient.RefreshResult.Ok
        assertEquals("rt2", ok.refreshToken)
    }

    @Test fun `refresh tolerates a server that does not rotate`() {
        val client = clientReturning(200, refreshBody("at2", null))
        val ok = client.refresh("rt1") as ApiAuthClient.RefreshResult.Ok
        assertNull(ok.refreshToken)
    }

    @Test fun `consumed token (401) maps to Rejected so the caller signs out`() {
        val client = clientReturning(401, """{"detail":"Session revoked"}""")
        assertEquals(ApiAuthClient.RefreshResult.Rejected, client.refresh("rt1"))
    }

    @Test fun `server error maps to Unavailable for a later retry`() {
        val client = clientReturning(500, """{"detail":"boom"}""")
        assertEquals(ApiAuthClient.RefreshResult.Unavailable, client.refresh("rt1"))
    }
}
