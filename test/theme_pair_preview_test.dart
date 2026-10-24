import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/templates/applied_preview_model.dart';
import 'package:chat/ui/core/templates/template_models.dart';
import 'package:chat/ui/core/templates/template_registry.dart';
import 'package:chat/ui/core/theme/theme_pair_preview.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';

void main() {
  testWidgets('theme pair preview shows home and conversation from the theme tokens',
      (tester) async {
    final presets = ThemePresets.all;
    final light = presets.firstWhere((p) => p.brightness == Brightness.light);
    final dark = presets.firstWhere((p) => p.brightness == Brightness.dark);
    for (final preset in [light, dark]) {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: Center(child: ThemePairPreview(themeConfig: preset))),
        ),
      );
      expect(find.text('Home'), findsOneWidget);
      expect(find.text('Conversation'), findsOneWidget);
      expect(find.text('Chats'), findsOneWidget);
      expect(find.text('Sounds good'), findsOneWidget);
      final frames = tester
          .widgetList<Container>(find.byType(Container))
          .where((c) =>
              c.decoration is BoxDecoration &&
              (c.decoration as BoxDecoration).color == preset.backgroundColor);
      expect(frames.length, greaterThanOrEqualTo(2),
          reason: 'both phones use ${preset.name} background token');
    }
  });

  test('applied preview model uses the app precedence and apply merge', () {
    final palette = ThemePresets.all.first;
    // A component override wins over the base template for that component.
    final config = UserTemplateConfiguration(
      baseTemplate: ChatyTemplateId.messageFirst,
      componentOverrides: const {
        TemplateComponentType.composer: ChatyTemplateId.powerChat,
      },
    );
    final model = AppliedPreviewModel.resolve(
      palette: palette,
      config: config,
      applyTemplateConversation: true,
    );
    final power = ChatyTemplateRegistry.get(ChatyTemplateId.powerChat);
    final base = ChatyTemplateRegistry.get(ChatyTemplateId.messageFirst);
    expect(model.layout.composer.actionPlacement, power.composer.actionPlacement);
    expect(model.layout.home.headerStyle, base.home.headerStyle);
    expect(model.layout.chatList.density, base.chatList.density);
    // Apply writes only the conversation fields; colours are preserved.
    expect(model.theme.accentColor, palette.accentColor);
    expect(model.theme.backgroundColor, palette.backgroundColor);
    expect(model.theme.cornerRadius, base.conversation.bubbleCornerRadius);
    expect(model.theme.tickStyle, base.conversation.tickStyle);
    // When the state is already active the palette is shown untouched.
    final active = AppliedPreviewModel.resolve(
      palette: palette,
      config: config,
      applyTemplateConversation: false,
    );
    expect(active.theme.cornerRadius, palette.cornerRadius);
  });
}
