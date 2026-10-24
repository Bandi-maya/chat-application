import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
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
}
