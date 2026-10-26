package com.example.chat.data.services

import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.data.messaging.SupabaseRpc
import com.example.chat.domain.models.MessageAutomationPreferences
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Server-side scheduled message row (exact port of the Dart
 *  ServerScheduledMessage in gb_feature_backend_service.dart). */
data class ServerScheduledMessage(
    val id: String,
    val conversationId: String,
    val body: String,
    val scheduledAt: ZonedDateTime,
    val state: String,
    val executedAt: ZonedDateTime?,
    val lastError: String?,
)

/** Exact port of lib/data/services/gb_feature_backend_service.dart.
 *
 *  Availability semantics: on api the service checks Supabase.instance.isInitialized
 *  when no custom client is injected. The Kotlin port receives nullable clients
 *  up front; a null queryClient means "Supabase unavailable" and every method
 *  follows the exact same branch as the Dart `_isSupabaseAvailable == false`
 *  path (silent no-op for sync/deletes, local fallback id for createAutoReplyRule).
 */
class GbFeatureBackendService(
    private val queryClient: PostgrestQueryClient?,
    private val rpcClient: SupabaseRpc?,
    private val currentUserId: () -> String?,
) {
    private val isSupabaseAvailable: Boolean get() = queryClient != null

    private val userId: String
        get() = currentUserId() ?: throw IllegalStateException("Authentication required.")

    suspend fun synchronizeAutomation(prefs: MessageAutomationPreferences) {
        val client = queryClient ?: return
        val userId = userId
        // The master Auto-Reply switch gates every rule. When it is off, rules are
        // persisted (so the user's list survives) but pushed as disabled so the
        // server-side responder never fires for any of them.
        val masterEnabled = prefs.enableAutoReply
        for (rule in prefs.autoReplyRules) {
            if (rule.keyword.trim().isEmpty() || rule.responseMessage.trim().isEmpty()) continue
            val existing = client.from("auto_reply_rules")
                .select("id")
                .eq("user_id", userId)
                .ilike("keyword", rule.keyword.trim())
                .eq("response_body", rule.responseMessage.trim())
                .limit(1)
                .execute()
                .firstOrNull()
            if (existing == null) {
                client.from("auto_reply_rules").insert(
                    mapOf(
                        "user_id" to userId,
                        "keyword" to rule.keyword.trim(),
                        "response_body" to rule.responseMessage.trim(),
                        "scope" to scopeOf(rule.recipientFilter),
                        "enabled" to (masterEnabled && rule.enabled),
                    ),
                )
            } else {
                client.from("auto_reply_rules")
                    .eq("id", existing["id"])
                    .update(
                        mapOf(
                            "scope" to scopeOf(rule.recipientFilter),
                            "enabled" to (masterEnabled && rule.enabled),
                            "updated_at" to Instant.now().toString(),
                        ),
                    )
            }
        }

        for (template in prefs.quickReplies) {
            val shortcut = template.shortcut.trim()
            val content = template.content.trim()
            if (shortcut.isEmpty() || content.isEmpty()) continue
            client.from("quick_reply_templates").upsert(
                mapOf(
                    "user_id" to userId,
                    "shortcut" to shortcut,
                    "title" to template.title.trim(),
                    "content" to content,
                    "updated_at" to Instant.now().toString(),
                ),
                onConflict = "user_id,shortcut",
            )
        }
    }

    suspend fun createAutoReplyRule(
        keyword: String,
        response: String,
        scope: String = "all",
    ): String {
        val client = queryClient
            ?: return "rule_${System.currentTimeMillis()}"
        val rows = client.from("auto_reply_rules").insert(
            mapOf(
                "user_id" to userId,
                "keyword" to keyword.trim(),
                "response_body" to response.trim(),
                "scope" to scope,
                "enabled" to true,
            ),
        )
        return rows.first()["id"].toString()
    }

    suspend fun deleteAutoReplyRule(ruleId: String) {
        val client = queryClient ?: return
        client.from("auto_reply_rules")
            .eq("id", ruleId)
            .eq("user_id", userId)
            .delete()
    }

    suspend fun deleteAutoReplyRuleBySignature(keyword: String, response: String) {
        val client = queryClient ?: return
        client.from("auto_reply_rules")
            .eq("user_id", userId)
            .ilike("keyword", keyword.trim())
            .eq("response_body", response.trim())
            .delete()
    }

    suspend fun scheduleMessage(
        conversationId: String,
        body: String,
        scheduledAt: Instant,
    ): String {
        val raw = rpcClient!!.rpc(
            "schedule_message",
            mapOf(
                "p_conversation_id" to conversationId,
                "p_body" to body.trim(),
                "p_scheduled_at" to scheduledAt.toString(),
                "p_metadata" to mapOf("source" to "chaty_scheduler"),
            ),
        )
        return raw.toString()
    }

    suspend fun cancelScheduledMessage(id: String) {
        rpcClient!!.rpc("cancel_scheduled_message", mapOf("p_id" to id))
    }

    suspend fun getScheduledMessages(): List<ServerScheduledMessage> {
        val client = queryClient!!
        val rows = client.from("scheduled_messages")
            .select("id,conversation_id,body,scheduled_at,state,executed_at,last_error")
            .eq("user_id", userId)
            .order("scheduled_at", ascending = false)
            .execute()
        return rows.map { row ->
            ServerScheduledMessage(
                id = row["id"].toString(),
                conversationId = row["conversation_id"].toString(),
                body = row["body"]?.toString() ?: "",
                scheduledAt = parseToLocal(row["scheduled_at"].toString())!!,
                state = row["state"]?.toString() ?: "pending",
                executedAt = row["executed_at"]?.toString()?.let { parseToLocal(it) },
                lastError = row["last_error"]?.toString(),
            )
        }
    }

    suspend fun upsertQuickReply(shortcut: String, title: String, content: String): String {
        val client = queryClient!!
        val rows = client.from("quick_reply_templates").upsertReturning(
            mapOf(
                "user_id" to userId,
                "shortcut" to shortcut.trim(),
                "title" to title.trim(),
                "content" to content.trim(),
                "updated_at" to Instant.now().toString(),
            ),
            onConflict = "user_id,shortcut",
        )
        return rows.first()["id"].toString()
    }

    suspend fun deleteQuickReply(shortcut: String) {
        val client = queryClient!!
        client.from("quick_reply_templates")
            .eq("user_id", userId)
            .eq("shortcut", shortcut.trim())
            .delete()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getBlockedUsers(): List<Map<String, Any?>> {
        val raw = rpcClient!!.rpc("get_my_blocked_users", emptyMap())
        if (raw !is List<*>) return emptyList()
        return raw.filterIsInstance<Map<String, Any?>>()
    }

    suspend fun blockUser(userId: String) {
        rpcClient!!.rpc("block_user", mapOf("p_user_id" to userId))
    }

    suspend fun unblockUser(userId: String) {
        rpcClient!!.rpc("unblock_user", mapOf("p_user_id" to userId))
    }

    suspend fun massSend(conversationIds: List<String>, body: String): Int {
        val raw = rpcClient!!.rpc(
            "mass_send_message",
            mapOf(
                "p_conversation_ids" to conversationIds,
                "p_body" to body.trim(),
            ),
        )
        return (raw as? Int) ?: (raw as? Number)?.toInt() ?: raw?.toString()?.toIntOrNull() ?: 0
    }

    suspend fun sendStructuredMessage(
        conversationId: String,
        type: String,
        body: String,
        metadata: Map<String, Any?> = emptyMap(),
    ): String {
        val raw = rpcClient!!.rpc(
            "send_message",
            mapOf(
                "p_conversation_id" to conversationId,
                "p_client_message_id" to UUID.randomUUID().toString(),
                "p_body" to body,
                "p_type" to type,
                "p_metadata" to metadata,
            ),
        )
        return raw.toString()
    }

    suspend fun createPoll(
        conversationId: String,
        question: String,
        options: List<String>,
        allowMultiple: Boolean = false,
    ): String {
        val clean = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.size < 2) throw Exception("A poll requires at least two options.")
        val raw = rpcClient!!.rpc(
            "create_poll",
            mapOf(
                "p_conversation_id" to conversationId,
                "p_client_message_id" to UUID.randomUUID().toString(),
                "p_question" to question.trim(),
                "p_options" to clean,
                "p_allow_multiple" to allowMultiple,
            ),
        )
        return raw.toString()
    }

    /** Dart _PollViewerSheet._load(): Supabase rpc get_poll(p_message_id); a
     *  non-map payload yields null (caller raises 'Poll data is unavailable.'). */
    suspend fun getPoll(messageId: String): Map<String, Any?>? {
        val raw = rpcClient!!.rpc("get_poll", mapOf("p_message_id" to messageId))
        return (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
    }

    suspend fun votePoll(messageId: String, optionId: String) {
        rpcClient!!.rpc(
            "vote_poll",
            mapOf("p_message_id" to messageId, "p_option_id" to optionId),
        )
    }

    suspend fun setTyping(conversationId: String, isTyping: Boolean) {
        rpcClient!!.rpc(
            "set_typing_state",
            mapOf("p_conversation_id" to conversationId, "p_is_typing" to isTyping),
        )
    }

    suspend fun editMessage(messageId: String, body: String) {
        rpcClient!!.rpc(
            "edit_chat_message",
            mapOf("p_message_id" to messageId, "p_body" to body.trim()),
        )
    }

    companion object {
        /** Exact port of the Dart `_scope`. */
        fun scopeOf(recipientFilter: String): String =
            when (recipientFilter.lowercase()) {
                "groups", "group" -> "group"
                "contacts", "direct" -> "direct"
                else -> "all"
            }

        /** Mirrors DateTime.parse(...).toLocal() (and tryParse at call sites):
         *  tolerant of both 'T' and ' ' separators and 'Z'/offset suffixes;
         *  returns null on unparseable input. */
        fun parseToLocal(raw: String): ZonedDateTime? = try {
            OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault())
        } catch (_: Exception) {
            try {
                Instant.parse(raw).atZone(ZoneId.systemDefault())
            } catch (_: Exception) {
                try {
                    OffsetDateTime.parse(raw.replace(' ', 'T'))
                        .atZoneSameInstant(ZoneId.systemDefault())
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
