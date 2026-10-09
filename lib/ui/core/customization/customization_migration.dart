import 'customization_snapshot.dart';

class CustomizationMigration {
  CustomizationMigration._();

  static CustomizationSnapshot migrate(Map<String, dynamic> rawJson) {
    final version = rawJson['schemaVersion'] as int? ?? 1;

    var current = Map<String, dynamic>.from(rawJson);

    if (version < 1) {
      current['schemaVersion'] = 1;
      current.putIfAbsent('globalThemeId', () => 'chaty_emerald');
      current.putIfAbsent('navigationVariant', () => 'classic_label_bar');
      current.putIfAbsent('headerVariant', () => 'standard_identity');
      current.putIfAbsent('composerVariant', () => 'chaty_capsule');
      current.putIfAbsent('bubbleVariant', () => 'stock');
      current.putIfAbsent('tickVariant', () => 'rc_ios_11');
    }

    return CustomizationSnapshot.fromJson(current);
  }
}
