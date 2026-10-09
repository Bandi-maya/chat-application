import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/controllers/home_preset_normalizer.dart';

void main() {
  group('HomePresetNormalizer', () {
    test('normalizes home preset labels consumed by the home screen', () {
      expect(HomePresetNormalizer.homeStyle('Chaty'), 'Chaty Default');
      expect(HomePresetNormalizer.homeStyle('Cards'), 'Cards');
      expect(HomePresetNormalizer.homeStyle('Stories first'), 'Stories First');
      expect(HomePresetNormalizer.homeStyle('Classic'), 'Classic');
      expect(HomePresetNormalizer.homeStyle('Compact'), 'Compact');
      expect(HomePresetNormalizer.homeStyle('Minimal'), 'Minimal');
      expect(HomePresetNormalizer.homeStyle('Productivity'), 'Productivity');
    });

    test('normalizes stories preset labels consumed by the stories rail', () {
      expect(HomePresetNormalizer.storiesStyle('Cards'), 'Card');
      expect(HomePresetNormalizer.storiesStyle('Card'), 'Card');
      expect(HomePresetNormalizer.storiesStyle('Squircle'), 'Squircle');
      expect(HomePresetNormalizer.storiesStyle('Compact'), 'Compact');
      expect(HomePresetNormalizer.storiesStyle('Minimal'), 'Minimal');
      expect(HomePresetNormalizer.storiesStyle('Circular'), 'Circular');
    });

    test('trims user-facing labels and uses a safe default for unknown values', () {
      expect(HomePresetNormalizer.homeStyle('  STORIES FIRST '), 'Stories First');
      expect(HomePresetNormalizer.storiesStyle('  cards '), 'Card');
      expect(HomePresetNormalizer.homeStyle('future-layout'), 'Chaty Default');
      expect(HomePresetNormalizer.storiesStyle('future-layout'), 'Circular');
    });
  });
}
