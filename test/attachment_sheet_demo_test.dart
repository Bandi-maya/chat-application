import 'package:chat/features/messages/attachment_sheet.dart';
import 'package:chat/ui/core/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const _theme = ThemeConfig(
  id: 'test_theme',
  name: 'Test Theme',
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
);

void main() {
  testWidgets('AttachmentSheet renders Demo Sandbox dock and standard actions', (
    tester,
  ) async {
    var demoDocTapped = false;
    var demoAudioTapped = false;
    var demoCallTapped = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) {
              return ElevatedButton(
                onPressed: () {
                  showModalBottomSheet<void>(
                    context: context,
                    builder: (_) => AttachmentSheet(
                      theme: _theme,
                      onMediaRequested: (_) {},
                      onLocationRequested: () {},
                      onContactRequested: () {},
                      onPollRequested: () {},
                      onTaskOption: () {},
                      onDemoDocument: () => demoDocTapped = true,
                      onDemoAudio: () => demoAudioTapped = true,
                      onDemoCall: () => demoCallTapped = true,
                    ),
                  );
                },
                child: const Text('Open Sheet'),
              );
            },
          ),
        ),
      ),
    );

    // Open sheet
    await tester.tap(find.text('Open Sheet'));
    await tester.pumpAndSettle();

    // Verify Demo Sandbox presence
    expect(find.text('DEMO SANDBOX'), findsOneWidget);
    expect(find.text('Test Doc (PDF)'), findsOneWidget);
    expect(find.text('Test Music (MP3)'), findsOneWidget);
    expect(find.text('Test Call Card'), findsOneWidget);
    expect(find.text('Test Task Card'), findsOneWidget);
    expect(find.text('Test Location'), findsOneWidget);

    // Verify Standard Attachment Grid actions
    expect(find.text('Gallery'), findsOneWidget);
    expect(find.text('Video'), findsOneWidget);
    expect(find.text('Document'), findsOneWidget);
    expect(find.text('Audio'), findsOneWidget);
    expect(find.text('Location'), findsOneWidget);
    expect(find.text('Contact'), findsOneWidget);
    expect(find.text('Poll'), findsOneWidget);
    expect(find.text('Task'), findsOneWidget);

    // Tap Test Doc (PDF)
    await tester.tap(find.text('Test Doc (PDF)'));
    await tester.pumpAndSettle();

    expect(demoDocTapped, isTrue);
    expect(demoAudioTapped, isFalse);
    expect(demoCallTapped, isFalse);
  });
}
