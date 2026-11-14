package com.example.chat.data.api

import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Authenticated server event, not a push-provided caller name or authorization. */
class DurableCallEvent(val eventId: String, val sequence: Long, val senderId: String,
    val data: JSONObject, val createdAt: Instant, val expiresAt: Instant) {
    val callId: String get() = data.getString("call_id")
    val kind: String get() = data.getString("signal_type")
    fun encoded(): JSONObject = JSONObject().put("event_id", eventId).put("seq", sequence).put("sender_id", senderId)
        .put("data", data).put("created_at", createdAt.toString()).put("expires_at", expiresAt.toString())
    companion object {
        fun decode(row: JSONObject): DurableCallEvent {
            val event = row.opt("event_id") as? String ?: error("Missing event identity.")
            check(runCatching { UUID.fromString(event).toString() == event }.getOrDefault(false)) { "Invalid event identity." }
            check(row.opt("id") == null || row.opt("id") == event) { "Conflicting event identity." }
            val sequenceRaw = row.opt("seq")
            check(sequenceRaw is Int || sequenceRaw is Long) { "Call sequence must be a JSON integer." }
            val sequence = sequenceRaw as Number
            check(sequence.toLong() >= 0 && sequence.toDouble() == sequence.toLong().toDouble()) { "Invalid event sequence." }
            val sender = row.opt("sender_id") as? String ?: error("Missing event sender.")
            check(runCatching { UUID.fromString(sender).toString() == sender }.getOrDefault(false)) { "Invalid canonical call sender." }
            val data = row.getJSONObject("data")
            check(data.toString().toByteArray(Charsets.UTF_8).size <= 65536)
            val call = data.opt("call_id") as? String ?: error("Missing call identity.")
            val kind = data.opt("signal_type") as? String ?: error("Missing signal type.")
            check(call.length in 1..100 && call.none(Char::isISOControl) && kind.length in 1..32 && kind.none(Char::isISOControl))
            val inner = data.opt("sender_id") as? String
            check(inner == null || inner == sender) { "Conflicting call sender." }
            val createdRaw = row.getString("created_at"); val expiresRaw = row.getString("expires_at")
            check(createdRaw.endsWith("Z") && expiresRaw.endsWith("Z"))
            val created = Instant.parse(createdRaw); val expires = Instant.parse(expiresRaw)
            check(expires.isAfter(created) && !expires.isAfter(created.plusSeconds(65))) { "Invalid call lifetime." }
            return DurableCallEvent(event, sequence.toLong(), sender, JSONObject(data.toString()).put("sender_id", sender), created, expires)
        }
    }
}
data class DurableCallPage(val events: List<DurableCallEvent>, val hasMore: Boolean)

/** Never invokes the destructive legacy endpoint. Supplied transport must reject redirects. */
class DurableCallInboxClient(private val owner: ApiAccountScope, private val transport: ApiHttpTransport,
    private val isCurrent: () -> Boolean) {
    fun supports(): Boolean = isCurrent() && ApiServerCapabilities.supports(owner, transport, "call_signal_inbox_v1") && isCurrent()
    fun peek(): DurableCallPage {
        val data = request("GET", "chat/call/signals/peek?limit=100", null)
        val rows = data.getJSONArray("events")
        check(rows.length() <= 100 && data.opt("has_more") is Boolean) { "Invalid call inbox page." }
        val events = (0 until rows.length()).map { DurableCallEvent.decode(rows.getJSONObject(it)) }
        check(events.map { it.eventId }.distinct().size == events.size && events.map { it.sequence }.distinct().size == events.size) { "Duplicate call event identity/sequence." }
        check(events.zipWithNext().all { (a, b) -> a.sequence < b.sequence }) { "Call inbox page is not ordered." }
        return DurableCallPage(events, data.getBoolean("has_more"))
    }
    fun acknowledge(eventIds: List<String>): Boolean {
        require(eventIds.isNotEmpty() && eventIds.size <= 100 && eventIds.distinct().size == eventIds.size)
        eventIds.forEach { require(runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)) }
        return request("POST", "chat/call/signals/ack", JSONObject().put("event_ids", JSONArray(eventIds)).toString()).opt("accepted") == true
    }
    private fun request(method: String, path: String, body: String?): JSONObject {
        val uri = java.net.URI(owner.baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
        check(isCurrent() && !owner.token.isNullOrBlank()) { "Call account changed." }
        val headers = mapOf("Authorization" to "Bearer ${owner.token}", "x-tenant-id" to owner.tenantId, "Content-Type" to "application/json")
        var response = transport.execute(method, "${owner.baseUrl.trimEnd('/')}/$path", headers, body)
        check(isCurrent()) { "Call account changed." }
        if (response.first == 404) {
            response = transport.execute(method, "${owner.baseUrl.trimEnd('/')}/api/v1/$path", headers, body)
            check(isCurrent()) { "Call account changed." }
        }
        check(response.first == 200) { "Call inbox unavailable (${response.first})." }
        return JSONObject(response.second).optJSONObject("data") ?: error("Invalid call inbox response.")
    }
}
