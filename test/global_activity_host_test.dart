import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/design_system/components/global_activity_host.dart';

void main() {
  testWidgets('global activity host preserves routed child and activity slots', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ChatyGlobalActivityHost(
          child: Text('route-content'),
          primaryActivity: Text('call-activity'),
          bottomActivity: Text('sync-activity'),
        ),
      ),
    );

    expect(find.text('route-content'), findsOneWidget);
    expect(find.text('call-activity'), findsOneWidget);
    expect(find.text('sync-activity'), findsOneWidget);
    expect(find.byType(RepaintBoundary), findsWidgets);
    expect(find.byType(AnimatedSwitcher), findsNWidgets(3));
  });

  testWidgets('empty activity slots do not remove routed content', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ChatyGlobalActivityHost(child: Text('route-only')),
      ),
    );

    expect(find.text('route-only'), findsOneWidget);
    expect(find.byType(AnimatedSwitcher), findsNWidgets(3));
  });
}
