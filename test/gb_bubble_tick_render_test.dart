// Render harness for the GB bubble + tick catalogs (test render, not a
// device screenshot). Produces labeled grids for visual verification.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/domain/models/chat_message.dart';
import 'package:chat/ui/core/bubbles/bubble_painter.dart';
import 'package:chat/ui/core/bubbles/bubble_style_id.dart';
import 'package:chat/ui/core/ticks/delivery_icon_style.dart';
import 'package:chat/ui/core/ticks/delivery_status_icon.dart';

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
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
  });

  Widget labeled(String label, Widget child) => Padding(
    padding: const EdgeInsets.fromLTRB(10, 8, 10, 0),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(label, style: const TextStyle(fontSize: 11)),
        const SizedBox(height: 3),
        child,
      ],
    ),
  );

  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('gb bubbles+ticks ${b.name}', (tester) async {
      tester.view.physicalSize = const Size(720, 11000);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final key = GlobalKey();
      final dark = b == Brightness.dark;
      final bubbleIn = dark ? const Color(0xFF202C33) : const Color(0xFFFFFFFF);
      final bubbleOut = dark
          ? const Color(0xFF005C4B)
          : const Color(0xFFD9FDD3);

      final newBubbles = [
        BubbleStyleId.textra,
        BubbleStyleId.goSms,
        BubbleStyleId.cvIos,
        BubbleStyleId.rcPal,
        BubbleStyleId.rcIosLine,
        BubbleStyleId.rcImLine,
        BubbleStyleId.rcGoSms,
        BubbleStyleId.rcTranLine,
        BubbleStyleId.rcImess,
        BubbleStyleId.gabiMess,
        BubbleStyleId.rcFancy,
      ];

      final tickGrid = <Widget>[
        for (var i = 0; i < DeliveryIconStyle.values.length; i += 6)
          Row(
            children: [
              for (final id in DeliveryIconStyle.values.skip(i).take(6))
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox(
                        width: 38,
                        height: 22,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            SizedBox(
                              width: 16,
                              height: 16,
                              child: DeliveryStatusIcon(
                                style: id,
                                state: DeliveryState.delivered,
                                unreadColor: dark
                                    ? Colors.grey
                                    : Colors.black54,
                                readColor: const Color(0xFF53BDEB),
                              ),
                            ),
                            const SizedBox(width: 4),
                            SizedBox(
                              width: 16,
                              height: 16,
                              child: DeliveryStatusIcon(
                                style: id,
                                state: DeliveryState.read,
                                unreadColor: dark
                                    ? Colors.grey
                                    : Colors.black54,
                                readColor: const Color(0xFF53BDEB),
                              ),
                            ),
                          ],
                        ),
                      ),
                      Text(
                        id.displayName,
                        style: const TextStyle(fontSize: 8),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
            ],
          ),
      ];

      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: ThemeData(brightness: b),
            home: DefaultTextStyle(
              style: TextStyle(
                fontSize: 13,
                color: dark ? Colors.white : Colors.black87,
              ),
              child: Scaffold(
              backgroundColor: dark
                  ? const Color(0xFF0B141A)
                  : const Color(0xFFECE5DD),
              body: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Padding(
                    padding: EdgeInsets.all(10),
                    child: Text(
                      'GB bubbles (11 new), outgoing/incoming',
                      style: TextStyle(fontSize: 13),
                    ),
                  ),
                  for (final id in newBubbles)
                    labeled(
                      id.displayName,
                      Row(
                        children: [
                          CustomPaint(
                            painter: BubblePainter(
                              styleId: id,
                              isMe: false,
                              fillColor: bubbleIn,
                              strokeColor: const Color(0xFF00A884),
                              accentColor: const Color(0xFF00A884),
                            ),
                            child: const Padding(
                              padding: EdgeInsets.fromLTRB(14, 10, 14, 12),
                              child: Text(
                                'Hi there',
                                style: TextStyle(fontSize: 13),
                              ),
                            ),
                          ),
                          const SizedBox(width: 14),
                          CustomPaint(
                            painter: BubblePainter(
                              styleId: id,
                              isMe: true,
                              fillColor: bubbleOut,
                              strokeColor: const Color(0xFF00A884),
                              accentColor: const Color(0xFF00A884),
                            ),
                            child: const Padding(
                              padding: EdgeInsets.fromLTRB(14, 10, 14, 12),
                              child: Text(
                                'Hello!',
                                style: TextStyle(fontSize: 13),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  const Padding(
                    padding: EdgeInsets.all(10),
                    child: Text(
                      'GB ticks (delivered | read)',
                      style: TextStyle(fontSize: 13),
                    ),
                  ),
                  ...tickGrid,
                ],
              ),
            ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 400));
      expect(tester.takeException(), isNull);
      await tester.runAsync(() async {
        final boundary =
            key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/ui_renders')..createSync(recursive: true);
        File(
          '${dir.path}/gb_bubbles_ticks_${b.name}.png',
        ).writeAsBytesSync(data!.buffer.asUint8List());
      });
    });
  }
}
