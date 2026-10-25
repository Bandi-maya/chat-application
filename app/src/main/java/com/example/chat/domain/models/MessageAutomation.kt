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
)

data class QuickReplyTemplate(
    val shortcut: String,
    val title: String,
    val content: String,
)

data class ScheduledMessageEntry(
    val id: String,
    val recipientId: String,
    val recipientName: String,
    val text: String,
    val scheduledAtMillis: Long,
    val isExecuted: Boolean = false,
)

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
