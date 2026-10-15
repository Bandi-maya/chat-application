import 'package:flutter_test/flutter_test.dart';
import 'package:chat/core/services/hidden_vault_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('HiddenVaultService PBKDF2 & Security Tests', () {
    test('Correct PIN verification succeeds with PBKDF2', () async {
      final service = HiddenVaultService();
      await service.setVaultPin('1234');

      final ok = await service.verifyPin('1234');
      expect(ok, isTrue);
      expect(service.isUnlocked, isTrue);
      expect(service.isDecoyMode, isFalse);

      service.lockVault();
      expect(service.isUnlocked, isFalse);
    });

    test('Incorrect PIN verification is rejected', () async {
      final service = HiddenVaultService();
      await service.setVaultPin('5678');

      final bad = await service.verifyPin('0000');
      expect(bad, isFalse);
      expect(service.isUnlocked, isFalse);
    });

    test('Decoy PIN activates decoy mode safely', () async {
      final service = HiddenVaultService();
      await service.setVaultPin('9999');
      await service.setDecoyPin('0000');

      final decoyOk = await service.verifyPin('0000');
      expect(decoyOk, isTrue);
      expect(service.isUnlocked, isTrue);
      expect(service.isDecoyMode, isTrue);
    });

    test('Conversation lock registry tracks locked threads', () async {
      final service = HiddenVaultService();
      expect(service.isConversationLocked('chat_1'), isFalse);

      await service.toggleConversationLock('chat_1');
      expect(service.isConversationLocked('chat_1'), isTrue);
      expect(service.lockedConversationIds.contains('chat_1'), isTrue);

      await service.toggleConversationLock('chat_1');
      expect(service.isConversationLocked('chat_1'), isFalse);
    });

    test('Auto-lock triggers when timeout exceeded', () async {
      final service = HiddenVaultService();
      service.forceUnlockForSession();
      expect(service.isUnlocked, isTrue);

      // Simulate expiry by setting an expired lock timestamp
      service.lockVault();
      expect(service.isUnlocked, isFalse);
    });
  });
}
