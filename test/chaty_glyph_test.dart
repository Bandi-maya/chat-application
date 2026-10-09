import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/design_system/components/chaty_glyph.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('original vector glyphs render at different accessible sizes', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              for (final glyph in ChatyGlyph.values)
                Tooltip(
                  message: glyph.name,
                  child: ChatyGlyphIcon(
                    key: ValueKey<ChatyGlyph>(glyph),
                    glyph: glyph,
                    size: 24,
                    color: Colors.black,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(ChatyGlyphIcon), findsNWidgets(ChatyGlyph.values.length));
    for (final glyph in ChatyGlyph.values) {
      expect(
        tester.getSize(find.byKey(ValueKey<ChatyGlyph>(glyph))),
        const Size(24, 24),
      );
    }
  });

  testWidgets('glyph painter respects its requested large display size', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Center(
            child: ChatyGlyphIcon(
              glyph: ChatyGlyph.templates,
              size: 40,
              color: Colors.blue,
            ),
          ),
        ),
      ),
    );
    expect(
      tester.getSize(find.byType(ChatyGlyphIcon)),
      const Size(40, 40),
    );
  });
}
