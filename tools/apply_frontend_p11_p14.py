#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


# ---------------------------------------------------------------------------
# P11/P12 — accessibility + adaptive policy.
# Allow the app shell to respect up to 200% text scaling instead of artificially
# capping users at 160%. Individual components must resolve overflow through
# wrapping/adaptive layout rather than shrinking accessibility preferences.
# ---------------------------------------------------------------------------
path = 'lib/main.dart'
text = read(path)
text = text.replace('.clamp(0.8, 1.6)', '.clamp(0.8, 2.0)')
write(path, text)

# Add practical adaptive helpers used by phone/tablet/foldable surfaces.
# IMPORTANT: this script is executed repeatedly by CI reconciliation. Keep each
# helper independently idempotent so previously promoted lane code cannot gain
# duplicate class members on the next integration run.
path = 'lib/ui/core/design_system/chaty_adaptive.dart'
text = read(path)

if 'static bool usesTwoPane' not in text:
    marker = '''  static bool prefersRail(BuildContext context) {
    final value = of(context);
    return value == ChatyWindowClass.expanded ||
        value == ChatyWindowClass.large;
  }
'''
    replacement = marker + '''
  static bool usesTwoPane(BuildContext context) {
    final value = of(context);
    return value == ChatyWindowClass.expanded ||
        value == ChatyWindowClass.large;
  }
'''
    if marker not in text:
        raise SystemExit('P11-P14 adaptive marker missing: prefersRail')
    text = text.replace(marker, replacement, 1)

if 'static EdgeInsets pageInsets' not in text:
    marker = '  static double contentMaxWidth(BuildContext context) {'
    helper = '''  static EdgeInsets pageInsets(BuildContext context) {
    return switch (of(context)) {
      ChatyWindowClass.compact => const EdgeInsets.symmetric(horizontal: 12),
      ChatyWindowClass.medium => const EdgeInsets.symmetric(horizontal: 20),
      ChatyWindowClass.expanded => const EdgeInsets.symmetric(horizontal: 28),
      ChatyWindowClass.large => const EdgeInsets.symmetric(horizontal: 36),
    };
  }

'''
    if marker not in text:
        raise SystemExit('P11-P14 adaptive marker missing: contentMaxWidth')
    text = text.replace(marker, helper + marker, 1)

if 'static double conversationListWidth' not in text:
    marker = '  static EdgeInsets pageInsets(BuildContext context) {'
    helper = '''  static double conversationListWidth(BuildContext context) {
    return switch (of(context)) {
      ChatyWindowClass.compact => double.infinity,
      ChatyWindowClass.medium => double.infinity,
      ChatyWindowClass.expanded => 360,
      ChatyWindowClass.large => 400,
    };
  }

'''
    if marker not in text:
        raise SystemExit('P11-P14 adaptive marker missing: pageInsets')
    text = text.replace(marker, helper + marker, 1)

write(path, text)

# Motion helper already respects MediaQuery.disableAnimations. Make its public
# contract explicit and provide a transform duration helper for components.
path = 'lib/ui/core/design_system/chaty_motion.dart'
text = read(path)
if 'static bool reducedMotion' not in text:
    text = text.replace(
        '''  static Duration duration(
    BuildContext context, {
    Duration preferred = base,
  }) {
''',
        '''  static bool reducedMotion(BuildContext context) =>
      MediaQuery.maybeOf(context)?.disableAnimations == true;

  static Duration duration(
    BuildContext context, {
    Duration preferred = base,
  }) {
''',
        1,
    )
write(path, text)


# ---------------------------------------------------------------------------
# P13/P14 — regression contract tests. These are source-level invariants for
# the device-only failures seen during QA and for mock-data isolation.
# ---------------------------------------------------------------------------
test_path = ROOT / 'test/frontend_master_contract_test.dart'
test_path.write_text(r'''import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('root call capsule does not depend on Tooltip overlay', () {
    final source = File(
      'lib/ui/core/design_system/components/call_activity_capsule.dart',
    ).readAsStringSync();
    expect(source, contains('Semantics('));
    expect(source, isNot(contains('Tooltip(')));
  });

  test('root and navigation merged listenables are stable fields', () {
    final mainSource = File('lib/main.dart').readAsStringSync();
    final navigation = File(
      'lib/features/chats/main_navigation_shell.dart',
    ).readAsStringSync();
    expect(mainSource, contains('late final Listenable _rootSignals;'));
    expect(mainSource, contains('listenable: _rootSignals'));
    expect(navigation, contains('late final Listenable _navigationSignals;'));
    expect(navigation, contains('listenable: _navigationSignals'));
  });

  test('frontend supports reduced motion and 200 percent text scale', () {
    final motion = File(
      'lib/ui/core/design_system/chaty_motion.dart',
    ).readAsStringSync();
    final mainSource = File('lib/main.dart').readAsStringSync();
    expect(motion, contains('disableAnimations'));
    expect(mainSource, contains('.clamp(0.8, 2.0)'));
  });

  test('accessible preferred touch target remains 48 dp', () {
    final tokens = File(
      'lib/ui/core/design_system/tokens/app_tokens.dart',
    ).readAsStringSync();
    expect(tokens, contains('preferredTouchTarget = 48.0'));
  });

  test('UI Lab is debug-only and isolated from production repositories', () {
    final settings = File(
      'lib/features/settings/settings_root_screen.dart',
    ).readAsStringSync();
    final lab = File('lib/features/ui_lab/ui_lab_screen.dart').readAsStringSync();
    final repo = File(
      'lib/features/ui_lab/ui_lab_repository.dart',
    ).readAsStringSync();
    expect(settings, contains('if (kDebugMode)'));
    expect(lab, contains("assert(kDebugMode"));
    expect(repo, isNot(contains('Supabase')));
    expect(repo, isNot(contains('ChatyBackendService')));
  });

  test('poll transport markers are stripped from visible poll question', () {
    final attachments = File(
      'lib/features/messages/chat_attachment_actions.dart',
    ).readAsStringSync();
    final chat = File(
      'lib/features/chats/chat_detail_screen.dart',
    ).readAsStringSync();
    expect(attachments, contains('static String pollQuestion'));
    expect(attachments, contains("while (value.startsWith('[POLL]'))"));
    expect(chat, contains('ChatyPollSummaryCard('));
  });

  test('adaptive policy defines two-pane expanded layouts', () {
    final adaptive = File(
      'lib/ui/core/design_system/chaty_adaptive.dart',
    ).readAsStringSync();
    expect(adaptive, contains('usesTwoPane'));
    expect(adaptive, contains('conversationListWidth'));
  });
}
''', encoding='utf-8')

checks = {
    'lib/main.dart': ['.clamp(0.8, 2.0)'],
    'lib/ui/core/design_system/chaty_adaptive.dart': [
        'usesTwoPane',
        'conversationListWidth',
    ],
    'lib/ui/core/design_system/chaty_motion.dart': ['reducedMotion'],
    'test/frontend_master_contract_test.dart': [
        'root call capsule does not depend on Tooltip overlay',
        'UI Lab is debug-only',
    ],
}
for file_path, needles in checks.items():
    value = read(file_path)
    for needle in needles:
        if needle not in value:
            raise SystemExit(f'P11-P14 invariant missing: {file_path}: {needle}')

print('Frontend P11-P14 integration applied.')
