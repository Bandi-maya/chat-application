import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'system PiP is explicit, capability gated, and reports mode changes',
    () {
      final screen = File(
        'lib/features/calls/ongoing_call_screen.dart',
      ).readAsStringSync();
      final bridge = File(
        'lib/data/services/native_call_pip_service.dart',
      ).readAsStringSync();
      final activity = File(
        'android/app/src/main/kotlin/com/example/chat/MainActivity.kt',
      ).readAsStringSync();

      expect(screen, isNot(contains('AppLifecycleState.inactive')));
      expect(screen, contains("tooltip: 'Picture-in-picture'"));
      expect(bridge, contains("call.method == 'pipModeChanged'"));
      expect(activity, contains('FEATURE_PICTURE_IN_PICTURE'));
      expect(activity, contains('onPictureInPictureModeChanged'));
    },
  );

  test(
    'signaling reconnect reconciles candidates missed during channel swap',
    () {
      final signaling = File(
        'lib/data/services/call_signaling_service.dart',
      ).readAsStringSync();

      expect(signaling, contains('await _reconcileRemoteCandidates();'));
      expect(signaling, contains(".from('call_ice_candidates')"));
      expect(signaling, contains('_processedRemoteCandidateIds'));
    },
  );
}
