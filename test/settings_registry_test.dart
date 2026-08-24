import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/settings/settings_models.dart';
import 'package:chat/ui/core/settings/settings_registry.dart';

void main() {
  group('Settings System & Registry Tests', () {
    test('Validate all setting IDs are globally unique', () {
      expect(SettingsRegistry.validateInvariants(), isTrue);
    });

    test('All settings clusters belong to valid canonical categories', () {
      for (final cluster in SettingsRegistry.clusters) {
        expect(cluster.id.isNotEmpty, isTrue);
        expect(cluster.title.isNotEmpty, isTrue);
        expect(cluster.settings.isNotEmpty, isTrue);
        for (final s in cluster.settings) {
          expect(s.id.isNotEmpty, isTrue);
          expect(s.canonicalRoute.startsWith('/settings'), isTrue);
          expect(s.category, equals(cluster.category));
        }
      }
    });

    test('Canonical Settings Categories cover all 5 root groups', () {
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.account).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.privacy).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.security).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.chats).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.appearance).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.homeAndNavigation).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.notifications).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.calls).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.storageAndMedia).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.devices).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.effects).isNotEmpty, isTrue);
      expect(SettingsRegistry.clustersForCategory(SettingsCategory.permissions).isNotEmpty, isTrue);
    });
  });
}
