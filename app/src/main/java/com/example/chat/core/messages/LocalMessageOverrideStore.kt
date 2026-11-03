package com.example.chat.core.messages

import android.content.Context
import com.example.chat.domain.models.ChatMessage

/** Account/chat/message-scoped local presentation. No API writes or canonical message copies. */
class LocalMessageOverrideStore(context: Context, private val accountId: String, private val conversationId: String) {
    private val prefs = context.getSharedPreferences("chaty_local_message_overrides_v1", Context.MODE_PRIVATE)
    fun text(message: ChatMessage): String? {
        if (message.conversationId != conversationId || !LocalMessageOverridePolicy.editable(message)) return null
        val key = LocalMessageOverridePolicy.key(accountId, conversationId, message.id) ?: return null
        if (prefs.getString("$key.original", null) != LocalMessageOverridePolicy.fingerprint(message)) return null
        return prefs.getString(key, null)
    }
    fun save(message: ChatMessage, text: String): Boolean {
        if (message.conversationId != conversationId || !LocalMessageOverridePolicy.editable(message) || text.length > 12000) return false
        val key = LocalMessageOverridePolicy.key(accountId, conversationId, message.id) ?: return false
        prefs.edit().putString(key, text).putString("$key.original", LocalMessageOverridePolicy.fingerprint(message)).apply()
        return true
    }
    fun reset(messageId: String) {
        val key = LocalMessageOverridePolicy.key(accountId, conversationId, messageId) ?: return
        prefs.edit().remove(key).remove("$key.original").apply()
    }
}
