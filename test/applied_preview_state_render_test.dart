// Renders the paired Home/Conversation preview for explicit resolved states:
// (A) plain template, (B) component overrides + hidden Tasks/Updates +
// another bottom bar style. Widget-test renders with SDK fonts, not device.
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/templates/applied_preview_model.dart';
import 'package:chat/ui/core/templates/template_models.dart';
import 'package:chat/ui/core/theme/theme_pair_preview.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';

Future<void> _loadFont(String family, String file) async {
  final root = Platform.environment['FLUTTER_ROOT'] ?? '';
  final f = File('$root/bin/cache/artifacts/material_fonts/$file');
  if (!f.existsSync()) return;
  final bytes = await f.readAsBytes();
  final loader = FontLoader(family)
    ..addFont(Future<ByteData>.value(ByteData.sublistView(Uint8List.fromList(bytes))));
  await loader.load();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await _loadFont('Roboto', 'Roboto-Regular.ttf');
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
  });

  final light = ThemePresets.all.firstWhere((p) => p.brightness == Brightness.light);
  final dark = ThemePresets.all.firstWhere((p) => p.brightness == Brightness.dark);
  final ids = ChatyTemplateId.values;

  final plain = UserTemplateConfiguration(baseTemplate: ids.first);
  final overridden = UserTemplateConfiguration(
    baseTemplate: ids.first,
    componentOverrides: {
      TemplateComponentType.navigation: ids.last,
      TemplateComponentType.composer: ids[1],
      TemplateComponentType.home: ids[2],
    },
  );

  test('state resolution: overrides win and hidden tabs are removed', () {
    final a = PreviewLayout.resolve(plain);
    final b = PreviewLayout.resolve(
      overridden,
      hiddenDestinationIds: const {'tasks', 'updates'},
      bottomBarStyle: 'Raised Center Action',
    );
    expect(b.navigation.layout, isNot(equals(a.navigation.layout)) );
    expect(b.destinationIds.contains('tasks'), isFalse);
    expect(b.destinationIds.contains('updates'), isFalse);
    expect(b.bottomBarStyle, 'Raised Center Action');
    expect(b.homeStylePreset, isNotEmpty);
  });

  final states = <String, PreviewLayout>{
    'plain_${ids.first.name}': PreviewLayout.resolve(plain),
    'overrides_hidden_tasks_updates': PreviewLayout.resolve(
      overridden,
      hiddenDestinationIds: const {'tasks', 'updates'},
      bottomBarStyle: 'Raised Center Action',
    ),
  };

  for (final theme in [light, dark]) {
    for (final e in states.entries) {
      testWidgets('render ${e.key} ${theme.brightness.name}', (tester) async {
        final key = GlobalKey();
        tester.view.physicalSize = const Size(1000, 760);
        tester.view.devicePixelRatio = 2;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          RepaintBoundary(
            key: key,
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              home: Scaffold(
                backgroundColor: theme.backgroundColor,
                body: Center(
                  child: ThemePairPreview(
                    themeConfig: themeWithTemplateConversation(theme, e.value.conversation),
                    layout: e.value,
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pump(const Duration(milliseconds: 200));
        expect(tester.takeException(), isNull);
        await tester.runAsync(() async {
          final b = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
          final img = await b.toImage(pixelRatio: 1);
          final data = await img.toByteData(format: ui.ImageByteFormat.png);
          final dir = Directory('build/settings_render')..createSync(recursive: true);
          File('${dir.path}/state_${e.key}_${theme.brightness.name}.png')
              .writeAsBytesSync(data!.buffer.asUint8List());
        });
      });
    }
  }
}
