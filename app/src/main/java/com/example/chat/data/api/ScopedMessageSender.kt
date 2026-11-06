package com.example.chat.data.api

import com.example.chat.domain.models.ChatMessage

/**
 * Sends one text message as EXACTLY the account in `expected`. Base URL, tenant and token come from that scope
 * object, never from the current session. The request goes through [transportFor] (production: the scope-bound,
 * cancel-on-transition transport). The confirmed message is handed to [merge] only through [guarded] while
 * `expected` is still the live scope; a stale confirmation returns false and merges nothing.
 */
class ScopedMessageSender(
    private val current: () -> ApiAccountScope,
    private val guarded: (ApiAccountScope, () -> Unit) -> Boolean,
    private val transportFor: (ApiAccountScope) -> ApiHttpTransport,
    private val merge: (ChatMessage) -> Unit,
) {
    /** Blocking (call from an IO dispatcher). */
    fun send(expected: ApiAccountScope, conversationId: String, text: String): Boolean {
        val token = expected.token?.takeIf { it.isNotBlank() } ?: return false
        if (conversationId.isBlank() || text.isBlank() || current() !== expected) return false
        val client = ApiChatClient(
            baseUrl = { expected.baseUrl }, tenantId = { expected.tenantId }, token = { token },
            transport = transportFor(expected),
        )
        val confirmed = client.sendMessage(conversationId, text) ?: return false
        return guarded(expected) { merge(confirmed) }
    }
}
