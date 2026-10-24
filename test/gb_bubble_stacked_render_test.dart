// Stacked-message render harness: verifies that tall/tail-heavy GB bubble
// styles never paint over adjacent messages or timestamp rows. Mimics the
// production layout contract (message_bubble.dart): CustomPaint inside a
// Container margined/padded by BubbleStyleRegistry geometry.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/bubbles/bubble_painter.dart';
import 'package:chat/ui/core/bubbles/bubble_style_id.dart';
import 'package:chat/ui/core/bubbles/bubble_style_registry.dart';

Future<void> _loadFont(String family, String file) async {
  final root = Platform.environment['FLUTTER_ROOT'] ?? '';
  final f = File('$root/bin/cache/artifacts/material_fonts/$file');
  if (!f.existsSync()) return;
  final bytes = await f.readAsBytes();
  final loader = FontLoader(family)
    ..addFont(
      Future<ByteData>.value(ByteData.sublistView(Uint8List.fromList(bytes))),
    );
  await loader.load();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await _loadFont('Roboto', 'Roboto-Regular.ttf');
  });

  const tallAndTailed = [
    BubbleStyleId.rcPal,
    BubbleStyleId.rcImess,
    BubbleStyleId.rcImLine,
    BubbleStyleId.rcTranLine,
    BubbleStyleId.rcIosLine,
    BubbleStyleId.gabiMess,
    BubbleStyleId.textra,
    BubbleStyleId.goSms,
    BubbleStyleId.rcGoSms,
    BubbleStyleId.rcFancy,
    BubbleStyleId.cvIos,
  ];

  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('gb stacked bubbles ${b.name}', (tester) async {
      tester.view.physicalSize = const Size(720, 8200);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final key = GlobalKey();
      final dark = b == Brightness.dark;
      final bubbleIn = dark ? const Color(0xFF202C33) : const Color(0xFFFFFFFF);
      final bubbleOut = dark ? const Color(0xFF005C4B) : const Color(0xFFD9FDD3);
      final textColor = dark ? Colors.white : Colors.black87;

      Widget bubble(BubbleStyleId id, bool isMe, String text) {
        final geo = BubbleStyleRegistry.getGeometry(id);
        return Align(
          alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
          child: Container(
            margin: geo.bubbleMargin,
            child: CustomPaint(
              painter: BubblePainter(
                styleId: id,
                isMe: isMe,
                fillColor: isMe ? bubbleOut : bubbleIn,
                strokeColor: const Color(0xFF00A884),
                accentColor: const Color(0xFF00A884),
              ),
              child: Padding(
                padding: geo.contentPadding,
                child: Text(text, style: TextStyle(fontSize: 13, color: textColor)),
              ),
            ),
          ),
        );
      }

      Widget timestamp(bool isMe, String t) => Align(
        alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text(t, style: TextStyle(fontSize: 10, color: textColor.withValues(alpha: 0.6))),
        ),
      );

      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: ThemeData(brightness: b),
            home: Scaffold(
              backgroundColor: dark ? const Color(0xFF0B141A) : const Color(0xFFECE5DD),
              body: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final id in tallAndTailed) ...[
                    Padding(
                      padding: const EdgeInsets.fromLTRB(10, 8, 10, 0),
                      child: Text(id.displayName, style: TextStyle(fontSize: 11, color: textColor)),
                    ),
                    // short single-line pair
                    bubble(id, false, 'Hi'),
                    timestamp(false, '13:41'),
                    bubble(id, true, 'Hey!'),
                    timestamp(true, '13:41  read'),
                    // multiline pair stacked back to back
                    bubble(id, false, 'A longer message that wraps onto a second line to stress the geometry'),
                    bubble(id, true, 'Reply with multiple lines of text so the bubble grows tall and the dome has real headroom to draw into'),
                    timestamp(true, '13:42  read'),
                  ],
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      expect(tester.takeException(), isNull);
      await tester.runAsync(() async {
        final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/ui_renders')..createSync(recursive: true);
        File('${dir.path}/gb_bubbles_stacked_${b.name}.png').writeAsBytesSync(data!.buffer.asUint8List());
      });
    });
  }
}
