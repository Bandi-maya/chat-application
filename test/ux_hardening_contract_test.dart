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

  test('emoji picker exposes one Unicode picker without duplicate animated tab', () {
    final picker = File(
      'lib/core/emoji/widgets/chaty_emoji_picker.dart',
    ).readAsStringSync();
    expect(picker, contains('EmojiPicker('));
    expect(picker, contains('_buildUnicodePicker(context, height)'));
    expect(picker, isNot(contains('_buildAnimatedTab')));
    expect(picker, isNot(contains('TabController(length: 2')));
  });

  test('bubble and tick selectors require explicit apply', () {
    final bubbleScreen = File(
      'lib/features/settings/conversation/conversation_bubble_and_ticks_screen.dart',
    );
    final source = bubbleScreen.existsSync()
        ? bubbleScreen.readAsStringSync()
        : File(
            'lib/features/settings/conversation/conversation_settings_page.dart',
          ).readAsStringSync();
    expect(
      RegExp('showApplyButton: true').allMatches(source).length,
      greaterThanOrEqualTo(2),
    );
  });
}
