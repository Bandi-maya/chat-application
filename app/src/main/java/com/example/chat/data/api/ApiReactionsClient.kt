package com.example.chat.data.api

import com.example.chat.domain.models.MessageReaction
import java.time.Instant
import org.json.JSONObject

class ReactionAggregate(val messageId: String, val roomId: String, val updatedAt: Instant, val reactions: List<MessageReaction>) {
    companion object {
        fun parse(data: Map<String, Any?>): ReactionAggregate {
            val id = data["id"]?.toString()?.takeIf { it.isNotBlank() } ?: error("Missing reaction message")
            val room = data["room_id"]?.toString()?.takeIf { it.isNotBlank() } ?: error("Missing reaction chat")
            val at = java.time.OffsetDateTime.parse(data["updated_at"]?.toString()).toInstant()
            val rows = data["reactions"] as? List<*> ?: error("Invalid reaction aggregate")
            val reactions = rows.map { item ->
                val row = item as? Map<*, *> ?: error("Invalid reaction row")
                val emoji = row["emoji"] as? String ?: error("Missing reaction emoji")
                val ids = row["user_ids"] as? List<*> ?: error("Invalid reaction owners")
                MessageReaction(emoji, ids.map { it as? String ?: error("Invalid reaction owner") }.distinct())
            }
            return ReactionAggregate(id, room, at, reactions)
        }
    }
}

class ApiReactionsClient(private val owner: ApiAccountScope, private val transport: ApiHttpTransport, private val current: () -> Boolean) {
    fun supported(): Boolean = current() && ApiServerCapabilities.supports(owner, transport, "reactions_v1") && current()
    fun set(messageId: String, emoji: String?): ReactionAggregate {
        check(current()) { "Account changed." }
        val id = ApiPathSegments.safe(messageId) ?: error("Invalid message")
        val response = transport.execute(if (emoji == null) "DELETE" else "PUT",
            "${owner.baseUrl.trimEnd('/')}/chat/messages/$id/reaction",
            mapOf("Authorization" to "Bearer ${owner.token ?: error("Not authenticated")}", "x-tenant-id" to owner.tenantId,
                "Content-Type" to "application/json"), emoji?.let { JSONObject().put("emoji", it).toString() })
        check(current()) { "Account changed." }
        check(response.first in 200..299) { "Reaction was not confirmed (${response.first})." }
        return ReactionAggregate.parse(ApiChatClient.deepMap(JSONObject(response.second).getJSONObject("data")))
    }
}
