package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiMlsRpcClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** D1 rebind: the MLS function names must land on the Chaty backend /e2ee
 *  routes with p_* params flattened and the APIResponse envelope unwrapped. */
class ApiMlsRpcClientTest {

    private class RecordingTransport(val status: Int, val body: String) : ApiHttpTransport {
        var method: String? = null
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var requestBody: String? = null
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            this.method = method; this.url = url; this.headers = headers; this.requestBody = body
            return status to body
        }
    }

    private fun envelope(data: JSONObject) = JSONObject().put("success", true).put("data", data).toString()

    @Test fun `register maps to devices endpoint, flattens params, unwraps data`() = runBlocking {
        val transport = RecordingTransport(200, envelope(JSONObject().put("available_key_packages", 7)))
        val client = ApiMlsRpcClient(
            baseUrl = { "https://api.example.test" },
            tenantId = { "chat-application" },
            token = { "tok" },
            transport = transport,
        )
        val result = client.rpc("register_mls_device_v1", mapOf(
            "p_device_id" to "mls_12345678",
            "p_ciphersuite" to "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
            "p_key_packages" to listOf(mapOf("id" to "x", "key_package" to "AA==")),
        ))
        assertEquals("https://api.example.test/e2ee/devices/register", transport.url)
        assertEquals("Bearer tok", transport.headers["Authorization"])
        assertEquals("chat-application", transport.headers["x-tenant-id"])
        val sent = JSONObject(transport.requestBody!!)
        assertEquals("mls_12345678", sent.getString("device_id"))
        assertEquals("x", sent.getJSONArray("key_packages").getJSONObject(0).getString("id"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(7, (result as Map<String, Any?>)["available_key_packages"])
        Unit
    }

    @Test fun `state fetch maps and returns maps and lists`() = runBlocking {
        val data = JSONObject()
            .put("group", JSONObject().put("group_id", "Z3JvdXA=").put("epoch", 3))
            .put("controls", org.json.JSONArray().put(JSONObject().put("epoch", 3).put("commit_payload", "QQ==")))
        val transport = RecordingTransport(200, envelope(data))
        val client = ApiMlsRpcClient({ "https://api.example.test/" }, { "t" }, { null }, transport)
        val result = client.rpc("get_mls_conversation_state_v1", mapOf("p_conversation_id" to "c", "p_device_id" to "d", "p_after_epoch" to 0))
        @Suppress("UNCHECKED_CAST")
        val map = result as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        assertEquals(3, (map["group"] as Map<String, Any?>)["epoch"])
        @Suppress("UNCHECKED_CAST")
        assertEquals("QQ==", ((map["controls"] as List<Map<String, Any?>>)[0])["commit_payload"])
        Unit
    }

    @Test fun `server error surfaces the detail string for setup-pending matching`() = runBlocking {
        val transport = RecordingTransport(409, JSONObject().put("detail", "MLS group already exists for this conversation.").toString())
        val client = ApiMlsRpcClient({ "https://api.example.test" }, { "t" }, { null }, transport)
        val error = assertThrows(java.io.IOException::class.java) {
            runBlocking { client.rpc("publish_mls_group_v1", mapOf("p_conversation_id" to "c")) }
        }
        assertTrue(error.message!!.contains("already exists"))
    }

    @Test fun `send message maps to messages endpoint and returns the id scalar`() = runBlocking {
        val id = java.util.UUID.randomUUID().toString()
        val transport = RecordingTransport(200, JSONObject().put("success", true).put("data", id).toString())
        val client = ApiMlsRpcClient({ "https://api.example.test" }, { "t" }, { "tok" }, transport)
        val result = client.rpc("send_mls_message_v1", mapOf(
            "p_conversation_id" to "c", "p_client_message_id" to "m", "p_sender_device_id" to "d",
            "p_group_id" to "Zw==", "p_epoch" to 0, "p_ciphertext" to "QUJDREVGR0hJSktMTU5PUA==",
        ))
        assertEquals("https://api.example.test/e2ee/messages/send", transport.url)
        val sent = JSONObject(transport.requestBody!!)
        assertEquals("c", sent.getString("conversation_id"))
        assertEquals(0, sent.getInt("epoch"))
        assertEquals(id, result)
        Unit
    }

    @Test fun `edit and delete map to the message endpoints`() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = object : ApiHttpTransport {
            override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
                calls.add(url)
                return 200 to JSONObject().put("success", true).put("data", JSONObject().put("ok", true)).toString()
            }
        }
        val client = ApiMlsRpcClient({ "https://api.example.test" }, { "t" }, { "tok" }, transport)
        client.rpc("edit_mls_message_v1", mapOf("p_message_id" to "m", "p_sender_device_id" to "d", "p_group_id" to "g", "p_epoch" to 1, "p_ciphertext" to "QUJDREVGR0hJSktMTU5PUA=="))
        client.rpc("delete_chat_message", mapOf("p_message_id" to "m", "p_for_everyone" to true))
        assertEquals("https://api.example.test/e2ee/messages/edit", calls[0])
        assertEquals("https://api.example.test/e2ee/messages/delete", calls[1])
        Unit
    }

    @Test fun `unknown function fails closed`() = runBlocking {
        val transport = RecordingTransport(200, "{}")
        val client = ApiMlsRpcClient({ "https://api.example.test" }, { "t" }, { null }, transport)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.rpc("delete_everything_v1", emptyMap()) }
        }
        Unit
    }

    @Test fun `404 retries under api-v1 mount`() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = object : ApiHttpTransport {
            override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
                calls.add(url)
                return if (calls.size == 1) 404 to "{}" else 200 to envelope(JSONObject().put("acked", true))
            }
        }
        val client = ApiMlsRpcClient({ "https://api.example.test" }, { "t" }, { null }, transport)
        client.rpc("ack_mls_welcome_v1", mapOf("p_welcome_id" to "w", "p_device_id" to "d"))
        assertEquals("https://api.example.test/e2ee/welcomes/ack", calls[0])
        assertEquals("https://api.example.test/api/v1/e2ee/welcomes/ack", calls[1])
        Unit
    }
}

