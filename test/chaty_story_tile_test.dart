import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/design_system/components/chaty_story_tile.dart';

Widget _strip(Brightness b, String style, Color accent) {
  final dark = b == Brightness.dark;
  final bg = dark ? const Color(0xFF0B0B0C) : Colors.white;
  final look = chatyStoryLookFor(style);
  final names = ['Asha', 'Ravi', 'Meera', 'Kiran'];
  final colors = [
    const Color(0xFF6C63FF),
    const Color(0xFFE57373),
    const Color(0xFF4DB6AC),
    const Color(0xFFFFB74D),
  ];
  return Container(
    color: bg,
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 90,
          height: chatyStoryStripHeight(look, 82),
          child: Center(
            child: Text(
              style,
              style: TextStyle(
                color: dark ? Colors.white : Colors.black,
                fontSize: 11,
              ),
            ),
          ),
        ),
        Expanded(
          child: SizedBox(
          height: chatyStoryStripHeight(look, 82),
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: [
              for (var i = 0; i < 4; i++)
                Padding(
                  padding: const EdgeInsets.only(right: 10),
                  child: ChatyStoryTile(
                    look: look,
                    initials: names[i].substring(0, 1),
                    avatarColor: colors[i],
                    name: names[i],
                    online: i == 1,
                    avatarSize: 48,
                    baseWidth: 58,
                    nameFontSize: 10.5,
                    accent: accent,
                    cardColor: dark ? const Color(0xFF1A1A1C) : const Color(0xFFF4F6F6),
                    borderColor: dark ? const Color(0xFF2A2A2D) : const Color(0xFFE3E6E6),
                    nameColor: dark ? Colors.white70 : Colors.black54,
                    onlineColor: const Color(0xFF2ECC71),
                    backgroundColor: bg,
                  ),
                ),
            ],
          ),
          ),
        ),
      ],
    ),
  );
}

void main() {
  const styles = [
    'Circular',
    'Instagram',
    'Stock',
    'Facebook',
    'Squircle',
    'Card',
    'Wide Card',
  ];

  test('every stories style maps to its own look', () {
    final looks = styles.map(chatyStoryLookFor).toSet();
    expect(looks.length, styles.length);
  });

  test('tall and wide looks grow the strip, others keep the base', () {
    expect(chatyStoryStripHeight(ChatyStoryLook.facebook, 82), 118);
    expect(chatyStoryStripHeight(ChatyStoryLook.wideCard, 82), 82);
    expect(chatyStoryStripHeight(ChatyStoryLook.circular, 82), 82);
  });

  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('renders all story looks ${b.name}', (tester) async {
      tester.view.physicalSize = const Size(900, 2000);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final accent = b == Brightness.dark ? const Color(0xFF00C49A) : const Color(0xFF00A884);
      final key = GlobalKey();
      await tester.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: MediaQuery(
            data: const MediaQueryData(size: Size(450, 1000)),
            child: RepaintBoundary(
              key: key,
              child: Material(
                color: b == Brightness.dark ? const Color(0xFF0B0B0C) : Colors.white,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [for (final s in styles) _strip(b, s, accent)],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      expect(find.byKey(const ValueKey<String>('story-portrait-card')), findsWidgets);
      expect(find.byKey(const ValueKey<String>('story-wide-card')), findsWidgets);
      await tester.runAsync(() async {
        final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 2);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/story_looks')..createSync(recursive: true);
        File('${dir.path}/story_looks_${b.name}.png').writeAsBytesSync(data!.buffer.asUint8List());
      });
    });
  }
}
