import 'package:chat/ui/core/templates/template_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('header keeps navigation reachable and long title constrained', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 640),
            textScaler: TextScaler.linear(1.6),
            padding: EdgeInsets.only(top: 24),
          ),
          child: Scaffold(
            body: TemplateShellHeader(
              title: 'A very long account and destination title that must fit',
              navigation: IconButton(
                tooltip: 'Open menu',
                onPressed: () {},
                icon: const Icon(Icons.menu_rounded),
              ),
              actions: [
                IconButton(
                  tooltip: 'Search',
                  onPressed: () {},
                  icon: const Icon(Icons.search_rounded),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(find.byTooltip('Open menu'), findsOneWidget);
    expect(find.byTooltip('Search'), findsOneWidget);
    expect(tester.getSize(find.byTooltip('Open menu')), const Size(48, 48));
    expect(
      tester.widget<Text>(find.textContaining('A very long account')).overflow,
      TextOverflow.ellipsis,
    );
  });
}
