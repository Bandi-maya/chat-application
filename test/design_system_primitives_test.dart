import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/core/design_system/design_system.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Design System Primitives Tests', () {
    testWidgets('SpecularGlassCard renders child content with glass effect', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SpecularGlassCard(
              borderRadius: 16.0,
              child: Text('Specular Glass Test'),
            ),
          ),
        ),
      );

      expect(find.text('Specular Glass Test'), findsOneWidget);
    });

    testWidgets('SpringInteractiveScale triggers onTap on user press', (tester) async {
      bool tapped = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SpringInteractiveScale(
              onTap: () => tapped = true,
              child: const Text('Tap Me'),
            ),
          ),
        ),
      );

      expect(find.text('Tap Me'), findsOneWidget);
      await tester.tap(find.text('Tap Me'));
      await tester.pumpAndSettle();

      expect(tapped, isTrue);
    });

    testWidgets('MorphingSegmentedControl displays items and notifies selection', (tester) async {
      String selected = 'All';

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) {
                return MorphingSegmentedControl<String>(
                  selectedValue: selected,
                  items: const [
                    MorphingSegmentItem(value: 'All', label: 'All'),
                    MorphingSegmentItem(value: 'Unread', label: 'Unread'),
                    MorphingSegmentItem(value: 'Vault', label: 'Vault'),
                  ],
                  onSelected: (val) {
                    setState(() => selected = val);
                  },
                );
              },
            ),
          ),
        ),
      );

      expect(find.text('All'), findsOneWidget);
      expect(find.text('Unread'), findsOneWidget);
      expect(find.text('Vault'), findsOneWidget);

      await tester.tap(find.text('Unread'));
      await tester.pumpAndSettle();
      expect(selected, 'Unread');
    });

    testWidgets('BentoCard renders title and child', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: BentoCard(
              title: 'Bento Title',
              subtitle: 'Bento Subtitle',
              child: Text('Bento Content'),
            ),
          ),
        ),
      );

      expect(find.text('Bento Title'), findsOneWidget);
      expect(find.text('Bento Subtitle'), findsOneWidget);
      expect(find.text('Bento Content'), findsOneWidget);
    });
  });
}
