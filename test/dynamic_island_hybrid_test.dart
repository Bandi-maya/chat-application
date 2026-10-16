import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/features/island/dynamic_island.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Apple & iQOO Hybrid Dynamic Island Features', () {
    test('Multi-Task Carousel (OriginOS): navigates through multiple concurrent tasks', () {
      final controller = DynamicIslandController();

      final p1 = DynamicIslandPayload(
        id: 'media_1',
        type: IslandPayloadType.media,
        title: 'Song Track',
        isPlaying: true,
        timestamp: DateTime.now().subtract(const Duration(seconds: 10)),
      );

      final p2 = DynamicIslandPayload(
        id: 'chat_1',
        type: IslandPayloadType.chatMessage,
        title: 'Message from Alex',
        timestamp: DateTime.now().subtract(const Duration(seconds: 5)),
      );

      final p3 = DynamicIslandPayload(
        id: 'clipboard_1',
        type: IslandPayloadType.clipboardIntent,
        title: 'Tracking Code',
        message: '1Z9999999999999999',
        clipboardActionLabel: 'Track Package',
        timestamp: DateTime.now(),
      );

      controller.postPayload(p1, autoExpand: false);
      controller.postPayload(p2, autoExpand: false);
      controller.postPayload(p3, autoExpand: false);

      expect(controller.queue.length, equals(3));
      expect(controller.carouselIndex, equals(0));

      // Advance carousel
      controller.nextCarouselItem();
      expect(controller.carouselIndex, equals(1));

      controller.nextCarouselItem();
      expect(controller.carouselIndex, equals(2));

      // Cycles back to 0
      controller.nextCarouselItem();
      expect(controller.carouselIndex, equals(0));

      // Backward navigation
      controller.prevCarouselItem();
      expect(controller.carouselIndex, equals(2));

      controller.dispose();
    });

    test('Smart Clipboard Intent (OriginOS): posts and executes contextual action', () {
      final controller = DynamicIslandController();
      bool actionTriggered = false;

      controller.postClipboardIntent(
        title: 'Copied Phone Number',
        message: '+1 (555) 019-2834',
        actionLabel: 'Call Number',
        onAction: () {
          actionTriggered = true;
        },
      );

      expect(controller.queue.isNotEmpty, isTrue);
      final payload = controller.queue.first;
      expect(payload.type, equals(IslandPayloadType.clipboardIntent));
      expect(payload.clipboardActionLabel, equals('Call Number'));
      expect(payload.isBandiIcon, isTrue);

      // Execute action callback
      payload.onClipboardAction?.call();
      expect(actionTriggered, isTrue);

      controller.dispose();
    });

    test('Drag & Go Target Dock (OriginOS): activates drop zone and handles dropped payload', () {
      final controller = DynamicIslandController();

      expect(controller.isDragTargetActive, isFalse);

      controller.setDragTargetActive(true);
      expect(controller.isDragTargetActive, isTrue);

      controller.handleDroppedData('test_attachment.png');
      expect(controller.isDragTargetActive, isFalse);
      expect(controller.mode, equals(IslandMode.expanded));
      expect(controller.activePayload?.title, equals('Content Attached'));
      expect(controller.activePayload?.isBandiIcon, isTrue);

      controller.dispose();
    });

    testWidgets('BandiKineticCrest widget renders vector paths cleanly', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Center(
              child: BandiKineticCrest(
                size: 48,
                primaryColor: Color(0xFF60A5FA),
                accentColor: Color(0xFF38BDF8),
              ),
            ),
          ),
        ),
      );

      expect(find.byType(BandiKineticCrest), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.byType(CustomPaint), findsWidgets);
    });

    testWidgets('DynamicIslandOverlayHost renders Smart Clipboard Intent and Carousel Indicator', (tester) async {
      final controller = DynamicIslandController();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DynamicIslandOverlayHost(
              controller: controller,
              enableBlur: false,
              child: const Center(child: Text('Main Workspace Screen')),
            ),
          ),
        ),
      );

      // Post clipboard intent and expand
      controller.postClipboardIntent(
        title: 'Copied Tracking',
        message: 'USPS 9400 1000 0000',
        actionLabel: 'Track Delivery',
        onAction: () {},
      );
      controller.expand();

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));

      expect(find.text('Copied Tracking'), findsOneWidget);
      expect(find.text('Track Delivery'), findsOneWidget);
      expect(find.text('Smart Context Detected'), findsOneWidget);

      controller.dispose();
    });
  });
}
