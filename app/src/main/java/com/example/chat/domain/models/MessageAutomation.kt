package com.example.chat.domain.models

/** Exact port of the automation-related models from
 *  lib/domain/models/preferences.dart (AutoReplyRule, QuickReplyTemplate,
 *  ScheduledMessageEntry, MessageAutomationPreferences), including the
 *  default rules/templates. The rest of preferences.dart ports with M7/M8. */
data class AutoReplyRule(
    val id: String,
    val enabled: Boolean = true,
    val keyword: String,
    val responseMessage: String,
    val recipientFilter: String = "All", // 'All', 'Contacts', 'Groups'
    val matchType: String = "contains", // 'contains', 'exact', 'starts_with'
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "enabled" to enabled,
        "keyword" to keyword,
        "responseMessage" to responseMessage,
        "recipientFilter" to recipientFilter,
        "matchType" to matchType,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): AutoReplyRule = AutoReplyRule(
            id = map["id"] as String? ?: "",
            enabled = map["enabled"] as Boolean? ?: true,
            keyword = map["keyword"] as String? ?: "",
            responseMessage = map["responseMessage"] as String? ?: "",
            recipientFilter = map["recipientFilter"] as String? ?: "All",
            matchType = map["matchType"] as String? ?: "contains",
        )
    }
}

data class QuickReplyTemplate(
    val shortcut: String,
    val title: String,
    val content: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "shortcut" to shortcut,
        "title" to title,
        "content" to content,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): QuickReplyTemplate = QuickReplyTemplate(
            shortcut = map["shortcut"] as String? ?: "",
            title = map["title"] as String? ?: "",
            content = map["content"] as String? ?: "",
        )
    }
}

data class ScheduledMessageEntry(
    val id: String,
    val recipientId: String,
    val recipientName: String,
    val text: String,
    val scheduledAtMillis: Long,
    val isExecuted: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "recipientId" to recipientId,
        "recipientName" to recipientName,
        "text" to text,
        "scheduledAt" to scheduledAtMillis,
        "isExecuted" to isExecuted,
    )

    companion object {
        /** Dart fallback DateTime.now() preserved for a missing timestamp. */
        fun fromMap(map: Map<String, Any?>): ScheduledMessageEntry = ScheduledMessageEntry(
            id = map["id"] as String,
            recipientId = map["recipientId"] as String? ?: "",
            recipientName = map["recipientName"] as String? ?: "",
            text = map["text"] as String? ?: "",
            scheduledAtMillis = (map["scheduledAt"] as Number?)?.toLong()
                ?: System.currentTimeMillis(),
            isExecuted = map["isExecuted"] as Boolean? ?: false,
        )
    }
}

data class MessageAutomationPreferences(
    val enableAutoReply: Boolean = false,
    val autoReplyRules: List<AutoReplyRule> = listOf(
        AutoReplyRule(
            id = "rule_1",
            enabled = true,
            keyword = "busy",
            responseMessage = "I am currently in focused mode via Chaty. I'll get back to you shortly!",
        ),
    ),
    val scheduledMessages: List<ScheduledMessageEntry> = emptyList(),
    val quickReplies: List<QuickReplyTemplate> = listOf(
        QuickReplyTemplate(
            shortcut = "#thanks",
            title = "Thank you",
            content = "Thank you so much! Really appreciate it.",
        ),
        QuickReplyTemplate(
            shortcut = "#eta",
            title = "ETA 5 mins",
            content = "On my way! Be there in 5 minutes.",
        ),
    ),
)

fun MessageAutomationPreferences.toMap(): Map<String, Any?> = mapOf(
    "enableAutoReply" to enableAutoReply,
    "autoReplyRules" to autoReplyRules.map { it.toMap() },
    "scheduledMessages" to scheduledMessages.map { it.toMap() },
    "quickReplies" to quickReplies.map { it.toMap() },
)

/** Dart fromMap asymmetry preserved: missing lists decode to empty, NOT to
 *  the const defaults. */
fun messageAutomationPreferencesFromMap(map: Map<String, Any?>): MessageAutomationPreferences =
    MessageAutomationPreferences(
        enableAutoReply = map["enableAutoReply"] as Boolean? ?: false,
        autoReplyRules = (map["autoReplyRules"] as List<*>?)
            ?.mapNotNull { (it as? Map<String, Any?>)?.let(AutoReplyRule::fromMap) }
            ?: emptyList(),
        scheduledMessages = (map["scheduledMessages"] as List<*>?)
            ?.mapNotNull { (it as? Map<String, Any?>)?.let(ScheduledMessageEntry::fromMap) }
            ?: emptyList(),
        quickReplies = (map["quickReplies"] as List<*>?)
            ?.mapNotNull { (it as? Map<String, Any?>)?.let(QuickReplyTemplate::fromMap) }
            ?: emptyList(),
    )
