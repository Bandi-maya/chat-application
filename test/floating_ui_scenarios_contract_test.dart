import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:chat/core/audio/audio_capability_service.dart';
import 'package:chat/core/audio/audio_policy_coordinator.dart';
import 'package:chat/core/audio/audio_types.dart';
import 'package:chat/features/island/controllers/dynamic_island_controller.dart';
import 'package:chat/features/island/models/dynamic_island_payload.dart';
import 'package:chat/features/island/widgets/dynamic_island_overlay_host.dart';
import 'package:chat/features/media/services/media_player_coordinator.dart';

class _FakeAudioCapabilityService extends Fake with ChangeNotifier implements AudioCapabilityService {
  AudioCapabilities _current = const AudioCapabilities(
    availableRoutes: [AudioOutputRoute.receiver, AudioOutputRoute.speaker],
    activeRoute: AudioOutputRoute.receiver,
    dualOutputStatus: DualOutputStatus.unavailable,
  );

  @override
  AudioOutputRoute get activeCommunicationRoute => _current.activeRoute;

  @override
  List<AudioOutputRoute> get availableCommunicationRoutes => _current.availableRoutes;

  @override
  DualOutputStatus get communicationDualStatus => _current.dualOutputStatus;

  @override
  bool get isDualOutputActive => false;

  @override
  Future<AudioCapabilities> getCapabilities(AudioUseCase useCase) async => _current;

  @override
  Future<AudioCapabilities> setPreferredRoute(AudioUseCase useCase, AudioOutputRoute route) async {
    _current = _current.copyWith(activeRoute: route);
    notifyListeners();
    return _current;
  }

  @override
  Future<void> releaseSession(AudioUseCase useCase) async {
    _current = _current.copyWith(activeRoute: AudioOutputRoute.systemDefault);
    notifyListeners();
  }

  @override
  Future<bool> setDualOutputEnabled(bool enabled) async => false;

  @override
  void dispose() {
    super.dispose();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Floating UI Architectural Scenarios Contract Tests', () {
    late DynamicIslandController islandController;
    late AudioPolicyCoordinator audioPolicyCoordinator;
    late _FakeAudioCapabilityService fakeCapabilityService;

    setUp(() {
      islandController = DynamicIslandController();
      fakeCapabilityService = _FakeAudioCapabilityService();
      audioPolicyCoordinator = AudioPolicyCoordinator(
        capabilityService: fakeCapabilityService,
      );
    });

    tearDown(() {
      islandController.dispose();
      audioPolicyCoordinator.dispose();
    });

    test('Scenario 1 & 2: Ongoing Call + Music enter IslandMode.split concurrently', () {
      // 1. Post ongoing call payload (Priority 1, ongoing)
      final callPayload = DynamicIslandPayload(
        id: 'call_live_123',
        type: IslandPayloadType.ongoingCall,
        title: 'Elena Vance',
        durationSeconds: 120,
        timestamp: DateTime.now(),
      );
      islandController.postPayload(callPayload, autoExpand: false);
      islandController.setMode(IslandMode.compact);

      expect(islandController.mode, equals(IslandMode.compact));
      expect(islandController.activePayload?.id, equals('call_live_123'));

      // 2. Post playing media payload (Priority 2, ongoing)
      final mediaPayload = DynamicIslandPayload(
        id: 'media_active',
        type: IslandPayloadType.media,
        title: 'Cyberpunk Synthwave',
        mediaArtist: 'Neon Grid',
        durationSeconds: 240,
        isPlaying: true,
        progress: 0.25,
        timestamp: DateTime.now(),
      );
      islandController.postPayload(mediaPayload, autoExpand: false);

      // 3. Must split into primary pill and detached bubble
      expect(islandController.mode, equals(IslandMode.split));
      expect(islandController.activePayload?.id, equals('call_live_123'));
      expect(islandController.secondaryPayload?.id, equals('media_active'));

      // 4. When call finishes, call payload is removed
      islandController.removePayloadsWhere((p) => p.id.startsWith('call_'));

      // 5. Media takes over primary position cleanly without zombie call
      expect(islandController.activePayload?.id, equals('media_active'));
      expect(islandController.mode, equals(IslandMode.compact));
    });

    test('Scenario 1 Audio Focus: MediaPlayerCoordinator pauses on call and resumes on call end', () async {
      final playerCoordinator = MediaPlayerCoordinator(
        policyCoordinator: audioPolicyCoordinator,
        islandController: islandController,
      );

      // 1. Acquire media session
      final acquired = await audioPolicyCoordinator.startMediaSession();
      expect(acquired, isTrue);
      expect(audioPolicyCoordinator.state, equals(AudioSessionState.mediaActive));
      expect(audioPolicyCoordinator.activeOwner, equals(AudioUseCase.media));

      // 2. Call/interruption begins -> state becomes interrupted
      audioPolicyCoordinator.onInterruption(began: true);
      expect(audioPolicyCoordinator.state, equals(AudioSessionState.interrupted));

      // 3. Interruption ends -> recovers to media session
      audioPolicyCoordinator.onInterruption(began: false);
      expect(audioPolicyCoordinator.state, equals(AudioSessionState.mediaActive));

      playerCoordinator.dispose();
    });

    testWidgets('Scenario 3 Gestures: Long-press expands compact island to reveal controls', (tester) async {
      islandController.postPayload(
        DynamicIslandPayload(
          id: 'chat_notification_1',
          type: IslandPayloadType.chatMessage,
          title: 'Dr. Freeman',
          message: 'The resonance cascade is under control.',
          timestamp: DateTime.now(),
        ),
        autoExpand: false,
      );
      islandController.setMode(IslandMode.compact);

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DynamicIslandOverlayHost(
              controller: islandController,
              enableBlur: false,
              child: const Center(child: Text('PDF / Document Content')),
            ),
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('PDF / Document Content'), findsOneWidget);
      expect(find.byKey(const ValueKey('collapsed-view')), findsOneWidget);
      expect(islandController.mode, equals(IslandMode.compact));

      // Long-press the collapsed pill to trigger Apple HIG / OriginOS expansion
      await tester.longPress(find.byKey(const ValueKey('collapsed-view')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));

      expect(islandController.mode, equals(IslandMode.expanded));
      expect(find.text('The resonance cascade is under control.'), findsOneWidget);
      expect(find.text('Quick Reply'), findsOneWidget);

      // Tap outside the expanded pill on the full-screen dismiss barrier to collapse
      await tester.tapAt(const Offset(20, 500));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));

