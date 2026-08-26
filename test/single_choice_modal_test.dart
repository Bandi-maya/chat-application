import 'package:chat/ui/core/design_system/components/single_choice_modal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('single choice is staged until Apply', (tester) async {
    String? committed;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: FilledButton(
              onPressed: () async {
                committed = await ChatySingleChoiceModal.show<String>(
                  context: context,
                  title: 'Bubble style',
                  value: 'Rounded',
                  options: const ['Rounded', 'Compact'],
                  labelBuilder: (value) => value,
                );
              },
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();

    final apply = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Apply'),
    );
    expect(apply.onPressed, isNull);

    await tester.tap(find.text('Compact'));
    await tester.pumpAndSettle();
    expect(committed, isNull);
    expect(
      tester
          .widget<FilledButton>(find.widgetWithText(FilledButton, 'Apply'))
          .onPressed,
      isNotNull,
    );

    await tester.tap(find.text('Apply'));
    await tester.pumpAndSettle();
    expect(committed, 'Compact');
  });

  testWidgets('Cancel discards a staged choice', (tester) async {
    String? committed;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: FilledButton(
              onPressed: () async {
                committed = await ChatySingleChoiceModal.show<String>(
                  context: context,
                  title: 'Navigation style',
                  value: 'Classic',
                  options: const ['Classic', 'Floating'],
                  labelBuilder: (value) => value,
                );
              },
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Floating'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(committed, isNull);
  });
}
