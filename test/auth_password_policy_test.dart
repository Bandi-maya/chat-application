import 'package:flutter_test/flutter_test.dart';

import 'package:chat/ui/core/validators/input_validators.dart';

void main() {
  group('Chaty production password policy', () {
    test('accepts a password meeting every requirement', () {
      expect(ChatyValidators.validatePassword('Aa1!aaaaaaaa'), isNull);
    });

    test('rejects passwords shorter than the production minimum', () {
      expect(
        ChatyValidators.validatePassword('Aa1!short'),
        contains('${ChatyValidators.minPasswordLength}'),
      );
    });

    test('requires lowercase, uppercase, number and symbol', () {
      expect(
        ChatyValidators.validatePassword('AA1!AAAAAAAA'),
        contains('lowercase'),
      );
      expect(
        ChatyValidators.validatePassword('aa1!aaaaaaaa'),
        contains('uppercase'),
      );
      expect(
        ChatyValidators.validatePassword('Aa!!aaaaaaaa'),
        contains('number'),
      );
      expect(
        ChatyValidators.validatePassword('Aa11aaaaaaaa'),
        contains('symbol'),
      );
    });

    test('accepts the maximum supported length', () {
      final password = 'Aa1!${List<String>.filled(124, 'a').join()}';
      expect(password.length, ChatyValidators.maxPasswordLength);
      expect(ChatyValidators.validatePassword(password), isNull);
    });

    test('rejects passwords above the maximum supported length', () {
      final password = 'Aa1!${List<String>.filled(125, 'a').join()}';
      expect(
        ChatyValidators.validatePassword(password),
        contains('${ChatyValidators.maxPasswordLength}'),
      );
    });
  });
}
