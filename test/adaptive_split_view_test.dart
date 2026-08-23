import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/design_system/components/adaptive_split_view.dart';

void main() {
  testWidgets('compact width renders primary pane only', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      const MaterialApp(
        home: ChatyAdaptiveSplitView(
          primary: Text('primary'),
          secondary: Text('secondary'),
        ),
      ),
    );

    expect(find.text('primary'), findsOneWidget);
    expect(find.text('secondary'), findsNothing);
  });

  testWidgets('expanded width renders primary and secondary panes', (tester) async {
    tester.view.physicalSize = const Size(1000, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      const MaterialApp(
        home: ChatyAdaptiveSplitView(
          primary: Text('primary'),
          secondary: Text('secondary'),
        ),
      ),
    );

    expect(find.text('primary'), findsOneWidget);
    expect(find.text('secondary'), findsOneWidget);
  });
}
