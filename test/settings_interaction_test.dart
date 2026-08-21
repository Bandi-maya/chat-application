import 'package:chat/ui/core/design_system/settings_primitives.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('settings switch row reacts immediately to a row tap', (
    tester,
  ) async {
    var value = false;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) => ChatySwitchTile(
              title: 'Immediate setting',
              value: value,
              onChanged: (next) => setState(() => value = next),
            ),
          ),
        ),
      ),
    );

    expect(value, isFalse);
    await tester.tap(find.text('Immediate setting'));
    await tester.pump();
    expect(value, isTrue);
  });
}
