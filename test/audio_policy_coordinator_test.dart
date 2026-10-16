import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/core/audio/audio_types.dart';
import 'package:chat/core/audio/chaty_audio_platform.dart';
import 'package:chat/core/audio/audio_capability_service.dart';
import 'package:chat/core/audio/audio_policy_coordinator.dart';
import 'package:chat/core/audio/audio_settings_controller.dart';

class MockChatyAudioPlatform implements ChatyAudioPlatform {
  AudioCapabilities currentCapabilities = const AudioCapabilities(
    availableRoutes: [
      AudioOutputRoute.systemDefault,
      AudioOutputRoute.receiver,
      AudioOutputRoute.speaker,
      AudioOutputRoute.bluetooth,
    ],
    activeRoute: AudioOutputRoute.receiver,
    dualOutputStatus: DualOutputStatus.notVerified,
    isStereoSupported: true,
    platform: 'test',
  );

  bool releaseCalled = false;
  AudioOutputRoute preferredRoute = AudioOutputRoute.receiver;
  bool shouldFailRoute = false;

  final StreamController<AudioCapabilities> _capsController =
      StreamController<AudioCapabilities>.broadcast();
  final StreamController<AudioOutputRoute> _routesController =
      StreamController<AudioOutputRoute>.broadcast();

  @override
  Future<AudioCapabilities> getCapabilities(AudioUseCase useCase) async {
    return currentCapabilities;
  }

  @override
  Future<AudioOutputRoute> getCurrentRoute(AudioUseCase useCase) async {
    return currentCapabilities.activeRoute;
  }

  @override
  Future<AudioCapabilities> setPreferredRoute(
    AudioUseCase useCase,
    AudioOutputRoute route,
  ) async {
    if (shouldFailRoute) {
      throw Exception('Route selection failed');
    }
    preferredRoute = route;
    currentCapabilities = currentCapabilities.copyWith(activeRoute: route);
    _capsController.add(currentCapabilities);
    _routesController.add(route);
    return currentCapabilities;
  }

  @override
  Future<DualOutputStatus> getDualOutputStatus(AudioUseCase useCase) async {
    return currentCapabilities.dualOutputStatus;
  }

  @override
  Future<void> releaseSession(AudioUseCase useCase) async {
    releaseCalled = true;
  }

  @override
  Stream<AudioCapabilities> get capabilityChanges => _capsController.stream;

  @override
  Stream<AudioOutputRoute> get routeChanges => _routesController.stream;
}

void main() {
  group('Chaty Dual-Speaker Audio Subsystem & Policy Coordinator', () {
    late MockChatyAudioPlatform mockPlatform;
    late AudioCapabilityService capabilityService;
    late AudioPolicyCoordinator coordinator;

    setUp(() {
      mockPlatform = MockChatyAudioPlatform();
      capabilityService = AudioCapabilityService(platform: mockPlatform);
      coordinator = AudioPolicyCoordinator(capabilityService: capabilityService);
    });

    test('Initial coordinator state is idle with no active owner', () {
      expect(coordinator.state, equals(AudioSessionState.idle));
      expect(coordinator.activeOwner, isNull);
      expect(coordinator.activeRoute, equals(AudioOutputRoute.systemDefault));
    });

    test('Starting call audio session acquires communication ownership and routes to receiver', () async {
      final success = await coordinator.startCallSession(
        initialRoute: AudioOutputRoute.receiver,
      );

      expect(success, isTrue);
      expect(coordinator.activeOwner, equals(AudioUseCase.communication));
      expect(coordinator.activeRoute, equals(AudioOutputRoute.receiver));
      expect(coordinator.state, equals(AudioSessionState.receiverCall));
    });

    test('Switching route to loudspeaker transitions state to speakerphoneCall', () async {
      await coordinator.startCallSession(initialRoute: AudioOutputRoute.receiver);
      final success = await coordinator.setCallRoute(AudioOutputRoute.speaker);

      expect(success, isTrue);
      expect(coordinator.activeRoute, equals(AudioOutputRoute.speaker));
      expect(coordinator.state, equals(AudioSessionState.speakerphoneCall));
    });

    test('Switching route to Bluetooth transitions state to externalCall', () async {
      await coordinator.startCallSession(initialRoute: AudioOutputRoute.receiver);
      final success = await coordinator.setCallRoute(AudioOutputRoute.bluetooth);

      expect(success, isTrue);
      expect(coordinator.activeRoute, equals(AudioOutputRoute.bluetooth));
      expect(coordinator.state, equals(AudioSessionState.externalCall));
    });

    test('Media playback request is rejected when an active call owns the audio session', () async {
      await coordinator.startCallSession();
      final mediaStarted = await coordinator.startMediaSession();

      expect(mediaStarted, isFalse);
      expect(coordinator.activeOwner, equals(AudioUseCase.communication));
    });

    test('In-call notification sound plays without altering active call session', () async {
      await coordinator.startCallSession(initialRoute: AudioOutputRoute.receiver);

      bool soundPlayed = false;
      await coordinator.playNotificationSound(
        playerAction: () async {
          soundPlayed = true;
        },
      );

      expect(soundPlayed, isTrue);
      expect(coordinator.activeOwner, equals(AudioUseCase.communication));
      expect(coordinator.activeRoute, equals(AudioOutputRoute.receiver));
      expect(coordinator.state, equals(AudioSessionState.receiverCall));
    });

    test('Graceful fallback engaged when preferred route selection encounters an error', () async {
      await coordinator.startCallSession();
      mockPlatform.shouldFailRoute = true;

      final success = await coordinator.setCallRoute(AudioOutputRoute.bluetooth);
      expect(success, isFalse);
    });

    test('Call session teardown idempotently cleans up resources and restores idle state', () async {
      await coordinator.startCallSession();
      expect(coordinator.state, isNot(AudioSessionState.idle));

      await coordinator.endCallSession();
      expect(coordinator.state, equals(AudioSessionState.idle));
      expect(coordinator.activeOwner, isNull);
      expect(mockPlatform.releaseCalled, isTrue);

      // Repeated teardown is safe
      await coordinator.endCallSession();
      expect(coordinator.state, equals(AudioSessionState.idle));
    });

    test('Interruption transitions to interrupted and recovers back to active route', () async {
      await coordinator.startCallSession(initialRoute: AudioOutputRoute.speaker);
      expect(coordinator.state, equals(AudioSessionState.speakerphoneCall));

      coordinator.onInterruption(began: true);
      expect(coordinator.state, equals(AudioSessionState.interrupted));

      coordinator.onInterruption(began: false);
      expect(coordinator.state, equals(AudioSessionState.speakerphoneCall));
    });

    test('AudioSettingsController computes verified device test distribution correctly', () {
      final settings = AudioSettingsController(
        capabilityService: capabilityService,
        coordinator: coordinator,
      );

      expect(settings.preferenceMode, equals(AudioPreferenceMode.automatic));
      settings.setPreferenceMode(AudioPreferenceMode.preferStereo);
      expect(settings.preferenceMode, equals(AudioPreferenceMode.preferStereo));

      final dist = AudioSettingsController.computeTestDistribution();
      expect(dist.containsKey('Verified Dual Output'), isTrue);
      expect(dist['Verified Dual Output']!, greaterThanOrEqualTo(1));
    });
  });
}
