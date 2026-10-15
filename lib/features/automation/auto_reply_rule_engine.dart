import 'dart:async';
import 'package:flutter/foundation.dart';
import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/gb_feature_backend_service.dart';
import '../../domain/models/chat_message.dart';
import '../../domain/models/preferences.dart';
import '../../ui/core/controllers/preferences_controller.dart';

/// Rule matching engine for automatic message responses.
class AutoReplyRuleEngine extends ChangeNotifier {
  AutoReplyRuleEngine({
    required ChatyPreferencesController preferencesController,
    GbFeatureBackendService? backendService,
  })  : _prefs = preferencesController,
        _backend = backendService ?? GbFeatureBackendService();

  final ChatyPreferencesController _prefs;
  final GbFeatureBackendService _backend;

  bool get isMasterEnabled => _prefs.automation.enableAutoReply;
  List<AutoReplyRule> get rules => _prefs.automation.autoReplyRules;

  /// Toggles master auto-reply on/off.
  void setMasterEnabled(bool enabled) {
    final updated = _prefs.automation.copyWith(enableAutoReply: enabled);
    _prefs.updateAutomation(updated);
    unawaited(_backend.synchronizeAutomation(updated));
    notifyListeners();
  }

  /// Evaluates whether an inbound message triggers any active auto-reply rule.
  AutoReplyRule? evaluate({
    required String messageText,
    required bool isDirect,
  }) {
    if (!isMasterEnabled) return null;

    final trimmed = messageText.trim().toLowerCase();
    if (trimmed.isEmpty) return null;

    for (final rule in rules) {
      if (!rule.enabled) continue;

      // Filter by recipient scope
      final filter = rule.recipientFilter.toLowerCase();
      if ((filter == 'direct' || filter == 'contacts') && !isDirect) continue;
      if (filter == 'group' && isDirect) continue;

      final keyword = rule.keyword.trim().toLowerCase();
      if (keyword.isEmpty) continue;

      final matched = switch (rule.matchType) {
        'exact' => trimmed == keyword,
        'starts_with' => trimmed.startsWith(keyword),
        'contains' => trimmed.contains(keyword),
        _ => trimmed.contains(keyword),
      };

      if (matched) {
        return rule;
      }
    }
    return null;
  }

  /// Evaluates an incoming message and sends the auto-reply if matched.
  Future<bool> processInboundMessage({
    required String conversationId,
    required String messageText,
    required bool isDirect,
    required ChatyDataStore dataStore,
  }) async {
    final matchedRule = evaluate(
      messageText: messageText,
      isDirect: isDirect,
    );

    if (matchedRule == null) return false;

    // Send the configured auto-reply message
    await dataStore.sendMessage(
      conversationId: conversationId,
      text: matchedRule.responseMessage,
      type: MessageType.text,
    );
    return true;
  }

  /// Adds a new auto-reply rule.
  void addRule({
    required String keyword,
    required String responseMessage,
    String matchType = 'contains',
    String recipientFilter = 'all',
  }) {
    final currentRules = List<AutoReplyRule>.from(rules);
    currentRules.add(
      AutoReplyRule(
        id: 'rule_${DateTime.now().millisecondsSinceEpoch}_${currentRules.length}',
        keyword: keyword.trim(),
        responseMessage: responseMessage.trim(),
        matchType: matchType,
        recipientFilter: recipientFilter,
        enabled: true,
      ),
    );

    final updated = _prefs.automation.copyWith(autoReplyRules: currentRules);
    _prefs.updateAutomation(updated);
    unawaited(_backend.synchronizeAutomation(updated));
    notifyListeners();
  }

  /// Toggles an individual rule enabled/disabled.
  void toggleRule(int index, bool enabled) {
    if (index < 0 || index >= rules.length) return;
    final currentRules = List<AutoReplyRule>.from(rules);
    final target = currentRules[index];
    currentRules[index] = target.copyWith(enabled: enabled);

    final updated = _prefs.automation.copyWith(autoReplyRules: currentRules);
    _prefs.updateAutomation(updated);
    unawaited(_backend.synchronizeAutomation(updated));
    notifyListeners();
  }

  /// Deletes a rule at [index].
  void deleteRule(int index) {
    if (index < 0 || index >= rules.length) return;
    final currentRules = List<AutoReplyRule>.from(rules);
    final removed = currentRules.removeAt(index);

    final updated = _prefs.automation.copyWith(autoReplyRules: currentRules);
    _prefs.updateAutomation(updated);
    unawaited(
      _backend.deleteAutoReplyRuleBySignature(
        removed.keyword,
        removed.responseMessage,
      ),
    );
    notifyListeners();
  }
}
