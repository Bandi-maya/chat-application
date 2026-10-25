package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiSubscriptionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Behavior parity tests for the REST subscription plane
 *  (api_backend_service.dart getAvailablePlans / subscribeToPlan). */
class ApiSubscriptionClientParityTest {

    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url, headers, body)
            calls.add(call)
            return handler(call)
        }
    }

    private fun client(t: FakeTransport, tok: String? = "tok") =
        ApiSubscriptionClient({ "http://10.0.2.2:8000" }, { "chat-application" }, { tok }, t)

    @Test
    fun getAvailablePlansSendsTenantAndAuthHeaders() {
        val t = FakeTransport { call ->
            assertEquals("GET", call.method)
            assertEquals("http://10.0.2.2:8000/organization/subscription/products", call.url)
            assertEquals("Bearer tok", call.headers["Authorization"])
            assertEquals("chat-application", call.headers["x-tenant-id"])
            200 to """{"data":[]}"""
        }
        val plans = client(t).getAvailablePlans(organizationId = "org-1")
        assertEquals(0, plans.size)
    }

    @Test
    fun getAvailablePlansOmitsAuthHeaderWithoutToken() {
        val t = FakeTransport { call ->
            assertTrue(call.headers["Authorization"] == null)
            200 to """{"data":[]}"""
        }
        client(t, tok = null).getAvailablePlans()
    }

    @Test
    fun getAvailablePlansFallsBackToApiV1On404() {
        val t = FakeTransport { call ->
            if (call.url.contains("/api/v1/")) 200 to """{"data":[]}""" else 404 to ""
        }
        val plans = client(t).getAvailablePlans()
        assertEquals(0, plans.size)
        assertEquals(2, t.calls.size)
        assertEquals("http://10.0.2.2:8000/api/v1/organization/subscription/products", t.calls[1].url)
    }

    @Test
    fun getAvailablePlansFlattensProductsIntoPlans() {
        val t = FakeTransport {
            200 to """{"data":[
                {"name":"Chaty","category":"messaging","default_tenant_type":"chat",
                 "plans":[
                    {"id":"p1","name":"Free","tier":"free","monthly_price":0,"annual_price":0},
                    {"id":"p2","name":"Pro","highlighted":true,"monthly_price":499.0,"annual_price":4790.0,"currency":"INR","features":["a","b"]}
                 ]},
                {"id":"solo","name":"Standalone"}
            ]}"""
        }
        val plans = client(t).getAvailablePlans()
        assertEquals(3, plans.size)
        assertEquals("p1", plans[0]["id"])
        assertEquals("Chaty", plans[0]["product_name"])
        assertEquals("messaging", plans[0]["product_category"])
        assertEquals("chat", plans[0]["default_tenant_type"])
        assertEquals("Chaty", plans[1]["product_name"])
        assertEquals(listOf("a", "b"), plans[1]["features"])
        // Product without a plans array is extracted as-is (no product_name injection).
        assertEquals("solo", plans[2]["id"])
        assertTrue(!plans[2].containsKey("product_name"))
    }

    @Test
    fun getAvailablePlansReadsMapDataWithPlansArray() {
        val t = FakeTransport { 200 to """{"data":{"plans":[{"id":"p9","name":"Solo"}]}}""" }
        val plans = client(t).getAvailablePlans()
        assertEquals(1, plans.size)
        assertEquals("p9", plans[0]["id"])
    }

    @Test
    fun getAvailablePlansReadsBareListBody() {
        val t = FakeTransport { 200 to """{"plans":[{"id":"p7"}]}""" }
        val plans = client(t).getAvailablePlans()
        assertEquals(1, plans.size)
        assertEquals("p7", plans[0]["id"])
    }

    @Test
    fun getAvailablePlansMapsServerDetailToError() {
        val t = FakeTransport { 500 to """{"detail":"products backend down"}""" }
        try {
            client(t).getAvailablePlans()
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("products backend down", e.message)
        }
    }

    @Test
    fun getAvailablePlansWrapsTransportFailure() {
        val t = FakeTransport { throw RuntimeException("connection refused") }
        try {
            client(t).getAvailablePlans()
            fail("expected exception")
        } catch (e: Exception) {
            assertTrue(e.message!!.startsWith("Unable to reach server to fetch subscription plans:"))
        }
        // Transport failure on the primary does NOT attempt the fallback.
        assertEquals(1, t.calls.size)
    }

    @Test
    fun getAvailablePlansReadsBareArrayBody() {
        val t = FakeTransport { 200 to """[{"plans":[{"id":"p3","name":"Bare"}]}]""" }
        val plans = client(t).getAvailablePlans()
        assertEquals(1, plans.size)
        assertEquals("p3", plans[0]["id"])
    }

    @Test
    fun subscribeToPlanPostsExactPayload() {
        val t = FakeTransport { call ->
            assertEquals("POST", call.method)
            assertEquals("http://10.0.2.2:8000/organization/subscription/create-order", call.url)
            assertEquals("Bearer tok", call.headers["Authorization"])
            assertEquals("chat-application", call.headers["x-tenant-id"])
            val body = org.json.JSONObject(call.body)
            assertEquals("org-1", body.getString("organization_id"))
            assertEquals("p2", body.getString("plan_type"))
            assertEquals(12, body.getInt("duration_months"))
            assertEquals(false, body.getBoolean("is_trial"))
            200 to """{"status":"ok"}"""
        }
        assertTrue(client(t).subscribeToPlan(" org-1 ", " p2 ", durationMonths = 12))
    }

    @Test
    fun subscribeToPlanHasNoApiV1Fallback() {
        val t = FakeTransport { 404 to """{"detail":"not found"}""" }
        try {
            client(t).subscribeToPlan("org-1", "p2")
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("not found", e.message)
        }
        assertEquals(1, t.calls.size)
    }

    @Test
    fun subscribeToPlanGenericErrorOnNonJsonBody() {
        val t = FakeTransport { 502 to """<html>bad gateway</html>""" }
        try {
            client(t).subscribeToPlan("org-1", "p2")
            fail("expected exception")
        } catch (e: Exception) {
            assertEquals("Failed to activate subscription (502)", e.message)
        }
    }
}
