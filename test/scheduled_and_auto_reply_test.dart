import 'package:flutter_test/flutter_test.dart';
import 'package:chat/data/services/gb_feature_backend_service.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/features/automation/auto_reply_rule_engine.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('AutoReplyRuleEngine & Message Automation Tests', () {
    late ChatyPreferencesController prefsController;
    late AutoReplyRuleEngine engine;

    setUp(() {
      prefsController = ChatyPreferencesController();
      // Configure default automation state
      final automation = prefsController.automation.copyWith(
        enableAutoReply: true,
        autoReplyRules: [
          const AutoReplyRule(
            id: 'rule_1',
            keyword: 'hello',
            responseMessage: 'Hi there! How can I help you?',
            matchType: 'contains',
            recipientFilter: 'all',
            enabled: true,
          ),
          const AutoReplyRule(
            id: 'rule_2',
            keyword: 'status',
            responseMessage: 'All systems are operating normally.',
            matchType: 'exact',
            recipientFilter: 'direct',
            enabled: true,
          ),
          const AutoReplyRule(
            id: 'rule_3',
            keyword: 'urgent',
            responseMessage: 'Please call me directly for emergencies.',
            matchType: 'starts_with',
            recipientFilter: 'group',
            enabled: true,
          ),
        ],
      );
      prefsController.updateAutomation(automation);
      engine = AutoReplyRuleEngine(
        preferencesController: prefsController,
        backendService: GbFeatureBackendService(),
      );
    });

    test('Engine matches contains rule correctly', () {
      final match = engine.evaluate(
        messageText: 'Well hello there friend',
        isDirect: true,
      );
      expect(match, isNotNull);
      expect(match?.id, equals('rule_1'));
      expect(match?.responseMessage, equals('Hi there! How can I help you?'));
    });

    test('Engine matches exact rule and rejects partial matches', () {
      // Partial match on exact rule should fail
      final partialMatch = engine.evaluate(
        messageText: 'What is the status now?',
        isDirect: true,
      );
      expect(partialMatch, isNull);

      // Exact match should succeed
      final exactMatch = engine.evaluate(
        messageText: 'status',
        isDirect: true,
      );
      expect(exactMatch, isNotNull);
      expect(exactMatch?.id, equals('rule_2'));
    });

    test('Engine respects recipient scope filtering (direct vs group)', () {
      // rule_2 has recipientFilter: 'direct'
      final inGroup = engine.evaluate(
        messageText: 'status',
        isDirect: false, // In a group chat
      );
      expect(inGroup, isNull);

      // rule_3 has recipientFilter: 'group'
      final directUrgent = engine.evaluate(
        messageText: 'urgent attention needed',
        isDirect: true, // In direct chat
      );
      expect(directUrgent, isNull);

      final groupUrgent = engine.evaluate(
        messageText: 'urgent attention needed',
        isDirect: false, // In group chat
      );
      expect(groupUrgent, isNotNull);
      expect(groupUrgent?.id, equals('rule_3'));
    });

    test('Master disabled toggle disables all rule evaluation', () {
      engine.setMasterEnabled(false);
      expect(engine.isMasterEnabled, isFalse);

      final match = engine.evaluate(
        messageText: 'hello',
        isDirect: true,
      );
      expect(match, isNull);
    });

    test('Engine rule modification lifecycle (add, toggle, delete)', () {
      final initialCount = engine.rules.length;

      // Add a rule
      engine.addRule(
        keyword: 'pricing',
        responseMessage: 'Check our pricing at chaty.app/pricing',
        matchType: 'contains',
        recipientFilter: 'all',
      );
      expect(engine.rules.length, equals(initialCount + 1));
      expect(engine.rules.last.keyword, equals('pricing'));

      // Toggle rule disabled
      final newIndex = engine.rules.length - 1;
      engine.toggleRule(newIndex, false);
      expect(engine.rules[newIndex].enabled, isFalse);

      // Disabled rule does not match
      final matchDisabled = engine.evaluate(
        messageText: 'What is your pricing?',
        isDirect: true,
      );
      expect(matchDisabled, isNull);

      // Delete rule
      engine.deleteRule(newIndex);
      expect(engine.rules.length, equals(initialCount));
    });

    test('ServerScheduledMessage model maps fields accurately', () {
      final now = DateTime.now();
      final msg = ServerScheduledMessage(
        id: 'sched_123',
        conversationId: 'conv_456',
        body: 'Scheduled reminder text',
        scheduledAt: now,
        state: 'pending',
      );

      expect(msg.id, equals('sched_123'));
      expect(msg.conversationId, equals('conv_456'));
      expect(msg.body, equals('Scheduled reminder text'));
      expect(msg.scheduledAt, equals(now));
      expect(msg.state, equals('pending'));
      expect(msg.executedAt, isNull);
      expect(msg.lastError, isNull);
    });
  });
}
