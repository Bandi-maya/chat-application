import 'package:chat/ui/core/notifications/notification_icon_preset.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Chaty notification icon presets', () {
    test('every visible notification style has a unique label and renderer', () {
      final presets = ChatyNotificationIconPresets.values;
      expect(presets.length, 28);
      expect(presets.map((preset) => preset.name).toSet().length, presets.length);
      for (final preset in presets) {
        expect(preset.name.trim(), isNotEmpty);
        expect(preset.icon, isNotNull);
        expect(preset.color, isNotNull);
        expect(
          ChatyNotificationIconPresets.forName(preset.name),
          same(preset),
        );
      }
    });

    test('unknown persisted labels safely fall back to the first available style', () {
      expect(
        ChatyNotificationIconPresets.forName('legacy-notification-icon'),
        same(ChatyNotificationIconPresets.values.first),
      );
    });
  });
}
