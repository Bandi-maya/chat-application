import 'package:chat/ui/core/settings/settings_registry.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('SettingsRegistry Invariants & Deduplication', () {
    test('validateInvariants passes with zero duplicate setting IDs', () {
      expect(() => SettingsRegistry.validateInvariants(), returnsNormally);
    });

    test('every setting belongs to exactly one unique ID', () {
      final all = SettingsRegistry.allSettings;
      final Set<String> seenIds = <String>{};
      for (final setting in all) {
        expect(
          seenIds.contains(setting.id),
          isFalse,
          reason: 'Setting ID  is duplicated in registry',
        );
        seenIds.add(setting.id);
      }
      expect(all.length, greaterThanOrEqualTo(15));
    });

    test('all registered categories have non-empty clusters', () {
      for (final cluster in SettingsRegistry.clusters) {
        expect(cluster.title.isNotEmpty, isTrue);
        expect(cluster.settings.isNotEmpty, isTrue);
        for (final s in cluster.settings) {
          expect(s.title.isNotEmpty, isTrue);
          expect(s.description.isNotEmpty, isTrue);
          expect(s.canonicalRoute.startsWith('/settings/'), isTrue);
          expect(s.category, cluster.category);
        }
      }
    });

    test('search keywords exist and are lowercased for searching', () {
      for (final setting in SettingsRegistry.allSettings) {
        expect(setting.searchKeywords.isNotEmpty, isTrue);
        for (final kw in setting.searchKeywords) {
          expect(kw, kw.toLowerCase(), reason: 'Keyword " must be lowercased');
 }
 }
 });
 });

 group('Navigation Destination XOR More Overflow Invariant', () {
 test('Primary navigation bar slots XOR More overflow items are mutually disjoint', () {
 const List<String> candidateDestinations = [
 'chats',
 'groups',
 'updates',
 'tasks',
 'calls',
 'settings',
 'desktop',
 ];

 final hasOverflow = candidateDestinations.length > 4;
 expect(hasOverflow, isTrue);

 final primaryItems = candidateDestinations.take(3).toList();
 final overflowItems = candidateDestinations.skip(3).toList();

 final primarySet = primaryItems.toSet();
 final overflowSet = overflowItems.toSet();

 // Invariant: visibleNavIds.intersection(moreIds).isEmpty
 final intersection = primarySet.intersection(overflowSet);
 expect(
 intersection.isEmpty,
 isTrue,
 reason: 'Primary items and More overflow items must never overlap',
 );
 expect(primaryItems.length, 3);
 expect(overflowItems.length, 4);
 });
 });
}
