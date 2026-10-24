import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/bubbles/bubble_style_id.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/gb/gb_theme_overrides.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';
import 'package:chat/ui/core/ticks/delivery_icon_style.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
  });

  test('a template or typed bubble/tick pick wins over an older GB bubble key', () {
    final prefs = ChatyPreferencesController();
    prefs.updateGbFeatures(<String, Object?>{'bubble_style': 'Telegram Arc'});
    // What a template apply or the typed picker writes afterwards.
    prefs.updateConversation(
      prefs.conversation.copyWith(bubbleStyle: '3D', tickStyle: 'Sticker'),
    );
    final resolved = GbThemeOverrides.resolve(ThemePresets.midnight, prefs);
    expect(resolved.bubbleStyle, BubbleStyleIdExtension.fromString('3D'));
    expect(resolved.deliveryTickStyle, DeliveryIconStyle.sticker);
  });

  test('the legacy GB key still drives bubbles when the typed value is empty', () {
    final prefs = ChatyPreferencesController();
    prefs.updateConversation(prefs.conversation.copyWith(bubbleStyle: ''));
    prefs.updateGbFeatures(<String, Object?>{'bubble_style': 'Telegram Arc'});
    final resolved = GbThemeOverrides.resolve(ThemePresets.midnight, prefs);
    expect(resolved.bubbleStyle, BubbleStyleId.telegram);
  });

  test('saved typed ids survive a save and restore round trip', () {
    final saved = const ConversationPreferences().copyWith(
      bubbleStyle: BubbleStyleId.telegram.displayName,
      tickStyle: DeliveryIconStyle.bbmV2.displayName,
    );
    final restored = ConversationPreferences.fromMap(saved.toMap());
    expect(BubbleStyleIdExtension.fromString(restored.bubbleStyle),
        BubbleStyleId.telegram);
    expect(DeliveryIconStyleExtension.fromString(restored.tickStyle),
        DeliveryIconStyle.bbmV2);
  });
}