class ApiMlsRpcClientReadsTest {

    private class RecordingTransport(val status: Int, val body: String) : ApiHttpTransport {
        var method: String? = null
        var url: String? = null
        var requestBody: String? = null
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            this.method = method; this.url = url; this.requestBody = body
            return status to body
        }
    }

    private fun client(transport: ApiHttpTransport) = ApiMlsRpcClient(
        baseUrl = { "https://api.example.test" },
        tenantId = { "chat-application" },
        token = { "tok" },
        transport = transport,
    )

    @Test fun `message list maps to e2ee list endpoint with after_seq flattened`() = runBlocking {
        val transport = RecordingTransport(200, JSONObject().put("data", org.json.JSONArray()).toString())
        client(transport).rpc("get_conversation_messages", mapOf(
            "p_conversation_id" to "room-1", "p_limit" to 200, "p_after_seq" to 41,
        ))
        assertEquals("POST", transport.method)
        assertEquals("https://api.example.test/e2ee/messages/list", transport.url)
        val body = JSONObject(transport.requestBody)
        assertEquals("room-1", body.getString("conversation_id"))
        assertEquals(41, body.getInt("after_seq"))
    }

    @Test fun `conversations list maps to e2ee conversations endpoint`() = runBlocking {
        val transport = RecordingTransport(200, JSONObject().put("data", org.json.JSONArray()).toString())
        client(transport).rpc("get_my_conversations", emptyMap())
        assertEquals("https://api.example.test/e2ee/conversations/list", transport.url)
    }

    @Test fun `profile search uses GET app-users with query`() = runBlocking {
        val transport = RecordingTransport(200, JSONObject().put("data", org.json.JSONArray()).toString())
        client(transport).rpc("search_profiles", mapOf("p_query" to "may a"))
        assertEquals("GET", transport.method)
        assertEquals("https://api.example.test/app-users?q=may+a", transport.url)
    }
}
