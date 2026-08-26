import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('attachment actions use one adaptive grid', () {
    final source = File(
      'lib/features/messages/attachment_sheet.dart',
    ).readAsStringSync();
    expect(source, isNot(contains('_OrbitGroup(')));
    expect(source, contains('final columns ='));
    expect(source, contains('MediaQuery.textScalerOf'));
  });

  test('emoji search uses Chaty metadata instead of raw ids only', () {
    final registry = File(
      'lib/core/emoji/emoji_registry.dart',
    ).readAsStringSync();
    final picker = File(
      'lib/core/emoji/widgets/chaty_emoji_picker.dart',
    ).readAsStringSync();
    expect(registry, contains('class ChatyEmojiEntry'));
    expect(registry, contains('aliases'));
    expect(registry, contains('keywords'));
    expect(picker, contains('entry.matches(q)'));
  });

  test('bubble and tick selectors require explicit apply', () {
    final source = File(
      'lib/features/settings/conversation/conversation_settings_page.dart',
    ).readAsStringSync();
    expect(
      RegExp('showApplyButton: true').allMatches(source).length,
      greaterThanOrEqualTo(2),
    );
  });
}
