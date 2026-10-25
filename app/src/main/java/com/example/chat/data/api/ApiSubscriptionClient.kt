package com.example.chat.data.api

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/** Context-free port of the api branch's REST subscription surface
 *  (lib/data/services/api_backend_service.dart: getAvailablePlans +
 *  subscribeToPlan). Same transport seam as ApiAuthClient/ApiChatClient so
 *  the request/response logic stays JVM-testable.
 *
 *  Endpoint layout:
 *  - plans:    GET  {base}/organization/subscription/products
 *              (404 fallback {base}/api/v1/organization/subscription/products)
 *  - order:    POST {base}/organization/subscription/create-order (no fallback)
 *  Both carry Content-Type + x-tenant-id, and Authorization: Bearer when a
 *  session token exists — exactly the Dart header set. */
class ApiSubscriptionClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: ApiHttpTransport,
) {

    /** ApiBackendService.getAvailablePlans. [organizationId] mirrors the Dart
     *  signature; the Dart implementation accepts but never sends it. */
    fun getAvailablePlans(organizationId: String? = null): List<Map<String, Any?>> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
        token()?.let { headers["Authorization"] = "Bearer $it" }

        val response: Pair<Int, String> = try {
            val primary = transport.execute(
                "GET", "${baseUrl()}/organization/subscription/products", headers, null,
            )
            if (primary.first == 404) {
                transport.execute(
                    "GET", "${baseUrl()}/api/v1/organization/subscription/products", headers, null,
                )
            } else {
                primary
            }
        } catch (e: Exception) {
            throw Exception("Unable to reach server to fetch subscription plans: $e")
        }

        if (response.first == 200) {
            // Dart jsonDecode accepts an object OR a bare array body.
            val parsed = JSONTokener(response.second).nextValue()
            val data = if (parsed is JSONObject && parsed.has("data")) parsed.get("data") else parsed
            val extracted = mutableListOf<Map<String, Any?>>()

            if (data is JSONArray) {
                for (i in 0 until data.length()) {
                    val item = data.opt(i) as? JSONObject ?: continue
                    val plans = item.optJSONArray("plans")
                    val productName = (if (item.has("name") && !item.isNull("name")) item.get("name")
                        else if (item.has("title") && !item.isNull("title")) item.get("title")
                        else "").toString()
                    val productCategory = (if (item.has("category") && !item.isNull("category")) item.get("category") else "").toString()
                    val defaultTenantType = (if (item.has("default_tenant_type") && !item.isNull("default_tenant_type")) item.get("default_tenant_type") else "").toString()

                    if (plans != null && plans.length() > 0) {
                        for (j in 0 until plans.length()) {
                            val p = plans.opt(j) as? JSONObject ?: continue
                            val planMap = jsonToMap(p).toMutableMap()
                            if (productName.isNotEmpty()) planMap.putIfAbsent("product_name", productName)
                            if (productCategory.isNotEmpty()) planMap.putIfAbsent("product_category", productCategory)
                            if (defaultTenantType.isNotEmpty()) planMap.putIfAbsent("default_tenant_type", defaultTenantType)
                            extracted.add(planMap)
                        }
                    } else {
                        extracted.add(jsonToMap(item))
                    }
                }
            } else if (data is JSONObject) {
                val plans = data.optJSONArray("plans")
                if (plans != null) {
                    for (j in 0 until plans.length()) {
                        val p = plans.opt(j) as? JSONObject ?: continue
                        extracted.add(jsonToMap(p))
                    }
                } else {
                    extracted.add(jsonToMap(data))
                }
            }
            return extracted
        }

        var errorMsg = "Failed to load subscription plans (${response.first})"
        try {
            val parsed = JSONObject(response.second)
            errorMsg = parsed.optString("detail").ifEmpty { parsed.optString("message").ifEmpty { errorMsg } }
        } catch (_: Exception) {
        }
        throw Exception(errorMsg)
    }

    /** ApiBackendService.subscribeToPlan: POST create-order, no 404 fallback.
     *  Returns true on HTTP 200; throws the server detail/message otherwise. */
    fun subscribeToPlan(
        organizationId: String,
        planId: String,
        durationMonths: Int = 1,
        isTrial: Boolean = false,
    ): Boolean {
        val payload = JSONObject()
            .put("organization_id", organizationId.trim())
            .put("plan_type", planId.trim())
            .put("duration_months", durationMonths)
            .put("is_trial", isTrial)
            .toString()

        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
        token()?.let { headers["Authorization"] = "Bearer $it" }

        val response = transport.execute(
            "POST", "${baseUrl()}/organization/subscription/create-order", headers, payload,
        )

        if (response.first == 200) return true
        try {
            val parsed = JSONObject(response.second)
            val detail = parsed.optString("detail").ifEmpty {
                parsed.optString("message").ifEmpty { "Subscription failed (${response.first})" }
            }
            throw Exception(detail)
        } catch (e: Exception) {
            // Dart: a JSON decode failure falls through to the generic message;
            // an already-meaningful Exception is rethrown.
            if (e is JSONException) throw Exception("Failed to activate subscription (${response.first})")
            throw e
        }
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = if (obj.isNull(key)) null else unwrap(obj.get(key))
        }
        return map
    }

    private fun unwrap(value: Any): Any = when (value) {
        is JSONObject -> jsonToMap(value)
        is JSONArray -> {
            val list = ArrayList<Any?>()
            for (i in 0 until value.length()) {
                list.add(if (value.isNull(i)) null else unwrap(value.get(i)))
            }
            list
        }
        else -> value
    }
}
