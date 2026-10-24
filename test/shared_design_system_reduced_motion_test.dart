import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('shared component motion uses the canonical reduced-motion resolver', () {
    final tokenSource = File(
      'lib/ui/core/design_system/tokens/app_tokens.dart',
    ).readAsStringSync();
    expect(tokenSource, contains('static Duration duration('));
    expect(tokenSource, contains('MediaQuery.maybeOf(context)?.disableAnimations'));

    for (final path in <String>[
      'lib/ui/core/design_system/components/app_components.dart',
      'lib/ui/core/design_system/components/chaty_kit.dart',
      'lib/ui/core/design_system/components/messaging_components.dart',
      'lib/ui/core/design_system/components/social_components.dart',
      'lib/ui/core/components/composer/chaty_composer_renderer.dart',
    ]) {
      final source = File(path).readAsStringSync();
      expect(
        source,
        contains('ChatyMotion.duration('),
        reason: '$path should use the shared reduced-motion duration resolver',
      );
    }
  });

  test('core feature screens suppress decorative transitions for reduced motion', () {
    final paths = <String>[
      'lib/features/calls/call_contact_picker_screen.dart',
      'lib/features/tasks/tasks_screen.dart',
      'lib/features/messages/message_bubble.dart',
      'lib/features/messages/chat_attachment_actions.dart',
      'lib/features/chats/main_navigation_shell.dart',
      'lib/features/settings/appearance/component_styles_screen.dart',
      'lib/features/settings/appearance/app_icon_settings_screen.dart',
      'lib/features/settings/pro_pricing_screen.dart',
      'lib/features/settings/security/app_lock_overlay.dart',
      'lib/features/onboarding/permission_onboarding_screen.dart',
      'lib/ui/core/design_system/components/signature_components.dart',
      'lib/ui/core/design_system/components/chaty_modal.dart',
      'lib/ui/core/design_system/gb_design_system.dart',
    ];

    for (final path in paths) {
      final source = File(path).readAsStringSync();
      expect(
        source.contains('MediaQuery.disableAnimationsOf(context)') ||
            source.contains('ChatyMotion.duration('),
        isTrue,
        reason: '$path should respect reduced motion',
      );
    }
  });

  test('feature navigation and swipe-to-reply controllers honor reduced motion', () {
    final navigation = File(
      'lib/features/chats/main_navigation_shell.dart',
    ).readAsStringSync();
    final messageBubble = File(
      'lib/features/messages/message_bubble.dart',
    ).readAsStringSync();

    expect(navigation, contains('_ctrl.duration = MediaQuery.disableAnimationsOf(context)'));
    expect(messageBubble, contains('_controller.duration = MediaQuery.disableAnimationsOf(context)'));
    expect(navigation, contains('duration: MediaQuery.disableAnimationsOf(context)'));
    expect(messageBubble, contains('duration: MediaQuery.disableAnimationsOf(context)'));
  });

  test('skeleton shimmer stops and resumes when reduced-motion preference changes', () {
    final source = File(
      'lib/ui/core/design_system/components/app_components.dart',
    ).readAsStringSync();

    expect(source, contains('void didChangeDependencies()'));
    expect(source, contains('_syncPulseWithMotionPreference();'));
    expect(source, contains('if (_pulse.isAnimating) _pulse.stop();'));
    expect(source, contains('_pulse.repeat(reverse: true);'));
    expect(source, contains('if (reduceMotion) return block();'));
  });
}
