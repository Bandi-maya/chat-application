import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../lib/ui/core/controllers/appearance_variant_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{});
  });

  test(
    'concurrent appearance changes persist without losing independent values',
    () async {
      final controller = AppearanceVariantController();

      await Future.wait(<Future<void>>[
        controller.setTypographyStyle('Accessibility'),
        controller.setEntryAnimation('None'),
        controller.setTypographyStyle('Large UI'),
      ]);

      final preferences = await SharedPreferences.getInstance();
      expect(preferences.getString('appearance.typography'), 'Large UI');
      expect(preferences.getString('appearance.entryAnimation'), 'None');
      expect(controller.typographyStyle, 'Large UI');
      expect(controller.entryAnimation, 'None');
    },
  );

  test('unsupported appearance values are ignored', () async {
    final controller = AppearanceVariantController();
    await controller.setTypographyStyle('Not a supported style');

    final preferences = await SharedPreferences.getInstance();
    expect(preferences.getString('appearance.typography'), isNull);
    expect(controller.typographyStyle, 'System Default');
  });
}
