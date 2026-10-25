package com.example.chat.ui.automation

import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.GbFeatureBackendService
import com.example.chat.domain.models.AutoReplyRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Exact port of lib/features/automation/auto_reply_rule_engine.dart.
 *
 *  Rule matching engine for automatic message responses. ChangeNotifier is
 *  ported as the same listener pattern used by ChatyPreferencesController.
 *
 *  Seams (Dart dependencies not owned by this area):
 *  - ChatyDataStore.sendMessage -> [sendReply]; the integration layer wires
 *    this to the shared message-send path (ApiBackendService /
 *    ConversationsRepository.sendMessage with MessageType.text).
 */
class AutoReplyRuleEngine(
    private val prefs: ChatyPreferencesController,
    private val backend: GbFeatureBackendService,
    private val scope: CoroutineScope,
    private val sendReply: suspend (conversationId: String, text: String) -> Unit = { _, _ -> },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    val isMasterEnabled: Boolean get() = prefs.automation.enableAutoReply
    val rules: List<AutoReplyRule> get() = prefs.automation.autoReplyRules

    /** Toggles master auto-reply on/off. */
    fun setMasterEnabled(enabled: Boolean) {
        val updated = prefs.automation.copy(enableAutoReply = enabled)
        prefs.updateAutomation(updated)
        scope.launch { runCatching { backend.synchronizeAutomation(updated) } }
        notifyListeners()
    }

    /** Evaluates whether an inbound message triggers any active auto-reply rule. */
    fun evaluate(messageText: String, isDirect: Boolean): AutoReplyRule? {
        if (!isMasterEnabled) return null

        val trimmed = messageText.trim().lowercase()
        if (trimmed.isEmpty()) return null

        for (rule in rules) {
            if (!rule.enabled) continue

            // Filter by recipient scope
            val filter = rule.recipientFilter.lowercase()
            if ((filter == "direct" || filter == "contacts") && !isDirect) continue
            if (filter == "group" && isDirect) continue

            val keyword = rule.keyword.trim().lowercase()
            if (keyword.isEmpty()) continue

            val matched = when (rule.matchType) {
                "exact" -> trimmed == keyword
                "starts_with" -> trimmed.startsWith(keyword)
                "contains" -> trimmed.contains(keyword)
                else -> trimmed.contains(keyword)
            }

            if (matched) {
                return rule
            }
        }
        return null
    }

    /** Evaluates an incoming message and sends the auto-reply if matched. */
    suspend fun processInboundMessage(
        conversationId: String,
        messageText: String,
        isDirect: Boolean,
    ): Boolean {
        val matchedRule = evaluate(messageText = messageText, isDirect = isDirect)
            ?: return false

        // Send the configured auto-reply message
        sendReply(conversationId, matchedRule.responseMessage)
        return true
    }

    /** Adds a new auto-reply rule. */
    fun addRule(
        keyword: String,
        responseMessage: String,
        matchType: String = "contains",
        recipientFilter: String = "all",
    ) {
        val currentRules = rules.toMutableList()
        currentRules.add(
            AutoReplyRule(
                id = "rule_${now()}_${currentRules.size}",
                keyword = keyword.trim(),
                responseMessage = responseMessage.trim(),
                matchType = matchType,
                recipientFilter = recipientFilter,
                enabled = true,
            ),
        )

        val updated = prefs.automation.copy(autoReplyRules = currentRules)
        prefs.updateAutomation(updated)
        scope.launch { runCatching { backend.synchronizeAutomation(updated) } }
        notifyListeners()
    }

    /** Toggles an individual rule enabled/disabled. */
    fun toggleRule(index: Int, enabled: Boolean) {
        if (index < 0 || index >= rules.size) return
        val currentRules = rules.toMutableList()
        val target = currentRules[index]
        currentRules[index] = target.copy(enabled = enabled)

        val updated = prefs.automation.copy(autoReplyRules = currentRules)
        prefs.updateAutomation(updated)
        scope.launch { runCatching { backend.synchronizeAutomation(updated) } }
        notifyListeners()
    }

    /** Deletes a rule at [index]. */
    fun deleteRule(index: Int) {
        if (index < 0 || index >= rules.size) return
        val currentRules = rules.toMutableList()
        val removed = currentRules.removeAt(index)

        val updated = prefs.automation.copy(autoReplyRules = currentRules)
        prefs.updateAutomation(updated)
        scope.launch {
            runCatching {
                backend.deleteAutoReplyRuleBySignature(removed.keyword, removed.responseMessage)
            }
        }
        notifyListeners()
    }
}