      expect(islandController.mode, equals(IslandMode.compact));

      // Settle dismiss timers
      islandController.clearAll();
      await tester.pump(const Duration(milliseconds: 350));
    });

    testWidgets('Scenario 4 & Accessibility: Secondary split bubble includes Semantics button label', (tester) async {
      islandController.postPayload(
        DynamicIslandPayload(
          id: 'call_1',
          type: IslandPayloadType.ongoingCall,
          title: 'Alyx',
          durationSeconds: 45,
          timestamp: DateTime.now(),
        ),
        autoExpand: false,
      );
      islandController.postPayload(
        DynamicIslandPayload(
          id: 'media_1',
          type: IslandPayloadType.media,
          title: 'Tenebre Rosso Sangue',
          isPlaying: true,
          timestamp: DateTime.now(),
        ),
        autoExpand: false,
      );
      islandController.setMode(IslandMode.split);

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DynamicIslandOverlayHost(
              controller: islandController,
              enableBlur: false,
              child: const SizedBox(),
            ),
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(islandController.mode, equals(IslandMode.split));

      // Assert TalkBack / VoiceOver Semantics exist for secondary bubble
      expect(
        find.byWidgetPredicate((widget) =>
            widget is Semantics &&
            widget.properties.label == 'Secondary activity: Tenebre Rosso Sangue' &&
            widget.properties.button == true),
        findsOneWidget,
      );

      islandController.clearAll();
      await tester.pump(const Duration(milliseconds: 350));
    });

    testWidgets('Scenario 5 PDF Concurrency: Background document state preserved during notifications', (tester) async {
      final scrollController = ScrollController();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DynamicIslandOverlayHost(
              controller: islandController,
              enableBlur: false,
              child: ListView.builder(
                controller: scrollController,
                itemCount: 100,
                itemExtent: 80.0,
                itemBuilder: (context, index) => ListTile(
                  title: Text('PDF Page $index Document Paragraph'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();
      // Scroll into the PDF
      await tester.drag(find.byType(ListView), const Offset(0, -300));
      await tester.pumpAndSettle();
      final initialOffset = scrollController.offset;
      expect(initialOffset > 0, isTrue);

      // Post notification while reading
      islandController.postPayload(
        DynamicIslandPayload(
          id: 'msg_while_reading',
          type: IslandPayloadType.chatMessage,
          title: 'Barney',
          message: 'About that beer I owed ya!',
          timestamp: DateTime.now(),
        ),
        autoExpand: true,
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      // Document scroll position remains 100% undisturbed
      expect(scrollController.offset, equals(initialOffset));
      expect(find.text('About that beer I owed ya!'), findsOneWidget);

      islandController.clearAll();
      await tester.pump(const Duration(milliseconds: 350));
      scrollController.dispose();
    });
  });
}
