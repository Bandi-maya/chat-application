package com.example.chat.data.messaging

import com.example.chat.data.local.SnapshotCodec
import com.example.chat.domain.models.ChatMessage

/**
 * S1 offline read fallback (Bandi, 2026-11-14): reads the room's encrypted
 * snapshot-cache page (the same scope [ConversationsRepository.persistMessageSnapshot]
 * writes, `messages_<conversationId>`) so [ConversationsRepository.loadMessages] can
 * serve saved messages when the RPC is unreachable, instead of showing a spinner.
 *
 * Reads exactly what this repository writes - no shared state with
 * ApiBackendService's coordinator. Cached rows are stale by definition and must
 * never advance seq cursors or trigger a re-persist; the caller handles both.
 *
 * Scope discipline matches persistMessageSnapshot: the account scope is captured
 * by the caller and re-checked before AND after the storage read, so an account
 * switch mid-read can never return another account's messages. Storage or decode
 * failures degrade to a cache miss (null); coroutine cancellation still propagates.
 */
class MessageSnapshotFallback(
    private val snapshotCache: SnapshotCacheStore?,
    private val messageNamespace: (Any?) -> String?,
    private val withScope: (Any?, () -> Unit) -> Boolean,
) {
    /** The cached page for this room, sorted by time, or null when absent/unreadable. */
    suspend fun read(conversationId: String, expectedScope: Any?): List<ChatMessage>? {
        val cache = snapshotCache ?: return null
        val userId = messageNamespace(expectedScope) ?: return null
        if (conversationId.isBlank()) return null
        var stillCurrent = false
        if (!withScope(expectedScope) { stillCurrent = true } || !stillCurrent) return null
        val raw = try { cache.readJson(userId, "messages_$conversationId") }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { null } ?: return null
        val list = raw as? List<*> ?: return null
        val decoded = list.mapNotNull { item ->
            (item as? Map<*, *>)?.let { map ->
                @Suppress("UNCHECKED_CAST")
                runCatching { SnapshotCodec.chatMessageFromJson(map as Map<String, Any?>) }.getOrNull()
            }
        }.filter { it.id.isNotBlank() && it.conversationId == conversationId }.sortedBy { it.createdAt }
        stillCurrent = false
        if (!withScope(expectedScope) { stillCurrent = true } || !stillCurrent) return null
        return decoded.ifEmpty { null }
    }
}
