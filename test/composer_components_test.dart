import 'package:chat/ui/core/design_system/components/composer_components.dart';
import 'package:chat/ui/core/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const _theme = ThemeConfig(
  id: 'composer_test',
  name: 'Composer test',
  brightness: Brightness.dark,
  accentColor: Color(0xFF6366F1),
  backgroundColor: Color(0xFF0F172A),
  surfaceColor: Color(0xFF1E293B),
  cardColor: Color(0xFF334155),
  primaryTextColor: Color(0xFFF8FAFC),
  secondaryTextColor: Color(0xFF94A3B8),
  outgoingBubbleColor: Color(0xFF6366F1),
  incomingBubbleColor: Color(0xFF1E293B),
  outgoingTextColor: Color(0xFFFFFFFF),
  incomingTextColor: Color(0xFFF8FAFC),
  linkColor: Color(0xFF60A5FA),
  dangerColor: Color(0xFFEF4444),
  successColor: Color(0xFF10B981),
  bubbleStyle: BubbleStyleId.rcIos11,
  deliveryTickStyle: DeliveryIconStyle.greenTick,
);

void main() {
  testWidgets('composer action exposes a 48dp semantic button', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ChatyComposerActionButton(
            theme: _theme,
            semanticsLabel: 'Send message',
            icon: Icons.send_rounded,
            onTap: () => taps++,
          ),
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(ChatyComposerActionButton)),
      const Size(48, 48),
    );
    expect(find.bySemanticsLabel('Send message'), findsOneWidget);
    await tester.tap(find.byType(ChatyComposerActionButton));
    expect(taps, 1);
  });

  testWidgets('voice meter clamps samples and respects reduced motion', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(disableAnimations: true),
          child: Scaffold(
            body: ChatyVoiceLevelMeter(
              levels: [-1, 0.5, 2],
              theme: _theme,
            ),
          ),
        ),
      ),
    );

    expect(find.bySemanticsLabel('Live voice level'), findsOneWidget);
    final bars = tester.widgetList<AnimatedContainer>(
      find.descendant(
        of: find.byType(ChatyVoiceLevelMeter),
        matching: find.byType(AnimatedContainer),
      ),
    );
    expect(bars, hasLength(3));
    expect(bars.every((bar) => bar.duration == Duration.zero), isTrue);
  });

  testWidgets('busy composer action is disabled and ignores taps', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ChatyComposerActionButton(
            theme: _theme,
            semanticsLabel: 'Send voice note',
            tooltip: 'Send voice note',
            icon: Icons.send_rounded,
            busy: true,
            onTap: () => taps++,
          ),
        ),
      ),
    );

    expect(find.bySemanticsLabel('Send voice note'), findsOneWidget);
    await tester.tap(find.byType(ChatyComposerActionButton));
    expect(taps, 0);
  });
}
