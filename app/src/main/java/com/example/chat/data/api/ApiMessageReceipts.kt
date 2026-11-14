package com.example.chat.data.api

import java.time.Instant
import java.util.UUID
import org.json.JSONObject

/** Server timestamps only. Never derive receipt times from local clocks or presence. */
data class MessageReceiptTimes(val deliveredAt: Instant?, val readAt: Instant?, val playedAt: Instant?)
data class MessageReceiptEvent(val messageId: String, val roomId: String, val userId: String,
    val times: MessageReceiptTimes, val allDelivered: Boolean, val allRead: Boolean) {
    companion object {
        fun parse(json: JSONObject): MessageReceiptEvent? {
            val message = json.optString("message_id"); val room = json.optString("room_id"); val user = json.optString("user_id")
            if (message.isBlank() || room.isBlank() || user.isBlank() || json.opt("all_read") !is Boolean || json.opt("all_delivered") !is Boolean) return null
            return MessageReceiptEvent(message, room, user, MessageReceiptTimes(receiptInstant(json, "delivered_at"),
                receiptInstant(json, "read_at"), receiptInstant(json, "played_at")), json.getBoolean("all_delivered"), json.getBoolean("all_read"))
        }
    }
}
internal fun receiptInstant(json: JSONObject, key: String): Instant? =
    (json.opt(key) as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() }
data class MessageInfoRecipient(val userId: String, val sentAt: Instant?, val times: MessageReceiptTimes, val readHidden: Boolean)
data class MessageInfoPage(val messageId: String, val snapshotKnown: Boolean, val sentAt: Instant?,
    val recipients: List<MessageInfoRecipient>, val nextAfterUserId: String?)

/** Captured credentials/routing only. No active-account closures; redirects disabled by supplied transport. */
class ApiMessageReceiptsClient(private val owner: ApiAccountScope, private val transport: ApiHttpTransport,
    private val isCurrent: () -> Boolean) {
    private fun id(value: String): String {
        require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) { "Invalid message or recipient ID." }
        return value
    }
    fun info(messageId: String, after: String? = null): MessageInfoPage {
        val query = "?limit=100" + (after?.let { "&after_user_id=${id(it)}" } ?: "")
        val data = request("GET", "chat/messages/${id(messageId)}/info$query", null)
        check(data.opt("snapshot_known") is Boolean && data.optString("message_id") == messageId) { "Invalid message info response." }
        val rows = data.optJSONArray("recipients") ?: error("Missing recipients.")
        check(rows.length() <= 100) { "Invalid message info page size." }
        val recipients = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val hidden = when (row.optString("read_visibility")) { "hidden" -> true; "visible" -> false; else -> error("Invalid receipt visibility.") }
            MessageInfoRecipient(id(row.getString("user_id")), receiptInstant(row, "sent_at"), MessageReceiptTimes(
                receiptInstant(row, "delivered_at"), if (hidden) null else receiptInstant(row, "read_at"),
                if (hidden) null else receiptInstant(row, "played_at")), hidden)
        }
        val next = (data.opt("next_after_user_id") as? String)?.takeIf { it.isNotBlank() }?.let(::id)
        check(next == null || next != after) { "Receipt page did not advance." }
        return MessageInfoPage(messageId, data.getBoolean("snapshot_known"), receiptInstant(data, "sent_at"), recipients, next)
    }
    fun acknowledge(messageId: String, kind: String): Boolean {
        require(kind in setOf("delivered", "read", "played"))
        return request("POST", "chat/messages/${id(messageId)}/receipt", JSONObject().put("kind", kind).toString()).opt("recorded") == true
    }
    private fun request(method: String, path: String, body: String?): JSONObject {
        check(isCurrent() && !owner.token.isNullOrBlank()) { "Account changed. Open message info again." }
        val uri = java.net.URI(owner.baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Secure receipt endpoint required." }
        val headers = mapOf("Authorization" to "Bearer ${owner.token}", "x-tenant-id" to owner.tenantId, "Content-Type" to "application/json")
        var response = transport.execute(method, "${owner.baseUrl.trimEnd('/')}/$path", headers, body)
        check(isCurrent()) { "Account changed. Open message info again." }
        if (response.first == 404) {
            response = transport.execute(method, "${owner.baseUrl.trimEnd('/')}/api/v1/$path", headers, body)
            check(isCurrent()) { "Account changed. Open message info again." }
        }
        check(response.first == 200) { if (response.first == 403) "Message info is only available to its sender while still a member." else "Message receipts unavailable (${response.first})." }
        return JSONObject(response.second).optJSONObject("data") ?: error("Invalid receipt response.")
    }
}
