import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:chat/data/services/local_lock_service.dart';
import 'package:chat/features/settings/security/app_lock_overlay.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';

/// Regression coverage for the "No Overlay widget found" crash class.
///
/// AppLockOverlayModal is hosted by MaterialApp.builder as a sibling ABOVE the
/// Navigator, so it has no Overlay ancestor. Tooltip-backed IconButtons
/// crashed on long press in that position; pin-pad actions must stay
/// overlay-free (Semantics-based) and fully functional.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  FlutterSecureStorage.setMockInitialValues({});

  // Reports a configured PIN so the modal renders the pad instead of the
  // first-time setup branch.
  final lockService = _ConfiguredLockService();

  Future<void> pumpLockedShell(WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => Stack(
          fit: StackFit.expand,
          children: [
            child ?? const SizedBox.shrink(),
            AppLockOverlayModal(
              preferencesController: ChatyPreferencesController(),
              lockService: lockService,
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'pin pad action keys survive long press without an Overlay ancestor',
    (tester) async {
      await pumpLockedShell(tester);

      expect(find.byType(Tooltip), findsNothing);

      await tester.longPress(find.byIcon(Icons.fingerprint_rounded));
      await tester.pump();
      await tester.longPress(find.byIcon(Icons.backspace_outlined));
      await tester.pump();

      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('backspace removes entered digits and wrong PIN shows an error', (
    tester,
  ) async {
    await pumpLockedShell(tester);

    await tester.tap(find.text('1'));
    await tester.pump(const Duration(milliseconds: 150));

    await tester.tap(find.byIcon(Icons.backspace_outlined));
    await tester.pump(const Duration(milliseconds: 150));

    // Enter 2580 (nothing was kept from the deleted digit) -> verification of
    // a missing credential fails safely with the retry message.
    for (final digit in ['2', '5', '8', '0']) {
      await tester.tap(find.text(digit));
      await tester.pump(const Duration(milliseconds: 150));
    }
    await tester.pumpAndSettle();

    expect(find.text('Incorrect pin. Try again.'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

class _ConfiguredLockService extends LocalLockService {
  @override
  Future<bool> hasCredential(String method) async => true;

  @override
  Future<int> getPinLength() async => 4;
}
