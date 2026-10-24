// Font-box bisect: which chip construct loses its family under flutter_test?
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

Future<void> _load(String fam, String file) async {
  final root = Platform.environment['FLUTTER_ROOT'] ?? '';
  final f = File('$root/bin/cache/artifacts/material_fonts/$file');
  final b = await f.readAsBytes();
  final l = FontLoader(fam)..addFont(Future.value(ByteData.sublistView(Uint8List.fromList(b))));
  await l.load();
}

Widget _chip(String t) => ChoiceChip(selected: false, onSelected: (_) {}, label: Text(t));

void main() {
  setUpAll(() async {
    for (final fam in ['Roboto', 'sans-serif', 'sans-serif-medium']) {
      await _load(fam, 'Roboto-Regular.ttf');
      await _load(fam, 'Roboto-Medium.ttf');
    }
  });

  testWidgets('chip labelStyle family bisect', (tester) async {
    final key = GlobalKey();
    final base = ThemeData(
      useMaterial3: true,
      textTheme: const TextTheme().apply(fontFamily: 'Roboto'),
      primaryTextTheme: const TextTheme().apply(fontFamily: 'Roboto'),
    );
    await tester.pumpWidget(RepaintBoundary(
      key: key,
      child: MaterialApp(
        theme: base,
        home: Scaffold(
          backgroundColor: Colors.white,
          body: Column(children: [
            // A: app-exact: chipTheme labelStyle color+w600, no family
            Theme(data: base.copyWith(chipTheme: const ChipThemeData(
                labelStyle: TextStyle(color: Colors.black, fontWeight: FontWeight.w600))),
              child: _chip('A appExact')),
            // B: same + explicit family
            Theme(data: base.copyWith(chipTheme: const ChipThemeData(
                labelStyle: TextStyle(fontFamily: 'Roboto', color: Colors.black, fontWeight: FontWeight.w600))),
              child: _chip('B famRoboto')),
            // C: color only, no family, no weight (replace vs merge test)
            Theme(data: base.copyWith(chipTheme: const ChipThemeData(
                labelStyle: TextStyle(color: Colors.black))),
              child: _chip('C colorOnly')),
            // D: no chipTheme override (defaults path)
            _chip('D noChipTheme'),
            // E: app-exact chipTheme + label Text with explicit family
            Theme(data: base.copyWith(chipTheme: const ChipThemeData(
                labelStyle: TextStyle(color: Colors.black, fontWeight: FontWeight.w600))),
              child: ChoiceChip(selected: false, onSelected: (_) {},
                label: const Text('E labelFam', style: TextStyle(fontFamily: 'Roboto')))),
          ]),
        ),
      ),
    ));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.runAsync(() async {
      final rb = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
      final img = await rb.toImage(pixelRatio: 2);
      final d = await img.toByteData(format: ui.ImageByteFormat.png);
      File('build/ui_renders/font_probe.png').writeAsBytesSync(d!.buffer.asUint8List());
    });
  });
}
