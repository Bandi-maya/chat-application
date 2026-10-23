import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/bubbles/bubble_style_id.dart';

void main() {
  test('every bubble display name round-trips to the same id', () {
    final names = <String>{};
    for (final id in BubbleStyleId.values) {
      expect(BubbleStyleIdExtension.fromString(id.displayName), id,
          reason: id.displayName);
      names.add(id.displayName);
    }
    expect(names.length, BubbleStyleId.values.length,
        reason: 'display names must be unique so the picker value is unambiguous');
  });

  test('legacy picker labels still resolve and Stock is the default', () {
    expect(BubbleStyleIdExtension.fromString('Stock'), BubbleStyleId.stock);
    expect(BubbleStyleIdExtension.fromString(null), BubbleStyleId.stock);
    expect(BubbleStyleIdExtension.fromString('WhatsApp Default'), BubbleStyleId.stock);
    expect(BubbleStyleIdExtension.fromString('Telegram Arc'), BubbleStyleId.telegram);
  });
}
