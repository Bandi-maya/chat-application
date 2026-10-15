import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/features/island/dynamic_island.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('DynamicIslandController State Machine & Priority Queue Tests', () {
    test('Initializes in idle mode with empty queue', () {
      final controller = DynamicIslandController();
      expect(controller.mode, equals(IslandMode.idle));
      expect(controller.queue, isEmpty);
      expect(controller.activePayload, isNull);
      controller.dispose();
    });

    test('State transitions: posting payload transitions idle -> expanded -> compact -> idle', () async {
      final controller = DynamicIslandController();

      // Post standard chat message
      final msg = DynamicIslandPayload(
        id: 'msg_1',
        type: IslandPayloadType.chatMessage,
        title: 'Alice',
        message: 'Hello there!',
        timestamp: DateTime.now(),
      );

      controller.postPayload(msg, autoExpand: true);
      expect(controller.mode, equals(IslandMode.expanded));
      expect(controller.activePayload?.id, equals('msg_1'));

      // Manual collapse
      controller.collapse();
      expect(controller.mode, equals(IslandMode.compact));

      // Clearing all transitions to idle
      controller.clearAll();
      expect(controller.mode, equals(IslandMode.idle));
      expect(controller.queue, isEmpty);

      controller.dispose();
    });

    test('Priority Queue: incomingCall preempts contactOnline and chatMessage', () {
      final controller = DynamicIslandController();

      final onlineAlert = DynamicIslandPayload(
        id: 'status_1',
        type: IslandPayloadType.contactOnline,
        title: 'Bob is online',
        timestamp: DateTime.now(),
      );

      final chatMsg = DynamicIslandPayload(
        id: 'chat_1',
        type: IslandPayloadType.chatMessage,
        title: 'Charlie',
        message: 'Let us meet',
        timestamp: DateTime.now(),
      );

      final incomingCall = DynamicIslandPayload(
        id: 'call_1',
        type: IslandPayloadType.incomingCall,
        title: 'David',
        message: 'Incoming Audio Call',
        timestamp: DateTime.now(),
      );

      // Post lower priority first
      controller.postPayload(onlineAlert, autoExpand: false);
      controller.postPayload(chatMsg, autoExpand: false);
      expect(controller.activePayload?.id, equals('chat_1'));

      // Incoming call must preempt all others and take position 0
      controller.postPayload(incomingCall, autoExpand: true);
      expect(controller.activePayload?.id, equals('call_1'));
      expect(controller.queue.first.type, equals(IslandPayloadType.incomingCall));
      expect(controller.queue.length, equals(3));
      expect(controller.queue[0].id, equals('call_1'));
      expect(controller.queue[1].id, equals('chat_1'));
      expect(controller.queue[2].id, equals('status_1'));

      controller.dispose();
    });

    test('Split mode: ongoing call + incoming notification transitions to split mode', () {
      final controller = DynamicIslandController();

      final ongoingCall = DynamicIslandPayload(
        id: 'call_active',
        type: IslandPayloadType.ongoingCall,
        title: 'Sarah',
        durationSeconds: 42,
        timestamp: DateTime.now(),
      );

      controller.postPayload(ongoingCall, autoExpand: false);
      controller.setMode(IslandMode.compact);

      final newMsg = DynamicIslandPayload(
        id: 'msg_2',
        type: IslandPayloadType.chatMessage,
        title: 'Emma',
        message: 'Where are you?',
        timestamp: DateTime.now(),
      );

      controller.postPayload(newMsg, autoExpand: false);
      expect(controller.mode, equals(IslandMode.split));
      expect(controller.activePayload?.id, equals('call_active'));
      expect(controller.secondaryPayload?.id, equals('msg_2'));

      controller.dispose();
    });

    test('Dismiss active pops next payload in queue', () {
      final controller = DynamicIslandController();

      controller.postPayload(DynamicIslandPayload(
        id: 'item_1',
        type: IslandPayloadType.chatMessage,
        title: 'First',
        timestamp: DateTime.now().subtract(const Duration(seconds: 5)),
      ), autoExpand: false);

      controller.postPayload(DynamicIslandPayload(
        id: 'item_2',
        type: IslandPayloadType.chatMessage,
        title: 'Second',
        timestamp: DateTime.now(),
      ), autoExpand: false);

      expect(controller.activePayload?.id, equals('item_2'));
      controller.dismissActive();
      expect(controller.activePayload?.id, equals('item_1'));
      controller.dismissActive();
      expect(controller.mode, equals(IslandMode.idle));
      expect(controller.queue, isEmpty);

      controller.dispose();
    });
  });

  group('Universal Cutout Anchor Calibration Tests', () {
    testWidgets('Calibrates standard status bar without cutout', (tester) async {
      late CutoutAnchorMetrics metrics;

      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(
              padding: EdgeInsets.only(top: 44.0),
              viewPadding: EdgeInsets.only(top: 44.0),
              size: Size(390, 844),
            ),
            child: Builder(
              builder: (context) {
                metrics = CutoutAnchorResolver.resolve(context);
                return const SizedBox();
              },
            ),
          ),
        ),
      );

      expect(metrics.hasCutout, isFalse);
      expect(metrics.alignment, equals(Alignment.topCenter));
      expect(metrics.topOffset, greaterThan(0));
    });

    testWidgets('Calibrates center camera punch-hole cutout display feature', (tester) async {
      late CutoutAnchorMetrics metrics;

      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(
              padding: EdgeInsets.only(top: 48.0),
              viewPadding: EdgeInsets.only(top: 48.0),
              size: Size(412, 915),
              displayFeatures: [
                DisplayFeature(
                  bounds: Rect.fromLTWH(188, 12, 36, 36),
                  type: DisplayFeatureType.cutout,
                  state: DisplayFeatureState.unknown,
                ),
              ],
            ),
            child: Builder(
              builder: (context) {
                metrics = CutoutAnchorResolver.resolve(context);
                return const SizedBox();
              },
            ),
          ),
        ),
      );

      expect(metrics.hasCutout, isTrue);
      expect(metrics.cutoutWidth, equals(36.0));
      expect(metrics.alignment, equals(Alignment.topCenter));
    });
  });

  group('DynamicIslandOverlayHost Widget UI Tests', () {
    testWidgets('Renders collapsed pill and expands on tap with spring morphing', (tester) async {
      final controller = DynamicIslandController();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DynamicIslandOverlayHost(
              controller: controller,
              enableBlur: false, // disable GPU shader passes for fast unit testing
              child: const Center(child: Text('Main Screen Content')),
            ),
          ),
        ),
      );

      expect(find.text('Main Screen Content'), findsOneWidget);

      // Post notification
      controller.postPayload(
        DynamicIslandPayload(
          id: 'ui_msg',
          type: IslandPayloadType.chatMessage,
          title: 'Elena Vance',
          message: 'The package arrived safely.',
          timestamp: DateTime.now(),
        ),
        autoExpand: false,
      );
      controller.setMode(IslandMode.compact);

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byKey(const ValueKey('collapsed-view')), findsOneWidget);
      expect(find.text('Elena Vance'), findsOneWidget);

      // Tap on the collapsed pill to spring-expand
      await tester.tap(find.byKey(const ValueKey('collapsed-view')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));

      expect(controller.mode, equals(IslandMode.expanded));
      expect(find.byKey(const ValueKey('expanded-chat-ui_msg')), findsOneWidget);
      expect(find.text('The package arrived safely.'), findsOneWidget);
      expect(find.text('Quick Reply'), findsOneWidget);

      controller.dispose();
    });

    testWidgets('Renders media player with waveform and seek scrubber', (tester) async {
      final controller = DynamicIslandController();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DynamicIslandOverlayHost(
              controller: controller,
              enableBlur: false,
              child: const SizedBox(),
            ),
          ),
        ),
      );

      controller.postPayload(
        DynamicIslandPayload(
          id: 'media_track',
          type: IslandPayloadType.media,
          title: 'Neon Horizons',
          mediaArtist: 'Luna Eclipse',
          durationSeconds: 214,
          progress: 0.35,
          isPlaying: true,
          timestamp: DateTime.now(),
        ),
        autoExpand: true,
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));

      expect(find.byKey(const ValueKey('expanded-media-media_track')), findsOneWidget);
      expect(find.text('Neon Horizons'), findsOneWidget);
      expect(find.text('Luna Eclipse'), findsOneWidget);
      expect(find.byType(IslandSeekBar), findsOneWidget);

      controller.dispose();
    });
  });
}
