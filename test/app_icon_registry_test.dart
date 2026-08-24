import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('launcher icon registry ids are unique and round-trip', () {
    final ids = LauncherIconVariant.values
        .map((variant) => variant.id)
        .toList();
    expect(ids.toSet().length, ids.length);
    expect(ids.length, 6);

    for (final variant in LauncherIconVariant.values) {
      expect(LauncherIconVariantMetadata.fromId(variant.id), variant);
      expect(variant.androidAlias, variant.id);
    }
  });

  test('unknown launcher icon preference safely falls back to warm default', () {
    expect(
      LauncherIconVariantMetadata.fromId('removed_future_icon'),
      LauncherIconVariant.warm,
    );
    expect(
      LauncherIconVariantMetadata.fromId('original'),
      LauncherIconVariant.warm,
    );
  });
}
