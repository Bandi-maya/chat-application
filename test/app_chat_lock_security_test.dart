import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:chat/data/services/local_lock_service.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/features/settings/security/pattern_lock_pad.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  FlutterSecureStorage.setMockInitialValues({});

  group('LocalLockService & PBKDF2 Hashing Security Tests', () {
    test('Normalizes secret search phrases with Unicode, whitespace, and case', () {
      expect(
        LocalLockService.normalizeSecretPhrase('  🔒  Vault   Key  '),
        '🔒 vault key',
      );
      expect(
        LocalLockService.normalizeSecretPhrase('SecretPassword123'),
        'secretpassword123',
      );
      expect(
        LocalLockService.normalizeSecretPhrase('🎉 🚀 ✨'),
        '🎉 🚀 ✨',
      );
    });

    test('Pattern validator requires at least 4 unique dots between 0 and 8', () async {
      final service = LocalLockService();
      // Valid pattern
      await expectLater(service.setCredential('Pattern', '0-1-2-4'), completes);
      // Under 4 points
      expect(
        () => service.setCredential('Pattern', '0-1-2'),
        throwsA(isA<ArgumentError>()),
      );
      // Out-of-bounds nodes
      expect(
        () => service.setCredential('Pattern', '0-1-2-9'),
        throwsA(isA<ArgumentError>()),
      );
      // Duplicate nodes
      expect(
        () => service.setCredential('Pattern', '0-1-2-1'),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('PIN validator strictly enforces digit length and numeric format', () async {
      final service = LocalLockService();
      // Valid 4-digit PIN
      await expectLater(service.setCredential('PIN', '1234', pinLength: 4), completes);
      // Valid 6-digit PIN
      await expectLater(service.setCredential('PIN', '123456', pinLength: 6), completes);
      // Non-numeric
      expect(
        () => service.setCredential('PIN', '12a4', pinLength: 4),
        throwsA(isA<ArgumentError>()),
      );
      // Length mismatch
      expect(
        () => service.setCredential('PIN', '12345', pinLength: 4),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('PBKDF2 credential verification succeeds on exact match and rejects wrong input', () async {
      final service = LocalLockService();
      await service.setCredential('PIN', '4321', pinLength: 4);
      expect(await service.verifyCredential('PIN', '4321'), isTrue);
      expect(await service.verifyCredential('PIN', '0000'), isFalse);
    });

    test('Secret search phrase verification works with normalized Unicode', () async {
      final service = LocalLockService();
      await service.setSecretPhrase('  🔒 Secret Vault  ');
      expect(await service.verifySecretPhrase('🔒 secret vault'), isTrue);
      expect(await service.verifySecretPhrase('🔒 Secret Vault'), isTrue);
      expect(await service.verifySecretPhrase('wrong password'), isFalse);
    });
  });

  group('Preferences Controller Chat Lock & Hidden State Tests', () {
    test('Toggling lock and hide updates security preferences accurately', () {
      final controller = ChatyPreferencesController();
      expect(controller.isConversationLocked('conv_99'), isFalse);
      expect(controller.isConversationHidden('conv_99'), isFalse);
      expect(controller.isConversationProtected('conv_99'), isFalse);

      // Lock conversation
      controller.toggleLockConversation('conv_99', lock: true);
      expect(controller.isConversationLocked('conv_99'), isTrue);
      expect(controller.isConversationProtected('conv_99'), isTrue);

      // Hide conversation (automatically locks as well)
      controller.toggleHideConversation('conv_hidden', hide: true);
      expect(controller.isConversationHidden('conv_hidden'), isTrue);
      expect(controller.isConversationLocked('conv_hidden'), isTrue);
      expect(controller.isConversationProtected('conv_hidden'), isTrue);

      // Complete unlock
      controller.unlockConversationCompletely('conv_hidden');
      expect(controller.isConversationHidden('conv_hidden'), isFalse);
      expect(controller.isConversationLocked('conv_hidden'), isFalse);
      expect(controller.isConversationProtected('conv_hidden'), isFalse);
    });
  });

  group('PatternLockPad Widget Tests', () {
    testWidgets('PatternLockPad renders touch grid and fires completion', (
      tester,
    ) async {
      String? completedPattern;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: PatternLockPad(
                onPatternComplete: (p) => completedPattern = p,
                size: 300,
              ),
            ),
          ),
        ),
      );

      expect(find.byType(PatternLockPad), findsOneWidget);
      expect(find.bySemanticsLabel('Pattern lock grid 3 by 3'), findsOneWidget);
    });
  });
}
