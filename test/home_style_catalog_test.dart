import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/settings/home_style_catalog.dart';

void main() {
  group('Chaty home style catalog', () {
    test('offers the requested platform-inspired styles by readable names', () {
      expect(ChatyHomeStyleCatalog.options, contains('One UI'));
      expect(ChatyHomeStyleCatalog.options, contains('Telegram'));
      expect(ChatyHomeStyleCatalog.options, contains('iOS'));

      expect(
        ChatyHomeStyleCatalog.structuralStyle(
          ChatyHomeStyleCatalog.storedValue('One UI'),
        ),
        ChatyHomeStyleCatalog.oneUi,
      );
      expect(
        ChatyHomeStyleCatalog.structuralStyle(
          ChatyHomeStyleCatalog.storedValue('Telegram'),
        ),
        ChatyHomeStyleCatalog.telegram,
      );
      expect(
        ChatyHomeStyleCatalog.structuralStyle(
          ChatyHomeStyleCatalog.storedValue('iOS'),
        ),
        ChatyHomeStyleCatalog.ios,
      );
    });

    test('migrates legacy labels without breaking saved preferences', () {
      expect(ChatyHomeStyleCatalog.displayName('ONE UI'), 'One UI');
      expect(
        ChatyHomeStyleCatalog.displayName('IOS STYLE'),
        'iOS',
      );
      expect(
        ChatyHomeStyleCatalog.displayName('BUBBLES TAB STYLE'),
        'Bubbles Tab Style',
      );
      expect(
        ChatyHomeStyleCatalog.structuralStyle('WhatsApp UI Stock'),
        'Classic',
      );
      expect(
        ChatyHomeStyleCatalog.structuralStyle('WhatsApp OLD UI'),
        ChatyHomeStyleCatalog.defaultStyle,
      );
    });

    test('every selectable style resolves to a non-empty renderer style', () {
      for (final option in ChatyHomeStyleCatalog.options) {
        final stored = ChatyHomeStyleCatalog.storedValue(option);
        expect(
          ChatyHomeStyleCatalog.structuralStyle(stored).isNotEmpty,
          isTrue,
          reason: 'Style "$option" must resolve to a renderer style.',
        );
      }
    });
  });
}
