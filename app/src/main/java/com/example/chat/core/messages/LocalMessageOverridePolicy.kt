package com.example.chat.core.messages

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import java.security.MessageDigest

/** Local presentation only. Never mutate canonical text or server edit/delivery metadata. */
object LocalMessageOverridePolicy {
    fun editable(message: ChatMessage): Boolean = message.type == MessageType.text && !message.text.startsWith("[POLL] ") && !message.text.startsWith("[TASK] ") &&
        !message.isDeletedForEveryone && !message.isDeletedForMe && !message.isRevokedBySender && !ViewOnceTextPolicy.restricted(message.metadata)
    fun accountKey(tenant: String, baseUrl: String, userId: String): String? {
        if (tenant.isBlank() || baseUrl.isBlank() || userId.isBlank()) return null
        return listOf(tenant, baseUrl.trimEnd('/'), userId).joinToString("") { "${it.length}:$it" }
    }
    fun sessionCurrent(expected: Any, actual: Any): Boolean = expected === actual
    fun key(accountId: String, conversationId: String, messageId: String): String? {
        if (accountId.isBlank() || conversationId.isBlank() || messageId.isBlank()) return null
        val input = listOf(accountId, conversationId, messageId).joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun fingerprint(message: ChatMessage): String = MessageDigest.getInstance("SHA-256").digest(message.text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun displayText(message: ChatMessage, localText: String?): String =
        if (editable(message) && localText != null) localText else message.text
}
