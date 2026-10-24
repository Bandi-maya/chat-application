// Production-widget render harness: real MessageBubble instances at narrow
// and wide widths, stacked like a message list, for dome/tail-heavy GB
// styles. Verifies text containment inside painted shapes at production
// layout (not just registry contract).
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/domain/models/chat_message.dart';
import 'package:chat/features/messages/message_bubble.dart';
import 'package:chat/ui/core/bubbles/bubble_style_id.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';

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

  const styles = [
    BubbleStyleId.rcPal,
    BubbleStyleId.rcImess,
    BubbleStyleId.rcImLine,
    BubbleStyleId.rcTranLine,
    BubbleStyleId.textra,
    BubbleStyleId.gabiMess,
  ];

  for (final width in [320.0, 600.0]) {
    for (final b in [Brightness.light, Brightness.dark]) {
      testWidgets('messagebubble domes w${width.toInt()} ${b.name}', (tester) async {
        tester.view.physicalSize = Size(width * 2, 5200);
        tester.view.devicePixelRatio = 2;
        addTearDown(tester.view.reset);
        final key = GlobalKey();
        final dark = b == Brightness.dark;
        final base = dark ? ThemePresets.midnight : ThemePresets.monochromeLight;

        var n = 0;
        Widget mb(BubbleStyleId id, bool isMe, String text) {
          n++;
          return MessageBubble(
            message: ChatMessage(
              id: 'm$n',
              conversationId: 'c1',
              senderId: isMe ? 'me' : 'them',
              text: text,
              deliveryState: text == 'Hey!'
                  ? DeliveryState.delivered
                  : DeliveryState.read,
              createdAt: DateTime(2026, 10, 24, 13, 41),
            ),
            isMe: isMe,
            theme: base.copyWith(bubbleStyle: id),
            onLongPress: () {},
          );
        }

        await tester.pumpWidget(
          RepaintBoundary(
            key: key,
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: ThemeData(brightness: b),
              home: Scaffold(
                backgroundColor: dark ? const Color(0xFF0B141A) : const Color(0xFFECE5DD),
                body: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (final id in styles) ...[
                        Padding(
                          padding: const EdgeInsets.fromLTRB(10, 8, 10, 0),
                          child: Text(id.displayName, style: TextStyle(fontSize: 11, color: dark ? Colors.white : Colors.black87)),
                        ),
                        mb(id, false, 'Hi'),
                        mb(id, true, 'Hey!'),
                        mb(id, false, 'A longer message that wraps onto at least two lines in this chat to stress the dome and body containment'),
                        mb(id, true, 'Reply that also wraps across multiple rendered lines so the first line can collide with the dome cap if headroom is not reserved properly'),
                      ],
                    ],
                  ),
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
          File('${dir.path}/gb_msgbubble_w${width.toInt()}_${b.name}.png').writeAsBytesSync(data!.buffer.asUint8List());
        });
      });
    }
  }
}
