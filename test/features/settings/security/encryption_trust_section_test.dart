import 'package:chat/features/settings/security/encryption_trust_section.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'encryption settings disclose that contact fingerprint verification is unavailable',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: EncryptionTrustSection(),
            ),
          ),
        ),
      );

      expect(find.text('MLS-based encryption'), findsOneWidget);
      expect(
        find.textContaining('contact fingerprint verification is not available here'),
        findsOneWidget,
      );
      expect(find.textContaining('05423 89104'), findsNothing);
      expect(find.text('Verify Safety Number QR'), findsNothing);

      await tester.tap(find.text('MLS-based encryption'));
      await tester.pumpAndSettle();

      expect(
        find.text('Encryption and identity verification'),
        findsOneWidget,
      );
      expect(
        find.textContaining('does not currently derive or verify a contact-specific cryptographic fingerprint'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Do not use a sample number or a status label'),
        findsOneWidget,
      );
      expect(find.text('Close'), findsOneWidget);

      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
      expect(find.text('Encryption and identity verification'), findsNothing);
    },
  );
}
