package com.example.chat.data.api

import com.example.chat.domain.models.ChatMessage
import org.json.JSONObject

/** One-shot explicit API operations. Transport must disable automatic retries and redirects. */
class ApiViewOnceTextClient(
    private val owner: ApiAccountScope,
    private val transport: ApiHttpTransport,
    private val current: () -> Boolean,
) {
    private fun request(path: String, payload: JSONObject): Pair<Int, String> {
        check(current()) { "Account changed." }
        val response = transport.execute("POST", "${owner.baseUrl.trimEnd('/')}/$path",
            mapOf("Authorization" to "Bearer ${owner.token ?: error("Not authenticated")}",
                "x-tenant-id" to owner.tenantId, "Content-Type" to "application/json"), payload.toString())
        check(current()) { "Account changed." }
        return response
    }
    fun supported(): Boolean = current() && ApiServerCapabilities.supports(owner, transport, "view_once_v1") && current()
    fun send(roomId: String, text: String, clientId: String): ChatMessage {
        check(supported()) { "View-once text is unavailable on this server. Nothing was sent." }
        val room = ApiPathSegments.safe(roomId) ?: error("Invalid chat")
        val response = request("chat/rooms/$room/messages", JSONObject().put("content", text)
            .put("client_id", clientId).put("view_once", true))
        check(response.first in 200..299) { "View-once send was not confirmed (${response.first}). Check the chat before sending again." }
        val raw = JSONObject(response.second).getJSONObject("data")
        check(raw.optBoolean("view_once", false)) { "Server did not confirm view-once support. Check the chat before sending again." }
        return com.example.chat.core.messages.ViewOnceTextPolicy.redact(ChatMessage.fromApi(ApiChatClient.deepMap(raw)))
    }
    /** Never retry: even a missing response can mean the server already consumed the message. */
    fun openPlain(messageId: String): String {
        check(supported()) { "View-once text is unavailable on this server." }
        val id = ApiPathSegments.safe(messageId) ?: error("Invalid message")
        val response = request("e2ee/messages/$id/open", JSONObject())
        check(response.first in 200..299) { "No longer available" }
        val data = JSONObject(response.second).getJSONObject("data")
        check(data.isNull("encrypted_payload")) { "Encrypted view-once text is unavailable in this build." }
        return data.getString("content")
    }
}
