import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/design_system/components/call_activity_capsule.dart';
import 'package:chat/ui/core/design_system/components/global_activity_host.dart';

void main() {
  testWidgets(
    'global activity host preserves routed child and activity slots',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ChatyGlobalActivityHost(
            primaryActivity: Text('call-activity'),
            bottomActivity: Text('sync-activity'),
            child: Text('route-content'),
          ),
        ),
      );

      expect(find.text('route-content'), findsOneWidget);
      expect(find.text('call-activity'), findsOneWidget);
      expect(find.text('sync-activity'), findsOneWidget);
      expect(find.byType(RepaintBoundary), findsWidgets);
      expect(find.byType(AnimatedSwitcher), findsNWidgets(3));
    },
  );

  testWidgets('empty activity slots do not remove routed content', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ChatyGlobalActivityHost(child: Text('route-only')),
      ),
    );

    expect(find.text('route-only'), findsOneWidget);
    expect(find.byType(AnimatedSwitcher), findsNWidgets(3));
  });

  testWidgets('minimized call capsule renders above routes and fires actions', (
    tester,
  ) async {
    var opened = false;
    var toggledSpeaker = false;
    var hungUp = false;
    Widget buildCapsule() => ChatyCallActivityCapsule(
      contactName: 'Ada Lovelace',
      status: 'Connected',
      isVideo: false,
      isSpeaker: true,
      onOpen: () => opened = true,
      onToggleSpeaker: () => toggledSpeaker = true,
      onHangUp: () => hungUp = true,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: ChatyGlobalActivityHost(
          primaryActivity: buildCapsule(),
          child: const Text('route-content'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('route-content'), findsOneWidget);
    expect(find.text('Ada Lovelace'), findsOneWidget);
    expect(find.byType(Tooltip), findsNothing);

    await tester.tap(find.text('Ada Lovelace'));
    await tester.tap(find.byIcon(Icons.volume_up_rounded));
    await tester.tap(find.byIcon(Icons.call_end_rounded));
    expect(opened, isTrue);
    expect(toggledSpeaker, isTrue);
    expect(hungUp, isTrue);

    // No activity -> slot collapses to an empty surface.
    await tester.pumpWidget(
      const MaterialApp(home: ChatyGlobalActivityHost(child: Text('route-2'))),
    );
    await tester.pumpAndSettle();
    expect(find.byType(ChatyCallActivityCapsule), findsNothing);
    expect(find.text('route-2'), findsOneWidget);
  });
}
