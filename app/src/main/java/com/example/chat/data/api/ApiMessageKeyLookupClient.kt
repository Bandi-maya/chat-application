package com.example.chat.data.api

import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

/** Identity metadata only. Never proof that the pending payload was accepted unchanged or delivered. */
data class MessageKeyIdentity(val messageId: String, val roomId: String, val clientId: String,
    val state: String, val sequence: Long?, val createdAt: Instant?, val editedAt: Instant?, val deletedAt: Instant?)

class ApiMessageKeyLookupClient(private val owner: ApiAccountScope, private val transport: ApiHttpTransport,
    private val current: () -> Boolean) {
    private fun uuid(id: String): String = id.also { require(UUID.fromString(it).toString() == it) }
    fun lookup(roomId: String, originalKey: String): MessageKeyIdentity {
        require(originalKey.length in 1..100 && originalKey.none(Char::isISOControl))
        val uri = URI(owner.baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
        check(current() && !owner.token.isNullOrBlank()) { "Account changed. Reconciliation was not performed." }
        check(ApiServerCapabilities.supports(owner, transport, "message_client_id_lookup_v1") && current()) {
            "Message reconciliation is not available on this server. The original send is kept and will not be sent under a new key."
        }
        val path = "chat/rooms/${uuid(roomId)}/message-by-client-id?client_id=${URLEncoder.encode(originalKey, "UTF-8")}"
        val headers = mapOf("Authorization" to "Bearer ${owner.token}", "x-tenant-id" to owner.tenantId)
        var response = transport.execute("GET", "${owner.baseUrl.trimEnd('/')}/$path", headers, null)
        check(current()) { "Account changed. Reconciliation was not performed." }
        if (response.first == 404) {
            response = transport.execute("GET", "${owner.baseUrl.trimEnd('/')}/api/v1/$path", headers, null)
            check(current()) { "Account changed. Reconciliation was not performed." }
        }
        check(response.first == 200) {
            when (response.first) {
                404 -> "No accessible matching message was found. This does not prove it was never sent; the original key is kept."
                409 -> "More than one original message may match. Reconciliation remains unresolved; no new message was sent."
                else -> "Could not reconcile this message (${response.first}). The original key is kept."
            }
        }
        val root = JSONObject(response.second)
        check(root.opt("success") == true)
        val row = root.getJSONObject("data")
        check(row.getString("room_id") == roomId && row.getString("client_id") == originalKey) { "Message lookup identity mismatch." }
        val state = row.getString("state")
        check(state in setOf("sent", "edited", "deleted"))
        fun time(key: String): Instant? {
            val value = row.opt(key)
            if (value == null || value == JSONObject.NULL) return null
            check(value is String && value.endsWith("Z"))
            return Instant.parse(value)
        }
        val seq = row.opt("seq")
        check(seq == null || seq == JSONObject.NULL || ((seq is Int || seq is Long) && (seq as Number).toLong() >= 0))
        return MessageKeyIdentity(uuid(row.getString("message_id")), roomId, originalKey, state,
            (seq as? Number)?.toLong(), time("created_at"), time("edited_at"), time("deleted_at"))
    }
}
